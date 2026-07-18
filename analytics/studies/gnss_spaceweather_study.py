"""GNSS x space-weather correlation study (Phase 2 — closing the loop).

Hypothesis: geomagnetic storms degrade production GNSS solutions.

Two GNSS degradation metrics, computed INDEPENDENTLY of the space-weather classifier:
  - jitter_m    : median epoch-to-epoch 3D displacement of ECEF (3hour solutions),
                  robust to metre-level station resets (drop steps > 0.5 m).
  - invalid_rate: fraction of solutions flagged is_valid = 0 (failed fix).
Independent oracle = geomagnetic activity from Kp/Dst (daily max Kp, min Dst), plus the two
named 2024 superstorms as anchor events. Dates bucketed in UTC (Kp/Dst are UT).

IMPORTANT — confound control: raw jitter falls over the years as the network matured, while
Kp rises over the years with the solar cycle. That shared time trend makes a naive
jitter-vs-Kp correlation SPURIOUS. We therefore also report the **detrended** correlation
(jitter minus its 31-day rolling median), which isolates the storm-timescale response.

Run: docker exec -i skypulse-analytics python < analytics/studies/gnss_spaceweather_study.py
"""
import datetime as dt
import math
import duck

con = duck.attach_pg(duck.new_connection())
print("computing daily GNSS metrics over the 3hour solutions ...", flush=True)

con.execute(
    """
    CREATE TEMP TABLE gnss_daily AS
    WITH j AS (
      SELECT (result_time)::date AS d,
        sqrt( power(ecef_x - lag(ecef_x) OVER w, 2)
            + power(ecef_y - lag(ecef_y) OVER w, 2)
            + power(ecef_z - lag(ecef_z) OVER w, 2) ) AS jit,
        is_valid
      FROM pg.gnss_positions
      WHERE time_string = '3hour'
      WINDOW w AS (PARTITION BY area, station_id ORDER BY result_time)
    )
    SELECT d,
      median(jit) FILTER (WHERE jit IS NOT NULL AND jit < 0.5) AS jitter_m,
      avg(CASE WHEN is_valid = 0 THEN 1.0 ELSE 0.0 END)        AS invalid_rate,
      count(*)                                                  AS n
    FROM j GROUP BY d
    """
)
rows = con.execute(
    """
    WITH sw AS (SELECT (time)::date d, max(kp_value) kp FROM pg.kp_index_records GROUP BY 1),
         ds AS (SELECT (time)::date d, min(dst_value) dst FROM pg.dst_index_records GROUP BY 1)
    SELECT g.d, g.jitter_m, g.invalid_rate, g.n, sw.kp, ds.dst
    FROM gnss_daily g JOIN sw USING(d) JOIN ds USING(d)
    WHERE g.jitter_m IS NOT NULL AND g.n > 100
    ORDER BY g.d
    """
).fetchall()

d   = [r[0] for r in rows]
jit = [float(r[1]) for r in rows]
inv = [float(r[2]) for r in rows]
kp  = [float(r[4]) for r in rows]
dst = [float(r[5]) for r in rows]
ndst = [-x for x in dst]
N = len(rows)


def pearson(xs, ys):
    m = len(xs); mx = sum(xs)/m; my = sum(ys)/m
    cov = sum((x-mx)*(y-my) for x, y in zip(xs, ys))
    sx = math.sqrt(sum((x-mx)**2 for x in xs)); sy = math.sqrt(sum((y-my)**2 for y in ys))
    return cov/(sx*sy) if sx and sy else 0.0

def spearman(xs, ys):
    def ranks(v):
        o = sorted(range(len(v)), key=lambda i: v[i]); rk = [0]*len(v)
        for p, i in enumerate(o): rk[i] = p
        return rk
    return pearson(ranks(xs), ranks(ys))

def med(v):
    s = sorted(v); return s[len(s)//2] if s else float("nan")

# 31-day rolling median detrend of jitter (isolate storm-timescale response)
def rolling_med(v, half=15):
    out = []
    for i in range(len(v)):
        lo, hi = max(0, i-half), min(len(v), i+half+1)
        out.append(med(v[lo:hi]))
    return out
jit_base = rolling_med(jit)
jit_anom = [jit[i]-jit_base[i] for i in range(N)]

print(f"\n=== overlap: {N} days, {d[0]} .. {d[-1]} ===")
print(f"jitter median {med(jit)*1000:.2f} mm   invalid_rate mean {sum(inv)/N*100:.3f}%")

print("\n=== yearly trend (confirms the confound) ===")
for y in range(2020, 2027):
    ys = [jit[i]*1000 for i in range(N) if d[i].year == y]
    ks = [kp[i] for i in range(N) if d[i].year == y]
    if ys:
        print(f"  {y}: jitter median {med(ys):.2f} mm   Kp mean {sum(ks)/len(ks):.2f}   ({len(ys)}d)")

print("\n=== correlation vs Kp / -Dst  (RAW is confounded; DETRENDED is the honest one) ===")
print(f"jitter  RAW        vs Kp: Spearman {spearman(jit,kp):+.3f}   vs -Dst: {spearman(jit,ndst):+.3f}")
print(f"jitter  DETRENDED  vs Kp: Spearman {spearman(jit_anom,kp):+.3f}   vs -Dst: {spearman(jit_anom,ndst):+.3f}")
print(f"invalid            vs Kp: Spearman {spearman(inv,kp):+.3f}   vs -Dst: {spearman(inv,ndst):+.3f}")

print("\n=== quiet (Kp<3) vs storm (Kp>=5) ===")
q = [i for i in range(N) if kp[i] < 3]; s = [i for i in range(N) if kp[i] >= 5]
print(f"jitter DETRENDED anomaly: quiet {med([jit_anom[i] for i in q])*1000:+.3f} mm  ->  storm {med([jit_anom[i] for i in s])*1000:+.3f} mm")
print(f"invalid_rate mean       : quiet {sum(inv[i] for i in q)/len(q)*100:.3f}%  ->  storm {sum(inv[i] for i in s)/len(s)*100:.3f}%   x{(sum(inv[i] for i in s)/len(s))/(sum(inv[i] for i in q)/len(q) or 1e-9):.1f}")
print(f"days with ANY failure   : quiet {sum(1 for i in q if inv[i]>0)/len(q)*100:.1f}%  ->  storm {sum(1 for i in s if inv[i]>0)/len(s)*100:.1f}%")

print("\n=== 2024 superstorm event study (detrended jitter anomaly + invalid) ===")
for name, a, b in [("May-2024 G5", "2024-05-08", "2024-05-14"), ("Oct-2024 G4", "2024-10-08", "2024-10-13")]:
    idx = [i for i in range(N) if dt.date.fromisoformat(a) <= d[i] <= dt.date.fromisoformat(b)]
    if idx:
        print(f"  {name}: peak Kp {max(kp[i] for i in idx):.1f}  min Dst {min(dst[i] for i in idx):.0f}"
              f"  jitter anom {med([jit_anom[i] for i in idx])*1000:+.2f} mm  invalid {sum(inv[i] for i in idx)/len(idx)*100:.2f}%")
