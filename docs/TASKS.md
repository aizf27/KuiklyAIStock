# 任务清单

## Task 1：AI 股票行情 Demo —— 已完成

行情列表 → 股票详情 → AI 解读的完整链路，默认使用腾讯真实行情快照，Mock 仅用于测试和显式演示，代码位于 `shared/src/commonMain`。

- 行情首页单页三 Tab（行情/自选/AI解读）+ 市场摘要 + 高密度列表。
- 详情页：价格、关键行情、分时/日K 走势、AI 观点分层，路由仅传 `code`。
- 加载/空/错误/重试响应式状态，`vif`/`velse` 驱动。
- 自选状态收敛到 `WatchlistStore`，Repository 异步化 + 请求序号保护。

### 个股详情 AI 分析增强与 DeepSeek 接入 —— 已完成代码实现

- 默认仅展示趋势、周期、综合判断、观察条件和首要风险，完整依据按需展开。
- 共享层新增远程 AI Repository、结构化 JSON 校验、失败降级和进程内缓存。
- Android Debug 通过 `HttpURLConnection` 直连 DeepSeek；Key 仅由本机 `local.properties` 注入。
- iOS、鸿蒙预留同名 AI Bridge 协议，未实现能力时不发远程 AI 请求。
- DeepSeek 只接收真实行情快照；未接入真实分时和日 K 时不推断时序走势。
- 2026 年 9 月 11 日共享层编译、单元测试及 Android Kotlin 编译已通过。

待验收：Android 真机远程成功、断网、无效 Key、额度不足、缓存命中、快速切股、窄屏布局和展开交互；未标记为真机通过。

### 腾讯真实行情快照接入 —— 已完成代码实现

- `commonMain` 新增腾讯代码映射、GBK 解码入口、纯 Kotlin 解析器、Kuikly `NetworkModule` Transport、共享缓存和 Repository。
- 首页批量加载配置股票池与三大指数，每 30 秒刷新；详情每 15 秒刷新，并处理部分成功、缓存、过期和旧响应丢弃。
- 首页、自选、持仓、模拟交易和详情共用最新报价；真实行情无时序数据时显示“暂无真实走势数据”。
- Android 与 JS 编译已覆盖平台 GBK 实现；iOS、鸿蒙尚未做宿主和真机验证。
- 2026 年 9 月 11 日共享层编译、单元测试、Android Kotlin 编译及 JS 编译已通过。

待验收：Android 真机核对腾讯字段、中文名称、断网/超时/HTTP 错误、部分缺失、定时刷新、快速切股和页面销毁。

## Task 2：AI 股票问答 Demo —— 待做

- 聊天主页：输入、发送、会话记录。
- AI 回复渲染：Markdown + 股票/指数结构化卡片。
- 聊天结果跳转详情页，复用 Task 1 的模型、Repository、详情页与通用 UI。

## 明确延期

- 搜索、提醒、行情分类、消息中心。
- 周K/月K、缩放、十字光标、全屏、深色模式。
- 真实分时/K 线、全市场搜索、正式环境服务端 AI 代理，以及 iOS/鸿蒙宿主 AI Bridge 与真机验证。
