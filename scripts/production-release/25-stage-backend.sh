#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：25-stage-backend.sh --jar /更新包/artifacts/site-platform-1.0.0.jar \
       --expected-sha256 <64位摘要> --destination /opt/site-platform/releases/<发布ID>/backend \
       --backup-dir /已验证停机备份 [--dry-run] --confirm STAGE_BACKEND_DIANXINYUN

只把新 JAR 安装到全新版本目录，不切换正式软链、不启动服务。目标 JAR 固定为
root:site-platform 0640，并验证 site-platform 用户可读。
EOF
}

jar_source=''
expected_sha=''
destination=''
backup_dir=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --jar) [ "$#" -ge 2 ] || usage_error '--jar 缺少值'; jar_source="$2"; shift ;;
    --expected-sha256) [ "$#" -ge 2 ] || usage_error '--expected-sha256 缺少值'; expected_sha="$2"; shift ;;
    --destination) [ "$#" -ge 2 ] || usage_error '--destination 缺少值'; destination="$2"; shift ;;
    --backup-dir) [ "$#" -ge 2 ] || usage_error '--backup-dir 缺少值'; backup_dir="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

[ -n "$jar_source" ] || usage_error '必须提供 --jar'
[ -n "$expected_sha" ] || usage_error '必须提供 --expected-sha256'
[ -n "$destination" ] || usage_error '必须提供 --destination'
[ -n "$backup_dir" ] || usage_error '必须提供 --backup-dir'
assert_safe_absolute_path "$jar_source" 'JAR 源文件'
assert_safe_absolute_path "$destination" '后端版本目录'
case "$destination" in "$APP_ROOT"/releases/*/backend) ;; *) die "后端版本目录必须位于 $APP_ROOT/releases/<发布ID>/backend" ;; esac
validate_sha256 "$expected_sha" 'JAR 期待摘要'
require_confirmation STAGE_BACKEND_DIANXINYUN "$confirmation"
require_commands sha256sum unzip install runuser systemctl
if [ "$DRY_RUN" != 1 ]; then
  require_root
  acquire_release_operation_lock || die '无法取得生产发布全程互斥锁'
fi
verify_backup_directory "$backup_dir"
assert_maintenance_lock "$backup_dir" || die '生产维护锁与本次停机备份不匹配'
assert_service_inactive
assert_service_boot_disabled || die '维护期主服务开机自启未保持 disabled'
[ -f "$jar_source" ] || die "JAR 不存在：$jar_source"
[ ! -e "$destination" ] || die "后端版本目录已存在，拒绝覆盖：$destination"

actual_sha="$(sha256sum "$jar_source" | awk '{print $1}')"
[ "$actual_sha" = "${expected_sha,,}" ] || die "JAR SHA-256 不匹配：$actual_sha"
unzip -t "$jar_source" >/dev/null

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：JAR 完整性通过；将安装到 $destination/site-platform.jar 并设置 root:$SERVICE_GROUP 0640"
  exit 0
fi

install -d -o root -g "$SERVICE_GROUP" -m 0750 "$destination"
install -o root -g "$SERVICE_GROUP" -m 0640 "$jar_source" "$destination/site-platform.jar"
printf '%s  site-platform.jar\n' "$actual_sha" > "$destination/SHA256SUMS"
printf 'STAGED_AT=%s\nSOURCE_SHA256=%s\nSTATUS=STAGED_NOT_ACTIVE\n' "$(date -Iseconds)" "$actual_sha" \
  > "$destination/STAGED_BACKEND.env"
chown root:"$SERVICE_GROUP" "$destination/SHA256SUMS" "$destination/STAGED_BACKEND.env"
chmod 0640 "$destination/SHA256SUMS" "$destination/STAGED_BACKEND.env"
assert_readable_by_user "$SERVICE_USER" "$destination/site-platform.jar"
(cd "$destination" && sha256sum -c SHA256SUMS)
log "新后端已安全暂存，尚未切换：$destination/site-platform.jar"
