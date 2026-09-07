# TASKS.md

## Task 1：AI 股票行情原型 Demo

### 需求

1. 行情列表页：展示股票名称、代码、最新价、涨跌额、涨跌幅，支持滚动和点击进入详情。
2. 股票详情页：展示名称、代码、最新价、涨跌幅、最高价、最低价、成交量等基础行情。
3. AI 分析与解读模块：以卡片、标签或文本区域展示趋势判断、操作提示、风险提醒、信号解读或行情总结。

## Task 2：AI 股票问答应用 Demo

### 需求

1. AI 聊天主页：支持输入问题、发送消息、展示会话记录。
2. AI 返回内容渲染：支持 Markdown 文本，以及股票、指数、行情摘要等结构化卡片或图表形态。
3. 股票/指数详情承接页：至少支持一个聊天结果跳转到详情页，展示基础行情、走势、摘要或 AI 解读。

## 当前完成状态

- [x] 完成项目目录、构建配置和核心源码勘察
- [x] 确认现有 Kuikly 页面入口、路由模块和状态响应式能力
- [x] 创建项目级开发约束文档
- [x] Task 1 页面和业务能力
- [ ] Task 2 页面和业务能力
- [ ] 真实行情接口或真实 AI 服务接入

## Task 1 详细计划：AI 股票行情原型 Demo

目标：完成一条可演示的“行情列表 → 股票详情 → AI 分析”链路。第一阶段使用 `commonMain` 和 Mock 数据，不接入真实行情服务。

### 范围与交付物

- `stock_home`、`stock_detail` 两个 Kuikly 页面。
- 可被两个 Task 复用的股票模型、Mock Repository 和 AI 分析模型。
- 从行情列表点击股票进入详情的完整路由链路。

```text
Task 1
├── 1. 模型契约
├── 2. Mock 数据与 Repository
├── 3. stock_home 页面骨架
├── 4. 列表展示与详情路由
├── 5. stock_detail 页面
├── 6. AI 分析模块
└── 7. 编译与跨端验收
```

### 0. 基线确认

- 沿用现有 `BasePager`、`@Page`、`RouterModule` 和 `observable`。
- 页面参数从 `pagerData.params` 读取，业务实现优先放在 `shared/src/commonMain`。

### 1. 模型契约

- 定义股票报价、股票详情、走势点和 AI 分析的数据结构。
- 统一使用 `code` 作为详情定位字段，必要时兼容 `symbol`。
- 产出：可被列表页、详情页和后续 Task 2 复用的共享 Model。

### 2. Mock 数据与 Repository

- 准备至少 5 条稳定的股票样本，包含名称、代码、价格、涨跌、最高价、最低价、成交量和走势数据。
- 提供按列表读取、按代码读取详情、读取 AI 分析的 Repository 接口及 Mock 实现。
- 未知股票代码返回明确的空结果或错误结果。
- 产出：页面不直接拼装数据，替换真实 API 时不需要改 UI 结构。

### 3. `stock_home` 页面骨架

- 使用 `@Page("stock_home")` 注册页面并继承 `BasePager`。
- 复用现有导航栏模式，使用 Kuikly `View`、`Text`、`Scroller` 等跨端组件。
- 准备加载、空数据和错误状态；页面级状态使用 `observable`。

### 4. 列表展示与详情路由

- 每行展示股票名称、代码、最新价、涨跌额和涨跌幅。
- 红涨绿跌或其他颜色规则保持全局统一。
- 支持列表滚动和点击整行。
- 使用 `RouterModule.openPage("stock_detail", pageData)` 传递股票 `code`/`symbol`。

### 5. `stock_detail` 页面

- 使用 `@Page("stock_detail")` 注册并从 `pagerData.params` 读取股票标识。
- 展示名称、代码、最新价、涨跌幅、最高价、最低价、成交量等基础行情。
- 增加简洁的走势区域，优先复用 Kuikly Canvas 或已有跨端 View 能力。
- 无效或缺失股票标识时提供可识别的错误/空状态。

### 6. AI 分析模块

- 展示趋势判断、操作提示、风险提醒、信号解读和行情总结中的至少三类内容。
- 使用卡片、标签或文本区分不同信息，避免与基础行情混在一起。
- AI 内容由 Mock Repository 提供，页面只负责状态和展示。

### 7. 编译与跨端验收

- 修改后执行可用的 shared 编译；环境阻塞时记录失败位置和原因。
- 条件允许时执行 `:androidApp:assembleDebug`，并在 Android 宿主验证 `stock_home → stock_detail`。
- 检查页面状态更新、返回操作、参数传递和无关文件变更。

## Task 1 验收标准

- [x] 存在可被 Kuikly 注册的 `stock_home` 和 `stock_detail` 页面，页面继承 `BasePager`。
- [x] `stock_home` 能滚动展示至少五只确定性 Mock 股票，并显示名称、代码、价格、涨跌额、涨跌幅。
- [x] 点击任意股票后，通过 `RouterModule` 携带 `code` 或 `symbol` 进入对应详情页。
- [x] `stock_detail` 能根据路由参数展示正确股票的基础行情和至少一个走势区域。
- [x] 详情页有独立 AI 分析区域，同时展示趋势判断、操作提示、风险提醒和行情总结四类信息。
- [x] 加载、空数据和无效参数均有可识别反馈，状态字段使用 `observable` 驱动 UI 更新。
- [x] 页面和业务模型位于 `shared/src/commonMain`，未引入 Android Compose、Fragment 或 ViewModel。
- [x] Mock 数据不依赖网络且结果稳定；Task 2 可复用模型、Repository、详情页和通用组件。
- [ ] 执行 `:shared:compileKotlinJvm`，条件允许时执行 `:androidApp:assembleDebug`；代码变更经过 `git diff` 检查且无无关修改。

### Task 1 暂不包含

- 真实行情接口、真实 AI 服务、登录、交易功能。
- 复杂图表交互、生产级数据刷新和大规模架构重构。

## Task 2：AI 股票问答应用 Demo

1. 复用 Task 1 的股票模型、Repository、详情页和通用 UI。
2. 实现 `stock_chat` 的消息列表、输入框、发送操作和 Mock AI 回复。
3. 将 AI 回复拆分为 Markdown 文本和股票/指数结构化内容。
4. Markdown 内容使用可用的 Kuikly Markdown 能力，结构化内容使用业务组件渲染。
5. 从聊天中的股票或指数卡片跳转到公共 `stock_detail` 页面。

## 公共收尾与验证

1. 统一导航栏、卡片、颜色、涨跌样式和加载/空状态。
2. 检查 `commonMain` 与各宿主的职责边界。
3. 每个小功能点完成后执行对应编译或测试并检查 `git diff`。
4. 未经明确要求不自动 commit，不进行无关重构或依赖升级。
