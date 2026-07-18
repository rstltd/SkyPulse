"""Derive GNSS station lat/lon (ECEF -> WGS84) from gnss_positions and emit the JSON array
bundled into the frontend as static reference data. Uses the median ECEF per (area, station)
— robust to metre-level resets — then Bowring's closed-form ECEF->geodetic.

  docker exec -i skypulse-analytics python < analytics/studies/gnss_stations_latlon.py \
    > frontend/src/assets/gnss-stations.json
"""
import json
import math

import duck

con = duck.attach_pg(duck.new_connection())
rows = con.execute(
    "SELECT area, station_id, median(ecef_x) x, median(ecef_y) y, median(ecef_z) z "
    "FROM pg.gnss_positions WHERE ecef_x IS NOT NULL "
    "GROUP BY area, station_id ORDER BY area, station_id"
).fetchall()

a = 6378137.0
f = 1 / 298.257223563
b = a * (1 - f)
e2 = f * (2 - f)
ep2 = (a * a - b * b) / (b * b)

out = []
for area, st, x, y, z in rows:
    x, y, z = float(x), float(y), float(z)
    p = math.hypot(x, y)
    th = math.atan2(z * a, p * b)
    lon = math.atan2(y, x)
    lat = math.atan2(z + ep2 * b * math.sin(th) ** 3, p - e2 * a * math.cos(th) ** 3)
    out.append({"area": area, "station": st, "lat": round(math.degrees(lat), 6), "lon": round(math.degrees(lon), 6)})

print(json.dumps(out, ensure_ascii=False))
