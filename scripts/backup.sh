#!/usr/bin/env bash
# Backs up the Amadya database (pg_dump custom format, already compressed) and keeps the newest $KEEP backups.
# Run by scripts/deploy.sh before every deploy and nightly from root's crontab (docs/deploy.md).
#
#   scripts/backup.sh nightly
#   scripts/backup.sh pre-deploy-3598903
#
# Restore: see docs/deploy.md ("Restore a backup").
# AMADYA_ROOT is the checkout whose stack is backed up; default: this repo.
# Overridable for testing: BACKUP_DIR, KEEP, COMPOSE_FILES.
set -euo pipefail
cd "${AMADYA_ROOT:-$(dirname "$0")/..}"

reason=${1:?usage: scripts/backup.sh <reason>}
[[ $reason =~ ^[a-z0-9-]+$ ]] || { echo "backup: reason must be lowercase letters, digits and dashes" >&2; exit 2; }

BACKUP_DIR=${BACKUP_DIR:-/root/backups/amadya}
KEEP=${KEEP:-14}
read -ra compose_files <<< "${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
compose() { docker compose "${compose_files[@]}" "$@"; }

umask 077
mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/amadya-$(date -u +%Y%m%dT%H%M%SZ)-$reason.dump"
trap 'rm -f "$file.tmp"' EXIT

# Written to a temporary name first: a dump that fails half-way never looks like a backup.
compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$file.tmp"
if [[ ! -s $file.tmp ]]; then
  echo "backup: pg_dump produced an empty file" >&2
  exit 1
fi
mv "$file.tmp" "$file"
echo "backup: $file ($(du -h "$file" | cut -f1))"

# Retention: nightly and pre-deploy backups together.
ls -1t "$BACKUP_DIR"/amadya-*.dump | tail -n +$((KEEP + 1)) | while read -r old; do
  rm -f -- "$old"
  echo "backup: removed $old"
done
