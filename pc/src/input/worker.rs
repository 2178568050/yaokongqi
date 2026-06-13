use std::sync::mpsc::{self, Sender};
use std::sync::OnceLock;
use std::thread;

use crate::protocol::{KeyAction, MouseButton};

use super::inject::{
    inject_combo, inject_key, inject_mouse_click, inject_mouse_move, inject_mouse_scroll,
    inject_text, release_all_stuck_inputs, system_shutdown,
};

enum InputCommand {
    Key {
        vk: u16,
        action: KeyAction,
        mods: u8,
    },
    Combo {
        vk: u16,
        mods: u8,
    },
    MouseMove {
        dx: i32,
        dy: i32,
    },
    MouseClick {
        button: MouseButton,
        action: KeyAction,
    },
    MouseScroll {
        delta_y: i32,
        delta_x: i32,
    },
    Text {
        text: String,
    },
    SystemShutdown,
    ReleaseAll,
}

static INPUT_TX: OnceLock<Sender<InputCommand>> = OnceLock::new();

pub fn init_worker() {
    let (tx, rx) = mpsc::channel();
    thread::Builder::new()
        .name("yaokongqi-input".into())
        .spawn(move || run_worker(rx))
        .expect("input worker thread");

    INPUT_TX.set(tx).ok();
}

fn run_worker(rx: mpsc::Receiver<InputCommand>) {
    while let Ok(cmd) = rx.recv() {
        match cmd {
            InputCommand::MouseMove { dx, dy } => {
                let (mut total_dx, mut total_dy) = (dx, dy);
                while let Ok(InputCommand::MouseMove { dx, dy }) = rx.try_recv() {
                    total_dx += dx;
                    total_dy += dy;
                }
                inject_mouse_move(total_dx, total_dy);
            }
            InputCommand::ReleaseAll => {
                release_all_stuck_inputs();
            }
            other => dispatch(other),
        }
    }
}

fn dispatch(cmd: InputCommand) {
    match cmd {
        InputCommand::Key { vk, action, mods } => inject_key(vk, action, mods),
        InputCommand::Combo { vk, mods } => inject_combo(vk, mods),
        InputCommand::MouseMove { dx, dy } => inject_mouse_move(dx, dy),
        InputCommand::MouseClick { button, action } => inject_mouse_click(button, action),
        InputCommand::MouseScroll { delta_y, delta_x } => inject_mouse_scroll(delta_y, delta_x),
        InputCommand::Text { text } => inject_text(&text),
        InputCommand::SystemShutdown => system_shutdown(),
        InputCommand::ReleaseAll => release_all_stuck_inputs(),
    }
}

fn send(cmd: InputCommand) {
    if let Some(tx) = INPUT_TX.get() {
        let _ = tx.send(cmd);
    }
}

pub fn enqueue_key(vk: u16, action: KeyAction, mods: u8) {
    send(InputCommand::Key { vk, action, mods });
}

pub fn enqueue_combo(vk: u16, mods: u8) {
    send(InputCommand::Combo { vk, mods });
}

pub fn enqueue_mouse_move(dx: i32, dy: i32) {
    if dx == 0 && dy == 0 {
        return;
    }
    send(InputCommand::MouseMove { dx, dy });
}

pub fn enqueue_mouse_click(button: MouseButton, action: KeyAction) {
    send(InputCommand::MouseClick { button, action });
}

pub fn enqueue_mouse_scroll(delta_y: i32, delta_x: i32) {
    if delta_y == 0 && delta_x == 0 {
        return;
    }
    send(InputCommand::MouseScroll { delta_y, delta_x });
}

pub fn enqueue_text(text: String) {
    if text.is_empty() {
        return;
    }
    send(InputCommand::Text { text });
}

pub fn enqueue_system_shutdown() {
    send(InputCommand::SystemShutdown);
}

pub fn enqueue_release_all() {
    send(InputCommand::ReleaseAll);
}
