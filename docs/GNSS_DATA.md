# GNSS Production Solutions — `pos_collect_realtime`

The production GNSS displacement-solving system exports its real-time position
solutions. A dump is provided at `schma/pos_collect_realtime.sql` (a phpMyAdmin /
MariaDB dump of the `gnss_process` database).

**The dump is ~4.6 GB and is git-ignored** (`schma/*.sql`). Bulk data belongs in a
database, not version control — GitHub also rejects files over 100 MB. This file
tracks the *schema* so the structure is versioned even though the data is not.

## Why this matters

This is the GNSS displacement data SkyPulse previously could not obtain directly (the
acquisition API is still under construction). It closes the loop on the Phase-2
space-weather study: the real solution-quality fields (`ratio`, `is_valid`,
`sat_count`, `sigma_*`) can be correlated against the space-weather quality flags to
test whether geomagnetic storms *actually* degrade production GNSS solutions — moving
from "we classify space weather" to "we measured its effect on real GNSS."

## Table `pos_collect_realtime`

| column | type | meaning |
|---|---|---|
| `area` | varchar(64) | site / area, e.g. `Alishan` |
| `station_id` | varchar(64) | GNSS station, e.g. `GNSS1` |
| `result_time` | datetime | solution epoch (~10-minute cadence) |
| `time_string` | varchar(16) | processing window label, e.g. `3hour` |
| `ecef_x`, `ecef_y`, `ecef_z` | decimal(12,4) | **ECEF position in metres — the displacement signal** |
| `sigma_x`, `sigma_y`, `sigma_z` | decimal(8,4) | position 1σ uncertainty (m), often NULL |
| `fix_quality` | tinyint | solution fix type |
| `sat_count` | tinyint unsigned | satellites used |
| `n_points` | smallint unsigned | points in the processing window |
| `is_valid` | tinyint | solution validity flag |
| `ratio` | decimal(7,2) | ambiguity ratio (RTK quality discriminator) |
| `created_at`, `updated_at` | timestamp | ingest bookkeeping |

The displacement is the change in `ecef_x/y/z` over time (sub-mm to mm), tracked per
`(area, station_id)`.

## Loading into SkyPulse (planned — not yet done)

The source is MariaDB; SkyPulse is PostgreSQL + TimescaleDB. Intended path:

1. Translate the dump (MySQL → Postgres) or stream its `INSERT`s into a TimescaleDB
   hypertable, e.g. `gnss_positions(result_time, area, station_id, ecef_x/y/z, …)`
   segmented by `(area, station_id)`, chunked by time.
2. Load into the Pi's `skypulse` database out-of-band (**do not** push 4.6 GB through
   Flyway — Flyway is for schema, not bulk data).
3. The read-only analytics service can then join GNSS solution quality against
   `kp_index_records` / `dst_index_records` / `solar_wind_records` for the validation.

Natural next study: **does space weather measurably degrade real GNSS solutions?**
