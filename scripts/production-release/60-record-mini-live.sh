#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：60-record-mini-live.sh --proof-file /绝对/新证明文件 --transition-proof /兼容停点证明 \
       --operator <验收人> --evidence <微信后台发布记录或证据引用> [--dry-run] \
       --confirm MINI_0_1_8_IS_LIVE_FOR_USERS

只有微信后台 0.1.8 审核发布、用户端确认已生效、合法域名/隐私和 iOS/Android 真机闭环完成后执行。
正式构建已显式关闭 DCloud uniStatistics；只允许封存包中的 mp-weixin/，禁止工作区 dev/build 目录。
EOF
}

proof_file=''
transition_proof=''
operator=''
evidence=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --proof-file) [ "$#" -ge 2 ] || usage_error '--proof-file 缺少值'; proof_file="$2"; shift ;;
    --transition-proof) [ "$#" -ge 2 ] || usage_error '--transition-proof 缺少值'; transition_proof="$2"; shift ;;
    --operator) [ "$#" -ge 2 ] || usage_error '--operator 缺少值'; operator="$2"; shift ;;
    --evidence) [ "$#" -ge 2 ] || usage_error '--evidence 缺少值'; evidence="$2"; shift ;;
    --confirm) [ "$#" -ge 2 ] || usage_error '--confirm 缺少值'; confirmation="$2"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --help|-h) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
  shift
done

[ -n "$proof_file" ] || usage_error '必须提供 --proof-file'
[ -n "$transition_proof" ] || usage_error '必须提供 --transition-proof'
[ -n "$operator" ] || usage_error '必须提供 --operator'
[ -n "$evidence" ] || usage_error '必须提供 --evidence'
assert_safe_absolute_path "$proof_file" '小程序生效证明'
[ -f "$transition_proof" ] || die 'transition 兼容停点证明不存在'
[ -f "$transition_proof.sha256" ] || die 'transition 兼容停点证明缺少 SHA-256 sidecar'
(cd "$(dirname "$transition_proof")" && sha256sum -c "$(basename "$transition_proof.sha256")")
[ "$(read_kv "$transition_proof" STATUS)" = PASSED ] || die 'transition 兼容停点未通过'
case "$operator$evidence" in *$'\n'*|*$'\r'*) die '验收人和证据引用不得包含换行' ;; esac
require_confirmation MINI_0_1_8_IS_LIVE_FOR_USERS "$confirmation"
require_commands systemctl curl grep sha256sum install
systemctl is-active --quiet "$SERVICE_NAME" || die '记录小程序生效前新后端必须 active'
health_check_backend
current_web="$(canonical_existing_path "$WEB_LINK" '当前 transition Web')"
current_manifest="$(dirname "$current_web")/RELEASE_MANIFEST.txt"
[ -f "$current_manifest" ] || die '当前 Web 缺少 transition manifest'
[ "$(manifest_value "$current_manifest" VARIANT)" = transition ] || die '记录小程序生效前当前 Web 必须仍为 transition'

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：不会写入小程序 0.1.8 正式生效证明 $proof_file"
  exit 0
fi

require_root
[ ! -e "$proof_file" ] || die "证明文件已存在，拒绝覆盖：$proof_file"
install -d -o root -g root -m 0700 "$(dirname "$proof_file")"
{
  printf 'STATUS=RELEASED\n'
  printf 'VERSION=0.1.8\n'
  printf 'USER_VISIBLE_VERIFIED=YES\n'
  printf 'DCLOUD_UNI_STATISTICS=DISABLED_VERIFIED\n'
  printf 'LEGAL_DOMAINS_AND_PRIVACY=VERIFIED\n'
  printf 'IOS_ANDROID_REAL_DEVICE=PASSED\n'
  printf 'RECORDED_AT=%s\n' "$(date -Iseconds)"
  printf 'OPERATOR=%s\n' "$operator"
  printf 'EVIDENCE=%s\n' "$evidence"
} > "$proof_file"
chmod 0600 "$proof_file"
sha256sum "$proof_file" > "$proof_file.sha256"
log "小程序 0.1.8 正式生效证据已记录：$proof_file；此时才允许切 final Web"
