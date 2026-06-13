package com.yaokongqi.remote

data class ParsedHost(
    val host: String,
    val port: Int = AppConfig.LISTEN_PORT,
)

object AppConfig {
    /** TCP 端口（取自标识 10230825 的有效后缀 10825） */
    const val LISTEN_PORT = 10825

    /** 协议魔数 */
    const val APP_MAGIC = 10230825

    const val WS_PATH = "/ws"

    /** 解析用户输入：支持纯 IP、IP:端口、粘贴 ws:// 地址等 */
    fun parseHost(input: String): ParsedHost {
        var raw = input.trim()
        if (raw.isEmpty()) return ParsedHost("", LISTEN_PORT)

        raw = raw.removePrefix("ws://")
            .removePrefix("wss://")
            .removePrefix("http://")
            .removePrefix("https://")
        raw = raw.substringBefore('/').substringBefore('?').trim()

        // 去掉用户误输入的末尾 :10825/ws 等
        if (raw.endsWith(WS_PATH, ignoreCase = true)) {
            raw = raw.dropLast(WS_PATH.length).trimEnd('/')
        }

        val colon = raw.lastIndexOf(':')
        if (colon > 0) {
            val hostPart = raw.substring(0, colon).trim()
            val portPart = raw.substring(colon + 1).trim()
            val port = portPart.toIntOrNull()
            if (port != null && port in 1..65535 && hostPart.isNotEmpty()) {
                return ParsedHost(hostPart, port)
            }
        }

        return ParsedHost(raw, LISTEN_PORT)
    }

    fun wsUrl(input: String): String {
        val parsed = parseHost(input)
        require(parsed.host.isNotEmpty()) { "IP 地址不能为空" }
        return "ws://${parsed.host}:${parsed.port}$WS_PATH"
    }
}
