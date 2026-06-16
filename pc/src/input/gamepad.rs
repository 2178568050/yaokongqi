use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{self, Receiver, RecvTimeoutError, Sender};
use std::sync::{Mutex, OnceLock};
use std::thread;
use std::time::{Duration, Instant};

use vigem_client::{Client, TargetId, XGamepad, Xbox360Wired};

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct GamepadSnapshot {
    pub thumb_lx: i16,
    pub thumb_ly: i16,
    pub thumb_rx: i16,
    pub thumb_ry: i16,
    pub left_trigger: u8,
    pub right_trigger: u8,
    pub buttons: u16,
}

#[derive(Clone, Copy, Default, PartialEq, Eq)]
struct PushedPadState {
    thumb_lx: i16,
    thumb_ly: i16,
    thumb_rx: i16,
    thumb_ry: i16,
    left_trigger: u8,
    right_trigger: u8,
    buttons: u16,
}

#[derive(Clone)]
enum GamepadCommand {
    SetMode { enabled: bool, hz: u16 },
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
static VIGEM_AVAILABLE: AtomicBool = AtomicBool::new(false);
static GAMEPAD_MODE: AtomicBool = AtomicBool::new(false);

pub fn init_gamepad() {
    let (tx, rx) = mpsc::channel();
    thread::Builder::new()
        .name("yaokongqi-gamepad".into())
        .spawn(move || run_gamepad_thread(rx))
        .expect("gamepad thread");
    GAMEPAD_TX.set(tx).ok();
}

pub fn vigem_available() -> bool {
    VIGEM_AVAILABLE.load(Ordering::SeqCst)
}

pub fn gamepad_mode_active() -> bool {
    GAMEPAD_MODE.load(Ordering::SeqCst)
}

pub fn set_gamepad_mode(enabled: bool, hz: u16) {
    GAMEPAD_MODE.store(enabled, Ordering::SeqCst);
    send(GamepadCommand::SetMode {
        enabled,
        hz: hz.clamp(60, 500),
    });
}

pub fn apply_gamepad_snapshot(snapshot: GamepadSnapshot) {
    if let Ok(mut guard) = LATEST.lock() {
        *guard = snapshot;
    }
    send(GamepadCommand::Update(snapshot));
}

pub fn release_gamepad() {
    GAMEPAD_MODE.store(false, Ordering::SeqCst);
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
    const IDLE_POLL: Duration = Duration::from_micros(100);

    raise_gamepad_thread_priority();

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
    let mut tick_hz: u16 = 180;
    let mut last_tick = Instant::now();
    let mut last_pushed = PushedPadState::default();

    VIGEM_AVAILABLE.store(true, Ordering::SeqCst);
    log::info!("ViGEm 虚拟手柄已就绪");

    loop {
        let interval = Duration::from_micros(1_000_000 / tick_hz.clamp(60, 500) as u64);

        let mut batch = Vec::new();
        match rx.recv_timeout(IDLE_POLL) {
            Ok(cmd) => batch.push(cmd),
            Err(RecvTimeoutError::Timeout) => {}
            Err(RecvTimeoutError::Disconnected) => break,
        }
        while let Ok(cmd) = rx.try_recv() {
            batch.push(cmd);
        }
        if !batch.is_empty() {
            process_command_batch(
                &batch,
                &mut target,
                &mut plugged,
                &mut mode_enabled,
                &mut tick_hz,
                &mut last_pushed,
            );
        }

        if mode_enabled && plugged && last_tick.elapsed() >= interval {
            last_tick = Instant::now();
            let snapshot = LATEST.lock().map(|g| *g).unwrap_or_default();
            push_snapshot_if_changed(&mut target, snapshot, &mut last_pushed);
        }
    }
}

/// 合并 batch 内连续 Update，只 push 最新一帧；状态未变则跳过 ViGEm update。
fn process_command_batch(
    batch: &[GamepadCommand],
    target: &mut Xbox360Wired<Client>,
    plugged: &mut bool,
    mode_enabled: &mut bool,
    tick_hz: &mut u16,
    last_pushed: &mut PushedPadState,
) {
    let mut pending_update: Option<GamepadSnapshot> = None;

    for cmd in batch {
        match cmd {
            GamepadCommand::Update(snapshot) => pending_update = Some(*snapshot),
            other => {
                if let Some(snapshot) = pending_update.take() {
                    apply_update(target, snapshot, last_pushed, *mode_enabled && *plugged);
                }
                dispatch_gamepad_command(
                    other.clone(),
                    target,
                    plugged,
                    mode_enabled,
                    tick_hz,
                    last_pushed,
                );
            }
        }
    }

    if let Some(snapshot) = pending_update {
        apply_update(target, snapshot, last_pushed, *mode_enabled && *plugged);
    }
}

fn apply_update(
    target: &mut Xbox360Wired<Client>,
    snapshot: GamepadSnapshot,
    last_pushed: &mut PushedPadState,
    active: bool,
) {
    if active {
        push_snapshot_if_changed(target, snapshot, last_pushed);
    }
}

fn dispatch_gamepad_command(
    cmd: GamepadCommand,
    target: &mut Xbox360Wired<Client>,
    plugged: &mut bool,
    mode_enabled: &mut bool,
    tick_hz: &mut u16,
    last_pushed: &mut PushedPadState,
) {
    match cmd {
        GamepadCommand::SetMode { enabled, hz } => {
            *mode_enabled = enabled;
            *tick_hz = hz;
            if enabled && !*plugged {
                if plug_controller(target) {
                    *plugged = true;
                    *last_pushed = PushedPadState::default();
                } else {
                    *mode_enabled = false;
                    GAMEPAD_MODE.store(false, Ordering::SeqCst);
                }
            } else if !enabled && *plugged {
                push_zero_state(target, last_pushed);
                unplug_controller(target);
                *plugged = false;
            }
        }
        GamepadCommand::Update(_) => {}
        GamepadCommand::Release => {
            *mode_enabled = false;
            if *plugged {
                push_zero_state(target, last_pushed);
                unplug_controller(target);
                *plugged = false;
            }
        }
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

fn push_zero_state(target: &mut Xbox360Wired<Client>, last_pushed: &mut PushedPadState) {
    push_snapshot_if_changed(target, GamepadSnapshot::default(), last_pushed);
}

fn push_snapshot_if_changed(
    target: &mut Xbox360Wired<Client>,
    snapshot: GamepadSnapshot,
    last_pushed: &mut PushedPadState,
) -> bool {
    let next = pad_state_from_snapshot(snapshot);
    if *last_pushed == next {
        return false;
    }
    let pad = xgamepad_from_state(next);
    if target.update(&pad).is_ok() {
        *last_pushed = next;
        true
    } else {
        false
    }
}

fn pad_state_from_snapshot(snapshot: GamepadSnapshot) -> PushedPadState {
    PushedPadState {
        thumb_lx: sanitize_move_stick(snapshot.thumb_lx),
        thumb_ly: sanitize_move_stick(snapshot.thumb_ly),
        thumb_rx: sanitize_aim_stick(snapshot.thumb_rx),
        thumb_ry: sanitize_aim_stick(snapshot.thumb_ry),
        left_trigger: snapshot.left_trigger,
        right_trigger: snapshot.right_trigger,
        buttons: snapshot.buttons,
    }
}

fn xgamepad_from_state(state: PushedPadState) -> XGamepad {
    XGamepad {
        thumb_lx: state.thumb_lx,
        thumb_ly: state.thumb_ly,
        thumb_rx: state.thumb_rx,
        thumb_ry: state.thumb_ry,
        left_trigger: state.left_trigger,
        right_trigger: state.right_trigger,
        buttons: state.buttons.into(),
        ..Default::default()
    }
}

/// 左摇杆：轻量死区（Android 端已滤小值，避免双重过滤导致移动发钝）
fn sanitize_move_stick(value: i16) -> i16 {
    const DEADZONE: i16 = 480;
    if value.abs() < DEADZONE {
        0
    } else {
        value
    }
}

/// 右摇杆瞄准：极小死区，保留微调
fn sanitize_aim_stick(value: i16) -> i16 {
    const DEADZONE: i16 = 24;
    if value.abs() < DEADZONE {
        0
    } else {
        value
    }
}

fn raise_gamepad_thread_priority() {
    #[cfg(windows)]
    {
        use windows::Win32::System::Threading::{
            GetCurrentThread, SetThreadPriority, THREAD_PRIORITY_ABOVE_NORMAL,
        };
        unsafe {
            let _ = SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_ABOVE_NORMAL);
        }
    }
}
