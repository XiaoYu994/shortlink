#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONFIG_DIR="${PROJECT_DIR}/docker/nacos/config"
GROUP="${NACOS_CONFIG_GROUP:-DEFAULT_GROUP}"
NAMESPACE="${NACOS_NAMESPACE:-}"
OVERWRITE="${NACOS_CONFIG_OVERWRITE:-false}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 1
  fi
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

is_enabled() {
  case "${1:-true}" in
    true|TRUE|yes|YES|1) return 0 ;;
    *) return 1 ;;
  esac
}

resolve_nacos_base() {
  if [[ -n "${NACOS_ADDR:-}" ]]; then
    echo "${NACOS_ADDR}"
    return
  fi

  if is_enabled "${MANAGE_NACOS:-true}"; then
    local ip
    ip=$(get_container_ip shortlink-nacos)
    if [[ -z "${ip}" ]]; then
      echo "managed nacos container not found; set NACOS_ADDR" >&2
      exit 1
    fi
    echo "http://${ip}:8848"
    return
  fi

  local server_addr="${NACOS_SERVER_ADDR:-}"
  if [[ -n "${server_addr}" && "${server_addr}" != "nacos:8848" ]]; then
    case "${server_addr}" in
      http://*|https://*) echo "${server_addr}" ;;
      *) echo "http://${server_addr}" ;;
    esac
    return
  fi

  echo "external nacos needs NACOS_ADDR (host-reachable URL, e.g. http://127.0.0.1:8848)" >&2
  exit 1
}

nacos_login() {
  local base=$1
  local body
  body=$(curl -sf -X POST "${base}/nacos/v1/auth/login" \
    -d "username=${NACOS_USERNAME}" \
    -d "password=${NACOS_PASSWORD}" || true)

  if [[ -z "${body}" ]]; then
    body=$(curl -sf -X POST "${base}/nacos/v1/auth/login" \
      -d "username=nacos" \
      -d "password=nacos" || true)
    if is_enabled "${MANAGE_NACOS:-true}" && [[ -n "${body}" && "${NACOS_PASSWORD}" != "nacos" ]]; then
      local bootstrap_token
      bootstrap_token=$(printf '%s' "${body}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
      if [[ -n "${bootstrap_token}" ]]; then
        curl -sf -X PUT "${base}/nacos/v1/auth/users?accessToken=${bootstrap_token}" \
          -d "username=nacos" \
          -d "newPassword=${NACOS_PASSWORD}" >/dev/null || true
        body=$(curl -sf -X POST "${base}/nacos/v1/auth/login" \
          -d "username=${NACOS_USERNAME}" \
          -d "password=${NACOS_PASSWORD}" || true)
      fi
    fi
  fi

  printf '%s' "${body}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

config_exists() {
  local base=$1
  local data_id=$2
  local token=$3
  local url="${base}/nacos/v1/cs/configs?dataId=${data_id}&group=${GROUP}"
  if [[ -n "${NAMESPACE}" ]]; then
    url="${url}&tenant=${NAMESPACE}"
  fi
  if [[ -n "${token}" ]]; then
    url="${url}&accessToken=${token}"
  fi

  local body
  body=$(curl -sf "${url}" || true)
  if [[ -z "${body}" || "${body}" == "config data not exist" ]]; then
    return 1
  fi
  return 0
}

render_template() {
  local file=$1
  local content
  content=$(cat "${file}")
  content=${content//__REDIS_HOST__/${REDIS_HOST:-redis}}
  content=${content//__REDIS_PORT__/${REDIS_PORT:-6379}}
  content=${content//__REDIS_PASSWORD__/${REDIS_PASSWORD:-}}
  content=${content//__ROCKETMQ_NAME_SERVER__/${ROCKETMQ_NAME_SERVER:-namesrv:9876}}
  content=${content//__DASHSCOPE_API_KEY__/${DASHSCOPE_API_KEY:-}}
  content=${content//__SHORT_LINK_DOMAIN_DEFAULT__/${SHORT_LINK_DOMAIN_DEFAULT:-}}
  content=${content//__SHORT_LINK_DOMAIN_PROTOCOL__/${SHORT_LINK_DOMAIN_PROTOCOL:-http}}
  content=${content//__SHORT_LINK_STATS_LOCALE_AMAP_KEY__/${SHORT_LINK_STATS_LOCALE_AMAP_KEY:-}}
  printf '%s' "${content}"
}

publish_config() {
  local base=$1
  local data_id=$2
  local file=$3
  local token=$4

  if [[ ! -f "${file}" ]]; then
    echo "missing config template: ${file}" >&2
    exit 1
  fi

  if [[ "${OVERWRITE}" != "true" ]] && config_exists "${base}" "${data_id}" "${token}"; then
    echo "nacos config exists, skip: ${data_id}"
    return
  fi

  local content
  content=$(render_template "${file}")

  local args=(
    -sf
    -X POST
    "${base}/nacos/v1/cs/configs"
    --data-urlencode "dataId=${data_id}"
    --data-urlencode "group=${GROUP}"
    --data-urlencode "type=yaml"
    --data-urlencode "content=${content}"
  )
  if [[ -n "${NAMESPACE}" ]]; then
    args+=(--data-urlencode "tenant=${NAMESPACE}")
  fi
  if [[ -n "${token}" ]]; then
    args+=(--data-urlencode "accessToken=${token}")
  fi

  if ! curl "${args[@]}" >/dev/null; then
    echo "failed to publish nacos config: ${data_id}" >&2
    exit 1
  fi
  echo "published nacos config: ${data_id}"
}

require_command curl
if [[ -z "${NACOS_ADDR:-}" ]] && is_enabled "${MANAGE_NACOS:-true}"; then
  require_command docker
fi

if [[ ! -d "${CONFIG_DIR}" ]]; then
  echo "missing ${CONFIG_DIR}" >&2
  exit 1
fi

ensure_namespace() {
  local base=$1
  local token=$2
  if [[ -z "${NAMESPACE}" ]]; then
    return
  fi
  local url="${base}/nacos/v1/console/namespaces"
  local args=(-sf -X POST "${url}" --data-urlencode "customNamespaceId=${NAMESPACE}" --data-urlencode "namespaceName=${NAMESPACE}" --data-urlencode "namespaceDesc=shortlink")
  if [[ -n "${token}" ]]; then
    args+=(--data-urlencode "accessToken=${token}")
  fi
  curl "${args[@]}" >/dev/null 2>&1 || true
}

NACOS_BASE=$(resolve_nacos_base)
TOKEN=""
if [[ "${NACOS_AUTH_ENABLE:-true}" != "false" ]]; then
  TOKEN=$(nacos_login "${NACOS_BASE}" || true)
  if [[ -z "${TOKEN}" ]]; then
    echo "nacos login failed; publishing without token" >&2
  fi
fi

ensure_namespace "${NACOS_BASE}" "${TOKEN}"
publish_config "${NACOS_BASE}" "shortlink-common.yaml" "${CONFIG_DIR}/shortlink-common.yaml" "${TOKEN}"
publish_config "${NACOS_BASE}" "shortlink-aggregation-service.yaml" "${CONFIG_DIR}/shortlink-aggregation-service.yaml" "${TOKEN}"
publish_config "${NACOS_BASE}" "shortlink-gateway-service.yaml" "${CONFIG_DIR}/shortlink-gateway-service.yaml" "${TOKEN}"
