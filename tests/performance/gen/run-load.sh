#!/usr/bin/env bash
# Generate 10k sharded short links, backfill bloom, write JMeter CSVs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
GEN="$ROOT/tests/performance/gen"
DATA="$ROOT/tests/performance/data"
IMAGE="${AGG_IMAGE:-ghcr.io/xiaoyu994/shortlink-aggregation-service:local}"
NETWORK="${DOCKER_NETWORK:-shortlink-network}"
REDIS_PASSWORD="${REDIS_PASSWORD:-xhy_redis}"
COUNT="${COUNT:-10000}"
DOMAIN="${DOMAIN:-localhost}"

cd "$ROOT"

if [[ "${1:-}" == "--purge" ]]; then
  python3 "$GEN/load_links.py" --purge --marker perf-gen-10k
  exit 0
fi

python3 "$GEN/load_links.py" --count "$COUNT" --gids 512 --domain "$DOMAIN" --out-dir "$DATA" --load

CLASS_DIR="$(mktemp -d)"
trap 'rm -rf "$CLASS_DIR"' EXIT
LIB_DIR="$(mktemp -d)"
trap 'rm -rf "$CLASS_DIR" "$LIB_DIR"' EXIT
docker cp shortlink-aggregation:/app/BOOT-INF/lib/. "$LIB_DIR/"
javac -encoding UTF-8 -cp "$(echo "$LIB_DIR"/*.jar | tr ' ' ':')" -d "$CLASS_DIR" "$GEN/BloomBackfill.java"

docker run --rm --network "$NETWORK" --entrypoint sh \
  -e REDIS_PASSWORD="$REDIS_PASSWORD" \
  -v "$CLASS_DIR:/work/classes:ro" \
  -v "$DATA:/work/data:ro" \
  "$IMAGE" \
  -c 'java -cp "/work/classes:/app/BOOT-INF/lib/*" BloomBackfill redis://redis:6379 "$REDIS_PASSWORD" /work/data/full-urls.txt'

echo "sample mix.csv:"
head -4 "$DATA/mix.csv"
