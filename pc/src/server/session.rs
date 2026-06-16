use std::sync::atomic::{AtomicU32, Ordering};

use rand::Rng;

static UDP_SESSION_KEY: AtomicU32 = AtomicU32::new(0);

pub fn rotate_udp_session_key() -> u32 {
    let mut key = rand::thread_rng().gen::<u32>();
    if key == 0 {
        key = 1;
    }
    UDP_SESSION_KEY.store(key, Ordering::SeqCst);
    key
}

pub fn clear_udp_session_key() {
    UDP_SESSION_KEY.store(0, Ordering::SeqCst);
}

pub fn active_udp_session_key() -> u32 {
    UDP_SESSION_KEY.load(Ordering::SeqCst)
}
