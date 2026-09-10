# AGENTS.md

## 项目与执行基线

KuiklyAIStock 是基于 Kuikly 的 Kotlin Multiplatform 股票 AI Demo。共享页面和业务代码位于 `shared/src/commonMain`，Task 拆解与验收以 `docs/TASKS.md` 为准。

当前核心版本：Kuikly Open `2.7.0-Kotlin 2.1.21`、Kotlin `2.1.21`、Gradle `8.5`、AGP `7.4.2`、compileSdk `34`、JDK `17`。没有明确需求时禁止升级依赖或构建工具。

## 开发约束

- 优先复用现有 Kuikly 能力，在 `commonMain` 实现跨端页面和业务。
- 遵循最小修改原则；先确认入口、路由、生命周期和现有组件，不做无关重构。
- 页面使用 `@Page` 注册，Pager 继承 `BasePager`，UI 在 `body(): ViewBuilder` 中使用 Kuikly DSL。
- 不使用 Compose、XML、Fragment；不引入 `remember`、`mutableStateOf`、ViewModel、LiveData 或 StateFlow。
- 页面状态使用项目现有 `observable`；条件渲染优先使用 `vif` / `velse` 和响应式 getter。
- 业务数据采用 Model + Repository；Mock 数据保留清晰替换边界，UI 不拼装复杂业务数据。
- 仅在确有平台差异时增加宿主代码，避免共享层依赖 Android 专有 API。
- 相同股票模型、详情页、指标、导航和通用组件必须复用。

## 路由约定

```text
stock_home  -> stock_detail
stock_chat  -> stock_detail
```

- `stock_home`：行情、自选和 AI 解读主页面。
- `stock_detail`：Task 1、Task 2 共用的股票详情页，通过 `code` 或 `symbol` 定位股票。
- `stock_chat`：AI 股票问答页。
- 页面跳转统一使用现有 `RouterModule`。

## UI 约定

- 颜色、间距、圆角和尺寸统一使用 `StockDesignTokens`；详细规范见 `DESIGN.md`。
- 顶部栏消费状态栏、刘海和安全区；底部导航消费系统手势区。
- 页面内容左右边距统一为 `16dp`，卡片圆角 `12dp`，最小触控区域 `44dp × 44dp`。
- 卡片、列表和走势图显式限制在内容宽度内，不依赖内容自适应撑宽。
- 行情涨跌同时使用颜色和正负号表达。

## 工程与 Git

- 修改前检查工作区，禁止覆盖用户或其他任务的未提交改动。
- 每次修改后执行对应 Kotlin 编译和 `git diff --check`；完整 APK 构建由需求决定。
- 注释使用简短单行 `//`；关键请求、成功/失败分支和导航跳转保留必要日志。
- 文档必须精简、结构清晰，避免长篇原理和大段代码。
- 未经明确要求不要自动提交。
- 需要提交时保持原子化，每个小功能单独提交，提交信息使用中文。

## 实施流程

1. 对照 `docs/TASKS.md` 确认当前目标。
2. 分析现有实现并给出最小方案。
3. 完成必要修改，不触碰无关文件。
4. 编译、检查 diff，并记录环境限制和待真机验证项。
