#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：10-stop-and-backup.sh --backup-dir /绝对/新目录 [--dry-run]
                              --confirm STOP_AND_BACKUP_DIANXINYUN

先重跑精确只读预检，再停止后端并生成同一停机点的数据库、uploads、JAR、Web、env、
Nginx、systemd 和软链目标备份。任何失败都会在数据库尚未迁移时尝试重启原服务。
EOF
}

backup_dir=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --backup-dir) [ "$#" -ge 2 ] || usage_error '--backup-dir 缺少值'; backup_dir="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

[ -n "$backup_dir" ] || usage_error '必须提供 --backup-dir'
assert_safe_absolute_path "$backup_dir" '备份目录'
require_confirmation STOP_AND_BACKUP_DIANXINYUN "$confirmation"
require_commands "$MYSQL_BIN" "$MYSQLDUMP_BIN" systemctl readlink sha256sum gzip tar find sort xargs stat awk hostname uname nginx
init_mysql_args

[ ! -e "$backup_dir" ] || die "备份目录已存在，拒绝覆盖：$backup_dir"

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：将只读核对 68 表/13 标记，停止 $SERVICE_NAME，并创建 $backup_dir"
  log 'DRY-RUN：将备份数据库、uploads、当前 JAR/Web、env、Nginx、systemd 和全部软链目标，再验证 SHA-256'
  exit 0
fi

require_root
"$SCRIPT_DIR/00-preflight-readonly.sh"

jar_target="$(canonical_existing_path "$JAR_LINK" '当前 JAR')"
web_target="$(canonical_existing_path "$WEB_LINK" '当前 Web')"
upload_target="$(canonical_existing_path "$UPLOAD_LINK" '当前 uploads')"
fragment_path="$(systemctl show "$SERVICE_NAME" --property=FragmentPath --value)"
[ -f "$fragment_path" ] || die "systemd FragmentPath 不可读：$fragment_path"

service_stopped=0
backup_complete=0
on_exit() {
  local rc=$?
  if [ "$rc" -ne 0 ] && [ "$service_stopped" = 1 ] && [ "$backup_complete" = 0 ]; then
    warn '备份未完成；数据库尚未迁移，正在尝试恢复原服务'
    systemctl start "$SERVICE_NAME" || warn '原服务自动重启失败，请立即人工处理'
  fi
  exit "$rc"
}
trap on_exit EXIT

mkdir -p "$backup_dir"/{database,files,runtime,config,inventory}
chmod 0700 "$backup_dir" "$backup_dir"/{database,files,runtime,config,inventory}

{
  printf 'BACKUP_STARTED_AT=%s\n' "$(date -Iseconds)"
  printf 'HOSTNAME=%s\n' "$(hostname)"
  printf 'SERVICE_NAME=%s\n' "$SERVICE_NAME"
  printf 'MYSQL_DATABASE=%s\n' "$MYSQL_DATABASE"
  printf 'JAR_LINK=%s\n' "$JAR_LINK"
  printf 'JAR_TARGET=%s\n' "$jar_target"
  printf 'WEB_LINK=%s\n' "$WEB_LINK"
  printf 'WEB_TARGET=%s\n' "$web_target"
  printf 'UPLOAD_LINK=%s\n' "$UPLOAD_LINK"
  printf 'UPLOAD_TARGET=%s\n' "$upload_target"
  printf 'ENV_FILE=%s\n' "$ENV_FILE"
  printf 'NGINX_ROOT=%s\n' "$NGINX_ROOT"
  printf 'SYSTEMD_FRAGMENT=%s\n' "$fragment_path"
} > "$backup_dir/inventory/targets.env"

date --iso-8601=seconds > "$backup_dir/inventory/date.txt"
uname -a > "$backup_dir/inventory/uname.txt"
systemctl show "$SERVICE_NAME" --no-pager --property=Id --property=LoadState --property=ActiveState \
  --property=SubState --property=FragmentPath --property=DropInPaths --property=User --property=Group \
  --property=WorkingDirectory --property=ExecStart \
  > "$backup_dir/inventory/systemd-show.txt"
systemctl cat "$SERVICE_NAME" > "$backup_dir/inventory/systemd-cat.txt"
sha256sum "$jar_target" > "$backup_dir/inventory/current-jar.sha256"
find "$web_target" -xdev -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum \
  > "$backup_dir/inventory/current-web-files.sha256"
grep -E '^[A-Z0-9_]+=' "$ENV_FILE" | cut -d= -f1 | LC_ALL=C sort -u \
  > "$backup_dir/inventory/environment-keys.txt"

log "停止 $SERVICE_NAME，建立一致性停机点"
systemctl stop "$SERVICE_NAME"
service_stopped=1
systemctl is-active --quiet "$SERVICE_NAME" && die "$SERVICE_NAME 未停止"

capture_current_tables "$backup_dir/inventory/tables.txt"
capture_current_markers "$backup_dir/inventory/migration-markers.txt"
compare_exact_file "$EXPECTED_TABLES_FILE" "$backup_dir/inventory/tables.txt" '停服后 68 张正式旧表清单'
compare_exact_file "$EXPECTED_MARKERS_FILE" "$backup_dir/inventory/migration-markers.txt" '停服后 13 个正式旧迁移标记'

"$MYSQLDUMP_BIN" "${mysqldump_base_args[@]}" --single-transaction --routines --triggers --events \
  --hex-blob --set-gtid-purged=OFF --add-drop-database --databases "$MYSQL_DATABASE" \
  | gzip -9 > "$backup_dir/database/$MYSQL_DATABASE.sql.gz"

tar --numeric-owner -C "$(dirname "$upload_target")" -czf "$backup_dir/files/uploads.tar.gz" "$(basename "$upload_target")"
(cd "$upload_target" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum \
  > "$backup_dir/inventory/uploads-files.sha256")
printf 'UPLOAD_FILE_COUNT=%s\nUPLOAD_TOTAL_BYTES=%s\n' \
  "$(find "$upload_target" -type f | wc -l | tr -d ' ')" \
  "$(find "$upload_target" -type f -printf '%s\n' | awk '{sum += $1} END {print sum + 0}')" \
  > "$backup_dir/inventory/uploads-summary.env"
install -m 0600 "$jar_target" "$backup_dir/runtime/site-platform.jar"
tar --numeric-owner -C "$(dirname "$web_target")" -czf "$backup_dir/runtime/frontend.tar.gz" "$(basename "$web_target")"
install -m 0600 "$ENV_FILE" "$backup_dir/config/site-platform.env"
tar --numeric-owner -C / -czf "$backup_dir/config/nginx.tar.gz" "${NGINX_ROOT#/}"

systemd_members=("${fragment_path#/}")
dropin_paths="$(systemctl show "$SERVICE_NAME" --property=DropInPaths --value)"
if [ -n "$dropin_paths" ]; then
  IFS=' ' read -r -a dropin_array <<< "$dropin_paths"
  for dropin in "${dropin_array[@]}"; do
    [ -f "$dropin" ] || die "systemd drop-in 不可读：$dropin"
    systemd_members+=("${dropin#/}")
  done
fi
tar --numeric-owner -C / -czf "$backup_dir/config/systemd.tar.gz" "${systemd_members[@]}"

nginx -T > "$backup_dir/inventory/nginx-effective.txt" 2>&1
printf 'BACKUP_STATE=COMPLETE\nCOMPLETED_AT=%s\n' "$(date -Iseconds)" > "$backup_dir/BACKUP_COMPLETE"
(cd "$backup_dir" && find . -type f ! -name SHA256SUMS -print0 | LC_ALL=C sort -z | xargs -0 sha256sum > SHA256SUMS)

verify_backup_directory "$backup_dir"
backup_complete=1
trap - EXIT
log "停服一致性备份完成且已验证：$backup_dir"
log "$SERVICE_NAME 保持停止；下一步只能配置新密钥并执行迁移，或运行同点回滚"
