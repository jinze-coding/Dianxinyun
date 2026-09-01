#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：40-activate-backend.sh --jar /opt/site-platform/releases/<ID>/backend/site-platform.jar \
       --backup-dir /已验证停机备份 --log-file /新日志文件 [--dry-run] \
       --confirm ACTIVATE_BACKEND_WITH_TRANSITION_WEB

只允许在 98 表/25 标记、访客修复复验已通过，且当前 Web 已是会议创建关闭的 transition 包时，
原子切换 JAR 并启动服务。失败会恢复旧 JAR 软链但保持服务停止，随后必须执行同点回滚。
EOF
}

jar_path=''
backup_dir=''
log_file=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --jar) [ "$#" -ge 2 ] || usage_error '--jar 缺少值'; jar_path="$2"; shift ;;
    --backup-dir) [ "$#" -ge 2 ] || usage_error '--backup-dir 缺少值'; backup_dir="$2"; shift ;;
    --log-file) [ "$#" -ge 2 ] || usage_error '--log-file 缺少值'; log_file="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

[ -n "$jar_path" ] || usage_error '必须提供 --jar'
[ -n "$backup_dir" ] || usage_error '必须提供 --backup-dir'
[ -n "$log_file" ] || usage_error '必须提供 --log-file'
assert_safe_absolute_path "$jar_path" '新 JAR'
assert_safe_absolute_path "$log_file" '后端启动日志'
case "$jar_path" in "$APP_ROOT"/releases/*/backend/site-platform.jar) ;; *) die '新 JAR 必须位于正式 releases 版本目录' ;; esac
require_confirmation ACTIVATE_BACKEND_WITH_TRANSITION_WEB "$confirmation"
require_commands systemctl journalctl curl grep runuser "$MYSQL_BIN" readlink sha256sum sleep
init_mysql_args
verify_backup_directory "$backup_dir"
assert_service_inactive
[ -f "$jar_path" ] || die "新 JAR 不存在：$jar_path"
assert_readable_by_user "$SERVICE_USER" "$jar_path"
[ ! -e "$log_file" ] || die "日志文件已存在：$log_file"
[ -d "$(dirname "$log_file")" ] || die '启动日志父目录不存在'

[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'")" = 98 ] \
  || die '后端切换前表数不是 98'
[ "$(mysql_query 'SELECT COUNT(*) FROM sys_data_migration')" = 25 ] || die '后端切换前迁移标记不是 25'
[ "$(mysql_query "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='20260901_SITE_ACCESS_REENCRYPT_LEGACY_DEVELOPMENT_KEY_V1'")" = 1 ] \
  || die '访客修复标记缺失或重复'

current_web="$(canonical_existing_path "$WEB_LINK" '当前 transition Web')"
web_release_root="$(dirname "$current_web")"
web_manifest="$web_release_root/RELEASE_MANIFEST.txt"
[ -f "$web_manifest" ] || die '当前 Web 缺少 RELEASE_MANIFEST.txt；必须先切换 transition Web'
[ "$(manifest_value "$web_manifest" VARIANT)" = transition ] || die '当前 Web 不是 transition 版本'
[ "$(manifest_value "$web_manifest" MEETING_CREATION_ENABLED)" = false ] || die 'transition Web 错误启用了会议创建'
grep -R --binary-files=text -q 'dianxinyun-web-transition-meeting-disabled' "$current_web" \
  || die 'transition Web 缺少会议关闭发布标记'

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：后端/数据库/transition Web 门禁通过；将原子切换 $JAR_LINK -> $jar_path 并启动"
  exit 0
fi

require_root
old_jar_target="$(canonical_existing_path "$JAR_LINK" '旧 JAR')"
link_swapped=0
activation_complete=0
on_activation_exit() {
  local rc=$?
  trap - EXIT ERR INT TERM HUP
  [ "$activation_complete" = 1 ] && [ "$rc" = 0 ] && return 0
  [ "$rc" -ne 0 ] || rc=1
  set +e
  warn "新后端验收失败（exit=$rc），正在停止服务并恢复旧 JAR 软链；不会在新结构上自动启动旧服务"
  systemctl stop "$SERVICE_NAME" >/dev/null 2>&1 || true
  if systemctl is-active --quiet "$SERVICE_NAME"; then
    systemctl kill --kill-who=all --signal=SIGKILL "$SERVICE_NAME" >/dev/null 2>&1 || true
    systemctl stop "$SERVICE_NAME" >/dev/null 2>&1 || true
  fi
  if [ "$link_swapped" = 1 ]; then
    atomic_symlink_swap "$old_jar_target" "$JAR_LINK" || warn '旧 JAR 软链自动恢复失败'
  fi
  exit "$rc"
}
trap on_activation_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

atomic_symlink_swap "$jar_path" "$JAR_LINK"
link_swapped=1
systemctl start "$SERVICE_NAME"
systemctl is-active --quiet "$SERVICE_NAME"
wait_for_backend_health
for docs_path in /doc.html /swagger-ui/index.html /v3/api-docs; do
  status="$(curl --silent --show-error --max-time 15 --output /dev/null --write-out '%{http_code}' "$PUBLIC_BASE_URL$docs_path")"
  [ "$status" = 404 ] || die "$docs_path 正式环境状态应为 404，当前为 $status"
done
journalctl -u "$SERVICE_NAME" -n 200 --no-pager > "$log_file"
if grep -Eiq 'APPLICATION FAILED TO START|OutOfMemoryError|(^|[^0-9])500([^0-9]|$)' "$log_file"; then
  die '后端启动日志出现失败、OOM 或 500 信号'
fi
sha256sum "$log_file" > "$log_file.sha256"
activation_complete=1
trap - EXIT INT TERM HUP
log "新后端已启动且基础健康门禁通过：$jar_path"
log '仍处于发布兼容停点；必须完成 transition Web + 当前正式小程序冒烟后才能结束维护窗口'
