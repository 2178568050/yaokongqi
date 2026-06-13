# 遥控器 — 通信协议

## 概述

- 传输：局域网 WiFi，WebSocket
- 监听端口：`10825`（配置常量 `10230825` 为应用魔数，见下方说明）
- 配对：6 位 PIN，**无 QR**
- 编码：UTF-8 JSON，单行帧

> **端口说明**：用户指定标识 `10230825` 超出 TCP 端口上限 65535，因此网络端口使用 `10825`，
> 握手时通过 `magic` 字段校验应用身份。

## 连接地址

```
ws://<PC局域网IP>:10825/ws
```

## 消息类型

### pair — 配对请求（手机 → PC）

```json
{"type":"pair","magic":10230825,"pin":"482913","device":"Pixel-7"}
```

### paired — 配对成功（PC → 手机）

```json
{"type":"paired","token":"<session_token>","pc_name":"DESKTOP-XXX"}
```

### key — 按键（手机 → PC）

```json
{"type":"key","token":"<session_token>","action":"tap","vk":65,"mods":0}
```

- `action`: `down` | `up` | `tap`
- `vk`: Windows Virtual-Key 码
- `mods` 位掩码: `1=Shift` `2=Ctrl` `4=Alt` `8=Win`

### combo — 组合键（手机 → PC）

```json
{"type":"combo","token":"<session_token>","vk":67,"mods":2}
```

PC 端按 mods 按下修饰键 → 主键 tap → 释放修饰键。

### ping / pong — 心跳

```json
{"type":"ping"}
{"type":"pong"}
```

### error — 错误（PC → 手机）

```json
{"type":"error","code":"AUTH_FAILED","msg":"invalid token"}
```

错误码：`AUTH_FAILED` | `INVALID_PIN` | `INVALID_MSG` | `NOT_PAIRED`

## 修饰键位掩码

| 位 | 修饰键 |
|----|--------|
| 1  | Shift  |
| 2  | Ctrl   |
| 4  | Alt    |
| 8  | Win    |

## 常用 VK 对照

| 键 | VK |
|----|-----|
| A–Z | 0x41–0x5A |
| 0–9 | 0x30–0x39 |
| Enter | 0x0D |
| Backspace | 0x08 |
| Tab | 0x09 |
| Esc | 0x1B |
| Space | 0x20 |
| ←↑→↓ | 0x25–0x28 |
| F1–F12 | 0x70–0x7B |
