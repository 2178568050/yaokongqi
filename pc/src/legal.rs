//! 应用内法律信息（与仓库 docs/、NOTICE 保持一致）
//! Copyright (C) 2026 遥控器项目作者 · AGPL-3.0

use std::ffi::OsStr;
use std::os::windows::ffi::OsStrExt;

use windows::core::PCWSTR;
use windows::Win32::Foundation::HWND;
use windows::Win32::UI::WindowsAndMessaging::{MessageBoxW, MB_ICONINFORMATION, MB_OK};

use crate::config::APP_NAME;

pub const CONTACT_EMAIL: &str = "2178568050@qq.com";

pub fn about_message(version: &str) -> String {
    format!(
        "{APP_NAME} (Yaokongqi) v{version}\n\
         Copyright © 2026 遥控器项目作者\n\n\
         【开源许可】\n\
         本软件以 GNU AGPL v3.0 开源。\n\
         您可自由使用、研究，并在遵守许可证的前提下 Fork 与二次开发\n\
         （衍生作品须同样开源并保留版权声明）。\n\n\
         【非商用保护】\n\
         未经版权持有人书面授权，禁止将本软件或其衍生作品用于商业目的，\n\
         包括但不限于：收费分发、嵌入商业产品/服务、营利性 SaaS 等。\n\n\
         【免责声明】\n\
         本软件按「现状」提供，使用风险由用户自行承担。\n\
         请在可信局域网内使用。详见仓库 docs/DISCLAIMER.md\n\n\
         【联系】\n\
         {CONTACT_EMAIL}\n\
         （问题反馈 / 商业授权 / 安全漏洞私下报告）"
    )
}

pub fn show_about(version: &str) {
    let body = to_wide(&about_message(version));
    let title = to_wide(&format!("关于 {APP_NAME}"));
    unsafe {
        let _ = MessageBoxW(
            HWND::default(),
            PCWSTR(body.as_ptr()),
            PCWSTR(title.as_ptr()),
            MB_OK | MB_ICONINFORMATION,
        );
    }
}

fn to_wide(value: &str) -> Vec<u16> {
    OsStr::new(value).encode_wide().chain(Some(0)).collect()
}
