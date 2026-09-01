#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：00-preflight-readonly.sh [--database-only]

只读核对正式旧库必须精确等于 68 张表和 13 个迁移标记；默认还核对 systemd、JAR、Web、
uploads、环境文件、Nginx 和磁盘。数据库认证只允许 MYSQL_DEFAULTS_FILE 或服务器现有受控配置。
EOF
}

database_only=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --database-only) database_only=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

require_commands "$MYSQL_BIN" diff sort mktemp wc awk
init_mysql_args

log "核对数据库 $MYSQL_DATABASE 的精确旧基线"
assert_exact_legacy_baseline

if [ "$database_only" = 1 ]; then
  log '只读数据库基线通过：68 张表、13 个准确迁移标记'
  exit 0
fi

require_commands systemctl readlink stat grep nginx df sha256sum
systemctl is-active --quiet "$SERVICE_NAME" || die "$SERVICE_NAME 当前不是 active"
[ -L "$JAR_LINK" ] || die "JAR 正式入口不是软链：$JAR_LINK"
[ -L "$WEB_LINK" ] || die "Web 正式入口不是软链：$WEB_LINK"
[ -L "$UPLOAD_LINK" ] || die "uploads 正式入口不是软链：$UPLOAD_LINK"

jar_target="$(canonical_existing_path "$JAR_LINK" '当前 JAR')"
web_target="$(canonical_existing_path "$WEB_LINK" '当前 Web')"
upload_target="$(canonical_existing_path "$UPLOAD_LINK" '当前 uploads')"
[ -f "$jar_target" ] || die "当前 JAR 目标不是文件：$jar_target"
[ -d "$web_target" ] || die "当前 Web 目标不是目录：$web_target"
[ -d "$upload_target" ] || die "当前 uploads 目标不是目录：$upload_target"

[ -r "$ENV_FILE" ] || die "环境文件不可读：$ENV_FILE"
env_mode="$(stat -c '%a' "$ENV_FILE")"
[ "$env_mode" = 600 ] || die "环境文件权限必须是 600，当前为 $env_mode"

for key in DB_URL DB_USERNAME DB_PASSWORD REDIS_HOST REDIS_PORT REDIS_PASSWORD JWT_SECRET \
  VISITOR_DATA_ENCRYPTION_KEY SEAL_SCENE_ENCRYPTION_KEY SPRING_PROFILES_ACTIVE FILE_STORAGE_TYPE; do
  [ "$(grep -Ec "^${key}=" "$ENV_FILE")" = 1 ] || die "环境文件必须且只能配置一次 $key"
done

if grep -Eq '^SITE_ACCESS_LEGACY_REENCRYPTION_|visitor-legacy-reencrypt' "$ENV_FILE"; then
  die '正式环境文件不得持久化访客一次性重加密 Profile 或参数'
fi

nginx -t
df -h -- "$APP_ROOT" "$upload_target"

log "只读预检通过；JAR=$jar_target"
log "只读预检通过；Web=$web_target"
log "只读预检通过；uploads=$upload_target"
log "当前 JAR SHA-256=$(sha256sum "$jar_target" | awk '{print $1}')"
