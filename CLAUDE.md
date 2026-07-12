# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SkyPulse (天脈) is a Spring Boot data platform for RST.ltd's GNSS slope disaster monitoring service. It aggregates meteorological, hydrological, seismic, and space weather data to assist landslide early warning decisions. The system feeds data to an existing GNSS displacement calculation system (5-6 years in operation) for cross-referencing.

**Deployment target:** Raspberry Pi with 256 GB SSD.

## Tech Stack

### Backend
- Java 21 + Spring Boot 3.4.4 + Spring Data JPA
- PostgreSQL 16 with TimescaleDB (hypertables, compression, continuous aggregates)
- WebClient (non-blocking HTTP) for external API calls
- Flyway for DB migrations (V1-V12)
- Docker Compose (timescale/timescaledb:latest-pg16)
- Jackson for JSON
- springdoc-openapi v2.8.6 (Swagger UI at /swagger-ui.html)
- Bucket4j for rate limiting
- Spring Security (session-based + API key authentication)

### Frontend
- Vue 3 (Composition API, `<script setup>`) + TypeScript
- Vite (dev server port 5173)
- ECharts (via vue-echarts) for data visualization
- Axios for HTTP requests (session cookie auth)
- Vue Router v4 (SPA with backend forwarding via `SpaForwardingController`)

## Build & Run Commands

```bash
# Start database (dev)
cd docker && docker-compose up -d

# Start test database (integration tests)
cd docker && docker compose -f docker-compose.test.yml up -d

# Build (Maven assumed from Spring Boot project)
./mvnw clean package

# Run with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
./mvnw test

# Run single test
./mvnw test -Dtest=CwaRainfallCollectorTest

# Frontend dev
cd frontend && npm install && npm run dev
```

## Required Environment Variables

- `CWA_API_KEY` — CWA (Central Weather Administration) API key
- `DB_PASSWORD` — PostgreSQL password (defaults to `skypulse_dev` in dev)
- `SKYPULSE_API_KEY` — Data query API key (X-API-Key header, USER role)
- `SKYPULSE_ADMIN_KEY` — Admin operations API key (X-API-Key header, ADMIN role)
- `SKYPULSE_LOGIN_USER` / `SKYPULSE_LOGIN_PASSWORD` — Optional web login credentials
- `SKYPULSE_CORS_ORIGINS` — Allowed CORS origins (default: http://localhost:8080)

## Architecture

### Data Flow

External APIs → **Collector** (scheduled ETL) → **Repository** (JPA) → **Service** → **REST API** (`/api/v1/...`) → **Vue Frontend**

### Key Layers

- **`collector/`** — Scheduled data fetchers (12 collectors). All extend `CollectorBase<T>` which provides `fetch()` → `validate()` → `persist()` lifecycle with retry (exponential backoff, max 2 retries), logging, and timing. Four data sources: `cwa/` (5 collectors), `wra/` (2), `usgs/` (1), `swpc/` (4).
- **`backfill/`** — One-time historical data import (6 years). Triggered manually via `POST /api/v1/backfill/{source}`. Uses exists-check-then-insert (existsByEventId/existsById) for idempotent dedup, supports resume on interruption.
- **`domain/`** — JPA entities organized by domain: `weather/`, `seismic/`, `spaceweather/`, `hydrology/`, `alert/`, `station/`, `log/`.
- **`service/`** — Business logic: `WeatherService` (effective rainfall ETR1/ETR2), `SeismicService` (cross-source dedup), `SpaceWeatherService` (GNSS quality), `HydrologyService` (water level + reservoirs), `AlertService`, `DashboardService` (multi-domain aggregation), `SystemLogService` (audit trail).
- **`api/`** — REST controllers:
  - `AuthController` (`/auth`) — Login/logout/session check
  - `WeatherController` (`/api/v1/weather`) — Rainfall, observations, forecasts, effective rainfall
  - `SeismicController` (`/api/v1/seismic`) — Earthquake events
  - `SpaceWeatherController` (`/api/v1/spaceweather`) — Kp, Dst, solar wind, GNSS quality
  - `HydrologyController` (`/api/v1/hydrology`) — Water levels, reservoir status
  - `AlertController` (`/api/v1/alerts`) — Hazard alerts
  - `StationController` (`/api/v1/stations`) — Station metadata
  - `DashboardController` (`/api/v1/dashboard`) — Consolidated site dashboard
  - `MonitorController` (`/api/v1/monitor`) — Public monitoring summary
  - `SystemController` (`/api/v1/system`) — Collector status, system logs (ADMIN)
  - `HealthController` (`/api/v1/health`) — Health check
  - `BackfillController` (`/api/v1/backfill`) — Historical data import (ADMIN)
- **`config/`** — WebClient beans (5: cwa, wra, usgs, swpc, omniWeb), SecurityConfig (dual filter chain), SpaForwardingController, SchedulerConfig (4 threads), RateLimitFilter, OpenApiConfig.

### Security

Two-chain filter architecture:
- **API chain** (`/api/v1/**`): `SessionOrApiKeyAuthFilter` + `RateLimitFilter`
  - Public (no auth): `/api/v1/health`, `/api/v1/monitor/**`
  - USER role: All other `/api/v1/**` endpoints
  - ADMIN role: `/api/v1/backfill/**`, `/api/v1/system/**`
- **Web chain** (`/**`): Session-based for frontend pages
  - Public: `/auth/**`, `/`, `/assets/**`, `/swagger-ui/**`

Rate limiting (Bucket4j):
- Data query APIs: 100 requests/min/IP
- Backfill APIs: 5 requests/hour/IP

### External Data Sources

| Source | Auth | Data |
|--------|------|------|
| CWA (氣象署) | API Key required | Rainfall, weather, earthquakes, alerts, forecasts |
| WRA (水利署) | Public (data.gov.tw) | Water levels, reservoir status |
| USGS | Public | Earthquakes (M4.0+, Taiwan region) |
| NOAA SWPC | Public | Kp, Dst, solar wind, space weather alerts |
| NASA OmniWeb | Public | Kp/Dst historical backfill |

### Database Design

- Time-series tables use TimescaleDB hypertables with 7-day auto-compression
- Accumulated rainfall is computed in WeatherService at query time (sum of precip_1hr). The V8 continuous aggregates were broken — a tautological window filter made every window equal the plain hourly sum — and were dropped in V13; true rolling-window accumulation is rebuilt in the Phase 2 schema rework
- `earthquake_events` is NOT compressed (very low volume: ~5-10 rows/day)
- All observation tables store `raw_data JSONB` for traceability
- Segment-by keys: `station_code` for weather/hydrology, `reservoir_id` for reservoirs
- `system_logs` hypertable for collector execution metrics (30-day retention, daily cleanup at 3AM UTC)
- Station `alert_level1/2/3` fields for water level alert thresholds

### Effective Rainfall Calculation

WeatherService implements SWCB-standard effective rainfall:
- **ETR1**: Event-based accumulated rainfall (events split when 6h rainfall < 4mm)
- **ETR2**: Decay-weighted effective rainfall: R_eff = Σ(precip_1hr_i × 0.5^(t_i / T½)), T½ = 12h
- API: `GET /api/v1/weather/rainfall/effective?stationCode=...&windowHours=72`

### GNSS Quality Classification

```
NORMAL:   Kp < 4   AND Dst > -30 nT
CAUTION:  Kp 4~5   OR  Dst -30 ~ -50 nT
DEGRADED: Kp 5~7   OR  Dst -50 ~ -100 nT   OR G2+
SEVERE:   Kp > 7   OR  Dst < -100 nT        OR G3+
```

### Scheduling (cron in application.yml)

12 scheduled collectors across 4 sources:

| Collector | Frequency |
|-----------|-----------|
| CWA Alerts | 3 min |
| CWA/USGS Earthquakes | 5 min |
| SWPC Solar Wind | 5 min |
| WRA Water Level | 10 min |
| SWPC Kp Index | 15 min |
| CWA Rainfall / Weather | 1 hr |
| WRA Reservoir / SWPC Dst | 1 hr |
| SWPC Alerts | 10 min |
| CWA Forecast | 6 hr |

All schedules configured under `skypulse.{source}.schedule` in YAML.

### Frontend Pages

| Route | Auth | Description |
|-------|------|-------------|
| `/login` | Public | 登入頁面 |
| `/dashboard` | USER | 總覽儀表板 (GNSS 品質、collector 狀態、地震、警報) |
| `/weather` | USER | 降雨分析 (ETR1/ETR2、累積雨量、I-R 散佈圖) |
| `/hydrology` | USER | 水位站 (警戒水位) + 水庫狀態 |
| `/seismic` | USER | 地震事件列表 (規模/深度篩選) |
| `/spaceweather` | USER | 太空天氣 (Kp/Dst 圖表、GNSS 品質評估) |
| `/alerts` | USER | 災害警報 (即時 + 歷史) |
| `/admin` | ADMIN | Collector 狀態 + Backfill 操作 |
| `/logs` | ADMIN | 系統日誌查詢與統計 |

## Design Decisions

- CWA and USGS both provide earthquake data — deduplication is needed for overlapping events (30s time window, 10km distance, 0.3 magnitude threshold)
- Earthquake filtering at collector level: only M4.0+ persisted
- USGS queries scoped to Taiwan region (lat 21.5-25.5, lon 119.0-122.5)
- Backfill is disabled by default (`skypulse.backfill.enabled: false`)
- JPA `ddl-auto: validate` — schema managed entirely by Flyway
- All timestamps use `TIMESTAMPTZ` (UTC-aware)
- API responses wrapped in `ApiResponse<T>` with `success`, `data`, `timestamp`, `code` fields
- Paginated endpoints use `PagedResponse<T>` (page, size, totalElements, totalPages)

## Common Errors to Avoid

- Testcontainers 1.20.6 (docker-java 3.4.1) 與 Docker Desktop 29.x (API 1.53) 不相容。改用 Docker Compose 測試 DB (`docker/docker-compose.test.yml`, port 5433)
- TimescaleDB hypertable 的 UNIQUE/PRIMARY KEY 必須包含分區鍵 (time)，否則 `create_hypertable` 會失敗
- TimescaleDB continuous aggregates (`CREATE MATERIALIZED VIEW ... WITH (timescaledb.continuous)`) 不能在 transaction 內執行。拆到獨立 Flyway migration 並使用 `WITH NO DATA`

## Testing

Rationale and the full layered pipeline: `docs/TESTING_STRATEGY.md`. The pipeline is deliberately multi-layered so **no single metric (line coverage, mutation score) is a gameable target** — because the same model often writes both the code and its tests, tests must be anchored to oracles that are *independent of the implementation*, not a replay of what the code currently does.

### Hard rules when writing tests (anti-gaming guardrails)

- **Never derive an expected value from the code under test.** Expected values come from an independent source: a spec (SWCB effective-rainfall formulas; the dedup thresholds under "Design Decisions"; CWA/USGS/NOAA docs), a hand calculation, a known sample, a property, or an independent reference implementation.
- **Prefer relationship-based assertions** (round-trip, symmetry, monotonicity, comparison to a reference) over pinning a magic number that could be back-filled from the implementation.
- **Property tests (jqwik) encode domain math / spec; they must NOT call the SUT to compute the expected value.** No trivial properties (`assertNotNull`, `x == x`); each property states which wrong implementation it rules out. Canonical example: `SeismicServiceDedupPropertyTest` (symmetry, idempotence, 30s boundary matrix).
- **Metamorphic relations must come from the spec/physics**, not from observed code behaviour.
- **Never make a test green by weakening it**: no removing/commenting assertions, empty test bodies, empty `catch` swallowing exceptions, added `retry`/`sleep`, or widened tolerance/float epsilon to hide a real failure. When a property fails, fix the code or correct the oracle against the spec — do not loosen the property to pass.
- **Snapshot/approval tests only characterize existing trusted code during refactors**, never as the correctness oracle for new behaviour. Do not auto-update `*.approved`/snapshot files.
- **Integration tests run against real Postgres+TimescaleDB** (`docker/docker-compose.test.yml`, port 5433) — never silently swap to H2/embedded (TimescaleDB semantics would pass falsely; see "Common Errors to Avoid").
- **Mutation testing (PIT) is a diagnostic, not a KPI.** Judge each surviving mutant: a *productive* survivor = a missing assertion (add a meaningful test); an *equivalent* mutant (e.g. a `>` vs `>=` on a floating-point threshold no input can hit exactly — as with the 10km / 0.3-mag dedup boundaries) must NOT be chased. Never set a global mutation-score target; never expand PIT exclusion lists to raise the number.

### What a human reviews (not the AI)

Oracle provenance (does the expected value trace to an external authority?), assertion intent (why is this value correct?), and a spot-check of surviving mutants / property statements (real gap vs equivalent mutant).

### Commands

```bash
./mvnw test                                                                 # unit + integration
./mvnw -P'!frontend' test-compile org.pitest:pitest-maven:mutationCoverage  # mutation diagnostic → target/pit-reports/index.html
```

## Workflow Preferences

- 使用者偏好先進 plan mode 規劃再開始實作
