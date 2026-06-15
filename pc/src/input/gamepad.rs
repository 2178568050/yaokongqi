use std::sync::mpsc::{self, Receiver, Sender};
use std::sync::{Mutex, OnceLock};
use std::thread;
use std::time::{Duration, Instant};

use vigem_client::{Client, TargetId, XGamepad, Xbox360Wired};

#[derive(Clone, Copy, Debug, Default)]
pub struct GamepadSnapshot {
    pub thumb_lx: i16,
    pub thumb_ly: i16,
    pub thumb_rx: i16,
    pub thumb_ry: i16,
    pub left_trigger: u8,
    pub right_trigger: u8,
    pub buttons: u16,
}

enum GamepadCommand {
    SetMode { enabled: bool, hz: u8 },
    Update(GamepadSnapshot),
    Release,
}

static GAMEPAD_TX: OnceLock<Sender<GamepadCommand>> = OnceLock::new();
static LATEST: Mutex<GamepadSnapshot> = Mutex::new(GamepadSnapshot {
    thumb_lx: 0,
    thumb_ly: 0,
    thumb_rx: 0,
    thumb_ry: 0,
    left_trigger: 0,
    right_trigger: 0,
    buttons: 0,
});
static VIGEM_AVAILABLE: std::sync::atomic::AtomicBool =
    std::sync::atomic::AtomicBool::new(false);
static GAMEPAD_MODE: std::sync::atomic::AtomicBool =
    std::sync::atomic::AtomicBool::new(false);

pub fn init_gamepad() {
    let (tx, rx) = mpsc::channel();
    thread::Builder::new()
        .name("yaokongqi-gamepad".into())
        .spawn(move || run_gamepad_thread(rx))
        .expect("gamepad thread");
    GAMEPAD_TX.set(tx).ok();
}

pub fn vigem_available() -> bool {
    VIGEM_AVAILABLE.load(std::sync::atomic::Ordering::SeqCst)
}

pub fn gamepad_mode_active() -> bool {
    GAMEPAD_MODE.load(std::sync::atomic::Ordering::SeqCst)
}

pub fn set_gamepad_mode(enabled: bool, hz: u8) {
    GAMEPAD_MODE.store(enabled, std::sync::atomic::Ordering::SeqCst);
    send(GamepadCommand::SetMode {
        enabled,
        hz: hz.clamp(60, 250),
    });
}

pub fn apply_gamepad_snapshot(snapshot: GamepadSnapshot) {
    if let Ok(mut guard) = LATEST.lock() {
        *guard = snapshot;
    }
    send(GamepadCommand::Update(snapshot));
}

pub fn release_gamepad() {
    GAMEPAD_MODE.store(false, std::sync::atomic::Ordering::SeqCst);
    let zero = GamepadSnapshot::default();
    if let Ok(mut guard) = LATEST.lock() {
        *guard = zero;
    }
    send(GamepadCommand::Release);
}

fn send(cmd: GamepadCommand) {
    if let Some(tx) = GAMEPAD_TX.get() {
        let _ = tx.send(cmd);
    }
}

fn run_gamepad_thread(rx: Receiver<GamepadCommand>) {
    let client = match Client::connect() {
        Ok(c) => c,
        Err(e) => {
            log::warn!(
                "虚拟手柄不可用（需安装 ViGEmBus 驱动）: {e} | https://github.com/nefarius/ViGEmBus/releases"
            );
            return;
        }
    };

    let mut target = Xbox360Wired::new(client, TargetId::XBOX360_WIRED);
    let mut plugged = false;
    let mut mode_enabled = false;
    let mut tick_hz: u8 = 180;
    let mut last_tick = Instant::now();

    VIGEM_AVAILABLE.store(true, std::sync::atomic::Ordering::SeqCst);
    log::info!("ViGEm 虚拟手柄已就绪");

    loop {
        let interval = Duration::from_micros(1_000_000 / tick_hz.max(60) as u64);
        while let Ok(cmd) = rx.try_recv() {
            match cmd {
                GamepadCommand::SetMode { enabled, hz } => {
                    mode_enabled = enabled;
                    tick_hz = hz;
                    if enabled && !plugged {
                        if plug_controller(&mut target) {
                            plugged = true;
                        } else {
                            mode_enabled = false;
                            GAMEPAD_MODE.store(false, std::sync::atomic::Ordering::SeqCst);
                        }
                    } else if !enabled && plugged {
                        let _ = push_state(&mut target, GamepadSnapshot::default());
                        unplug_controller(&mut target);
                        plugged = false;
                    }
                }
                GamepadCommand::Update(snapshot) => {
                    if mode_enabled && plugged {
                        let _ = push_state(&mut target, snapshot);
                    }
                }
                GamepadCommand::Release => {
                    mode_enabled = false;
                    if plugged {
                        let _ = push_state(&mut target, GamepadSnapshot::default());
                        unplug_controller(&mut target);
                        plugged = false;
                    }
                }
            }
        }

        if mode_enabled && plugged && last_tick.elapsed() >= interval {
            last_tick = Instant::now();
            let snapshot = LATEST.lock().map(|g| *g).unwrap_or_default();
            let _ = push_state(&mut target, snapshot);
        }

        thread::sleep(Duration::from_millis(1));
    }
}

fn plug_controller(target: &mut Xbox360Wired<Client>) -> bool {
    if target.plugin().is_err() {
        log::warn!("虚拟手柄 plugin 失败");
        return false;
    }
    if target.wait_ready().is_err() {
        log::warn!("虚拟手柄 wait_ready 超时");
        let _ = target.unplug();
        return false;
    }
    log::info!("虚拟 Xbox 360 手柄已连接");
    true
}

fn unplug_controller(target: &mut Xbox360Wired<Client>) {
    let _ = target.unplug();
    log::info!("虚拟 Xbox 360 手柄已断开");
}

fn push_state(target: &mut Xbox360Wired<Client>, snapshot: GamepadSnapshot) -> bool {
    let pad = XGamepad {
        thumb_lx: sanitize_stick(snapshot.thumb_lx),
        thumb_ly: sanitize_stick(snapshot.thumb_ly),
        thumb_rx: sanitize_stick(snapshot.thumb_rx),
        thumb_ry: sanitize_stick(snapshot.thumb_ry),
        left_trigger: snapshot.left_trigger,
        right_trigger: snapshot.right_trigger,
        buttons: snapshot.buttons.into(),
        ..Default::default()
    };
    target.update(&pad).is_ok()
}

/// 过滤摇杆微漂移，减少 PC 端视角/移动抖动
fn sanitize_stick(value: i16) -> i16 {
    const DEADZONE: i16 = 780;
    if value.abs() < DEADZONE {
        0
    } else {
        value
    }
}
