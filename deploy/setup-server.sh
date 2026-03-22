#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR=/opt/shortlink
DOCKER_DIR="${PROJECT_DIR}/docker"
MQ_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.deploy.yml"
APP_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.app.yml"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 1
  fi
}

install_docker_if_missing() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    return
  fi

  curl -fsSL https://get.docker.com | sh
  systemctl enable docker
  systemctl start docker
}

wait_for_port() {
  local host=$1
  local port=$2
  local retries=${3:-60}

  for _ in $(seq 1 "${retries}"); do
    if (echo >"/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "timeout waiting for ${host}:${port}" >&2
  return 1
}

normalize_wait_host() {
  local host=${1:-127.0.0.1}
  case "${host}" in
    host.docker.internal|localhost)
      echo "127.0.0.1"
      ;;
    *)
      echo "${host}"
      ;;
  esac
}

install_docker_if_missing
require_command docker

mkdir -p "${PROJECT_DIR}/docker" "${PROJECT_DIR}/resources/database" "${PROJECT_DIR}/deploy"

if [[ ! -f "${DOCKER_DIR}/.env" ]]; then
  echo "missing ${DOCKER_DIR}/.env" >&2
  exit 1
fi

set -a
source "${DOCKER_DIR}/.env"
set +a

if [[ -n "${GHCR_USERNAME:-}" && -n "${GHCR_TOKEN:-}" ]]; then
  echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USERNAME}" --password-stdin
fi

cd "${PROJECT_DIR}"

wait_for_port "$(normalize_wait_host "${SHORTLINK_MYSQL_HOST:-127.0.0.1}")" "${SHORTLINK_MYSQL_PORT:-3306}"
wait_for_port "$(normalize_wait_host "${SPRING_DATA_REDIS_HOST:-127.0.0.1}")" "${SPRING_DATA_REDIS_PORT:-6379}"

IFS=':' read -r nacos_host nacos_port <<<"${SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR}"
wait_for_port "$(normalize_wait_host "${nacos_host}")" "${nacos_port:-8848}"

docker compose --project-name shortlink -f "${MQ_COMPOSE_FILE}" pull
docker compose --project-name shortlink -f "${MQ_COMPOSE_FILE}" up -d

wait_for_port 127.0.0.1 9876
wait_for_port 127.0.0.1 10911

docker compose --project-name shortlink -f "${APP_COMPOSE_FILE}" pull
docker compose --project-name shortlink -f "${APP_COMPOSE_FILE}" up -d
docker compose --project-name shortlink -f "${MQ_COMPOSE_FILE}" ps
docker compose --project-name shortlink -f "${APP_COMPOSE_FILE}" ps

echo "frontend: http://$(hostname -I | awk '{print $1}')"
echo "gateway: http://$(hostname -I | awk '{print $1}'):8000"
echo "redirect base: http://$(hostname -I | awk '{print $1}'):8003"
