#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR=/opt/shortlink
DOCKER_DIR="${PROJECT_DIR}/docker"

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

docker compose --project-name shortlink -f docker/docker-compose.yml up -d

wait_for_port 127.0.0.1 3306
wait_for_port 127.0.0.1 8848
wait_for_port 127.0.0.1 9876

docker compose --project-name shortlink -f docker/docker-compose.yml -f docker/docker-compose.app.yml pull
docker compose --project-name shortlink -f docker/docker-compose.yml -f docker/docker-compose.app.yml up -d
docker compose --project-name shortlink -f docker/docker-compose.yml -f docker/docker-compose.app.yml ps

echo "frontend: http://$(hostname -I | awk '{print $1}')"
echo "redirect base: http://$(hostname -I | awk '{print $1}'):8003"
