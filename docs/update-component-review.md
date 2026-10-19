# 1.5.1 更新组件修复与复查

用户在 Xiaomi 15 Pro／HyperOS 4／Android 17 上报告 1.5.0 显示“更新组件暂时不可用”，并提供本机诊断。异常为 `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []`；诊断中 1.4.1 的更新操作也出现相同异常。

核查 1.5.0 实际发布 APK 的 DEX，`WorkDatabase_Impl` 类存在，却没有 public 无参构造函数。WorkManager 2.10.1 使用 Room 2.6.1；Room 按生成类名查找实现，再调用 `getDeclaredConstructor().newInstance()`。现有消费者规则保留了数据库类，却没有显式保护这个反射构造入口。Debug 测试不运行 R8，原有发布检查只核对 Worker 类名，因此未检出此缺陷。

修复采用精确保留规则，保护 `androidx.work.impl.WorkDatabase_Impl` 类和 `public <init>()`，其余优化继续启用。保留原有按需初始化、首屏就绪后调度更新及异常隔离，避免恢复启动路径上的风险。未更改数据库结构，不清除用户数据，不要求卸载。

复查还发现 `UpdateManager.check()` 只保护 DOWNLOADING，自动启动检查可能把 READY 覆盖成 CHECKING，隐藏已经下载好的安装入口。现在同时保护 DOWNLOADING 和 READY；回归测试验证不会触及调度器、修改提示或丢失下载进度。

发布验证增加 `scripts/verify_update_artifact.py`：用 Android SDK apkanalyzer 检查最终压缩 APK 的 DEX，要求更新数据库确实包含 public 无参构造函数。实测 1.5.0 发布包被拒绝，修复包须通过后才能发布；通用与 ARM64 包还需通过 DEX 字节一致检查。GitHub 发布脚本必须收到这项成功记录。

自动验证包括 Android 16／17 下实际按需创建 WorkManager、数据库打开、定期检查任务入队与取消，及原有下载传输、哈希／包名／版本／签名拒绝、架构选择、更新界面和搜索阅读回归。完整数量、签名及产物校验值见 Release 附件 `verification.json`。

限制：没有连接物理设备。APK 构造函数检查直接覆盖这次报告的缺失入口，但不等于已经在该小米系统上完成检查、下载和系统安装全流程。1.5.0 更新器已经不可用的设备，需从 Release 下载修复包覆盖安装；后续版本继续通过应用内更新。

依据：[Room 2.6.1 官方源码归档](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.6.1/room-runtime-2.6.1-sources.jar) 中 `androidx/room/Room.kt` 的 `getGeneratedImplementation`、[Android R8 保留规则](https://developer.android.com/topic/performance/app-optimization/add-keep-rules)、[WorkManager 按需初始化](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)。实际依赖版本见 Gradle 依赖与构建合并规则；修复依据是本机诊断和已发布 APK 的 DEX。
