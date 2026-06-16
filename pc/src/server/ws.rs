use std::net::SocketAddr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use anyhow::Result;
use futures_util::{SinkExt, StreamExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::{Mutex, RwLock};
use tokio::sync::watch;
use tokio_tungstenite::{accept_async, tungstenite::Message};

use crate::auth::AppConfig;
use crate::config::{APP_MAGIC, LISTEN_PORT, UDP_LISTEN_PORT, WS_PATH};
use crate::input::{
    apply_gamepad_snapshot, enqueue_combo, enqueue_key, enqueue_mouse_click, enqueue_mouse_move,
    enqueue_mouse_scroll, enqueue_release_all, enqueue_system_shutdown, enqueue_text,
    gamepad_mode_active, release_gamepad, set_gamepad_mode, vigem_available, GamepadSnapshot,
};
use crate::protocol::binary::parse_ws_frame;
use crate::protocol::{normalize_gamepad_hz, ClientMessage, RemoteInputMode, ServerMessage, SystemAction};
use crate::server::session::{active_udp_session_key, clear_udp_session_key, rotate_udp_session_key};
use crate::server::udp::reset_udp_seq;

struct ActiveSession {
    id: u64,
    cancel: watch::Sender<bool>,
}

static NEXT_SESSION_ID: AtomicU64 = AtomicU64::new(1);

static ACTIVE_SESSION: once_cell::sync::Lazy<Arc<Mutex<Option<ActiveSession>>>> =
    once_cell::sync::Lazy::new(|| Arc::new(Mutex::new(None)));

pub async fn run_server(config: Arc<RwLock<AppConfig>>) -> Result<()> {
    tokio::spawn(async {
        if let Err(e) = super::udp::run_udp_server().await {
            log::error!("UDP 服务异常: {e}");
        }
    });

    let addr = SocketAddr::from(([0, 0, 0, 0], LISTEN_PORT));
    let listener = TcpListener::bind(addr).await?;
    log::info!("遥控器服务已启动 ws://0.0.0.0:{LISTEN_PORT}{WS_PATH}");

    loop {
        let (stream, peer) = listener.accept().await?;
        let cfg = Arc::clone(&config);
        tokio::spawn(async move {
            if let Err(e) = handle_connection(stream, peer, cfg).await {
                log::warn!("连接 {peer} 异常: {e}");
            }
        });
    }
}

async fn handle_connection(
    stream: TcpStream,
    peer: SocketAddr,
    config: Arc<RwLock<AppConfig>>,
) -> Result<()> {
    let ws_stream = accept_async(stream).await?;

    let udp_key = rotate_udp_session_key();
    reset_udp_seq();

    let (mut write, mut read) = ws_stream.split();
    log::info!("客户端已连接: {peer} | UDP key={udp_key}");

    let (cancel_tx, mut cancel_rx) = watch::channel(false);
    let session_id = NEXT_SESSION_ID.fetch_add(1, Ordering::SeqCst);
    {
        let mut guard = ACTIVE_SESSION.lock().await;
        if let Some(old) = guard.take() {
            let _ = old.cancel.send(true);
            log::info!("已通知旧客户端让位");
        }
        *guard = Some(ActiveSession {
            id: session_id,
            cancel: cancel_tx.clone(),
        });
    }

    loop {
        tokio::select! {
            changed = cancel_rx.changed() => {
                if changed.is_ok() && *cancel_rx.borrow() {
                    log::info!("连接 {peer} 被新客户端替换");
                    let _ = write.close().await;
                    break;
                }
            }
            msg = read.next() => {
                match msg {
                    Some(Ok(msg)) => {
                        if msg.is_close() {
                            break;
                        }
                        if msg.is_binary() {
                            process_binary(msg.into_data(), &config).await;
                            continue;
                        }
                        if !msg.is_text() {
                            continue;
                        }

                        let text = match msg.to_text() {
                            Ok(t) => t.trim(),
                            Err(_) => continue,
                        };
                        if text.is_empty() {
                            continue;
                        }

                        let reply = process_message(text, &config).await;
                        if let Some(json) = reply {
                            if write.send(Message::Text(json.into())).await.is_err() {
                                break;
                            }
                        }
                    }
                    Some(Err(e)) => {
                        log::warn!("连接 {peer} 读错误: {e}");
                        break;
                    }
                    None => break,
                }
            }
        }
    }

    log::info!("客户端断开: {peer}");

    let should_release = {
        let mut guard = ACTIVE_SESSION.lock().await;
        if guard.as_ref().is_some_and(|session| session.id == session_id) {
            *guard = None;
            true
        } else {
            false
        }
    };

    if should_release {
        clear_udp_session_key();
        reset_udp_seq();
        release_gamepad();
        enqueue_release_all();
    }
    Ok(())
}

async fn process_binary(data: Vec<u8>, _config: &Arc<RwLock<AppConfig>>) {
    let Some(frame) = parse_ws_frame(&data) else {
        return;
    };
    if !gamepad_mode_active() {
        return;
    }
    apply_gamepad_snapshot(frame.snapshot());
}

fn udp_session_info() -> (u16, u32) {
    use crate::server::session::active_udp_session_key;
    (UDP_LISTEN_PORT, active_udp_session_key())
}

async fn process_message(text: &str, config: &Arc<RwLock<AppConfig>>) -> Option<String> {
    let parsed: Result<ClientMessage, _> = serde_json::from_str(text);
    let msg = match parsed {
        Ok(m) => m,
        Err(e) => {
            return Some(ServerMessage::err("INVALID_MSG", &e.to_string()).to_json());
        }
    };

    match msg {
        ClientMessage::Ping { seq, ts } => {
            let (udp_port, udp_key) = udp_session_info();
            Some(ServerMessage::pong(seq, ts, udp_port, udp_key).to_json())
        }

        ClientMessage::InputMode { token, mode, hz } => {
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            match mode {
                RemoteInputMode::KeyboardMouse => {
                    set_gamepad_mode(false, normalize_gamepad_hz(hz));
                    None
                }
                RemoteInputMode::Gamepad => {
                    if !vigem_available() {
                        return Some(
                            ServerMessage::err(
                                "GAMEPAD_UNAVAILABLE",
                                "PC 未安装或未加载 ViGEmBus 驱动，无法使用虚拟手柄",
                            )
                            .to_json(),
                        );
                    }
                    set_gamepad_mode(true, normalize_gamepad_hz(hz));
                    None
                }
            }
        }

        ClientMessage::Gamepad {
            token,
            lx,
            ly,
            rx,
            ry,
            lt,
            rt,
            buttons,
        } => {
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            if !gamepad_mode_active() {
                return None;
            }

            apply_gamepad_snapshot(GamepadSnapshot {
                thumb_lx: lx,
                thumb_ly: ly,
                thumb_rx: rx,
                thumb_ry: ry,
                left_trigger: lt,
                right_trigger: rt,
                buttons,
            });
            None
        }

        ClientMessage::Pair { magic, pin, device } => {
            if magic != APP_MAGIC {
                return Some(ServerMessage::err("INVALID_MSG", "magic mismatch").to_json());
            }

            let mut cfg = config.write().await;
            if !cfg.pin_valid(&pin) {
                return Some(ServerMessage::err("INVALID_PIN", "PIN 错误或已过期").to_json());
            }

            cfg.refresh_pin();
            let _ = cfg.save();
            log::info!("设备已配对: {device}");

            Some(
                ServerMessage::paired(
                    cfg.session_token.clone(),
                    hostname(),
                    UDP_LISTEN_PORT,
                    active_udp_session_key(),
                )
                .to_json(),
            )
        }

        ClientMessage::Key {
            token,
            action,
            vk,
            mods,
        } => {
            if gamepad_mode_active() {
                return None;
            }
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_key(vk, action, mods);
            None
        }

        ClientMessage::Combo { token, vk, mods } => {
            if gamepad_mode_active() {
                return None;
            }
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_combo(vk, mods);
            None
        }

        ClientMessage::MouseMove { token, dx, dy } => {
            if gamepad_mode_active() {
                return None;
            }
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_mouse_move(dx, dy);
            None
        }

        ClientMessage::MouseClick { token, button, action } => {
            if gamepad_mode_active() {
                return None;
            }
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_mouse_click(button, action);
            None
        }

        ClientMessage::MouseScroll { token, delta_y, delta_x } => {
            if gamepad_mode_active() {
                return None;
            }
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_mouse_scroll(delta_y, delta_x);
            None
        }

        ClientMessage::TextInput { token, text } => {
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_text(text);
            None
        }

        ClientMessage::System { token, action } => {
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            match action {
                SystemAction::Shutdown => enqueue_system_shutdown(),
            }
            None
        }
    }
}

fn hostname() -> String {
    std::env::var("COMPUTERNAME").unwrap_or_else(|_| "Windows-PC".to_string())
}
