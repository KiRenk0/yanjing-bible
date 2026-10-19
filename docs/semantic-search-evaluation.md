# v1.4 检索评估与安装包优化

评估采用应用实际使用的 ONNX 模型、WordPiece 分词器、索引与 Kotlin 排序代码。基线是 v1.3.0 的索引和纯向量余弦排序，下限 0.5、最多 60 条。新方案在同一模型上增加有限的词义证据、补充召回与候选过滤，重新生成单句量化索引，没有新增远程查询或大型模型。

## 结果与边界

| 指标 | v1.3.0 基线 | v1.4.0 |
| --- | ---: | ---: |
| 有预期出处的查询组合 | 67 | 67 |
| 首位找到预期出处 | 37 | 41 |
| 前五位找到预期出处 | 45 | 53 |
| 范围外查询没有返回结果 | 22 / 24 | 22 / 24 |

这些是手工编写的有限样例，不是完整的相关性标注。一次查询可能有其他同样合适而未列入标注的原文。样例覆盖圣经、古兰经、论语，含不同说法、否定、多个条件与无关问题。开发和校验分开记录，但校验汇总也用于选择权重，因此这些数字不是盲测、置信概率或对实际用户查询总体准确率的估计。部分样例仍会误匹配，不能据此声称理解了所有否定或复杂句意。

查询集合在 `data/semantic-evaluation.tsv`，旧版冻结结果在 `data/semantic-baseline.tsv`。旧版结果使用 ONNX Runtime 1.25.1 与应用相同的 NFKC／简繁预处理运行在旧索引上；该基线绑定查询集合 SHA-256，修改标注时必须重新计算。新的实际结果在 `docs/semantic-evaluation.tsv`。

## 实现

- BGE 向量仍负责主要语义召回，先取 120 个候选。
- 通用同义词和已有表达仅扩充索引用词，不生成、修改或替换原文。倒排表补充最多 32 个候选；按连续文字窗口核对词义，避免长篇原文中相距很远的词被随意拼成含义。
- 用词证据的总加分最多 0.165，保持模型句意分数为主。原始余弦值和二次排序值分别保存，都不是概率。绝对下限与距最佳候选的相对下限控制结果尾部。
- 多模式融合保留语义分数差距；原文包含关键词和明确表达仍可独立命中。轻微用词重叠不会绕过语义筛选。模型进程退出时仍保留表达／原文模式的独立降级路径。
- 索引生成工具从旧版 ONNX Runtime 1.23.2、32 句一批，对齐到手机的 1.25.1、逐句、两条 CPU 线程。该设置本身没有在所有样例中带来提升；最终选择依据整体召回与排序结果，不把设置对齐当作准确度保证。

## 复现

普通构建直接使用已提交的索引。重新生成需安装 `scripts/semantic-requirements.txt`，使用 README 中固定修订的模型和分词文件。

```powershell
python scripts/prepare-semantic.py --corpus bible
python scripts/prepare-semantic.py --corpus quran
python scripts/prepare-semantic.py --corpus analects
python scripts/evaluate-retrieval.py --mode refined --output .tools/semantic/refined.json
.\gradlew.bat :app:testDebugUnitTest --tests org.yanjing.app.core.RetrievalEvaluationTest
```

Python 评估是便于比较方案的参考实现；最终发布数字来自 Kotlin 测试生成的 `app/build/reports/semantic-evaluation.tsv`。旧索引可从 [v1.3.0 Release](https://github.com/KiRenk0/yanjing-bible/releases/tag/v1.3.0) APK 的 `assets/semantic` 提取，使用 `--index-dir` 指向该目录、`--mode baseline` 复核，`--export-baseline data/semantic-baseline.tsv` 可导出冻结结果；该 APK 的 SHA-256 是 `e2bbd3be70c0551f282e04fb7ac691d0cd784d1591f8bfe0a5854e56b7058c61`。

## 安装包与兼容性

v1.3.0 APK 为 181,334,116 字节。新包启用 R8／资源裁剪、模型和向量压缩、四架构原生库压缩；准确的字节数和降幅写入 Release 的 `verification.json`。所有原文、许可证、模型与架构均保留。下载体积与安装后占用不同：Android 会提取当前架构原生库，模型首次加载会解压到私有缓存。

使用 [Android 官方裁剪指导](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)、[16 KB 页面说明](https://developer.android.com/guide/practices/page-sizes) 和 [ONNX 官方 R8 保留规则](https://onnxruntime.ai/docs/build/android.html#note-proguard-rules-for-r8-minimization-android-app-builds-to-work)。核对压缩方式、`extractNativeLibs`、16 KB ELF 段、签名、全部资源哈希，以及 JNI／已排队更新 Worker 的名称保留。Release 附带 R8 映射。测试覆盖 Robolectric Android 15／16 与实际桌面原生 ONNX；未进行物理 Android 设备或成功启动的 Android 模拟器验证。
