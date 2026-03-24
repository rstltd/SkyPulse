# SkyPulse 天脈 — Spring Boot 專案架構設計

> 版本：v3.0 (Final) | 日期：2026-03-23  
> 技術棧：Java 21 + Spring Boot 3.x + PostgreSQL + TimescaleDB + Docker  
> 部署目標：Raspberry Pi (256 GB SSD)  
> 應用場景：大規模崩塌監測輔助決策

---

## 一、專案定位

SkyPulse 天脈是 RST.ltd GNSS 邊坡防災監測服務的輔助決策資料中台。以大規模崩塌監測為主要應用場景，匯集氣象、水文、地震、太空天氣資料，透過 API 提供給既有 GNSS 解算系統（已運作 5-6 年）進行交叉比對與綜合決策。

### 核心價值

| 場景 | SkyPulse 提供的價值 |
|------|-------------------|
| 累積雨量觸發邊坡滑動 | 時雨量 + 3/24/48/72h 累積雨量 → 提前預警 |
| 地震擾動邊坡穩定性 | M4.0+ 地震資訊 → 判斷位移是否由震動引起 |
| 電離層干擾 GNSS 精度 | Kp + Dst + 太陽風 → 辨別真實位移 vs 假訊號 |
| 水文異常 | 河川水位 + 水庫水情 → 評估坡腳沖蝕風險 |

---

## 二、資料來源

### 2.1 確認範圍：4 個來源 + 1 個補充

| 來源 | 認證 | 即時 API | 歷史回溯 |
|------|------|---------|---------|
| CWA 中央氣象署 | ✅ 已取得 API Key | REST JSON | CODiS (時雨量) + C-B0025-002 (每日雨量 9 年) |
| WRA 水利署 | ✅ data.gov.tw 免申請 | REST JSON | 暫不回溯 |
| USGS Earthquake | ✅ 公開免認證 | GeoJSON | 同一 API + starttime/endtime |
| NOAA SWPC | ✅ 公開免認證 | JSON | SWPC FTP 歸檔 (Kp 1994+), NCEI (太陽風) |
| WDC Kyoto (Dst) | ✅ 公開 (非商業用途) | via SWPC JSON | WDC 歸檔 (1957+) |

暫緩：NCDR 災防中心

### 2.2 CWA 中央氣象署

- **Base URL**：`https://opendata.cwa.gov.tw/api/v1/rest/datastore/{DatasetID}?Authorization={KEY}&format=JSON`

| 資料集 ID | 名稱 | 用途 | 頻率 | 優先 |
|-----------|------|------|------|------|
| O-A0002-001 | 自動雨量站雨量觀測 | 時雨量，崩塌警戒核心指標 | 每小時 | P1 |
| O-A0001-001 | 自動氣象站觀測 | 溫度/濕度/氣壓/風速 | 每小時 | P1 |
| W-C0033-002 | 天氣警特報 | 豪雨特報告警 | 每 3 分鐘 | P1 |
| E-A0014-001 | 顯著有感地震報告 | M4.0+ 地震（Collector 端過濾） | 每 5 分鐘 | P2 |
| E-A0015-001 | 小區域有感地震報告 | M4.0+ 地震（Collector 端過濾） | 每 5 分鐘 | P2 |
| F-D0047-091 | 鄉鎮天氣預報（一週） | 降雨預測 | 每 12 小時 | P2 |
| F-A0010-001 | 一般天氣預報（36h） | 短期天氣 | 每 6 小時 | P3 |
| O-A0003-001 | 氣壓觀測 | 氣壓變化趨勢 | 每小時 | P3 |
| C-B0025-002 | 過去 9 年每日雨量 | 歷史回填用 | 一次性 | P4 |

### 2.3 WRA 水利署

- **Base URL**：`https://opendata.wra.gov.tw/api/v2/{GUID}?format=JSON`
- 透過 data.gov.tw 取得，免申請

| data.gov.tw 編號 | 名稱 | 頻率 | 優先 |
|-----------------|------|------|------|
| 25768 | 即時水位資料 | 每 10 分鐘 | P2 |
| 45501 | 水庫水情資料 | 每小時 | P2 |
| 32729 | 雨量站基本資料 | 每年（靜態） | P3 |

### 2.4 USGS Earthquake

- **Base URL**：`https://earthquake.usgs.gov/fdsnws/event/1/query`
- 公開免認證，GeoJSON 格式
- 篩選：台灣區域 + **M4.0 以上**

```
?format=geojson
&minlatitude=21.5&maxlatitude=25.5
&minlongitude=119.0&maxlongitude=122.5
&minmagnitude=4
```

### 2.5 NOAA SWPC + WDC Kyoto

- **Base URL**：`https://services.swpc.noaa.gov`
- 公開免認證

| 端點 | 內容 | GNSS 關聯性 | 優先 |
|------|------|------------|------|
| `/products/summary/planetary-k-index.json` | Kp 指數 | Kp ≥ 5 時 GNSS 精度劣化 | P1 |
| `/products/kyoto-dst.json` | Dst 指數 | Dst < -50 nT 為磁暴，影響電離層 | P1 |
| `/products/noaa-scales.json` | G/S/R 等級 | G2+ 需注意 GNSS | P1 |
| `/products/summary/solar-wind-speed.json` | 太陽風速度 | 高速太陽風擾動電離層 | P2 |
| `/products/summary/solar-wind-mag-field.json` | 太陽風磁場 | Bz 南向時磁暴風險增加 | P2 |
| `/json/goes/primary/xrays-1-day.json` | X 射線通量 | M/X 級閃焰擾動電離層 | P2 |
| `/products/alerts.json` | 太空天氣警報 | 事件告警 | P2 |
| `/products/geomagnetic-forecast.json` | 地磁預報 | 未來 3 天預測 | P3 |

---

## 三、專案結構

```
skypulse/
├── docker/
│   ├── docker-compose.yml
│   ├── docker-compose.prod.yml
│   └── init-db/
│       └── 001-init-timescaledb.sql
│
├── src/main/java/com/rstltd/skypulse/
│   ├── SkyPulseApplication.java
│   │
│   ├── config/
│   │   ├── WebClientConfig.java
│   │   ├── SchedulerConfig.java
│   │   └── JacksonConfig.java
│   │
│   ├── collector/                      # 即時 ETL 擷取層
│   │   ├── common/
│   │   │   ├── CollectorBase.java
│   │   │   ├── CollectorResult.java
│   │   │   └── RetryHandler.java
│   │   ├── cwa/
│   │   │   ├── CwaClient.java
│   │   │   ├── CwaRainfallCollector.java
│   │   │   ├── CwaWeatherCollector.java
│   │   │   ├── CwaEarthquakeCollector.java    # validate: mag >= 4.0
│   │   │   ├── CwaAlertCollector.java
│   │   │   ├── CwaForecastCollector.java
│   │   │   └── dto/
│   │   │       ├── CwaApiResponse.java
│   │   │       ├── CwaStationObservation.java
│   │   │       └── CwaEarthquakeReport.java
│   │   ├── wra/
│   │   │   ├── WraClient.java
│   │   │   ├── WraWaterLevelCollector.java
│   │   │   ├── WraReservoirCollector.java
│   │   │   └── dto/
│   │   │       ├── WraWaterLevelData.java
│   │   │       └── WraReservoirData.java
│   │   ├── usgs/
│   │   │   ├── UsgsClient.java
│   │   │   ├── UsgsEarthquakeCollector.java   # query: minmagnitude=4
│   │   │   └── dto/
│   │   │       ├── GeoJsonFeatureCollection.java
│   │   │       └── GeoJsonFeature.java
│   │   └── swpc/
│   │       ├── SwpcClient.java
│   │       ├── SwpcKpIndexCollector.java
│   │       ├── SwpcDstIndexCollector.java
│   │       ├── SwpcSolarWindCollector.java
│   │       ├── SwpcAlertCollector.java
│   │       └── dto/
│   │           ├── SwpcKpIndex.java
│   │           ├── SwpcDstIndex.java
│   │           └── SwpcSolarWind.java
│   │
│   ├── backfill/                       # 歷史資料回填（一次性）
│   │   ├── BackfillService.java
│   │   ├── BackfillController.java
│   │   ├── UsgsHistoricalBackfill.java    # 6 年 M4.0+ 台灣地震
│   │   ├── SwpcKpHistoricalBackfill.java  # 6 年 Kp
│   │   ├── SwpcDstHistoricalBackfill.java # 6 年 Dst（WDC Kyoto 歸檔）
│   │   ├── SwpcSolarWindBackfill.java     # 6 年太陽風
│   │   └── CwaCodisBackfill.java          # 6 年 CODiS 時雨量（特定站點）
│   │
│   ├── domain/
│   │   ├── station/
│   │   │   └── Station.java
│   │   ├── weather/
│   │   │   ├── RainfallObservation.java
│   │   │   ├── WeatherObservation.java
│   │   │   └── WeatherForecast.java
│   │   ├── seismic/
│   │   │   └── EarthquakeEvent.java
│   │   ├── spaceweather/
│   │   │   ├── KpIndexRecord.java
│   │   │   ├── DstIndexRecord.java
│   │   │   ├── SolarWindRecord.java
│   │   │   └── SpaceWeatherAlert.java
│   │   ├── hydrology/
│   │   │   ├── WaterLevelObservation.java
│   │   │   └── ReservoirStatus.java
│   │   └── alert/
│   │       └── HazardAlert.java
│   │
│   ├── repository/
│   │   ├── StationRepository.java
│   │   ├── RainfallObservationRepository.java
│   │   ├── WeatherObservationRepository.java
│   │   ├── EarthquakeEventRepository.java
│   │   ├── KpIndexRepository.java
│   │   ├── DstIndexRepository.java
│   │   ├── SolarWindRepository.java
│   │   ├── WaterLevelObservationRepository.java
│   │   ├── ReservoirStatusRepository.java
│   │   └── HazardAlertRepository.java
│   │
│   ├── service/
│   │   ├── WeatherService.java
│   │   ├── SeismicService.java
│   │   ├── SpaceWeatherService.java
│   │   ├── HydrologyService.java
│   │   └── AlertService.java
│   │
│   ├── api/
│   │   ├── v1/
│   │   │   ├── WeatherController.java
│   │   │   ├── SeismicController.java
│   │   │   ├── SpaceWeatherController.java
│   │   │   ├── HydrologyController.java
│   │   │   ├── AlertController.java
│   │   │   └── HealthController.java
│   │   └── dto/
│   │       ├── ApiResponse.java
│   │       ├── RainfallSummaryResponse.java
│   │       └── GnssQualityIndicator.java
│   │
│   └── util/
│       ├── TimeUtils.java
│       └── GeoUtils.java
│
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/
│       ├── V1__create_stations.sql
│       ├── V2__create_weather_tables.sql
│       ├── V3__create_seismic_tables.sql
│       ├── V4__create_spaceweather_tables.sql
│       ├── V5__create_hydrology_tables.sql
│       ├── V6__create_alert_tables.sql
│       └── V7__create_hypertables_and_compression.sql
│
└── src/test/java/com/rstltd/skypulse/
    ├── collector/
    │   ├── cwa/CwaRainfallCollectorTest.java
    │   └── swpc/SwpcKpIndexCollectorTest.java
    └── api/v1/
        └── WeatherControllerTest.java
```

---

## 四、核心設計

### 4.1 Collector 抽象基底

所有 Collector 繼承 CollectorBase，統一處理排程觸發、API 呼叫、重試、錯誤日誌、計時。

```java
public abstract class CollectorBase<T> {

    protected abstract String getSourceName();
    protected abstract Mono<List<T>> fetch();
    protected abstract void persist(List<T> data);
    protected abstract boolean validate(T item);

    protected void execute() {
        long start = System.currentTimeMillis();
        try {
            List<T> rawData = fetch().block(Duration.ofSeconds(30));
            if (rawData == null || rawData.isEmpty()) {
                log.warn("[{}] No data returned", getSourceName());
                return;
            }
            List<T> validData = rawData.stream()
                .filter(this::validate)
                .toList();
            persist(validData);
            log.info("[{}] Collected {} records ({} ms)",
                getSourceName(), validData.size(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[{}] Collection failed: {}", getSourceName(), e.getMessage());
        }
    }
}
```

### 4.2 WebClient 配置

每個外部 API 獨立 WebClient instance：

```java
@Configuration
public class WebClientConfig {

    @Bean("cwaWebClient")
    public WebClient cwaWebClient(@Value("${skypulse.cwa.base-url}") String baseUrl) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();
    }

    @Bean("wraWebClient")
    public WebClient wraWebClient(@Value("${skypulse.wra.base-url}") String baseUrl) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();
    }

    @Bean("usgsWebClient")
    public WebClient usgsWebClient() {
        return WebClient.builder()
            .baseUrl("https://earthquake.usgs.gov")
            .build();
    }

    @Bean("swpcWebClient")
    public WebClient swpcWebClient() {
        return WebClient.builder()
            .baseUrl("https://services.swpc.noaa.gov")
            .build();
    }
}
```

### 4.3 排程配置

```yaml
skypulse:
  cwa:
    base-url: https://opendata.cwa.gov.tw/api/v1/rest/datastore
    api-key: ${CWA_API_KEY}
    schedule:
      rainfall: "0 0 * * * *"       # 每小時（時雨量）
      weather: "0 0 * * * *"        # 每小時
      earthquake: "0 */5 * * * *"   # 每 5 分鐘
      alert: "0 */3 * * * *"        # 每 3 分鐘
      forecast: "0 0 */6 * * *"     # 每 6 小時
    earthquake:
      min-magnitude: 4.0             # M4.0 以上
  wra:
    base-url: https://opendata.wra.gov.tw/api/v2
    schedule:
      water-level: "0 */10 * * * *"
      reservoir: "0 0 * * * *"
  usgs:
    base-url: https://earthquake.usgs.gov
    schedule:
      earthquake: "0 */5 * * * *"
    earthquake:
      min-magnitude: 4.0
      min-latitude: 21.5
      max-latitude: 25.5
      min-longitude: 119.0
      max-longitude: 122.5
  swpc:
    base-url: https://services.swpc.noaa.gov
    schedule:
      kp-index: "0 */15 * * * *"
      dst-index: "0 0 * * * *"      # 每小時
      solar-wind: "0 */5 * * * *"
      alert: "0 */10 * * * *"

  backfill:
    enabled: false                   # 手動觸發

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/skypulse
    username: skypulse
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

---

## 五、資料庫設計

### V1：觀測站

```sql
CREATE TABLE stations (
    id              BIGSERIAL PRIMARY KEY,
    station_code    VARCHAR(30) NOT NULL UNIQUE,
    station_name    VARCHAR(100) NOT NULL,
    source          VARCHAR(20) NOT NULL,
    station_type    VARCHAR(30) NOT NULL,
    latitude        DECIMAL(9,6),
    longitude       DECIMAL(9,6),
    altitude        DECIMAL(7,2),
    county          VARCHAR(20),
    township        VARCHAR(20),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_stations_source ON stations(source);
CREATE INDEX idx_stations_type ON stations(station_type);
```

### V2：氣象與雨量

```sql
CREATE TABLE rainfall_observations (
    time            TIMESTAMPTZ NOT NULL,
    station_code    VARCHAR(30) NOT NULL,
    precipitation   DECIMAL(8,2),
    source          VARCHAR(20) NOT NULL,
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE weather_observations (
    time            TIMESTAMPTZ NOT NULL,
    station_code    VARCHAR(30) NOT NULL,
    temperature     DECIMAL(5,2),
    humidity        DECIMAL(5,2),
    pressure        DECIMAL(7,2),
    wind_speed      DECIMAL(6,2),
    wind_direction  DECIMAL(5,2),
    precipitation   DECIMAL(8,2),
    source          VARCHAR(20) NOT NULL,
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE weather_forecasts (
    id              BIGSERIAL PRIMARY KEY,
    location_name   VARCHAR(50) NOT NULL,
    forecast_time   TIMESTAMPTZ NOT NULL,
    issued_time     TIMESTAMPTZ NOT NULL,
    weather_desc    VARCHAR(100),
    min_temp        DECIMAL(5,2),
    max_temp        DECIMAL(5,2),
    rain_prob       INTEGER,
    source          VARCHAR(20) DEFAULT 'CWA',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### V3：地震

```sql
CREATE TABLE earthquake_events (
    time            TIMESTAMPTZ NOT NULL,
    event_id        VARCHAR(50) NOT NULL UNIQUE,
    magnitude       DECIMAL(4,2) NOT NULL,
    depth_km        DECIMAL(7,2),
    latitude        DECIMAL(9,6) NOT NULL,
    longitude       DECIMAL(9,6) NOT NULL,
    location_desc   VARCHAR(200),
    source          VARCHAR(20) NOT NULL,
    max_intensity   VARCHAR(10),
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### V4：太空天氣

```sql
CREATE TABLE kp_index_records (
    time            TIMESTAMPTZ NOT NULL,
    kp_value        DECIMAL(3,1) NOT NULL,
    source          VARCHAR(20) DEFAULT 'SWPC',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE dst_index_records (
    time            TIMESTAMPTZ NOT NULL,
    dst_value       DECIMAL(6,1) NOT NULL,
    source          VARCHAR(20) DEFAULT 'WDC_KYOTO',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE solar_wind_records (
    time            TIMESTAMPTZ NOT NULL,
    wind_speed      DECIMAL(8,2),
    density         DECIMAL(8,2),
    bz              DECIMAL(8,2),
    bt              DECIMAL(8,2),
    source          VARCHAR(20) DEFAULT 'SWPC',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE space_weather_alerts (
    id              BIGSERIAL PRIMARY KEY,
    alert_time      TIMESTAMPTZ NOT NULL,
    alert_type      VARCHAR(50),
    serial_number   VARCHAR(30),
    message         TEXT,
    g_scale         INTEGER,
    s_scale         INTEGER,
    r_scale         INTEGER,
    source          VARCHAR(20) DEFAULT 'SWPC',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### V5：水文

```sql
CREATE TABLE water_level_observations (
    time            TIMESTAMPTZ NOT NULL,
    station_code    VARCHAR(30) NOT NULL,
    water_level     DECIMAL(8,3),
    source          VARCHAR(20) DEFAULT 'WRA',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE reservoir_statuses (
    time            TIMESTAMPTZ NOT NULL,
    reservoir_id    VARCHAR(20) NOT NULL,
    reservoir_name  VARCHAR(50),
    water_level     DECIMAL(8,3),
    full_level      DECIMAL(8,3),
    storage_pct     DECIMAL(5,2),
    inflow          DECIMAL(10,2),
    outflow         DECIMAL(10,2),
    daily_rainfall  DECIMAL(8,2),
    source          VARCHAR(20) DEFAULT 'WRA',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### V6：統一告警

```sql
CREATE TABLE hazard_alerts (
    id              BIGSERIAL PRIMARY KEY,
    alert_time      TIMESTAMPTZ NOT NULL,
    alert_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    source          VARCHAR(20) NOT NULL,
    source_alert_id VARCHAR(100),
    title           VARCHAR(200),
    description     TEXT,
    affected_area   VARCHAR(200),
    expires_at      TIMESTAMPTZ,
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_alerts_type ON hazard_alerts(alert_type);
CREATE INDEX idx_alerts_time ON hazard_alerts(alert_time DESC);
```

### V7：Hypertable + 壓縮 + 連續聚合

```sql
-- ==========================================
-- Hypertable 建立
-- ==========================================

SELECT create_hypertable('rainfall_observations', 'time');
SELECT create_hypertable('weather_observations', 'time');
SELECT create_hypertable('kp_index_records', 'time');
SELECT create_hypertable('dst_index_records', 'time');
SELECT create_hypertable('solar_wind_records', 'time');
SELECT create_hypertable('water_level_observations', 'time');
SELECT create_hypertable('reservoir_statuses', 'time');
SELECT create_hypertable('earthquake_events', 'time');

-- ==========================================
-- 壓縮策略（7 天後自動壓縮，永久保留）
-- 壓縮後資料仍可正常查詢（唯讀）
-- ==========================================

ALTER TABLE rainfall_observations SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code',
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('rainfall_observations', INTERVAL '7 days');

ALTER TABLE weather_observations SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code',
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('weather_observations', INTERVAL '7 days');

ALTER TABLE water_level_observations SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code',
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('water_level_observations', INTERVAL '7 days');

ALTER TABLE kp_index_records SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('kp_index_records', INTERVAL '7 days');

ALTER TABLE dst_index_records SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('dst_index_records', INTERVAL '7 days');

ALTER TABLE solar_wind_records SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('solar_wind_records', INTERVAL '7 days');

ALTER TABLE reservoir_statuses SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'reservoir_id',
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('reservoir_statuses', INTERVAL '7 days');

-- earthquake_events 資料量極小（M4.0+ 每天 5-10 筆），不壓縮

-- ==========================================
-- 連續聚合：時雨量摘要
-- ==========================================

CREATE MATERIALIZED VIEW hourly_rainfall_summary
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS hourly_precipitation,
    COUNT(*) AS record_count
FROM rainfall_observations
GROUP BY bucket, station_code;

SELECT add_continuous_aggregate_policy('hourly_rainfall_summary',
    start_offset    => INTERVAL '3 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

-- ==========================================
-- 連續聚合：累積雨量（大規模崩塌核心指標）
-- 3h / 24h / 48h / 72h
-- ==========================================

CREATE MATERIALIZED VIEW accumulated_rainfall_3h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS accumulated_3h
FROM rainfall_observations
WHERE time > time_bucket('1 hour', time) - INTERVAL '3 hours'
GROUP BY bucket, station_code;

SELECT add_continuous_aggregate_policy('accumulated_rainfall_3h',
    start_offset    => INTERVAL '6 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

CREATE MATERIALIZED VIEW accumulated_rainfall_24h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS accumulated_24h
FROM rainfall_observations
WHERE time > time_bucket('1 hour', time) - INTERVAL '24 hours'
GROUP BY bucket, station_code;

SELECT add_continuous_aggregate_policy('accumulated_rainfall_24h',
    start_offset    => INTERVAL '27 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

CREATE MATERIALIZED VIEW accumulated_rainfall_48h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS accumulated_48h
FROM rainfall_observations
WHERE time > time_bucket('1 hour', time) - INTERVAL '48 hours'
GROUP BY bucket, station_code;

SELECT add_continuous_aggregate_policy('accumulated_rainfall_48h',
    start_offset    => INTERVAL '51 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

CREATE MATERIALIZED VIEW accumulated_rainfall_72h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS accumulated_72h
FROM rainfall_observations
WHERE time > time_bucket('1 hour', time) - INTERVAL '72 hours'
GROUP BY bucket, station_code;

SELECT add_continuous_aggregate_policy('accumulated_rainfall_72h',
    start_offset    => INTERVAL '75 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');
```

---

## 六、歷史資料回填

### 6.1 回填目標

涵蓋 GNSS 系統過去 6 年觀測期間（約 2020-2026），與既有位移資料做交叉比對。

### 6.2 回填清單

| 資料 | 來源 | 方式 | 預估資料量 |
|------|------|------|-----------|
| 台灣 M4.0+ 地震 | USGS API | 同一 API + starttime/endtime | ~1,500 筆 |
| Kp 指數 | SWPC FTP 歸檔 | 文字檔下載 → 解析 | ~17,500 筆 |
| Dst 指數 | WDC Kyoto 歸檔 | 文字檔下載 → 解析 | ~52,500 筆 |
| 太陽風 | NCEI / NASA OMNIWeb | CSV 下載 → 解析 | ~630,000 筆 |
| 時雨量 | CWA CODiS | HTTP GET 逐站逐月 CSV | 視站數而定 |
| 每日雨量 | CWA API C-B0025-002 | 同 API Key 呼叫 | ~23,000 筆/站 |

### 6.3 觸發方式

```
POST /api/v1/backfill/usgs-earthquake?startDate=2020-01-01&endDate=2026-03-23
POST /api/v1/backfill/swpc-kp?startDate=2020-01-01&endDate=2026-03-23
POST /api/v1/backfill/swpc-dst?startDate=2020-01-01&endDate=2026-03-23
POST /api/v1/backfill/swpc-solar-wind?startDate=2020-01-01&endDate=2026-03-23
POST /api/v1/backfill/cwa-codis-rainfall?stationCode=C0D660&startDate=2020-01-01&endDate=2026-03-23
POST /api/v1/backfill/cwa-daily-rainfall?startDate=2020-01-01&endDate=2026-03-23
```

手動觸發、分批處理、UPSERT 避免重複、可中斷續傳。

### 6.4 不回溯的資料

| 資料 | 原因 |
|------|------|
| WRA 水位 | 無批次 API |
| 天氣預報 | 時效性資料，歷史預報無意義 |
| 天氣警特報 | 歷史警報價值有限 |

---

## 七、REST API

```
# 健康檢查
GET  /api/v1/health

# 氣象
GET  /api/v1/weather/rainfall/latest
GET  /api/v1/weather/rainfall/station/{code}
GET  /api/v1/weather/rainfall/accumulated?stationCode={}&hours=3|24|48|72
GET  /api/v1/weather/observations/latest
GET  /api/v1/weather/forecasts/{location}
GET  /api/v1/weather/alerts

# 地震（M4.0+）
GET  /api/v1/seismic/events/latest
GET  /api/v1/seismic/events?since={datetime}&minMag={value}
GET  /api/v1/seismic/events/nearby?lat={}&lon={}&radiusKm={}

# 太空天氣
GET  /api/v1/spaceweather/kp/current
GET  /api/v1/spaceweather/kp/history?hours=72
GET  /api/v1/spaceweather/dst/current
GET  /api/v1/spaceweather/dst/history?hours=72
GET  /api/v1/spaceweather/solar-wind/current
GET  /api/v1/spaceweather/alerts
GET  /api/v1/spaceweather/gnss-quality

# 水文
GET  /api/v1/hydrology/water-level/latest
GET  /api/v1/hydrology/water-level/station/{code}
GET  /api/v1/hydrology/reservoirs

# 統一告警
GET  /api/v1/alerts/active
GET  /api/v1/alerts?type={}&since={}

# 綜合（給 GNSS 系統）
GET  /api/v1/dashboard/site/{siteId}

# 歷史回填（手動觸發）
POST /api/v1/backfill/{source}?startDate={}&endDate={}
```

### GNSS 品質指標 API

Kp + Dst 雙指標交叉判定：

```json
GET /api/v1/spaceweather/gnss-quality

{
    "timestamp": "2026-03-23T08:30:00Z",
    "qualityLevel": "DEGRADED",
    "kpIndex": 5.3,
    "dstIndex": -72.0,
    "bzComponent": -8.2,
    "solarWindSpeed": 620,
    "gScale": 2,
    "rScale": 1,
    "assessment": "Kp >= 5 and Dst < -50 nT, moderate geomagnetic storm in progress.",
    "recommendation": "FLAG_DISPLACEMENT_DATA"
}
```

品質分級邏輯：

```
NORMAL:   Kp < 4   AND Dst > -30 nT
CAUTION:  Kp 4~5   OR  Dst -30 ~ -50 nT
DEGRADED: Kp 5~7   OR  Dst -50 ~ -100 nT   OR G2+
SEVERE:   Kp > 7   OR  Dst < -100 nT        OR G3+
```

---

## 八、儲存空間估算

### 每日新增

| 資料表 | Rows/天 | MB/天 |
|--------|---------|-------|
| rainfall_observations | 16,800 | 4.5 |
| weather_observations | 16,800 | 5.9 |
| water_level_observations | 43,200 | 8.6 |
| reservoir_statuses | 1,200 | 0.4 |
| earthquake_events (M4.0+) | 5-10 | <0.1 |
| kp_index_records | 96 | <0.1 |
| dst_index_records | 24 | <0.1 |
| solar_wind_records | 288 | <0.1 |
| 索引開銷 (+30%) | — | ~6.0 |
| **合計** | **~78,400** | **~26 MB** |

### 長期預測

| 期間 | 未壓縮 | 壓縮後 (~90%) |
|------|--------|--------------|
| 90 天 | ~2.3 GB | ~0.5 GB |
| 1 年 | ~9.5 GB | ~1.5 GB |
| 6 年（含回填） | ~57 GB | ~6 GB |
| 10 年 | ~95 GB | ~10 GB |

256 GB SSD 可容納 10 年以上壓縮資料。

---

## 九、Docker Compose

```yaml
services:
  postgres:
    image: timescale/timescaledb:latest-pg16
    container_name: skypulse-db
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: skypulse
      POSTGRES_USER: skypulse
      POSTGRES_PASSWORD: ${DB_PASSWORD:-skypulse_dev}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U skypulse"]
      interval: 10s
      timeout: 5s
      retries: 5

  skypulse:
    build: ..
    container_name: skypulse-app
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/skypulse
      SPRING_DATASOURCE_USERNAME: skypulse
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-skypulse_dev}
      CWA_API_KEY: ${CWA_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  pgdata:
```

---

## 十、技術選型

| 項目 | 選擇 | 理由 |
|------|------|------|
| Java | 21 (LTS) | 長期支援，Virtual Threads |
| Spring Boot | 3.3+ | 最新穩定版 |
| HTTP Client | WebClient | 非阻塞，多源 API 呼叫 |
| ORM | Spring Data JPA | 熟悉的 JPA 生態 |
| DB Migration | Flyway | Spring Boot 原生整合 |
| 排程 | Spring Scheduler | 初期夠用 |
| JSON | Jackson | Spring Boot 預設 |
| 容器化 | Docker Compose | 開發部署統一 |
| 時序 DB | TimescaleDB | 壓縮 + 連續聚合 + JPA 相容 |

---

## 十一、開發里程碑

### Sprint 1（第 1-2 週）：基礎架構 + CWA 雨量

- 專案骨架、Docker Compose、Flyway migration (V1-V7)
- WebClientConfig、CollectorBase、SchedulerConfig
- CwaClient + CwaRainfallCollector（時雨量）
- rainfall_observations hypertable + compression
- /api/v1/weather/rainfall/latest
- /api/v1/health

### Sprint 2（第 3-4 週）：CWA 擴充 + NOAA 太空天氣

- CwaWeatherCollector、CwaEarthquakeCollector (M4.0+)、CwaAlertCollector
- SwpcKpIndexCollector、SwpcDstIndexCollector、SwpcSolarWindCollector
- GNSS 品質指標 API（Kp + Dst 雙指標）
- 統一告警模型

### Sprint 3（第 5-6 週）：USGS + WRA

- UsgsEarthquakeCollector (M4.0+, 台灣區域)
- WraWaterLevelCollector、WraReservoirCollector
- 地震事件去重（CWA vs USGS 同一事件）

### Sprint 4（第 7-8 週）：歷史回填

- BackfillService + BackfillController
- USGS 6 年 M4.0+ 地震回填
- SWPC 6 年 Kp + Dst + 太陽風回填
- CWA CODiS 時雨量回填（特定站點）

### Sprint 5（第 9-10 週）：整合優化

- 累積雨量連續聚合（3h/24h/48h/72h）
- 綜合 dashboard API
- 天氣預報 Collector
- 錯誤處理、重試機制完善
- Swagger/OpenAPI 文件
- Raspberry Pi 部署測試
