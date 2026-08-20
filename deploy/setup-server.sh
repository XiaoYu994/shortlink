#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR=/opt/shortlink
DOCKER_DIR="${PROJECT_DIR}/docker"
INFRA_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.deploy.yml"
APP_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.app.yml"
APP_EXTERNAL_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.app.external.yml"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 1
  fi
}

is_enabled() {
  case "${1:-true}" in
    true|TRUE|yes|YES|1) return 0 ;;
    *) return 1 ;;
  esac
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

apply_connection_defaults() {
  export MANAGE_MYSQL="${MANAGE_MYSQL:-true}"
  export MANAGE_REDIS="${MANAGE_REDIS:-true}"
  export MANAGE_NACOS="${MANAGE_NACOS:-true}"
  export MANAGE_ROCKETMQ="${MANAGE_ROCKETMQ:-true}"
  export MYSQL_HOST="${MYSQL_HOST:-mysql}"
  export MYSQL_PORT="${MYSQL_PORT:-3306}"
  export MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
  export MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
  export MYSQL_DATABASE="${MYSQL_DATABASE:-link}"
  export MYSQL_COLD_DATABASE="${MYSQL_COLD_DATABASE:-link_cold}"
  export REDIS_HOST="${REDIS_HOST:-redis}"
  export REDIS_PORT="${REDIS_PORT:-6379}"
  export NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-nacos:8848}"
  export ROCKETMQ_NAME_SERVER="${ROCKETMQ_NAME_SERVER:-namesrv:9876}"
  export DOCKER_NETWORK="${DOCKER_NETWORK:-shortlink-network}"
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

  if [[ "${host}" == "host.docker.internal" ]]; then
    host=127.0.0.1
  fi

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
  local network="${DOCKER_NETWORK:-shortlink-network}"
  local ip
  ip=$(docker inspect -f "{{with index .NetworkSettings.Networks \"${network}\"}}{{.IPAddress}}{{end}}" "${container}" 2>/dev/null || true)
  if [[ -n "${ip}" ]]; then
    printf '%s' "${ip}"
    return
  fi
  docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{println}}{{end}}' "${container}" 2>/dev/null | awk 'NF { print; exit }'
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

wait_for_http() {
  local url=$1
  local retries=${2:-60}

  for _ in $(seq 1 "${retries}"); do
    if curl -fsS --max-time 3 "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "timeout waiting for ${url}" >&2
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

wait_from_docker_network() {
  local host=$1
  local port=$2
  local retries=${3:-60}
  local network="${DOCKER_EXTERNAL_NETWORK:-${DOCKER_NETWORK}}"

  if ! docker network inspect "${network}" >/dev/null 2>&1; then
    if [[ "${network}" == "${DOCKER_NETWORK}" ]]; then
      docker network create "${network}" >/dev/null
    else
      echo "missing external docker network: ${network}" >&2
      echo "create it first, or set DOCKER_EXTERNAL_NETWORK to an existing network" >&2
      return 1
    fi
  fi

  for _ in $(seq 1 "${retries}"); do
    if docker run --rm --network "${network}" busybox:1.36 nc -z -w 2 "${host}" "${port}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "timeout waiting from docker network ${network} for ${host}:${port}" >&2
  return 1
}

wait_for_endpoint() {
  local host=$1
  local port=$2
  local retries=${3:-60}

  case "${host}" in
    localhost|127.0.0.1|host.docker.internal)
      wait_for_port "${host}" "${port}" "${retries}"
      ;;
    *)
      if [[ "${host}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        wait_for_port "${host}" "${port}" "${retries}" || wait_from_docker_network "${host}" "${port}" "${retries}"
      else
        wait_from_docker_network "${host}" "${port}" "${retries}"
      fi
      ;;
  esac
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

ensure_app_network() {
  docker network inspect "${DOCKER_NETWORK}" >/dev/null 2>&1 || docker network create "${DOCKER_NETWORK}" >/dev/null
}

infra_profiles() {
  local -a profiles=()
  is_enabled "${MANAGE_MYSQL}" && profiles+=(--profile mysql)
  is_enabled "${MANAGE_REDIS}" && profiles+=(--profile redis)
  is_enabled "${MANAGE_NACOS}" && profiles+=(--profile nacos)
  is_enabled "${MANAGE_ROCKETMQ}" && profiles+=(--profile rocketmq)
  printf '%s\n' "${profiles[@]}"
}

has_managed_infra() {
  is_enabled "${MANAGE_MYSQL}" || is_enabled "${MANAGE_REDIS}" || is_enabled "${MANAGE_NACOS}" || is_enabled "${MANAGE_ROCKETMQ}"
}

infra_compose() {
  local -a cmd=(
    docker compose
    --env-file "${DOCKER_DIR}/.env"
    --project-name shortlink
    -f "${INFRA_COMPOSE_FILE}"
  )
  local -a profiles=()
  mapfile -t profiles < <(infra_profiles)
  if [[ "${#profiles[@]}" -eq 0 ]]; then
    echo "all middleware is external; skip managed infra compose"
    return 0
  fi
  "${cmd[@]}" "${profiles[@]}" "$@"
}

app_compose() {
  local -a cmd=(
    docker compose
    --env-file "${DOCKER_DIR}/.env"
    --project-name shortlink
    -f "${APP_COMPOSE_FILE}"
  )
  if [[ -n "${DOCKER_EXTERNAL_NETWORK:-}" ]]; then
    cmd+=(-f "${APP_EXTERNAL_COMPOSE_FILE}")
  fi
  "${cmd[@]}" "$@"
}

import_nacos_config() {
  chmod +x "${PROJECT_DIR}/deploy/nacos-import-config.sh"
  bash "${PROJECT_DIR}/deploy/nacos-import-config.sh"
}

wait_for_managed_or_external() {
  if is_enabled "${MANAGE_MYSQL}"; then
    wait_for_container_health shortlink-mysql 120
  else
    echo "using external mysql ${MYSQL_HOST}:${MYSQL_PORT}"
    wait_for_endpoint "${MYSQL_HOST}" "${MYSQL_PORT}" 60
  fi

  if is_enabled "${MANAGE_REDIS}"; then
    wait_for_container_health shortlink-redis 120
  else
    echo "using external redis ${REDIS_HOST}:${REDIS_PORT}"
    wait_for_endpoint "${REDIS_HOST}" "${REDIS_PORT}" 60
  fi

  if is_enabled "${MANAGE_NACOS}"; then
    wait_for_container_health shortlink-nacos 180
  else
    echo "using external nacos ${NACOS_SERVER_ADDR}"
    local nacos_host=${NACOS_SERVER_ADDR%%:*}
    local nacos_port=${NACOS_SERVER_ADDR##*:}
    if [[ "${nacos_host}" == "${nacos_port}" ]]; then
      nacos_port=8848
    fi
    wait_for_endpoint "${nacos_host}" "${nacos_port}" 60
  fi

  if is_enabled "${MANAGE_ROCKETMQ}"; then
    wait_for_container_port shortlink-namesrv 9876 120
    wait_for_container_port shortlink-broker 10911 120
  else
    echo "using external rocketmq ${ROCKETMQ_NAME_SERVER}"
    local mq_host=${ROCKETMQ_NAME_SERVER%%:*}
    local mq_port=${ROCKETMQ_NAME_SERVER##*:}
    if [[ "${mq_host}" == "${mq_port}" ]]; then
      mq_port=9876
    fi
    wait_for_endpoint "${mq_host}" "${mq_port}" 60
  fi
}

migrate_mysql_shards() {
  if ! is_enabled "${MANAGE_MYSQL}"; then
    echo "using external mysql; automatic shard migration is skipped"
    return 0
  fi

  echo "ensuring MySQL shard tables exist"
  docker exec shortlink-mysql bash /opt/migrations/create-shard-tables.sh
}

setup_infra() {
  ensure_app_network

  if has_managed_infra; then
    infra_compose pull
    infra_compose up -d
  else
    echo "MANAGE_*=false for all middleware; not creating Nacos/RocketMQ/MySQL/Redis containers"
  fi

  wait_for_managed_or_external
  migrate_mysql_shards
  import_nacos_config

  if has_managed_infra; then
    infra_compose ps
  fi
}

reset_managed_infra() {
  echo "recreating only middleware this project manages"
  if is_enabled "${MANAGE_MYSQL}"; then
    remove_container_if_present shortlink-mysql
  fi
  if is_enabled "${MANAGE_REDIS}"; then
    remove_container_if_present shortlink-redis
  fi
  if is_enabled "${MANAGE_NACOS}"; then
    remove_container_if_present shortlink-nacos
  fi
  if is_enabled "${MANAGE_ROCKETMQ}"; then
    remove_container_if_present shortlink-namesrv
    remove_container_if_present shortlink-broker
  fi
  setup_infra
}

deploy_app() {
  if [[ -n "${GHCR_USERNAME:-}" && -n "${GHCR_TOKEN:-}" ]]; then
    echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USERNAME}" --password-stdin
  fi

  ensure_app_network
  app_compose pull
  import_nacos_config
  remove_containers_if_present \
    shortlink-gateway \
    shortlink-aggregation \
    shortlink-stats \
    shortlink-risk \
    shortlink-frontend
  app_compose up -d

  wait_for_port 127.0.0.1 80 120
  wait_for_container_port shortlink-gateway 8000 120
  wait_for_container_port shortlink-aggregation 8003 120
  wait_for_http "http://$(get_container_ip shortlink-gateway):8000/actuator/health" 120
  wait_for_http "http://$(get_container_ip shortlink-aggregation):8003/actuator/health" 120
  ensure_container_running shortlink-gateway
  ensure_container_running shortlink-aggregation
  ensure_container_running shortlink-frontend

  app_compose ps
  echo "frontend: http://$(hostname -I | awk '{print $1}')/console/"
  echo "api: http://$(hostname -I | awk '{print $1}')/api/"
  echo "redirect base: http://$(hostname -I | awk '{print $1}')"
}

main() {
  local scope=${1:-full}

  install_docker_if_missing
  require_command docker

  mkdir -p "${PROJECT_DIR}/docker" "${PROJECT_DIR}/resources/database" "${PROJECT_DIR}/deploy"

  if [[ ! -f "${DOCKER_DIR}/.env" ]]; then
    echo "missing ${DOCKER_DIR}/.env" >&2
    exit 1
  fi

  load_env_file "${DOCKER_DIR}/.env"
  apply_connection_defaults
  cd "${PROJECT_DIR}"

  case "${scope}" in
    full)
      setup_infra
      deploy_app
      ;;
    infra)
      setup_infra
      ;;
    infra-reset)
      reset_managed_infra
      ;;
    app)
      deploy_app
      ;;
    *)
      echo "usage: $0 [full|infra|infra-reset|app]" >&2
      exit 1
      ;;
  esac
}

main "${1:-full}"
