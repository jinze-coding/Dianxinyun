#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：
  06-offline-worker-recovery.sh --backup-dir /已验证停机备份 \
    --confirm CLEAR_STALE_OFFLINE_WORKER_AFTER_DATABASE_REVIEW

仅用于 30-visitor-reencrypt.sh 的 transient worker 已经停止、但 RUNNING/SUBMITTING 标记因
SSH 断连、OOM 或 wrapper 被强制终止而残留的故障恢复。执行前必须完成日志和数据库状态复核。
脚本不会启动/停止 worker，也不会修改数据库；它只在发布锁、维护锁、主服务停止且 disabled、
worker unit 已无进程、数据库处于 98 表且修复标记为精确 24/0 或 25/1 时清理匹配标记。
EOF
}

backup_dir=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --backup-dir) [ "$#" -ge 2 ] || usage_error '--backup-dir 缺少值'; backup_dir="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

[ -n "$backup_dir" ] || usage_error '必须提供 --backup-dir'
[ "$confirmation" = CLEAR_STALE_OFFLINE_WORKER_AFTER_DATABASE_REVIEW ] \
  || die '必须显式确认：--confirm CLEAR_STALE_OFFLINE_WORKER_AFTER_DATABASE_REVIEW'
require_commands systemctl stat awk flock rm "$MYSQL_BIN"
init_mysql_args
require_root
acquire_release_operation_lock ALLOW_OFFLINE_WORKER_RECOVERY \
  || die '无法取得离线 worker 恢复专用发布锁'
verify_backup_directory "$backup_dir"
assert_maintenance_lock "$backup_dir" || die '生产维护锁与本次停机备份不匹配'
assert_service_inactive
assert_service_boot_disabled || die '清理残留 worker 标记前主服务必须保持 disabled'
acquire_offline_worker_coordination_lock || die '无法取得离线 worker 协调锁'
load_offline_worker_marker || die '离线 worker 标记不完整或不安全'

stale_state="$OFFLINE_WORKER_MARKER_STATE"
stale_unit="$OFFLINE_WORKER_MARKER_UNIT"
if offline_worker_unit_is_live "$stale_unit"; then
  die "离线 worker 仍在运行/启动/停止过程中，拒绝清理：${stale_unit}.service"
else
  worker_state_rc=$?
fi
[ "$worker_state_rc" -eq 1 ] \
  || die "无法证明离线 worker 已彻底退出：${stale_unit}.service"

table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'")"
marker_count="$(mysql_query 'SELECT COUNT(*) FROM sys_data_migration')"
repair_marker_count="$(mysql_query "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='20260901_SITE_ACCESS_REENCRYPT_LEGACY_DEVELOPMENT_KEY_V1'")"
[ "$table_count" = 98 ] || die "数据库表数不是精确 98，拒绝清理：$table_count"
case "$marker_count:$repair_marker_count" in
  24:0|25:1) ;;
  *) die "访客修复标记状态不完整，拒绝清理：总标记=$marker_count 修复标记=$repair_marker_count" ;;
esac

remove_offline_worker_marker_if_matches "$stale_unit" "$stale_state" \
  || die '残留离线 worker 标记未能安全清理'
release_offline_worker_coordination_lock \
  || die '离线 worker 标记已清理，但协调锁未能显式释放；请等待当前进程退出'
log "离线 worker 残留标记已受控清理：unit=$stale_unit state=$stale_state database=$table_count/$marker_count/$repair_marker_count"
if [ "$marker_count" = 25 ]; then
  log '数据库已有唯一修复标记；下一步只能运行 30 --mode post-verify'
else
  log '数据库尚无修复标记；先重新运行 30 --mode verify，再按新输出决定是否 APPLY'
fi
