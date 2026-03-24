# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SkyPulse (天脈) is a Spring Boot data platform for RST.ltd's GNSS slope disaster monitoring service. It aggregates meteorological, hydrological, seismic, and space weather data to assist landslide early warning decisions. The system feeds data to an existing GNSS displacement calculation system (5-6 years in operation) for cross-referencing.

**Deployment target:** Raspberry Pi with 256 GB SSD.

## Tech Stack

- Java 21 + Spring Boot 3.4.4 + Spring Data JPA
- PostgreSQL 16 with TimescaleDB (hypertables, compression, continuous aggregates)
- WebClient (non-blocking HTTP) for external API calls
- Flyway for DB migrations (V1-V9)
- Docker Compose (timescale/timescaledb:latest-pg16)
- Jackson for JSON
- springdoc-openapi (Swagger UI at /swagger-ui.html)

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
```

## Required Environment Variables

- `CWA_API_KEY` — CWA (Central Weather Administration) API key
- `DB_PASSWORD` — PostgreSQL password (defaults to `skypulse_dev` in dev)

## Architecture

### Data Flow

External APIs → **Collector** (scheduled ETL) → **Repository** (JPA) → **Service** → **REST API** (`/api/v1/...`)

### Key Layers

- **`collector/`** — Scheduled data fetchers. All extend `CollectorBase<T>` which provides `fetch()` → `validate()` → `persist()` lifecycle with retry, logging, and timing. Four data sources: `cwa/` (CWA weather bureau), `wra/` (WRA hydrology), `usgs/` (USGS earthquakes), `swpc/` (NOAA space weather).
- **`backfill/`** — One-time historical data import (6 years). Triggered manually via `POST /api/v1/backfill/{source}`. Uses UPSERT to avoid duplicates, supports resume on interruption.
- **`domain/`** — JPA entities organized by domain: `weather/`, `seismic/`, `spaceweather/`, `hydrology/`, `alert/`, `station/`.
- **`api/v1/`** — REST controllers. Includes a GNSS quality indicator endpoint that cross-references Kp + Dst indices.
- **`config/`** — Each external API has its own named `WebClient` bean (e.g., `cwaWebClient`, `swpcWebClient`). Max in-memory buffer: 5 MB for CWA/WRA.

### External Data Sources

| Source | Auth | Data |
|--------|------|------|
| CWA (氣象署) | API Key required | Rainfall, weather, earthquakes, alerts, forecasts |
| WRA (水利署) | Public (data.gov.tw) | Water levels, reservoir status |
| USGS | Public | Earthquakes (M4.0+, Taiwan region) |
| NOAA SWPC | Public | Kp, Dst, solar wind, space weather alerts |

### Database Design

- Time-series tables use TimescaleDB hypertables with 7-day auto-compression
- Continuous aggregates for accumulated rainfall (3h/24h/48h/72h) — the core landslide warning metric
- `earthquake_events` is NOT compressed (very low volume: ~5-10 rows/day)
- All observation tables store `raw_data JSONB` for traceability
- Segment-by keys: `station_code` for weather/hydrology, `reservoir_id` for reservoirs

### GNSS Quality Classification

```
NORMAL:   Kp < 4   AND Dst > -30 nT
CAUTION:  Kp 4~5   OR  Dst -30 ~ -50 nT
DEGRADED: Kp 5~7   OR  Dst -50 ~ -100 nT   OR G2+
SEVERE:   Kp > 7   OR  Dst < -100 nT        OR G3+
```

### Scheduling (cron in application.yml)

Highest frequency collectors: CWA alerts (3 min), CWA/USGS earthquakes & SWPC solar wind (5 min). Lowest: forecasts (6-12h). All schedules are configured under `skypulse.{source}.schedule` in YAML.

## Design Decisions

- CWA and USGS both provide earthquake data — deduplication is needed for overlapping events
- Earthquake filtering at collector level: only M4.0+ persisted
- USGS queries scoped to Taiwan region (lat 21.5-25.5, lon 119.0-122.5)
- Backfill is disabled by default (`skypulse.backfill.enabled: false`)
- JPA `ddl-auto: validate` — schema managed entirely by Flyway
- All timestamps use `TIMESTAMPTZ` (UTC-aware)

## Common Errors to Avoid

- Testcontainers 1.20.6 (docker-java 3.4.1) 與 Docker Desktop 29.x (API 1.53) 不相容。改用 Docker Compose 測試 DB (`docker/docker-compose.test.yml`, port 5433)
- TimescaleDB hypertable 的 UNIQUE/PRIMARY KEY 必須包含分區鍵 (time)，否則 `create_hypertable` 會失敗
- TimescaleDB continuous aggregates (`CREATE MATERIALIZED VIEW ... WITH (timescaledb.continuous)`) 不能在 transaction 內執行。拆到獨立 Flyway migration 並使用 `WITH NO DATA`

## Workflow Preferences

- 使用者偏好先進 plan mode 規劃再開始實作
