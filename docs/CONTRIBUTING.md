# 贡献指南

感谢你对 Yaokongqi（遥控器）的关注！

## 如何贡献

1. **Fork** 本仓库；
2. 创建特性分支：`git checkout -b feature/your-feature`；
3. 提交改动：`git commit -m "feat: 简要说明"`；
4. 推送并发起 **Pull Request**。

## 提交规范

- 提交信息使用中文或英文均可，但需清晰描述「做了什么、为什么」；
- 单次 PR 聚焦一个主题，避免无关改动；
- 若涉及 UI/协议变更，请更新对应文档（`docs/`、`README.md`）。

## 代码约定

| 部分 | 约定 |
|------|------|
| Android | Kotlin + Compose，包名 `com.yaokongqi.remote` |
| PC | Rust 2021 edition，`cargo fmt` / `cargo clippy` |
| 协议 | 修改 `docs/protocol.md` 并保证双端兼容 |

## 法律与许可

- 你贡献的代码将以 **AGPL-3.0** 授权；
- 提交 PR 即表示你拥有贡献内容的合法权利，并同意上述许可；
- 请勿提交密钥、个人 IP、PIN 等敏感信息。

## 报告问题

- **Bug**：请提供机型/系统版本、复现步骤、期望与实际行为；
- **安全漏洞**：请私下邮件 **2178568050@qq.com**，勿公开披露可利用细节。

## 行为准则

请保持尊重、建设性的交流。骚扰、歧视、人身攻击不被接受。

---

再次感谢你的贡献！
