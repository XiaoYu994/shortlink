#!/usr/bin/env bash
set -euo pipefail

DATABASE="${MYSQL_DATABASE:-link}"
SHARD_COUNT="${SHORTLINK_SHARD_COUNT:-16}"
SHARDED_TABLES=(t_user t_group t_link t_link_goto)

if ! [[ "${SHARD_COUNT}" =~ ^[1-9][0-9]*$ ]]; then
  echo "SHORTLINK_SHARD_COUNT must be a positive integer: ${SHARD_COUNT}" >&2
  exit 1
fi

mysql_args=(mysql -uroot -p"${MYSQL_ROOT_PASSWORD}")
if [[ -n "${MYSQL_HOST:-}" ]]; then
  mysql_args+=( -h"${MYSQL_HOST}" )
fi
if [[ -n "${MYSQL_PORT:-}" ]]; then
  mysql_args+=( -P"${MYSQL_PORT}" )
fi
mysql_cmd=("${mysql_args[@]}" "${DATABASE}")

# Keep shard creation in one mysql session so first initialization and upgrades
# use the same idempotent operation.
{
  for table in "${SHARDED_TABLES[@]}"; do
    for ((index = 0; index < SHARD_COUNT; index++)); do
      printf 'CREATE TABLE IF NOT EXISTS `%s_%s` LIKE `%s`;\n' "${table}" "${index}" "${table}"
    done
  done
} | "${mysql_cmd[@]}"

expected_count=$((SHARD_COUNT * ${#SHARDED_TABLES[@]}))
table_list=""
for table in "${SHARDED_TABLES[@]}"; do
  for ((index = 0; index < SHARD_COUNT; index++)); do
    table_list+="'${table}_${index}',"
  done
done
table_list="${table_list%,}"

actual_count=$("${mysql_args[@]}" --batch --skip-column-names -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (${table_list});" "${DATABASE}")
if [[ "${actual_count}" != "${expected_count}" ]]; then
  echo "shard table migration incomplete: expected ${expected_count}, found ${actual_count}" >&2
  exit 1
fi

echo "verified ${actual_count} shard tables in ${DATABASE}"
