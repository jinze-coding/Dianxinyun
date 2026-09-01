#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：
  30-visitor-reencrypt.sh --mode verify --jar /已暂存/site-platform.jar \
    --backup-dir /已验证停机备份 --log-file /新日志文件

  30-visitor-reencrypt.sh --mode apply --jar /已暂存/site-platform.jar \
    --backup-dir /已验证停机备份 --log-file /新日志文件 \
    --expected-total N --expected-current N --expected-legacy N --expected-token-matches N \
    --expected-fingerprint <64位摘要> --confirm APPLY_VISITOR_LEGACY_REENCRYPT_V1

  30-visitor-reencrypt.sh --mode post-verify --jar /已暂存/site-platform.jar \
    --backup-dir /已验证停机备份 --log-file /新日志文件

VERIFY 默认只读；APPLY 必须逐项传入同一停机窗口 VERIFY 输出。脚本通过 systemd EnvironmentFile
载入正式密钥，不 source 环境文件，不输出密钥。所有一次性参数都显式 export 后传给离线进程。
EOF
}

mode=''
jar_path=''
backup_dir=''
log_file=''
confirmation=''
expected_total=''
expected_current=''
expected_legacy=''
expected_token_matches=''
expected_fingerprint=''

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mode) [ "$#" -ge 2 ] || usage_error '--mode 缺少值'; mode="$2"; shift ;;
    --jar) [ "$#" -ge 2 ] || usage_error '--jar 缺少值'; jar_path="$2"; shift ;;
    --backup-dir) [ "$#" -ge 2 ] || usage_error '--backup-dir 缺少值'; backup_dir="$2"; shift ;;
    --log-file) [ "$#" -ge 2 ] || usage_error '--log-file 缺少值'; log_file="$2"; shift ;;
    --expected-total) [ "$#" -ge 2 ] || usage_error '--expected-total 缺少值'; expected_total="$2"; shift ;;
    --expected-current) [ "$#" -ge 2 ] || usage_error '--expected-current 缺少值'; expected_current="$2"; shift ;;
    --expected-legacy) [ "$#" -ge 2 ] || usage_error '--expected-legacy 缺少值'; expected_legacy="$2"; shift ;;
    --expected-token-matches) [ "$#" -ge 2 ] || usage_error '--expected-token-matches 缺少值'; expected_token_matches="$2"; shift ;;
    --expected-fingerprint) [ "$#" -ge 2 ] || usage_error '--expected-fingerprint 缺少值'; expected_fingerprint="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

case "$mode" in verify|apply|post-verify) ;; *) usage_error '--mode 必须是 verify、apply 或 post-verify' ;; esac
[ -n "$jar_path" ] || usage_error '必须提供 --jar'
[ -n "$backup_dir" ] || usage_error '必须提供 --backup-dir'
[ -n "$log_file" ] || usage_error '必须提供 --log-file'
assert_safe_absolute_path "$jar_path" '离线 JAR'
assert_safe_absolute_path "$log_file" '迁移日志文件'
require_commands systemd-run systemctl runuser "$MYSQL_BIN" grep tee sha256sum
init_mysql_args
verify_backup_directory "$backup_dir"
assert_service_inactive
[ -f "$jar_path" ] || die "离线 JAR 不存在：$jar_path"
assert_readable_by_user "$SERVICE_USER" "$jar_path"
[ ! -e "$log_file" ] || die "日志文件已存在，拒绝覆盖：$log_file"
[ -d "$(dirname "$log_file")" ] || die "日志父目录不存在：$(dirname "$log_file")"

table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'")"
marker_count="$(mysql_query 'SELECT COUNT(*) FROM sys_data_migration')"
repair_marker_count="$(mysql_query "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='20260901_SITE_ACCESS_REENCRYPT_LEGACY_DEVELOPMENT_KEY_V1'")"
[ "$table_count" = 98 ] || die "访客修复前表数必须是 98，当前为 $table_count"

if [ "$mode" = post-verify ]; then
  [ "$marker_count" = 25 ] || die "复验前迁移标记必须是 25，当前为 $marker_count"
  [ "$repair_marker_count" = 1 ] || die '复验前访客修复标记必须唯一存在'
else
  [ "$marker_count" = 24 ] || die "初次 VERIFY/APPLY 前迁移标记必须是 24，当前为 $marker_count"
  [ "$repair_marker_count" = 0 ] || die '访客修复标记已存在，请改用 post-verify'
fi

if [ "$mode" = apply ]; then
  require_confirmation APPLY_VISITOR_LEGACY_REENCRYPT_V1 "$confirmation"
  for pair in "expected-total:$expected_total" "expected-current:$expected_current" \
    "expected-legacy:$expected_legacy" "expected-token-matches:$expected_token_matches"; do
    value="${pair#*:}"
    case "$value" in ''|*[!0-9]*) die "${pair%%:*} 必须是非负整数" ;; esac
  done
  validate_sha256 "$expected_fingerprint" '候选指纹'
fi

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：离线模式=$mode，JAR=$jar_path；不会启动 Java 或写数据库"
  exit 0
fi

require_root
export SPRING_PROFILES_ACTIVE='prod,visitor-legacy-reencrypt'
export SPRING_MAIN_WEB_APPLICATION_TYPE='none'
export APP_SCHEDULING_ENABLED='false'
export SITE_ACCESS_LEGACY_REENCRYPTION_ENABLED='true'
if [ "$mode" = apply ]; then
  export SITE_ACCESS_LEGACY_REENCRYPTION_MODE='APPLY'
  export SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_TOTAL="$expected_total"
  export SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_CURRENT="$expected_current"
  export SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_LEGACY="$expected_legacy"
  export SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_TOKEN_MATCHES="$expected_token_matches"
  export SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_FINGERPRINT="${expected_fingerprint,,}"
  export SITE_ACCESS_LEGACY_REENCRYPTION_CONFIRMATION='APPLY_VISITOR_LEGACY_REENCRYPT_V1'
else
  export SITE_ACCESS_LEGACY_REENCRYPTION_MODE='VERIFY'
fi

unit_name="dianxinyun-visitor-reencrypt-${mode//-/_}-$(date +%s)-$$"
systemd_args=(
  --quiet --wait --collect --pipe --unit "$unit_name"
  --property=Type=exec --property="User=$SERVICE_USER" --property="Group=$SERVICE_GROUP"
  --property="WorkingDirectory=$APP_ROOT" --property="EnvironmentFile=$ENV_FILE"
  --setenv=SPRING_PROFILES_ACTIVE --setenv=SPRING_MAIN_WEB_APPLICATION_TYPE
  --setenv=APP_SCHEDULING_ENABLED --setenv=SITE_ACCESS_LEGACY_REENCRYPTION_ENABLED
  --setenv=SITE_ACCESS_LEGACY_REENCRYPTION_MODE
)
if [ "$mode" = apply ]; then
  systemd_args+=(
    --setenv=SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_TOTAL
    --setenv=SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_CURRENT
    --setenv=SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_LEGACY
    --setenv=SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_TOKEN_MATCHES
    --setenv=SITE_ACCESS_LEGACY_REENCRYPTION_EXPECTED_FINGERPRINT
    --setenv=SITE_ACCESS_LEGACY_REENCRYPTION_CONFIRMATION
  )
fi

systemd-run "${systemd_args[@]}" "$JAVA_BIN" -jar "$jar_path" 2>&1 | tee "$log_file"

if [ "$mode" = verify ]; then
  grep -q '访客历史密钥 VERIFY 完成' "$log_file" || die 'VERIFY 日志缺少完成标记'
elif [ "$mode" = apply ]; then
  grep -q '访客历史密钥 APPLY 完成' "$log_file" || die 'APPLY 日志缺少完成标记'
  [ "$(mysql_query 'SELECT COUNT(*) FROM sys_data_migration')" = 25 ] || die 'APPLY 后迁移标记总数不是 25'
  [ "$(mysql_query "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='20260901_SITE_ACCESS_REENCRYPT_LEGACY_DEVELOPMENT_KEY_V1'")" = 1 ] \
    || die 'APPLY 后访客修复标记没有唯一写入'
else
  grep -q '访客历史密钥迁移标记已存在，后验校验通过' "$log_file" || die '复验日志缺少后验通过标记'
  [ "$(mysql_query 'SELECT COUNT(*) FROM sys_data_migration')" = 25 ] || die '复验后迁移标记总数不是 25'
fi
sha256sum "$log_file" > "$log_file.sha256"
log "访客历史密钥 $mode 完成；日志只含运行信息，密钥值未输出：$log_file"
