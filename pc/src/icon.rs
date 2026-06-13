use tray_icon::Icon;

/// 32×32 托盘图标：低饱和蓝底 + 白色键位线条
pub fn build_tray_icon() -> Icon {
    const SIZE: u32 = 32;
    let mut rgba = vec![0u8; (SIZE * SIZE * 4) as usize];

    for y in 0..SIZE {
        for x in 0..SIZE {
            let idx = ((y * SIZE + x) * 4) as usize;
            let edge = x < 3 || y < 3 || x >= SIZE - 3 || y >= SIZE - 3;
            let corner = (x < 6 && y < 6)
                || (x >= SIZE - 6 && y < 6)
                || (x < 6 && y >= SIZE - 6)
                || (x >= SIZE - 6 && y >= SIZE - 6);

            if edge && !corner {
                rgba[idx..idx + 4].copy_from_slice(&[0, 0, 0, 0]);
            } else {
                rgba[idx..idx + 4].copy_from_slice(&[0x25, 0x63, 0xEB, 0xFF]);
            }
        }
    }

    // 三条白色横线，象征键盘
    for &(y, x0, x1) in &[(11, 9, 23), (16, 9, 23), (21, 9, 20)] {
        for x in x0..=x1 {
            let idx = ((y * SIZE + x) * 4) as usize;
            rgba[idx..idx + 4].copy_from_slice(&[0xFA, 0xFA, 0xF9, 0xFF]);
        }
    }

    Icon::from_rgba(rgba, SIZE, SIZE).expect("tray icon")
}
