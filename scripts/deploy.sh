#!/usr/bin/env bash
# Deploys one release on the server: backup, switch AMADYA_TAG, pull, restart, wait until the api is ready.
# If the api is not ready in time, the previous release is started again and the deploy fails (ADR 0012).
#
#   scripts/deploy.sh 3598903        # tag = first 7 characters of the commit sha
#
# Normally run by /usr/local/sbin/amadya-deploy (the Release workflow over SSH), never edited on the server.
# AMADYA_ROOT is the checkout to deploy (the gate runs a copy of this script from outside it); default: this repo.
# Overridable for testing: ENV_FILE, COMPOSE_FILES, STATE_DIR, LOCK_FILE, READY_TIMEOUT, plus backup.sh's BACKUP_DIR.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
export AMADYA_ROOT=${AMADYA_ROOT:-$(dirname "$here")}
cd "$AMADYA_ROOT"

tag=${1:?usage: scripts/deploy.sh <7-character tag>}
[[ $tag =~ ^[0-9a-f]{7}$ ]] || { echo "deploy: tag must be 7 hex characters, got '$tag'" >&2; exit 2; }

ENV_FILE=${ENV_FILE:-.env}
STATE_DIR=${STATE_DIR:-.deploy}
# Must be on a local filesystem: flock does not hold across some network or VM file shares.
LOCK_FILE=${LOCK_FILE:-/run/amadya-deploy.lock}
READY_TIMEOUT=${READY_TIMEOUT:-180}
read -ra compose_files <<< "${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
compose() { docker compose --env-file "$ENV_FILE" "${compose_files[@]}" "$@"; }
log() { echo "[deploy $(date -u +%H:%M:%S)] $*"; }

# One deploy at a time: a second one waits here (the lock is released when this process exits, however it exits).
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "another deploy is running; waiting for it to finish"
  flock 9
fi

[[ -f $ENV_FILE ]] || { echo "deploy: $ENV_FILE not found" >&2; exit 1; }
current_tag() { sed -n 's/^AMADYA_TAG=//p' "$ENV_FILE" | tail -1; }
set_tag() {
  if grep -q '^AMADYA_TAG=' "$ENV_FILE"; then
    sed -i.bak "s/^AMADYA_TAG=.*/AMADYA_TAG=$1/" "$ENV_FILE" && rm -f "$ENV_FILE.bak"
  else
    printf '\nAMADYA_TAG=%s\n' "$1" >> "$ENV_FILE"
  fi
}
# The Caddyfile is a single-file bind mount: a checkout replaces the file, the running container keeps the old one.
# Restart Caddy (about a second) only when the routes changed, e.g. a new app.
reload_caddy() {
  if ! compose exec -T caddy cat /etc/caddy/Caddyfile 2>/dev/null | cmp -s - infra/caddy/Caddyfile; then
    log "Caddyfile changed; restarting caddy"
    compose restart caddy
  fi
}
ready() { compose exec -T caddy wget -qO- http://api:8080/actuator/health/readiness 2>/dev/null | grep -q '"UP"'; }
wait_ready() {
  local deadline=$((SECONDS + READY_TIMEOUT))
  until ready; do
    ((SECONDS < deadline)) || return 1
    sleep 5
  done
}

previous=$(current_tag)
previous=${previous:-latest}
log "deploying $tag (running: $previous)"

# 1. Backup first: nothing changes if it fails.
backup_line=$("$here/backup.sh" "pre-deploy-$tag")
echo "$backup_line"
backup_file=$(sed -n 's/^backup: \([^ ]*\) .*/\1/p' <<< "$backup_line" | head -1)

# 2. Pull before touching the running stack: a missing image fails here, with production unchanged.
if ! AMADYA_TAG=$tag compose pull --quiet; then
  log "FAILED: could not pull the images for $tag; production still runs $previous"
  exit 1
fi

# 3. Switch and restart.
set_tag "$tag"
compose up -d --no-build --quiet-pull
reload_caddy

# 4. Ready, or put the previous release back.
if wait_ready; then
  mkdir -p "$STATE_DIR"
  [[ -f $STATE_DIR/current && $(cat "$STATE_DIR/current") != "$tag" ]] && cp "$STATE_DIR/current" "$STATE_DIR/previous"
  echo "$tag" > "$STATE_DIR/current"
  docker image prune -af --filter "label=org.opencontainers.image.source=https://github.com/balmus-apps/amadya" > /dev/null
  log "OK: $tag is live"
else
  log "FAILED: the api was not ready within ${READY_TIMEOUT}s; starting $previous again"
  compose logs --tail 40 api || true
  set_tag "$previous"
  compose up -d --no-build --quiet-pull
  if wait_ready; then log "$previous is running again"; else log "WARNING: $previous is not ready either; check the server"; fi
  log "the database may already be migrated by $tag. Backup taken before this deploy: $backup_file"
  log "restore it only if $previous misbehaves; see docs/deploy.md, 'Restore a backup'"
  exit 1
fi
