# Deployment (Raspberry Pi)

SkyPulse runs on a Raspberry Pi 5 as two containers managed by Docker Compose:

- `skypulse-db` — TimescaleDB (PostgreSQL 16), data on the NVMe SSD, **not exposed** (only reachable from the app over the Docker network).
- `skypulse-app` — the Spring Boot fat jar (Vue frontend bundled in), published on `:8080`.

Both use `restart: unless-stopped`, so the stack comes back automatically after a reboot.
The app is served under the `/skypulse` context path: **`http://<pi-ip>:8080/skypulse/`**.

## One-click deploy

```bash
scripts/deploy-pi.sh                       # build + ship + (re)start + health check
SKIP_BUILD=1 scripts/deploy-pi.sh          # reuse the existing target/*.jar
PI_HOST=rst@192.168.50.196 scripts/deploy-pi.sh   # override the SSH target
```

On Windows, run it from **Git Bash** (it uses `ssh`/`scp`/`./mvnw`). Re-running is safe — the
`pgdata` volume is preserved; only the app image/container is rebuilt from the new jar.

### What the script does
1. `./mvnw clean package -DskipTests` — the `frontend` Maven profile installs Node, runs
   `npm ci` + `npm run build`, and Vite writes the bundle into `src/main/resources/static`, so it
   ends up **inside the jar**. The jar is architecture-independent.
2. Ships the bundle over SSH to `~/skypulse-deploy/` on the Pi: the jar, `Dockerfile`, and
   `docker/` (`docker-compose.prod.yml`, `docker-compose.pi.yml`, `.env`, `init-db/`).
3. On the Pi: `docker compose -f docker-compose.prod.yml -f docker-compose.pi.yml up -d --build`
   builds the **arm64** runtime image (`Dockerfile` just copies the jar into `eclipse-temurin:21-jre-alpine`),
   pulls TimescaleDB, and starts both containers. Flyway migrates the schema on startup.

### Prerequisites
- **Dev machine:** JDK 21, the `./mvnw` wrapper, `ssh`/`scp`. An `~/.ssh/config` alias `skypulse-pi`
  (or pass `PI_HOST=user@host`).
- **Pi:** Docker + Docker Compose, login user in the `docker` group.
- **Secrets:** `docker/.env` (gitignored) with `DB_PASSWORD`, `CWA_API_KEY`, `SKYPULSE_API_KEY`,
  `SKYPULSE_ADMIN_KEY`, `SKYPULSE_LOGIN_PASSWORD` (+ optional `MOENV_API_KEY`). See `docker/.env.example`.

## Pi-specific overlay — `docker/docker-compose.pi.yml`

Layered on top of `docker-compose.prod.yml` for this Pi:

- **`JAVA_OPTS=-Xmx768m -Xms256m`** — the Pi 5 firmware injects `cgroup_disable=memory` into the
  kernel cmdline (visible in `/proc/cmdline`, not in any editable file), so Docker `mem_limit` is
  **not enforced** and the JVM's default `MaxRAMPercentage` would size the heap off the full 8 GB.
  A fixed `-Xmx` bounds it instead. (The `Your kernel does not support memory limit capabilities`
  warning at `up` time is expected and harmless.)
- **`postgres.image: timescale/timescaledb:2.25.2-pg16`** — pinned (not `:latest-pg16`) for
  reproducibility and to match the version the migrated data was dumped from.
- `MOENV_API_KEY`, `SKYPULSE_CORS_ORIGINS` for the LAN.

## Data migration (dev → Pi)

To copy an existing SkyPulse database (e.g. a dev box with backfilled history) onto the Pi, use a
**same-version** TimescaleDB dump/restore — TimescaleDB does not guarantee cross-version logical
restore, so pin both sides to the same `timescaledb` version first.

```bash
# 1. dump on the source (custom format)
docker exec skypulse-db pg_dump -U skypulse -Fc -d skypulse -f /tmp/skypulse.dump
docker cp skypulse-db:/tmp/skypulse.dump ./skypulse.dump

# 2. ship to the Pi and into the DB container
scp skypulse.dump skypulse-pi:skypulse.dump
ssh skypulse-pi 'docker cp ~/skypulse.dump skypulse-db:/tmp/skypulse.dump'

# 3. restore on the Pi (drop/recreate the DB, then pre/post_restore)
ssh skypulse-pi 'docker exec skypulse-db psql -U skypulse -d postgres -c "DROP DATABASE skypulse; CREATE DATABASE skypulse;"'
ssh skypulse-pi 'docker exec skypulse-db psql -U skypulse -d skypulse -c "CREATE EXTENSION IF NOT EXISTS timescaledb; SELECT timescaledb_pre_restore();"'
ssh skypulse-pi 'docker exec skypulse-db pg_restore -U skypulse -d skypulse --no-owner --no-privileges /tmp/skypulse.dump'
ssh skypulse-pi 'docker exec skypulse-db psql -U skypulse -d skypulse -c "SELECT timescaledb_post_restore();"'
```

Verify with exact `COUNT(*)` per table (not `pg_stat` estimates, which are unreliable right after a
restore). The app's own collectors resume on their schedule and keep the data current afterwards.

## Common operations (on the Pi, in `~/skypulse-deploy/docker`)

```bash
CF="-f docker-compose.prod.yml -f docker-compose.pi.yml"
docker compose $CF ps                       # status
docker logs -f skypulse-app                 # app logs
docker compose $CF down                     # stop (keeps data)
docker compose $CF down -v                  # stop AND delete the pgdata volume (destroys data)
```
