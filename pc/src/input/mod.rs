mod inject;
mod worker;

pub use worker::{
    enqueue_combo, enqueue_key, enqueue_mouse_click, enqueue_mouse_move, enqueue_mouse_scroll,
    enqueue_release_all, enqueue_system_shutdown, enqueue_text, init_worker,
};
