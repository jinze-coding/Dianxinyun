#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法（后端启动前）：
  50-install-web.sh --stage transition --artifact /Dianxinyun-web-transition-prod-*.tar.gz \
    --expected-sha256 <摘要> --destination /opt/site-platform/releases/<ID>/web-transition \
    --backup-dir /停机备份 --confirm INSTALL_TRANSITION_WEB_DIANXINYUN

用法（小程序 0.1.8 已正式生效后）：
  50-install-web.sh --stage final --artifact /Dianxinyun-web-final-prod-*.tar.gz \
    --expected-sha256 <摘要> --destination /opt/site-platform/releases/<ID>/web-final \
    --backup-dir /停机备份 --transition-proof /兼容停点证明 \
    --mini-live-proof /小程序生效证明 --confirm INSTALL_FINAL_WEB_DIANXINYUN

两种模式均支持 --dry-run。脚本拒绝 AppleDouble/不安全成员，核对内部摘要及 transition/final
发布标记，统一设置目录 0755、文件 0644、root:www-data，并原子切换 Web 软链。
EOF
}

stage=''
artifact=''
expected_sha=''
destination=''
backup_dir=''
transition_proof=''
mini_live_proof=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --stage) [ "$#" -ge 2 ] || usage_error '--stage 缺少值'; stage="$2"; shift ;;
    --artifact) [ "$#" -ge 2 ] || usage_error '--artifact 缺少值'; artifact="$2"; shift ;;
    --expected-sha256) [ "$#" -ge 2 ] || usage_error '--expected-sha256 缺少值'; expected_sha="$2"; shift ;;
    --destination) [ "$#" -ge 2 ] || usage_error '--destination 缺少值'; destination="$2"; shift ;;
    --backup-dir) [ "$#" -ge 2 ] || usage_error '--backup-dir 缺少值'; backup_dir="$2"; shift ;;
    --transition-proof) [ "$#" -ge 2 ] || usage_error '--transition-proof 缺少值'; transition_proof="$2"; shift ;;
    --mini-live-proof) [ "$#" -ge 2 ] || usage_error '--mini-live-proof 缺少值'; mini_live_proof="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

case "$stage" in transition|final) ;; *) usage_error '--stage 必须是 transition 或 final' ;; esac
[ -n "$artifact" ] || usage_error '必须提供 --artifact'
[ -n "$expected_sha" ] || usage_error '必须提供 --expected-sha256'
[ -n "$destination" ] || usage_error '必须提供 --destination'
[ -n "$backup_dir" ] || usage_error '必须提供 --backup-dir'
assert_safe_absolute_path "$artifact" 'Web 归档'
assert_safe_absolute_path "$destination" 'Web 版本目录'
case "$destination" in "$APP_ROOT"/releases/*/web-"$stage") ;; *) die "Web 版本目录必须位于 $APP_ROOT/releases/<发布ID>/web-$stage" ;; esac
validate_sha256 "$expected_sha" 'Web 归档期待摘要'
if [ "$stage" = transition ]; then
  require_confirmation INSTALL_TRANSITION_WEB_DIANXINYUN "$confirmation"
else
  require_confirmation INSTALL_FINAL_WEB_DIANXINYUN "$confirmation"
fi
require_commands sha256sum tar gzip find sort diff grep awk install runuser nginx systemctl curl mktemp
verify_backup_directory "$backup_dir"
[ -f "$artifact" ] || die "Web 归档不存在：$artifact"
[ ! -e "$destination" ] || die "Web 版本目录已存在，拒绝覆盖：$destination"

actual_sha="$(sha256sum "$artifact" | awk '{print $1}')"
[ "$actual_sha" = "${expected_sha,,}" ] || die "Web 归档 SHA-256 不匹配：$actual_sha"
gzip -t "$artifact"
assert_no_appledouble_or_unsafe_members "$artifact"

if [ "$stage" = transition ]; then
  assert_service_inactive
else
  systemctl is-active --quiet "$SERVICE_NAME" || die '安装 final Web 前新后端必须 active'
  [ -f "$transition_proof" ] || die 'final Web 缺少 transition 兼容停点证明'
  [ -f "$mini_live_proof" ] || die 'final Web 缺少小程序正式生效证明'
  [ -f "$transition_proof.sha256" ] || die 'transition 证明缺少 SHA-256 sidecar'
  [ -f "$mini_live_proof.sha256" ] || die '小程序证明缺少 SHA-256 sidecar'
  (cd "$(dirname "$transition_proof")" && sha256sum -c "$(basename "$transition_proof.sha256")")
  (cd "$(dirname "$mini_live_proof")" && sha256sum -c "$(basename "$mini_live_proof.sha256")")
  [ "$(read_kv "$transition_proof" STATUS)" = PASSED ] || die 'transition 兼容停点证明无效'
  [ "$(read_kv "$mini_live_proof" STATUS)" = RELEASED ] || die '小程序生效证明无效'
  [ "$(read_kv "$mini_live_proof" VERSION)" = 0.1.8 ] || die '生效的小程序版本不是 0.1.8'
  [ "$(read_kv "$mini_live_proof" USER_VISIBLE_VERIFIED)" = YES ] || die '没有确认用户端小程序已生效'

  current_web="$(canonical_existing_path "$WEB_LINK" '当前 transition Web')"
  current_manifest="$(dirname "$current_web")/RELEASE_MANIFEST.txt"
  [ -f "$current_manifest" ] || die '当前 Web 缺少 transition manifest'
  [ "$(manifest_value "$current_manifest" VARIANT)" = transition ] || die 'final 切换前当前 Web 不是 transition'
fi

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：Web $stage 归档通过外层校验和安全成员检查；不会解包或切换 $WEB_LINK"
  exit 0
fi

require_root
parent_dir="$(dirname "$destination")"
install -d -o root -g "$NGINX_GROUP" -m 0755 "$parent_dir"
staging_dir="$(mktemp -d "$parent_dir/.web-${stage}.staging.XXXXXX")"
link_swapped=0
old_web_target="$(canonical_existing_path "$WEB_LINK" '当前 Web')"
cleanup() {
  local rc=$?
  if [ "$rc" -ne 0 ] && [ "$link_swapped" = 1 ]; then
    warn 'Web 切换失败，正在恢复上一 Web 软链并 reload Nginx'
    atomic_symlink_swap "$old_web_target" "$WEB_LINK" || true
    nginx -t >/dev/null 2>&1 && systemctl reload nginx.service || true
  fi
  if [ -d "$staging_dir" ]; then rm -rf -- "$staging_dir"; fi
  exit "$rc"
}
trap cleanup EXIT

tar --no-same-owner -xzf "$artifact" -C "$staging_dir"
[ -d "$staging_dir/dist" ] || die 'Web 包缺少 dist/'
[ -f "$staging_dir/ARTIFACT_FILES.sha256" ] || die 'Web 包缺少 ARTIFACT_FILES.sha256'
[ -f "$staging_dir/RELEASE_MANIFEST.txt" ] || die 'Web 包缺少 RELEASE_MANIFEST.txt'
if find "$staging_dir" -type l -o -name '._*' -o -name '.DS_Store' | grep -q .; then
  die 'Web 解包目录含软链、AppleDouble 或 .DS_Store'
fi
unexpected_root="$(find "$staging_dir" -mindepth 1 -maxdepth 1 ! -name dist ! -name ARTIFACT_FILES.sha256 ! -name RELEASE_MANIFEST.txt -print -quit)"
[ -z "$unexpected_root" ] || die "Web 包含非预期根成员：$unexpected_root"
(cd "$staging_dir/dist" && sha256sum -c ../ARTIFACT_FILES.sha256)

manifest="$staging_dir/RELEASE_MANIFEST.txt"
[ "$(manifest_value "$manifest" VARIANT)" = "$stage" ] || die 'Web manifest VARIANT 与安装阶段不符'
[ "$(manifest_value "$manifest" API_BASE)" = /api/v1 ] || die 'Web API_BASE 不是 /api/v1'
if [ "$stage" = transition ]; then
  expected_enabled=false
  expected_marker='dianxinyun-web-transition-meeting-disabled'
  forbidden_marker='dianxinyun-web-final-meeting-enabled'
else
  expected_enabled=true
  expected_marker='dianxinyun-web-final-meeting-enabled'
  forbidden_marker='dianxinyun-web-transition-meeting-disabled'
fi
[ "$(manifest_value "$manifest" MEETING_CREATION_ENABLED)" = "$expected_enabled" ] || die 'Web 会议创建开关与阶段不符'
[ "$(manifest_value "$manifest" RELEASE_MARKER)" = "$expected_marker" ] || die 'Web 发布标记与阶段不符'
grep -R --binary-files=text -q "$expected_marker" "$staging_dir/dist" || die 'Web 内容缺少预期发布标记'
if grep -R --binary-files=text -q "$forbidden_marker" "$staging_dir/dist"; then
  die 'Web 内容混入另一发布阶段标记'
fi

find "$staging_dir" -type d -exec chmod 0755 {} +
find "$staging_dir" -type f -exec chmod 0644 {} +
chown -R root:"$NGINX_GROUP" "$staging_dir"
assert_traversable_by_user "$NGINX_USER" "$staging_dir/dist"
assert_readable_by_user "$NGINX_USER" "$staging_dir/dist/index.html"

mv -- "$staging_dir" "$destination"
staging_dir=''
nginx -t
atomic_symlink_swap "$destination/dist" "$WEB_LINK"
link_swapped=1
nginx -t
systemctl reload nginx.service

status="$(curl --silent --show-error --max-time 20 --output /dev/null --write-out '%{http_code}' "$PUBLIC_BASE_URL/")"
[ "$status" = 200 ] || die "Web 首页 HTTP 状态不是 200：$status"
trap - EXIT
log "Web $stage 已原子切换：$WEB_LINK -> $destination/dist"
if [ "$stage" = transition ]; then
  log 'transition 会议创建保持关闭；现在才允许启动新后端，随后进入当前正式小程序兼容停点'
else
  log 'final Web 已切换；仅因小程序 0.1.8 生效证明和 transition 兼容证明均通过才获准执行'
fi
