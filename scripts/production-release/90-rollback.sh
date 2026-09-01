#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：90-rollback.sh --backup-dir /已验证同点备份 --failure-snapshot-dir /绝对/新目录 \
       --mini-state not-released|released [--mini-confirm <确认语>] [--dry-run] \
       --confirm RESTORE_DIANXINYUN_FROM_SAMEPOINT_BACKUP

执行前先停服并保存故障现场数据库、uploads、JAR、Web、env、Nginx 和 systemd；随后使用停机点
完整数据库 dump（DROP/CREATE DATABASE）以及配对 uploads/JAR/Web/config 恢复。禁止反向 DROP/ALTER。
若小程序 0.1.8 已发布，还必须提供：
  --mini-confirm MINI_0_1_8_ROLLBACK_OR_OLD_BACKEND_COMPATIBILITY_APPROVED
EOF
}

backup_dir=''
failure_dir=''
mini_state=''
mini_confirmation=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --backup-dir) [ "$#" -ge 2 ] || usage_error '--backup-dir 缺少值'; backup_dir="$2"; shift ;;
    --failure-snapshot-dir) [ "$#" -ge 2 ] || usage_error '--failure-snapshot-dir 缺少值'; failure_dir="$2"; shift ;;
    --mini-state) [ "$#" -ge 2 ] || usage_error '--mini-state 缺少值'; mini_state="$2"; shift ;;
    --mini-confirm) [ "$#" -ge 2 ] || usage_error '--mini-confirm 缺少值'; mini_confirmation="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

[ -n "$backup_dir" ] || usage_error '必须提供 --backup-dir'
[ -n "$failure_dir" ] || usage_error '必须提供 --failure-snapshot-dir'
case "$mini_state" in not-released|released) ;; *) usage_error '--mini-state 必须是 not-released 或 released' ;; esac
assert_safe_absolute_path "$failure_dir" '故障现场目录'
require_confirmation RESTORE_DIANXINYUN_FROM_SAMEPOINT_BACKUP "$confirmation"
if [ "$mini_state" = released ]; then
  require_confirmation MINI_0_1_8_ROLLBACK_OR_OLD_BACKEND_COMPATIBILITY_APPROVED "$mini_confirmation"
fi
require_commands "$MYSQL_BIN" "$MYSQLDUMP_BIN" systemctl readlink sha256sum gzip zgrep tar find sort xargs stat awk nginx curl install runuser mv sleep
init_mysql_args
verify_backup_directory "$backup_dir"
[ ! -e "$failure_dir" ] || die "故障现场目录已存在，拒绝覆盖：$failure_dir"

targets_file="$backup_dir/inventory/targets.env"
[ -f "$targets_file" ] || die '同点备份缺少 inventory/targets.env'
backup_jar_link="$(read_kv "$targets_file" JAR_LINK)"
backup_web_link="$(read_kv "$targets_file" WEB_LINK)"
backup_upload_link="$(read_kv "$targets_file" UPLOAD_LINK)"
backup_upload_target="$(read_kv "$targets_file" UPLOAD_TARGET)"
backup_env_file="$(read_kv "$targets_file" ENV_FILE)"
backup_nginx_root="$(read_kv "$targets_file" NGINX_ROOT)"
backup_systemd_fragment="$(read_kv "$targets_file" SYSTEMD_FRAGMENT)"

[ "$backup_jar_link" = "$JAR_LINK" ] || die '备份 JAR_LINK 与本次目标不一致'
[ "$backup_web_link" = "$WEB_LINK" ] || die '备份 WEB_LINK 与本次目标不一致'
[ "$backup_upload_link" = "$UPLOAD_LINK" ] || die '备份 UPLOAD_LINK 与本次目标不一致'
[ "$backup_env_file" = "$ENV_FILE" ] || die '备份 ENV_FILE 与本次目标不一致'
[ "$backup_nginx_root" = "$NGINX_ROOT" ] || die '备份 NGINX_ROOT 与本次目标不一致'
case "$backup_upload_target" in "$APP_ROOT"/*) ;; *) die '备份 uploads 目标不在 APP_ROOT 内，拒绝自动恢复' ;; esac
case "$backup_systemd_fragment" in /etc/systemd/system/*) ;; *) die 'systemd FragmentPath 不在 /etc/systemd/system，拒绝自动恢复' ;; esac

database_dump="$backup_dir/database/$MYSQL_DATABASE.sql.gz"
zgrep -Fq "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`" "$database_dump" || die '数据库备份不含 DROP DATABASE，不能保证完整同点恢复'
zgrep -Fq "CREATE DATABASE" "$database_dump" || die '数据库备份不含 CREATE DATABASE'

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：同点备份全部校验通过；将先保存故障现场到 $failure_dir，再整体恢复数据库、uploads、JAR/Web/env/Nginx/systemd"
  [ "$mini_state" = released ] && log 'DRY-RUN：已声明小程序 0.1.8 发布，正式执行时必须另行确认客户端回退或旧后端兼容性'
  exit 0
fi

require_root
systemctl stop "$SERVICE_NAME" || true
assert_service_inactive

mkdir -p "$failure_dir"/{database,files,runtime,config,inventory}
chmod 0700 "$failure_dir" "$failure_dir"/{database,files,runtime,config,inventory}
current_jar_target="$(canonical_existing_path "$JAR_LINK" '故障现场 JAR')"
current_web_target="$(canonical_existing_path "$WEB_LINK" '故障现场 Web')"
current_upload_target="$(canonical_existing_path "$UPLOAD_LINK" '故障现场 uploads')"

{
  printf 'CAPTURED_AT=%s\n' "$(date -Iseconds)"
  printf 'JAR_TARGET=%s\n' "$current_jar_target"
  printf 'WEB_TARGET=%s\n' "$current_web_target"
  printf 'UPLOAD_TARGET=%s\n' "$current_upload_target"
  printf 'MINI_STATE=%s\n' "$mini_state"
} > "$failure_dir/inventory/targets.env"
"$MYSQLDUMP_BIN" "${mysqldump_base_args[@]}" --single-transaction --routines --triggers --events \
  --hex-blob --set-gtid-purged=OFF --add-drop-database --databases "$MYSQL_DATABASE" \
  | gzip -9 > "$failure_dir/database/$MYSQL_DATABASE.sql.gz"
tar --numeric-owner -C "$(dirname "$current_upload_target")" -czf "$failure_dir/files/uploads.tar.gz" "$(basename "$current_upload_target")"
install -m 0600 "$current_jar_target" "$failure_dir/runtime/site-platform.jar"
tar --numeric-owner -C "$(dirname "$current_web_target")" -czf "$failure_dir/runtime/frontend.tar.gz" "$(basename "$current_web_target")"
install -m 0600 "$ENV_FILE" "$failure_dir/config/site-platform.env"
tar --numeric-owner -C / -czf "$failure_dir/config/nginx.tar.gz" "${NGINX_ROOT#/}"
failure_systemd_members=("${backup_systemd_fragment#/}")
if [ -d "${backup_systemd_fragment}.d" ]; then
  failure_systemd_members+=("${backup_systemd_fragment#/}.d")
fi
tar --numeric-owner -C / -czf "$failure_dir/config/systemd.tar.gz" "${failure_systemd_members[@]}"
printf 'FAILURE_SNAPSHOT_STATE=COMPLETE\nCOMPLETED_AT=%s\n' "$(date -Iseconds)" > "$failure_dir/FAILURE_SNAPSHOT_COMPLETE"
(cd "$failure_dir" && find . -type f ! -name SHA256SUMS -print0 | LC_ALL=C sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd "$failure_dir" && sha256sum -c SHA256SUMS)
gzip -t "$failure_dir/database/$MYSQL_DATABASE.sql.gz"
tar -tzf "$failure_dir/files/uploads.tar.gz" >/dev/null

stamp="$(date '+%Y%m%d-%H%M%S')"
rollback_release="$APP_ROOT/releases/rollback-$stamp"
[ ! -e "$rollback_release" ] || die "回滚版本目录已存在：$rollback_release"
install -d -o root -g root -m 0755 "$rollback_release"

nginx_displaced=''
fragment_displaced=''
fragment_dropin_displaced=''
nginx_restore_committed=0
systemd_restore_committed=0
restore_phase='before-database-restore'
restore_complete=0
on_restore_exit() {
  local rc=$?
  trap - EXIT ERR INT TERM HUP
  [ "$restore_complete" = 1 ] && [ "$rc" = 0 ] && return 0
  [ "$rc" -ne 0 ] || rc=1
  set +e
  warn "同点恢复失败（exit=$rc，phase=$restore_phase）；正在确保 $SERVICE_NAME 停止，数据库和文件不得继续人工拼接"
  systemctl stop "$SERVICE_NAME" >/dev/null 2>&1
  if systemctl is-active --quiet "$SERVICE_NAME"; then
    warn "$SERVICE_NAME 常规停止后仍为 active，执行服务范围内的强制终止"
    systemctl kill --kill-who=all --signal=SIGKILL "$SERVICE_NAME" >/dev/null 2>&1
    systemctl stop "$SERVICE_NAME" >/dev/null 2>&1
  fi
  if systemctl is-active --quiet "$SERVICE_NAME"; then
    warn "严重：$SERVICE_NAME 仍为 active，禁止恢复流量或继续发布"
  fi
  if [ -n "$nginx_displaced" ]; then
    restore_displaced_path_before_commit "$nginx_restore_committed" "$NGINX_ROOT" \
      "$nginx_displaced" "${NGINX_ROOT}.partial-$stamp" 'Nginx' \
      || warn 'Nginx 未提交移动动作自动回退失败，保留现场待人工核对'
  fi
  if [ -n "$fragment_displaced" ]; then
    restore_displaced_path_before_commit "$systemd_restore_committed" "$backup_systemd_fragment" \
      "$fragment_displaced" "${backup_systemd_fragment}.partial-$stamp" 'systemd unit' \
      || warn 'systemd 未提交移动动作自动回退失败，保留现场待人工核对'
  fi
  if [ -n "$fragment_dropin_displaced" ]; then
    restore_displaced_path_before_commit "$systemd_restore_committed" "${backup_systemd_fragment}.d" \
      "$fragment_dropin_displaced" "${backup_systemd_fragment}.d.partial-$stamp" 'systemd drop-in' \
      || warn 'systemd drop-in 未提交移动动作自动回退失败，保留现场待人工核对'
  fi
  [ "$systemd_restore_committed" = 1 ] || systemctl daemon-reload >/dev/null 2>&1 || true
  printf 'ROLLBACK_STATE=FAILED\nFAILED_AT=%s\nFAILED_PHASE=%s\nEXIT_CODE=%s\nSOURCE_BACKUP=%s\nFAILURE_SNAPSHOT=%s\nSERVICE_ACTIVE_AFTER_SAFETY=%s\n' \
    "$(date -Iseconds)" "$restore_phase" "$rc" "$backup_dir" "$failure_dir" \
    "$(systemctl is-active "$SERVICE_NAME" 2>/dev/null || true)" > "$rollback_release/ROLLBACK_FAILED" || true
  chmod 0600 "$rollback_release/ROLLBACK_FAILED" 2>/dev/null || true
  exit "$rc"
}
trap on_restore_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

log '恢复同点数据库；这是完整 DROP/CREATE + 导入，不执行任何手工反向 DDL'
restore_mysql_args=()
if [ -n "${MYSQL_DEFAULTS_FILE:-}" ]; then restore_mysql_args+=("--defaults-extra-file=$MYSQL_DEFAULTS_FILE"); fi
restore_mysql_args+=(--default-character-set=utf8mb4)
gzip -dc "$database_dump" | "$MYSQL_BIN" "${restore_mysql_args[@]}"
assert_exact_legacy_baseline

restore_phase='uploads-restore'
uploads_extract="$(mktemp -d "$APP_ROOT/.uploads-restore.XXXXXX")"
tar --numeric-owner -xzf "$backup_dir/files/uploads.tar.gz" -C "$uploads_extract"
restored_upload_root="$uploads_extract/$(basename "$backup_upload_target")"
[ -d "$restored_upload_root" ] || die 'uploads 备份顶层目录与目标不一致'
if [ -e "$backup_upload_target" ]; then
  displaced_upload="$backup_upload_target.failed-$stamp"
  [ ! -e "$displaced_upload" ] || die "uploads 保留目标已存在：$displaced_upload"
  mv -- "$backup_upload_target" "$displaced_upload"
  warn "原 uploads 已可恢复地移动到 $displaced_upload"
fi
install -d -o root -g root -m 0755 "$(dirname "$backup_upload_target")"
mv -- "$restored_upload_root" "$backup_upload_target"
rmdir "$uploads_extract"
(cd "$backup_upload_target" && sha256sum -c "$backup_dir/inventory/uploads-files.sha256")
atomic_symlink_swap "$backup_upload_target" "$UPLOAD_LINK"

restore_phase='jar-web-env-restore'
install -d -o root -g "$SERVICE_GROUP" -m 0750 "$rollback_release/backend"
install -o root -g "$SERVICE_GROUP" -m 0640 "$backup_dir/runtime/site-platform.jar" "$rollback_release/backend/site-platform.jar"
assert_readable_by_user "$SERVICE_USER" "$rollback_release/backend/site-platform.jar"
atomic_symlink_swap "$rollback_release/backend/site-platform.jar" "$JAR_LINK"

web_extract="$(mktemp -d "$APP_ROOT/.web-restore.XXXXXX")"
tar --no-same-owner -xzf "$backup_dir/runtime/frontend.tar.gz" -C "$web_extract"
restored_web_root="$web_extract/$(basename "$(read_kv "$targets_file" WEB_TARGET)")"
[ -d "$restored_web_root" ] || die 'Web 备份顶层目录与记录目标不一致'
mv -- "$restored_web_root" "$rollback_release/frontend"
rmdir "$web_extract"
find "$rollback_release/frontend" -type d -exec chmod 0755 {} +
find "$rollback_release/frontend" -type f -exec chmod 0644 {} +
chown -R root:"$NGINX_GROUP" "$rollback_release/frontend"
assert_readable_by_user "$NGINX_USER" "$rollback_release/frontend/index.html"
atomic_symlink_swap "$rollback_release/frontend" "$WEB_LINK"

install -o root -g root -m 0600 "$backup_dir/config/site-platform.env" "$ENV_FILE"

restore_phase='nginx-systemd-restore'
nginx_displaced="${NGINX_ROOT}.failed-$stamp"
[ ! -e "$nginx_displaced" ] || die "Nginx 故障配置保留目录已存在：$nginx_displaced"
mv -- "$NGINX_ROOT" "$nginx_displaced"
tar --numeric-owner -xzf "$backup_dir/config/nginx.tar.gz" -C /
nginx_restore_committed=1

fragment_displaced="${backup_systemd_fragment}.failed-$stamp"
[ ! -e "$fragment_displaced" ] || die "systemd 故障 unit 保留文件已存在：$fragment_displaced"
if [ -e "$backup_systemd_fragment" ]; then mv -- "$backup_systemd_fragment" "$fragment_displaced"; fi
if [ -d "${backup_systemd_fragment}.d" ]; then
  fragment_dropin_displaced="${backup_systemd_fragment}.d.failed-$stamp"
  mv -- "${backup_systemd_fragment}.d" "$fragment_dropin_displaced"
fi
tar --numeric-owner -xzf "$backup_dir/config/systemd.tar.gz" -C /
systemd_restore_committed=1

systemctl daemon-reload
nginx -t
systemctl reload nginx.service
restore_phase='old-service-start-and-health'
systemctl start "$SERVICE_NAME"
systemctl is-active --quiet "$SERVICE_NAME"
wait_for_backend_health

printf 'ROLLBACK_STATE=COMPLETE\nCOMPLETED_AT=%s\nSOURCE_BACKUP=%s\nFAILURE_SNAPSHOT=%s\n' \
  "$(date -Iseconds)" "$backup_dir" "$failure_dir" > "$rollback_release/ROLLBACK_COMPLETE"
chmod 0600 "$rollback_release/ROLLBACK_COMPLETE"
restore_complete=1
trap - EXIT INT TERM HUP
log "同点回滚完成：旧数据库 68 表/13 标记、配对 uploads、旧 JAR/Web/env/Nginx/systemd 已恢复"
log "故障现场保存在：$failure_dir；被置换的 Nginx 配置保存在：$nginx_displaced"
