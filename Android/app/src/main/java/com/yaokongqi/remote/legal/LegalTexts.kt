package com.yaokongqi.remote.legal

/**
 * 应用内法律信息文案（与仓库 docs/、NOTICE 保持一致）。
 * Copyright (C) 2026 遥控器项目作者 · AGPL-3.0
 */
object LegalTexts {
    const val APP_NAME = "遥控器"
    const val APP_NAME_EN = "Yaokongqi"
    const val VERSION = "0.1.0"
    const val COPYRIGHT = "Copyright © 2026 遥控器项目作者"
    const val LICENSE_NAME = "GNU Affero General Public License v3.0 (AGPL-3.0)"
    const val CONTACT_EMAIL = "2178568050@qq.com"

    val LICENSE_SUMMARY: String =
        "本软件以 AGPL-3.0 开源。您可自由使用、研究，并在遵守许可证的前提下 Fork 与二次开发（衍生作品须同样开源并保留版权声明）。\n\n" +
        "未经版权持有人书面授权，禁止将本软件或其衍生作品用于商业目的，包括但不限于：收费分发、嵌入商业产品/服务、营利性 SaaS、应用商店付费上架等。\n\n" +
        "Fork 或公开二次发布时，请修改应用名称与图标，避免与「遥控器」官方版本混淆。"

    val DISCLAIMER_SUMMARY: String =
        "本软件按「现状」提供，不提供任何明示或暗示的保证。使用本软件远程控制 PC 输入所产生的风险（含误操作、网络安全、兼容性等）由用户自行承担。\n\n" +
        "请在可信局域网内使用，勿用于未授权访问他人设备。作者不对因使用本软件造成的直接或间接损失承担责任。"

    val USAGE_SUMMARY: String =
        "· 仅限自己拥有或已授权的设备与网络\n" +
        "· 不得用于违法、骚扰、恶意控制等行为\n" +
        "· 商用、闭源集成或大规模营利用途须邮件联系作者取得授权\n" +
        "· 二次开发请遵守 AGPL-3.0 与仓库 docs/USAGE-POLICY.md"

    const val REPO_HINT = "完整法律文档见 GitHub 仓库 docs/ 目录"
}
