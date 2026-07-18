#!/usr/bin/env bash
#
# One-click deploy of SkyPulse to the Raspberry Pi.
#
# What it does (idempotent — safe to re-run; the pgdata volume is preserved):
#   1. Build the fat jar locally (Vue frontend is bundled in by the Maven frontend profile).
#   2. Ship the deploy bundle (jar + Dockerfile + docker/ compose+.env+init-db) over SSH.
#   3. (Re)build the arm64 app image and (re)start the stack with docker compose on the Pi.
#   4. Wait for the health endpoint and report.
#
# The jar is architecture-independent, so it is built on the dev machine (needs JDK 21) and the
# arm64 runtime image is built on the Pi — no JDK/Node/Maven required on the Pi itself.
#
# Prerequisites
#   dev machine : JDK 21, the ./mvnw wrapper, ssh + scp.  On Windows, run this from Git Bash.
#   Pi          : docker + docker compose, and the login user in the `docker` group.
#   secrets     : docker/.env must exist (gitignored) with DB_PASSWORD, CWA_API_KEY,
#                 SKYPULSE_API_KEY, SKYPULSE_ADMIN_KEY, SKYPULSE_LOGIN_PASSWORD (see .env.example).
#
# Usage
#   scripts/deploy-pi.sh                         # build + deploy
#   SKIP_BUILD=1 scripts/deploy-pi.sh            # reuse the existing target/*.jar
#   PI_HOST=rst@192.168.50.196 scripts/deploy-pi.sh   # override SSH target (default: skypulse-pi)
#
# Config (override via environment)
#   PI_HOST      SSH target: an ~/.ssh/config alias or user@host   (default: skypulse-pi)
#   DEPLOY_DIR   remote dir, relative to the login user's home      (default: skypulse-deploy)
#   SKIP_BUILD   set to 1/true to skip the Maven build              (default: unset)
#
set -euo pipefail

PI_HOST="${PI_HOST:-skypulse-pi}"
DEPLOY_DIR="${DEPLOY_DIR:-skypulse-deploy}"
SKIP_BUILD="${SKIP_BUILD:-0}"
HEALTH_PATH="/skypulse/api/v1/health"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"

SSH="ssh -o BatchMode=yes"
SCP="scp -o BatchMode=yes"
COMPOSE="-f docker-compose.prod.yml -f docker-compose.pi.yml"

log(){ printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die(){ printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# ---- 0. preflight -----------------------------------------------------------
log "Deploy target: $PI_HOST:$DEPLOY_DIR   (SKIP_BUILD=$SKIP_BUILD)"
[ -f docker/.env ] || die "docker/.env is missing — copy docker/.env.example and fill in the secrets."
$SSH "$PI_HOST" true 2>/dev/null || die "cannot SSH to '$PI_HOST' — set PI_HOST=user@host or add an ~/.ssh/config alias."

# ---- 1. build ---------------------------------------------------------------
case "$SKIP_BUILD" in
  1|true|yes|TRUE|YES) log "Skipping build (SKIP_BUILD=$SKIP_BUILD)";;
  *) log "Building jar (frontend bundled): ./mvnw clean package -DskipTests"
     if [ -x ./mvnw ]; then ./mvnw clean package -DskipTests; else mvn clean package -DskipTests; fi;;
esac

JAR="$(ls -1 target/skypulse-*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
[ -n "$JAR" ] || die "no target/skypulse-*.jar found (build failed, or SKIP_BUILD set with no prior build)."
log "Using jar: $JAR"

# ---- 2. ship bundle ---------------------------------------------------------
log "Shipping bundle to $PI_HOST:$DEPLOY_DIR"
$SSH "$PI_HOST" "mkdir -p '$DEPLOY_DIR/target' '$DEPLOY_DIR/docker' && rm -f '$DEPLOY_DIR'/target/*.jar"
$SCP "$JAR"                                                            "$PI_HOST:$DEPLOY_DIR/target/"
$SCP Dockerfile                                                        "$PI_HOST:$DEPLOY_DIR/Dockerfile"
$SCP docker/docker-compose.prod.yml docker/docker-compose.pi.yml docker/.env "$PI_HOST:$DEPLOY_DIR/docker/"
$SCP -r docker/init-db                                                 "$PI_HOST:$DEPLOY_DIR/docker/"

# Windows checkouts may carry CRLF; strip it so docker/compose/.env parse cleanly.
$SSH "$PI_HOST" "cd '$DEPLOY_DIR' && sed -i 's/\r\$//' Dockerfile docker/docker-compose.prod.yml docker/docker-compose.pi.yml docker/.env docker/init-db/*.sql"

# ---- 3. deploy --------------------------------------------------------------
log "Building image + starting stack on the Pi (pgdata preserved)"
$SSH "$PI_HOST" "cd '$DEPLOY_DIR/docker' && docker compose $COMPOSE --env-file .env up -d --build"

# ---- 4. health check --------------------------------------------------------
log "Waiting for health at $HEALTH_PATH"
ok=0
for i in $(seq 1 40); do
  code="$($SSH "$PI_HOST" "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080$HEALTH_PATH" 2>/dev/null || echo 000)"
  printf '  [%02d] http=%s\n' "$i" "$code"
  if [ "$code" = "200" ]; then ok=1; break; fi
  sleep 3
done

if [ "$ok" != "1" ]; then
  log "Health check did NOT pass — recent app logs:"
  $SSH "$PI_HOST" "docker logs --tail 40 skypulse-app" 2>&1 || true
  die "deploy finished but the app is not healthy (see logs above)."
fi

IP="$($SSH "$PI_HOST" "hostname -I | awk '{print \$1}'" 2>/dev/null || echo '<pi-ip>')"
log "Deployed OK ✅   http://$IP:8080/skypulse/"
$SSH "$PI_HOST" "cd '$DEPLOY_DIR/docker' && docker compose $COMPOSE ps"
