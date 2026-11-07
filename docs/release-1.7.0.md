# 盐筋 v1.7.0 · 原生 iOS 与英语／简中／繁中

新增 **Swift / SwiftUI 原生 iPhone、iPad 版本**，同时为 Android 和 iOS 加入 English、简体中文、繁體中文界面；可跟随系统，也可在设置里独立切换。界面语言不改变经典原文和所选译本。

- iOS 使用 Apple 原生导航、搜索标签、列表、表单、弹层与系统分享；在支持的系统上呈现 Liquid Glass，并支持深色、Dynamic Type 和阿拉伯文 RTL。
- 两端保留全部 16 套文本与离线检索资源。iOS 支持出处、原文、常用表达、中文句意检索、上下文、版本对照、收藏及各经典阅读位置。
- Android 的设置、提示、搜索、更新与启动恢复已本地化，并保留原包名、图标和签名，可直接覆盖安装以保留本机数据。
- 英语界面与模型语言能力独立。中文 BGE 模型不保证英语句意检索效果，英语文本请使用原文搜索或英文出处。iOS 使用独立 Swift 排序实现，不能套用 Android 历史评估分数。

## 下载与安装

| 附件 | 用途 |
| --- | --- |
| `yanjing-1.7.0-arm64-v8a.apk` | Android ARM64 手机，Android 8.0+ |
| `yanjing-1.7.0.apk` | Android 通用包，含四种架构 |
| `yanjing-1.7.0-ios-unsigned.ipa` | iPhone / iPad arm64，iOS 18+，由侧载工具在本机签名 |
| `yanjing-1.7.0-ios-simulator.zip` | Mac 上的 iOS 模拟器应用，不能装到 iPhone |
| `yanjing-1.7.0-source.zip` | 两端源码、原始数据、索引和许可证；iOS 按文档生成工程 |
| `yanjing-xcode-project.zip` | CI 生成的 Xcode 工程；配合源码、资源准备脚本使用 |

**无需付费 Apple Developer 会员的侧载方式**：将 IPA 导入 AltStore Classic / Sideloadly 等工具，用自己的免费 Apple ID 在本机完成个人签名。IPA 未签名，不能直接点击安装；免费签名通常需要每 7 天刷新，并受 Apple 的免费账户限制。[AltStore Windows 安装教程](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) · [刷新与账户限制](https://faq.altstore.io/altstore-classic/your-altstore)。

原始经典文本与编号保持不变，拉丁文按所附 CrossWire 分节映射对照，未映射部分仍可阅读和搜索原文。经典来源和各自许可证与应用代码许可分开；CBETA 文本及衍生索引包含非商业、相同方式共享条件。完整说明在应用内及源码 `third_party/` 中。

## 验证

Android 检查包含实际模型、原有完整回归、英／简中／繁中切换、收藏与历史保留、启动恢复、更新、Release 签名和压缩资源。iOS 检查包含全文、出处、合并分节、拉丁诗篇映射、与 Android 一致的分词样例、真实 ONNX 模型命中、数据持久化及三语言模拟器界面；设备归档另外验证 arm64 Mach-O、全部资源哈希和三种语言包。确切测试数量、Xcode 版本、提交和 SHA-256 见 `verification.json` 与 `ios-verification.json`。

**验证边界**：未进行物理 iPhone 侧载安装或物理 Android 设备测试。未签名 IPA 由用户自己的侧载工具签名后使用；不包含共享企业证书。

---

**English**: Native SwiftUI iOS app plus English, Simplified Chinese and Traditional Chinese interfaces on both platforms. All original texts, offline model resources, local bookmarks and reading positions remain available. The iOS IPA is an unsigned arm64 build for iOS 18+, intended for personal signing with AltStore Classic or another local signing tool using a free Apple ID. A paid developer membership is not required for that flow; free provisioning expires and must be refreshed. The simulator ZIP is for a Mac only. See the attached verification files for actual test counts, build tools, source commit and checksums. Physical-device sideload installation has not been tested.
