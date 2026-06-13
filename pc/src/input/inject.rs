use crate::protocol::{KeyAction, MouseButton};
use anyhow::{Context, Result};
use log::warn;
use std::thread;
use std::time::Duration;
use windows::Win32::Foundation::{HANDLE, HGLOBAL, HWND};
use windows::Win32::System::DataExchange::{CloseClipboard, EmptyClipboard, OpenClipboard, SetClipboardData};
use windows::Win32::System::Memory::{GlobalAlloc, GlobalLock, GlobalUnlock, GMEM_MOVEABLE};
use windows::Win32::System::Ole::CF_UNICODETEXT;
use windows::Win32::System::Shutdown::{ExitWindowsEx, EWX_POWEROFF, EWX_SHUTDOWN, SHUTDOWN_REASON};
use windows::Win32::UI::Input::KeyboardAndMouse::{
    SendInput, INPUT, INPUT_0, INPUT_KEYBOARD, INPUT_MOUSE, KEYBD_EVENT_FLAGS, KEYEVENTF_KEYUP,
    KEYEVENTF_UNICODE, MOUSEEVENTF_HWHEEL, MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP,
    MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP, MOUSEEVENTF_MOVE, MOUSEEVENTF_RIGHTDOWN,
    MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_WHEEL, MOUSEINPUT, MOUSE_EVENT_FLAGS, VIRTUAL_KEY,
};

const MOD_SHIFT: u8 = 1;
const MOD_CTRL: u8 = 2;
const MOD_ALT: u8 = 4;
const MOD_WIN: u8 = 8;

fn vk_from_mod(bit: u8) -> Option<u16> {
    match bit {
        MOD_SHIFT => Some(0x10),
        MOD_CTRL => Some(0x11),
        MOD_ALT => Some(0x12),
        MOD_WIN => Some(0x5B),
        _ => None,
    }
}

fn send_vk(vk: u16, down: bool) {
    let flags = if down {
        KEYBD_EVENT_FLAGS(0)
    } else {
        KEYEVENTF_KEYUP
    };

    let input = INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 {
            ki: windows::Win32::UI::Input::KeyboardAndMouse::KEYBDINPUT {
                wVk: VIRTUAL_KEY(vk),
                wScan: 0,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };

    unsafe {
        let sent = SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
        if sent == 0 {
            warn!("SendInput vk={vk} down={down} failed (UIPI/权限?)");
        }
    }
}

fn apply_mods(mods: u8, down: bool) {
    for bit in [MOD_SHIFT, MOD_CTRL, MOD_ALT, MOD_WIN] {
        if mods & bit != 0 {
            if let Some(vk) = vk_from_mod(bit) {
                send_vk(vk, down);
            }
        }
    }
}

pub(crate) fn inject_key(vk: u16, action: KeyAction, mods: u8) {
    match action {
        KeyAction::Down => {
            apply_mods(mods, true);
            send_vk(vk, true);
        }
        KeyAction::Up => {
            send_vk(vk, false);
            apply_mods(mods, false);
        }
        KeyAction::Tap | KeyAction::Double => {
            apply_mods(mods, true);
            send_vk(vk, true);
            send_vk(vk, false);
            apply_mods(mods, false);
            if matches!(action, KeyAction::Double) {
                apply_mods(mods, true);
                send_vk(vk, true);
                send_vk(vk, false);
                apply_mods(mods, false);
            }
        }
    }
}

pub(crate) fn inject_combo(vk: u16, mods: u8) {
    inject_key(vk, KeyAction::Tap, mods);
}

fn send_mouse(flags: MOUSE_EVENT_FLAGS, dx: i32, dy: i32, data: u32) {
    let input = INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: INPUT_0 {
            mi: MOUSEINPUT {
                dx,
                dy,
                mouseData: data,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };

    unsafe {
        let sent = SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
        if sent == 0 {
            warn!("SendInput mouse flags={:?} failed", flags);
        }
    }
}

/// 禁止 Windows 合并鼠标移动事件，避免指针「跳帧」。
const MOUSEEVENTF_MOVE_NOCOALESCE: MOUSE_EVENT_FLAGS = MOUSE_EVENT_FLAGS(0x2000);

pub(crate) fn inject_mouse_move(dx: i32, dy: i32) {
    if dx == 0 && dy == 0 {
        return;
    }
    send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_MOVE_NOCOALESCE, dx, dy, 0);
}

fn send_mouse_button(button: MouseButton, down: bool) {
    let flags = match (button, down) {
        (MouseButton::Left, true) => MOUSEEVENTF_LEFTDOWN,
        (MouseButton::Left, false) => MOUSEEVENTF_LEFTUP,
        (MouseButton::Right, true) => MOUSEEVENTF_RIGHTDOWN,
        (MouseButton::Right, false) => MOUSEEVENTF_RIGHTUP,
        (MouseButton::Middle, true) => MOUSEEVENTF_MIDDLEDOWN,
        (MouseButton::Middle, false) => MOUSEEVENTF_MIDDLEUP,
    };
    send_mouse(flags, 0, 0, 0);
}

pub(crate) fn inject_mouse_click(button: MouseButton, action: KeyAction) {
    match action {
        KeyAction::Down => send_mouse_button(button, true),
        KeyAction::Up => send_mouse_button(button, false),
        KeyAction::Tap => {
            send_mouse_button(button, true);
            send_mouse_button(button, false);
        }
        KeyAction::Double => {
            send_mouse_button(button, true);
            send_mouse_button(button, false);
            send_mouse_button(button, true);
            send_mouse_button(button, false);
        }
    }
}

pub(crate) fn inject_mouse_scroll(delta_y: i32, delta_x: i32) {
    if delta_y != 0 {
        send_mouse(MOUSEEVENTF_WHEEL, 0, 0, delta_y as i16 as u32);
    }
    if delta_x != 0 {
        send_mouse(MOUSEEVENTF_HWHEEL, 0, 0, delta_x as i16 as u32);
    }
}

fn send_unicode_char(ch: u16) {
    for down in [true, false] {
        let flags = if down {
            KEYEVENTF_UNICODE
        } else {
            KEYEVENTF_UNICODE | KEYEVENTF_KEYUP
        };
        let input = INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: windows::Win32::UI::Input::KeyboardAndMouse::KEYBDINPUT {
                    wVk: VIRTUAL_KEY(0),
                    wScan: ch,
                    dwFlags: flags,
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        };
        unsafe {
            SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
        }
    }
}

pub(crate) fn inject_text(text: &str) {
    if text.is_empty() {
        return;
    }
    if let Err(e) = paste_text_via_clipboard(text) {
        warn!("剪贴板粘贴失败 ({e})，回退逐字 Unicode 注入");
        inject_text_unicode_fallback(text);
    }
}

fn paste_text_via_clipboard(text: &str) -> Result<()> {
    set_clipboard_utf16(text).context("写入剪贴板")?;
    thread::sleep(Duration::from_millis(20));
    inject_key(0x56, KeyAction::Tap, MOD_CTRL);
    Ok(())
}

fn set_clipboard_utf16(text: &str) -> Result<()> {
    let mut wide: Vec<u16> = text.encode_utf16().collect();
    wide.push(0);
    let byte_len = wide.len() * std::mem::size_of::<u16>();

    unsafe {
        OpenClipboard(HWND::default()).context("OpenClipboard")?;
        let _ = EmptyClipboard();

        let hglob: HGLOBAL = GlobalAlloc(GMEM_MOVEABLE, byte_len).context("GlobalAlloc")?;
        let dest = GlobalLock(hglob) as *mut u16;
        if dest.is_null() {
            let _ = CloseClipboard();
            anyhow::bail!("GlobalLock 失败");
        }
        std::ptr::copy_nonoverlapping(wide.as_ptr(), dest, wide.len());
        let _ = GlobalUnlock(hglob);

        SetClipboardData(CF_UNICODETEXT.0 as u32, HANDLE(hglob.0)).context("SetClipboardData")?;
        CloseClipboard().context("CloseClipboard")?;
    }

    Ok(())
}

fn inject_text_unicode_fallback(text: &str) {
    for ch in text.chars() {
        match ch {
            '\n' | '\r' => inject_key(0x0D, KeyAction::Tap, 0),
            '\t' => inject_key(0x09, KeyAction::Tap, 0),
            _ => {
                let mut buf = [0u16; 2];
                for unit in ch.encode_utf16(&mut buf) {
                    send_unicode_char(*unit);
                    thread::sleep(Duration::from_millis(8));
                }
            }
        }
    }
}

pub(crate) fn system_shutdown() {
    unsafe {
        let _ = ExitWindowsEx(EWX_SHUTDOWN | EWX_POWEROFF, SHUTDOWN_REASON(0));
    }
}

/// 客户端断开或连接被替换时，释放可能卡住的修饰键/鼠标键。
pub(crate) fn release_all_stuck_inputs() {
    for vk in [0x10u16, 0x11, 0x12, 0x5B, 0x5C] {
        send_vk(vk, false);
    }
    for button in [MouseButton::Left, MouseButton::Right, MouseButton::Middle] {
        send_mouse_button(button, false);
    }
    log::info!("已释放可能卡住的按键/鼠标键");
}
