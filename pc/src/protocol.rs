use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum ClientMessage {
    Pair {
        magic: u32,
        pin: String,
        device: String,
    },
    Key {
        token: String,
        action: KeyAction,
        vk: u16,
        #[serde(default)]
        mods: u8,
    },
    Combo {
        token: String,
        vk: u16,
        #[serde(default)]
        mods: u8,
    },
    #[serde(rename = "mouse_move")]
    MouseMove {
        token: String,
        dx: i32,
        dy: i32,
    },
    #[serde(rename = "mouse_click")]
    MouseClick {
        token: String,
        button: MouseButton,
        action: KeyAction,
    },
    #[serde(rename = "mouse_scroll")]
    MouseScroll {
        token: String,
        #[serde(default)]
        delta_y: i32,
        #[serde(default)]
        delta_x: i32,
    },
    #[serde(rename = "text_input")]
    TextInput {
        token: String,
        text: String,
    },
    System {
        token: String,
        action: SystemAction,
    },
    Ping {
        #[serde(default)]
        seq: u32,
        #[serde(default)]
        ts: u64,
    },
    #[serde(rename = "input_mode")]
    InputMode {
        token: String,
        mode: RemoteInputMode,
        #[serde(default = "default_gamepad_hz")]
        hz: u8,
    },
    #[serde(rename = "gamepad")]
    Gamepad {
        token: String,
        lx: i16,
        ly: i16,
        rx: i16,
        ry: i16,
        #[serde(default)]
        lt: u8,
        #[serde(default)]
        rt: u8,
        #[serde(default)]
        buttons: u16,
    },
}

fn default_gamepad_hz() -> u8 {
    180
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum RemoteInputMode {
    KeyboardMouse,
    Gamepad,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum SystemAction {
    Shutdown,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum MouseButton {
    Left,
    Right,
    Middle,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum KeyAction {
    Down,
    Up,
    Tap,
    Double,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum ServerMessage {
    Paired {
        token: String,
        pc_name: String,
    },
    Pong {
        #[serde(default)]
        seq: u32,
        #[serde(default)]
        ts: u64,
    },
    Error {
        code: String,
        msg: String,
    },
}

impl ServerMessage {
    pub fn err(code: &str, msg: &str) -> Self {
        Self::Error {
            code: code.to_string(),
            msg: msg.to_string(),
        }
    }

    pub fn to_json(&self) -> String {
        serde_json::to_string(self).unwrap_or_else(|_| {
            r#"{"type":"error","code":"INTERNAL","msg":"serialize failed"}"#.to_string()
        })
    }
}
