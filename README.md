# SkyPulse 天脈

GNSS 邊坡防災監測輔助決策資料中台。匯集氣象、水文、地震、太空天氣資料，提供給既有 GNSS 位移解算系統進行交叉比對與綜合決策。

## 功能概覽

- **12 個排程資料收集器** — 自動從 CWA、SWPC、USGS、WRA 四個來源擷取資料
- **23 個 REST API 端點** — 包含分頁、認證、參數驗證
- **GNSS 品質指標** — 依據 Kp + Dst 雙指標判定 GNSS 訊號可靠度
- **歷史資料回填** — 透過 USGS API 和 NASA OMNIWeb 回填 2019 年至今的資料
- **CWA/USGS 地震去重** — 跨資料源自動識別同一地震事件
- **監控 Dashboard** — 內建即時系統監控頁面
- **Swagger 文件** — 自動生成 API 文件

## 技術棧

| 項目 | 技術 |
|------|------|
| 語言 | Java 21 (LTS) |
| 框架 | Spring Boot 3.4.4 |
| 資料庫 | PostgreSQL 16 + TimescaleDB |
| ORM | Spring Data JPA + Flyway |
| HTTP 客戶端 | WebClient (非阻塞) |
| 容器化 | Docker Compose |
| API 文件 | springdoc-openapi (Swagger UI) |
| 認證 | API Key (X-API-Key header) |
| 速率限制 | Bucket4j |

## 資料來源

| 來源 | 認證 | 資料類型 | 收集頻率 |
|------|------|---------|---------|
| CWA 中央氣象署 | API Key | 雨量、氣象、地震、警報、預報 | 3 分鐘 ~ 6 小時 |
| NOAA SWPC | 公開 | Kp、Dst、太陽風、太空天氣警報 | 5 ~ 15 分鐘 |
| USGS | 公開 | 地震 (M4.0+, 台灣區域) | 5 分鐘 |
| WRA 水利署 | 公開 | 河川水位、水庫水情 | 10 分鐘 ~ 1 小時 |

---

## 快速開始

### 前置需求

- Docker Desktop (或 Docker Engine + Docker Compose)
- CWA API Key（[申請連結](https://opendata.cwa.gov.tw/)）

### 1. 取得程式碼

```bash
git clone https://github.com/rstltd/SkyPulse.git
cd SkyPulse
```

### 2. 設定環境變數

```bash
cp docker/.env.example docker/.env
```

編輯 `docker/.env`：

```env
DB_PASSWORD=your_secure_password
CWA_API_KEY=your_cwa_api_key
SKYPULSE_API_KEY=your_api_key_for_clients
SKYPULSE_ADMIN_KEY=your_admin_key
```

### 3. 建置並啟動

```bash
# 建置 JAR
./mvnw clean package -DskipTests

# 啟動 (開發環境)
cd docker && docker compose up -d --build
```

### 4. 確認服務正常

```bash
# 健康檢查
curl http://localhost:8080/api/v1/health

# 監控 Dashboard
open http://localhost:8080/

# Swagger API 文件
open http://localhost:8080/swagger-ui/index.html
```

---

## 部署指南

### 開發環境

```bash
# 啟動資料庫
cd docker && docker compose up -d

# 啟動應用程式 (本機開發)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 正式環境（Docker Compose）

```bash
# 正式環境會驗證所有環境變數是否設定
cd docker && docker compose -f docker-compose.prod.yml up -d --build
```

正式環境特點：
- 資料庫不對外暴露 port（僅容器間通訊）
- 所有環境變數為必填
- `restart: unless-stopped` 自動重啟

### Raspberry Pi 部署

1. 安裝 Docker：
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
```

2. 將專案複製到 Pi：
```bash
scp -r SkyPulse/ pi@raspberrypi:~/
```

3. 在 Pi 上建置並啟動：
```bash
cd SkyPulse
./mvnw clean package -DskipTests
cd docker && docker compose -f docker-compose.prod.yml up -d --build
```

> 注意：首次建置在 Pi 上可能需要較長時間。可在開發機上 cross-compile JAR 後直接複製 `target/*.jar`。

### 環境變數一覽

| 變數 | 必填 | 說明 |
|------|------|------|
| `DB_PASSWORD` | 是 | PostgreSQL 密碼 |
| `CWA_API_KEY` | 是 | 中央氣象署 API Key |
| `SKYPULSE_API_KEY` | 是 | 資料查詢 API 認證金鑰 |
| `SKYPULSE_ADMIN_KEY` | 是 | 管理操作 API 認證金鑰 |
| `SKYPULSE_CORS_ORIGINS` | 否 | 允許的跨域來源（預設 `http://localhost:8080`） |

---

## API 使用教學

### 認證方式

所有資料查詢 API 需在 Header 帶入 API Key：

```bash
curl -H "X-API-Key: your_api_key" http://localhost:8080/api/v1/...
```

| 端點類型 | 所需 Key |
|---------|---------|
| `/api/v1/health`、Dashboard、Swagger | 不需認證 |
| `/api/v1/weather/**`、`/api/v1/seismic/**` 等查詢 API | `SKYPULSE_API_KEY` |
| `/api/v1/backfill/**`、`/api/v1/system/**` | `SKYPULSE_ADMIN_KEY` |

### 常用端點範例

#### GNSS 品質指標（最重要的端點）

```bash
curl -H "X-API-Key: $KEY" http://localhost:8080/api/v1/spaceweather/gnss-quality
```

回應：
```json
{
  "success": true,
  "data": {
    "qualityLevel": "NORMAL",
    "kpIndex": 2.67,
    "dstIndex": -39,
    "solarWindSpeed": 607,
    "bzComponent": -2,
    "assessment": "Kp=2.67, Dst=-39.0 nT. Geomagnetic conditions normal.",
    "recommendation": "NORMAL"
  }
}
```

品質等級：
| 等級 | 條件 | 建議 |
|------|------|------|
| NORMAL | Kp < 4 且 Dst > -30 | 正常運作 |
| CAUTION | Kp 4~5 或 Dst -30~-50 | 監控中 |
| DEGRADED | Kp 5~7 或 Dst -50~-100 或 G2+ | 標記位移資料 |
| SEVERE | Kp > 7 或 Dst < -100 或 G3+ | 位移資料不可靠 |

#### 最新雨量觀測

```bash
curl -H "X-API-Key: $KEY" http://localhost:8080/api/v1/weather/rainfall/latest
```

#### 特定站點累積雨量

```bash
# 24 小時累積雨量
curl -H "X-API-Key: $KEY" \
  "http://localhost:8080/api/v1/weather/rainfall/accumulated?stationCode=C0D660&hours=24"
```

#### 最近地震

```bash
curl -H "X-API-Key: $KEY" http://localhost:8080/api/v1/seismic/events/latest
```

#### 特定時間範圍地震（帶分頁）

```bash
curl -H "X-API-Key: $KEY" \
  "http://localhost:8080/api/v1/seismic/events?since=2025-01-01T00:00:00Z&minMag=4.5&page=0&size=10"
```

#### 附近地震

```bash
# 花蓮 100km 半徑內
curl -H "X-API-Key: $KEY" \
  "http://localhost:8080/api/v1/seismic/events/nearby?lat=23.97&lon=121.60&radiusKm=100"
```

#### GNSS 站點綜合 Dashboard

```bash
# 一次取得所有相關資料
curl -H "X-API-Key: $KEY" \
  "http://localhost:8080/api/v1/dashboard/site/GNSS-001?lat=23.5&lon=121.0&stationCode=C0D660"
```

#### 水庫水情

```bash
curl -H "X-API-Key: $KEY" http://localhost:8080/api/v1/hydrology/reservoirs
```

#### 歷史資料回填（需 Admin Key）

```bash
# 回填 USGS 地震（2019 至今）
curl -H "X-API-Key: $ADMIN_KEY" -X POST \
  "http://localhost:8080/api/v1/backfill/usgs-earthquake?startDate=2019-01-01&endDate=2026-03-24"

# 回填太空天氣（Kp + Dst + 太陽風，via NASA OMNIWeb）
curl -H "X-API-Key: $ADMIN_KEY" -X POST \
  "http://localhost:8080/api/v1/backfill/omniweb-space-weather?startDate=2019-01-01&endDate=2026-03-24"
```

> 注意：回填前需在 `application.yml` 設定 `skypulse.backfill.enabled: true`

### 速率限制

| 端點 | 限制 |
|------|------|
| 資料查詢 API | 100 次/分鐘/IP |
| 回填 API | 5 次/小時/IP |
| 健康檢查、Swagger | 無限制 |

超限會回傳 `429 Too Many Requests`。

---

## 系統監控

### Dashboard

瀏覽器開啟 `http://localhost:8080/` 即可看到：
- GNSS 品質指標即時狀態
- 12 個 Collector 運行狀況
- 最近地震列表
- 活躍警報

Dashboard 每 60 秒自動刷新。

### Collector 狀態 API

```bash
curl -H "X-API-Key: $ADMIN_KEY" http://localhost:8080/api/v1/system/collectors
```

---

## 資料庫

使用 TimescaleDB（PostgreSQL 擴充）儲存時序資料：

- **Hypertable** — 時序資料自動分區，查詢效能最佳化
- **壓縮** — 7 天後自動壓縮，儲存空間節省 ~90%
- **連續聚合** — 自動計算 3h/24h/48h/72h 累積雨量

### 儲存空間估算

| 期間 | 未壓縮 | 壓縮後 |
|------|--------|--------|
| 1 年 | ~9.5 GB | ~1.5 GB |
| 6 年 | ~57 GB | ~6 GB |

256 GB SSD 可容納 10 年以上資料。

---

## 開發

### 執行測試

```bash
# 啟動測試資料庫
cd docker && docker compose -f docker-compose.test.yml up -d

# 執行所有測試
./mvnw test

# 執行單一測試
./mvnw test -Dtest=CwaRainfallCollectorTest
```

### 專案結構

```
src/main/java/com/rstltd/skypulse/
├── api/v1/          # REST Controllers (7 個)
├── api/dto/         # API 回應 DTO
├── backfill/        # 歷史資料回填
├── collector/       # 排程資料收集器
│   ├── common/      #   CollectorBase 抽象基底
│   ├── cwa/         #   CWA 氣象署 (5 collector)
│   ├── swpc/        #   NOAA 太空天氣 (4 collector)
│   ├── usgs/        #   USGS 地震 (1 collector)
│   └── wra/         #   WRA 水利署 (2 collector)
├── config/          # 設定 (Security, WebClient, Scheduler)
├── domain/          # JPA Entity (12 個)
├── repository/      # JPA Repository (12 個)
├── service/         # 業務邏輯 (7 個)
└── util/            # 工具類 (TimeUtils, GeoUtils)
```

---

## 授權

Copyright RST.ltd. All rights reserved.
