# SkyPulse Phase 2 設計草案（給專案擁有者審閱）

> 本草案綜合五面向設計研究 + 一份對抗式事實查核。凡查核標「更正/存疑」處，已採更正值或標「未證實」。所有時間欄 `TIMESTAMPTZ`(UTC)，hypertable 唯一鍵含分區鍵 `time`。DDL/API/公式/URL 保留原文。

---

## 1. 總覽 — Phase 2 三子階段

| 子階段 | 一句話目標 |
|--------|-----------|
| **P2a — Schema 打掉重畫 + 從頭 backfill** | 把 schema 重寫成「以 `rain_10min_mm` 為累加主軸、stations 拆通用空間維度 + 能力關聯表、reservoirs/water_level 拆維度表、補地震震度/PGA 與 NOAA G/R/S 時序表、全面補齊站/庫座標」的乾淨基線，並修好 collector 寫入熱點。 |
| **P2b — 座標查詢 API 契約** | 交付 Direction B 主產品 `GET /api/v1/context`（單點）+ `POST /api/v1/context/batch`（GNSS 多場址），完全 stateless、不儲存任何場址，以 auto-radius 找就近站算指標，每個 domain block 附 provenance/freshness/SWCB 燈號。 |
| **P2c — 前端** | 公共監看台（全域摘要 + 資料透明度儀表）+ 內部 admin 前端對齊新 schema/新端點；退役與 Direction B 衝突的 per-site 頁面。 |

Direction B 核心資料流：**座標 → auto-radius 就近站 → 整合環境指標（含 SWCB 土石流警戒燈號）→ JSON**，SkyPulse 不落任何場址。

---

## 2. SWCB 對齊（查核：全部證實）

### 2.1 採用公式（版本鎖定）
現行《土石流警戒基準值訂定及警戒發布作業技術指引》**114 年 7 月修訂版（2025）**。查核逐項證實：

- **有效累積雨量** `Rt = R0 + P`，其中前期降雨 `P = Σ 0.7^i × Ri`（`Ri` = 本次降雨開始前第 i 日之 24 小時累積雨量）。
- **雨量衰減係數 α = 0.7**（於 102 年第三次土石流災害潛勢資料審查會通過，沿用至今）。
- **回溯窗 = 本次降雨開始前 7 日（168 小時）**，超過忽略。折減單位是「日/24hr」，**非小時**。
- **RTI = I × Rt**（I = 小時降雨強度 mm/hr，Rt = 有效累積雨量 mm）。
- **雨場分割**：時雨量 > 4mm 為降雨「開始」，其後連續 6 小時時雨量均 < 4mm 為「結束」（與現有 ETR1 註解一致）。
- **警戒基準值語意**：在 I = 10 mm/hr 條件下，RTI70 對應之總有效累積雨量即警戒基準值 **R70（mm）**。ardswc 的 `AlertValue` 極可能即此 mm 級門檻（單位語意仍列存疑，見 §9）。

### 2.2 ⚠️ 落地最大陷阱（版本錯置）
現有 `WeatherService` 的 ETR2 = `Σ(precip_1hr × 0.5^(t/12h))`（**小時級、半衰減 0.5**）**不是 SWCB 官方法**。官方是**日級、折減 0.7、7 日窗**。實作時：
- 切勿把 12h 半衰減當成「SWCB 對齊」；
- 切勿把 0.7 誤套成每小時折減（會嚴重低估前期雨量）。
- Phase 2 需以 `Rt = R0 + Σ 0.7^i·Ri` 取代 ETR2；`WeatherService.getEffectiveRainfall`(:81) 整支替換。

### 2.3 座標 → 鄉鎮 → 警戒燈號的鏈
```
查詢座標 (lat,lon)
  → auto-radius 找就近雨量站 → 取該站 10-min 序列
  → 算 I(最大時雨量) 與 Rt(0.7 日折減/7日窗)  → RTI = I × Rt
  → 用最近站的 county/township 反查 SWCB AlertValue(R70 門檻)
  → signal: GREEN / YELLOW / RED（Rt or RTI vs 門檻）
```
- 警戒基準值來源：`GetDebrisRainData.ashx`（潛勢溪流層級，含參考雨量站 STID1/STID2 + AlertValue）或 `GetCountyTownAlertValueList.ashx`（鄉鎮層級，較粗）。
- **決策點**：燈號依「就近潛勢溪流門檻」還是「鄉鎮門檻」判？前者需潛勢溪流座標（176524 SHP，須離線轉檔），後者只需鄉鎮對照表（純 JSON，較簡單）。建議 P2 先做鄉鎮層級，潛勢溪流座標列為選配。

---

## 3. 新 Schema 設計（標與現有差異）

三大核心修正：(1) 雨量以 `rain_10min_mm`（過去 10 分鐘、非重疊）為累加主軸，`precipitation` 正名 `daily_accum_mm`（禁止跨列相加），`trailing_*` 僅交叉驗證；(2) 滾動窗用「固定桶 hourly/daily CAgg + 讀取時 window function」，杜絕 V8 那種 WHERE 自我恆真的假 CAgg；(3) stations 拆通用空間維度 + capability 關聯表。

### 3.1 雨量 — `rainfall_observations`（10 分鐘 hypertable）
差異：新增 `rain_10min_mm` 為累加主軸；`precipitation`→`daily_accum_mm`、`precip_Nhr`→`trailing_Nhr_mm` 正名並註明禁止 SUM；**建議停存 `raw_data`**（見 §6）。
```sql
CREATE TABLE rainfall_observations (
    time             TIMESTAMPTZ NOT NULL,
    station_code     VARCHAR(40) NOT NULL,
    rain_10min_mm    DECIMAL(6,2),   -- ★累加主軸：過去10分鐘（非重疊）
    daily_accum_mm   DECIMAL(7,2),   -- CWA Now：當日累積(午夜歸零)，禁止跨列相加
    trailing_1hr_mm  DECIMAL(6,2),   -- 官方 trailing 快照，禁止 SUM，僅交叉驗證/fallback
    trailing_3hr_mm  DECIMAL(6,2),
    trailing_6hr_mm  DECIMAL(6,2),
    trailing_12hr_mm DECIMAL(7,2),
    trailing_24hr_mm DECIMAL(7,2),
    source           VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT rainfall_obs_time_station_unique UNIQUE (time, station_code)
);
SELECT create_hypertable('rainfall_observations','time', chunk_time_interval => INTERVAL '1 day');
ALTER TABLE rainfall_observations SET (timescaledb.compress,
    timescaledb.compress_segmentby='station_code', timescaledb.compress_orderby='time DESC');
SELECT add_compression_policy('rainfall_observations', INTERVAL '3 days');
```

**滾動窗（這次要對）** — 只物化固定非重疊桶，滾動窗留讀取時算：
```sql
-- (A) 唯一即時 CAgg：固定 1 小時桶（正確、非重疊）
CREATE MATERIALIZED VIEW rainfall_hourly WITH (timescaledb.continuous) AS
SELECT time_bucket('1 hour', time) AS bucket, station_code,
       SUM(rain_10min_mm) AS rain_mm, COUNT(*) AS sample_count  -- 完整度(本應6筆/hr)
FROM rainfall_observations GROUP BY bucket, station_code WITH NO DATA;

-- (B) 日雨量 CAgg（SWCB 以「當地日曆日」為界，tz-aware）
CREATE MATERIALIZED VIEW rainfall_daily WITH (timescaledb.continuous) AS
SELECT time_bucket('1 day', time, 'Asia/Taipei') AS bucket, station_code,  -- ★本地日界，非UTC日
       SUM(rain_10min_mm) AS rain_mm
FROM rainfall_observations GROUP BY bucket, station_code WITH NO DATA;
```
- 3h/24h/48h/72h 一律讀取時用 window function（`RANGE ... PRECEDING`）over 小體積 hourly CAgg；「當下」點值近 72h 每站僅 432 列，直接掃 raw。
- CAgg 的 `CREATE` 不能在 transaction 內 → 每個 CAgg 拆獨立 Flyway migration + `WITH NO DATA`（V8 教訓）。
- `time_bucket` 第三參數（時區）為 TimescaleDB 2.x 功能（版本待實測，見 §9）。

**有效累積雨量 Rt — 建議讀取時算**：只物化 `rainfall_daily` 作日雨量來源；Rt/RTI/警戒判定住 service 程式（可版本化、可回溯重算，避免把公式版本凍死在欄位裡）。熱路徑若需低延遲，另建快取表 `station_rainfall_index_latest(station_code PK, rt_mm, rti, alert_level, computed_at)`，每採集週期刷新（是快取非真相來源）。

### 3.2 stations — 補座標 + 一站多型別
差異：去掉單一 `station_type NOT NULL`（會被 weather collector 覆蓋踩型別）、去掉污染整表的 `alert_level1/2/3`；PK 改自然鍵 `station_code`；新增 `geom` 空間欄。
```sql
CREATE TABLE stations (
    station_code  VARCHAR(40) PRIMARY KEY,
    station_name  VARCHAR(100) NOT NULL,
    source        VARCHAR(20)  NOT NULL,
    latitude      DECIMAL(9,6),                    -- WGS84
    longitude     DECIMAL(9,6),
    geom          geography(Point,4326)            -- 供 KNN 就近查詢(PostGIS，待確認映像支援)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude,latitude),4326)::geography) STORED,
    altitude_m    DECIMAL(7,2),
    county        VARCHAR(20),
    township      VARCHAR(20),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_stations_geom ON stations USING GIST (geom);

CREATE TABLE station_capability (   -- 解一站多型別 + 記來源/新鮮度(透明度一等目標)
    station_code VARCHAR(40) NOT NULL REFERENCES stations(station_code),
    capability   VARCHAR(20) NOT NULL,   -- RAINFALL / WEATHER / WATER_LEVEL
    dataset_id   VARCHAR(60),            -- O-A0002-001 等，來源可追
    first_seen   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (station_code, capability)
);

CREATE TABLE water_level_station (  -- WATER_LEVEL 專屬門檻，從 stations 移出
    station_code VARCHAR(40) PRIMARY KEY REFERENCES stations(station_code),
    river_name   VARCHAR(50), basin VARCHAR(50),
    alert_level1 DECIMAL(8,3), alert_level2 DECIMAL(8,3), alert_level3 DECIMAL(8,3),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```
就近查詢（PostGIS KNN）：`ORDER BY s.geom <-> ST_MakePoint(:lon,:lat)::geography LIMIT :k`。若映像無 PostGIS → 退 `cube+earthdistance`（`<@>`）或應用層 bbox 預篩 + haversine。**（映像是否含 PostGIS 未證實，見 §9）**

### 3.3 reservoirs — 拆靜態維度 / 時序狀態
差異：把 name/full_level 等靜態 metadata 從時序表移出（現 collector 每列 join refData 補值 + storage_pct 溢位 hack）。
```sql
CREATE TABLE reservoirs (   -- 靜態維度
    reservoir_id VARCHAR(20) PRIMARY KEY, reservoir_name VARCHAR(50),
    full_level_m DECIMAL(8,3), design_capacity_m3 DECIMAL(14,2),
    latitude DECIMAL(9,6), longitude DECIMAL(9,6),  -- ★需補，來源存疑(見§5.1c/§9)
    geom geography(Point,4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude,latitude),4326)::geography) STORED,
    basin VARCHAR(50), county VARCHAR(20), is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE reservoir_status (   -- 純時序觀測
    time TIMESTAMPTZ NOT NULL, reservoir_id VARCHAR(20) NOT NULL,
    water_level_m DECIMAL(8,3), effective_storage_m3 DECIMAL(14,2),
    storage_pct DECIMAL(6,2),   -- 加寬避免溢位；建議讀取時 = storage/capacity 算
    inflow_cms DECIMAL(10,2), outflow_cms DECIMAL(10,2), catchment_rain_mm DECIMAL(7,2),
    source VARCHAR(20) NOT NULL, raw_data JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reservoir_status_time_id_unique UNIQUE (time, reservoir_id)
);
SELECT create_hypertable('reservoir_status','time');
```

### 3.4 earthquake — 補震度/PGA
差異：`max_intensity` 欄自 V3 存在但 collector 從未寫入；補正規化序位與 PGA/PGV，並選配站點級子表（對邊坡近震分析有價值）。
```sql
-- earthquake_events 補欄（或重建時直接含）：
    max_intensity      VARCHAR(6),    -- CWA 震度階：0,1,2,3,4,5弱,5強,6弱,6強,7
    max_intensity_rank SMALLINT,      -- 正規化序位 0..9 供排序/篩選
    pga_gal            DECIMAL(7,2),
    pgv_cms            DECIMAL(7,2);
-- 選配子表（支援「座標→就近站震度」auto-radius）：
CREATE TABLE earthquake_station_intensity (
    event_id VARCHAR(50) NOT NULL, time TIMESTAMPTZ NOT NULL,
    station_code VARCHAR(40), county VARCHAR(20), intensity VARCHAR(6),
    pga_gal DECIMAL(7,2), pgv_cms DECIMAL(7,2),
    station_lat DECIMAL(9,6), station_lon DECIMAL(9,6),
    PRIMARY KEY (event_id, station_code)
);   -- 低量，非 hypertable。需 DTO 解析 Intensity/ShakingArea/EqStation
```
`earthquake_events` 維持不壓縮（量極低）。震度值域須含 5弱/5強/6弱/6強（10 級制），勿只列到 7。

### 3.5 spaceweather — G/R/S 分級（新時序表）
差異：現 g/r/s 只掛在有 alert 時的 `space_weather_alerts`；`SwpcAlertCollector` 根本未 parse（→ `SpaceWeatherService` 讀到永遠 null，g 分支形同不作用）。新增連續取樣表：
```sql
CREATE TABLE noaa_scales (
    time       TIMESTAMPTZ NOT NULL,
    horizon    VARCHAR(12) NOT NULL,   -- ★用日偏移為準：observed(0) / predicted_d1(1)...(見§5.3更正)
    g_scale    SMALLINT, r_scale SMALLINT, s_scale SMALLINT,   -- 0..5
    source     VARCHAR(20) NOT NULL DEFAULT 'SWPC',
    raw_data   JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT noaa_scales_time_horizon_unique UNIQUE (time, horizon)
);
SELECT create_hypertable('noaa_scales','time');
```
`kp/dst/solar_wind` 結構不變（PK=time，全域無站點）。

### 3.6 TimescaleDB 壓縮/保留一覽（建議值，均可調）
| 表 | chunk | 壓縮起點 | segmentby | 保留 |
|----|-------|---------|-----------|------|
| rainfall_observations | 1 day | 3 days | station_code | 無（建議無限期，成本極低；空間吃緊才設 2 年） |
| water_level_observations | 1 day | 3 days | station_code | 同上 |
| weather_observations | 1 day | 7 days | station_code | 730 days |
| reservoir_status | 7 days | 7 days | reservoir_id | 無 |
| noaa_scales / kp / dst / solar_wind | 7 days | 7 days | (無/time) | 無 |
| earthquake_events | 7 days | 不壓縮 | — | 無 |
| rainfall_hourly / rainfall_daily (CAgg) | — | — | — | 無（長期保留對齊 GNSS 5-6 年壽命） |
| system_logs | 沿用 | 沿用 | — | 90 days |

保留與 CAgg 互動：若對 raw 加 retention，須確保 CAgg refresh 已 materialize 該時段後才 drop raw chunk（`end_offset` 涵蓋、refresh 先於 retention），否則長區間查詢出現空洞。

---

## 4. 座標查詢 API 契約（P2b 主產品）

> 部署前綴 `context-path=/skypulse`，對外絕對路徑 `https://<host>/skypulse/api/v1/context`；以下一律以 route `/api/v1/...` 表示。回應 `meta.contractVersion="1.0"`（隨 URI /v1 綁定）。

### 4.1 端點總覽與現有端點處置
| 方法 | Route | 用途 | Auth |
|------|-------|------|------|
| GET | `/api/v1/context` | 單一座標整合環境脈絡（主產品）| ROLE_USER (X-API-Key) |
| POST | `/api/v1/context/batch` | GNSS 一次查多場址 | ROLE_USER |
| GET | `/api/v1/context/coverage` | 只回各 domain 會用哪個站+距離，不算指標（透明度/除錯）| ROLE_USER |
| GET | `/api/v1/meta/freshness` | 系統級各來源最後成功抓取 + SLA + 是否 stale | public |

現有端點：per-domain（weather/seismic/spaceweather/hydrology/alerts/stations）+ `monitor/summary` **全保留（站中心/全域語意，供監看台/除錯，與座標中心正交）**；`dashboard/site/{siteId}`、`SiteController`/`SiteInfo`/`skypulse.sites.list`、`DashboardResponse` **建議退役**（假設 SkyPulse 認得場址，直接違反 Direction B），聚合邏輯重寫為 `ContextService`。

### 4.2 Request — GET /api/v1/context
| 參數 | 型別 | 必填 | 預設 | 說明 |
|------|------|------|------|------|
| `lat` | double | 是 | — | `@Min(-90)@Max(90)` + Taiwan bbox 建議檢查 |
| `lon` | double | 是 | — | `@Min(-180)@Max(180)` |
| `radiusKm` | double | 否 | auto | `@Min(0.1)@Max(100)`；省略=auto（取最近站，上限 `maxAutoRadiusKm`=50）|
| `rainRadiusKm`/`waterRadiusKm` | double | 否 | =radiusKm | 各 type 半徑覆寫 |
| `quakeRadiusKm` | double | 否 | 100 | `@Max(300)`；地震以查詢點為圓心 |
| `at` | ISO-8601 | 否 | now | 不得未來/早於保留邊界；歷史脈絡查詢 |
| `include` | csv | 否 | all | `rainfall,seismic,gnss,water` 選算 |

auto-radius 語意：給 `radiusKm`=硬限制，內無站 → block=null + warning `NO_STATION_IN_RADIUS`；省略=取最近站（上限 `maxAutoRadiusKm`）；`distanceKm` 一律回傳讓 GNSS 自判。GNSS 品質（太空天氣）**與座標無關、恆回傳**、`gnssQuality.global=true`。

### 4.3 可重用信封 — Provenance / Freshness（透明度一等）
**Provenance**：`source`(enum: CWA/WRA/USGS/SWPC/WDC_KYOTO/**SWCB**新增) / `dataset` / `stationCode` / `stationName` / `stationLat` / `stationLon` / `distanceKm`(Haversine, 2 位)。
**Freshness**：`observedAt` / `ageSeconds` / `stale`(bool) / `expectedMaxAgeSeconds`(SLA)。

### 4.4 ContextResponse 欄位表
| 欄位 | 型別 | 說明 |
|------|------|------|
| `query` | QueryEcho | 回放解析後參數（含解析後 `at`、`autoRadius`、`maxAutoRadiusKm`）|
| `location` | LocationInfo | `{county, township, inTaiwan}`（取決定 rainfall/water 用的最近站）|
| `rainfall` | RainfallContext \| null | provenance/freshness + `accumulatedMm{h3,h6,h12,h24,h48,h72}` + `maxHourlyIntensityMm`(I) + `effectiveRainfallMm`(Rt) + `rti`(=I×Rt) + `alertBaseline` + `signal`(GREEN/YELLOW/RED) + `signalBasis` |
| `seismic` | SeismicContext \| null | `strongestNearby`(Event) + `nearbyCount` + `window` + provenance(source=CWA/USGS, stationCode=null) + freshness(observedAt=最後成功輪詢時間) |
| `gnssQuality` | GnssQualityContext | **恆非 null**；qualityLevel/kp/dst/bz/solarWind/gScale/rScale/sScale/assessment/recommendation + `global:true` + `provenance[]`(每指數各自來源) + freshness |
| `waterLevel` | WaterLevelContext \| null | provenance/freshness + `waterLevelM` + `alertLevels{level1,2,3}` + `alertStatus`(NORMAL/LEVEL1/2/3) |
| `warnings` | Warning[] | `{domain,code,message,searchedRadiusKm?}`；code ∈ `NO_STATION_IN_RADIUS`/`STALE_DATA`/`OUTSIDE_COVERAGE`/`NO_BASELINE_FOR_TOWNSHIP`/`AT_BEFORE_RETENTION` |
| `meta` | ResponseMeta | `{generatedAt, contractVersion:"1.0", partial}` |

`Event`：`{eventId, time, magnitude, depthKm, epicenterLat, epicenterLon, distanceKm, maxIntensity(事件級測站最大震度), locationDesc, source}`。座標點推估震度需地動衰減模型 → future 欄位 `estimatedLocalIntensity`，暫不提供。四個 domain 欄位建議 `@JsonInclude(ALWAYS)`（顯式 null，配合 warnings 說明原因）。

### 4.5 完整 JSON 範例（單點成功）
> 範例中 SWCB 門檻(250)、燈號、地震 dataset ID(`E-A0015-001`)、水位 GUID 皆為示意；WRA GUID 與 CWA O-A0002-001 已驗證，SWCB 門檻值為官方 R70 需實打 ardswc 取得。
```json
{
  "success": true,
  "data": {
    "query": { "lat": 23.508, "lon": 120.805, "radiusKm": 15, "quakeRadiusKm": 100,
               "at": "2026-07-11T06:00:00Z", "autoRadius": false, "maxAutoRadiusKm": 50 },
    "location": { "county": "嘉義縣", "township": "阿里山鄉", "inTaiwan": true },
    "rainfall": {
      "provenance": { "source": "CWA", "dataset": "O-A0002-001", "stationCode": "C0Z100",
        "stationName": "阿里山", "stationLat": 23.5108, "stationLon": 120.8052, "distanceKm": 0.42 },
      "freshness": { "observedAt": "2026-07-11T05:50:00Z", "ageSeconds": 600,
        "stale": false, "expectedMaxAgeSeconds": 1800 },
      "accumulatedMm": { "h3": 42.5, "h6": 88.0, "h12": 140.5, "h24": 205.0, "h48": 260.5, "h72": 275.0 },
      "maxHourlyIntensityMm": 31.5, "effectiveRainfallMm": 248.7, "rti": 7834.05,
      "alertBaseline": { "township": "阿里山鄉", "thresholdMm": 250, "source": "SWCB",
        "dataset": "土石流警戒基準值", "effectiveFrom": "2025-05-28" },
      "signal": "YELLOW",
      "signalBasis": "effectiveRainfallMm(248.7) 達警戒基準值(250)的 99%，未逾越 → 黃色警戒"
    },
    "seismic": {
      "strongestNearby": { "eventId": "CWA-2026070903", "time": "2026-07-09T21:14:05Z",
        "magnitude": 5.2, "depthKm": 12.4, "epicenterLat": 23.61, "epicenterLon": 120.72,
        "distanceKm": 14.8, "maxIntensity": "4", "locationDesc": "嘉義縣政府東北方", "source": "CWA" },
      "nearbyCount": 3, "window": "30d",
      "provenance": { "source": "CWA", "dataset": "E-A0015-001", "stationCode": null },
      "freshness": { "observedAt": "2026-07-11T05:57:00Z", "ageSeconds": 180,
        "stale": false, "expectedMaxAgeSeconds": 900 }
    },
    "gnssQuality": {
      "qualityLevel": "CAUTION", "kpIndex": 4.3, "dstIndex": -35, "bzComponent": -6.2,
      "solarWindSpeed": 520, "gScale": 1, "rScale": 0, "sScale": 0,
      "assessment": "Kp=4.3, Dst=-35 nT, G1. Minor geomagnetic disturbance.",
      "recommendation": "MONITOR", "global": true,
      "provenance": [
        { "index": "Kp", "source": "SWPC", "observedAt": "2026-07-11T03:00:00Z" },
        { "index": "Dst", "source": "SWPC", "observedAt": "2026-07-11T05:00:00Z" },
        { "index": "solarWind", "source": "SWPC", "observedAt": "2026-07-11T05:55:00Z" }
      ],
      "freshness": { "observedAt": "2026-07-11T03:00:00Z", "ageSeconds": 10800,
        "stale": false, "expectedMaxAgeSeconds": 10800 }
    },
    "waterLevel": {
      "provenance": { "source": "WRA", "dataset": "c4acc691-7416-40ca-9464-292c0c00da92",
        "stationCode": "1730H013", "stationName": "觸口", "stationLat": 23.47, "stationLon": 120.63,
        "distanceKm": 11.2 },
      "freshness": { "observedAt": "2026-07-11T05:50:00Z", "ageSeconds": 600,
        "stale": false, "expectedMaxAgeSeconds": 1800 },
      "waterLevelM": 82.15, "alertLevels": { "level1": 84.0, "level2": 85.5, "level3": 87.0 },
      "alertStatus": "NORMAL"
    },
    "warnings": [],
    "meta": { "generatedAt": "2026-07-11T06:00:00.123Z", "contractVersion": "1.0", "partial": false }
  },
  "message": null, "errorCode": null
}
```
半徑內無站 → HTTP 200 + `success:true` + block=null + `warnings[NO_STATION_IN_RADIUS]` + `meta.partial=true`。台灣 bbox 外合法座標 → 200 + `OUTSIDE_COVERAGE` + `location.inTaiwan=false`（僅經緯度值域非法才 400）。

### 4.6 Freshness SLA（建議預設，config 可覆寫）
| domain | 取樣/輪詢 | `expectedMaxAgeSeconds` |
|--------|----------|------------------------|
| rainfall | 10 分鐘 | 1800 (30m) |
| waterLevel | 10 分鐘 | 1800 (30m) |
| seismic | 5 分鐘（freshness=最後成功輪詢）| 900 (15m) |
| gnss/Kp | 3 小時 | 10800 (3h) |
| gnss/Dst | 每小時 | 7200 (2h) |
| gnss/solarWind | 5 分鐘 | 1200 (20m) |
| reservoir | 每小時/每日 | 90000 (25h) |

> `/meta/freshness` 若要穩定，建議改讀 `system_logs` hypertable（現 `CollectorStatusService` 為 in-memory、重啟歸零）。

### 4.7 版本化 / Auth / Rate limit / 錯誤碼
- **URI 版本化 `/v1`；additive-only**（可加欄位/enum 值/warning code，不得移除改名改型）。契約明文要求消費端**容忍未知 enum 值**，fallback 至最保守處置。棄用政策：`Deprecation`+`Sunset` header + ≥6 個月過渡。
- **Auth**：GNSS 機器客戶端走 X-API-Key，`/api/v1/context/**` → ROLE_USER，直接用現有 `SKYPULSE_API_KEY` 即可上線。演進：擴充 filter 支援 N 把具名 key（per-consumer 輪替/稽核/限流，additive 內部強化）。
- **Rate limit**：單點沿用 100/min；batch 建議獨立 bucket（20 req/min）；建議改以 API-key/clientId 計數（避免多台 GNSS 在同一 NAT IP 後互相排擠）。即時查詢 `Cache-Control: max-age=60`，歷史查詢（過去 `at`）可長 `max-age`。
- **錯誤碼**沿用 `GlobalExceptionHandler`：400 `VALIDATION_FAILED`/`MISSING_PARAMETER`/`TYPE_MISMATCH`/`INVALID_DATE_FORMAT`/`BAD_REQUEST`/`BATCH_TOO_LARGE`；401 `AUTH_REQUIRED`；403 `FORBIDDEN`；429 `RATE_LIMIT_EXCEEDED`；500 `INTERNAL_ERROR`。「合法但無資料/無站/涵蓋外」一律 200 + block=null + warnings。

---

## 5. 新資料取得計畫

### 5.1 WRA 座標
**(a) 水位站座標【已通，只差沒解析】**：現用 station-info GUID `c4acc691-7416-40ca-9464-292c0c00da92`。
API：`GET https://opendata.wra.gov.tw/api/v2/c4acc691-7416-40ca-9464-292c0c00da92?format=JSON`。查核實測含 **`locationbytwd97_xy`**（空格分隔「X Y」公尺，如 `"310928.30 2790168.45"`）、`locationbytwd67_xy`，**無任何 lat/lon 欄** → 必須自行轉換。〔更正：每筆為 **80 欄**（設計稿誤植 85），不影響結論〕
做法：`WraStationInfoRecord` 加 `String locationbytwd97_xy`（record component 名須與 JSON key 完全一致）；`WraWaterLevelCollector.loadStationInfo()` 拆兩 double → proj4j 轉 WGS84 → `setLatitude/setLongitude`（`Station.latitude/longitude` 已存在，純新增無 migration）。已廢站座標欄可能空字串 → 須 null-safe。

**(b) TWD97 TM2 → WGS84**：建議 `org.locationtech.proj4j`。EPSG:3826 proj4 參數字串（免 EPSG DB 依賴）：
```
+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs
```
注意：`k=0.9999` 非 1.0（用錯位移數十公尺）；用參數字串則不需 `proj4j-epsg` artifact。

**(c) 水庫座標【查核：存疑，未解除 — 唯一尚未打通的核心來源】**：reservoir-daily/basic 資料集實測**皆無座標**。替代三選一：
- **A. 環境部 GISEPA_P_27「水庫水質監測站位置圖」**：欄位含 `Dam`、`TM2_X`、`TM2_Y`、**`LONGITUTE`/`LATITUTE`（官方拼字錯誤，已 WGS84 免轉換）**、CountyName、TownName。**但這是「水質監測站」點位、以 Dam 名為鍵、非 WRA 水庫全集**，須以水庫名 join WRA 水情，可能非 1:1、涵蓋率未證實。
- B. 水庫蓄水範圍 KML（data.gov.tw/13795）取 centroid，須 parse KML。
- C. 人工建 Flyway seed（實際有日營運資料的水庫僅 ~20-30 座，一次性人工填、可靠可控）。
- 建議 A 為主 + C 補漏；**動工前須先實際 join 驗涵蓋率**（見 §9）。

### 5.2 CWA 地震震度/PGA（E-A0015-001）
`GET https://opendata.cwa.gov.tw/api/v1/rest/datastore/E-A0015-001?Authorization={CWA_API_KEY}&format=JSON`（現有 client 已用）。查核實測確認：`Intensity` 是 `Earthquake` 直屬子物件（**與 `EarthquakeInfo` 同層、不在其下**），現 DTO 完全沒抓。結構：
```
Intensity.ShakingArea[]  ← 各縣市最大震度
  - AreaDesc / CountyName / AreaIntensity(如"4級") / InfoStatus
  - EqStation[]  ← 該區各測站
      - StationName / StationID / SeismicIntensity(如"4級")
      - pga { EWComponent, NSComponent, VComponent, IntScaleValue, unit(小寫，如"gal") }
      - pgv { ... unit(如"kine") }
      - EpicenterDistance / StationLatitude / StationLongitude
```
〔更正：`pga.unit` 為**小寫 `unit`**（設計稿誤植 `Unit`）；子欄位精確名仍 plausible，實作前打一次真實事件 JSON 對驗〕
落庫：最小改動＝取 ShakingArea 最大 `AreaIntensity` 填 `EarthquakeEvent.max_intensity`（欄已存在、collector 未填）；支援「座標→就近站震度」則需 `earthquake_station_intensity` 表（新 V-migration，含 StationLatitude/Longitude 供 auto-radius）。

### 5.3 NOAA G/R/S scale
`GET https://services.swpc.noaa.gov/products/noaa-scales.json`（免金鑰，現有 `SwpcApiClient.getRawJson` 可直接抓）。查核實測：頂層 key 為**相對日期數字字串** `"-1","0","1","2","3"`（`"0"`=基準日觀測、負=過去、正=預測）。每日物件含 `DateStamp`、`TimeStamp`、`G`/`R`/`S`，每 scale 子欄 `Scale`(字串 "0"~"5" 或 null)、`Text`(none/minor/…)；預測日 R/S 常只有機率（`MinorProb`/`MajorProb`/`Prob`）無 Scale。
〔更正：`noaa_scales.horizon` 應以**相對日偏移 -1..+3** 為準（設計稿此處「待對照」問題已解）〕
現況真實 gap：`SwpcAlertCollector.persist` 從不 parse/set G/R/S → `SpaceWeatherService` 讀到永遠 null。做法：新增 `SwpcNoaaScalesCollector` 取 key `"0"`（當前 observed）的 G/R/S.Scale → Integer 存 `noaa_scales`；`SpaceWeatherService` 改讀當前 observed scale（比掃 alert 訊息穩定）。

### 5.4 SWCB 警戒（怎麼抓）
主體：農業部農村發展及水土保持署。查核確認三個 `.ashx` GET 端點皆真實：
- **(A) 參考雨量站 + 警戒值（核心）**：`https://246.ardswc.gov.tw/webService/GetDebrisRainData.ashx`
  欄位：County, Town, Village, DebrisNO, **AlertValue**(警戒基準值 mm), STID1/STID2(參考雨量站), STName1/2, STRT1/2(權重比)。
- (B) 縣市鄉鎮警戒值：`https://246.ardswc.gov.tw/WebService/GetCountyTownAlertValueList.ashx`（County/Town/AlertValue）。
- (C) 即時警戒：`https://246.ardswc.gov.tw/webService/GetAlertData.ashx`（黃/紅警戒發布時間）。
- 〔查核補充：大規模崩塌另有 `https://ls.ardswc.gov.tw/api/LandslideAlertOpenData` + `GetLSCountyTownAlertValueList.ashx`，建議一併納入〕
- 潛勢溪流座標（給 auto-radius 找就近溪流）：176524「土石流潛勢溪流基本資料」為 **SHP(TWD97)**，座標僅在 geometry 內、無乾淨 JSON 座標 API，須離線 parse 成 seed（屬選配）。
- 做法：新增 `SwcbDebrisCollector` 抓 (A) 一次載全清單存對照表 `debris_stream(debris_no, county/town/village, alert_value, ref_station1/ratio1, ref_station2/ratio2)`；即時警戒另抓 (C)。`DataSource` enum 加 `SWCB`。
- 〔存疑：`AlertValue` 確切單位/語意（R70 mm vs RTI 值）、是否需 query 參數、是否回全量，未逐 byte 打點，見 §9〕

---

## 6. 取樣 / 儲存 / Backfill

### 6.1 取樣頻率
- **雨量：每小時 → 每 10 分鐘（核心變更）**。CWA `O-A0002-001` 查核確認原生 10 分鐘、即時快照；現每小時取樣＝每 6 桶只抓 1 桶。改 `rainfall: "0 2/10 * * * *"`（:02,:12,…，邊界後 ~2 分鐘等 CWA 發布）。**勿比 10 分鐘更密**（來源就是 10 分鐘更新，5 分鐘取樣只重抓被 dedup 丟掉）。去重靠既有 `(time, station_code)` 唯一鍵。
- 其餘：水位已 10 分鐘（維持，加 offset）；**天氣建議維持每小時**（溫濕壓風對山崩非次小時關鍵、省空間）；其餘維持，僅做 offset 錯開。
- **排程驚群**：現幾乎全在 `second=0` 觸發搶 4 執行緒。套 second/minute 雙層 offset（兩個 10 分鐘級重表 rainfall :x2 / water-level :x6 錯開 4 分鐘；同分鐘用秒偏移）。排程池建議 4→6（collector I/O-bound、各寫不同表無新 race）。

### 6.2 Pi 儲存估算（1341 站 × 10 分鐘）
- 列數：1341 × 144/日 ≈ **19.3 萬列/日 ≈ 70.5M/年**，6 年 ≈ 423M。
- **raw_data JSONB 是唯一顯著槓桿**（估 ~700-900 B/列，未實測）：

| 情境 | 未壓縮/年 | 壓縮後/6 年 |
|------|-----------|-------------|
| **停存 raw_data** | ~8.5 GB | **~3-7 GB** |
| **保留 raw_data** | ~63 GB | **~30-50 GB** |

- **建議：10 分鐘級高頻表（rainfall/water_level）停存 raw_data**（結構化欄位已足，透明度靠 source/station metadata/system_logs）；低頻表（earthquake/alert/forecast/reservoir）續存。全庫停存下 6 年 ~9-15 GB，256GB 極寬裕。**容量不是瓶頸。**
- 〔壓縮比、raw_data 單列大小為假設/估算值，非本 Pi 實測，見 §9〕

### 6.3 Collector 熱點（10 分鐘化前置條件，Pi 真正壓力點）
1. **每 tick 1341 次 `existsByStationCode`**（10 分鐘級 = 8046 SELECT/hr 純判斷站是否註冊）→ 啟動時載 in-memory station-code 快取 Set，只查未見過的站。
2. **Hibernate 逐列 INSERT**（未設 batch_size 下 1341 條個別 INSERT/tick）→ 設 `hibernate.jdbc.batch_size=100`+`order_inserts=true`，或改 native `INSERT ... ON CONFLICT (time,station_code) DO NOTHING`（利用 V9 唯一鍵，一併省掉 read-then-filter）。
- 寫入 IO 本身極輕（每 tick <2000 列 ≈ 3 列/秒），HTTP/解析/壓縮皆 negligible。

### 6.4 歷史 Backfill 解析度的殘酷現實
| 資料 | 歷史可得性 | 可重建解析度 |
|------|-----------|--------------|
| 雨量/天氣 (CWA) | opendata 僅即時快照、無歷史 API | **10 分鐘 forward-only**；歷史頂多時/日級（須走 CODiS 另建 ETL）|
| 水位 (WRA) | 快照無歷史 | 只能往後累積 |
| 水庫 (WRA) | 有 daily 資料集 | 日級或可回補（歷史深度未證實）|
| 地震 (CWA/USGS) | USGS FDSN 完整歷史 | **事件級完整回補**（已實作）|
| Kp/Dst/太陽風 | NOAA/OmniWeb 完整歷史 | **完整回補**（已實作）|

**對「從頭 backfill」的實際意義**：雨量 10 分鐘 RTI/移動窗口能力是 **forward-only**，須上線累積滿窗長（72h+，7 日 α 折減需 7 天）才完全準確；上線初期以快照內既有 `trailing_*` 累積欄位做降級 fallback。歷史雨量若真要，只能走 **CWA CODiS（codis.cwa.gov.tw，獨立系統、僅時/日級、機器介面未證實）**另建 ETL。**Backfill 按解析度分層**：raw 10 分鐘表只放即時起往後真值，歷史時/日回填另設 `*_history` 表或直灌 CAgg 等價層，**勿把日值偽裝成 10 分鐘值污染增量主軸**。此不對稱與每資料源「歷史起點/解析度」須在透明度 UI 明示。

---

## 7. 建議實作順序

### P2a — Schema + Collector（先解阻塞）
1. **先解水庫座標阻塞**：實打 GISEPA_P_27 以水庫名 join WRA 水情驗涵蓋率 → 定案 A/B/C 方案（唯一尚未打通的核心來源，動工前必解）。
2. 確認 `timescale/timescaledb:latest-pg16` 是否含 PostGIS → 定案就近查詢空間索引選型（PostGIS / cube+earthdistance / 應用層 haversine）。
3. 重寫乾淨 Flyway baseline（收斂 V1-V14，去 V8 死 CAgg 包袱），CAgg 拆獨立非交易 migration + `WITH NO DATA`。
4. 同步改 entity/collector：欄位正名（RainfallObservation/ReservoirStatus）、Station 去 type/alert_levels + 新增 StationCapability/WaterLevelStation/Reservoir、修 collector 熱點（cache + native upsert）。
5. 新資料接線：WRA 座標解析 + proj4j 轉換、CWA 震度 DTO 擴充、`SwpcNoaaScalesCollector`、`SwcbDebrisCollector`（+ `DataSource` enum 加 SWCB）。
6. 雨量 collector 改 10 分鐘 + 全體 offset cron + 排程池 4→6。

### P2b — 座標查詢 API
1. `StationRepository` 加 geo 查詢（nearest-station，依 §7.2 選型）。
2. `WeatherService`：Rt 改 SWCB α=0.7/7 日、RTI=I×Rt、警戒燈號；`getAccumulatedRainfall` 加 `endTime`（歷史 `at`）版本。
3. `ContextService`（重寫自 DashboardService 骨架）+ `ContextResponse`/Provenance/Freshness DTO（`@JsonInclude(ALWAYS)`）。
4. `ContextController`（單點 + batch + coverage）+ `MetaController` freshness/enums 擴充。
5. 退役 per-site 骨架（DashboardController /site、SiteController、SiteInfo、skypulse.sites.list）。
6. Auth/rate-limit 演進（具名 key / clientId 計數）— 可延後，先用現有單一 USER key 上線。

### P2c — 前端
1. 公共監看台：全域摘要（沿用 monitor/summary）+ 資料透明度儀表（涵蓋/新鮮度/來源，讀 /meta/freshness）。
2. Admin 前端對齊新 schema/端點；退役 per-site 頁面。
3. （選配）座標查詢 demo/除錯頁（coverage 視覺化）。

---

## 8. 需使用者拍板的關鍵決策（彙整去重）

**A. 基礎設施**
1. 就近查詢空間索引選型：PostGIS geography+GiST+`<->`（首選，須確認映像支援）／cube+earthdistance／應用層 bbox+haversine。
2. 座標轉換庫：proj4j（輕、參數字串免 EPSG DB dep，建議）／geotools（重）。是否接受新增 `org.locationtech.proj4j` 依賴。

**B. Schema / 建模**
3. capability 建模：`station_capability` 關聯表（首選，記來源/新鮮度）／stations 上 `has_*` 布林旗標（查更快但擴充要改 schema）。
4. 有效累積雨量 Rt 存法：讀取時算 + 選配 latest 快取表（建議，避免凍死公式版本）／物化 Rt 欄位。
5. 地震震度落庫深度：只補 `EarthquakeEvent.max_intensity`（最小改動）／另建 `earthquake_station_intensity` 站點級表（支援座標→就近站震度 auto-radius）。
6. NOAA G/R/S 落地：新 `SwpcNoaaScalesCollector` + `noaa_scales` 表讀當前 observed（建議）／從 alert message regex 補現有欄。
7. 遷移執行：重寫乾淨 baseline（flyway clean 重跑，建議）／加 V15 drop + V16+ rebuild（保歷史但留 dead 定義）。

**C. 資料取得**
8. **水庫座標來源（動工前阻塞）**：A 環境部 GISEPA_P_27（已 WGS84 按名 join，涵蓋率未證實）／B KML centroid／C 人工 ~20-30 座 seed。建議 A 為主 + C 補漏，先驗涵蓋率。
9. SWCB 潛勢溪流座標是否要（176524 SHP 須離線轉 seed）— 只在 auto-radius 要對「就近潛勢溪流」而非鄉鎮門檻時才需要。
10. 警戒燈號依據：就近潛勢溪流門檻（需溪流座標）／鄉鎮門檻（純 JSON，建議先做）。

**D. API 契約**
11. 是否退役 per-site 骨架（DashboardController /site、SiteController、SiteInfo、skypulse.sites.list）？建議退役。
12. GNSS API key：沿用單一 `SKYPULSE_API_KEY`（可先上線）／擴充 N 把具名 key（per-consumer 輪替/稽核/限流）。
13. Rate limit 是否改以 API-key/clientId 計數（避免多台 GNSS 在同一 NAT IP 後互相排擠）。
14. batch 上限（建議 100 點/請求）+ 是否給 batch 獨立 bucket（建議 20 req/min）。
15. domain 欄位是否 `@JsonInclude(ALWAYS)` 顯式回傳 null（建議）。
16. auto-radius `maxAutoRadiusKm`（建議 50km）+ 各 domain 預設半徑（rainfall/water=radiusKm、quake=100km）是否認可。
17. 台灣 bbox 外合法座標：回 200+`OUTSIDE_COVERAGE`（建議）／400 拒絕。
18. 是否要 `/context/coverage` 與 `/meta/freshness` 兩透明度輔助端點。
19. `at` 歷史查詢可回溯最舊邊界（受 backfill 進度 + 保留策略限制）如何界定。

**E. 取樣 / 儲存**
20. raw_data 取捨：10 分鐘級高頻表停存（建議，6 年 ~3-7 GB vs 保留 ~30-50 GB）／保留／折衷（精簡 JSON 或每小時抽存 1 筆）。
21. raw 10 分鐘保留期限：無限期（保住未來重算 SWCB 公式，建議）／設 2-3 年 retention。
22. 天氣 collector 是否跟進 10 分鐘（建議維持每小時省空間）。
23. 是否為雨量建歷史 backfill：走 CODiS（獨立系統、時/日級、需另建 ETL）／接受 forward-only + 初期 fallback（建議後者）。
24. 排程池 4→6 + 套用 second/minute offset cron 表。
25. collector 熱點是否本階段修（existsByStationCode 記憶體快取 + Hibernate 批次/native ON CONFLICT）— 10 分鐘化前置條件。

---

## 9. 仍未證實 / 需實測的點

**查核已解除（可信）**：SWCB 公式（α=0.7 日折減/7日/RTI=I×Rt/R70）、ardswc 三 .ashx 端點、WRA `locationbytwd97_xy`+EPSG:3826、NOAA scales 日偏移結構、CWA `Intensity.ShakingArea/EqStation`、CWA O-A0002-001 原生 10 分鐘 — 全部證實為真、無捏造端點。

**仍需盯（實作前打點/實測）**：
1. **水庫座標涵蓋率（最高優先，阻塞）**：GISEPA_P_27 是環境部水質站、非 WRA 水庫全集；須以 Dam 名 join WRA 水情驗涵蓋率（實際筆數/是否涵蓋全部有日水情水庫未證實，預覽頁曾顯示「尚無資料」疑前端過濾）。
2. **SWCB `AlertValue` 單位/語意**：推定 R70 mm 有效累積雨量門檻，但是否需 query 參數、是否回全量、單位是 mm vs RTI 值，未逐 byte 打點 → 實打一次印原始 JSON。
3. **CWA 6 年 10 分鐘雨量歷史**：O-A0002-001 forward-only 屬實；CODiS 機器可存取介面/API/欄位/可回溯深度未證實。
4. **CWA `EqStation.pga/pgv` 子欄位精確名**：`unit` 小寫已確認，其餘（EWComponent/NSComponent/VComponent/IntScaleValue）plausible，實作前打一次真實地震事件對驗必現性。
5. **proj4j Maven 版本座標** + 用 `createFromName("epsg:3826")` 是否需 `proj4j-epsg` artifact（用參數字串可規避）。
6. **WRA `locationbytwd97_xy` 是否所有現存站皆非空**（實測樣本為已廢站但有值，未全站抽驗）。
7. **NOAA key `"0"` 是否恆存在、`TimeStamp` 時區**（推定 UTC）未逐案驗證。
8. **`timescale/timescaledb:latest-pg16` 是否內建 PostGIS** — 決定 geom/GiST/`<->` 方案或退擴充/haversine。
9. **`time_bucket` 第三參數（時區）** 在專案實際 TimescaleDB 版本是否支援（2.x 支援）；若較舊，日雨量 CAgg 須改別的本地日界作法。
10. **raw_data JSONB 單列大小（~700-900 B 估算）**：上線後 `SELECT pg_column_size(raw_data)` 抽樣校正。
11. **TimescaleDB 壓縮比（90-95% 數值/80-90% JSONB）**：官方/實務值，非本 Pi+本資料實測。
12. **WRA 水位/水庫站數**（以「數百站」估算，未逐一查證）。
13. **`CollectorStatusService` in-memory、重啟歸零**：`/meta/freshness` 要穩定須改讀 `system_logs`（其 schema 是否含每來源最後成功時間便捷查詢待確認）。
14. **WRA reservoir-daily 可回補歷史深度**未證實。
15. **Spring `CronExpression` 對 minute 欄 `2/10`(start/step) 語法**高度確信但未在本專案實跑驗證。

**落地紅線提醒**：勿把舊 12h 半衰減 0.5（ETR2）誤當 SWCB 官方法 — 官方是日折減 0.7、7 日/168hr 窗、`Rt=R0+Σ0.7^i·Ri`、`RTI=I×Rt`、警戒基準值=R70(mm)。
---

## 10. 動工前打點結果（2026-07-12 實測，補 §9）

- **§9.8 PostGIS → 已解**：`timescale/timescaledb:latest-pg16` **不含 PostGIS**；`cube`(1.5)+`earthdistance`(1.2) 可用。就近站查詢改用 **cube+earthdistance**（`CREATE EXTENSION cube, earthdistance;` + `ll_to_earth` GiST 索引 + `earth_distance`/`earth_box`），非 PostGIS geography。§3.2 的 `geom geography` DDL 須改為 earthdistance 方案（或應用層 bbox+haversine）。
- **§9.1 水庫座標 → 阻塞確認**：WRA reservoir 資料集(GUID 2be9044c…)實測欄位無任何座標。必走替代：環境部 GISEPA_P_27（按 Dam 名 join，涵蓋率待驗）／KML centroid／人工 ~20-30 座 seed。**P2a 動工前仍須驗涵蓋率**。
- **§9.2 SWCB AlertValue → 仍未打通**：`https://246.ardswc.gov.tw/webService/GetDebrisRainData.ashx` 直接 GET 回空（25s 無輸出）；base OpenData API 前測回 200。可能需 query 參數/特定 header/POST。**警戒燈號的資料存取是 P2a 首要待解**——動工前須先確認正確呼叫方式與 AlertValue 格式，否則座標→鄉鎮→警戒燈號這條鏈缺料。
