# 开发者文档

## 项目结构

```
.
├── android/          # Android 客户端（Kotlin + Jetpack Compose）
├── pc/               # Windows PC 端（Rust，系统托盘 + WebSocket）
├── docs/             # 文档（协议、法律、贡献）
├── licenses/         # 许可证全文
├── dist/             # 发布包（本地构建，不纳入 Git）
└── LICENSE           # AGPL-3.0 + 项目版权说明
```

## 环境要求

| 组件 | 要求 |
|------|------|
| Android | JDK 17，Android Studio Ladybug+，SDK 35 |
| PC | Rust stable，Windows 10/11 x64 |
| 网络 | 手机与 PC 同一局域网 |

## Android 构建

```powershell
cd android
.\gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

## PC 构建

```powershell
cd pc
cargo build --release
# 输出：target/release/yaokongqi.exe
```

## 协议与端口

- WebSocket：`ws://<PC_IP>:10825/ws`
- 魔数：`10230825`
- 详见 [protocol.md](protocol.md)

## 架构概览

```
Android App                    PC (Rust)
───────────                    ─────────
ConnectionManager  ◄──WS──►   ws.rs (tokio)
TrackpadGestures               input/worker.rs → inject.rs
LayoutPresetStore              auth (PIN)
MainViewModel                  tray (PIN 显示)
```

## 调试建议

- PC 日志：`%APPDATA%\Yaokongqi\yaokongqi.log`
- Android：Logcat 过滤 `yaokongqi` 或包名
- 连接失败：检查防火墙、IP 是否为局域网地址

## 二次开发提示

- 修改协议时须同步 `android/.../protocol/Protocol.kt` 与 `pc/src/protocol.rs`；
- 内置布局预设见 `ScenarioLayoutPresets.kt`；
- 法律文案集中：`android/.../LegalTexts.kt`、`pc/src/legal.rs`。

## 许可证

源码遵循 **AGPL-3.0**。商用请联系 **2178568050@qq.com**。
