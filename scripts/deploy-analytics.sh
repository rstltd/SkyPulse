#!/usr/bin/env bash
#
# Deploy the analytics sidecar (Python/FastAPI + DuckDB) to the Pi, alongside the Spring app.
# Ships analytics/ source + the compose overlay, then builds+starts ONLY the `analytics` service
# — `skypulse-app` and `skypulse-db` are left running and untouched (read-only companion).
#
# Prereqs: the Spring stack is already deployed (scripts/deploy-pi.sh) so docker/ + .env exist
# on the Pi; docker/docker-compose.prod.yml + docker-compose.pi.yml are present in the deploy dir.
#
# Usage:
#   scripts/deploy-analytics.sh
#   PI_HOST=rst@192.168.50.196 scripts/deploy-analytics.sh
#
set -euo pipefail

PI_HOST="${PI_HOST:-skypulse-pi}"
DEPLOY_DIR="${DEPLOY_DIR:-skypulse-deploy}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"

SSH="ssh -o BatchMode=yes"
SCP="scp -o BatchMode=yes"
CF="-f docker-compose.prod.yml -f docker-compose.pi.yml -f docker-compose.analytics.yml"

log(){ printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die(){ printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

$SSH "$PI_HOST" true 2>/dev/null || die "cannot SSH to '$PI_HOST' — set PI_HOST=user@host."
$SSH "$PI_HOST" "test -f '$DEPLOY_DIR/docker/docker-compose.prod.yml'" \
  || die "Spring stack not deployed yet — run scripts/deploy-pi.sh first."

log "Shipping analytics/ + overlay to $PI_HOST:$DEPLOY_DIR"
$SSH "$PI_HOST" "mkdir -p '$DEPLOY_DIR/analytics'"
$SCP -r analytics                         "$PI_HOST:$DEPLOY_DIR/"
$SCP docker/docker-compose.analytics.yml  "$PI_HOST:$DEPLOY_DIR/docker/"
# Windows checkouts may carry CRLF; strip it from shipped text files.
$SSH "$PI_HOST" "cd '$DEPLOY_DIR' && sed -i 's/\r\$//' analytics/*.py analytics/features/*.py analytics/requirements.txt analytics/Dockerfile docker/docker-compose.analytics.yml"

log "Building + starting ONLY the analytics service (Spring untouched)"
$SSH "$PI_HOST" "cd '$DEPLOY_DIR/docker' && docker compose $CF --env-file .env up -d --build analytics"

log "Waiting for analytics health"
ok=0
for i in $(seq 1 40); do
  code="$($SSH "$PI_HOST" "curl -s -o /dev/null -w '%{http_code}' http://localhost:8000/analytics/health" 2>/dev/null || echo 000)"
  printf '  [%02d] http=%s\n' "$i" "$code"
  [ "$code" = "200" ] && { ok=1; break; }
  sleep 3
done
[ "$ok" = "1" ] || { $SSH "$PI_HOST" "docker logs --tail 40 skypulse-analytics" 2>&1 || true; die "analytics not healthy."; }

IP="$($SSH "$PI_HOST" "hostname -I | awk '{print \$1}'" 2>/dev/null || echo '<pi-ip>')"
log "Analytics up ✅   http://$IP:8000/analytics/health   (coverage: /analytics/coverage)"
$SSH "$PI_HOST" "cd '$DEPLOY_DIR/docker' && docker compose $CF ps"
