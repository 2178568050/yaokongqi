//! Windows GUI 程序：Release 模式下不弹出控制台窗口
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod auth;
mod config;
mod icon;
mod input;
mod legal;
mod protocol;
mod server;
mod tray;

use std::fs::OpenOptions;
use std::sync::Arc;

use anyhow::Result;
use tokio::sync::RwLock;

use auth::AppConfig;
use input::init_worker;

fn main() -> Result<()> {
    init_logging();
    init_worker();

    let config = Arc::new(RwLock::new(AppConfig::load()?));
    let cfg = config.blocking_read();
    log::info!(
        "{} 已就绪 | 端口 {} | PIN: {}（有效至 {}，过期自动刷新）",
        config::APP_NAME,
        config::LISTEN_PORT,
        cfg.pin,
        cfg.pin_expires_local_hm(),
    );
    drop(cfg);

    let server_cfg = Arc::clone(&config);
    std::thread::spawn(move || {
        let rt = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .expect("tokio runtime");
        rt.block_on(async {
            if let Err(e) = server::run_server(server_cfg).await {
                log::error!("服务异常: {e}");
            }
        });
    });

    tray::run_tray(config)
}

fn init_logging() {
    let mut builder =
        env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"));

    #[cfg(not(debug_assertions))]
    if let Some(path) = log_file_path() {
        if let Ok(file) = OpenOptions::new().create(true).append(true).open(&path) {
            builder.target(env_logger::Target::Pipe(Box::new(file)));
        }
    }

    builder.init();
}

#[cfg(not(debug_assertions))]
fn log_file_path() -> Option<std::path::PathBuf> {
    AppConfig::config_path()
        .ok()
        .and_then(|p| p.parent().map(|d| d.to_path_buf()))
        .map(|d| d.join("yaokongqi.log"))
}
