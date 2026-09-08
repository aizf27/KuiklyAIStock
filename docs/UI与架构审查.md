# KuiklyAIStock UI 与架构审查

审查时间：2026-09-08
审查对象：Task 1 全部页面、组件、模型、Repository 与设计文档
审查方式：只读代码，不改动实现

## 一、结论

Task 1 的业务闭环（行情 → 详情 → AI 解读）已经完整，模型、Repository、页面、通用组件职责边界清晰，`StockDesignTokens` 统一了颜色/间距，这是合格的 Demo 骨架。但当前实现存在两类必须解决的问题：

1. **UI 层面**：导航栏整体仍是 Kuikly 模板样式（青紫渐变标题 + 模板返回箭头），与 `DESIGN.md` 定义的品牌蓝和"克制白色表面"直接冲突，是 codex 优化时被整块遗漏的区域。
2. **架构层面**：底部导航用 `push` 路由导致返回栈无限堆积；自选状态跨页面同步依赖"手动版本号 + 非响应式单例"，存在真实的不同步风险；同步 Repository 让所有 loading/骨架屏形同虚设。

以下按严重度分级列出，每条给出位置与建议。

---

## 二、架构问题

### A1（高）底部导航用 push 造成返回栈泄漏

- 位置：`StockAppShell.kt:74-86`（`openStockPage`）、`StockHomePage.kt:96-102`、`StockAiPage.kt:96-103`
- 现象：首页点"AI解读"→ `openStockPage("stock_ai")` 是 push 一个新页；AI 页点"行情/自选"→ 又 push 一个 `stock_home`。用户来回切 Tab 时，页面栈 `stock_home → stock_ai → stock_home → stock_ai …` 无限增长，按返回键要逐层退出。
- 建议：底部导航切换应使用 replace / `closeSamePage` 语义，或改用 Kuikly 提供的单页内 Tab 容器。至少 `openStockPage` 打开 `stock_home` 时应带 `closeSamePage=true`，避免同名页面反复入栈。

### A2（高）自选状态跨页面不同步风险

- 位置：`WatchlistStore.kt:4-20`（object 单例 + 非响应式 `linkedSetOf`）、`StockHomePage.kt:24` 与 `StockDetailPage.kt:24`（各自维护 `favoriteVersion`）
- 现象：自选状态靠"每个页面读一次 `favoriteVersion` 触发重算"，而 `WatchlistStore` 本身不是 observable。用户在详情页取消自选后返回首页，首页 `favoriteVersion` 未变、`created()` 不重跑，列表星标是否刷新取决于 Kuikly 返回时 body 是否重算——这是不确定行为，容易漏刷。
- 建议：把自选状态收敛为一处 observable 数据源（如 `WatchlistStore` 内部持有 `observableList`，页面直接订阅），去掉每页各自的 `favoriteVersion` 手动失效。

### A3（高）Repository 同步接口让 loading 态形同虚设

- 位置：`StockRepository.kt:13-20`（全同步方法）、`StockHomePage.kt:106-116`、`StockDetailPage.kt:107-125`、`StockAiPage.kt:24-30`
- 现象：`loadQuotes()/loadDetail()` 同步返回，`created()` 里 `loading=true` 后立刻 `loading=false`，`body()` 首次执行时 loading 已是 false，`StockLoadingState` 骨架屏永远不可见。将来接真实 API 必须改成 suspend / callback，接口签名要重写。
- 建议：现在就把 Repository 接口设计为异步（suspend 或 callback），Mock 实现加一个可配置的模拟延迟；否则"替换真实 API 不动 UI 结构"的边界承诺不成立。

### A4（中）BridgeModule / RouterPage 模板残留

- 位置：`base/BridgeModule.kt:1-356`、`RouterPage.kt:16-231`
- 现象：`BridgeModule` 350+ 行里只有 `log()` 被股票业务使用，其余 `callPhone`、`openSelectAddressView`、`qqLiveSSORequest`、`humanVerification`、`preDownloadPAGResource` 等都是模板自带的无关方法。`RouterPage`（`@Page("router")`）是 Kuikly 模板的页面路由调试器，含 ImageAdapter 基准测试、彩虹渐变输入框，与股票业务无关但未删除。
- 建议：按最小修改原则，只保留实际用到的桥方法（`log`，必要时 `toast`/`closePage`）；删除或隔离 `RouterPage` 调试页，避免和正式页面混在一起。

### A5（中）通用导航栏定义在模板文件里，职责混乱

- 位置：`RouterPage.kt:233-306`（`RouterNavigationBar` / `RouterNavBar`）
- 现象：三个股票页面复用的 `RouterNavBar` 定义在 `RouterPage.kt` 内，与模板调试页共用一个文件。导航栏应属于共享 UI 组件，与 `StockAppShell`、`StockUiComponents` 同层。
- 建议：把 `RouterNavBar` 抽到独立组件文件，与模板页解耦。

### A6（低）UI 组件全为顶层扩展函数，散落 6 个文件

- 位置：`StockAppShell.kt`、`StockUiComponents.kt`、`StockMarketSummary.kt`、`StockTrendChart.kt`、`StockAiAnalysis.kt`、`StockTrendChart.kt`
- 现象：所有组件都是 `ViewContainer<*,*>` 顶层扩展函数，无统一组织。对 Kuikly 这是可用范式，但随 Task 2 增加聊天组件后会继续膨胀。
- 建议：Task 2 前可按 feature（`home/`、`detail/`、`ai/`、`common/`）或保持现状但约定命名前缀，避免进一步散乱。

---

## 三、UI 问题

### B1（高）导航栏标题沿用模板青紫渐变，与品牌蓝冲突

- 位置：`RouterPage.kt:262-267`（`backgroundLinearGradient(0xFF23D3FD → 0xFFAD37FE)`）
- 现象：三个页面的顶部标题（"AI 股票"、"股票详情"、"AI 解读"）都用 `RouterNavBar`，标题被渲染为青→紫渐变，这是 Kuikly 模板招牌色，与 `StockDesignTokens.brand = #246BFD` 及 `DESIGN.md` 的"克制白色表面"完全冲突。codex 优化 UI 时整块遗漏了导航栏。
- 建议：改造 `RouterNavBar`，标题用 `primaryText` 纯色、去掉渐变，返回箭头改为与品牌一致的自绘图标，导航栏背景与页面统一。

### B2（高）详情页导航标题与正文重复显示股票名

- 位置：`StockDetailPage.kt:36-37`（导航 `title = detail.quote.name`）与 `StockDetailPage.kt:159-166`（`StockDetailIdentity` 内 22sp 股票名 + 代码）
- 现象：导航栏已经显示"腾讯控股"，正文又用 22sp 大标题再显示一遍"腾讯控股"。"重复大标题"是 `UI优化建议.md` 和 `DESIGN.md` 已点出的问题，但只改了首页，详情页未处理。
- 建议：导航栏保留返回 + 简短标题，正文只保留一层身份区（名字+代码），或反过来导航栏只留返回，身份区承担主标题。

### B3（中）底部导航用 unicode 占位字符，且"★"语义冲突

- 位置：`StockAppShell.kt:33-35`
- 现象：三个 Tab 图标分别是 `"▦"`（行情）、`"★"`（自选）、`"AI"`（文字）。`"▦"` 表达行情很抽象，`"AI"` 是文字非图标，`"★"` 与列表行的自选星标重复，选中态下易混淆。且 `DESIGN.md` 要求"底部导航顶部加入细分隔线"，当前 `StockBottomBar` 直接白底无分隔线。
- 建议：换用语义明确的图标（或至少行情用更贴近的符号），自选 Tab 与列表星标区分；底部栏顶部加 1dp 分隔线。

### B4（中）详情页仍是"每块一卡片"，与 DESIGN.md 理念有张力

- 位置：`StockDetailPage.kt`（价格面板、指标面板、走势图、AI 卡片均为独立白色圆角卡片浮于 `#F6F7F9` 灰底）
- 现象：`DESIGN.md` 声明"以白底加细分隔线为主，减少每个区块都做浮层卡片"，但详情页四大区块全部是独立圆角卡片，仍是卡片式布局。只有行情列表用了分隔线。
- 建议：要么把 `DESIGN.md` 的措辞调整为"聚合区块用卡片"并接受现状，要么在详情页改为分组标题 + 分隔线的轻量结构。二选一，避免规范与实现长期背离。

### B5（低）`formatStockVolume` 输出带冗余 `.0`

- 位置：`StockUiComponents.kt:29-30`
- 现象：`1832万` 会被输出成 `"1832.0万"`（`value / 10_000.0` 直接浮点除法后字符串插值）。
- 建议：格式化函数统一做整数判断，整数部分不带小数点；成交量、成交额共用一套数值格式化。

---

## 四、数据与模型问题

### C1（中）模型存在 UI 未使用的死字段

- 位置：`AiAnalysis.signalInterpretation`（`StockModels.kt:68`）、`MarketSummary.totalCount`（`StockModels.kt:25`）
- 现象：`signalInterpretation` 在详情 AI 区与 AI 页卡片均未渲染；`totalCount` 在市场概览未展示。模型比 UI 富，属于未闭合的契约。
- 建议：要么在 UI 补上展示，要么从模型移除；标注为"Task 2 / 后续"需有明确出处，避免 dead field 长期存在。

### C2（中）Mock"成交额"语义为样本之和，非市场口径

- 位置：`StockRepository.kt:170`（`turnover = details.sumOf { it.turnover }`）
- 现象：市场概览"成交额"= 5 只样本股票成交额之和约 158 亿，而 `DESIGN.md` 首页草图示意"成交额 1.26 万亿"。作为"演示数据"可接受，但"市场成交额"用 5 只样本之和表达，概念上是"样本成交额"。
- 建议：文案改成"样本成交额"或单独 mock 一个接近真实量级的市场成交额字段，避免误导。

### C3（低）`symbol` 是 `code` 的别名，未体现真实语义

- 位置：`StockModels.kt:12-13`（`val symbol get() = code`）
- 现象：`AGENTS.md` 约定"通过 code 或 symbol 定位"，但 `symbol` 实际就是 `code` 的 getter，没有真正的市场后缀（如 `00700.HK`）。路由兼容逻辑（`getDetail` 里 code/symbol 二选一）实际只匹配到同一个值。
- 建议：要么去掉 `symbol` 别名、统一用 `code`，要么引入真实 symbol 字段并明确 `code` 与 `symbol` 的映射关系。

### C4（低）走势图时间刻度语义跨周期不一致 + magic number

- 位置：`StockRepository.kt:224-225`（分时 `"${index+9}:30"`，日K `"${index+1}日"`）、`StockTrendChart.kt:129`（单点 `lineTo(..., 66f)` 硬编码）
- 现象：同一 `TrendPoint.time` 在不同周期语义不同（分时是时刻，日K是"第 N 日"）；单点数据画到固定 y=66f 的 magic number；走势图有昨收基准线但没有在图上标注基准线含义，缺 y 轴刻度与最高/最低横向参考线。
- 建议：`TrendPoint` 增加周期或时间类型字段；单点数据直接画点而非画横线；基准线、最高/最低线加图例或 y 轴标签。

---

## 五、文档一致性

### D1（高）四份文档对"UI P0 完成度"定义不一致

- 位置：`TASKS.md:28-41`、`PROJECT_PROGRESS.md:5-8`、`UI优化建议.md:137-159`、`DESIGN.md`
- 现象：
  - `TASKS.md` 与 `PROJECT_PROGRESS.md` 声称"UI P0 正式化改造已完成"；
  - `UI优化建议.md` 的 P0 清单里还有大量未落地项（搜索入口、市场切换、列表排序、下拉刷新、AI 页"问问 AI"主操作等），代码里均未实现；
  - `DESIGN.md` 与 `UI优化建议.md` 本身是"建议/规范"性质，但被进度文档表述成"已完成"，容易误导后续任务。
- 建议：明确区分"规范/建议文档"与"已完成实现"。进度文档应只标记代码里真实存在的项，未做的 P0 项移入"待办"，避免 Task 2 开始时误以为这些能力已就绪。

### D2（低）`UI优化建议.md` 部分条目与代码已脱节

- 位置：`UI优化建议.md:144`（"去除低价值趋势箭头"）
- 现象：`StockQuoteRow` 当前已无箭头、无迷你走势，该条建议在代码里已经完成，但文档仍列为待办 P0 项。
- 建议：审查完成后同步一次文档，删除已落地项或标记"已实现"。

---

## 六、测试覆盖

### T1（中）测试只覆盖 Repository，格式化与状态逻辑零覆盖

- 位置：`StockRepositoryTest.kt:1-56`
- 现象：现有测试只验证 5 条样本、详情/AI 解析、市场摘要计算、未知代码 null。以下均无覆盖：
  - `formatStockPrice / formatStockVolume / formatStockTurnover` 的负数、0、大数、`.0` 冗余边界；
  - `stockChangeColor` 的涨/跌/平三态；
  - `WatchlistStore` 的 toggle / filter；
  - Mock 数据内部一致性（`price - previousClose == change`、`changePercent` 与 `change` 的换算）。
- 建议：补充纯函数单测（格式化、颜色、自选 store），并加一条数据一致性断言，防止 Mock 数据手工漂移。

---

## 七、改进优先级

### P0（先止血，与"正式产品观感"强相关）

1. 改造 `RouterNavBar`：去掉青紫渐变标题，统一为品牌蓝/主文字纯色（B1）。
2. 底部导航改为 replace / `closeSamePage` 语义，消除返回栈泄漏（A1）。
3. 收敛自选状态为单一 observable 源，去掉 `favoriteVersion` 手动失效（A2）。
4. 修复详情页导航与正文的重复标题（B2）。

### P1（架构清理，Task 2 前应完成）

1. Repository 接口改异步（suspend/callback），Mock 加模拟延迟，让 loading 态真实可见（A3）。
2. 清理 `BridgeModule` / `RouterPage` 模板残留，抽出 `RouterNavBar`（A4/A5）。
3. 补格式化、颜色、自选 store、数据一致性的单测（T1）。
4. 处理死字段与"成交额"语义（C1/C2）。

### P2（体验细化，可与 Task 2 并行）

1. 底部导航图标换语义化图标 + 顶部分隔线（B3）。
2. 走势图补 y 轴刻度、基准线标注、单点处理与周期语义（C4）。
3. 统一"卡片 vs 分隔线"设计取向，修订 `DESIGN.md` 措辞（B4）。
4. 同步四份文档对 P0 完成度的定义（D1/D2）。
