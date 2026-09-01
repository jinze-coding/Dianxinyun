#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法：55-record-transition-compatibility.sh --proof-file /绝对/新证明文件 \
       --operator <验收人> --evidence <工单或证据引用> [--dry-run] \
       --confirm TRANSITION_WEB_CURRENT_MINI_COMPATIBILITY_PASSED

只有人工完成 transition Web + 新后端 + 当前正式小程序的登录、原质量流程、现有外访、资料附件、
巡检和质量读取/写入冒烟后才能确认。本脚本不替代业务验收，只固化停点证据。
EOF
}

proof_file=''
operator=''
evidence=''
confirmation=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --proof-file) [ "$#" -ge 2 ] || usage_error '--proof-file 缺少值'; proof_file="$2"; shift ;;
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
[ -n "$operator" ] || usage_error '必须提供 --operator'
[ -n "$evidence" ] || usage_error '必须提供 --evidence'
assert_safe_absolute_path "$proof_file" '兼容停点证明'
case "$operator$evidence" in *$'\n'*|*$'\r'*) die '验收人和证据引用不得包含换行' ;; esac
require_confirmation TRANSITION_WEB_CURRENT_MINI_COMPATIBILITY_PASSED "$confirmation"
require_commands systemctl curl grep sha256sum install
systemctl is-active --quiet "$SERVICE_NAME" || die '新后端不是 active'
health_check_backend

current_web="$(canonical_existing_path "$WEB_LINK" '当前 transition Web')"
manifest="$(dirname "$current_web")/RELEASE_MANIFEST.txt"
[ -f "$manifest" ] || die '当前 Web 缺少 RELEASE_MANIFEST.txt'
[ "$(manifest_value "$manifest" VARIANT)" = transition ] || die '当前 Web 不是 transition'
[ "$(manifest_value "$manifest" MEETING_CREATION_ENABLED)" = false ] || die 'transition Web 错误启用会议创建'

if [ "$DRY_RUN" = 1 ]; then
  log "DRY-RUN：自动门禁通过；不会写入兼容停点证明 $proof_file"
  exit 0
fi

require_root
[ ! -e "$proof_file" ] || die "证明文件已存在，拒绝覆盖：$proof_file"
install -d -o root -g root -m 0700 "$(dirname "$proof_file")"
{
  printf 'STATUS=PASSED\n'
  printf 'RECORDED_AT=%s\n' "$(date -Iseconds)"
  printf 'OPERATOR=%s\n' "$operator"
  printf 'EVIDENCE=%s\n' "$evidence"
  printf 'WEB_VARIANT=transition\n'
  printf 'MEETING_CREATION_ENABLED=false\n'
  printf 'OLD_WEB_REPLACED=YES\n'
  printf 'CURRENT_PRODUCTION_MINI_COMPATIBILITY=PASSED\n'
} > "$proof_file"
chmod 0600 "$proof_file"
sha256sum "$proof_file" > "$proof_file.sha256"
log "transition 兼容停点证据已记录：$proof_file"
