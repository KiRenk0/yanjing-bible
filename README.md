# 言·经 · 经文查询匹配器

原生 Android 应用。输入日常想说的话，找意思相近的真实经文，再读上下文与版本对照。界面使用官方 **Material 3 Expressive** 组件。图片仅作为设计灵感，不作为程序指令。

## v1.6.0：加入完整《法华经》并修复出处与发布打包

佛教经典新增 **《妙法莲华经》鸠摩罗什译本，七卷、二十八品、504 段**，从 CBETA T0262 固定版本提取完整正文。支持离线阅读、原文与现代汉语检索、上下文、收藏、复制和分享；单独内置语义索引与项目编写的检索提示。可输入 `法华经 25:1`、`《妙法蓮華經》第二十五品第一段`、`如来寿量品`、`普门品1`。段号依所附版本的正文／偈颂区块顺序，不冒充其他版本的标准节号。

代码审查修复：经典键与对照查询改由目录识别，避免新增经典漏掉收藏或被归入圣经；出处解析补充“品／分”、完整经名及繁体别名，并拒绝溢出的段号／范围，避免误返回整篇。源码 ZIP 只收录 Git 已跟踪文件，避免夹带本地未审查文件。继续保留切换经典后自动搜索、原签名覆盖升级、更新组件修复与 Android 17 启动保护。

来源为 [CBETA XML P5 固定修订](https://github.com/cbeta-org/xml-p5/blob/dbdea41071e1e260ad84b72faefd4587333cf76d/T/T09/T09n0262.xml)，采用 **CC BY-NC-SA 3.0 TW** 及所附非营利使用条件；正文、衍生检索字段及对应索引按此条款分发，与应用自有 MIT 代码分别授权。完整原始 XML 随源码提供，APK 保留完整来源 header 和 [版权／版本说明](third_party/CBETA-NOTICE.txt)。白话提示是查找辅助，不是完整翻译或权威注疏。

重建流程：先运行 `python scripts/prepare_lotus.py`，再运行 `node scripts/prepare-classics.mjs`；语义环境执行 `scripts/prepare-semantic.py --corpus lotus_sutra`。普通 Android 构建直接使用提交的全文和索引。新增检查覆盖全部段落出处往返、真实模型白话检索、索引切换及 Android 15/16 的搜索、收藏、复制、阅读和位置恢复；发布校验逐段比对 XML 提取结果。[代码审查与修复记录](docs/lotus-sutra-review.md)。未进行物理设备测试。

## v1.5.2：切换经文后自动继续搜索

完成搜索后，切换经典、同类书目或译本会保留搜索词，并在新文本加载完成后自动重新搜索、更新结果，无需再次输入或提交。保留已选搜索模式；跨经典切换时重置不适用的圣经新旧约筛选。仍可使用搜索栏的清空按钮主动结束搜索。

新增 Android 15/16 Compose 回归检查：输入并提交“天”，依次切换圣经、古兰经、论语、大学和繁体圣经，核对搜索栏、搜索模式、对应文本的结果及历史记录；另检查主动清空后切换仍为空。安装包沿用原签名，可覆盖安装并保留本机数据。

## v1.5.1：修复更新组件不可用

修复发布包中更新数据库缺少反射构造函数的问题，恢复更新检查与下载；同时保留已下载更新的安装入口。发布校验现在直接检查压缩 APK 的 DEX，防止只通过 Debug 测试却漏掉 R8 反射入口。详情见 [原因、修复与复查](docs/update-component-review.md)。

1.5.0 应用内更新不可用时，请从 [GitHub Release](https://github.com/KiRenk0/yanjing-bible/releases/latest) 下载 1.5.1 覆盖安装，无需卸载；收藏、历史和阅读进度保留。小米 15 Pro 可使用 ARM64 包。未进行修复版真机验证。

## v1.5.0：拉丁文圣经与儒释道经典

新增 **Vulgata Clementina 拉丁文圣经，73 卷、35,809 节**，保留原版编号。使用固定 CrossWire 分节数据关联中文查询与对照，例如中文诗篇 23:1 对应拉丁诗篇 22:1，而非按相同编号机械拼接。无共有对应的增补内容仍能完整阅读、按出处和拉丁原文搜索；不猜测中文对应。原始数据、分节表及各自许可随源码归档。

儒家四书（论语、大学、中庸、孟子）、佛教心经与金刚经、道家道德经与庄子，共 **八部完整经典、1,952 段**。按所附版本的篇章和段落编号，可用现代汉语描述意思，或按出处、原文查找；支持阅读、上下文、收藏、复制和分享。新增经典分别生成离线语义索引，配有本项目编写的部分现代检索提示；提示单列，不能代替完整翻译或权威注疏。大学／中庸按所附全文的一篇、多段展示，段号不冒充其他版本的标准章号。切换经典保留各书阅读位置，旧收藏继续可读。

新增 **ARM64 小安装包 65.1 MB**，较 1.4.1 通用包 91.0 MB 减少 **28.49%**；保留四种架构的通用包 **98.0 MB**（均为十进制下载大小）。新更新器按设备架构选择，逐包校验 SHA-256 与原签名；旧更新器仍可下载通用包。全部文本、模型和索引均保留，新增原始 JSON 随源码提供，APK 不重复携带。逐包验证见 Release `verification.json`；安装后占用不会同比例下降。

原文固定来源：chinese-poetry（MIT）、Chinese-Classical-Literature-Android（MIT）、Rushi scriptures（CC0）、Scrollmapper 拉丁文（公有领域文本／MIT 数据）；独立分节表采用 CrossWire 数据许可 GPL-2.0-only。完整许可和来源见 [NOTICE](third_party/NOTICE.md)。复现：先运行 `node scripts/prepare-classics.mjs`、`node --use-system-ca scripts/prepare-latin.mjs`；如需重建新增索引，使用匹配的 Python 环境执行 `scripts/prepare-semantic.py --corpus daxue` 等。普通 Android 构建直接使用提交的全部文本和索引。

继承 1.4.1 的 Android 17 启动与恢复修正。用户已确认 1.4.1 在所反馈的小米 15 Pro 上可正常进入；尚未进行 1.5.0 的同机验证。

## v1.4.1：启动兼容与恢复

针对 Xiaomi 15 Pro、HyperOS 4.0.0.17.XOBCNXM.D01、Android 17 的启动即闪退反馈，修正启动链中的可确认风险：WorkManager 改为官方的按需初始化，界面与本地文本就绪后才调度自动更新；更新组件初始化或调度失败会报告错误，不再直接中断主界面。动态配色缺少系统色板资源时回退到内置配色。

新入口使用平台原生 View。上次启动未完成时，显示“启动恢复”，可重试、兼容启动（关闭动态配色与自动检查更新）或导出本机诊断；保留收藏、历史与阅读进度。也可长按桌面图标选择“启动恢复与诊断”。诊断补充系统构建指纹及异常发生时的应用版本，不会自动上传。旧 MainActivity 组件继续可供旧桌面入口访问。

补充 Android 17（API 37）框架与原生绘图的 Robolectric 启动测试，以及 Android 16/17 下的恢复流程、调度失败与缺失色板回归。现有 Android 15/16 Compose 全流程仍保留。**尚未获得反馈设备的异常堆栈，也没有该机真机验证，因此不能断言反馈中的闪退根因已确认。** Android 17 的 Espresso 输入注入仍依赖已移除的隐藏 API，37 的启动检查直接布局绘制，不冒充触控全流程测试。WorkManager 配置见 [Android 官方说明](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)。

## v1.4.0：查询排序与安装包优化

三部经典的句意查询加入第二轮排序：先用原有 BGE 模型召回候选，再以通用同义词、原文、论语检索释义和已有表达线索补充召回、细调排序。额外用词加分有上限，仍以句意相似度为主。按绝对下限与相对最佳结果的差距过滤较弱候选；多选搜索模式时，不再让弱表达重叠重新混入已筛掉的结果，保留原文精确命中与明确表达关联。模型服务不可用时仍可独立使用其他模式。

索引使用与 Android 一致的 ONNX Runtime **1.25.1**、单句输入、两条 CPU 线程重新生成；模型权重、经典原文和向量维度不变。所有版本使用对应经典的中文文本作辅助排序，然后按真实出处展示所选版本。检索时只保留当前经典的索引和辅助倒排表，切换时释放其他经典的表。

启用 R8 代码／资源裁剪，以及模型、索引和原生库的 APK 压缩，通用安装包 **181.3 MB → 91.0 MB，减少 49.81%**（十进制 MB）。继续内置四种架构、全部文本与离线模型。ONNX JNI 与旧版已排队的 WorkManager 任务名称按官方要求保留；随 Release 附带 gzip 压缩的 R8 映射以便排查问题。压缩减少下载体积；Android 安装时会解压当前架构的原生库，安装后占用不会同比例减少。

用实际模型、所附索引和应用排序代码运行 91 组查询／经典组合，其中 67 组有人工标注的预期出处、24 组属于范围外查询。当前样例中首位预期命中 **37 → 41**，前五位预期命中 **45 → 53**；范围外查询无结果 **22/24**，没有解决所有误匹配。这些样例参与开发与参数选择，不能当作盲测或所有查询的准确率。逐条记录、复现步骤与局限见 [评估说明](docs/semantic-search-evaluation.md) 和 [应用实际评估输出](docs/semantic-evaluation.tsv)。

## v1.3.0：用现代汉语搜索《论语》原文

搜索页新增“论语”，完整内置 **二十篇、512 章句** 文言原文。可以直接用白话描述意思，例如：

- “读书时要动脑筋，不能光背下来” → “学而不思则罔，思而不学则殆。”
- “不能把自己讨厌的待遇加到别人身上” → “己所不欲，勿施于人。”
- “地位比我低的人也值得我去求教” → “敏而好学，不耻下问。”

每个章句都有本项目编写的现代汉语检索释义，与原文分别编码到离线 BGE 索引；句意搜索支持词典以外的白话改述。“日常表达”结合常用说法及检索释义用词匹配；“原文搜索”只检索文言原文。结果保留原始章句，详情单列“现代汉语检索释义”，可阅读整篇、查上下文、收藏、复制和分享。复制、分享保留文言原文。三部经典分别保存阅读位置，旧收藏继续可用。

支持 `论语 2:15`、`論語２：１５`、`为政15`、`学而1-3`、`学而篇`、`论语第十五篇第二十四章`。编号按所附版本的篇序和段落序；与其他版本的分章可能不同，不应只凭章号核对。

原文来自 [chinese-poetry 固定版本](https://github.com/chinese-poetry/chinese-poetry/blob/fa1814a8044db4fb0c7a9e0a0449974e8c3463b7/论语/lunyu.json)，保留原始 JSON、MIT 许可和完整性哈希。白话检索释义是辅助查找的简短说明，不是完整翻译或权威注疏。句意相近不等于解释准确，仍需结合原文与历史语境阅读。

```powershell
.tools\semantic\venv\Scripts\python.exe scripts/prepare-analects.py
.tools\semantic\venv\Scripts\python.exe scripts/prepare-semantic.py --corpus analects
```

普通 Android 构建直接使用已提交的文本和索引，不需要 Python 或重新生成。新增测试覆盖十二个未写入表达词典的白话改述、全部512条出处往返定位、篇章范围拒绝、三部经典索引切换，以及原生 Compose 的搜索、收藏、释义、复制、整篇阅读和位置恢复。

## v1.2.1：Android 16 崩溃修复

针对 Xiaomi 17 Pro（SM8850）／Android 16 的无日志闪退反馈，将 ONNX Runtime 从 1.23.2 升级到 **1.25.1**。上游 [#27884](https://github.com/microsoft/onnxruntime/issues/27884) 记录了相同芯片／系统上的 SME CPU 指令检测问题，并确认原生 1.24.4 已修复；Maven 未发布 1.24.4，因此采用后续的 1.25.1。没有该设备日志，尚未在此小米真机复现，不能断言反馈中的崩溃只有这个原因。

推理放入不对外导出的 `:semantic` 绑定服务，主进程不加载 ONNX；即便原生推理进程退出，也保留阅读、收藏、更新与已启用的其他搜索模式，并提示句意搜索暂不可用。服务死亡后停止本次运行内的自动重启，避免反复崩溃。模型缓存每次进程首次加载前核对 SHA-256，修复长度相同但内容损坏的缓存。另修复非有限字号设置可能导致的文字布局异常。

设置新增“导出本机诊断”：保存本机未捕获的 Java 异常，Android 11+ 读取系统提供的近期进程退出原因（包含原生崩溃原因和信号），经系统分享面板由用户选择导出。不会自动上传，不读取查询、历史或收藏。原生崩溃不会由 Java 异常处理器捕获，系统退出记录也可能因设备策略不可用。ONNX 遥测显式关闭。

[修复依据、代码复查与验证边界](docs/crash-fix-review.md)。


## v1.2.0：古兰经查询

搜索页可直接切换“圣经／古兰经”，顶部版本按钮可选择中文或阿拉伯文。内置完整 **114 章、6,236 节**：QuranEnc.com 马坚中文经义译本 **v1.0.2**（2026-10-19 经官方 API 核实当前版本）和 Tanzil Uthmani 阿拉伯文 **v1.1**。

支持 `2:255`、`古兰经第2章第255节`、`古蘭經２：２５５`、`Quran 94:5-6`、`开端章` 及阿拉伯数字。也可用中文描述句意，或搜索译文关键词；阿拉伯文关键词检索忽略音标与延长符，显示、复制和分享保留原文。阅读页可选全部 114 章；按来源 XML 将章首未编号的奉名词单独显示，保持中文与阿拉伯文节号对应。详情可查看中阿对照和上下文；收藏包含经典和版本信息，两部经典分别保存阅读位置。

句意索引单独由所附中文经义译文生成，较长经文按句段建立多个向量，并在返回完整原文前按实际出处合并。与圣经使用同一个离线模型，不发出查询网络请求。模型仍可能误判否定与细微语义，结果仅辅助查找。

显示文本均原样保留，原始 CSV（含 transcript information）、阿拉伯文 XML（含版权块）、章目元数据和哈希随源码附带，见 `app/src/main/assets/quran/`。QuranEnc 的有条件再发布条款及 Tanzil 的 CC BY 3.0／原样复制条件分别保留在 `third_party` 与应用内许可页；这些文本不适用本项目自有代码的 MIT 许可。后续数据更新应先核对官方版本和授权，重新准备文本及索引。

```powershell
python scripts/prepare-quran.py
python scripts/prepare-semantic.py --corpus quran
```

## 安装与搜索

Android 8.0（API 26）及以上。前往 [GitHub Release](https://github.com/KiRenk0/yanjing-bible/releases/latest)：ARM64 手机可选 `yanjing-1.6.0-arm64-v8a.apk`；其他设备或不确定时选 `yanjing-1.6.0.apk` 通用包。无需登录，所有文本和模型已内置，可离线使用。

搜索栏旁的设置按钮可**多选**搜索模式，至少保留一种，选择会保存在本机。默认同时启用句意搜索和日常表达。

| 模式 | 用途 |
| --- | --- |
| 句意搜索 | 本地 BGE 向量模型，理解中文句子的意思，检索意思相近的原文 |
| 日常表达 | 常用表达关联、中文字符和双字加权检索，适合图片中的日常说法 |
| 原文搜索 | 原文包含关键词，忽略标点、空格和英文大小写，支持简繁体 |

多选模式优先保留原文命中与完整常用表达，其余使用 Reciprocal Rank Fusion 合并排序；按真实节号去重，卡片标明命中模式。也可直接输入 `约 3:16`、`約翰福音３：１６－１８`、`诗篇 23`、`John 4:7`，出处定位不依赖模型。

可以试试：“做错了就坦白承认，不要掩饰”“有困难的时候朋友才最重要”“说话先过脑子，少发脾气”。结果可阅读上下文、对照四套文本、收藏、复制及系统分享。

## 离线句意检索

采用 MIT 授权的 [BAAI/bge-small-zh-v1.5](https://huggingface.co/BAAI/bge-small-zh-v1.5)，通过 [Xenova ONNX 转换](https://huggingface.co/Xenova/bge-small-zh-v1.5) 固定修订 `75c43b069aac4d136ba6bc1122f995fedcfd2781`。模型解压后约 24 MB、512 维，使用 ONNX Runtime 1.25.1 在手机 CPU 的独立进程推理；CLS 池化、L2 归一化，查询添加模型建议的检索前缀。首次使用会将模型放入私有目录。

全部简体和合本正文预先生成量化索引，约 16 MB。古兰经和论语使用独立索引，论语另编码项目自编现代汉语检索释义，支持白话到文言的查找。检索后按出处展示所选版本的原文，简繁体查询归一化。其他译本的独有措辞可配合“原文搜索”；英文句意查询不在此中文模型的主要能力范围内。

不生成、不改写经文；余弦相似度与融合分数均不是概率。采用经验相似度下限减少明显无关结果，复杂否定、隐喻和不同领域的句子仍可能误匹配。请结合上下文阅读。切换查询会取消旧任务，模型推理与索引扫描在后台执行。

## 内置文本与分节

| 文本 | 非占位条目 | 说明 |
| --- | ---: | --- |
| 和合本·简体 | 31,034 | 繁体源文本经 OpenCC 转换 |
| 和合本·繁體 | 31,034 | 白话文源文本 |
| 文理和合本 | 31,095 | 独立的文言译本 |
| King James Version | 31,102 | 英文 1769 文本 |

四套文本对应三种译本，简繁体和合本不是两种独立翻译。各版均含全部 66 卷、1,189 章。数据来自 [Scrollmapper](https://github.com/scrollmapper/bible_databases)，固定提交 `e1b254cef86d0e65b1a5d1a94b8b112d0f296a2c`。

原 ChiUn 中 69 个 `a` 是合并分节占位符，内容已在前一节。与 [eBible Public Domain 和合本](https://ebible.org/find/details.php?id=cmn-cu89s) 逐个核对后删除占位行，保留完整原文，单节查询映射到实际出处并标注合并。映射及校对存档哈希见 `data/cuv-verse-mappings.json`。文理太 6:34 同样并入 6:33。来源、许可见 [NOTICE](third_party/NOTICE.md)，资源哈希在 assets 的 manifest 中。

## 更新下载

启动时最多每 6 小时检查一次，WorkManager 每 24 小时安排后台检查，实际执行时间由 Android 调度。默认开启自动检查、自动下载及仅 Wi-Fi 自动下载；设置中可关闭，或手动“立即下载”使用当前网络。

更新信息来自本仓库最新 Release 的 `update.json`，APK 也作为 Release 附件发布。支持 GitHub 直连、自动切换、镜像优先（`gh-proxy.com`、`ghfast.top`）；每条线路都有超时和回退，支持断点续传及后台重试。镜像由第三方运营，可用性取决于地区和网络，无法保证所有中国大陆运营商一直可达。

安装前核对下载长度、SHA-256、实际包名、版本及与当前应用相同的签名证书。镜像不能通过修改安装包绕过签名验证。下载完成后由用户进入 Android 安装界面；首次需允许本应用安装未知来源应用，授权后返回再次点“安装更新”。应用不静默安装。

本版保留最初 1.0 体验包的签名身份，Release 为非 debuggable 构建。**后续更新必须继续使用同一私钥**；不要提交私钥或签名口令。第三方自行构建的包具有不同证书，不能覆盖本项目的安装包。

## Material Design

使用 [Material 3 Expressive](https://m3.material.io/get-started) 的官方 Compose 组件、色彩角色、形状、字体层级和动效。手机 NavigationBar、大屏 WideNavigationRail、官方全屏 contained search、详情 ModalBottomSheet、多选设置 Checkbox；支持系统字体缩放、TalkBack 标签、深浅色、Android 12+ 动态配色和键盘边距。

Material 3 **1.5.0-beta01** 提供公开 Expressive API（2026-10-18 核实），配合 Compose BOM 2026.09.00。该组件版本处于官方测试阶段。图标使用官方 Material Symbols VectorDrawable。

## 构建与验证

应用编译使用 JDK 17，Android 16 的 Robolectric 测试运行时另需 JDK 21；Gradle 9.5.0、AGP 9.3.3、Kotlin/Compose compiler 2.3.21、Android SDK 37.0、Build Tools 36.0.0。可通过 Gradle 的 `org.gradle.java.installations.paths` 指定本机 JDK 安装目录。Windows 建议使用最新补丁的 JDK，避免旧 JDK 所附 VC++ 运行库与桌面 ONNX 测试冲突。AGP 使用内置 Kotlin。

```powershell
# 如无 SDK，脚本下载官方工具并接受 SDK 开发条款。
.\scripts\setup-sdk.ps1
.\scripts\build.ps1
# 单独检查
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

经文、模型、索引已提交，普通 Android 构建不需要 Python、Node.js 或下载模型。测试包含完整经文、分节映射、真实 ONNX 改写检索、分词一致性、多模式融合、取消处理、断点下载、元数据限制与签名拒绝；Robolectric 运行真实 Compose UI，导出手机和平板截图到 `app/build/screenshots`。无物理 Android 设备验证。

重新生成经文：

```powershell
npm ci --ignore-scripts
node --use-system-ca scripts/download-data.mjs
npm run data
```

重新生成索引：将上述固定修订的 `onnx/model_quantized.onnx`、`tokenizer.json`、`vocab.txt` 下载到 `.tools/semantic`，模型命名为 `model.onnx`；安装 `scripts/semantic-requirements.txt`，运行 `scripts/prepare-semantic.py`。`scripts/evaluate-semantic.py` 输出全库检索示例。模型和语料哈希写入 `assets/semantic/manifest.json`。

## 发布与隐私

`scripts/create-update-manifest.mjs` 根据已签名 APK 生成 `update.json` 和 SHA-256 文件。新版本同时递增 `versionCode` 和 `versionName`，更新构建脚本的文件名，使用原签名构建，再将 APK、`update.json`、校验文件及源码 ZIP 上传到同一正式 Release；tag 与文件名必须为 `vX.Y.Z`、`yanjing-X.Y.Z.apk`，将该 Release 标为 latest。草稿与预发布不作为正常更新渠道。

完整测试与打包后，可用 `scripts/verify-release.py --build-tools .tools/android-sdk/build-tools/36.0.0` 检查 APK 签名、16 KB 对齐、全部内置资源和来源哈希、源码 ZIP、JUnit 与 lint 报告，并生成 `dist/verification.json`。发布脚本会核对验证摘要与各附件哈希，拒绝覆盖已发布版本。验证时应让 `JAVA_HOME` 指向用于构建的 JDK。

不含账号、广告、统计、云端搜索或远程模型 API。经文、查询、历史、收藏和模型计算都在本机；联网仅用于更新检查与安装包下载，服务提供者会收到常规网络连接信息。应用禁用云备份。自有代码 MIT，第三方许可证在 `third_party` 与应用内保留。


## 界面预览

手机首页、多选搜索设置及合并结果（Robolectric 生成）：

<img src="docs/screenshots/home.png" alt="首页" width="240"> <img src="docs/screenshots/search-modes.png" alt="多选搜索模式" width="240"> <img src="docs/screenshots/matches.png" alt="合并匹配结果" width="240">

古兰经章号检索、中阿对照与阿拉伯文阅读：

<img src="docs/screenshots/quran-search.png" alt="古兰经检索" width="240"> <img src="docs/screenshots/quran-compare.png" alt="古兰经中阿对照" width="240"> <img src="docs/screenshots/quran-arabic.png" alt="古兰经阿拉伯文阅读" width="240">

论语白话搜索、现代汉语检索释义与整篇阅读：

<img src="docs/screenshots/analects-search.png" alt="用现代汉语搜索论语原文" width="240"> <img src="docs/screenshots/analects-gloss.png" alt="论语原文与检索释义" width="240"> <img src="docs/screenshots/analects-reader.png" alt="论语整篇阅读" width="240">

[平板阅读界面](docs/screenshots/tablet-reader.png)

法华经白话查找与普门品阅读：

<img src="docs/screenshots/lotus-search.png" alt="法华经白话检索" width="240"> <img src="docs/screenshots/lotus-reader.png" alt="法华经普门品阅读" width="240">
