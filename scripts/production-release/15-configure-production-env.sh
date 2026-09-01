#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：15-configure-production-env.sh --backup-dir /已验证停机备份 \
       [--dry-run] --confirm CONFIGURE_DOCUMENT_CIRCULATION_KEY

仅在后端已停止且旧 env 已进入同点备份后执行。若图纸 scene 密钥缺失，则生成独立随机值并
原子写回环境文件；不会打印密钥。同时要求 APP_SCHEDULING_ENABLED=true，拒绝持久化一次性迁移变量。
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
require_confirmation CONFIGURE_DOCUMENT_CIRCULATION_KEY "$confirmation"
require_commands systemctl grep stat mktemp install openssl
verify_backup_directory "$backup_dir"
assert_service_inactive
[ -f "$ENV_FILE" ] || die "环境文件不存在：$ENV_FILE"
[ "$(stat -c '%a' "$ENV_FILE")" = 600 ] || die '环境文件权限必须为 600'

doc_key_count="$(grep -Ec '^DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY=' "$ENV_FILE" || true)"
scheduling_count="$(grep -Ec '^APP_SCHEDULING_ENABLED=' "$ENV_FILE" || true)"
[ "$doc_key_count" -le 1 ] || die '图纸 scene 密钥存在重复配置'
[ "$scheduling_count" -le 1 ] || die 'APP_SCHEDULING_ENABLED 存在重复配置'
if grep -Eq '^SITE_ACCESS_LEGACY_REENCRYPTION_|visitor-legacy-reencrypt' "$ENV_FILE"; then
  die '环境文件持久化了一次性访客迁移参数，拒绝继续'
fi

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：将原子更新 $ENV_FILE；缺失时生成图纸独立随机密钥，并保证调度显式为 true"
  exit 0
fi

require_root
temp_env="$(mktemp "$(dirname "$ENV_FILE")/.site-platform.env.next.XXXXXX")"
env_installed=0
cleanup() {
  local rc=$?
  rm -f -- "$temp_env"
  if [ "$rc" -ne 0 ] && [ "$env_installed" = 1 ]; then
    warn '新环境门禁失败，正在从同点备份恢复原环境文件'
    install -o root -g root -m 0600 "$backup_dir/config/site-platform.env" "$ENV_FILE" || true
  fi
  exit "$rc"
}
trap cleanup EXIT
cp --preserve=mode,ownership -- "$ENV_FILE" "$temp_env"

if [ "$doc_key_count" = 0 ]; then
  document_key="$(openssl rand -base64 48)"
  printf '\nDOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY=%s\n' "$document_key" >> "$temp_env"
  unset document_key
  log '已生成图纸收发独立随机密钥（值未输出）'
fi

if [ "$scheduling_count" = 0 ]; then
  printf 'APP_SCHEDULING_ENABLED=true\n' >> "$temp_env"
elif ! grep -Eq '^APP_SCHEDULING_ENABLED=true$' "$temp_env"; then
  die '现有 APP_SCHEDULING_ENABLED 不是 true；拒绝隐式覆盖，请先人工核定'
fi

chmod 0600 "$temp_env"
chown --reference="$ENV_FILE" "$temp_env"
mv -f -- "$temp_env" "$ENV_FILE"
env_installed=1

unit_name="dianxinyun-env-gate-$(date +%s)-$$"
systemd-run --quiet --wait --collect --pipe --unit "$unit_name" \
  --property=Type=exec --property="EnvironmentFile=$ENV_FILE" \
  /bin/bash -c '
    set -eu
    export LC_ALL=C
    [ "$${#DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY}" -ge 32 ]
    [ "$${APP_SCHEDULING_ENABLED:-}" = true ]
    [ "$$DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY" != "$${JWT_SECRET:-}" ]
    [ "$$DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY" != "$${VISITOR_DATA_ENCRYPTION_KEY:-}" ]
    [ "$$DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY" != "$${SEAL_SCENE_ENCRYPTION_KEY:-}" ]
  '

env_installed=0
trap - EXIT
log '正式环境门禁通过：图纸密钥长度/独立性合格，调度显式启用；所有密钥值均未输出'
