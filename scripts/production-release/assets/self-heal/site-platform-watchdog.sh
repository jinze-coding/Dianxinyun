#!/usr/bin/env bash

set -u

SERVICE_NAME="${SERVICE_NAME:-site-platform.service}"
MYSQL_SERVICE_NAME="${MYSQL_SERVICE_NAME:-mysql.service}"
REDIS_SERVICE_NAME="${REDIS_SERVICE_NAME:-redis-server.service}"
MAINTENANCE_LOCK="${MAINTENANCE_LOCK:-/etc/site-platform/maintenance.lock}"
WATCHDOG_LOCK="${WATCHDOG_LOCK:-/run/site-platform-watchdog.lock}"
LOCAL_HEALTH_URL="${LOCAL_HEALTH_URL:-http://127.0.0.1:8080/api/v1/auth/captcha}"
HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-45}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"

usage() {
  printf 'Usage: %s --check-only|--repair-inactive\n' "$0" >&2
  exit 2
}

[ "$#" -eq 1 ] || usage
mode="$1"
case "$mode" in
  --check-only|--repair-inactive) ;;
  *) usage ;;
esac

case "$HEALTH_ATTEMPTS" in
  ''|*[!0-9]*) printf '[site-platform-watchdog][ERROR] HEALTH_ATTEMPTS must be a positive integer\n' >&2; exit 2 ;;
esac
[ "$HEALTH_ATTEMPTS" -gt 0 ] || {
  printf '[site-platform-watchdog][ERROR] HEALTH_ATTEMPTS must be greater than zero\n' >&2
  exit 2
}
case "$HEALTH_INTERVAL_SECONDS" in
  ''|*[!0-9]*) printf '[site-platform-watchdog][ERROR] HEALTH_INTERVAL_SECONDS must be a non-negative integer\n' >&2; exit 2 ;;
esac

for command_name in curl flock grep sleep systemctl; do
  command -v "$command_name" >/dev/null 2>&1 || {
    printf '[site-platform-watchdog][ERROR] missing command: %s\n' "$command_name" >&2
    exit 1
  }
done

exec 9>"$WATCHDOG_LOCK" || {
  printf '[site-platform-watchdog][ERROR] cannot open concurrency lock: %s\n' "$WATCHDOG_LOCK" >&2
  exit 1
}
if ! flock -n 9; then
  printf '[site-platform-watchdog] another check is already running; skip\n'
  exit 0
fi

log() {
  printf '[site-platform-watchdog] %s\n' "$*"
}

warn() {
  printf '[site-platform-watchdog][WARN] %s\n' "$*" >&2
}

unit_state() {
  systemctl is-active "$1" 2>/dev/null || true
}

apt_upgrade_active() {
  local unit state
  for unit in apt-daily.service apt-daily-upgrade.service; do
    state="$(unit_state "$unit")"
    case "$state" in
      active|activating|reloading)
        log "$unit is $state; skip"
        return 0
        ;;
    esac
  done
  return 1
}

dependencies_ready() {
  [ "$(unit_state "$MYSQL_SERVICE_NAME")" = active ] &&
    [ "$(unit_state "$REDIS_SERVICE_NAME")" = active ]
}

backend_healthy() {
  local response body http_code
  response="$(
    curl --silent --show-error \
      --connect-timeout 2 \
      --max-time 5 \
      --write-out $'\\n%{http_code}' \
      "$LOCAL_HEALTH_URL" 2>/dev/null
  )" || return 1

  http_code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  [ "$http_code" = 200 ] || return 1
  printf '%s' "$body" \
    | grep -Eq '"code"[[:space:]]*:[[:space:]]*200([[:space:]]*[,}])'
}

if [ -e "$MAINTENANCE_LOCK" ] || [ -L "$MAINTENANCE_LOCK" ]; then
  log "maintenance lock exists; skip"
  exit 0
fi

if apt_upgrade_active; then
  exit 0
fi

if ! dependencies_ready; then
  log "mysql or redis is not active; skip"
  exit 0
fi

service_state="$(unit_state "$SERVICE_NAME")"

if [ "$mode" = --check-only ]; then
  [ "$service_state" = active ] || {
    warn "$SERVICE_NAME is $service_state"
    exit 1
  }
  backend_healthy || {
    warn "$SERVICE_NAME is active but local captcha health failed"
    exit 1
  }
  log "$SERVICE_NAME is active and local captcha health passed"
  exit 0
fi

case "$service_state" in
  active)
    if backend_healthy; then
      log "$SERVICE_NAME is active and local captcha health passed"
    else
      warn "$SERVICE_NAME is active but local captcha health failed; conservative policy will not restart it"
    fi
    exit 0
    ;;
  activating|reloading|deactivating)
    log "$SERVICE_NAME is $service_state; skip"
    exit 0
    ;;
  inactive|failed)
    ;;
  *)
    warn "$SERVICE_NAME has unsupported state '$service_state'; skip"
    exit 0
    ;;
esac

# Close the race between the first observation and the only mutating action.
if [ -e "$MAINTENANCE_LOCK" ] || [ -L "$MAINTENANCE_LOCK" ]; then
  log "maintenance lock appeared before start; skip"
  exit 0
fi
if apt_upgrade_active; then
  exit 0
fi
if ! dependencies_ready; then
  log "mysql or redis stopped before start; skip"
  exit 0
fi
service_state="$(unit_state "$SERVICE_NAME")"
case "$service_state" in
  inactive|failed) ;;
  *)
    log "$SERVICE_NAME changed to $service_state before start; skip"
    exit 0
    ;;
esac

log "$SERVICE_NAME is $service_state with ready dependencies; start it once"
if ! systemctl start --no-block "$SERVICE_NAME"; then
  warn "failed to submit start job for $SERVICE_NAME"
  exit 1
fi

for ((attempt = 1; attempt <= HEALTH_ATTEMPTS; attempt++)); do
  if [ -e "$MAINTENANCE_LOCK" ] || [ -L "$MAINTENANCE_LOCK" ]; then
    log "maintenance lock appeared while waiting for health; stop checking"
    exit 0
  fi
  if apt_upgrade_active; then
    exit 0
  fi
  if ! dependencies_ready; then
    log "mysql or redis stopped while waiting for health; stop checking"
    exit 0
  fi
  if backend_healthy; then
    log "$SERVICE_NAME recovered; local captcha health passed on attempt $attempt"
    exit 0
  fi
  if [ "$attempt" -lt "$HEALTH_ATTEMPTS" ]; then
    sleep "$HEALTH_INTERVAL_SECONDS"
  fi
done

warn "$SERVICE_NAME was started once but local captcha health did not pass within the bounded wait; no restart will be attempted"
exit 0
