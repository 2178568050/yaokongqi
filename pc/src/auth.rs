use std::path::PathBuf;

use anyhow::{Context, Result};
use chrono::{DateTime, Utc};
use rand::Rng;
use serde::{Deserialize, Serialize};

use crate::config::PIN_TTL_SECS;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    pub session_token: String,
    pub pin: String,
    pub pin_expires_at: DateTime<Utc>,
    pub autostart: bool,
}

impl Default for AppConfig {
    fn default() -> Self {
        let mut rng = rand::thread_rng();
        let mut cfg = Self {
            session_token: generate_token(&mut rng),
            pin: String::new(),
            pin_expires_at: Utc::now(),
            autostart: false,
        };
        cfg.refresh_pin();
        cfg
    }
}

impl AppConfig {
    pub fn config_path() -> Result<PathBuf> {
        let dir = dirs::config_dir()
            .context("无法定位配置目录")?
            .join("Yaokongqi");
        std::fs::create_dir_all(&dir)?;
        Ok(dir.join("config.json"))
    }

    pub fn load() -> Result<Self> {
        let path = Self::config_path()?;
        let mut cfg = if path.exists() {
            let data = std::fs::read_to_string(&path)?;
            serde_json::from_str(&data)?
        } else {
            Self::default()
        };

        // 启动时始终生成新 PIN，避免配置文件中的旧码造成误导
        cfg.refresh_pin();
        cfg.save()?;
        Ok(cfg)
    }

    pub fn save(&self) -> Result<()> {
        let path = Self::config_path()?;
        let data = serde_json::to_string_pretty(self)?;
        std::fs::write(path, data)?;
        Ok(())
    }

    pub fn refresh_pin(&mut self) {
        let mut rng = rand::thread_rng();
        self.pin = format!("{:06}", rng.gen_range(0..1_000_000));
        self.pin_expires_at = Utc::now() + chrono::Duration::seconds(PIN_TTL_SECS);
    }

    pub fn pin_still_valid(&self) -> bool {
        Utc::now() < self.pin_expires_at
    }

    pub fn pin_valid(&self, pin: &str) -> bool {
        self.pin_still_valid() && self.pin == pin
    }

    pub fn verify_token(&self, token: &str) -> bool {
        !token.is_empty() && token == self.session_token
    }

    pub fn pin_expires_local_hm(&self) -> String {
        use chrono::Local;
        self.pin_expires_at.with_timezone(&Local).format("%H:%M").to_string()
    }
}

fn generate_token(rng: &mut impl Rng) -> String {
    const CHARS: &[u8] = b"abcdefghijklmnopqrstuvwxyz0123456789";
    (0..32)
        .map(|_| {
            let idx = rng.gen_range(0..CHARS.len());
            CHARS[idx] as char
        })
        .collect()
}
