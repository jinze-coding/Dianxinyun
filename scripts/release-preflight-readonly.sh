#!/usr/bin/env bash
# Collect deployment metadata only. Never load application secrets or modify services/data.
set -euo pipefail
export LC_ALL=C
printf 'DXY_PREFLIGHT_VERSION=20260915\n'
printf 'CHECKED_AT=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
printf 'ARCH=%s\n' "$(uname -m)"
printf '\n[SERVICE]\n'
if command -v systemctl >/dev/null; then
  systemctl show site-platform.service --no-pager \
    --property=LoadState,ActiveState,SubState,MainPID,WorkingDirectory,FragmentPath || true
fi
printf '\n[DISK]\n'
df -h / /opt /root 2>/dev/null || true
printf '\n[DEPLOYMENT_PATHS]\n'
for item in /opt/site-platform/backend/site-platform.jar /opt/site-platform/frontend /opt/site-platform/uploads; do
  if [ -e "$item" ]; then
    printf '%s -> %s\n' "$item" "$(readlink -f "$item")"
    if [ -f "$item" ]; then sha256sum "$item"; fi
  else printf 'MISSING=%s\n' "$item"; fi
done
printf '\n[TOOLS]\n'
for item in java mysql mysqldump nginx docker python3 sha256sum unzip; do
  if command -v "$item" >/dev/null; then printf '%s=available\n' "$item"; else printf '%s=missing\n' "$item"; fi
done
java -version 2>&1 | head -n 3 || true
if command -v docker >/dev/null; then
  docker image ls --format '{{.Repository}}:{{.Tag}} {{.ID}}' 2>/dev/null | sed -n '/meeting\|dianxinyun\|preview/p' || true
fi
printf '\n[LOCAL_HEALTH]\n'
if command -v curl >/dev/null && command -v python3 >/dev/null; then
  # Print only the business status, never the captcha payload or cookies.
  curl --silent --fail --max-time 10 http://127.0.0.1:8080/api/v1/auth/captcha |
    python3 -c 'import json,sys; d=json.load(sys.stdin); print("captcha_business_code="+str(d.get("code")))' || printf 'captcha_health=unavailable\n'
fi
printf '\n[DATABASE_METADATA]\n'
db_name="${DXY_DATABASE_NAME:-dianxinyun}"
if [[ ! "$db_name" =~ ^[a-zA-Z0-9_]+$ ]]; then printf 'Invalid DXY_DATABASE_NAME\n' >&2; exit 1; fi
mysql_args=()
if [ -n "${MYSQL_DEFAULTS_FILE:-}" ]; then
  [ -f "$MYSQL_DEFAULTS_FILE" ] && [ ! -L "$MYSQL_DEFAULTS_FILE" ] || { printf 'Invalid MYSQL_DEFAULTS_FILE\n' >&2; exit 1; }
  [ "$(stat -c '%a' "$MYSQL_DEFAULTS_FILE")" = 600 ] || { printf 'MYSQL_DEFAULTS_FILE must have mode 600\n' >&2; exit 1; }
  mysql_args+=("--defaults-extra-file=$MYSQL_DEFAULTS_FILE")
fi
mysql_args+=(--connect-timeout=5 --batch --skip-column-names)
if command -v mysql >/dev/null && mysql "${mysql_args[@]}" -e 'SELECT 1' >/dev/null 2>&1; then
  printf 'DATABASE=%s\n' "$db_name"
  mysql "${mysql_args[@]}" -e "SELECT VERSION(); SELECT CONCAT('TABLE_COUNT=',COUNT(*)) FROM information_schema.tables WHERE table_schema='$db_name' AND table_type='BASE TABLE'; SELECT CONCAT('TABLE=',table_name) FROM information_schema.tables WHERE table_schema='$db_name' AND table_type='BASE TABLE' ORDER BY table_name;"
  marker_exists="$(mysql "${mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db_name' AND table_name='sys_data_migration';")"
  if [ "$marker_exists" = 1 ]; then
    mysql "${mysql_args[@]}" "$db_name" -e "SELECT CONCAT('MIGRATION=',migration_key) FROM sys_data_migration ORDER BY migration_key;"
  else printf 'MIGRATION_TABLE=missing\n'; fi
else
  printf 'DATABASE_STATUS=metadata-not-read; existing local MySQL authentication is unavailable\n'
fi
printf '\nREAD_ONLY_COMPLETE=true\n'
printf 'This report does not authorize migration or prove release compatibility.\n'
