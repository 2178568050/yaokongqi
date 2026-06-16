//! 手柄二进制帧（WebSocket binary / UDP 共用魔数，布局按长度区分）

use crate::input::GamepadSnapshot;

pub const MAGIC: u16 = 0x4B59; // LE bytes b'Y', b'K'
pub const VERSION: u8 = 1;
pub const FLAG_UDP: u8 = 1;

pub const WS_FRAME_LEN: usize = 18;
pub const UDP_FRAME_LEN: usize = 22;

pub const GAMEPAD_HZ_MIN: u16 = 60;
pub const GAMEPAD_HZ_MAX: u16 = 500;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BinaryGamepadFrame {
    pub seq: u16,
    pub lx: i16,
    pub ly: i16,
    pub rx: i16,
    pub ry: i16,
    pub lt: u8,
    pub rt: u8,
    pub buttons: u16,
}

impl BinaryGamepadFrame {
    pub fn snapshot(&self) -> GamepadSnapshot {
        GamepadSnapshot {
            thumb_lx: self.lx,
            thumb_ly: self.ly,
            thumb_rx: self.rx,
            thumb_ry: self.ry,
            left_trigger: self.lt,
            right_trigger: self.rt,
            buttons: self.buttons,
        }
    }
}

pub fn parse_ws_frame(data: &[u8]) -> Option<BinaryGamepadFrame> {
    if data.len() != WS_FRAME_LEN {
        return None;
    }
    let magic = u16::from_le_bytes([data[0], data[1]]);
    if magic != MAGIC || data[2] != VERSION {
        return None;
    }
    Some(read_payload(data, u16::from_le_bytes([data[4], data[5]])))
}

pub fn parse_udp_frame(data: &[u8], expected_key: u32) -> Option<(u16, BinaryGamepadFrame)> {
    if data.len() != UDP_FRAME_LEN || expected_key == 0 {
        return None;
    }
    let magic = u16::from_le_bytes([data[0], data[1]]);
    if magic != MAGIC || data[2] != VERSION || data[3] != FLAG_UDP {
        return None;
    }
    let key = u32::from_le_bytes([data[4], data[5], data[6], data[7]]);
    if key != expected_key {
        return None;
    }
    let seq = u16::from_le_bytes([data[8], data[9]]);
    Some((seq, read_payload(data, seq)))
}

fn read_payload(data: &[u8], seq: u16) -> BinaryGamepadFrame {
    let base = if data.len() == UDP_FRAME_LEN { 10 } else { 6 };
    BinaryGamepadFrame {
        seq,
        lx: i16::from_le_bytes([data[base], data[base + 1]]),
        ly: i16::from_le_bytes([data[base + 2], data[base + 3]]),
        rx: i16::from_le_bytes([data[base + 4], data[base + 5]]),
        ry: i16::from_le_bytes([data[base + 6], data[base + 7]]),
        lt: data[base + 8],
        rt: data[base + 9],
        buttons: u16::from_le_bytes([data[base + 10], data[base + 11]]),
    }
}

pub fn clamp_gamepad_hz(hz: u16) -> u16 {
    hz.clamp(GAMEPAD_HZ_MIN, GAMEPAD_HZ_MAX)
}
