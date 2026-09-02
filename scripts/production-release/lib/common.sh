#!/usr/bin/env bash

set -Eeuo pipefail
IFS=$'\n\t'
umask 077

PRODUCTION_RELEASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
BASELINE_DIR="$PRODUCTION_RELEASE_DIR/baseline"
EXPECTED_TABLES_FILE="$BASELINE_DIR/expected-tables.txt"
EXPECTED_MARKERS_FILE="$BASELINE_DIR/expected-migration-markers.txt"
MIGRATION_PLAN_FILE="$BASELINE_DIR/migration-plan.tsv"

SERVICE_NAME="${SERVICE_NAME:-site-platform.service}"
SERVICE_USER="${SERVICE_USER:-site-platform}"
SERVICE_GROUP="${SERVICE_GROUP:-site-platform}"
NGINX_USER="${NGINX_USER:-www-data}"
NGINX_GROUP="${NGINX_GROUP:-www-data}"
APP_ROOT="${APP_ROOT:-/opt/site-platform}"
JAR_LINK="${JAR_LINK:-$APP_ROOT/backend/site-platform.jar}"
WEB_LINK="${WEB_LINK:-$APP_ROOT/frontend}"
UPLOAD_LINK="${UPLOAD_LINK:-$APP_ROOT/uploads}"
ENV_FILE="${ENV_FILE:-/etc/site-platform/site-platform.env}"
NGINX_ROOT="${NGINX_ROOT:-/etc/nginx}"
MYSQL_DATABASE="${MYSQL_DATABASE:-dianxinyun}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"
JAVA_BIN="${JAVA_BIN:-/usr/bin/java}"
HEALTH_URL="${HEALTH_URL:-https://zhihuiyz.xyz/api/v1/auth/captcha}"
LOCAL_HEALTH_URL="${LOCAL_HEALTH_URL:-http://127.0.0.1:8080/api/v1/auth/captcha}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://zhihuiyz.xyz}"
BACKEND_LOCAL_HEALTH_ATTEMPTS="${BACKEND_LOCAL_HEALTH_ATTEMPTS:-45}"
BACKEND_PUBLIC_HEALTH_ATTEMPTS="${BACKEND_PUBLIC_HEALTH_ATTEMPTS:-15}"
BACKEND_HEALTH_INTERVAL_SECONDS="${BACKEND_HEALTH_INTERVAL_SECONDS:-2}"
DRY_RUN="${DRY_RUN:-0}"
PRODUCTION_MAINTENANCE_LOCK_FILE='/etc/site-platform/maintenance.lock'
MAINTENANCE_LOCK_FILE="${MAINTENANCE_LOCK_FILE:-$PRODUCTION_MAINTENANCE_LOCK_FILE}"
PRODUCTION_WATCHDOG_COORDINATION_LOCK_FILE='/run/site-platform-watchdog.lock'
WATCHDOG_COORDINATION_LOCK_FILE="${WATCHDOG_COORDINATION_LOCK_FILE:-$PRODUCTION_WATCHDOG_COORDINATION_LOCK_FILE}"
WATCHDOG_COORDINATION_LOCK_HELD=0
PRODUCTION_RELEASE_OPERATION_LOCK_FILE='/run/site-platform-release.lock'
RELEASE_OPERATION_LOCK_FILE="${RELEASE_OPERATION_LOCK_FILE:-$PRODUCTION_RELEASE_OPERATION_LOCK_FILE}"
RELEASE_OPERATION_LOCK_HELD=0
PRODUCTION_OFFLINE_WORKER_MARKER_FILE='/run/site-platform-offline-worker.state'
OFFLINE_WORKER_MARKER_FILE="${OFFLINE_WORKER_MARKER_FILE:-$PRODUCTION_OFFLINE_WORKER_MARKER_FILE}"
PRODUCTION_OFFLINE_WORKER_COORDINATION_LOCK_FILE='/run/site-platform-offline-worker.lock'
OFFLINE_WORKER_COORDINATION_LOCK_FILE="${OFFLINE_WORKER_COORDINATION_LOCK_FILE:-$PRODUCTION_OFFLINE_WORKER_COORDINATION_LOCK_FILE}"
OFFLINE_WORKER_COORDINATION_LOCK_HELD=0

log() { printf '[production-release] %s\n' "$*" >&2; }
warn() { printf '[production-release][WARN] %s\n' "$*" >&2; }
die() { printf '[production-release][ERROR] %s\n' "$*" >&2; exit 1; }

usage_error() { die "$1；使用 --help 查看参数"; }

require_commands() {
  local command_name
  for command_name in "$@"; do
    command -v "$command_name" >/dev/null 2>&1 || die "缺少命令：$command_name"
  done
}

require_root() {
  [ "$(id -u)" -eq 0 ] || die '该写操作必须由 root 执行'
}

require_confirmation() {
  local expected="$1" actual="${2:-}"
  [ "$DRY_RUN" = 1 ] && return 0
  [ "$actual" = "$expected" ] || die "拒绝写入：必须显式提供确认语 $expected"
}

validate_database_name() {
  case "$MYSQL_DATABASE" in
    ''|*[!A-Za-z0-9_]*) die "MYSQL_DATABASE 只允许字母、数字和下划线：$MYSQL_DATABASE" ;;
  esac
}

assert_safe_absolute_path() {
  local path="$1" label="$2"
  case "$path" in
    /*) ;;
    *) die "$label 必须是绝对路径：$path" ;;
  esac
  case "$path" in
    /|/bin|/boot|/dev|/etc|/home|/lib|/lib64|/opt|/proc|/root|/run|/sbin|/srv|/sys|/tmp|/usr|/var)
      die "$label 过于宽泛，拒绝使用：$path"
      ;;
  esac
}

production_release_self_test_context() {
  local source_file
  [ "${PRODUCTION_RELEASE_SELF_TEST:-0}" = 1 ] || return 1
  for source_file in "${BASH_SOURCE[@]}"; do
    if [ "${source_file##*/}" = '99-self-test.sh' ]; then
      return 0
    fi
  done
  return 1
}

validate_maintenance_lock_path() {
  if [ "$MAINTENANCE_LOCK_FILE" != "$PRODUCTION_MAINTENANCE_LOCK_FILE" ] \
      && ! production_release_self_test_context; then
    warn "生产维护锁路径不可覆盖：$MAINTENANCE_LOCK_FILE"
    return 1
  fi
  case "$MAINTENANCE_LOCK_FILE" in
    /*) ;;
    *) warn "维护锁必须是绝对路径：$MAINTENANCE_LOCK_FILE"; return 1 ;;
  esac
  case "$MAINTENANCE_LOCK_FILE" in
    *$'\n'*|*$'\r'*) warn '维护锁路径含换行符'; return 1 ;;
  esac
}

validate_watchdog_coordination_lock_path() {
  if [ "$WATCHDOG_COORDINATION_LOCK_FILE" != "$PRODUCTION_WATCHDOG_COORDINATION_LOCK_FILE" ] \
      && ! production_release_self_test_context; then
    warn "watchdog 协调锁路径不可覆盖：$WATCHDOG_COORDINATION_LOCK_FILE"
    return 1
  fi
  case "$WATCHDOG_COORDINATION_LOCK_FILE" in
    /*) ;;
    *) warn "watchdog 协调锁必须是绝对路径：$WATCHDOG_COORDINATION_LOCK_FILE"; return 1 ;;
  esac
  case "$WATCHDOG_COORDINATION_LOCK_FILE" in
    *$'\n'*|*$'\r'*) warn 'watchdog 协调锁路径含换行符'; return 1 ;;
  esac
}

acquire_watchdog_coordination_lock() {
  local lock_parent
  [ "$WATCHDOG_COORDINATION_LOCK_HELD" = 0 ] \
    || { warn '当前进程已经持有 watchdog 协调锁'; return 1; }
  validate_watchdog_coordination_lock_path || return 1
  lock_parent="${WATCHDOG_COORDINATION_LOCK_FILE%/*}"
  [ -d "$lock_parent" ] && [ ! -L "$lock_parent" ] \
    || { warn "watchdog 协调锁父目录不存在或是软链：$lock_parent"; return 1; }
  if [ -e "$WATCHDOG_COORDINATION_LOCK_FILE" ] || [ -L "$WATCHDOG_COORDINATION_LOCK_FILE" ]; then
    [ -f "$WATCHDOG_COORDINATION_LOCK_FILE" ] && [ ! -L "$WATCHDOG_COORDINATION_LOCK_FILE" ] \
      || { warn "watchdog 协调锁不是普通文件或是软链：$WATCHDOG_COORDINATION_LOCK_FILE"; return 1; }
  fi
  exec 8>"$WATCHDOG_COORDINATION_LOCK_FILE" || {
    warn "无法打开 watchdog 协调锁：$WATCHDOG_COORDINATION_LOCK_FILE"
    return 1
  }
  if ! flock -x 8; then
    exec 8>&-
    warn "无法取得 watchdog 协调锁：$WATCHDOG_COORDINATION_LOCK_FILE"
    return 1
  fi
  WATCHDOG_COORDINATION_LOCK_HELD=1
}

release_watchdog_coordination_lock() {
  [ "$WATCHDOG_COORDINATION_LOCK_HELD" = 1 ] \
    || { warn '当前进程没有持有 watchdog 协调锁'; return 1; }
  flock -u 8 || return 1
  exec 8>&-
  WATCHDOG_COORDINATION_LOCK_HELD=0
}

validate_release_operation_lock_path() {
  if [ "$RELEASE_OPERATION_LOCK_FILE" != "$PRODUCTION_RELEASE_OPERATION_LOCK_FILE" ] \
      && ! production_release_self_test_context; then
    warn "生产发布操作锁路径不可覆盖：$RELEASE_OPERATION_LOCK_FILE"
    return 1
  fi
  case "$RELEASE_OPERATION_LOCK_FILE" in
    /*) ;;
    *) warn "生产发布操作锁必须是绝对路径：$RELEASE_OPERATION_LOCK_FILE"; return 1 ;;
  esac
  case "$RELEASE_OPERATION_LOCK_FILE" in
    *$'\n'*|*$'\r'*) warn '生产发布操作锁路径含换行符'; return 1 ;;
  esac
}

validate_offline_worker_marker_path() {
  if [ "$OFFLINE_WORKER_MARKER_FILE" != "$PRODUCTION_OFFLINE_WORKER_MARKER_FILE" ] \
      && ! production_release_self_test_context; then
    warn "离线 worker 标记路径不可覆盖：$OFFLINE_WORKER_MARKER_FILE"
    return 1
  fi
  case "$OFFLINE_WORKER_MARKER_FILE" in
    /*) ;;
    *) warn "离线 worker 标记必须是绝对路径：$OFFLINE_WORKER_MARKER_FILE"; return 1 ;;
  esac
  case "$OFFLINE_WORKER_MARKER_FILE" in
    *$'\n'*|*$'\r'*) warn '离线 worker 标记路径含换行符'; return 1 ;;
  esac
}

validate_offline_worker_coordination_lock_path() {
  if [ "$OFFLINE_WORKER_COORDINATION_LOCK_FILE" != "$PRODUCTION_OFFLINE_WORKER_COORDINATION_LOCK_FILE" ] \
      && ! production_release_self_test_context; then
    warn "离线 worker 协调锁路径不可覆盖：$OFFLINE_WORKER_COORDINATION_LOCK_FILE"
    return 1
  fi
  case "$OFFLINE_WORKER_COORDINATION_LOCK_FILE" in
    /*) ;;
    *) warn "离线 worker 协调锁必须是绝对路径：$OFFLINE_WORKER_COORDINATION_LOCK_FILE"; return 1 ;;
  esac
  case "$OFFLINE_WORKER_COORDINATION_LOCK_FILE" in
    *$'\n'*|*$'\r'*) warn '离线 worker 协调锁路径含换行符'; return 1 ;;
  esac
}

acquire_offline_worker_coordination_lock() {
  local lock_parent
  [ "$OFFLINE_WORKER_COORDINATION_LOCK_HELD" = 0 ] \
    || { warn '当前进程已经持有离线 worker 协调锁'; return 1; }
  validate_offline_worker_coordination_lock_path || return 1
  command -v flock >/dev/null 2>&1 \
    || { warn '缺少离线 worker 协调所需命令：flock'; return 1; }
  lock_parent="${OFFLINE_WORKER_COORDINATION_LOCK_FILE%/*}"
  [ -d "$lock_parent" ] && [ ! -L "$lock_parent" ] \
    || { warn "离线 worker 协调锁父目录不存在或是软链：$lock_parent"; return 1; }
  if [ -e "$OFFLINE_WORKER_COORDINATION_LOCK_FILE" ] \
      || [ -L "$OFFLINE_WORKER_COORDINATION_LOCK_FILE" ]; then
    [ -f "$OFFLINE_WORKER_COORDINATION_LOCK_FILE" ] \
      && [ ! -L "$OFFLINE_WORKER_COORDINATION_LOCK_FILE" ] \
      || { warn "离线 worker 协调锁不是普通文件或是软链：$OFFLINE_WORKER_COORDINATION_LOCK_FILE"; return 1; }
  fi
  exec 6>"$OFFLINE_WORKER_COORDINATION_LOCK_FILE" || {
    warn "无法打开离线 worker 协调锁：$OFFLINE_WORKER_COORDINATION_LOCK_FILE"
    return 1
  }
  if ! flock -x 6; then
    exec 6>&-
    warn "无法取得离线 worker 协调锁：$OFFLINE_WORKER_COORDINATION_LOCK_FILE"
    return 1
  fi
  OFFLINE_WORKER_COORDINATION_LOCK_HELD=1
}

release_offline_worker_coordination_lock() {
  [ "$OFFLINE_WORKER_COORDINATION_LOCK_HELD" = 1 ] \
    || { warn '当前进程没有持有离线 worker 协调锁'; return 1; }
  flock -u 6 || return 1
  exec 6>&-
  OFFLINE_WORKER_COORDINATION_LOCK_HELD=0
}

assert_no_offline_worker() {
  validate_offline_worker_marker_path || return 1
  if [ ! -e "$OFFLINE_WORKER_MARKER_FILE" ] \
      && [ ! -L "$OFFLINE_WORKER_MARKER_FILE" ]; then
    return 0
  fi
  load_offline_worker_marker || return 1
  if [ "$OFFLINE_WORKER_MARKER_STATE" = SUBMITTING ]; then
    if reconcile_submitting_offline_worker_marker "$OFFLINE_WORKER_MARKER_UNIT"; then
      return 0
    fi
  fi
  warn "离线 worker 尚未安全闭合；禁止并发生产写操作：state=$OFFLINE_WORKER_MARKER_STATE unit=$OFFLINE_WORKER_MARKER_UNIT marker=$OFFLINE_WORKER_MARKER_FILE"
  return 1
}

load_offline_worker_marker() {
  local marker_stat marker_state marker_unit_line extra_line=''
  OFFLINE_WORKER_MARKER_STATE=''
  OFFLINE_WORKER_MARKER_UNIT=''
  validate_offline_worker_marker_path || return 1
  [ -f "$OFFLINE_WORKER_MARKER_FILE" ] && [ ! -L "$OFFLINE_WORKER_MARKER_FILE" ] \
    || { warn "离线 worker 标记不是普通文件或是软链：$OFFLINE_WORKER_MARKER_FILE"; return 1; }
  marker_stat="$(stat_mode_uid_gid_links "$OFFLINE_WORKER_MARKER_FILE")" || {
    warn "无法读取离线 worker 标记属性：$OFFLINE_WORKER_MARKER_FILE"
    return 1
  }
  [ "$marker_stat" = '600:0:0:1' ] || {
    warn "离线 worker 标记权限/属主/硬链数必须是 600:root:root:1，当前为 $marker_stat"
    return 1
  }
  {
    if ! IFS= read -r marker_state; then
      warn '离线 worker 标记缺少状态字段'
      return 1
    fi
    if ! IFS= read -r marker_unit_line; then
      warn '离线 worker 标记缺少 unit 字段'
      return 1
    fi
    if IFS= read -r extra_line; then
      warn '离线 worker 标记字段数量非法'
      return 1
    fi
  } < "$OFFLINE_WORKER_MARKER_FILE"
  case "$marker_state" in
    STATE=SUBMITTING) OFFLINE_WORKER_MARKER_STATE='SUBMITTING' ;;
    STATE=RUNNING) OFFLINE_WORKER_MARKER_STATE='RUNNING' ;;
    *) warn "离线 worker 标记状态非法：$marker_state"; return 1 ;;
  esac
  case "$marker_unit_line" in
    UNIT_NAME=*) OFFLINE_WORKER_MARKER_UNIT="${marker_unit_line#UNIT_NAME=}" ;;
    *) warn '离线 worker 标记 unit 字段非法'; return 1 ;;
  esac
  case "$OFFLINE_WORKER_MARKER_UNIT" in
    ''|*[!A-Za-z0-9_.@:-]*)
      warn "离线 worker 标记中的 unit 名非法：$OFFLINE_WORKER_MARKER_UNIT"
      return 1
      ;;
  esac
}

offline_worker_unit_is_live() {
  local unit_name="$1" properties load_state active_state sub_state main_pid unit_job
  OFFLINE_WORKER_UNIT_LOAD_STATE=''
  OFFLINE_WORKER_UNIT_ACTIVE_STATE=''
  OFFLINE_WORKER_UNIT_MAIN_PID=''
  OFFLINE_WORKER_UNIT_JOB=''
  case "$unit_name" in
    ''|*[!A-Za-z0-9_.@:-]*) warn "离线 worker unit 名非法：$unit_name"; return 2 ;;
  esac
  if ! properties="$(
    systemctl show "${unit_name}.service" \
      --property=LoadState \
      --property=ActiveState \
      --property=SubState \
      --property=MainPID \
      --property=Job 2>/dev/null
  )"; then
    warn "无法确认离线 worker unit 状态：${unit_name}.service"
    return 2
  fi
  load_state="$(printf '%s\n' "$properties" | awk -F= '$1 == "LoadState" {print $2}')"
  active_state="$(printf '%s\n' "$properties" | awk -F= '$1 == "ActiveState" {print $2}')"
  sub_state="$(printf '%s\n' "$properties" | awk -F= '$1 == "SubState" {print $2}')"
  main_pid="$(printf '%s\n' "$properties" | awk -F= '$1 == "MainPID" {print $2}')"
  unit_job="$(printf '%s\n' "$properties" | awk -F= '$1 == "Job" {print $2}')"
  OFFLINE_WORKER_UNIT_LOAD_STATE="$load_state"
  OFFLINE_WORKER_UNIT_ACTIVE_STATE="$active_state"
  OFFLINE_WORKER_UNIT_MAIN_PID="$main_pid"
  OFFLINE_WORKER_UNIT_JOB="$unit_job"
  case "$load_state" in
    loaded|not-found) ;;
    *)
      warn "离线 worker unit LoadState 不明确：unit=$unit_name load=${load_state:-missing}"
      return 2
      ;;
  esac
  case "$unit_job" in
    ''|0) ;;
    *) return 0 ;;
  esac
  case "$active_state" in
    active|activating|reloading|deactivating) return 0 ;;
  esac
  case "$main_pid" in
    ''|*[!0-9]*)
      warn "离线 worker unit MainPID 非法：unit=$unit_name value=${main_pid:-missing}"
      return 2
      ;;
    0) ;;
    *) return 0 ;;
  esac
  case "$active_state" in
    # loaded + inactive/failed 只表示当前没有活动进程，仍必须留给 06 在
    # 精确数据库复核后处理；自动闭合路径还会额外要求 LoadState=not-found。
    inactive|failed) return 1 ;;
    *)
      warn "离线 worker unit 状态不明确：unit=$unit_name load=${load_state:-missing} active=${active_state:-missing} sub=${sub_state:-missing}"
      return 2
      ;;
  esac
}

remove_offline_worker_marker_if_matches() {
  local expected_unit="$1" expected_state="$2"
  [ "$RELEASE_OPERATION_LOCK_HELD" = 1 ] \
    || { warn '清理离线 worker 标记前必须持有生产发布操作锁'; return 1; }
  [ "$OFFLINE_WORKER_COORDINATION_LOCK_HELD" = 1 ] \
    || { warn '清理离线 worker 标记前必须持有离线 worker 协调锁'; return 1; }
  load_offline_worker_marker || return 1
  [ "$OFFLINE_WORKER_MARKER_UNIT" = "$expected_unit" ] \
    || { warn '拒绝清理与预期 unit 不匹配的离线 worker 标记'; return 1; }
  [ "$OFFLINE_WORKER_MARKER_STATE" = "$expected_state" ] \
    || { warn "拒绝清理状态不是 $expected_state 的离线 worker 标记"; return 1; }
  rm -- "$OFFLINE_WORKER_MARKER_FILE" || return 1
  [ ! -e "$OFFLINE_WORKER_MARKER_FILE" ] && [ ! -L "$OFFLINE_WORKER_MARKER_FILE" ] \
    || { warn '离线 worker 标记删除后仍存在'; return 1; }
  log "已安全清理匹配的离线 worker 标记：unit=$expected_unit state=$expected_state"
}

reconcile_submitting_offline_worker_marker() {
  local expected_unit="$1" live_rc result=1
  acquire_offline_worker_coordination_lock || return 1
  if ! load_offline_worker_marker; then
    release_offline_worker_coordination_lock || true
    return 1
  fi
  if [ "$OFFLINE_WORKER_MARKER_STATE" != SUBMITTING ]; then
    warn '离线 worker 已进入 RUNNING，不能按提交失败自动清理'
    release_offline_worker_coordination_lock || true
    return 1
  fi
  if [ "$OFFLINE_WORKER_MARKER_UNIT" != "$expected_unit" ]; then
    warn '离线 worker 标记与预期 unit 不匹配'
    release_offline_worker_coordination_lock || true
    return 1
  fi
  if offline_worker_unit_is_live "$expected_unit"; then
    live_rc=0
  else
    live_rc=$?
  fi
  case "$live_rc" in
    0)
      warn "离线 worker unit 仍在运行或启动中，保留标记：${expected_unit}.service"
      ;;
    1)
      if [ "$OFFLINE_WORKER_UNIT_LOAD_STATE" != not-found ]; then
        warn "离线 worker unit 仍已加载；即使当前无 PID 也不能按未提交自动清理：${expected_unit}.service"
      elif remove_offline_worker_marker_if_matches "$expected_unit" SUBMITTING; then
        result=0
      fi
      ;;
    *)
      warn "无法证明离线 worker 未启动，保留标记：${expected_unit}.service"
      ;;
  esac
  release_offline_worker_coordination_lock || return 1
  return "$result"
}

create_offline_worker_marker() {
  local unit_name="$1" temporary
  [ "$RELEASE_OPERATION_LOCK_HELD" = 1 ] \
    || { warn '创建离线 worker 标记前必须持有生产发布操作锁'; return 1; }
  case "$unit_name" in
    ''|*[!A-Za-z0-9_.@:-]*) warn "离线 worker unit 名非法：$unit_name"; return 1 ;;
  esac
  assert_no_offline_worker || return 1
  temporary="$(mktemp "${OFFLINE_WORKER_MARKER_FILE}.tmp.XXXXXX")" || return 1
  if ! printf 'STATE=SUBMITTING\nUNIT_NAME=%s\n' "$unit_name" > "$temporary"; then
    rm -f -- "$temporary"
    warn '无法写入离线 worker 临时标记'
    return 1
  fi
  if ! chmod 0600 "$temporary"; then
    rm -f -- "$temporary"
    warn '无法设置离线 worker 临时标记权限'
    return 1
  fi
  if ! chown root:root "$temporary"; then
    rm -f -- "$temporary"
    warn '无法设置离线 worker 临时标记属主'
    return 1
  fi
  if ! ln -- "$temporary" "$OFFLINE_WORKER_MARKER_FILE"; then
    rm -f -- "$temporary"
    warn "无法原子创建离线 worker 标记：$OFFLINE_WORKER_MARKER_FILE"
    return 1
  fi
  if ! rm -- "$temporary"; then
    rm -f -- "$OFFLINE_WORKER_MARKER_FILE" "$temporary"
    warn "离线 worker 临时硬链清理失败，拒绝继续：$temporary"
    return 1
  fi
  load_offline_worker_marker || return 1
  [ "$OFFLINE_WORKER_MARKER_STATE" = SUBMITTING ] \
    && [ "$OFFLINE_WORKER_MARKER_UNIT" = "$unit_name" ] \
    || { warn '新建离线 worker 标记自检失败'; return 1; }
  log "已创建离线 worker 提交中标记：unit=$unit_name"
}

acquire_release_operation_lock() {
  local lock_parent offline_policy="${1:-DENY_OFFLINE_WORKER}"
  case "$offline_policy" in
    DENY_OFFLINE_WORKER|ALLOW_OFFLINE_WORKER_RECOVERY) ;;
    *) warn "未知离线 worker 锁策略：$offline_policy"; return 1 ;;
  esac
  [ "$RELEASE_OPERATION_LOCK_HELD" = 0 ] \
    || { warn '当前进程已经持有生产发布操作锁'; return 1; }
  validate_release_operation_lock_path || return 1
  command -v flock >/dev/null 2>&1 \
    || { warn '缺少生产发布互斥所需命令：flock'; return 1; }
  lock_parent="${RELEASE_OPERATION_LOCK_FILE%/*}"
  [ -d "$lock_parent" ] && [ ! -L "$lock_parent" ] \
    || { warn "生产发布操作锁父目录不存在或是软链：$lock_parent"; return 1; }
  if [ -e "$RELEASE_OPERATION_LOCK_FILE" ] || [ -L "$RELEASE_OPERATION_LOCK_FILE" ]; then
    [ -f "$RELEASE_OPERATION_LOCK_FILE" ] && [ ! -L "$RELEASE_OPERATION_LOCK_FILE" ] \
      || { warn "生产发布操作锁不是普通文件或是软链：$RELEASE_OPERATION_LOCK_FILE"; return 1; }
  fi
  exec 7>"$RELEASE_OPERATION_LOCK_FILE" || {
    warn "无法打开生产发布操作锁：$RELEASE_OPERATION_LOCK_FILE"
    return 1
  }
  log "等待取得生产发布全程互斥锁：$RELEASE_OPERATION_LOCK_FILE"
  if ! flock -x 7; then
    exec 7>&-
    warn "无法取得生产发布操作锁：$RELEASE_OPERATION_LOCK_FILE"
    return 1
  fi
  RELEASE_OPERATION_LOCK_HELD=1
  log '已取得生产发布全程互斥锁；本进程退出时由内核自动释放'
  if [ "$offline_policy" = DENY_OFFLINE_WORKER ]; then
    assert_no_offline_worker || return 1
  fi
}

assert_release_window_reboot_safe() {
  local automatic_reboot
  if ! automatic_reboot="$(
    LC_ALL=C apt-config dump 2>/dev/null \
      | awk '$1 == "Unattended-Upgrade::Automatic-Reboot" {
          value = $2
        }
        END {
          gsub(/[";]/, "", value)
          print tolower(value)
        }'
  )"; then
    warn '无法可靠读取 APT 自动重启配置，生产维护窗口拒绝开始'
    return 1
  fi
  case "$automatic_reboot" in
    true|1|yes|on)
      warn 'APT unattended-upgrades 已启用 Automatic-Reboot；停机升级可能被主机重启打断'
      return 1
      ;;
  esac
  if [ -e /run/reboot-required ] || [ -L /run/reboot-required ]; then
    warn '系统已有 /run/reboot-required；请先在发布窗口外重启并重新完成预检'
    return 1
  fi
  log '发布重启门禁通过：APT 未启用自动重启，且当前没有 reboot-required'
}

assert_service_boot_enabled() {
  local enabled_state
  enabled_state="$(systemctl is-enabled "$SERVICE_NAME" 2>/dev/null || true)"
  [ "$enabled_state" = enabled ] \
    || { warn "$SERVICE_NAME 开机自启状态必须是 enabled，当前为 ${enabled_state:-unknown}"; return 1; }
}

assert_service_boot_disabled() {
  local enabled_state
  enabled_state="$(systemctl is-enabled "$SERVICE_NAME" 2>/dev/null || true)"
  [ "$enabled_state" = disabled ] \
    || { warn "$SERVICE_NAME 维护期必须是 disabled，当前为 ${enabled_state:-unknown}"; return 1; }
}

maintenance_lock_present() {
  [ -e "$MAINTENANCE_LOCK_FILE" ] || [ -L "$MAINTENANCE_LOCK_FILE" ]
}

normalize_maintenance_backup_dir() {
  local backup_dir="$1" parent_dir base_name resolved_parent
  case "$backup_dir" in
    /*) ;;
    *) warn "维护锁绑定的备份目录必须是绝对路径：$backup_dir"; return 1 ;;
  esac
  case "$backup_dir" in
    *$'\n'*|*$'\r'*) warn '维护锁绑定的备份目录含换行符'; return 1 ;;
  esac
  base_name="${backup_dir##*/}"
  parent_dir="${backup_dir%/*}"
  [ -n "$parent_dir" ] || parent_dir='/'
  case "$base_name" in
    ''|.|..) warn "维护锁绑定的备份目录非法：$backup_dir"; return 1 ;;
  esac
  if [ -e "$backup_dir" ] || [ -L "$backup_dir" ]; then
    [ -d "$backup_dir" ] && [ ! -L "$backup_dir" ] \
      || { warn "维护锁绑定的备份目录不是普通目录或是软链：$backup_dir"; return 1; }
  fi
  [ -d "$parent_dir" ] || { warn "备份目录父目录不存在：$parent_dir"; return 1; }
  [ ! -L "$parent_dir" ] || { warn "备份目录父目录不能是软链：$parent_dir"; return 1; }
  resolved_parent="$(cd "$parent_dir" && pwd -P)" || return 1
  case "$resolved_parent/$base_name" in
    /|/bin|/boot|/dev|/etc|/home|/lib|/lib64|/opt|/proc|/root|/run|/sbin|/srv|/sys|/tmp|/usr|/var)
      warn "维护锁绑定的备份目录过于宽泛：$backup_dir"
      return 1
      ;;
  esac
  printf '%s\n' "$resolved_parent/$base_name"
}

sha256_text() {
  printf '%s' "$1" | sha256sum | awk '{print $1}'
}

stat_mode_uid_gid_links() {
  local path="$1"
  if stat -c '%a:%u:%g:%h' -- "$path" >/dev/null 2>&1; then
    stat -c '%a:%u:%g:%h' -- "$path"
  else
    stat -f '%Lp:%u:%g:%l' -- "$path"
  fi
}

assert_maintenance_lock() {
  local backup_dir="$1" normalized_backup expected_service_hash expected_backup_hash
  local lock_stat created_at creator_pid key_count
  validate_maintenance_lock_path || return 1
  maintenance_lock_present || { warn "缺少生产维护锁：$MAINTENANCE_LOCK_FILE"; return 1; }
  [ -f "$MAINTENANCE_LOCK_FILE" ] && [ ! -L "$MAINTENANCE_LOCK_FILE" ] \
    || { warn "维护锁不是普通文件或是软链：$MAINTENANCE_LOCK_FILE"; return 1; }
  normalized_backup="$(normalize_maintenance_backup_dir "$backup_dir")" || return 1
  lock_stat="$(stat_mode_uid_gid_links "$MAINTENANCE_LOCK_FILE")" || {
    warn "无法读取维护锁属性：$MAINTENANCE_LOCK_FILE"
    return 1
  }
  [ "$lock_stat" = '600:0:0:1' ] || {
    warn "维护锁权限/属主/硬链数必须是 600:root:root:1，当前为 $lock_stat"
    return 1
  }
  key_count="$(awk -F= '
    BEGIN { expected[1]="FORMAT_VERSION"; expected[2]="PURPOSE"; expected[3]="SERVICE_NAME_SHA256";
            expected[4]="BACKUP_DIR_SHA256"; expected[5]="CREATED_AT"; expected[6]="CREATED_BY_UID";
            expected[7]="CREATOR_PID" }
    NR > 7 || $1 != expected[NR] || index($0, "=") == 0 { bad=1 }
    END { if (bad || NR != 7) exit 1; print NR }
  ' "$MAINTENANCE_LOCK_FILE")" || {
    warn '维护锁字段集合、顺序或数量非法'
    return 1
  }
  [ "$key_count" = 7 ] || { warn '维护锁字段数量非法'; return 1; }
  [ "$(read_kv "$MAINTENANCE_LOCK_FILE" FORMAT_VERSION)" = 1 ] \
    || { warn '维护锁格式版本不受支持'; return 1; }
  [ "$(read_kv "$MAINTENANCE_LOCK_FILE" PURPOSE)" = DIANXINYUN_PRODUCTION_RELEASE ] \
    || { warn '维护锁用途不匹配'; return 1; }
  expected_service_hash="$(sha256_text "$SERVICE_NAME")"
  expected_backup_hash="$(sha256_text "$normalized_backup")"
  [ "$(read_kv "$MAINTENANCE_LOCK_FILE" SERVICE_NAME_SHA256)" = "$expected_service_hash" ] \
    || { warn '维护锁服务摘要与当前服务不匹配'; return 1; }
  [ "$(read_kv "$MAINTENANCE_LOCK_FILE" BACKUP_DIR_SHA256)" = "$expected_backup_hash" ] \
    || { warn '维护锁与本次停机备份目录不匹配'; return 1; }
  [ "$(read_kv "$MAINTENANCE_LOCK_FILE" CREATED_BY_UID)" = 0 ] \
    || { warn '维护锁创建者不是 root'; return 1; }
  created_at="$(read_kv "$MAINTENANCE_LOCK_FILE" CREATED_AT)"
  [[ "$created_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[^[:space:]]+$ ]] \
    || { warn '维护锁创建时间格式非法'; return 1; }
  creator_pid="$(read_kv "$MAINTENANCE_LOCK_FILE" CREATOR_PID)"
  [[ "$creator_pid" =~ ^[1-9][0-9]*$ ]] || { warn '维护锁创建进程号非法'; return 1; }
}

create_maintenance_lock() {
  local backup_dir="$1" normalized_backup lock_parent temp_file service_hash backup_hash
  validate_maintenance_lock_path || return 1
  maintenance_lock_present && { warn "已有维护锁，拒绝覆盖：$MAINTENANCE_LOCK_FILE"; return 1; }
  normalized_backup="$(normalize_maintenance_backup_dir "$backup_dir")" || return 1
  lock_parent="${MAINTENANCE_LOCK_FILE%/*}"
  [ -d "$lock_parent" ] && [ ! -L "$lock_parent" ] \
    || { warn "维护锁父目录不存在或是软链：$lock_parent"; return 1; }
  service_hash="$(sha256_text "$SERVICE_NAME")"
  backup_hash="$(sha256_text "$normalized_backup")"
  temp_file="$(mktemp "$lock_parent/.maintenance.lock.tmp.XXXXXX")" || return 1
  chmod 0600 "$temp_file" || { rm -f -- "$temp_file"; return 1; }
  chown 0:0 "$temp_file" || { rm -f -- "$temp_file"; return 1; }
  if ! printf 'FORMAT_VERSION=1\nPURPOSE=DIANXINYUN_PRODUCTION_RELEASE\nSERVICE_NAME_SHA256=%s\nBACKUP_DIR_SHA256=%s\nCREATED_AT=%s\nCREATED_BY_UID=0\nCREATOR_PID=%s\n' \
      "$service_hash" "$backup_hash" "$(date -Iseconds)" "$$" > "$temp_file"; then
    rm -f -- "$temp_file"
    return 1
  fi
  if ! ln -- "$temp_file" "$MAINTENANCE_LOCK_FILE"; then
    rm -f -- "$temp_file"
    warn "维护锁并发创建或目标已存在：$MAINTENANCE_LOCK_FILE"
    return 1
  fi
  rm -f -- "$temp_file"
  if ! assert_maintenance_lock "$backup_dir"; then
    warn "新维护锁自检失败，保留现场：$MAINTENANCE_LOCK_FILE"
    return 1
  fi
  log "已创建生产维护锁（仅记录摘要，不含密钥）：$MAINTENANCE_LOCK_FILE"
}

ensure_maintenance_lock() {
  local backup_dir="$1"
  if maintenance_lock_present; then
    assert_maintenance_lock "$backup_dir"
  else
    create_maintenance_lock "$backup_dir"
  fi
}

release_maintenance_lock() {
  local backup_dir="$1"
  assert_maintenance_lock "$backup_dir" || return 1
  rm -- "$MAINTENANCE_LOCK_FILE" || return 1
  if maintenance_lock_present; then
    warn "维护锁删除后仍存在：$MAINTENANCE_LOCK_FILE"
    return 1
  fi
  log "生产维护锁已安全解除：$MAINTENANCE_LOCK_FILE"
}

canonical_existing_path() {
  local path="$1" label="$2" resolved
  [ -e "$path" ] || die "$label 不存在：$path"
  resolved="$(readlink -f -- "$path")"
  [ -n "$resolved" ] || die "无法解析 $label：$path"
  printf '%s\n' "$resolved"
}

mysql_base_args=()
mysqldump_base_args=()

init_mysql_args() {
  validate_database_name
  mysql_base_args=()
  mysqldump_base_args=()
  if [ -n "${MYSQL_DEFAULTS_FILE:-}" ]; then
    [ -r "$MYSQL_DEFAULTS_FILE" ] || die "MYSQL_DEFAULTS_FILE 不可读：$MYSQL_DEFAULTS_FILE"
    mysql_base_args+=("--defaults-extra-file=$MYSQL_DEFAULTS_FILE")
    mysqldump_base_args+=("--defaults-extra-file=$MYSQL_DEFAULTS_FILE")
  fi
  mysql_base_args+=(--default-character-set=utf8mb4 --database="$MYSQL_DATABASE" --batch --skip-column-names)
  mysqldump_base_args+=(--default-character-set=utf8mb4)
}

mysql_query() {
  "$MYSQL_BIN" "${mysql_base_args[@]}" -e "$1"
}

mysql_run_file() {
  local sql_file="$1"
  "$MYSQL_BIN" "${mysql_base_args[@]}" --show-warnings < "$sql_file"
}

capture_current_tables() {
  local output_file="$1"
  mysql_query "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name" \
    | LC_ALL=C sort -u > "$output_file"
}

capture_current_markers() {
  local output_file="$1"
  mysql_query 'SELECT migration_key FROM sys_data_migration ORDER BY migration_key' \
    | LC_ALL=C sort -u > "$output_file"
}

compare_exact_file() {
  local expected="$1" actual="$2" label="$3"
  if ! diff -u "$expected" "$actual"; then
    die "$label 与核定基线不一致"
  fi
}

assert_exact_legacy_baseline() {
  local temp_dir actual_tables actual_markers
  temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-baseline.XXXXXX")"
  actual_tables="$temp_dir/tables.txt"
  actual_markers="$temp_dir/markers.txt"
  capture_current_tables "$actual_tables"
  capture_current_markers "$actual_markers"
  compare_exact_file "$EXPECTED_TABLES_FILE" "$actual_tables" '68 张正式旧表清单'
  compare_exact_file "$EXPECTED_MARKERS_FILE" "$actual_markers" '13 个正式旧迁移标记'
  [ "$(wc -l < "$actual_tables" | tr -d ' ')" = 68 ] || die '旧表数量不是 68'
  [ "$(wc -l < "$actual_markers" | tr -d ' ')" = 13 ] || die '旧迁移标记数量不是 13'
  rm -rf -- "$temp_dir"
}

assert_service_inactive() {
  local properties load_state='' active_state='' sub_state=''
  local main_pid='' control_pid='' unit_job='' property value
  if ! properties="$(
    systemctl show "$SERVICE_NAME" \
      --property=LoadState \
      --property=ActiveState \
      --property=SubState \
      --property=MainPID \
      --property=ControlPID \
      --property=Job 2>/dev/null
  )"; then
    die "无法读取 $SERVICE_NAME 完整停服状态"
  fi
  while IFS='=' read -r property value; do
    case "$property" in
      LoadState) load_state="$value" ;;
      ActiveState) active_state="$value" ;;
      SubState) sub_state="$value" ;;
      MainPID) main_pid="$value" ;;
      ControlPID) control_pid="$value" ;;
      Job) unit_job="$value" ;;
    esac
  done <<< "$properties"

  [ "$load_state" = loaded ] \
    || die "$SERVICE_NAME 未处于 loaded，拒绝把缺失/异常 unit 当作已停稳：load=${load_state:-missing}"
  case "$active_state" in
    inactive|failed) ;;
    *)
      die "$SERVICE_NAME 尚未完全停稳：active=${active_state:-missing} sub=${sub_state:-missing}"
      ;;
  esac
  [ "$main_pid" = 0 ] \
    || die "$SERVICE_NAME 仍有 MainPID 或状态不明：${main_pid:-missing}"
  [ "$control_pid" = 0 ] \
    || die "$SERVICE_NAME 仍有 ControlPID 或状态不明：${control_pid:-missing}"
  case "$unit_job" in
    ''|0) ;;
    *) die "$SERVICE_NAME 仍有 systemd job，拒绝执行离线写操作：$unit_job" ;;
  esac
}

verify_backup_directory() {
  local backup_dir="$1"
  assert_safe_absolute_path "$backup_dir" '备份目录'
  [ -d "$backup_dir" ] || die "备份目录不存在：$backup_dir"
  [ -f "$backup_dir/BACKUP_COMPLETE" ] || die '备份缺少 BACKUP_COMPLETE，禁止继续'
  [ -f "$backup_dir/SHA256SUMS" ] || die '备份缺少 SHA256SUMS，禁止继续'
  (cd "$backup_dir" && sha256sum -c SHA256SUMS)
  gzip -t "$backup_dir/database/$MYSQL_DATABASE.sql.gz"
  tar -tzf "$backup_dir/files/uploads.tar.gz" >/dev/null
  tar -tzf "$backup_dir/runtime/frontend.tar.gz" >/dev/null
  tar -tzf "$backup_dir/config/nginx.tar.gz" >/dev/null
  tar -tzf "$backup_dir/config/systemd.tar.gz" >/dev/null
}

read_kv() {
  local file="$1" key="$2"
  awk -F= -v wanted="$key" '$1 == wanted {sub(/^[^=]*=/, ""); print; found++} END {if (found != 1) exit 3}' "$file"
}

validate_sha256() {
  local value="$1" label="$2"
  [[ "$value" =~ ^[0-9a-fA-F]{64}$ ]] || die "$label 必须是 64 位 SHA-256：$value"
}

atomic_symlink_swap() {
  local target="$1" link_path="$2" next_link
  [ -e "$target" ] || die "软链目标不存在：$target"
  [ -L "$link_path" ] || die "正式入口不是软链，拒绝替换：$link_path"
  next_link="$(dirname "$link_path")/.${link_path##*/}.next.$$"
  [ ! -e "$next_link" ] || die "临时软链已存在：$next_link"
  ln -s -- "$target" "$next_link"
  mv -Tf -- "$next_link" "$link_path"
}

restore_displaced_path_before_commit() {
  local restore_committed="$1" current_path="$2" displaced_path="$3" partial_path="$4" label="$5"
  case "$restore_committed" in
    0|1) ;;
    *) warn "$label 恢复提交标志非法：$restore_committed"; return 1 ;;
  esac
  [ "$restore_committed" = 0 ] || return 0
  if [ ! -e "$displaced_path" ] && [ ! -L "$displaced_path" ]; then
    return 0
  fi
  if [ -e "$partial_path" ] || [ -L "$partial_path" ]; then
    warn "$label 部分恢复保留路径已存在，拒绝覆盖：$partial_path"
    return 1
  fi
  if [ -e "$current_path" ] || [ -L "$current_path" ]; then
    mv -- "$current_path" "$partial_path" || return 1
  fi
  mv -- "$displaced_path" "$current_path"
}

manifest_value() {
  local manifest="$1" key="$2"
  read_kv "$manifest" "$key"
}

assert_no_appledouble_or_unsafe_members() {
  local archive="$1" list_file
  list_file="$(mktemp "${TMPDIR:-/tmp}/dianxinyun-tar-list.XXXXXX")"
  tar -tzf "$archive" > "$list_file"
  if awk 'BEGIN{bad=0} /(^|\/)\._/ || /(^|\/)\.DS_Store$/ || /^\// || /(^|\/)\.\.($|\/)/ {print "UNSAFE " $0; bad=1} END{exit bad}' "$list_file"; then
    :
  else
    rm -f -- "$list_file"
    die "归档含 AppleDouble、绝对路径或上级路径：$archive"
  fi
  rm -f -- "$list_file"
}

assert_readable_by_user() {
  local user="$1" path="$2"
  runuser -u "$user" -- test -r "$path" || die "$user 无法读取：$path"
}

assert_traversable_by_user() {
  local user="$1" path="$2"
  runuser -u "$user" -- test -x "$path" || die "$user 无法遍历：$path"
}

validate_health_polling_config() {
  case "$BACKEND_LOCAL_HEALTH_ATTEMPTS" in
    ''|*[!0-9]*) warn 'BACKEND_LOCAL_HEALTH_ATTEMPTS 必须是正整数'; return 1 ;;
  esac
  case "$BACKEND_PUBLIC_HEALTH_ATTEMPTS" in
    ''|*[!0-9]*) warn 'BACKEND_PUBLIC_HEALTH_ATTEMPTS 必须是正整数'; return 1 ;;
  esac
  case "$BACKEND_HEALTH_INTERVAL_SECONDS" in
    ''|*[!0-9]*) warn 'BACKEND_HEALTH_INTERVAL_SECONDS 必须是非负整数'; return 1 ;;
  esac
  [ "$BACKEND_LOCAL_HEALTH_ATTEMPTS" -gt 0 ] \
    || { warn 'BACKEND_LOCAL_HEALTH_ATTEMPTS 必须大于 0'; return 1; }
  [ "$BACKEND_PUBLIC_HEALTH_ATTEMPTS" -gt 0 ] \
    || { warn 'BACKEND_PUBLIC_HEALTH_ATTEMPTS 必须大于 0'; return 1; }
}

health_check_url() {
  local health_url="$1" label="$2" response_file http_code
  response_file="$(mktemp "${TMPDIR:-/tmp}/dianxinyun-health.XXXXXX")"
  if ! http_code="$(curl --silent --show-error --max-time 20 --output "$response_file" \
      --write-out '%{http_code}' "$health_url")"; then
    rm -f -- "$response_file"
    warn "$label 请求失败：$health_url"
    return 1
  fi
  if [ "$http_code" != 200 ]; then
    rm -f -- "$response_file"
    warn "$label HTTP 状态不是 200：$http_code"
    return 1
  fi
  if ! grep -Eq '"code"[[:space:]]*:[[:space:]]*200([,}])' "$response_file"; then
    rm -f -- "$response_file"
    warn "$label 业务 code 不是 200"
    return 1
  fi
  rm -f -- "$response_file"
}

health_check_backend() {
  health_check_url "$HEALTH_URL" '公网验证码接口'
}

wait_for_backend_health() {
  local attempt local_ready=0 public_ready=0
  validate_health_polling_config || return 1

  log "等待本机后端健康：最多 ${BACKEND_LOCAL_HEALTH_ATTEMPTS} 次，每次间隔 ${BACKEND_HEALTH_INTERVAL_SECONDS} 秒"
  for ((attempt = 1; attempt <= BACKEND_LOCAL_HEALTH_ATTEMPTS; attempt++)); do
    if systemctl is-active --quiet "$SERVICE_NAME" \
        && health_check_url "$LOCAL_HEALTH_URL" '本机验证码接口' >/dev/null 2>&1; then
      local_ready=1
      log "本机后端健康检查通过：第 $attempt 次"
      break
    fi
    if (( attempt % 5 == 0 || attempt == BACKEND_LOCAL_HEALTH_ATTEMPTS )); then
      log "本机后端尚未就绪：$attempt/$BACKEND_LOCAL_HEALTH_ATTEMPTS"
    fi
    if (( attempt < BACKEND_LOCAL_HEALTH_ATTEMPTS )); then
      sleep "$BACKEND_HEALTH_INTERVAL_SECONDS"
    fi
  done
  if [ "$local_ready" != 1 ]; then
    warn "本机后端在有界等待后仍未就绪：$LOCAL_HEALTH_URL"
    return 1
  fi

  log "等待公网健康：最多 ${BACKEND_PUBLIC_HEALTH_ATTEMPTS} 次，每次间隔 ${BACKEND_HEALTH_INTERVAL_SECONDS} 秒"
  for ((attempt = 1; attempt <= BACKEND_PUBLIC_HEALTH_ATTEMPTS; attempt++)); do
    if health_check_backend >/dev/null 2>&1; then
      public_ready=1
      log "公网健康检查通过：第 $attempt 次"
      break
    fi
    if (( attempt % 5 == 0 || attempt == BACKEND_PUBLIC_HEALTH_ATTEMPTS )); then
      log "公网入口尚未就绪：$attempt/$BACKEND_PUBLIC_HEALTH_ATTEMPTS"
    fi
    if (( attempt < BACKEND_PUBLIC_HEALTH_ATTEMPTS )); then
      sleep "$BACKEND_HEALTH_INTERVAL_SECONDS"
    fi
  done
  if [ "$public_ready" != 1 ]; then
    warn "公网入口在有界等待后仍未就绪：$HEALTH_URL"
    return 1
  fi
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  die 'common.sh 只能由生产发布脚本 source，不能直接执行'
fi
