"""Materialize TimescaleDB -> Parquet lake, and report coverage.

Read-only. Time-series hypertables are exported partitioned by year/month; dimension
tables and continuous aggregates are exported as single files. A full re-export each
run (the whole DB is ~0.3 GB, seconds on NVMe) keeps it idempotent and simple — the
target dir is wiped first so re-runs never duplicate. (Incremental watermarks are a
later optimization; not needed at this size.)
"""
from __future__ import annotations

import os
import shutil

from config import settings
from duck import attach_pg, new_connection

# hypertables — all use `time` as the TIMESTAMPTZ time column
TIMESERIES = [
    "rainfall_observations",
    "water_level_observations",
    "weather_observations",
    "reservoir_status",
    "kp_index_records",
    "dst_index_records",
    "solar_wind_records",
    "noaa_scales",
    "earthquake_events",
    "system_logs",
]

# static / low-churn tables exported whole
DIMENSIONS = [
    "stations",
    "station_capability",
    "water_level_station",
    "reservoirs",
    "township_alert_baseline",
    "debris_stream",
    "hazard_alerts",
    "weather_forecasts",
    "earthquake_station_intensity",
    "space_weather_alerts",
]

# TimescaleDB continuous aggregates (materialized views)
CONTINUOUS_AGG = ["rainfall_hourly", "rainfall_daily"]


def materialize(con=None) -> dict:
    own = con is None
    con = con or new_connection()
    try:
        attach_pg(con)
        os.makedirs(settings.lake_dir, exist_ok=True)
        done: dict[str, int] = {}
        failed: dict[str, str] = {}

        for t in TIMESERIES:
            out = os.path.join(settings.lake_dir, t)
            try:
                shutil.rmtree(out, ignore_errors=True)  # idempotent: no stale partitions
                con.execute(
                    f"COPY (SELECT *, year(time) AS _yr, month(time) AS _mo FROM pg.{t}) "
                    f"TO '{out}' (FORMAT parquet, PARTITION_BY (_yr, _mo))"
                )
                done[t] = con.execute(f"SELECT count(*) FROM pg.{t}").fetchone()[0]
            except Exception as e:  # noqa: BLE001 - report, don't abort the whole run
                failed[t] = str(e)

        for name in DIMENSIONS + CONTINUOUS_AGG:
            out = os.path.join(settings.lake_dir, f"{name}.parquet")
            try:
                con.execute(f"COPY (SELECT * FROM pg.{name}) TO '{out}' (FORMAT parquet)")
                done[name] = con.execute(f"SELECT count(*) FROM pg.{name}").fetchone()[0]
            except Exception as e:  # noqa: BLE001
                failed[name] = str(e)

        return {
            "materialized": done,
            "failed": failed,
            "summary": f"{len(done)} materialized, {len(failed)} failed",
        }
    finally:
        if own:
            con.close()


def coverage() -> dict:
    """Per-table data-potential report, read straight from Postgres (source of truth)."""
    con = attach_pg(new_connection())
    try:
        ts = []
        for t in TIMESERIES:
            try:
                cnt, tmin, tmax = con.execute(
                    f"SELECT count(*), min(time), max(time) FROM pg.{t}"
                ).fetchone()
                span = None
                per_day = None
                if cnt and tmin and tmax:
                    span = (tmax - tmin).total_seconds() / 86400.0
                    per_day = round(cnt / span, 1) if span > 0 else None
                ts.append({
                    "table": t,
                    "rows": cnt,
                    "from": tmin.isoformat() if tmin else None,
                    "to": tmax.isoformat() if tmax else None,
                    "spanDays": round(span, 1) if span else None,
                    "rowsPerDay": per_day,
                })
            except Exception as e:  # noqa: BLE001
                ts.append({"table": t, "error": str(e)})

        dims = {}
        for d in DIMENSIONS + CONTINUOUS_AGG:
            try:
                dims[d] = con.execute(f"SELECT count(*) FROM pg.{d}").fetchone()[0]
            except Exception as e:  # noqa: BLE001
                dims[d] = f"ERR: {e}"

        return {"timeseries": ts, "dimensions": dims}
    finally:
        con.close()
