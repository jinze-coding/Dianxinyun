#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：20-run-migrations.sh --release-dir /已验包/更新目录 --backup-dir /已验证停机备份 \
       --log-dir /绝对/新日志目录 [--dry-run] --confirm MIGRATE_DIANXINYUN_68_TO_98

从精确 68 表/13 标记旧基线开始，按固化计划执行 11 项迁移。每项校验 SQL SHA-256，使用
mysql --show-warnings，任何非零退出或 Warning 立即停止，并核对预期表数及完整迁移标记集合。
第 4 项若已有标记，脚本会在执行任何 SQL 前拒绝继续。
EOF
}

release_dir=''
backup_dir=''
log_dir=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --release-dir) [ "$#" -ge 2 ] || usage_error '--release-dir 缺少值'; release_dir="$2"; shift ;;
    --backup-dir) [ "$#" -ge 2 ] || usage_error '--backup-dir 缺少值'; backup_dir="$2"; shift ;;
    --log-dir) [ "$#" -ge 2 ] || usage_error '--log-dir 缺少值'; log_dir="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

[ -n "$release_dir" ] || usage_error '必须提供 --release-dir'
[ -n "$backup_dir" ] || usage_error '必须提供 --backup-dir'
[ -n "$log_dir" ] || usage_error '必须提供 --log-dir'
assert_safe_absolute_path "$release_dir" '发布目录'
assert_safe_absolute_path "$backup_dir" '备份目录'
assert_safe_absolute_path "$log_dir" '迁移日志目录'
require_confirmation MIGRATE_DIANXINYUN_68_TO_98 "$confirmation"
require_commands "$MYSQL_BIN" sha256sum sort diff awk grep tee mktemp find wc
init_mysql_args
if [ "$DRY_RUN" != 1 ]; then
  require_root
  acquire_release_operation_lock || die '无法取得生产发布全程互斥锁'
fi

migration_dir="$release_dir/database/migrations"
[ -d "$migration_dir" ] || die "发布包缺少迁移目录：$migration_dir"
if find "$migration_dir" -name '._*' -o -name '.DS_Store' | grep -q .; then
  die '迁移目录含 AppleDouble 或 .DS_Store，拒绝执行'
fi

plan_temp="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-migration-plan.XXXXXX")"
cleanup() { rm -rf -- "$plan_temp"; }
trap cleanup EXIT

awk -F'|' '!/^#/ && NF {print $2}' "$MIGRATION_PLAN_FILE" | LC_ALL=C sort > "$plan_temp/expected-files.txt"
find "$migration_dir" -maxdepth 1 -type f -name '*.sql' -exec basename {} \; | LC_ALL=C sort > "$plan_temp/actual-files.txt"
compare_exact_file "$plan_temp/expected-files.txt" "$plan_temp/actual-files.txt" '发布包中的 11 个 SQL 文件'
[ "$(wc -l < "$plan_temp/actual-files.txt" | tr -d ' ')" = 11 ] || die '迁移 SQL 文件数量不是 11'

while IFS='|' read -r order filename expected_tables expected_marker expected_sha; do
  case "$order" in ''|'#'*) continue ;; esac
  sql_file="$migration_dir/$filename"
  actual_sha="$(sha256sum "$sql_file" | awk '{print $1}')"
  [ "$actual_sha" = "$expected_sha" ] || die "$filename SHA-256 不符合固化计划"
done < "$MIGRATION_PLAN_FILE"

verify_backup_directory "$backup_dir"
assert_maintenance_lock "$backup_dir" || die '生产维护锁与本次停机备份不匹配'
assert_service_inactive
assert_service_boot_disabled || die '维护期主服务开机自启未保持 disabled'

if [ "$DRY_RUN" = 1 ]; then
  log 'DRY-RUN：11 个 SQL 文件、顺序和 SHA-256 均通过；不会连接数据库或创建日志'
  awk -F'|' '!/^#/ && NF {printf "  %s %s -> tables=%s marker=%s\n", $1, $2, $3, $4}' "$MIGRATION_PLAN_FILE" >&2
  exit 0
fi

[ ! -e "$log_dir" ] || die "迁移日志目录已存在，拒绝覆盖：$log_dir"
mkdir -p "$log_dir"
chmod 0700 "$log_dir"

assert_exact_legacy_baseline
general_marker_count="$(mysql_query "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='20260826_GENERAL_INSPECTION_V1'")"
[ "$general_marker_count" = 0 ] || die '第 4 项通用巡检标记已经存在，拒绝自动重跑；当前数据库不是核定旧基线'

expected_markers="$plan_temp/expected-markers.txt"
cp "$EXPECTED_MARKERS_FILE" "$expected_markers"

on_error() {
  local rc=$?
  warn "迁移在当前步骤停止（exit=$rc）；$SERVICE_NAME 保持停止。禁止继续后续脚本或手工反向 DDL，只能核查日志并执行同点回滚。"
  exit "$rc"
}
trap on_error ERR

while IFS='|' read -r order filename expected_tables expected_marker expected_sha; do
  case "$order" in ''|'#'*) continue ;; esac
  sql_file="$migration_dir/$filename"
  step_log="$log_dir/${order}-${filename%.sql}.log"
  log "执行第 $order 项：$filename"

  if [ "$order" = 04 ]; then
    [ "$(mysql_query "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='$expected_marker'")" = 0 ] \
      || die '第 4 项标记已存在，拒绝执行不可自动重跑脚本'
  fi

  mysql_run_file "$sql_file" 2>&1 | tee "$step_log"
  if awk '$1 == "Warning" || $1 == "Error" {found=1} END {exit !found}' "$step_log"; then
    die "$filename 产生 MySQL Warning/Error，发布已停止"
  fi

  actual_tables="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'")"
  [ "$actual_tables" = "$expected_tables" ] || die "$filename 后表数为 $actual_tables，预期 $expected_tables"
  marker_count="$(mysql_query "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='$expected_marker'")"
  [ "$marker_count" = 1 ] || die "$filename 后迁移标记 $expected_marker 数量为 $marker_count"

  printf '%s\n' "$expected_marker" >> "$expected_markers"
  LC_ALL=C sort -u "$expected_markers" -o "$expected_markers"
  capture_current_markers "$plan_temp/actual-markers.txt"
  compare_exact_file "$expected_markers" "$plan_temp/actual-markers.txt" "第 $order 项后的完整迁移标记集合"
  log "第 $order 项通过：tables=$actual_tables marker=$expected_marker"
done < "$MIGRATION_PLAN_FILE"

trap - ERR
final_tables="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'")"
final_markers="$(mysql_query 'SELECT COUNT(*) FROM sys_data_migration')"
[ "$final_tables" = 98 ] || die "迁移结束表数不是 98：$final_tables"
[ "$final_markers" = 24 ] || die "迁移结束标记数不是 24：$final_markers"

quality_enabled="$(mysql_query 'SELECT COUNT(*) FROM quality_weekly_reminder_setting WHERE enabled <> 0')"
electric_enabled="$(mysql_query 'SELECT COUNT(*) FROM project_inspection_setting WHERE submission_reminder_enabled <> 0')"
[ "$quality_enabled" = 0 ] || die '迁移后存在自动启用的质量提醒'
[ "$electric_enabled" = 0 ] || die '迁移后存在自动启用的电箱提醒'

printf 'MIGRATION_STATE=COMPLETE\nCOMPLETED_AT=%s\nTABLES=98\nMARKERS=24\n' "$(date -Iseconds)" > "$log_dir/MIGRATIONS_COMPLETE"
sha256sum "$log_dir"/*.log "$log_dir/MIGRATIONS_COMPLETE" > "$log_dir/SHA256SUMS"
log "11 项迁移全部完成：98 表、24 个准确标记；日志目录 $log_dir"
