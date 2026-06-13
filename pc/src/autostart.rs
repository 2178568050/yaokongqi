//! Windows 当前用户开机自启（注册表 Run 项）

use anyhow::{bail, Context, Result};
use std::ffi::OsStr;
use std::os::windows::ffi::OsStrExt;
use windows::core::PCWSTR;
use windows::Win32::Foundation::{ERROR_FILE_NOT_FOUND, ERROR_SUCCESS, WIN32_ERROR};
use windows::Win32::System::Registry::{
    RegCloseKey, RegDeleteValueW, RegOpenKeyExW, RegSetValueExW, HKEY,
    HKEY_CURRENT_USER, KEY_SET_VALUE, REG_SZ,
};

const RUN_KEY: &str = r"Software\Microsoft\Windows\CurrentVersion\Run";
const VALUE_NAME: &str = "Yaokongqi";

fn wide(s: &str) -> Vec<u16> {
    OsStr::new(s).encode_wide().chain(Some(0)).collect()
}

fn check(status: WIN32_ERROR, action: &str) -> Result<()> {
    if status == ERROR_SUCCESS {
        Ok(())
    } else {
        bail!("{action}失败: {status:?}")
    }
}

fn exe_command() -> Result<String> {
    let exe = std::env::current_exe().context("无法获取程序路径")?;
    Ok(format!("\"{}\"", exe.display()))
}

/// 写入或删除 Run 项，与配置保持一致
pub fn set_enabled(enabled: bool) -> Result<()> {
    if enabled {
        enable()
    } else {
        disable()
    }
}

pub fn enable() -> Result<()> {
    write_run_value(&exe_command()?)
}

pub fn disable() -> Result<()> {
    delete_run_value()
}

fn write_run_value(command: &str) -> Result<()> {
    unsafe {
        let mut hkey = HKEY::default();
        check(
            RegOpenKeyExW(
                HKEY_CURRENT_USER,
                PCWSTR(wide(RUN_KEY).as_ptr()),
                0,
                KEY_SET_VALUE,
                &mut hkey,
            ),
            "打开 Run 注册表项",
        )?;

        let name = wide(VALUE_NAME);
        let value = wide(command);
        let bytes = std::slice::from_raw_parts(value.as_ptr().cast(), value.len() * 2);
        check(
            RegSetValueExW(
                hkey,
                PCWSTR(name.as_ptr()),
                0,
                REG_SZ,
                Some(bytes),
            ),
            "写入开机自启配置",
        )?;
        let _ = RegCloseKey(hkey);
        Ok(())
    }
}

fn delete_run_value() -> Result<()> {
    unsafe {
        let mut hkey = HKEY::default();
        check(
            RegOpenKeyExW(
                HKEY_CURRENT_USER,
                PCWSTR(wide(RUN_KEY).as_ptr()),
                0,
                KEY_SET_VALUE,
                &mut hkey,
            ),
            "打开 Run 注册表项",
        )?;

        let name = wide(VALUE_NAME);
        let status = RegDeleteValueW(hkey, PCWSTR(name.as_ptr()));
        let _ = RegCloseKey(hkey);

        if status == ERROR_SUCCESS || status == ERROR_FILE_NOT_FOUND {
            Ok(())
        } else {
            bail!("删除开机自启配置失败: {status:?}")
        }
    }
}
