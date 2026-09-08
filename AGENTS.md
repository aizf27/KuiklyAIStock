# AGENTS.md

## 项目背景

KuiklyAIStock 是基于腾讯 Kuikly 跨端框架的股票类 AI Demo 工程，目标是完成两个开源实战 Task，并在 Android、Web/JS 及预留的 iOS、OpenHarmony 宿主中复用 shared 层业务页面。

## Task 目标

- Task 1：基于 Kuikly 开发 AI 股票行情原型 Demo，包含行情列表、股票详情和 AI 分析/解读模块。
- Task 2：基于 Kuikly 开发 AI 股票问答应用 Demo，包含聊天主页、消息记录、Markdown/结构化 AI 回复，以及跳转股票或指数详情页。

Task 的拆解、实施顺序和验收标准以根目录 `TASKS.md` 为准；本文件负责全局开发约束。

## 当前技术栈

- Kotlin Multiplatform，共享业务代码位于 `shared/src/commonMain`。
- Kuikly Open 2.7.0-Kotlin 2.1.21，使用 Kuikly Gradle 插件、KSP 和 Android 渲染器。
- Kotlin 2.1.21；Android Gradle Plugin 配置以根构建脚本的 7.4.2 为准；Gradle Wrapper 8.5。
- Android compileSdk 34，Android App minSdk 23、targetSdk 30；shared minSdk 21、targetSdk 30。
- Android Java/Kotlin 编译目标为 1.8；当前本机 JDK 17。

## 开发原则

- 当前优先复用现有 Kuikly 能力和跨端 `commonMain` 实现。
- 不随意升级 Kotlin、Gradle、AGP、Kuikly 或第三方依赖；升级必须有明确需求和验证结果。
- 遵循最小修改原则，只改动完成目标所需的文件。
- 不修改无关代码；修改前先分析现有入口、路由和生命周期。
- 每次修改后进行适合的编译或测试；构建受环境问题影响时记录具体原因。
- Git 提交保持原子化，每个小功能点单独提交，提交信息使用中文。
- 未经明确要求不要自动 commit。
- Android 业务注释使用简短单行 `//`，关键网络请求、成功/失败分支和导航跳转补充日志。
- Kuikly 页面和组件优先保持跨端兼容，避免直接依赖 Android 专有 UI。
- 文档中的规范服务于后续实现，不代表当前 Task 已完成；开始编码前应先核对 `TASKS.md` 的当前状态。

## Kuikly 开发范式

### 页面

- 页面使用 Kuikly 的 `@Page` 机制注册。
- 页面 Pager 继承当前项目已有的 `BasePager`。
- 页面 UI 统一在 `body(): ViewBuilder` 中通过 Kuikly DSL 构建。
- 不使用 Android Jetpack Compose、XML、Fragment 等 UI 方案。

### 状态

- 页面级状态放在对应 Pager。
- 使用 Kuikly 当前项目已有的 `observable` 响应式机制。
- 不引入 Android Compose 的 `remember`、`mutableStateOf`。
- 不引入 Android Architecture Components 的 ViewModel、LiveData、StateFlow，除非项目现有架构明确要求。
- 不把业务数据处理逻辑直接塞入 `body()`。

### 路由

- 页面跳转统一使用 Kuikly 当前项目已有的 `RouterModule`。
- 页面名称保持统一：`stock_home`、`stock_detail`、`stock_chat`。
- 页面之间通过路由参数传递业务标识。
- 股票详情页统一通过股票 `code` 或 `symbol` 定位股票。
- Task 1 和 Task 2 的股票详情跳转必须复用同一个详情页。

### 数据和业务逻辑

- 共享业务逻辑优先放在 `shared/src/commonMain`。
- 优先采用 Repository + Model 的方式隔离数据来源。
- 第一阶段股票数据和 AI 数据使用 Mock Repository。
- UI 不直接持有或拼装复杂业务数据。
- 为后续真实网络 API、AI API 替换保留清晰边界。

### 跨端

- 优先使用 Kuikly 提供的跨端组件和能力。
- 避免直接依赖 Android 专有 UI API。
- Android、iOS、Web、OpenHarmony 宿主只在确有平台差异时增加平台代码。
- 业务页面和业务组件原则上保持在 `commonMain`。

### 组件复用

- Task 1 与 Task 2 优先复用已有股票模型、股票详情页、指标组件、Mock Repository、导航和通用 UI。
- 不为相同业务重复创建模型或组件。
- 发现已有 Kuikly 组件能够满足需求时，优先复用而不是重新实现。

### 开发顺序

- 修改代码前先分析当前实现和调用关系。
- 先给出最小实现方案，再开始修改。
- 不因为个人代码偏好对现有 Kuikly 工程进行大规模重构。
- 不随意升级 Gradle、Kotlin、AGP、Kuikly 或第三方依赖。
- 每次功能修改后进行对应编译或测试。
- 完成任务后检查 git diff，确保没有无关修改。
- 具体任务拆解、交付物和验收标准以根目录 `TASKS.md` 为执行基线。

### 当前推荐页面结构

```text
stock_home  -> stock_detail
stock_chat  -> stock_detail
```

- `stock_home`：股票行情列表。
- `stock_detail`：股票详情 + AI 分析。
- `stock_chat`：AI 股票问答。
- `stock_detail` 是 Task 1 和 Task 2 的公共承接页面。

以后实现任何功能时，都必须优先遵循本章节规范。如果现有项目代码与规范存在冲突，先分析现有实现和仓库约定，不要擅自重构或修改规范。
