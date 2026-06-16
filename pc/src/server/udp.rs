use std::net::SocketAddr;
use std::sync::atomic::{AtomicU16, Ordering};

use anyhow::Result;
use tokio::net::UdpSocket;

use crate::config::UDP_LISTEN_PORT;
use crate::input::{apply_gamepad_snapshot, gamepad_mode_active};
use crate::protocol::binary::{parse_udp_frame, BinaryGamepadFrame};
use crate::server::session::active_udp_session_key;

static LAST_UDP_SEQ: AtomicU16 = AtomicU16::new(0);

pub async fn run_udp_server() -> Result<()> {
    let addr = SocketAddr::from(([0, 0, 0, 0], UDP_LISTEN_PORT));
    let socket = UdpSocket::bind(addr).await?;
    log::info!("手柄 UDP 通道已启动 udp://0.0.0.0:{UDP_LISTEN_PORT}");

    let mut buf = [0u8; 64];
    loop {
        let (len, _peer) = socket.recv_from(&mut buf).await?;
        if !gamepad_mode_active() {
            continue;
        }
        let key = active_udp_session_key();
        let Some((seq, frame)) = parse_udp_frame(&buf[..len], key) else {
            continue;
        };
        if !accept_udp_seq(seq) {
            continue;
        }
        apply_binary_frame(frame);
    }
}

fn accept_udp_seq(seq: u16) -> bool {
    let prev = LAST_UDP_SEQ.load(Ordering::Relaxed);
    if prev == 0 {
        LAST_UDP_SEQ.store(seq, Ordering::Relaxed);
        return true;
    }
    let delta = seq.wrapping_sub(prev);
    if delta == 0 || delta > 0x8000 {
        return false;
    }
    LAST_UDP_SEQ.store(seq, Ordering::Relaxed);
    true
}

pub fn reset_udp_seq() {
    LAST_UDP_SEQ.store(0, Ordering::Relaxed);
}

fn apply_binary_frame(frame: BinaryGamepadFrame) {
    apply_gamepad_snapshot(frame.snapshot());
}
