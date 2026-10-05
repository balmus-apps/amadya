#!/usr/bin/env bash
# Builds the four Amadya images for the server (linux/amd64) and pushes them to
# GitHub Container Registry. The server only pulls; it never builds (1 vCPU, 2 GB).
#
#   scripts/release.sh            # build + push, tagged <git sha> and latest
#   PUSH=0 scripts/release.sh     # build only, to check that everything compiles
#
# One-time login on this machine (the gh token needs write:packages):
#   gh auth token | docker login ghcr.io -u <github-user> --password-stdin
set -euo pipefail
cd "$(dirname "$0")/.."

REGISTRY=${AMADYA_REGISTRY:-ghcr.io/balmus-apps}
PLATFORM=${PLATFORM:-linux/amd64}
TAG=$(git rev-parse --short HEAD)
PUSH=${PUSH:-1}

if [[ $PUSH == 1 && -n $(git status --porcelain) ]]; then
  echo "Working tree has uncommitted changes; commit first so the tag $TAG matches the image." >&2
  exit 1
fi

output=$([[ $PUSH == 1 ]] && echo "--push" || echo "--output=type=cacheonly")

build() { # name, context, dockerfile
  echo "==> $REGISTRY/amadya-$1:$TAG ($PLATFORM)"
  docker buildx build --platform "$PLATFORM" "$output" \
    --label org.opencontainers.image.source=https://github.com/balmus-apps/amadya \
    --label org.opencontainers.image.revision="$(git rev-parse HEAD)" \
    -t "$REGISTRY/amadya-$1:$TAG" -t "$REGISTRY/amadya-$1:latest" \
    -f "$3" "$2"
}

build api      .        backend/Dockerfile
build admin    frontend frontend/apps/admin/Dockerfile
build kitchen  frontend frontend/apps/kitchen/Dockerfile
build menu-web frontend frontend/apps/menu-web/Dockerfile

if [[ $PUSH == 1 ]]; then
  echo "Pushed $TAG. On the server: AMADYA_TAG=$TAG (or latest) in .env, then"
  echo "  docker compose -f docker-compose.yml -f docker-compose.prod.yml pull"
  echo "  docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build"
fi
