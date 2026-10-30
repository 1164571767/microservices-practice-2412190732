#!/usr/bin/env bash
(
set -euo pipefail
# Executed only by the official MySQL entrypoint on a fresh microservices volume.
# NO_BACKSLASH_ESCAPES plus doubled quotes preserves arbitrary password characters.
create_service_database() {
  local name="$1" password="$2"
  if [[ -z "$password" ]]; then printf 'Missing password for %s\n' "$name" >&2; exit 1; fi
  password=${password//\'/\'\'}
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE DATABASE petcare_${name} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'svc_${name}'@'%' IDENTIFIED BY '${password}';
GRANT ALL PRIVILEGES ON petcare_${name}.* TO 'svc_${name}'@'%';
SQL
}
create_service_database identity "${IDENTITY_DB_PASSWORD:?required}"
create_service_database patient "${PATIENT_DB_PASSWORD:?required}"
create_service_database clinic "${CLINIC_DB_PASSWORD:?required}"
create_service_database pharmacy "${PHARMACY_DB_PASSWORD:?required}"
create_service_database billing "${BILLING_DB_PASSWORD:?required}"
create_service_database notification "${NOTIFICATION_DB_PASSWORD:?required}"
create_service_database reporting "${REPORTING_DB_PASSWORD:?required}"
)
