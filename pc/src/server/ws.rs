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
use crate::config::{APP_MAGIC, LISTEN_PORT, WS_PATH};
use crate::input::{
    enqueue_combo, enqueue_key, enqueue_mouse_click, enqueue_mouse_move, enqueue_mouse_scroll,
    enqueue_release_all, enqueue_system_shutdown, enqueue_text,
};
use crate::protocol::{ClientMessage, ServerMessage, SystemAction};

struct ActiveSession {
    id: u64,
    cancel: watch::Sender<bool>,
}

static NEXT_SESSION_ID: AtomicU64 = AtomicU64::new(1);

static ACTIVE_SESSION: once_cell::sync::Lazy<Arc<Mutex<Option<ActiveSession>>>> =
    once_cell::sync::Lazy::new(|| Arc::new(Mutex::new(None)));

pub async fn run_server(config: Arc<RwLock<AppConfig>>) -> Result<()> {
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

    let (mut write, mut read) = ws_stream.split();
    log::info!("客户端已连接: {peer}");

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

    {
        let mut guard = ACTIVE_SESSION.lock().await;
        if guard.as_ref().is_some_and(|session| session.id == session_id) {
            *guard = None;
        }
    }

    enqueue_release_all();
    Ok(())
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
        ClientMessage::Ping { seq, ts } => Some(ServerMessage::Pong { seq, ts }.to_json()),

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
                ServerMessage::Paired {
                    token: cfg.session_token.clone(),
                    pc_name: hostname(),
                }
                .to_json(),
            )
        }

        ClientMessage::Key {
            token,
            action,
            vk,
            mods,
        } => {
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_key(vk, action, mods);
            None
        }

        ClientMessage::Combo { token, vk, mods } => {
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_combo(vk, mods);
            None
        }

        ClientMessage::MouseMove { token, dx, dy } => {
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_mouse_move(dx, dy);
            None
        }

        ClientMessage::MouseClick { token, button, action } => {
            let cfg = config.read().await;
            if !cfg.verify_token(&token) {
                return Some(ServerMessage::err("AUTH_FAILED", "invalid token").to_json());
            }
            drop(cfg);

            enqueue_mouse_click(button, action);
            None
        }

        ClientMessage::MouseScroll { token, delta_y, delta_x } => {
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
