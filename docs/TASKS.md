# 任务清单

## Task 1：AI 股票行情 Demo —— 已完成

行情列表 → 股票详情 → AI 解读的完整链路，本地 Mock 数据，代码位于 `shared/src/commonMain`。

- 行情首页单页三 Tab（行情/自选/AI解读）+ 市场摘要 + 高密度列表。
- 详情页：价格、关键行情、分时/日K 走势、AI 观点分层，路由仅传 `code`。
- 加载/空/错误/重试响应式状态，`vif`/`velse` 驱动。
- 自选状态收敛到 `WatchlistStore`，Repository 异步化 + 请求序号保护。

### 个股详情 AI 分析增强与 DeepSeek 接入 —— 已完成代码实现

- 默认仅展示趋势、周期、综合判断、观察条件和首要风险，完整依据按需展开。
- 共享层新增远程 AI Repository、结构化 JSON 校验、失败降级和进程内缓存。
- Android Debug 通过 `HttpURLConnection` 直连 DeepSeek；Key 仅由本机 `local.properties` 注入。
- iOS、鸿蒙预留同名 Bridge 协议，未实现能力时继续展示 Mock，不等待网络。
- 行情仍为本地演示数据；真实模型结果不得表述为实时行情分析。
- 2026 年 9 月 11 日共享层编译、单元测试及 Android Kotlin 编译已通过。

待验收：Android 真机远程成功、断网、无效 Key、额度不足、缓存命中、快速切股、窄屏布局和展开交互；未标记为真机通过。

## Task 2：AI 股票问答 Demo —— 待做

- 聊天主页：输入、发送、会话记录。
- AI 回复渲染：Markdown + 股票/指数结构化卡片。
- 聊天结果跳转详情页，复用 Task 1 的模型、Repository、详情页与通用 UI。

## 明确延期

- 搜索、提醒、行情分类、消息中心。
- 周K/月K、缩放、十字光标、全屏、深色模式。
- 真实行情接口、正式环境服务端 AI 代理，以及 iOS/鸿蒙宿主 AI Bridge。
