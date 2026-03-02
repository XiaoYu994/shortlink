#!/usr/bin/env bash
set -euo pipefail

SKIP_ENDPOINT_CHECK=false
if [[ "${1:-}" == "--skip-endpoint-check" ]]; then
  SKIP_ENDPOINT_CHECK=true
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker/docker-compose.yml"

SERVICES=(
  "gateway-service:8000"
  "project-service:8001"
  "user-service:8002"
  "aggregation-service:8003"
  "stats-service:8004"
  "risk-service:8005"
)

echo "[1/3] Validate docker compose config"
docker compose -f "${COMPOSE_FILE}" config >/dev/null

echo "[2/3] Validate monitoring files exist"
for file in \
  "${ROOT_DIR}/docker/monitoring/prometheus.yml" \
  "${ROOT_DIR}/docker/monitoring/alert.rules.yml" \
  "${ROOT_DIR}/docker/monitoring/alertmanager.yml" \
  "${ROOT_DIR}/docker/monitoring/grafana/dashboards/shortlink-overview.json"; do
  if [[ ! -f "${file}" ]]; then
    echo "Missing file: ${file}" >&2
    exit 1
  fi
done

if [[ "${SKIP_ENDPOINT_CHECK}" == "true" ]]; then
  echo "[3/3] Skip endpoint checks (--skip-endpoint-check)"
  echo "Preflight check passed (static checks)."
  exit 0
fi

echo "[3/3] Validate actuator endpoints"
FAILED=0
for service in "${SERVICES[@]}"; do
  name="${service%%:*}"
  port="${service##*:}"

  health_url="http://127.0.0.1:${port}/actuator/health"
  prometheus_url="http://127.0.0.1:${port}/actuator/prometheus"

  if ! curl -fsS --max-time 3 "${health_url}" >/dev/null; then
    echo "Health check failed: ${name} (${health_url})" >&2
    FAILED=1
  fi

  if ! curl -fsS --max-time 3 "${prometheus_url}" >/dev/null; then
    echo "Prometheus endpoint check failed: ${name} (${prometheus_url})" >&2
    FAILED=1
  fi
done

if [[ "${FAILED}" -ne 0 ]]; then
  echo "Preflight check failed." >&2
  exit 1
fi

echo "Preflight check passed."
