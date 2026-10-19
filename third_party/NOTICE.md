# 第三方组件与经文来源

- 经文数据：Scrollmapper bible_databases，MIT，保留其许可证。https://github.com/scrollmapper/bible_databases
- ChiUn（和合本）：CrossWire 标注 Public Domain。https://www2.crosswire.org/sword/copyright/ModInfoCopyright.jsp?modName=ChiUn
- ChiUnL（文理和合本）：CrossWire 标注 Public Domain。https://ftp.crosswire.org/sword/modules/ModInfo.jsp?modName=ChiUnL
- KJV：CrossWire 分发的 1769 文本；Public Domain（英国存在特殊出版权，发布者应核对当地条件）。https://www.crosswire.org/sword/modules/ModInfo.jsp?modName=KJV
- 简体和合本由所附繁体文本通过 OpenCC.js 1.0.5 转换；不是另一种独立翻译。https://github.com/nk2028/opencc-js
- OpenCC.js：MIT；其所用 OpenCC 字典：Apache-2.0。https://github.com/BYVoid/OpenCC
- Kotlin、Jetpack Compose、Material 3、AndroidX、kotlinx.coroutines：Apache-2.0。
- Material Symbols：Google Material Design Icons，Apache-2.0。https://github.com/google/material-design-icons
- BAAI/bge-small-zh-v1.5：MIT，© 2023 BAAI。用于本机句意编码，CLS 池化与 L2 归一化。https://huggingface.co/BAAI/bge-small-zh-v1.5
- ONNX 转换：Xenova/bge-small-zh-v1.5，固定修订 75c43b069aac4d136ba6bc1122f995fedcfd2781，int8 模型；保留原 MIT 许可。https://huggingface.co/Xenova/bge-small-zh-v1.5
- ONNX Runtime 1.25.1：Microsoft，MIT，并附该版本的第三方声明。https://github.com/microsoft/onnxruntime/tree/v1.25.1
- AndroidX WorkManager：Apache-2.0，用于版本检查与可恢复下载。

程序会清除原始模块中的标记标签及中文源文本的分词空格，不改变经文词句。简繁转换可能涉及异体字选择，核对时可切换繁体源文本。原始文件与生成资源的 SHA-256、每版节数写入 data-manifest.json。所用源码版本为 e1b254cef86d0e65b1a5d1a94b8b112d0f296a2c。

图片中日常表达仅用于设计灵感；匹配词库由本项目维护。相关度是本地检索分数，不是语义置信度，也不表示经文原意。

分节差异：固定版本的 ChiUnL 将马太福音 6:34 的内容合入 6:33，6:34 原始字段为空。本应用对这一处使用明确映射，显示、收藏和复制时保留实际出处 6:33，并在对照页标出合并说明；不自行拆分或补写经文。

和合本 ChiUn 中另有 69 个字面值“a”，代表前一节已包含的合并分节。与 eBible 的 Public Domain 新标点和合本 VPL 逐个核对，这些节号同样未单列。应用移除占位行，保留前一节完整原文；单节查询映射到实际节号，对照页标出合并。可审查的映射、校对来源、下载存档 SHA-256 在 data/cuv-verse-mappings.json；生成资源各有 31,034 个非占位条目，覆盖全部 66 卷、1,189 章。https://ebible.org/find/details.php?id=cmn-cu89s

语义向量仅由所附简体和合本生成；检索后按节号展示所选版本的原文。模型余弦相似度不是置信概率。复杂否定、隐喻和英文句意检索可能不准确，不生成或改写经文。

古兰经文本：
- 中文经义译本：穆罕默德·马坚（Muhammad Makeen），QuranEnc.com / Rowwad Translation Center，chinese_makin v1.0.2，2022-09-07；2026-10-19 经官方 API 核实为当前版本。按 QuranEnc 条款原样再发布，保留完整原始 CSV 与 transcript information。https://quranenc.com/zh/browse/chinese_makin
- 再发布条款见 QuranEnc-TERMS.txt；译文权利归相应权利人，不适用本应用自有代码的 MIT 许可。https://quranenc.com/en/home/about
- 阿拉伯文：Tanzil Project，Uthmani v1.1，Creative Commons Attribution 3.0 及原样复制条件。完整原文、版权块与 Tanzil-NOTICE.txt 均随附。https://tanzil.net
- 章名和节数：Tanzil quran-data.xml（CC BY），中文章名取自上述 QuranEnc 目录。来源、版本与 SHA-256 见 assets/quran/sources.json。
- 两套文本均完整保留 114 章、6,236 节。显示正文不作简繁转换、去音标或改写；简繁归一化、阿拉伯文去音标只用于独立的搜索字段。古兰经的句意索引仅由所附中文经义译文生成，与圣经索引分开检索；译文和搜索结果不能取代原文及其上下文。

阿拉伯文使用 Tanzil 官方 XML 的 aya.text 字段逐节原样呈现；bismillah 属性在整章阅读时作为不编号的章首奉名词显示，不混入相邻的经文节号。第 1 章的第 1 节仍按原文作为编号经文保留，第 9 章无章首奉名词；第 95、97 章按 XML 保留其标音差异。

论语文本：
- 原文数据来自 chinese-poetry/chinese-poetry，固定提交 fa1814a8044db4fb0c7a9e0a0449974e8c3463b7，MIT，© 2016 JackeyGao。许可证见 chinese-poetry-LICENSE.txt。https://github.com/chinese-poetry/chinese-poetry/blob/fa1814a8044db4fb0c7a9e0a0449974e8c3463b7/论语/lunyu.json
- 完整保存原始 JSON，二十篇、512 个原文段落；以每篇的段落顺序编号为章句，不自行拆分、合并或改写原文。不同版本的分章、异体字和标点可能不同，查找时可结合篇名和原文核对。
- 现代汉语检索释义及日常表达关联由本项目编写，仅用于辅助查找，不是原文、完整翻译或权威注疏。对多义、古代制度和历史偏见保留语境说明。释义单独标注，复制与分享只导出原文、出处和来源，不把释义冒充经典文本。
- 论语句意索引分别编码原文与现代检索释义，长段另按句段编码，最终合并到原始章句。索引、原文、释义与许可均离线内置；来源、数量和 SHA-256 见 assets/analects/manifest.json 与 assets/semantic/analects-manifest.json。

v1.4 的句意查询另使用本项目维护的通用同义词与常用表达作为检索线索，并结合原文用词辅助排序。线索仅辅助查找，不生成或改写原文；匹配值不是概率。查询、推理与排序仍全部在本机进行。


儒释道新增经典（完整原文，编号依所附版本）：
- 大学（16 段）、中庸（39 段）、孟子（14 篇、690 段）：chinese-poetry/chinese-poetry @ fa1814a8044db4fb0c7a9e0a0449974e8c3463b7，MIT，© 2016 JackeyGao；见 chinese-poetry-LICENSE.txt。
- 心经（玄奘，7 段）、金刚经（鸠摩罗什，32 分、127 段）：hooosberg/Rushi @ bda9d95a2876aec4dc4d8b60b2594d9de79a1062，scriptures/**/*.md 的 CC0 1.0 dedication；见 hooosberg-LICENSE.txt。仅提取正文，不将译者说明、分目现代简述或来源元数据混入原文。
- 道德经（81 章）、庄子（内、外、杂共 33 篇、480 段）：banghuazhao/Chinese-Classical-Literature-Android @ f2c40b864c00c97d2d148a82857884fe8ba527d4，MIT，© 2024 Banghua Zhao；见 banghuazhao-LICENSE.txt。
- 原始文件与固定版本哈希保留在源码 data/classics/sources、scripts/classic-sources.json；APK 内只放完整正文、必要元数据及检索字段，避免重复携带原始 JSON。空白分隔按显示段落整理，原文字符不改写，检索归一化另存字段。
- 额外现代汉语检索提示由本项目独立编写，见 classics/search-hints.json；用于辅助查找，不是完整翻译或权威注疏。原文和提示分开显示，复制分享保留原文。

拉丁文圣经与分节数据：
- Vulgata Clementina：公有领域古典拉丁文文本，来自 Scrollmapper/bible_databases @ e1b254cef86d0e65b1a5d1a94b8b112d0f296a2c 的 sources/la/VulgClementine，数据仓库 MIT（scrollmapper-LICENSE.txt）。完整 73 卷、35,809 非空节；源数据库另外五个全空附录占位不计为书卷。原始 JSON、SHA-256 随源码 data/latin 保存，正文保留原版章号与节号。
- CrossWire Vulg 分节数据 © 2009–2013 CrossWire Bible Society，GNU GPL version 2；固定镜像提交 0a18221125bba6bc7f594e36074b42cf779555b5，完整原始 canon_vulg.h 与来源信息随源码 data/latin 保存。派生的独立非代码分节表 latin/mapping.tsv 同依 GPL-2.0-only 分发，见 GPL-2.0.txt；该表与应用自有 MIT 代码分别授权，未链接或打包 SWORD 运行库。
- 拉丁文采用独立书卷键，只有映射指明的共有出处参加中文句意关联；无对应的增补内容仍可完整阅读、按原文或出处搜索，不猜测中文对照。映射处理诗篇移章、但以理书等增补／移节及部分合节。不同版本存在读法与分节差异，应继续核对原文及上下文。
