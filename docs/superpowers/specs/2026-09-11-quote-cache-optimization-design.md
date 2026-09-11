# 行情缓存与刷新优化设计文档

**日期：** 2026-09-11  
**作者：** Claude  
**状态：** 待审核

---

## 目标

优化股票行情数据的缓存和请求策略，提升用户体验：

1. 首页启动时立即展示缓存数据，60秒定时刷新
2. 详情页进入时立即展示数据，提供手动刷新按钮
3. 引入 SQLDelight 数据库实现数据持久化
4. 扩展数据模型支持分时、K线、换手率、市盈率（本期用模拟数据）
5. AI 分析结果持久化，支持手动刷新

---

## 技术方案

### 架构选择

采用**渐进式迁移方案**，保持现有功能稳定的前提下引入数据库层。

**数据流向：**
```
UI Layer (StockHomePage/StockDetailPage)
    ↓ 
Repository Layer (TencentStockRepository)
    ↓ 优先读取              ↓ 网络请求
Database Layer          Network Layer (TencentQuoteTransport)
    ↑ 写入成功结果
```

**核心原则：**
- Database 是 single source of truth
- UI 优先展示数据库数据（冷启动快）
- 网络请求成功后更新数据库，失败时保留旧数据
- 保留内存缓存 `TencentQuoteCache` 用于请求去重

---

## 数据库设计

### 技术选型

**SQLDelight** - KMP 官方推荐的跨平台 SQL 方案，支持后续 iOS 迁移。

### 表结构

#### 1. StockQuote（股票快照）

```sql
CREATE TABLE StockQuote (
  code TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  price REAL NOT NULL,
  change REAL NOT NULL,
  changePercent REAL NOT NULL,
  open REAL NOT NULL,
  previousClose REAL NOT NULL,
  high REAL NOT NULL,
  low REAL NOT NULL,
  volume REAL NOT NULL,
  turnover REAL NOT NULL,
  turnoverRate REAL,
  peRatio REAL,
  updatedAt TEXT NOT NULL,
  cachedAt INTEGER NOT NULL
);
```

#### 2. IntradayTrend（分时数据）

```sql
CREATE TABLE IntradayTrend (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL,
  time TEXT NOT NULL,
  price REAL NOT NULL,
  volume REAL NOT NULL,
  FOREIGN KEY(code) REFERENCES StockQuote(code) ON DELETE CASCADE
);
CREATE INDEX idx_intraday_code ON IntradayTrend(code);
```

#### 3. KLineData（K线数据）

```sql
CREATE TABLE KLineData (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL,
  period TEXT NOT NULL,
  date TEXT NOT NULL,
  open REAL NOT NULL,
  close REAL NOT NULL,
  high REAL NOT NULL,
  low REAL NOT NULL,
  volume REAL NOT NULL,
  FOREIGN KEY(code) REFERENCES StockQuote(code) ON DELETE CASCADE,
  UNIQUE(code, period, date)
);
CREATE INDEX idx_kline_code_period ON KLineData(code, period);
```

**period 枚举值：** `'daily'`, `'weekly'`, `'monthly'`, `'five_day'`

#### 4. AiAnalysisCache（AI分析缓存）

```sql
CREATE TABLE AiAnalysisCache (
  code TEXT PRIMARY KEY NOT NULL,
  content TEXT NOT NULL,
  quoteTime TEXT NOT NULL,
  analyzedAt INTEGER NOT NULL,
  FOREIGN KEY(code) REFERENCES StockQuote(code) ON DELETE CASCADE
);
```

**说明：** `content` 字段存储 JSON 序列化的 `AiAnalysis` 对象。

---

## 数据模型扩展

### StockDetail 增强

```kotlin
data class StockDetail(
    val quote: StockQuote,
    val open: Double,
    val previousClose: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val turnover: Double,
    val turnoverRate: Double?,     // 新增
    val peRatio: Double?,          // 新增
    val intradayTrend: List<TrendPoint>,
    val fiveDayTrend: List<TrendPoint>,    // 新增
    val dailyKLine: List<OhlcPoint>,       // 新增
    val weeklyKLine: List<OhlcPoint>,      // 新增
    val monthlyKLine: List<OhlcPoint>,     // 新增
)
```

### TrendPoint（走势点）

```kotlin
data class TrendPoint(
    val label: String,   // 时间标签
    val price: Double,   // 价格
    val volume: Double,  // 成交量
)
```

---

## Repository 层设计

### StockDatabaseRepository（新增）

封装数据库 CRUD 操作，隔离 SQLDelight 实现细节。

```kotlin
interface StockDatabaseRepository {
    // 快照
    suspend fun getQuote(code: String): StockQuote?
    suspend fun getAllQuotes(): List<StockQuote>
    suspend fun insertQuotes(quotes: List<StockQuote>)
    
    // K线
    suspend fun getKLines(code: String, period: String): List<KLineData>
    suspend fun insertKLines(code: String, period: String, data: List<KLineData>)
    
    // 分时
    suspend fun getIntradayTrend(code: String): List<TrendPoint>
    suspend fun insertIntradayTrend(code: String, data: List<TrendPoint>)
    
    // AI分析
    suspend fun getAiAnalysis(code: String): AiAnalysis?
    suspend fun insertAiAnalysis(code: String, analysis: AiAnalysis, quoteTime: String)
}
```

### TencentStockRepository 改造

**改造点：**

1. 注入 `StockDatabaseRepository` 依赖
2. `loadHome()` 改为"先读数据库 → 后台刷新网络"模式
3. `loadDetail()` 同理
4. 网络请求成功后写入数据库
5. 保留 `TencentQuoteCache` 避免并发重复请求

**loadHome 伪代码：**

```kotlin
override suspend fun loadHome(scope: CoroutineScope): StockLoadResult<StockHomeData> {
    // 1. 优先读数据库
    val cachedQuotes = dbRepo.getAllQuotes()
    if (cachedQuotes.isNotEmpty()) {
        val homeData = buildHomeData(cachedQuotes)
        // 立即返回，UI 可渲染
        scope.launch { refreshFromNetwork() }
        return StockLoadResult.Success(homeData)
    }
    
    // 2. 数据库为空，等待网络
    return refreshFromNetwork()
}

private suspend fun refreshFromNetwork(): StockLoadResult<StockHomeData> {
    val result = fetchAndDecode(symbols)
    when (result) {
        is DecodedQuotes.Remote -> {
            val enriched = enrichWithMockData(result.quotes)
            dbRepo.insertQuotes(enriched)
            return StockLoadResult.Success(buildHomeData(enriched))
        }
        is DecodedQuotes.Failed -> {
            val fallback = dbRepo.getAllQuotes()
            return if (fallback.isNotEmpty()) {
                StockLoadResult.Success(buildHomeData(fallback))
            } else {
                StockLoadResult.Failure(result.errorMessage)
            }
        }
    }
}
```

**loadDetail 类似，额外处理 K 线和分时数据。**

---

## 模拟数据生成

### MockDataGenerator（新增）

**位置：** `shared/src/commonMain/kotlin/repository/MockDataGenerator.kt`

**功能：**

- 生成分时数据：9:30-15:00，每分钟一条，基于当前价格随机波动
- 生成五日走势：240 个点（每日 48 个 5 分钟 K）
- 生成日 K/周 K/月 K：各 30 条，向前推算日期
- 生成换手率：随机 0.5%-15%
- 生成市盈率：随机 10-50

**调用时机：**

网络请求成功后，数据写入数据库前，调用 `enrichWithMockData()` 填充缺失字段。

**示例：**

```kotlin
fun generateIntradayTrend(basePrice: Double): List<TrendPoint> {
    val result = mutableListOf<TrendPoint>()
    var price = basePrice
    for (minute in 0..240) { // 9:30-15:00 共 240 分钟
        price += Random.nextDouble(-0.02, 0.02) * basePrice
        result.add(TrendPoint(
            label = formatTime(minute),
            price = price,
            volume = Random.nextDouble(1000.0, 10000.0)
        ))
    }
    return result
}
```

---

## UI 层改造

### StockHomePage

#### 刷新策略调整

**当前：** 30 秒自动刷新，所有 Tab 都刷新  
**调整后：** 60 秒自动刷新，只在"行情" Tab 激活时刷新

```kotlin
private fun scheduleQuoteRefresh() {
    setTimeout(60_000) {
        if (quoteRefreshActive && selectedTab == StockTabs.MARKET) {
            loadContent()
            scheduleQuoteRefresh()
        }
    }
}
```

#### 立即请求

```kotlin
override fun created() {
    super.created()
    loadContent()           // 立即请求一次
    scheduleQuoteRefresh()  // 启动定时器
}
```

#### 错误处理

网络失败时，顶部显示黄色提示条："更新失败，显示缓存数据 [重试]"

---

### StockDetailPage

#### 新增状态

```kotlin
private var refreshing by observable(false)
```

#### 布局调整

**当前 StockDetailHeader：**
```
[股票名称]                    [★关注]
[代码 · 真实行情·日期]
```

**调整后：**
```
[股票名称]                    [★关注]
[代码]
[真实行情 · 日期]             [🔄刷新]
```

**实现要点：**
- "真实行情·日期"单独一行，避免挤压关注按钮
- 刷新按钮放在此行右侧，44dp 最小触摸区域
- 刷新中显示旋转动画，禁用点击

#### 刷新逻辑

```kotlin
private fun refreshQuote() {
    if (refreshing || detailLoading) return
    refreshing = true
    val code = pagerData.params.optString("code").trim()
    lifecycleScope.launch {
        when (val result = repository.loadDetail(this, code)) {
            is StockLoadResult.Success -> {
                detail = result.data.detail
                dataSource = result.data.dataSource
                quoteTime = result.data.quoteTime
                quoteExpired = result.data.isExpired
                refreshChartData(result.data.detail)
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("刷新成功")
            }
            is StockLoadResult.Failure -> {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("刷新失败: ${result.message}")
            }
            StockLoadResult.Empty -> {}
        }
        refreshing = false
    }
}
```

#### 自动刷新保留

```kotlin
private fun scheduleQuoteRefresh() {
    setTimeout(60_000) { // 从 15 秒改为 60 秒
        if (quoteRefreshActive) {
            loadDetail()
            scheduleQuoteRefresh()
        }
    }
}
```

#### K 线图适配

`refreshChartData()` 方法根据 `selectedChartPeriod` 从 `detail` 读取对应 K 线数据：

```kotlin
private fun refreshChartData(stock: StockDetail) {
    chartCandles.clear()
    val data = when (selectedChartPeriod) {
        StockChartPeriod.INTRADAY -> stock.intradayTrend.map { toOhlc(it) }
        StockChartPeriod.FIVE_DAY -> stock.fiveDayTrend.map { toOhlc(it) }
        StockChartPeriod.DAILY -> stock.dailyKLine
        StockChartPeriod.WEEKLY -> stock.weeklyKLine
        StockChartPeriod.MONTHLY -> stock.monthlyKLine
    }
    chartCandles.addAll(data)
}
```

---

### AI 分析刷新

#### 位置

`AiAnalysisSection` 组件标题栏右侧，与"AI 智能解读"在同一行。

**布局：**
```
[AI 智能解读]           [🔄 刷新分析]
```

#### 状态

复用现有 `aiLoadState` 枚举：
- `IDLE` - 未加载
- `LOADING` - 加载中（刷新按钮显示动画）
- `SUCCESS` - 成功
- `FAILURE` - 失败

#### 刷新逻辑

```kotlin
private fun refreshAiAnalysis() {
    if (aiLoadState == AiLoadState.LOADING) return
    detail?.let {
        resetAiInteractionState()
        analyzedQuoteTime = "" // 清除已分析标记，强制重新请求
        loadRemoteAnalysis(it)
    }
}
```

#### 持久化

`RemoteAiAnalysisRepository` 改造：
1. 请求成功后写入 `AiAnalysisCache` 表
2. `loadAnalysis()` 优先从数据库读取：
   - 如果 `quoteTime` 匹配，直接返回缓存
   - 不匹配或无缓存，发起网络请求

```kotlin
override suspend fun loadAnalysis(detail: StockDetail): AiAnalysisLoadResult {
    if (!transport.isSupported()) return AiAnalysisLoadResult.Unsupported
    
    // 1. 读数据库缓存
    val cached = dbRepo.getAiAnalysis(detail.quote.code)
    if (cached != null && cached.updatedAt == detail.quote.updatedAt) {
        return AiAnalysisLoadResult.Success(cached.copy(source = AiAnalysisSource.CACHE))
    }
    
    // 2. 发起网络请求
    val request = buildRequest(detail)
    return when (val result = transport.request(request)) {
        is AiTransportResult.Success -> {
            val parsed = parseRemoteAnalysis(detail, result)
            if (parsed is AiAnalysisLoadResult.Success) {
                dbRepo.insertAiAnalysis(
                    detail.quote.code,
                    parsed.analysis,
                    detail.quote.updatedAt
                )
            }
            parsed
        }
        is AiTransportResult.Failure -> AiAnalysisLoadResult.Failure(
            type = mapFailure(result),
            message = result.message.ifBlank { defaultErrorMessage(mapFailure(result)) }
        )
    }
}
```

---

## 错误处理

### 网络失败降级

1. **首页：** 展示数据库缓存数据 + 顶部黄色提示条 "更新失败，显示缓存数据 [重试]"
2. **详情页手动刷新：** Toast 提示 "刷新失败: {错误信息}"，保持当前数据
3. **详情页自动刷新：** 静默失败，保持当前数据，只记录日志
4. **AI 分析：** `aiLoadState = FAILURE`，显示错误信息和重试按钮

### 数据库异常

- 读取失败：记录日志，fallback 到网络请求
- 写入失败：记录日志，不影响 UI 展示（数据已在内存）

### 过期数据标识

数据库缓存时间超过 2 分钟（120 秒）时，标记为"已过期"：
- 首页：`quoteExpired = true`，状态栏显示"（已过期）"
- 详情页：同理

---

## 实现步骤

### Phase 1: 数据库基础设施
1. 添加 SQLDelight 依赖到 `build.gradle.kts`
2. 定义 `.sq` 文件（4 张表 + 索引）
3. 实现 `StockDatabaseRepository` 和 `SqlDelightStockDatabaseRepository`
4. 编写单元测试

### Phase 2: Repository 改造
1. `TencentStockRepository` 注入 `StockDatabaseRepository`
2. 改造 `loadHome()` 和 `loadDetail()` 为"先读数据库 → 后台刷新"模式
3. 实现 `MockDataGenerator`
4. 改造 `RemoteAiAnalysisRepository` 支持数据库缓存

### Phase 3: 数据模型扩展
1. `StockDetail` 增加新字段
2. `TrendPoint` 新增 `volume` 字段
3. 更新序列化/反序列化逻辑

### Phase 4: UI 改造
1. `StockHomePage` 调整刷新间隔和触发条件
2. `StockDetailPage` 新增刷新按钮和状态
3. 调整 `StockDetailHeader` 布局
4. `AiAnalysisSection` 新增刷新按钮
5. 适配 K 线图展示逻辑

### Phase 5: 测试与优化
1. 冷启动测试（数据库有数据 vs 无数据）
2. 网络失败场景测试
3. 并发刷新测试
4. 数据库迁移测试
5. 性能监控（数据库读写耗时）

---

## 数据迁移

### 初次部署

用户首次安装或升级到此版本时：
1. 数据库为空，首屏必须等待网络请求
2. 网络成功后写入数据库，后续启动立即展示

### 兼容性

保留 `TencentQuoteCache` 内存缓存，确保现有逻辑不受影响。数据库作为"增强缓存"，不替代现有机制。

---

## 性能考量

### 数据库读写

- SQLDelight 生成类型安全的 Kotlin 代码，性能接近原生 SQL
- 单次查询 36 只股票 + 索引 < 10ms
- 写入操作使用事务批量插入

### 内存占用

- 36 只股票完整数据（含 K 线）约 500KB
- 数据库文件预计 < 2MB

### 网络请求

- 保持现有腾讯行情接口调用频率
- 60 秒刷新不会增加服务器压力（从 30 秒降低）

---

## 风险与缓解

### 风险 1: SQLDelight 学习成本

**缓解：** 
- Repository 接口隔离实现细节
- 提供完整的 `.sq` 示例和注释

### 风险 2: 数据库写入失败

**缓解：**
- 写入失败不影响 UI（数据已在内存）
- 记录日志便于排查

### 风险 3: 数据不一致

**缓解：**
- Database 是 single source of truth
- 网络请求成功后立即写数据库
- 使用 `cachedAt` 时间戳判断数据新旧

### 风险 4: 模拟数据被误认为真实数据

**缓解：**
- UI 明确标注"真实行情"（快照）vs "模拟走势"
- 日志记录数据来源

---

## 后续优化方向

1. 真实 K 线接口接入（替换模拟数据）
2. 数据库定期清理（保留最近 7 天）
3. 增量更新（只请求变化的股票）
4. Flow 响应式数据流（自动更新 UI）
5. 离线模式优化（完全无网络时的体验）

---

## 验收标准

- [ ] 首页冷启动，数据库有数据时，1 秒内展示 UI
- [ ] 首页自动刷新间隔 60 秒，只在"行情" Tab 生效
- [ ] 详情页进入时立即展示数据，无空白加载
- [ ] 详情页刷新按钮可用，成功后 Toast 提示
- [ ] 详情页自动刷新间隔 60 秒
- [ ] AI 分析有刷新按钮，点击后重新请求
- [ ] 网络失败时展示缓存数据，有明确提示
- [ ] K 线图可切换周期，数据正确展示
- [ ] 换手率、市盈率显示在详情页指标区
- [ ] 所有日志包含 "数据来源" 标识（database/network/cache）
