#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# 기본 compose + override 오버레이로 내리기 (로컬 개발용)
docker compose \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.override.yml \
  down
