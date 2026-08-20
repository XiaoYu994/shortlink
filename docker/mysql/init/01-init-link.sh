#!/usr/bin/env bash
set -euo pipefail

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<'EOSQL'
CREATE DATABASE IF NOT EXISTS `link` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
EOSQL

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" link < /opt/bootstrap/link.sql

# The single-node ShardingSphere config uses autoTables with 16 physical
# tables per logical table. The dump contains the base schema, so clone the
# four sharded tables during first-time initialization.
for table in t_user t_group t_link t_link_goto; do
  for index in $(seq 0 15); do
    mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" link \
      -e "CREATE TABLE IF NOT EXISTS \`${table}_${index}\` LIKE \`${table}\`;"
  done
done
