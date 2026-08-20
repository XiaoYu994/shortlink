#!/usr/bin/env bash
set -euo pipefail

DATABASE="${MYSQL_DATABASE:-link}"

mysql_args=(mysql -uroot -p"${MYSQL_ROOT_PASSWORD}")
if [[ -n "${MYSQL_HOST:-}" ]]; then
  mysql_args+=( -h"${MYSQL_HOST}" )
fi
if [[ -n "${MYSQL_PORT:-}" ]]; then
  mysql_args+=( -P"${MYSQL_PORT}" )
fi

column_exists=$("${mysql_args[@]}" --batch --skip-column-names -e \
  "SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = '${DATABASE}'
     AND table_name = 't_user_notification'
     AND column_name = 'event_id';")

if [[ "${column_exists}" == "0" ]]; then
  echo "adding t_user_notification.event_id"
  "${mysql_args[@]}" "${DATABASE}" -e \
    "ALTER TABLE t_user_notification
       ADD COLUMN event_id varchar(64) DEFAULT NULL COMMENT '消息幂等 ID' AFTER read_flag;"
else
  echo "t_user_notification.event_id already exists"
fi

index_exists=$("${mysql_args[@]}" --batch --skip-column-names -e \
  "SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = '${DATABASE}'
     AND table_name = 't_user_notification'
     AND index_name = 'idx_unique_event_id';")

if [[ "${index_exists}" == "0" ]]; then
  echo "adding t_user_notification.idx_unique_event_id"
  "${mysql_args[@]}" "${DATABASE}" -e \
    "ALTER TABLE t_user_notification ADD UNIQUE KEY idx_unique_event_id (event_id);"
else
  echo "t_user_notification.idx_unique_event_id already exists"
fi
