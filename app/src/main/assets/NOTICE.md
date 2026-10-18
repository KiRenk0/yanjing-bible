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
- ONNX Runtime 1.23.2：Microsoft，MIT，并附其第三方声明。https://github.com/microsoft/onnxruntime
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
