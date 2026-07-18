"""Stream a phpMyAdmin/MariaDB dump of `pos_collect_realtime` into Postgres-loadable SQL.

Keeps only the multi-row INSERT statements, strips MySQL backticks, retargets the table to
`gnss_positions`, and pins the session to UTC. Everything else (comments, SET, CREATE TABLE,
ENGINE=…) is dropped — the Postgres hypertable is created separately from gnss_load_schema.sql.
Pure line streaming, O(1) memory — handles the multi-GB dump.

Usage (target the SkyPulse DB, out-of-band; do NOT run through Flyway):
  # 1. create the hypertable
  docker exec -i skypulse-db psql -U skypulse -d skypulse < analytics/studies/gnss_load_schema.sql
  # 2. stream-convert the dump into it
  python analytics/studies/gnss_load_convert.py < schma/pos_collect_realtime.sql \
    | docker exec -i skypulse-db psql -U skypulse -d skypulse -v ON_ERROR_STOP=1 -q
"""
import sys

w = sys.stdout.write
w("SET timezone='UTC';\n")
for line in sys.stdin:
    if line.startswith("INSERT INTO"):
        w(line.replace("`pos_collect_realtime`", "gnss_positions").replace("`", ""))
    elif line.startswith("("):          # phpMyAdmin value-tuple continuation rows
        w(line)
