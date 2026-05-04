#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
command -v docker >/dev/null 2>&1 || { echo "Нужен Docker"; exit 1; }
docker compose -f docker-compose.engine-relay.yml up -d
echo ""
echo "Ключ: $ROOT/engine-relay-data/id_ed25519.pub"
echo "Для других ПК в ЛВС: export RUC_ENGINE_RELAY=192.168.x.x:21117 перед docker compose up"
