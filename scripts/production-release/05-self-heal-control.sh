#!/usr/bin/env bash

set -Eeuo pipefail
IFS=$'\n\t'
umask 077

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
ASSET_DIR="$SCRIPT_DIR/assets/self-heal"

SERVICE_NAME="${SERVICE_NAME:-site-platform.service}"
MYSQL_SERVICE_NAME="${MYSQL_SERVICE_NAME:-mysql.service}"
REDIS_SERVICE_NAME="${REDIS_SERVICE_NAME:-redis-server.service}"
LOCAL_HEALTH_URL="${LOCAL_HEALTH_URL:-http://127.0.0.1:8080/api/v1/auth/captcha}"

SYSTEMD_ROOT="${SYSTEMD_ROOT:-/etc/systemd/system}"
LOCAL_SBIN_ROOT="${LOCAL_SBIN_ROOT:-/usr/local/sbin}"
BACKUP_ROOT="${SELF_HEAL_BACKUP_ROOT:-/var/backups/site-platform-self-heal}"
PRODUCTION_MAINTENANCE_LOCK='/etc/site-platform/maintenance.lock'
RELEASE_OPERATION_LOCK='/run/site-platform-release.lock'
OFFLINE_WORKER_MARKER='/run/site-platform-offline-worker.state'

DROPIN_TARGET="$SYSTEMD_ROOT/$SERVICE_NAME.d/60-self-heal.conf"
WATCHDOG_SERVICE_TARGET="$SYSTEMD_ROOT/site-platform-watchdog.service"
WATCHDOG_TIMER_TARGET="$SYSTEMD_ROOT/site-platform-watchdog.timer"
WATCHDOG_SCRIPT_TARGET="$LOCAL_SBIN_ROOT/site-platform-watchdog"

REPLACE_CONFIRMATION='REPLACE_SITE_PLATFORM_SELF_HEAL'

log() {
  printf '[self-heal-control] %s\n' "$*" >&2
}

die() {
  printf '[self-heal-control][ERROR] %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<EOF
Usage:
  $0 install [--replace --confirm $REPLACE_CONFIRMATION]
  $0 verify

install installs or adopts the exact packaged self-heal files, reloads the
systemd manager, and enables the watchdog timer. It never stops or restarts
$SERVICE_NAME. Existing different files are rejected unless --replace and the
fixed confirmation are both provided; replaced files are backed up first.

verify is read-only: it compares content, ownership, modes, effective systemd
state, dependencies, and the local captcha endpoint. It performs no repair.
Both commands refuse to run while the fixed production maintenance lock exists.
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

require_root() {
  [ "$(id -u)" -eq 0 ] || die 'this production control command must run as root'
}

acquire_release_operation_lock() {
  local lock_parent
  require_command flock
  lock_parent="${RELEASE_OPERATION_LOCK%/*}"
  [ -d "$lock_parent" ] && [ ! -L "$lock_parent" ] \
    || die "release operation lock parent is missing or unsafe: $lock_parent"
  if [ -e "$RELEASE_OPERATION_LOCK" ] || [ -L "$RELEASE_OPERATION_LOCK" ]; then
    [ -f "$RELEASE_OPERATION_LOCK" ] && [ ! -L "$RELEASE_OPERATION_LOCK" ] \
      || die "release operation lock is not a regular non-symlink file: $RELEASE_OPERATION_LOCK"
  fi
  exec 7>"$RELEASE_OPERATION_LOCK" \
    || die "cannot open release operation lock: $RELEASE_OPERATION_LOCK"
  log "waiting for exclusive production release operation lock: $RELEASE_OPERATION_LOCK"
  flock -x 7 || die "cannot acquire release operation lock: $RELEASE_OPERATION_LOCK"
  if [ -e "$OFFLINE_WORKER_MARKER" ] || [ -L "$OFFLINE_WORKER_MARKER" ]; then
    die "offline worker marker exists; wait for or investigate the database worker first: $OFFLINE_WORKER_MARKER"
  fi
  log 'exclusive production release operation lock acquired; the kernel releases it when this process exits'
}

assert_no_maintenance_lock() {
  if [ -e "$PRODUCTION_MAINTENANCE_LOCK" ] || [ -L "$PRODUCTION_MAINTENANCE_LOCK" ]; then
    die "production maintenance lock exists; finish or roll back the maintenance window first: $PRODUCTION_MAINTENANCE_LOCK"
  fi
}

unit_state() {
  systemctl is-active "$1" 2>/dev/null || true
}

unit_enablement_state() {
  systemctl is-enabled "$1" 2>/dev/null || true
}

assert_unit_persistently_enabled() {
  local unit_name="$1" enablement_state
  enablement_state="$(unit_enablement_state "$unit_name")"
  [ "$enablement_state" = enabled ] \
    || die "$unit_name must be persistently enabled; current state is ${enablement_state:-unknown}"
}

backend_healthy() {
  local response body http_code
  response="$(
    curl --silent --show-error \
      --connect-timeout 2 \
      --max-time 10 \
      --write-out $'\\n%{http_code}' \
      "$LOCAL_HEALTH_URL" 2>/dev/null
  )" || return 1

  http_code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  [ "$http_code" = 200 ] || return 1
  printf '%s' "$body" \
    | grep -Eq '"code"[[:space:]]*:[[:space:]]*200([[:space:]]*[,}])'
}

assert_assets() {
  local asset
  for asset in \
    "$ASSET_DIR/60-self-heal.conf" \
    "$ASSET_DIR/site-platform-watchdog.service" \
    "$ASSET_DIR/site-platform-watchdog.timer" \
    "$ASSET_DIR/site-platform-watchdog.sh"; do
    [ -f "$asset" ] && [ ! -L "$asset" ] || die "missing or unsafe packaged asset: $asset"
  done
  bash -n "$ASSET_DIR/site-platform-watchdog.sh" \
    || die 'packaged watchdog script failed Bash syntax validation'
}

assert_regular_target_or_missing() {
  local target="$1"
  if [ -e "$target" ] || [ -L "$target" ]; then
    [ -f "$target" ] && [ ! -L "$target" ] \
      || die "target is not a regular non-symlink file: $target"
  fi
}

assert_file_matches() {
  local source="$1" target="$2" expected_mode="$3"
  [ -f "$target" ] && [ ! -L "$target" ] || die "installed file is missing or unsafe: $target"
  cmp -s -- "$source" "$target" || die "installed content differs from packaged asset: $target"
  [ "$(stat -c '%U:%G' "$target")" = 'root:root' ] \
    || die "installed owner must be root:root: $target"
  [ "$(stat -c '%a' "$target")" = "$expected_mode" ] \
    || die "installed mode must be $expected_mode: $target"
}

assert_unit_list_contains() {
  local unit_list=" $1 " property="$2" expected="$3"
  case "$unit_list" in
    *" $expected "*) ;;
    *) die "$SERVICE_NAME effective $property does not include $expected" ;;
  esac
}

assert_unit_list_excludes() {
  local unit_list=" $1 " property="$2" forbidden="$3"
  case "$unit_list" in
    *" $forbidden "*)
      die "$SERVICE_NAME effective $property must not include $forbidden; stop propagation would defeat conservative recovery"
      ;;
  esac
}

verify_impl() {
  local after_units binds_to_units fragment_path requires_units restart_policy

  assert_no_maintenance_lock
  assert_assets
  for command_name in bash cmp curl flock grep sleep stat systemctl systemd-analyze; do
    require_command "$command_name"
  done

  assert_file_matches "$ASSET_DIR/60-self-heal.conf" "$DROPIN_TARGET" 644
  assert_file_matches "$ASSET_DIR/site-platform-watchdog.service" "$WATCHDOG_SERVICE_TARGET" 644
  assert_file_matches "$ASSET_DIR/site-platform-watchdog.timer" "$WATCHDOG_TIMER_TARGET" 644
  assert_file_matches "$ASSET_DIR/site-platform-watchdog.sh" "$WATCHDOG_SCRIPT_TARGET" 755
  bash -n "$WATCHDOG_SCRIPT_TARGET" || die 'installed watchdog failed Bash syntax validation'

  fragment_path="$(systemctl show "$SERVICE_NAME" --property=FragmentPath --value)"
  [ -f "$fragment_path" ] || die "$SERVICE_NAME fragment is not readable: $fragment_path"
  systemd-analyze verify \
    "$fragment_path" \
    "$WATCHDOG_SERVICE_TARGET" \
    "$WATCHDOG_TIMER_TARGET" >/dev/null

  restart_policy="$(systemctl show "$SERVICE_NAME" --property=Restart --value)"
  [ "$restart_policy" = on-failure ] \
    || die "$SERVICE_NAME effective Restart is $restart_policy, expected on-failure"
  after_units="$(systemctl show "$SERVICE_NAME" --property=After --value)"
  assert_unit_list_contains "$after_units" After "$MYSQL_SERVICE_NAME"
  assert_unit_list_contains "$after_units" After "$REDIS_SERVICE_NAME"
  requires_units="$(systemctl show "$SERVICE_NAME" --property=Requires --value)"
  binds_to_units="$(systemctl show "$SERVICE_NAME" --property=BindsTo --value)"
  assert_unit_list_excludes "$requires_units" Requires "$MYSQL_SERVICE_NAME"
  assert_unit_list_excludes "$requires_units" Requires "$REDIS_SERVICE_NAME"
  assert_unit_list_excludes "$binds_to_units" BindsTo "$MYSQL_SERVICE_NAME"
  assert_unit_list_excludes "$binds_to_units" BindsTo "$REDIS_SERVICE_NAME"

  assert_unit_persistently_enabled site-platform-watchdog.timer
  systemctl is-active --quiet site-platform-watchdog.timer \
    || die 'site-platform-watchdog.timer is not active'

  [ "$(unit_state "$MYSQL_SERVICE_NAME")" = active ] \
    || die "$MYSQL_SERVICE_NAME is not active"
  [ "$(unit_state "$REDIS_SERVICE_NAME")" = active ] \
    || die "$REDIS_SERVICE_NAME is not active"
  [ "$(unit_state "$SERVICE_NAME")" = active ] \
    || die "$SERVICE_NAME is not active"
  assert_unit_persistently_enabled "$SERVICE_NAME"
  backend_healthy || die "local captcha health failed: $LOCAL_HEALTH_URL"

  log 'verify passed; no system state was changed'
}

atomic_install() {
  local source="$1" target="$2" mode="$3" temporary
  temporary="${target}.new.$$"
  [ ! -e "$temporary" ] && [ ! -L "$temporary" ] \
    || die "temporary install path already exists: $temporary"
  install -o root -g root -m "$mode" -- "$source" "$temporary"
  mv -f -- "$temporary" "$target"
}

install_impl() {
  local replace="$1" confirmation="$2"
  local before_invocation before_pid after_invocation after_pid
  local backup_dir='' timestamp conflict_count=0 index target source mode fragment_path
  local timer_enablement_before='' timer_was_active=0 changes_started=0 install_complete=0
  local -a sources targets modes states backup_files changed_indices

  require_root
  assert_no_maintenance_lock
  assert_assets
  for command_name in \
    bash cmp cp curl date dirname find flock grep install mv rm sha256sum sleep sort stat systemctl systemd-analyze xargs; do
    require_command "$command_name"
  done

  [ "$(unit_state "$MYSQL_SERVICE_NAME")" = active ] \
    || die "$MYSQL_SERVICE_NAME must be active before install"
  [ "$(unit_state "$REDIS_SERVICE_NAME")" = active ] \
    || die "$REDIS_SERVICE_NAME must be active before install"
  [ "$(unit_state "$SERVICE_NAME")" = active ] \
    || die "$SERVICE_NAME must be active before install"
  assert_unit_persistently_enabled "$SERVICE_NAME"
  backend_healthy || die "local captcha health must pass before install: $LOCAL_HEALTH_URL"

  before_invocation="$(systemctl show "$SERVICE_NAME" --property=InvocationID --value)"
  before_pid="$(systemctl show "$SERVICE_NAME" --property=MainPID --value)"
  [ -n "$before_invocation" ] || die "$SERVICE_NAME has no InvocationID"
  [ -n "$before_pid" ] && [ "$before_pid" != 0 ] || die "$SERVICE_NAME has no running MainPID"

  sources=(
    "$ASSET_DIR/60-self-heal.conf"
    "$ASSET_DIR/site-platform-watchdog.service"
    "$ASSET_DIR/site-platform-watchdog.timer"
    "$ASSET_DIR/site-platform-watchdog.sh"
  )
  targets=(
    "$DROPIN_TARGET"
    "$WATCHDOG_SERVICE_TARGET"
    "$WATCHDOG_TIMER_TARGET"
    "$WATCHDOG_SCRIPT_TARGET"
  )
  modes=(644 644 644 755)
  states=()
  backup_files=()
  changed_indices=()

  for index in "${!targets[@]}"; do
    source="${sources[$index]}"
    target="${targets[$index]}"
    mode="${modes[$index]}"
    assert_regular_target_or_missing "$target"
    if [ ! -e "$target" ]; then
      states[$index]='missing'
      changed_indices+=("$index")
    elif cmp -s -- "$source" "$target" \
        && [ "$(stat -c '%U:%G' "$target")" = root:root ] \
        && [ "$(stat -c '%a' "$target")" = "$mode" ]; then
      states[$index]='same'
    else
      states[$index]='different'
      changed_indices+=("$index")
      conflict_count=$((conflict_count + 1))
    fi
  done

  if [ "$conflict_count" -gt 0 ]; then
    [ "$replace" = 1 ] \
      || die "$conflict_count existing self-heal file(s) differ; rerun only after review with --replace and --confirm $REPLACE_CONFIRMATION"
    [ "$confirmation" = "$REPLACE_CONFIRMATION" ] \
      || die "replacement requires --confirm $REPLACE_CONFIRMATION"

    timestamp="$(date '+%Y%m%d-%H%M%S')"
    install -d -o root -g root -m 0700 -- "$BACKUP_ROOT"
    backup_dir="$BACKUP_ROOT/replace-${timestamp}-$$"
    [ ! -e "$backup_dir" ] || die "backup path already exists: $backup_dir"
    install -d -o root -g root -m 0700 -- "$backup_dir"
    : > "$backup_dir/targets.tsv"

    for index in "${!targets[@]}"; do
      [ "${states[$index]}" = different ] || continue
      target="${targets[$index]}"
      backup_files[$index]="$backup_dir/$(printf '%02d-%s' "$index" "${target##*/}")"
      cp -a -- "$target" "${backup_files[$index]}"
      printf '%s\t%s\n' "$target" "${backup_files[$index]}" >> "$backup_dir/targets.tsv"
    done
    (
      cd "$backup_dir"
      find . -type f ! -name SHA256SUMS -print0 \
        | LC_ALL=C sort -z \
        | xargs -0 sha256sum > SHA256SUMS
    )
    log "different installed files backed up before replacement: $backup_dir"
  elif [ "$replace" = 1 ] || [ -n "$confirmation" ]; then
    [ -z "$confirmation" ] || [ "$confirmation" = "$REPLACE_CONFIRMATION" ] \
      || die "unexpected confirmation value"
    log 'no different installed files found; replacement authorization was not needed'
  fi

  timer_enablement_before="$(unit_enablement_state site-platform-watchdog.timer)"
  systemctl is-active --quiet site-platform-watchdog.timer && timer_was_active=1 || true

  on_install_exit() {
    local rc=$? restore_index restore_target restore_file
    trap - EXIT ERR INT TERM HUP
    [ "$install_complete" = 1 ] && [ "$rc" = 0 ] && return 0
    [ "$rc" -ne 0 ] || rc=1
    set +e
    if [ "$changes_started" = 1 ]; then
      systemctl stop site-platform-watchdog.timer site-platform-watchdog.service >/dev/null 2>&1 || true
      for restore_index in "${changed_indices[@]}"; do
        restore_target="${targets[$restore_index]}"
        case "${states[$restore_index]}" in
          missing)
            rm -f -- "$restore_target"
            ;;
          different)
            restore_file="${backup_files[$restore_index]:-}"
            if [ -n "$restore_file" ] && [ -f "$restore_file" ]; then
              cp -a -- "$restore_file" "${restore_target}.restore.$$"
              mv -f -- "${restore_target}.restore.$$" "$restore_target"
            fi
            ;;
        esac
      done
      systemctl daemon-reload >/dev/null 2>&1 || true
      case "$timer_enablement_before" in
        enabled)
          systemctl enable site-platform-watchdog.timer >/dev/null 2>&1 || true
          ;;
        enabled-runtime)
          systemctl disable site-platform-watchdog.timer >/dev/null 2>&1 || true
          systemctl enable --runtime site-platform-watchdog.timer >/dev/null 2>&1 || true
          ;;
        *)
          systemctl disable site-platform-watchdog.timer >/dev/null 2>&1 || true
          systemctl disable --runtime site-platform-watchdog.timer >/dev/null 2>&1 || true
          ;;
      esac
      if [ "$timer_was_active" = 1 ]; then
        systemctl start site-platform-watchdog.timer >/dev/null 2>&1 || true
      fi
    fi
    printf '[self-heal-control][ERROR] install failed; attempted to restore the previous self-heal files without touching %s\n' \
      "$SERVICE_NAME" >&2
    exit "$rc"
  }
  trap on_install_exit EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM HUP

  assert_no_maintenance_lock
  if [ "${#changed_indices[@]}" -gt 0 ]; then
    changes_started=1
    systemctl stop site-platform-watchdog.timer site-platform-watchdog.service >/dev/null 2>&1 || true

    [ -d "$SYSTEMD_ROOT" ] || die "systemd root is missing: $SYSTEMD_ROOT"
    [ -d "$LOCAL_SBIN_ROOT" ] || die "local sbin directory is missing: $LOCAL_SBIN_ROOT"
    install -d -o root -g root -m 0755 -- "$(dirname "$DROPIN_TARGET")"

    for index in "${changed_indices[@]}"; do
      atomic_install "${sources[$index]}" "${targets[$index]}" "${modes[$index]}"
    done
  fi

  changes_started=1
  systemctl daemon-reload
  fragment_path="$(systemctl show "$SERVICE_NAME" --property=FragmentPath --value)"
  [ -f "$fragment_path" ] || die "$SERVICE_NAME fragment is not readable: $fragment_path"
  systemd-analyze verify \
    "$fragment_path" \
    "$WATCHDOG_SERVICE_TARGET" \
    "$WATCHDOG_TIMER_TARGET" >/dev/null

  assert_no_maintenance_lock
  systemctl enable site-platform-watchdog.timer
  systemctl start site-platform-watchdog.timer

  after_invocation="$(systemctl show "$SERVICE_NAME" --property=InvocationID --value)"
  after_pid="$(systemctl show "$SERVICE_NAME" --property=MainPID --value)"
  [ "$after_invocation" = "$before_invocation" ] \
    || die "$SERVICE_NAME InvocationID changed during install"
  [ "$after_pid" = "$before_pid" ] \
    || die "$SERVICE_NAME MainPID changed during install"

  verify_impl
  install_complete=1
  trap - EXIT INT TERM HUP
  log "install passed; $SERVICE_NAME was neither stopped nor restarted"
}

action="${1:-}"
[ -n "$action" ] || { usage >&2; exit 2; }
shift

case "$action" in
  install)
    replace=0
    confirmation=''
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --replace)
          replace=1
          ;;
        --confirm)
          [ "$#" -ge 2 ] || die '--confirm requires a value'
          confirmation="$2"
          shift
          ;;
        --help|-h)
          usage
          exit 0
          ;;
        *)
          die "unknown install argument: $1"
          ;;
      esac
      shift
    done
    require_root
    acquire_release_operation_lock
    install_impl "$replace" "$confirmation"
    ;;
  verify)
    [ "$#" -eq 0 ] || die 'verify accepts no arguments'
    require_root
    acquire_release_operation_lock
    verify_impl
    ;;
  --help|-h|help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
