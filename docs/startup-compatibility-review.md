# 1.4.1 启动兼容复查

反馈：Xiaomi 15 Pro，HyperOS 4.0.0.17.XOBCNXM.D01，Android 17，1.4.0 打开即退出，无系统错误详情。没有该机异常堆栈或 ADB 连接，未确认具体根因。

复查及处理：

- 原 ViewModel 构造器同步调度更新，初始化异常会阻止创建主界面。改用官方 WorkManager 按需初始化，在界面与离线库就绪后调度；初始化、手动检查、下载和设置调度均处理异常。
- 系统色板资源缺失时使用内置配色，只处理对应资源异常，避免掩盖其他程序错误。
- 原诊断入口依赖 Compose 设置页，启动失败时无法访问。新增平台 View 恢复入口及桌面快捷操作；未完成的启动可重试、兼容启动或导出诊断。保留旧 MainActivity 组件以兼容旧快捷入口。
- 恢复标记在危险启动代码之前同步落盘，完成后清除；兼容启动仅修改动态色彩与自动检查开关，保留收藏、历史和阅读进度。异常报告包含发生时版本号与系统构建信息。
- 核对 Manifest：恢复入口导出，FileProvider 与语义服务不导出，更新目录和诊断缓存目录分别限定。保留四种 ABI、16 KB ELF 对齐及原签名；模型与语义索引不变。

验证：API 37 的 Robolectric 框架／原生绘图启动、API 36/37 恢复入口与数据保留、调度失败注入、色板缺失回退，以及已有 API 35/36 Compose 与真实 ONNX 回归。最终计数、lint、APK 哈希和签名记录见 Release 的 `verification.json`。

边界：API 37 的 Espresso 输入注入依赖已移除的隐藏接口，因此启动测试直接布局绘制，没有把它算作 Android 17 触控全流程。Windows Robolectric 无法验证 FileProvider 的 POSIX URI 路径匹配，检查了报告内容、入口可达及 APK 中的目录声明，分享授权仍需 Android 设备验证。模拟器未成功启动；没有物理 Android 或 HyperOS 真机验证，不能据此宣称设备问题已完全解决。

配置依据：[WorkManager 官方按需初始化说明](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)。
