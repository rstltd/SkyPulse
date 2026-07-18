"""Space-weather / GNSS validation study (Phase 2) — storm catalog, GNSS-quality budget,
and an unsupervised solar-wind anomaly detector backtested against geomagnetic storms.

This is a LAB deliverable: every headline number is validated against an INDEPENDENT oracle,
not re-derived from the code under test.

  - Ground truth = geomagnetic storm episodes from Dst on the standard scale (intense storm =
    Dst <= -50 nT), NOT our own classifier thresholds. Episodes are gap-sessionised (>24h apart).
  - Detector = unsupervised anomaly on the solar-wind DRIVERS: coupling proxy V*Bs
    (Bs = -min(Bz,0), the southward IMF). Flagged above its OWN 99th percentile — no storm
    labels are used to fit the threshold. Tests: do solar-wind anomalies coincide with / precede
    the storms that degrade GNSS?

Run inside the analytics container (has DuckDB + the read-only Postgres attach):
  docker cp analytics/studies/spaceweather_study.py skypulse-analytics:/app/  &&  \
  docker exec skypulse-analytics python /app/spaceweather_study.py
"""
import datetime as dt
import json
import statistics
from bisect import bisect_left, bisect_right

import duck

WINDOW_H = 6          # coincidence window around onset (hours)
FLAG_PCTL = 0.99      # detector threshold percentile (unsupervised)

con = duck.attach_pg(duck.new_connection())

# ---------------------------------------------------------------- 1. GNSS quality budget
rows = con.execute(
    """
    SELECT level, count(*) n FROM (
      SELECT CASE
        WHEN k.kp_value > 7  OR d.dst_value <  -100 THEN 'SEVERE'
        WHEN k.kp_value >= 5 OR d.dst_value <=  -50 THEN 'DEGRADED'
        WHEN k.kp_value >= 4 OR d.dst_value <=  -30 THEN 'CAUTION'
        ELSE 'NORMAL' END AS level
      FROM pg.kp_index_records k
      ASOF LEFT JOIN pg.dst_index_records d ON d.time <= k.time
    ) GROUP BY 1
    """
).fetchall()
qtot = sum(r[1] for r in rows)
quality = {lvl: {"n": n, "pct": round(100 * n / qtot, 2)} for lvl, n in rows}

# ---------------------------------------------------------------- 2. storm episode catalog
episodes = con.execute(
    """
    WITH s AS (
      SELECT time, dst_value,
        CASE WHEN lag(time) OVER (ORDER BY time) IS NULL
               OR time - lag(time) OVER (ORDER BY time) > INTERVAL '24 hours'
             THEN 1 ELSE 0 END AS newep
      FROM pg.dst_index_records WHERE dst_value <= -50),
    g AS (SELECT *, sum(newep) OVER (ORDER BY time) AS ep FROM s)
    SELECT min(time) AS onset, max(time) AS endt, min(dst_value) AS min_dst, count(*) AS hrs
    FROM g GROUP BY ep ORDER BY onset
    """
).fetchall()
n_ep = len(episodes)
onsets = [e[0] for e in episodes]

# ---------------------------------------------------------------- 3. unsupervised detector
thr = con.execute(
    "SELECT quantile_cont(wind_speed * greatest(-bz, 0), ?) "
    "FROM pg.solar_wind_records WHERE wind_speed IS NOT NULL AND bz IS NOT NULL",
    [FLAG_PCTL],
).fetchone()[0]
flag_times = [
    r[0] for r in con.execute(
        f"SELECT time FROM pg.solar_wind_records "
        f"WHERE wind_speed IS NOT NULL AND bz IS NOT NULL "
        f"AND wind_speed * greatest(-bz,0) >= {thr} ORDER BY time"
    ).fetchall()
]
sw_min, sw_max, sw_n = con.execute(
    "SELECT min(time), max(time), count(*) FROM pg.solar_wind_records "
    "WHERE wind_speed IS NOT NULL AND bz IS NOT NULL"
).fetchone()
flag_rate = len(flag_times) / sw_n

# ---------------------------------------------------------------- 4. backtest vs storms
w = dt.timedelta(hours=WINDOW_H)
hits, leads = 0, []
for onset, endt, min_dst, hrs in episodes:
    i = bisect_left(flag_times, onset - w)
    j = bisect_right(flag_times, onset + w)
    if j > i:
        hits += 1
        # lead = onset - earliest flag in window (positive => flag precedes onset)
        leads.append(round((onset - flag_times[i]).total_seconds() / 3600.0, 2))
recall = hits / n_ep if n_ep else 0.0
med_lead = statistics.median(leads) if leads else None

# precision: fraction of flags within [onset-W, endt+W] of ANY episode
ep_intervals = [(e[0] - w, e[1] + w) for e in episodes]
ep_starts = [iv[0] for iv in ep_intervals]
true_alarms = 0
for ft in flag_times:
    idx = bisect_right(ep_starts, ft) - 1
    if idx >= 0 and ep_intervals[idx][0] <= ft <= ep_intervals[idx][1]:
        true_alarms += 1
precision = true_alarms / len(flag_times) if flag_times else 0.0

# big-storm check: did the detector flag near the two named 2024 superstorms?
def caught(day):
    t0 = dt.datetime.fromisoformat(day + "T00:00:00+00:00")
    return any(t0 - dt.timedelta(days=1) <= ft <= t0 + dt.timedelta(days=1) for ft in flag_times)

out = {
    "gnss_quality_budget": quality,
    "quality_total_readings": qtot,
    "storms": {
        "n_episodes": n_ep,
        "strongest": [
            {"onset": str(e[0]), "min_dst": e[2], "duration_h": e[3]}
            for e in sorted(episodes, key=lambda e: e[2])[:5]
        ],
    },
    "detector": {
        "threshold_VBs": round(thr, 1),
        "flag_pctl": FLAG_PCTL,
        "n_flags": len(flag_times),
        "flag_rate_pct": round(100 * flag_rate, 2),
        "sw_span": f"{sw_min.date()}..{sw_max.date()}",
    },
    "backtest": {
        "window_hours": WINDOW_H,
        "recall": round(recall, 3),
        "precision": round(precision, 3),
        "hits": hits,
        "median_lead_h": med_lead,
        "caught_may2024_G5": caught("2024-05-11"),
        "caught_oct2024_G4": caught("2024-10-11"),
    },
}
print(json.dumps(out, indent=2, default=str))
