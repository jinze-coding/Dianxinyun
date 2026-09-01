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
  if systemctl is-active --quiet "$SERVICE_NAME"; then
    die "$SERVICE_NAME 仍在运行，拒绝执行离线写操作"
  fi
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
