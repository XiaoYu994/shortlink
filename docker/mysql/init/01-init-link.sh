#!/usr/bin/env bash
set -euo pipefail

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<'EOSQL'
CREATE DATABASE IF NOT EXISTS `link` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
EOSQL

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" link < /opt/bootstrap/link.sql
