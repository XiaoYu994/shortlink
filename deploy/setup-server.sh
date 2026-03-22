#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR=/opt/shortlink
DOCKER_DIR="${PROJECT_DIR}/docker"
INFRA_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.deploy.yml"
APP_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.app.yml"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 1
  fi
}

load_env_file() {
  local env_file=$1

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" ]] && continue
    [[ "${line}" =~ ^[[:space:]]*# ]] && continue

    local key=${line%%=*}
    local value=${line#*=}

    if [[ "${key}" == "${line}" ]]; then
      continue
    fi

    if [[ "${value}" =~ ^\".*\"$ ]] || [[ "${value}" =~ ^\'.*\'$ ]]; then
      value="${value:1:${#value}-2}"
    fi

    export "${key}=${value}"
  done < "${env_file}"
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

get_container_ip() {
  local container=$1
  docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "${container}" 2>/dev/null || true
}

wait_for_container_port() {
  local container=$1
  local port=$2
  local retries=${3:-60}

  for _ in $(seq 1 "${retries}"); do
    local ip
    ip=$(get_container_ip "${container}")
    if [[ -n "${ip}" ]] && (echo >"/dev/tcp/${ip}/${port}") >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  docker logs --tail 50 "${container}" >&2 || true
  echo "timeout waiting for ${container}:${port}" >&2
  return 1
}

wait_for_container_health() {
  local container=$1
  local retries=${2:-60}

  for _ in $(seq 1 "${retries}"); do
    local status
    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "${container}" 2>/dev/null || true)

    case "${status}" in
      healthy)
        return 0
        ;;
      unhealthy)
        docker logs --tail 50 "${container}" >&2 || true
        echo "container became unhealthy: ${container}" >&2
        return 1
        ;;
    esac

    sleep 2
  done

  echo "timeout waiting for container health: ${container}" >&2
  return 1
}

ensure_container_running() {
  local container=$1
  local status

  status=$(docker inspect --format '{{.State.Status}}' "${container}" 2>/dev/null || true)
  if [[ "${status}" != "running" ]]; then
    docker logs --tail 50 "${container}" >&2 || true
    echo "container is not running: ${container}" >&2
    return 1
  fi
}

remove_container_if_present() {
  local container=$1

  if docker inspect "${container}" >/dev/null 2>&1; then
    docker rm -f "${container}" >/dev/null 2>&1 || true
  fi
}

remove_containers_if_present() {
  for container in "$@"; do
    remove_container_if_present "${container}"
  done
}

install_docker_if_missing
require_command docker

mkdir -p "${PROJECT_DIR}/docker" "${PROJECT_DIR}/resources/database" "${PROJECT_DIR}/deploy"

if [[ ! -f "${DOCKER_DIR}/.env" ]]; then
  echo "missing ${DOCKER_DIR}/.env" >&2
  exit 1
fi

load_env_file "${DOCKER_DIR}/.env"

if [[ -n "${GHCR_USERNAME:-}" && -n "${GHCR_TOKEN:-}" ]]; then
  echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USERNAME}" --password-stdin
fi

cd "${PROJECT_DIR}"

docker compose --env-file "${DOCKER_DIR}/.env" --project-name shortlink -f "${INFRA_COMPOSE_FILE}" pull
remove_containers_if_present \
  shortlink-mysql \
  shortlink-redis \
  shortlink-nacos \
  shortlink-namesrv \
  shortlink-broker
docker compose --env-file "${DOCKER_DIR}/.env" --project-name shortlink -f "${INFRA_COMPOSE_FILE}" up -d

wait_for_container_health shortlink-mysql 120
wait_for_container_health shortlink-redis 120
wait_for_container_port shortlink-nacos 8848 120
wait_for_container_port shortlink-namesrv 9876 120
wait_for_container_port shortlink-broker 10911 120

docker compose --env-file "${DOCKER_DIR}/.env" --project-name shortlink -f "${APP_COMPOSE_FILE}" pull
remove_containers_if_present \
  shortlink-gateway \
  shortlink-aggregation \
  shortlink-stats \
  shortlink-risk \
  shortlink-frontend
docker compose --env-file "${DOCKER_DIR}/.env" --project-name shortlink -f "${APP_COMPOSE_FILE}" up -d
wait_for_port 127.0.0.1 80 120
wait_for_port 127.0.0.1 8000 120
wait_for_port 127.0.0.1 8003 120
ensure_container_running shortlink-gateway
ensure_container_running shortlink-aggregation
ensure_container_running shortlink-stats
ensure_container_running shortlink-risk
ensure_container_running shortlink-frontend

docker compose --env-file "${DOCKER_DIR}/.env" --project-name shortlink -f "${INFRA_COMPOSE_FILE}" ps
docker compose --env-file "${DOCKER_DIR}/.env" --project-name shortlink -f "${APP_COMPOSE_FILE}" ps

echo "frontend: http://$(hostname -I | awk '{print $1}')/console/"
echo "gateway: http://$(hostname -I | awk '{print $1}'):8000"
echo "redirect base: http://$(hostname -I | awk '{print $1}')"
