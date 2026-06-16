//! 应用常量

/// TCP 监听端口（取自标识 10230825 的有效后缀 10825）
pub const LISTEN_PORT: u16 = 10825;

/// 手柄 UDP 数据通道端口
pub const UDP_LISTEN_PORT: u16 = 10826;

/// 协议魔数，用于识别「遥控器」客户端
pub const APP_MAGIC: u32 = 10230825;

/// WebSocket 路径
pub const WS_PATH: &str = "/ws";

/// PIN 有效期（秒）
pub const PIN_TTL_SECS: i64 = 300;

/// 应用显示名
pub const APP_NAME: &str = "遥控器";
