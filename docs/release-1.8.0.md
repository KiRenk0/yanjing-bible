# 盐筋 v1.8.0 · 读经、玫瑰经与祷词

Android 和 iPhone / iPad 新增独立的 **「读经」入口**，内置天主教玫瑰经与常用祷词，无需登录或联网即可使用。

- **玫瑰经四端、二十个奥迹**：欢喜、光明、痛苦、荣福五端，附经节出处、项目编写的简短默想与按星期推荐；也可自行选择任意一组。
- **逐步诵念**：开始祷文、每端默想、天主经、十遍圣母经、圣三光荣经、可选法蒂玛祷词及结束祷文。显示端次、圣母经遍数与全程进度，可前进、后退或确认后重新开始。
- **本机续读**：四组五端分别保存进度，退出或重启后可继续；使用独立存储键，不影响经典阅读位置、收藏、历史与所选译本。
- **十二篇祷词**：圣号经、宗徒信经、天主经、圣母经、圣三光荣经、法蒂玛祷词、又圣母经、玫瑰经结束祷词、痛悔祷词、晨祷、晚祷与饭前祷词。支持文字选择、复制、系统分享及现有字号设置。
- 支持 English、简体中文、繁體中文，深色界面与系统字体缩放；iOS 保持原生 SwiftUI 导航与系统控件。

中文祷词为项目依据传统祷文编译的文本，晨祷、晚祷与奥迹默想由项目编写；没有将其标为特定地区的礼仪核准译本。应用内可查看文本版本及核对来源。圣母蒙召升天和荣冠天后的内容明确标为天主教传统，所列《默示录》经节作为默想意象。全部原有经典全文、模型、译本与来源条件继续适用。

## 下载

| 附件 | 适用设备 |
| --- | --- |
| `yanjing-1.8.0-arm64-v8a.apk` | Android ARM64 手机，Android 8.0 及以上 |
| `yanjing-1.8.0.apk` | Android 通用包，含四种架构 |
| `yanjing-1.8.0-ios-unsigned.ipa` | iPhone / iPad，iOS 18 及以上，需在本机签名侧载 |
| `yanjing-1.8.0-ios-simulator.zip` | Mac 上的 iOS 模拟器 |
| `yanjing-1.8.0-source.zip` | 两端源码、祷词数据、原始经典与许可证 |

Android 沿用原包名、图标与签名，可直接覆盖升级。iOS IPA 是未签名 arm64 设备构建，沿用上一版的侧载方式：通过 AltStore Classic / Sideloadly 等工具使用自己的 Apple ID 签名后安装，不能直接点击 IPA 安装。侧载说明见 [iOS README](https://github.com/KiRenk0/yanjing-bible/blob/v1.8.0/ios/README.md)。

## 验证

新增检查覆盖二十个奥迹、七天推荐、每组完整八十步、五十三遍圣母经、每端一至十遍计数、结束祷文、可选祷词、三种语言、各组独立进度、完成／重置／越界恢复，以及原生页面入口、复制与重新启动后继续诵念。继续执行原有检索、实际 ONNX 推理、收藏、阅读、语言、启动恢复与更新回归。

Release 附带 Android / iOS 验证报告、源码提交、SHA-256、R8 映射及生成的 Xcode 工程。两端包内的祷词数据逐字节核对同一份资源。iOS 在 Mac CI 上测试并归档；未进行物理 iPhone 侧载安装或物理 Android 设备测试。

**English:** Adds an offline Devotions tab on Android and iOS, with all twenty Rosary mysteries, weekday suggestions, an eighty-step guide per set, persistent per-set progress and twelve prayers. Includes English, Simplified Chinese and Traditional Chinese text, copy/share and adjustable reading size. Chinese prayers are project translations; meditations and morning/evening prayers are project authored. Android keeps its existing signing certificate. The iOS IPA is unsigned and requires personal local signing before sideloading. See the attached verification reports for actual checks, tests, hashes and source commit.
