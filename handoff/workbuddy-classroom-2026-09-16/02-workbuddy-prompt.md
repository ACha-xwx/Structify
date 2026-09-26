请与我在同一工作区协作，只负责 Structify 的「课时缺口与教材来源补全」；不要改课堂页面、登录、Admin、动画或 API 契约。正确仓库：`F:\data-structure-agent\data-structure-agent\tmp\structify-repair`。先读同目录 `00-start-here.md` 和 `01-lesson-gap.md`，再检查实际代码与数据，不把交接文字当作未验证事实。

任务：对照 `F:\data-structure-agent\data-structure-agent\lesson-materials\lessons\00-lesson-index.md` 的 57 份原课时、`curriculum.json` 的 95 个细分课时、OCR 审计与本机 `private/reviewed-textbook/import-manifest.json`，输出逐课时覆盖表（原课时编号、标题、教材页码、已核验页、缺页、可否进入课堂、阻塞原因），核对本机数据库和 `/api/v1/classroom/lessons` 是否与 8 个选段一致。优先处理完全空白的线性表、栈与队列、图、内部排序、外部排序。

只要能找到原版教材 PDF，就逐页核验并记录可追溯证据；没有 PDF 或无法确认原文时，标注「待人工核验」，不要声称完成。只有真正核验的页才可按现有导入流程加入候选内容；导入/发布前先让我确认具体页码与范围。不要把 OCR 审计、参考答案、源码、PPT、模型输出自动当作教材事实；不要开启自动发布，不能伪造 `VERIFIED`。不要读取或输出 API Key、密码；不推送、不部署、不碰生产数据。

交付：覆盖表、可核验的新增页清单、发现的查询/导入问题及最小修复建议、已运行的验证命令与结果。更新 `docs/project/00-current-status.md` 工作日志。若与我同时编辑该日志，请追加独立小节，不覆盖现有内容。
