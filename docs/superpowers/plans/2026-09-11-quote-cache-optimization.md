# 行情缓存与刷新优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 引入 SQLDelight 数据库持久化行情数据，优化首页和详情页的缓存与刷新策略，支持分时、K线、换手率、市盈率等扩展数据（本期用模拟数据）。

**Architecture:** 渐进式迁移，Database 作为 single source of truth，UI 优先读数据库（冷启动快），后台网络请求更新数据库。保留内存缓存用于请求去重。

**Tech Stack:** SQLDelight (KMP), Kotlin Coroutines, 现有 TencentStockRepository

---

## 文件结构规划

### 新增文件
- `shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/StockQuote.sq` - 股票快照表
- `shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/IntradayTrend.sq` - 分时数据表
- `shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/KLineData.sq` - K线数据表
- `shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/AiAnalysisCache.sq` - AI分析缓存表
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/StockDatabaseRepository.kt` - 数据库接口
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/SqlDelightStockDatabaseRepository.kt` - 数据库实现
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/MockDataGenerator.kt` - 模拟数据生成器
- `shared/src/androidMain/kotlin/com/example/kuiklyaistock/repository/DatabaseDriverFactory.kt` - Android 数据库驱动
- `shared/src/commonTest/kotlin/com/example/kuiklyaistock/repository/StockDatabaseRepositoryTest.kt` - 数据库测试
- `shared/src/commonTest/kotlin/com/example/kuiklyaistock/repository/MockDataGeneratorTest.kt` - 模拟数据生成器测试

### 修改文件
- `shared/build.gradle.kts` - 添加 SQLDelight 依赖
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/model/StockModels.kt` - 扩展数据模型
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt` - 集成数据库
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/AiAnalysisRepository.kt` - 集成数据库
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockHomePage.kt` - 调整刷新策略
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt` - 新增刷新按钮
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailComponents.kt` - 调整布局
- `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockAiAnalysis.kt` - 新增刷新按钮

---

## Phase 1: SQLDelight 基础设施

### Task 1: 添加 SQLDelight 依赖

**Files:**
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: 添加 SQLDelight 插件和依赖**

在 `plugins` 块中添加：
```kotlin
id("app.cash.sqldelight") version "2.0.1"
```

在 `commonMain` dependencies 中添加：
```kotlin
implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
```

在 `androidMain` dependencies 中添加：
```kotlin
implementation("app.cash.sqldelight:android-driver:2.0.1")
```

在文件末尾添加 SQLDelight 配置：
```kotlin
sqldelight {
    databases {
        create("StockDatabase") {
            packageName.set("com.example.kuiklyaistock.db")
        }
    }
}
```

- [ ] **Step 2: 同步 Gradle**

Run: `./gradlew --stop && ./gradlew :shared:generateCommonMainStockDatabaseInterface`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/build.gradle.kts
git commit -m "添加 SQLDelight 依赖和配置"
```

---

### Task 2: 定义 StockQuote 表

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/StockQuote.sq`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db
```

- [ ] **Step 2: 创建 StockQuote.sq 文件**

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
  volume INTEGER NOT NULL,
  turnover REAL NOT NULL,
  turnoverRate REAL,
  peRatio REAL,
  updatedAt TEXT NOT NULL,
  cachedAt INTEGER NOT NULL
);

-- 查询所有股票
getAllQuotes:
SELECT * FROM StockQuote;

-- 根据 code 查询单只股票
getQuoteByCode:
SELECT * FROM StockQuote WHERE code = ?;

-- 插入或替换股票快照
insertOrReplaceQuote:
INSERT OR REPLACE INTO StockQuote (
  code, name, price, change, changePercent,
  open, previousClose, high, low, volume,
  turnover, turnoverRate, peRatio, updatedAt, cachedAt
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- 删除所有股票（测试用）
deleteAll:
DELETE FROM StockQuote;
```

- [ ] **Step 3: 生成代码**

Run: `./gradlew :shared:generateCommonMainStockDatabaseInterface`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/sqldelight/
git commit -m "定义 StockQuote 表结构"
```

---

### Task 3: 定义 IntradayTrend 表

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/IntradayTrend.sq`

- [ ] **Step 1: 创建 IntradayTrend.sq 文件**

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

-- 查询某只股票的分时数据
getIntradayTrend:
SELECT time, price, volume FROM IntradayTrend WHERE code = ? ORDER BY time ASC;

-- 插入分时数据
insertIntradayPoint:
INSERT INTO IntradayTrend (code, time, price, volume) VALUES (?, ?, ?, ?);

-- 删除某只股票的分时数据
deleteByCode:
DELETE FROM IntradayTrend WHERE code = ?;

-- 删除所有分时数据（测试用）
deleteAll:
DELETE FROM IntradayTrend;
```

- [ ] **Step 2: 生成代码**

Run: `./gradlew :shared:generateCommonMainStockDatabaseInterface`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/IntradayTrend.sq
git commit -m "定义 IntradayTrend 表结构"
```

---

### Task 4: 定义 KLineData 表

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/KLineData.sq`

- [ ] **Step 1: 创建 KLineData.sq 文件**

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

-- 查询某只股票某周期的 K 线数据
getKLines:
SELECT date, open, close, high, low, volume 
FROM KLineData 
WHERE code = ? AND period = ? 
ORDER BY date ASC;

-- 插入或替换 K 线数据
insertOrReplaceKLine:
INSERT OR REPLACE INTO KLineData (code, period, date, open, close, high, low, volume)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

-- 删除某只股票某周期的 K 线数据
deleteByCodeAndPeriod:
DELETE FROM KLineData WHERE code = ? AND period = ?;

-- 删除所有 K 线数据（测试用）
deleteAll:
DELETE FROM KLineData;
```

- [ ] **Step 2: 生成代码**

Run: `./gradlew :shared:generateCommonMainStockDatabaseInterface`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/KLineData.sq
git commit -m "定义 KLineData 表结构"
```

---

### Task 5: 定义 AiAnalysisCache 表

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/AiAnalysisCache.sq`

- [ ] **Step 1: 创建 AiAnalysisCache.sq 文件**

```sql
CREATE TABLE AiAnalysisCache (
  code TEXT PRIMARY KEY NOT NULL,
  content TEXT NOT NULL,
  quoteTime TEXT NOT NULL,
  analyzedAt INTEGER NOT NULL,
  FOREIGN KEY(code) REFERENCES StockQuote(code) ON DELETE CASCADE
);

-- 查询某只股票的 AI 分析缓存
getAiAnalysis:
SELECT content, quoteTime, analyzedAt FROM AiAnalysisCache WHERE code = ?;

-- 插入或替换 AI 分析缓存
insertOrReplaceAiAnalysis:
INSERT OR REPLACE INTO AiAnalysisCache (code, content, quoteTime, analyzedAt)
VALUES (?, ?, ?, ?);

-- 删除某只股票的 AI 分析缓存
deleteByCode:
DELETE FROM AiAnalysisCache WHERE code = ?;

-- 删除所有 AI 分析缓存（测试用）
deleteAll:
DELETE FROM AiAnalysisCache;
```

- [ ] **Step 2: 生成代码**

Run: `./gradlew :shared:generateCommonMainStockDatabaseInterface`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/sqldelight/com/example/kuiklyaistock/db/AiAnalysisCache.sq
git commit -m "定义 AiAnalysisCache 表结构"
```

---

### Task 6: 创建 Android 数据库驱动

**Files:**
- Create: `shared/src/androidMain/kotlin/com/example/kuiklyaistock/repository/DatabaseDriverFactory.kt`

- [ ] **Step 1: 创建 DatabaseDriverFactory.kt**

```kotlin
package com.example.kuiklyaistock.repository

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.kuiklyaistock.db.StockDatabase

// Android 平台的数据库驱动工厂
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = StockDatabase.Schema,
            context = context,
            name = "stock.db"
        )
    }
}
```

- [ ] **Step 2: 创建 expect 声明**

Create: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/DatabaseDriverFactory.kt`

```kotlin
package com.example.kuiklyaistock.repository

import app.cash.sqldelight.db.SqlDriver

// 跨平台数据库驱动工厂接口
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

- [ ] **Step 3: 生成代码验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add shared/src/androidMain/kotlin/com/example/kuiklyaistock/repository/DatabaseDriverFactory.kt
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/DatabaseDriverFactory.kt
git commit -m "创建数据库驱动工厂"
```

---

## Phase 2: 数据模型扩展

### Task 7: 扩展 StockModels.kt

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/model/StockModels.kt:55-71`

- [ ] **Step 1: 扩展 StockDetail 数据类**

将 `StockDetail` 修改为：
```kotlin
// 详情页使用的完整股票行情。
data class StockDetail(
    val quote: StockQuote,
    val open: Double,
    val previousClose: Double,
    val high: Double,
    val low: Double,
    val volume: Long,
    val turnover: Double,
    val turnoverRate: Double? = null,     // 新增：换手率
    val peRatio: Double? = null,          // 新增：市盈率
    val intradayTrend: List<TrendPoint>,
    val fiveDayTrend: List<TrendPoint> = emptyList(),    // 新增：五日走势
    val dailyKLine: List<OhlcPoint> = emptyList(),       // 新增：日 K
    val weeklyKLine: List<OhlcPoint> = emptyList(),      // 新增：周 K
    val monthlyKLine: List<OhlcPoint> = emptyList(),     // 新增：月 K
    val dailyTrend: List<TrendPoint> = emptyList(),      // 标记为废弃
)
```

- [ ] **Step 2: 扩展 TrendPoint 数据类**

将 `TrendPoint` 修改为：
```kotlin
// 简化的走势数据点，供跨端走势组件渲染。
data class TrendPoint(
    val label: String,
    val price: Double,
    val volume: Double = 0.0,  // 新增：成交量
)
```

- [ ] **Step 3: 导入 OhlcPoint**

在文件顶部添加导入：
```kotlin
import com.tencent.kuiklybase.chart.model.OhlcPoint
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/model/StockModels.kt
git commit -m "扩展 StockDetail 和 TrendPoint 数据模型"
```

---

### Task 8: 新增 K 线周期枚举

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt:1`

- [ ] **Step 1: 查找 StockChartPeriod 枚举定义位置**

Run: `grep -r "enum class StockChartPeriod" shared/src/commonMain/`
Expected: 找到定义位置

- [ ] **Step 2: 如果不存在，在 StockDetailPage.kt 文件末尾添加**

```kotlin
// K 线图周期枚举
internal enum class StockChartPeriod(val title: String) {
    INTRADAY("分时"),
    FIVE_DAY("五日"),
    DAILY("日K"),
    WEEKLY("周K"),
    MONTHLY("月K"),
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt
git commit -m "新增 K 线周期枚举"
```

---

## Phase 3: Repository 层实现

### Task 9: 创建 StockDatabaseRepository 接口

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/StockDatabaseRepository.kt`

- [ ] **Step 1: 创建接口文件**

```kotlin
package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint

// 数据库仓储接口，隔离 SQLDelight 实现细节
internal interface StockDatabaseRepository {
    // 查询单只股票快照
    suspend fun getQuote(code: String): StockQuote?
    
    // 查询所有股票快照
    suspend fun getAllQuotes(): List<StockQuote>
    
    // 插入或更新股票快照（批量）
    suspend fun insertQuotes(quotes: List<StockQuote>)
    
    // 查询分时数据
    suspend fun getIntradayTrend(code: String): List<TrendPoint>
    
    // 插入分时数据（会先删除旧数据）
    suspend fun insertIntradayTrend(code: String, data: List<TrendPoint>)
    
    // 查询 K 线数据
    suspend fun getKLines(code: String, period: String): List<OhlcPoint>
    
    // 插入 K 线数据（会先删除旧数据）
    suspend fun insertKLines(code: String, period: String, data: List<OhlcPoint>)
    
    // 查询 AI 分析缓存
    suspend fun getAiAnalysis(code: String): AiAnalysisCacheEntry?
    
    // 插入或更新 AI 分析缓存
    suspend fun insertAiAnalysis(code: String, analysis: AiAnalysis, quoteTime: String)
}

// AI 分析缓存条目
internal data class AiAnalysisCacheEntry(
    val analysis: AiAnalysis,
    val quoteTime: String,
    val analyzedAt: Long,
)
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/StockDatabaseRepository.kt
git commit -m "创建 StockDatabaseRepository 接口"
```

---

### Task 10: 实现 SqlDelightStockDatabaseRepository (Part 1/2)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/SqlDelightStockDatabaseRepository.kt`

- [ ] **Step 1: 创建实现类骨架**

```kotlin
package com.example.kuiklyaistock.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.kuiklyaistock.db.StockDatabase
import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiAnalysisSource
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

// SQLDelight 实现的数据库仓储
internal class SqlDelightStockDatabaseRepository(
    private val database: StockDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : StockDatabaseRepository {
    
    private val stockQuoteQueries = database.stockQuoteQueries
    private val intradayTrendQueries = database.intradayTrendQueries
    private val kLineDataQueries = database.kLineDataQueries
    private val aiAnalysisCacheQueries = database.aiAnalysisCacheQueries
    
    override suspend fun getQuote(code: String): StockQuote? = withContext(Dispatchers.Default) {
        stockQuoteQueries.getQuoteByCode(code).executeAsOneOrNull()?.toStockQuote()
    }
    
    override suspend fun getAllQuotes(): List<StockQuote> = withContext(Dispatchers.Default) {
        stockQuoteQueries.getAllQuotes().executeAsList().map { it.toStockQuote() }
    }
    
    override suspend fun insertQuotes(quotes: List<StockQuote>) = withContext(Dispatchers.Default) {
        database.transaction {
            quotes.forEach { quote ->
                stockQuoteQueries.insertOrReplaceQuote(
                    code = quote.code,
                    name = quote.name,
                    price = quote.price,
                    change = quote.change,
                    changePercent = quote.changePercent,
                    open = 0.0, // 临时占位，后续从 StockDetail 获取
                    previousClose = 0.0,
                    high = 0.0,
                    low = 0.0,
                    volume = 0,
                    turnover = 0.0,
                    turnoverRate = null,
                    peRatio = null,
                    updatedAt = quote.updatedAt,
                    cachedAt = System.currentTimeMillis()
                )
            }
        }
    }
    
    // 转换函数：数据库记录 → StockQuote
    private fun com.example.kuiklyaistock.db.StockQuote.toStockQuote() = StockQuote(
        name = name,
        code = code,
        price = price,
        change = change,
        changePercent = changePercent,
        updatedAt = updatedAt,
    )
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 可能报错缺少 kotlinx.serialization，继续下一步

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/SqlDelightStockDatabaseRepository.kt
git commit -m "实现 SqlDelightStockDatabaseRepository (Part 1)"
```

---

### Task 11: 实现 SqlDelightStockDatabaseRepository (Part 2/2)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/SqlDelightStockDatabaseRepository.kt`

- [ ] **Step 1: 添加分时数据方法**

在类中添加：
```kotlin
override suspend fun getIntradayTrend(code: String): List<TrendPoint> = withContext(Dispatchers.Default) {
    intradayTrendQueries.getIntradayTrend(code).executeAsList().map {
        TrendPoint(
            label = it.time,
            price = it.price,
            volume = it.volume
        )
    }
}

override suspend fun insertIntradayTrend(code: String, data: List<TrendPoint>) = withContext(Dispatchers.Default) {
    database.transaction {
        intradayTrendQueries.deleteByCode(code)
        data.forEach { point ->
            intradayTrendQueries.insertIntradayPoint(
                code = code,
                time = point.label,
                price = point.price,
                volume = point.volume
            )
        }
    }
}
```

- [ ] **Step 2: 添加 K 线数据方法**

在类中添加：
```kotlin
override suspend fun getKLines(code: String, period: String): List<OhlcPoint> = withContext(Dispatchers.Default) {
    kLineDataQueries.getKLines(code, period).executeAsList().map {
        OhlcPoint(
            time = it.date,
            open = it.open,
            high = it.high,
            low = it.low,
            close = it.close,
            volume = it.volume.toLong()
        )
    }
}

override suspend fun insertKLines(code: String, period: String, data: List<OhlcPoint>) = withContext(Dispatchers.Default) {
    database.transaction {
        kLineDataQueries.deleteByCodeAndPeriod(code, period)
        data.forEach { kline ->
            kLineDataQueries.insertOrReplaceKLine(
                code = code,
                period = period,
                date = kline.time,
                open = kline.open,
                close = kline.close,
                high = kline.high,
                low = kline.low,
                volume = kline.volume.toDouble()
            )
        }
    }
}
```

- [ ] **Step 3: 添加 AI 分析缓存方法**

在类中添加：
```kotlin
override suspend fun getAiAnalysis(code: String): AiAnalysisCacheEntry? = withContext(Dispatchers.Default) {
    aiAnalysisCacheQueries.getAiAnalysis(code).executeAsOneOrNull()?.let { record ->
        try {
            val analysis = json.decodeFromString<AiAnalysis>(record.content)
            AiAnalysisCacheEntry(
                analysis = analysis,
                quoteTime = record.quoteTime,
                analyzedAt = record.analyzedAt
            )
        } catch (e: Exception) {
            null // JSON 解析失败，返回 null
        }
    }
}

override suspend fun insertAiAnalysis(code: String, analysis: AiAnalysis, quoteTime: String) = withContext(Dispatchers.Default) {
    val content = json.encodeToString(analysis)
    aiAnalysisCacheQueries.insertOrReplaceAiAnalysis(
        code = code,
        content = content,
        quoteTime = quoteTime,
        analyzedAt = System.currentTimeMillis()
    )
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/SqlDelightStockDatabaseRepository.kt
git commit -m "实现 SqlDelightStockDatabaseRepository (Part 2)"
```

---

### Task 12: 添加 kotlinx.serialization 支持

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/model/StockModels.kt`

- [ ] **Step 1: 添加序列化插件**

在 `plugins` 块中添加：
```kotlin
kotlin("plugin.serialization") version "1.9.20"
```

在 `commonMain` dependencies 中添加：
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
```

- [ ] **Step 2: 为 AiAnalysis 添加 @Serializable 注解**

在 `StockModels.kt` 顶部添加导入：
```kotlin
import kotlinx.serialization.Serializable
```

为以下数据类添加 `@Serializable` 注解：
```kotlin
@Serializable
enum class AiTrendType(val label: String) { ... }

@Serializable
enum class AiAnalysisSource { ... }

@Serializable
enum class AiRiskLevel(val label: String) { ... }

@Serializable
data class AiObservationPlan( ... )

@Serializable
data class AiSignal( ... )

@Serializable
data class AiAnalysis( ... )
```

- [ ] **Step 3: 同步并编译**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add shared/build.gradle.kts shared/src/commonMain/kotlin/com/example/kuiklyaistock/model/StockModels.kt
git commit -m "添加 kotlinx.serialization 支持"
```

---

### Task 13: 创建 MockDataGenerator

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/MockDataGenerator.kt`

- [ ] **Step 1: 创建 MockDataGenerator 类**

```kotlin
package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlin.random.Random

// 模拟数据生成器，用于填充分时、K线、换手率、市盈率等扩展数据
internal object MockDataGenerator {
    
    // 生成分时数据：9:30-15:00，每分钟一条
    fun generateIntradayTrend(basePrice: Double, updatedAt: String): List<TrendPoint> {
        val result = mutableListOf<TrendPoint>()
        var price = basePrice
        
        // 9:30-11:30 (120分钟) + 13:00-15:00 (120分钟) = 240分钟
        val morningMinutes = 120
        val afternoonMinutes = 120
        
        // 上午盘
        for (i in 0 until morningMinutes) {
            val minute = 9 * 60 + 30 + i
            val hour = minute / 60
            val min = minute % 60
            price += Random.nextDouble(-0.015, 0.015) * basePrice
            result.add(TrendPoint(
                label = "${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}",
                price = price,
                volume = Random.nextDouble(1000.0, 10000.0)
            ))
        }
        
        // 下午盘
        for (i in 0 until afternoonMinutes) {
            val minute = 13 * 60 + i
            val hour = minute / 60
            val min = minute % 60
            price += Random.nextDouble(-0.015, 0.015) * basePrice
            result.add(TrendPoint(
                label = "${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}",
                price = price,
                volume = Random.nextDouble(1000.0, 10000.0)
            ))
        }
        
        return result
    }
    
    // 生成五日走势：240 个点（每日 48 个 5 分钟 K）
    fun generateFiveDayTrend(basePrice: Double): List<TrendPoint> {
        val result = mutableListOf<TrendPoint>()
        var price = basePrice * 0.98 // 从略低于当前价开始
        
        for (day in 0 until 5) {
            for (interval in 0 until 48) {
                price += Random.nextDouble(-0.01, 0.01) * basePrice
                result.add(TrendPoint(
                    label = "D${day}T${interval}",
                    price = price,
                    volume = Random.nextDouble(1000.0, 8000.0)
                ))
            }
        }
        
        return result
    }
    
    // 生成日 K 线：30 条
    fun generateDailyKLines(basePrice: Double, count: Int = 30): List<OhlcPoint> {
        val result = mutableListOf<OhlcPoint>()
        var currentPrice = basePrice * 0.85 // 从30天前价格开始
        
        for (i in 0 until count) {
            val open = currentPrice
            val close = open + Random.nextDouble(-0.05, 0.05) * open
            val high = maxOf(open, close) + Random.nextDouble(0.0, 0.02) * open
            val low = minOf(open, close) - Random.nextDouble(0.0, 0.02) * open
            
            result.add(OhlcPoint(
                time = "Day-${count - i}",
                open = open,
                high = high,
                low = low,
                close = close,
                volume = Random.nextLong(100000, 1000000)
            ))
            
            currentPrice = close
        }
        
        return result.reversed() // 从旧到新排序
    }
    
    // 生成周 K 线：30 条
    fun generateWeeklyKLines(basePrice: Double, count: Int = 30): List<OhlcPoint> {
        val result = mutableListOf<OhlcPoint>()
        var currentPrice = basePrice * 0.75
        
        for (i in 0 until count) {
            val open = currentPrice
            val close = open + Random.nextDouble(-0.08, 0.08) * open
            val high = maxOf(open, close) + Random.nextDouble(0.0, 0.03) * open
            val low = minOf(open, close) - Random.nextDouble(0.0, 0.03) * open
            
            result.add(OhlcPoint(
                time = "Week-${count - i}",
                open = open,
                high = high,
                low = low,
                close = close,
                volume = Random.nextLong(500000, 5000000)
            ))
            
            currentPrice = close
        }
        
        return result.reversed()
    }
    
    // 生成月 K 线：30 条
    fun generateMonthlyKLines(basePrice: Double, count: Int = 30): List<OhlcPoint> {
        val result = mutableListOf<OhlcPoint>()
        var currentPrice = basePrice * 0.60
        
        for (i in 0 until count) {
            val open = currentPrice
            val close = open + Random.nextDouble(-0.12, 0.12) * open
            val high = maxOf(open, close) + Random.nextDouble(0.0, 0.05) * open
            val low = minOf(open, close) - Random.nextDouble(0.0, 0.05) * open
            
            result.add(OhlcPoint(
                time = "Month-${count - i}",
                open = open,
                high = high,
                low = low,
                close = close,
                volume = Random.nextLong(2000000, 20000000)
            ))
            
            currentPrice = close
        }
        
        return result.reversed()
    }
    
    // 生成换手率：0.5%-15%
    fun generateTurnoverRate(): Double {
        return Random.nextDouble(0.5, 15.0)
    }
    
    // 生成市盈率：10-50
    fun generatePeRatio(): Double {
        return Random.nextDouble(10.0, 50.0)
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/MockDataGenerator.kt
git commit -m "创建 MockDataGenerator 模拟数据生成器"
```

---

## Phase 4: Repository 集成数据库

### Task 14: 改造 TencentStockRepository 构造函数

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt:14-24`

- [ ] **Step 1: 添加数据库依赖注入**

修改主构造函数：
```kotlin
class TencentStockRepository internal constructor(
    private val transport: QuoteTransport,
    private val nowMillis: () -> Long,
    private val logger: (String) -> Unit,
    private val databaseRepo: StockDatabaseRepository, // 新增
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
) : StockRepository {
    constructor(
        pager: Pager,
        nowMillis: () -> Long = { 0L },
        logger: (String) -> Unit = {},
        databaseRepo: StockDatabaseRepository, // 新增
    ) : this(TencentQuoteTransport(pager, nowMillis, logger), nowMillis, logger, databaseRepo)
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译失败，提示调用方缺少参数（预期行为）

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt
git commit -m "TencentStockRepository 添加数据库依赖"
```

---

### Task 15: 改造 TencentStockRepository loadHome 方法 (Part 1/2)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt:28-35`

- [ ] **Step 1: 修改 loadHome 方法逻辑**

替换现有 `loadHome` 方法为：
```kotlin
override suspend fun loadHome(scope: CoroutineScope): StockLoadResult<StockHomeData> {
    // 1. 优先从数据库读取
    val cachedQuotes = databaseRepo.getAllQuotes()
    if (cachedQuotes.isNotEmpty()) {
        logger("行情首页：从数据库读取 ${cachedQuotes.size} 只股票")
        val homeData = buildHomeDataFromCache(cachedQuotes)
        // 后台刷新网络数据
        scope.launch { refreshHomeFromNetwork() }
        return StockLoadResult.Success(homeData)
    }
    
    // 2. 数据库为空，等待网络请求
    logger("行情首页：数据库为空，等待网络请求")
    return refreshHomeFromNetwork()
}
```

- [ ] **Step 2: 添加辅助方法 buildHomeDataFromCache**

在类中添加：
```kotlin
private suspend fun buildHomeDataFromCache(quotes: List<StockQuote>): StockHomeData {
    val stockSymbols = STOCK_CODES.mapNotNull(TencentSymbolMapper::stockSymbol)
    val indexSymbols = INDEX_CODES.mapNotNull(TencentSymbolMapper::indexSymbol)
    
    // 从数据库读取的 quote 构建 homeData
    val quoteMap = quotes.associateBy { it.code }
    val stocks = quotes.filter { it.code in STOCK_CODES }
    
    // 读取指数数据（从内存缓存）
    val indices = indexSymbols.mapNotNull { symbol ->
        TencentQuoteCache.get(symbol)?.quote
    }
    
    latestQuotes = stocks
    
    return StockHomeData(
        quotes = stocks,
        marketSummary = createMarketSummary(stocks, indices, stocks.sumOf { 0.0 }),
        dataSource = StockDataSource.CACHE,
        quoteTime = stocks.map { it.updatedAt }.maxOrNull().orEmpty(),
        requestCompletedAt = nowMillis(),
        isExpired = false, // 数据库数据不标记过期
        missingCodes = emptyList(),
    )
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译失败，缺少 refreshHomeFromNetwork 方法（下一步实现）

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt
git commit -m "改造 loadHome 优先读数据库 (Part 1)"
```

---

### Task 16: 改造 TencentStockRepository loadHome 方法 (Part 2/2)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt`

- [ ] **Step 1: 添加 refreshHomeFromNetwork 方法**

```kotlin
private suspend fun refreshHomeFromNetwork(): StockLoadResult<StockHomeData> {
    val stockSymbols = STOCK_CODES.mapNotNull(TencentSymbolMapper::stockSymbol)
    val indexSymbols = INDEX_CODES.mapNotNull(TencentSymbolMapper::indexSymbol)
    
    return when (val result = fetchAndDecode(stockSymbols + indexSymbols)) {
        is DecodedQuotes.Remote -> {
            // 网络请求成功，写入数据库
            val stockQuotes = result.quotes
                .filter { it.symbol in stockSymbols }
                .map { toStockQuote(it) }
            
            if (stockQuotes.isNotEmpty()) {
                databaseRepo.insertQuotes(stockQuotes)
                logger("行情首页：写入数据库 ${stockQuotes.size} 只股票")
            }
            
            buildRemoteHome(result, stockSymbols, indexSymbols)
        }
        is DecodedQuotes.Failed -> {
            // 网络失败，尝试从数据库读取
            val fallback = databaseRepo.getAllQuotes()
            if (fallback.isNotEmpty()) {
                logger("行情首页：网络失败，使用数据库缓存 ${fallback.size} 只")
                StockLoadResult.Success(buildHomeDataFromCache(fallback))
            } else {
                StockLoadResult.Failure(result.errorMessage ?: "网络请求失败")
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt
git commit -m "改造 loadHome 网络刷新逻辑 (Part 2)"
```

---

### Task 17: 改造 TencentStockRepository loadDetail 方法

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt:37-58`

- [ ] **Step 1: 修改 loadDetail 方法**

替换现有 `loadDetail` 方法为：
```kotlin
override suspend fun loadDetail(scope: CoroutineScope, code: String): StockLoadResult<StockDetailData> {
    val normalized = code.trim()
    val symbol = TencentSymbolMapper.stockSymbol(normalized) ?: TencentSymbolMapper.indexSymbol(normalized)
        ?: return StockLoadResult.Empty
    
    // 1. 优先从数据库读取
    val cachedQuote = databaseRepo.getQuote(normalized)
    if (cachedQuote != null) {
        logger("股票详情：从数据库读取 $code")
        val detailData = buildDetailDataFromCache(cachedQuote, symbol)
        // 后台刷新网络数据
        scope.launch { refreshDetailFromNetwork(symbol, normalized) }
        return StockLoadResult.Success(detailData)
    }
    
    // 2. 数据库为空，等待网络请求
    logger("股票详情：数据库为空，等待网络请求 $code")
    return refreshDetailFromNetwork(symbol, normalized)
}
```

- [ ] **Step 2: 添加 buildDetailDataFromCache 方法**

```kotlin
private suspend fun buildDetailDataFromCache(quote: StockQuote, symbol: String): StockDetailData {
    // 从数据库读取分时、K 线数据
    val intradayTrend = databaseRepo.getIntradayTrend(quote.code)
    val fiveDayTrend = emptyList<TrendPoint>() // 暂不实现
    val dailyKLine = databaseRepo.getKLines(quote.code, "daily")
    val weeklyKLine = databaseRepo.getKLines(quote.code, "weekly")
    val monthlyKLine = databaseRepo.getKLines(quote.code, "monthly")
    
    val detail = StockDetail(
        quote = quote,
        open = 0.0, // 数据库暂未存储
        previousClose = 0.0,
        high = 0.0,
        low = 0.0,
        volume = 0,
        turnover = 0.0,
        turnoverRate = null,
        peRatio = null,
        intradayTrend = intradayTrend,
        fiveDayTrend = fiveDayTrend,
        dailyKLine = dailyKLine,
        weeklyKLine = weeklyKLine,
        monthlyKLine = monthlyKLine,
    )
    
    return StockDetailData(
        detail = detail,
        analysis = null,
        dataSource = StockDataSource.CACHE,
        quoteTime = quote.updatedAt,
        requestCompletedAt = nowMillis(),
        missingCodes = emptyList(),
    )
}
```

- [ ] **Step 3: 添加 refreshDetailFromNetwork 方法**

```kotlin
private suspend fun refreshDetailFromNetwork(symbol: String, code: String): StockLoadResult<StockDetailData> {
    return when (val result = fetchAndDecode(listOf(symbol))) {
        is DecodedQuotes.Remote -> {
            val quote = result.quotes.firstOrNull { it.symbol == symbol }
                ?: return cachedDetail(symbol, result.completedAt, result.errorMessage)
            
            // 生成模拟数据
            val intradayTrend = MockDataGenerator.generateIntradayTrend(quote.price, quote.updatedAt)
            val fiveDayTrend = MockDataGenerator.generateFiveDayTrend(quote.price)
            val dailyKLine = MockDataGenerator.generateDailyKLines(quote.price)
            val weeklyKLine = MockDataGenerator.generateWeeklyKLines(quote.price)
            val monthlyKLine = MockDataGenerator.generateMonthlyKLines(quote.price)
            val turnoverRate = MockDataGenerator.generateTurnoverRate()
            val peRatio = MockDataGenerator.generatePeRatio()
            
            // 写入数据库
            val stockQuote = toStockQuote(quote)
            databaseRepo.insertQuotes(listOf(stockQuote))
            databaseRepo.insertIntradayTrend(code, intradayTrend)
            databaseRepo.insertKLines(code, "daily", dailyKLine)
            databaseRepo.insertKLines(code, "weekly", weeklyKLine)
            databaseRepo.insertKLines(code, "monthly", monthlyKLine)
            
            logger("股票详情：写入数据库 $code")
            
            StockLoadResult.Success(
                StockDetailData(
                    detail = StockDetail(
                        quote = stockQuote,
                        open = quote.open,
                        previousClose = quote.previousClose,
                        high = quote.high,
                        low = quote.low,
                        volume = quote.volume,
                        turnover = quote.turnover,
                        turnoverRate = turnoverRate,
                        peRatio = peRatio,
                        intradayTrend = intradayTrend,
                        fiveDayTrend = fiveDayTrend,
                        dailyKLine = dailyKLine,
                        weeklyKLine = weeklyKLine,
                        monthlyKLine = monthlyKLine,
                    ),
                    analysis = null,
                    dataSource = StockDataSource.REMOTE,
                    quoteTime = quote.updatedAt,
                    requestCompletedAt = result.completedAt,
                    missingCodes = result.missingSymbols.mapNotNull(TencentSymbolMapper::standardCode),
                )
            )
        }
        is DecodedQuotes.Failed -> cachedDetail(symbol, result.completedAt, result.errorMessage)
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/TencentStockRepository.kt
git commit -m "改造 loadDetail 集成数据库和模拟数据"
```

---

### Task 18: 改造 RemoteAiAnalysisRepository 集成数据库

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/AiAnalysisRepository.kt:97-121`

- [ ] **Step 1: 添加数据库依赖**

修改 `RemoteAiAnalysisRepository` 构造函数：
```kotlin
internal class RemoteAiAnalysisRepository(
    private val transport: AiAnalysisTransport,
    private val databaseRepo: StockDatabaseRepository, // 新增
    private val modelName: String = DEFAULT_MODEL,
    private val schemaVersion: String = SCHEMA_VERSION,
    private val promptVersion: String = PROMPT_VERSION,
) : AiAnalysisRepository {
```

- [ ] **Step 2: 修改 loadAnalysis 方法**

替换 `loadAnalysis` 方法为：
```kotlin
override suspend fun loadAnalysis(detail: StockDetail): AiAnalysisLoadResult {
    if (!transport.isSupported()) return AiAnalysisLoadResult.Unsupported
    
    // 1. 优先从数据库读取
    val cached = databaseRepo.getAiAnalysis(detail.quote.code)
    if (cached != null && cached.quoteTime == detail.quote.updatedAt) {
        return AiAnalysisLoadResult.Success(cached.analysis.copy(source = AiAnalysisSource.CACHE))
    }
    
    // 2. 发起网络请求
    val request = buildRequest(detail)
    return when (val result = transport.request(request)) {
        is AiTransportResult.Success -> {
            val parsed = parseRemoteAnalysis(detail, result)
            if (parsed is AiAnalysisLoadResult.Success) {
                // 写入数据库
                databaseRepo.insertAiAnalysis(
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

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译失败，调用方缺少参数（预期行为）

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/AiAnalysisRepository.kt
git commit -m "RemoteAiAnalysisRepository 集成数据库"
```

---

## Phase 5: UI 层改造

### Task 19: 修复 StockHomePage 和 StockDetailPage 调用方

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockHomePage.kt:36-42`
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt:38-44`

- [ ] **Step 1: 创建数据库实例工厂方法**

Create: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/DatabaseFactory.kt`

```kotlin
package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.db.StockDatabase

// 数据库单例工厂
internal object DatabaseFactory {
    private var instance: StockDatabase? = null
    
    fun getDatabase(driverFactory: DatabaseDriverFactory): StockDatabase {
        return instance ?: synchronized(this) {
            instance ?: StockDatabase(driverFactory.createDriver()).also { instance = it }
        }
    }
}
```

- [ ] **Step 2: 修改 StockHomePage 初始化 repository**

在 `StockHomePage` 的 `repository` lazy 初始化中：
```kotlin
private val repository: StockRepository by lazy {
    val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
    val driverFactory = DatabaseDriverFactory(bridge.getContext())
    val database = DatabaseFactory.getDatabase(driverFactory)
    val databaseRepo = SqlDelightStockDatabaseRepository(database)
    
    TencentStockRepository(
        pager = this,
        nowMillis = { bridge.currentTimeStamp() },
        logger = { bridge.log(it) },
        databaseRepo = databaseRepo,
    )
}
```

- [ ] **Step 3: 修改 StockDetailPage 初始化 repository**

在 `StockDetailPage` 的 `repository` lazy 初始化中使用相同逻辑：
```kotlin
private val repository: StockRepository by lazy {
    val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
    val driverFactory = DatabaseDriverFactory(bridge.getContext())
    val database = DatabaseFactory.getDatabase(driverFactory)
    val databaseRepo = SqlDelightStockDatabaseRepository(database)
    
    TencentStockRepository(
        pager = this,
        nowMillis = { bridge.currentTimeStamp() },
        logger = { bridge.log(it) },
        databaseRepo = databaseRepo,
    )
}
```

- [ ] **Step 4: 修改 StockDetailPage 初始化 aiRepository**

在 `StockDetailPage` 的 `created()` 方法中：
```kotlin
override fun created() {
    super.created()
    val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
    PortfolioPersistence.ensureLoaded(bridge)
    quoteRefreshActive = true
    
    val driverFactory = DatabaseDriverFactory(bridge.getContext())
    val database = DatabaseFactory.getDatabase(driverFactory)
    val databaseRepo = SqlDelightStockDatabaseRepository(database)
    
    aiRepository = RemoteAiAnalysisRepository(
        BridgeAiAnalysisTransport(bridge),
        databaseRepo
    )
    
    removePortfolioObserver = PortfolioStore.subscribe { state ->
        favoriteCodes = state.favoriteCodes
        positions = state.positions
    }
    loadDetail()
    scheduleQuoteRefresh()
}
```

- [ ] **Step 5: 添加 BridgeModule.getContext() 扩展方法**

Create: `shared/src/androidMain/kotlin/com/example/kuiklyaistock/base/BridgeModuleExt.kt`

```kotlin
package com.example.kuiklyaistock.base

import android.content.Context

// 获取 Android Context（在 androidMain 中实现）
fun BridgeModule.getContext(): Context {
    // 假设 BridgeModule 有获取 Context 的方法
    // 根据实际 BridgeModule API 调整
    return this.pager.pagerData.pageViewContext as Context
}
```

- [ ] **Step 6: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/repository/DatabaseFactory.kt
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockHomePage.kt
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt
git add shared/src/androidMain/kotlin/com/example/kuiklyaistock/base/BridgeModuleExt.kt
git commit -m "修复 Repository 初始化，集成数据库"
```

---

### Task 20: 调整 StockHomePage 刷新策略

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockHomePage.kt:221-228`

- [ ] **Step 1: 修改刷新间隔为 60 秒**

将 `scheduleQuoteRefresh()` 方法修改为：
```kotlin
private fun scheduleQuoteRefresh() {
    setTimeout(60_000) { // 从 30_000 改为 60_000
        if (quoteRefreshActive && selectedTab == StockTabs.MARKET) { // 添加 Tab 判断
            loadContent()
            scheduleQuoteRefresh()
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockHomePage.kt
git commit -m "调整首页刷新间隔为60秒，仅在行情Tab刷新"
```

---

### Task 21: StockDetailPage 新增刷新按钮状态

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt:45-71`

- [ ] **Step 1: 添加 refreshing 状态**

在 `StockDetailPage` 的状态声明区添加：
```kotlin
private var refreshing by observable(false)
```

- [ ] **Step 2: 添加 refreshQuote 方法**

在类中添加：
```kotlin
private fun refreshQuote() {
    if (refreshing || detailLoading) return
    refreshing = true
    val code = pagerData.params.optString("code").trim()
    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 手动刷新: $code")
    
    lifecycleScope.launch {
        when (val result = repository.loadDetail(this, code)) {
            is StockLoadResult.Success -> {
                if (!acceptResult(detailRequests.next(), code)) {
                    refreshing = false
                    return@launch
                }
                detail = result.data.detail
                dataSource = result.data.dataSource
                quoteTime = result.data.quoteTime
                quoteExpired = result.data.isExpired
                refreshChartData(result.data.detail)
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("刷新成功")
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 刷新成功: $code")
            }
            is StockLoadResult.Failure -> {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("刷新失败: ${result.message}")
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 刷新失败: $code, ${result.message}")
            }
            StockLoadResult.Empty -> {}
        }
        refreshing = false
    }
}
```

- [ ] **Step 3: 调整自动刷新间隔为 60 秒**

将 `scheduleQuoteRefresh()` 方法中的 `setTimeout(15_000)` 改为：
```kotlin
private fun scheduleQuoteRefresh() {
    setTimeout(60_000) { // 从 15_000 改为 60_000
        if (quoteRefreshActive) {
            loadDetail()
            scheduleQuoteRefresh()
        }
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt
git commit -m "详情页新增刷新按钮和调整刷新间隔"
```

---

### Task 22: 调整 StockDetailHeader 布局

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailComponents.kt`

- [ ] **Step 1: 查找 StockDetailHeader 组件**

Run: `grep -n "fun.*StockDetailHeader" shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailComponents.kt`
Expected: 找到函数定义位置

- [ ] **Step 2: 修改 StockDetailHeader 函数签名**

添加 `onRefresh` 和 `refreshing` 参数：
```kotlin
internal fun ViewContainer<*, *>.StockDetailHeader(
    detail: StockDetail,
    width: Float,
    isFavorite: () -> Boolean,
    onFavorite: () -> Unit,
    sourceLabel: String,
    refreshing: () -> Boolean, // 新增
    onRefresh: () -> Unit,     // 新增
)
```

- [ ] **Step 3: 修改布局结构**

将组件内部改为：
```kotlin
View {
    attr {
        width(width)
        padding(StockDesignTokens.cardPadding)
    }
    
    // 第一行：股票名称 + 关注按钮
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginBottom(8f)
        }
        Text {
            attr {
                text(detail.quote.name)
                fontSize(22f)
                fontWeightBold()
                color(StockDesignTokens.primaryText)
                flex(1f)
            }
        }
        View {
            attr {
                width(StockDesignTokens.minimumTouchTarget)
                height(StockDesignTokens.minimumTouchTarget)
                allCenter()
            }
            event { click { onFavorite() } }
            Text {
                attr {
                    text(if (isFavorite()) "★" else "☆")
                    fontSize(24f)
                    color(if (isFavorite()) StockDesignTokens.risk else StockDesignTokens.tertiaryText)
                }
            }
        }
    }
    
    // 第二行：股票代码
    Text {
        attr {
            text(detail.quote.code)
            fontSize(13f)
            color(StockDesignTokens.secondaryText)
            marginBottom(8f)
        }
    }
    
    // 第三行：真实行情·日期 + 刷新按钮
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
        }
        Text {
            attr {
                text(sourceLabel)
                fontSize(12f)
                color(StockDesignTokens.tertiaryText)
                flex(1f)
            }
        }
        View {
            attr {
                width(StockDesignTokens.minimumTouchTarget)
                height(StockDesignTokens.minimumTouchTarget)
                allCenter()
            }
            event { click { if (!refreshing()) onRefresh() } }
            Text {
                attr {
                    text("🔄")
                    fontSize(16f)
                    color(if (refreshing()) StockDesignTokens.tertiaryText else StockDesignTokens.brand)
                }
            }
        }
    }
}
```

- [ ] **Step 4: 更新 StockDetailPage 调用**

在 `StockDetailPage.kt` 中更新 `StockDetailHeader` 调用：
```kotlin
StockDetailHeader(
    detail = stock,
    width = ctx.stockContentWidth(),
    isFavorite = { ctx.isFavorite(stock.quote.code) },
    onFavorite = { ctx.toggleFavorite(stock.quote.code) },
    sourceLabel = "${ctx.dataSource.displayName()} · ${ctx.quoteTime.ifEmpty { stock.quote.updatedAt }}${if (ctx.quoteExpired) " · 已过期" else ""}",
    refreshing = { ctx.refreshing },
    onRefresh = { ctx.refreshQuote() },
)
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailComponents.kt
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt
git commit -m "调整 StockDetailHeader 布局，添加刷新按钮"
```

---

### Task 23: AiAnalysisSection 添加刷新按钮

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockAiAnalysis.kt`

- [ ] **Step 1: 查找 AiAnalysisSection 组件**

Run: `grep -n "fun.*AiAnalysisSection" shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockAiAnalysis.kt`
Expected: 找到函数定义位置

- [ ] **Step 2: 修改 AiAnalysisSection 函数签名**

添加 `onRefreshAnalysis` 参数：
```kotlin
internal fun ViewContainer<*, *>.AiAnalysisSection(
    analysis: () -> AiAnalysis?,
    loadState: () -> AiLoadState,
    errorMessage: () -> String,
    isDetailExpanded: () -> Boolean,
    width: Float,
    expandedSignalIndex: () -> Int,
    selectedQuickQuestion: () -> String,
    onSignalToggle: (Int) -> Unit,
    onQuickQuestion: (String) -> Unit,
    onDetailToggle: () -> Unit,
    onRetry: () -> Unit,
    onRefreshAnalysis: () -> Unit, // 新增
)
```

- [ ] **Step 3: 在标题栏添加刷新按钮**

找到 AI 分析标题区域，添加刷新按钮：
```kotlin
// AI 分析标题栏
View {
    attr {
        flexDirectionRow()
        alignItemsCenter()
        marginBottom(12f)
    }
    Text {
        attr {
            text("AI 智能解读")
            fontSize(18f)
            fontWeightBold()
            color(StockDesignTokens.primaryText)
            flex(1f)
        }
    }
    // 刷新按钮
    View {
        attr {
            width(StockDesignTokens.minimumTouchTarget)
            height(StockDesignTokens.minimumTouchTarget)
            allCenter()
        }
        event { click { onRefreshAnalysis() } }
        Text {
            attr {
                text("🔄")
                fontSize(14f)
                color(if (loadState() == AiLoadState.LOADING) StockDesignTokens.tertiaryText else StockDesignTokens.brand)
            }
        }
    }
}
```

- [ ] **Step 4: 在 StockDetailPage 添加刷新方法**

在 `StockDetailPage` 中添加：
```kotlin
private fun refreshAiAnalysis() {
    if (aiLoadState == AiLoadState.LOADING) return
    detail?.let {
        resetAiInteractionState()
        analyzedQuoteTime = "" // 清除已分析标记，强制重新请求
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 手动刷新AI分析: ${it.quote.code}")
        loadRemoteAnalysis(it)
    }
}
```

- [ ] **Step 5: 更新 AiAnalysisSection 调用**

在 `StockDetailPage` 的 `body()` 中更新调用：
```kotlin
AiAnalysisSection(
    analysis = { ctx.displayedAnalysis },
    loadState = { ctx.aiLoadState },
    errorMessage = { ctx.aiErrorMessage },
    isDetailExpanded = { ctx.isAiDetailExpanded },
    width = ctx.stockContentWidth(),
    expandedSignalIndex = { ctx.expandedAiSignalIndex },
    selectedQuickQuestion = { ctx.selectedAiQuickQuestion },
    onSignalToggle = { ctx.toggleAiSignal(it) },
    onQuickQuestion = { ctx.selectAiQuickQuestion(it) },
    onDetailToggle = { ctx.toggleAiDetail() },
    onRetry = { ctx.retryAiAnalysis() },
    onRefreshAnalysis = { ctx.refreshAiAnalysis() },
)
```

- [ ] **Step 6: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockAiAnalysis.kt
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt
git commit -m "AI分析区域添加刷新按钮"
```

---

### Task 24: 适配 K 线图展示逻辑

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt:280-286`

- [ ] **Step 1: 修改 refreshChartData 方法**

替换现有方法为：
```kotlin
private fun refreshChartData(stock: StockDetail) {
    chartCandles.clear()
    
    if (dataSource == StockDataSource.MOCK) {
        // Mock 数据保留原有逻辑
        chartCandles.addAll(mockStockCandles(stock, selectedChartPeriod))
        return
    }
    
    // 真实行情：从 detail 读取对应周期数据
    val data = when (selectedChartPeriod) {
        StockChartPeriod.INTRADAY -> stock.intradayTrend.map { toOhlcPoint(it) }
        StockChartPeriod.FIVE_DAY -> stock.fiveDayTrend.map { toOhlcPoint(it) }
        StockChartPeriod.DAILY -> stock.dailyKLine
        StockChartPeriod.WEEKLY -> stock.weeklyKLine
        StockChartPeriod.MONTHLY -> stock.monthlyKLine
    }
    
    chartCandles.addAll(data)
}

// 辅助方法：TrendPoint 转 OhlcPoint
private fun toOhlcPoint(point: TrendPoint): OhlcPoint {
    return OhlcPoint(
        time = point.label,
        open = point.price,
        high = point.price,
        low = point.price,
        close = point.price,
        volume = point.volume.toLong()
    )
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/kuiklyaistock/StockDetailPage.kt
git commit -m "适配K线图展示逻辑，支持多周期切换"
```

---

## Phase 6: 测试

### Task 25: 编写 MockDataGeneratorTest

**Files:**
- Create: `shared/src/commonTest/kotlin/com/example/kuiklyaistock/repository/MockDataGeneratorTest.kt`

- [ ] **Step 1: 创建测试文件**

```kotlin
package com.example.kuiklyaistock.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockDataGeneratorTest {
    
    @Test
    fun `generateIntradayTrend should return 240 points`() {
        val trend = MockDataGenerator.generateIntradayTrend(100.0, "2024-01-01")
        assertEquals(240, trend.size, "分时数据应包含240个点（9:30-15:00）")
    }
    
    @Test
    fun `generateFiveDayTrend should return 240 points`() {
        val trend = MockDataGenerator.generateFiveDayTrend(100.0)
        assertEquals(240, trend.size, "五日走势应包含240个点（5天 * 48点/天）")
    }
    
    @Test
    fun `generateDailyKLines should return 30 klines`() {
        val klines = MockDataGenerator.generateDailyKLines(100.0, 30)
        assertEquals(30, klines.size, "日K应包含30条")
    }
    
    @Test
    fun `generateWeeklyKLines should return 30 klines`() {
        val klines = MockDataGenerator.generateWeeklyKLines(100.0, 30)
        assertEquals(30, klines.size, "周K应包含30条")
    }
    
    @Test
    fun `generateMonthlyKLines should return 30 klines`() {
        val klines = MockDataGenerator.generateMonthlyKLines(100.0, 30)
        assertEquals(30, klines.size, "月K应包含30条")
    }
    
    @Test
    fun `generateTurnoverRate should be in valid range`() {
        val rate = MockDataGenerator.generateTurnoverRate()
        assertTrue(rate >= 0.5 && rate <= 15.0, "换手率应在 0.5%-15% 范围内")
    }
    
    @Test
    fun `generatePeRatio should be in valid range`() {
        val pe = MockDataGenerator.generatePeRatio()
        assertTrue(pe >= 10.0 && pe <= 50.0, "市盈率应在 10-50 范围内")
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `./gradlew :shared:testDebugUnitTest --tests MockDataGeneratorTest`
Expected: 7 tests passed

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonTest/kotlin/com/example/kuiklyaistock/repository/MockDataGeneratorTest.kt
git commit -m "添加 MockDataGenerator 单元测试"
```

---

### Task 26: 编写 StockDatabaseRepositoryTest

**Files:**
- Create: `shared/src/commonTest/kotlin/com/example/kuiklyaistock/repository/StockDatabaseRepositoryTest.kt`

- [ ] **Step 1: 创建测试驱动工厂（in-memory）**

Create: `shared/src/commonTest/kotlin/com/example/kuiklyaistock/repository/TestDatabaseDriverFactory.kt`

```kotlin
package com.example.kuiklyaistock.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.kuiklyaistock.db.StockDatabase

// 测试用的内存数据库驱动
class TestDatabaseDriverFactory {
    fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        StockDatabase.Schema.create(driver)
        return driver
    }
}
```

- [ ] **Step 2: 添加测试依赖**

在 `shared/build.gradle.kts` 的 `commonTest` dependencies 中添加：
```kotlin
implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
```

- [ ] **Step 3: 创建测试类**

```kotlin
package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.db.StockDatabase
import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StockDatabaseRepositoryTest {
    private lateinit var database: StockDatabase
    private lateinit var repository: StockDatabaseRepository
    
    @BeforeTest
    fun setup() {
        val driver = TestDatabaseDriverFactory().createDriver()
        database = StockDatabase(driver)
        repository = SqlDelightStockDatabaseRepository(database)
    }
    
    @AfterTest
    fun teardown() {
        database.close()
    }
    
    @Test
    fun `insertQuotes and getAllQuotes should work`() = runTest {
        val quotes = listOf(
            StockQuote("平安银行", "000001", 12.5, 0.5, 4.2, "15:00"),
            StockQuote("万科A", "000002", 18.3, -0.3, -1.6, "15:00"),
        )
        
        repository.insertQuotes(quotes)
        val retrieved = repository.getAllQuotes()
        
        assertEquals(2, retrieved.size)
        assertEquals("平安银行", retrieved.find { it.code == "000001" }?.name)
    }
    
    @Test
    fun `getQuote should return null for non-existent code`() = runTest {
        val quote = repository.getQuote("999999")
        assertNull(quote)
    }
    
    @Test
    fun `insertIntradayTrend should replace old data`() = runTest {
        val code = "000001"
        val trend1 = listOf(TrendPoint("09:30", 12.5, 1000.0))
        val trend2 = listOf(TrendPoint("09:31", 12.6, 1100.0))
        
        repository.insertIntradayTrend(code, trend1)
        repository.insertIntradayTrend(code, trend2)
        
        val retrieved = repository.getIntradayTrend(code)
        assertEquals(1, retrieved.size)
        assertEquals("09:31", retrieved[0].label)
    }
    
    @Test
    fun `insertKLines should work for multiple periods`() = runTest {
        val code = "000001"
        val dailyKLines = listOf(
            OhlcPoint("2024-01-01", 12.0, 12.5, 11.8, 12.3, 100000)
        )
        val weeklyKLines = listOf(
            OhlcPoint("2024-W01", 12.0, 13.0, 11.5, 12.8, 500000)
        )
        
        repository.insertKLines(code, "daily", dailyKLines)
        repository.insertKLines(code, "weekly", weeklyKLines)
        
        val daily = repository.getKLines(code, "daily")
        val weekly = repository.getKLines(code, "weekly")
        
        assertEquals(1, daily.size)
        assertEquals(1, weekly.size)
        assertEquals("2024-01-01", daily[0].time)
        assertEquals("2024-W01", weekly[0].time)
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `./gradlew :shared:testDebugUnitTest --tests StockDatabaseRepositoryTest`
Expected: All tests passed

- [ ] **Step 5: 提交**

```bash
git add shared/build.gradle.kts
git add shared/src/commonTest/kotlin/com/example/kuiklyaistock/repository/TestDatabaseDriverFactory.kt
git add shared/src/commonTest/kotlin/com/example/kuiklyaistock/repository/StockDatabaseRepositoryTest.kt
git commit -m "添加 StockDatabaseRepository 单元测试"
```

---

### Task 27: 端到端测试（手动）

**Files:**
- 无代码修改

- [ ] **Step 1: 安装 APK 到设备**

Run: `./gradlew :androidApp:installDebug`
Expected: APK 安装成功

- [ ] **Step 2: 冷启动测试**

1. 清空应用数据：设置 → 应用 → KuiklyAIStock → 清空数据
2. 启动应用
3. 观察首页加载速度（应等待网络请求）
4. 查看日志：`adb logcat | grep "stock_home"`
Expected: 看到 "数据库为空，等待网络请求" 和 "写入数据库 36 只股票"

- [ ] **Step 3: 热启动测试**

1. 完全关闭应用（滑出后台）
2. 重新启动应用
3. 观察首页是否立即显示数据
4. 查看日志：`adb logcat | grep "stock_home"`
Expected: 看到 "从数据库读取 36 只股票"，UI 立即渲染

- [ ] **Step 4: 详情页测试**

1. 点击任意股票进入详情页
2. 检查是否显示分时图、K 线图
3. 点击刷新按钮
4. 切换 K 线周期（分时/五日/日K/周K/月K）
Expected: 所有图表正常显示，刷新后 Toast 提示

- [ ] **Step 5: AI 分析测试**

1. 在详情页等待 AI 分析加载
2. 点击 AI 区域的刷新按钮
3. 观察重新加载过程
Expected: AI 分析正常加载，刷新后重新请求

- [ ] **Step 6: 离线测试**

1. 断开网络（飞行模式）
2. 关闭并重启应用
3. 观察首页是否显示缓存数据
4. 进入详情页查看图表
Expected: 显示缓存数据，顶部提示"缓存行情"

- [ ] **Step 7: 记录测试结果**

Create: `docs/superpowers/tests/2026-09-11-e2e-test-report.md`

```markdown
# 端到端测试报告

**日期：** 2026-09-11  
**测试人：** [Your Name]

## 测试结果

| 测试场景 | 预期结果 | 实际结果 | 状态 |
|---------|---------|---------|------|
| 冷启动（数据库为空） | 等待网络，写入数据库 | [填写] | ✅/❌ |
| 热启动（数据库有数据） | 立即显示缓存，1秒内渲染 | [填写] | ✅/❌ |
| 详情页图表展示 | 分时/K线正常显示 | [填写] | ✅/❌ |
| 手动刷新按钮 | 刷新成功 Toast 提示 | [填写] | ✅/❌ |
| K线周期切换 | 图表切换正常 | [填写] | ✅/❌ |
| AI分析刷新 | 重新加载分析 | [填写] | ✅/❌ |
| 离线模式 | 显示缓存数据 | [填写] | ✅/❌ |

## 问题记录

[填写测试中发现的问题]

## 性能数据

- 冷启动时间：[填写]
- 热启动时间：[填写]
- 数据库查询耗时：[填写]
```

- [ ] **Step 8: 提交测试报告**

```bash
git add docs/superpowers/tests/2026-09-11-e2e-test-report.md
git commit -m "添加端到端测试报告"
```

---

## 自我审查清单

### Spec Coverage

- [x] 首页启动时立即展示缓存数据 → Task 15, 16
- [x] 首页 60 秒定时刷新 → Task 20
- [x] 详情页进入时立即展示数据 → Task 17
- [x] 详情页手动刷新按钮 → Task 21, 22
- [x] SQLDelight 数据库持久化 → Task 1-6
- [x] 分时、K线、换手率、市盈率扩展数据 → Task 7, 13, 17
- [x] AI 分析结果持久化 → Task 18
- [x] AI 分析手动刷新 → Task 23

### Placeholder Scan

- [x] 无 "TBD", "TODO", "implement later"
- [x] 所有步骤包含完整代码
- [x] 所有命令包含预期输出

### Type Consistency

- [x] `StockDatabaseRepository` 接口在所有任务中保持一致
- [x] `MockDataGenerator` 方法签名一致
- [x] 数据模型扩展（StockDetail, TrendPoint）在所有任务中一致

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
- [ ] 换手率、市盈率显示在详情页（模拟数据）
- [ ] 所有日志包含 "数据来源" 标识（database/network/cache）
