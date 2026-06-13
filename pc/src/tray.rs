use std::sync::Arc;
use std::thread;
use std::time::Duration;

use anyhow::Result;
use tokio::sync::RwLock;
use tray_icon::{
    menu::{Menu, MenuEvent, MenuItem, PredefinedMenuItem},
    TrayIconBuilder,
};
use winit::event::Event;
use winit::event_loop::{ControlFlow, EventLoop, EventLoopProxy};

use crate::auth::AppConfig;
use crate::config::{APP_NAME, LISTEN_PORT, PIN_TTL_SECS};
use crate::icon::build_tray_icon;
use crate::legal;

enum TrayUserEvent {
    RefreshPin,
    CheckPinExpiry,
    About,
    Quit,
}

fn format_tooltip(local_ip: &str, pin: &str, expires_hm: &str) -> String {
    let minutes = PIN_TTL_SECS / 60;
    format!(
        "{APP_NAME}\n{local_ip}:{LISTEN_PORT}\nPIN: {pin}\n有效至 {expires_hm}（{minutes} 分钟，过期自动刷新）"
    )
}

pub fn run_tray(config: Arc<RwLock<AppConfig>>) -> Result<()> {
    let event_loop: EventLoop<TrayUserEvent> = EventLoop::with_user_event()?;
    let proxy: EventLoopProxy<TrayUserEvent> = event_loop.create_proxy();

    let pin_item = MenuItem::with_id("refresh_pin", "刷新配对码", true, None);
    let about_item = MenuItem::with_id("about", "关于与法律信息", true, None);
    let quit_item = MenuItem::with_id("quit", "退出", true, None);
    let menu = Menu::with_items(&[
        &pin_item,
        &PredefinedMenuItem::separator(),
        &about_item,
        &PredefinedMenuItem::separator(),
        &quit_item,
    ])?;

    let local_ip = local_ip_address::local_ip()
        .map(|ip| ip.to_string())
        .unwrap_or_else(|_| "未知".to_string());

    let cfg = config.blocking_read();
    let tooltip = format_tooltip(&local_ip, &cfg.pin, &cfg.pin_expires_local_hm());
    drop(cfg);

    let tray = TrayIconBuilder::new()
        .with_icon(build_tray_icon())
        .with_menu(Box::new(menu))
        .with_tooltip(tooltip)
        .with_title(APP_NAME)
        .build()?;

    let expiry_proxy = proxy.clone();
    thread::spawn(move || {
        loop {
            thread::sleep(Duration::from_secs(30));
            let _ = expiry_proxy.send_event(TrayUserEvent::CheckPinExpiry);
        }
    });

    let menu_proxy = proxy.clone();
    thread::spawn(move || {
        let menu_channel = MenuEvent::receiver();
        loop {
            if let Ok(event) = menu_channel.recv() {
                let user_event = match event.id.0.as_str() {
                    "refresh_pin" => Some(TrayUserEvent::RefreshPin),
                    "about" => Some(TrayUserEvent::About),
                    "quit" => Some(TrayUserEvent::Quit),
                    _ => None,
                };
                if let Some(ev) = user_event {
                    let _ = menu_proxy.send_event(ev);
                }
            }
        }
    });

    event_loop.run(move |event, elwt| {
        match event {
            Event::UserEvent(TrayUserEvent::RefreshPin) => {
                update_pin_tooltip(&config, &tray, &local_ip, true);
            }
            Event::UserEvent(TrayUserEvent::CheckPinExpiry) => {
                update_pin_tooltip(&config, &tray, &local_ip, false);
            }
            Event::UserEvent(TrayUserEvent::About) => {
                legal::show_about(env!("CARGO_PKG_VERSION"));
            }
            Event::UserEvent(TrayUserEvent::Quit) => {
                elwt.exit();
            }
            _ => {}
        }
        elwt.set_control_flow(ControlFlow::Wait);
    })?;

    Ok(())
}

fn update_pin_tooltip(
    config: &Arc<RwLock<AppConfig>>,
    tray: &tray_icon::TrayIcon,
    local_ip: &str,
    force: bool,
) {
    if let Ok(mut cfg) = config.try_write() {
        if force || !cfg.pin_still_valid() {
            cfg.refresh_pin();
            let _ = cfg.save();
            log::info!(
                "PIN 已刷新: {}，有效至 {}",
                cfg.pin,
                cfg.pin_expires_local_hm()
            );
        }
        let tip = format_tooltip(local_ip, &cfg.pin, &cfg.pin_expires_local_hm());
        let _ = tray.set_tooltip(Some(tip));
    }
}
