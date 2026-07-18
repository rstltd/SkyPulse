"""SkyPulse analytics sidecar — FastAPI over DuckDB + a Parquet lake.

Read-only companion to the Spring app: it never writes to TimescaleDB. Heavy historical
scans hit the materialized Parquet lake; small fresh lookups (effective rainfall) hit
Postgres directly. Response envelope mirrors the Spring `ApiResponse` shape: {success, data}.

Asia/Taipei has no DST (fixed UTC+8), so the calendar date is derived with `+ INTERVAL '8 hours'`
rather than a named-timezone cast — this avoids needing DuckDB's ICU extension.
"""
from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from datetime import date, datetime, timezone
from typing import Optional

from fastapi import FastAPI, HTTPException, Query

import duck
import materialize as mat
from config import settings
from features.rainfall import effective_rainfall, signal

log = logging.getLogger("analytics")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

_materialize_lock = asyncio.Lock()
_state = {"lastMaterialize": None, "lastResult": None}


async def _run_materialize() -> dict:
    async with _materialize_lock:
        res = await asyncio.to_thread(mat.materialize)
    _state["lastMaterialize"] = datetime.now(timezone.utc).isoformat()
    _state["lastResult"] = res
    return res


async def _materialize_loop() -> None:
    while True:
        try:
            res = await _run_materialize()
            log.info("materialize: %s", res["summary"])
        except Exception:  # noqa: BLE001
            log.exception("materialize loop error")
        await asyncio.sleep(settings.materialize_interval_seconds)


@asynccontextmanager
async def lifespan(app: FastAPI):
    task = asyncio.create_task(_materialize_loop())
    try:
        yield
    finally:
        task.cancel()


app = FastAPI(title="SkyPulse Analytics", version="0.1.0", lifespan=lifespan)


@app.get("/analytics/health")
def health():
    return {"success": True, "data": {
        "status": "UP", "service": "skypulse-analytics",
        "lastMaterialize": _state["lastMaterialize"],
    }}


@app.get("/analytics/coverage")
def coverage():
    """Per-table row counts / time span / density — the data-potential map for ML."""
    data = mat.coverage()
    data["lastMaterialize"] = _state["lastMaterialize"]
    return {"success": True, "data": data}


@app.post("/analytics/materialize")
async def materialize_now():
    return {"success": True, "data": await _run_materialize()}


@app.get("/analytics/rainfall/effective")
def rainfall_effective(
    station: str = Query(..., description="station_code"),
    asOf: Optional[date] = Query(None, description="Asia/Taipei date; defaults to latest data"),
    r70: Optional[float] = Query(None, description="optional R70 baseline (mm) to also return a signal"),
):
    """SWCB official effective rainfall (alpha=0.7 daily) over the migrated history."""
    con = duck.attach_pg(duck.new_connection())
    try:
        rows = con.execute(
            "SELECT (time + INTERVAL '8 hours')::date AS d, sum(rain_10min_mm) AS rain "
            "FROM pg.rainfall_observations "
            "WHERE station_code = ? AND time >= (now() - INTERVAL '9 days') "
            "GROUP BY 1 ORDER BY 1",
            [station],
        ).fetchall()
    finally:
        con.close()
    if not rows:
        raise HTTPException(404, f"no recent rainfall data for station {station}")

    daily = {r[0]: float(r[1] or 0) for r in rows}
    as_of = asOf or max(daily.keys())
    rt = effective_rainfall(daily, as_of)
    breakdown = [
        {"date": str(d), "rainMm": daily[d], "offset": (as_of - d).days}
        for d in sorted(daily) if 0 <= (as_of - d).days <= 6
    ]
    data = {
        "station": station, "asOf": str(as_of),
        "effectiveRainfallMm": float(rt), "alpha": 0.7, "windowDays": 7,
        "days": breakdown,
    }
    if r70 is not None:
        from decimal import Decimal
        data["r70"] = r70
        data["signal"] = signal(rt, Decimal(str(r70)))
    return {"success": True, "data": data}


@app.get("/analytics/timeseries/{table}")
def timeseries(
    table: str,
    station: Optional[str] = None,
    limit: int = Query(2000, ge=1, le=100000),
):
    """Recent rows for a hypertable, read from the Parquet lake (fast columnar)."""
    if table not in mat.TIMESERIES:
        raise HTTPException(404, f"unknown time-series table '{table}'")
    path = f"{settings.lake_dir}/{table}/**/*.parquet"
    con = duck.new_connection()
    try:
        where, params = "", []
        if station:
            where = "WHERE station_code = ?"
            params.append(station)
        res = con.execute(
            f"SELECT * EXCLUDE (_yr, _mo) FROM read_parquet('{path}', hive_partitioning=true) "
            f"{where} ORDER BY time DESC LIMIT ?",
            params + [limit],
        )
        cols = [c[0] for c in res.description]
        data = [dict(zip(cols, row)) for row in res.fetchall()]
    except Exception as e:  # noqa: BLE001
        raise HTTPException(503, f"lake not ready for '{table}' (materialize first): {e}")
    finally:
        con.close()
    return {"success": True, "data": {"table": table, "count": len(data), "rows": data}}
