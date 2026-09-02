#!/usr/bin/env bash

set -Eeuo pipefail
IFS=$'\n\t'
umask 077

PRODUCTION_MARKER='/run/site-platform-offline-worker.state'
PRODUCTION_COORDINATION_LOCK='/run/site-platform-offline-worker.lock'
coordination_lock="${OFFLINE_WORKER_COORDINATION_LOCK_FILE:-$PRODUCTION_COORDINATION_LOCK}"
coordination_lock_held=0

fail() {
  printf '[offline-worker-wrapper][ERROR] %s\n' "$*" >&2
  exit 1
}

[ "$#" -ge 6 ] || fail '参数不足'
marker_file="$1"
unit_name="$2"
worker_user="$3"
worker_group="$4"
shift 4

if [ "$marker_file" != "$PRODUCTION_MARKER" ] \
    && [ "${PRODUCTION_RELEASE_SELF_TEST:-0}" != 1 ]; then
  fail "离线 worker 标记路径不可覆盖：$marker_file"
fi
if [ "$coordination_lock" != "$PRODUCTION_COORDINATION_LOCK" ] \
    && [ "${PRODUCTION_RELEASE_SELF_TEST:-0}" != 1 ]; then
  fail "离线 worker 协调锁路径不可覆盖：$coordination_lock"
fi
case "$coordination_lock" in
  /*) ;;
  *) fail "离线 worker 协调锁必须是绝对路径：$coordination_lock" ;;
esac
case "$unit_name" in
  ''|*[!A-Za-z0-9_.@:-]*) fail "unit 名非法：$unit_name" ;;
esac

load_marker() {
  local marker_stat
  marker_state=''
  marker_unit=''
  extra_line=''
  [ -f "$marker_file" ] && [ ! -L "$marker_file" ] \
    || fail "离线 worker 标记缺失或不安全：$marker_file"
  marker_stat="$(stat -c '%a:%u:%g:%h' -- "$marker_file")" \
    || fail '无法读取离线 worker 标记属性'
  [ "$marker_stat" = '600:0:0:1' ] \
    || fail '离线 worker 标记权限、属主或硬链接数非法'
  {
    IFS= read -r marker_state || fail '离线 worker 标记缺少状态字段'
    IFS= read -r marker_unit || fail '离线 worker 标记缺少 unit 字段'
    if IFS= read -r extra_line; then
      fail '离线 worker 标记字段数量非法'
    fi
  } < "$marker_file"
  case "$marker_state" in
    STATE=SUBMITTING|STATE=RUNNING) ;;
    *) fail "离线 worker 标记状态非法：$marker_state" ;;
  esac
  [ "$marker_unit" = "UNIT_NAME=$unit_name" ] \
    || fail '离线 worker 标记与 transient unit 不匹配'
}

cleanup_marker() {
  local rc=$? current_state='' current_unit='' lock_parent=''
  trap - EXIT INT TERM HUP
  set +e
  if [ "$coordination_lock_held" != 1 ]; then
    lock_parent="${coordination_lock%/*}"
    if ! command -v flock >/dev/null 2>&1 \
        || [ ! -d "$lock_parent" ] || [ -L "$lock_parent" ]; then
      printf '[offline-worker-wrapper][ERROR] worker 退出前无法验证协调锁环境，保留标记\n' >&2
      exit 1
    fi
    if [ -e "$coordination_lock" ] || [ -L "$coordination_lock" ]; then
      if [ ! -f "$coordination_lock" ] || [ -L "$coordination_lock" ]; then
        printf '[offline-worker-wrapper][ERROR] worker 退出时协调锁不安全，保留标记\n' >&2
        exit 1
      fi
    fi
    if ! exec 9>"$coordination_lock" || ! flock -x 9; then
      printf '[offline-worker-wrapper][ERROR] worker 退出时无法取得协调锁，保留标记\n' >&2
      exit 1
    fi
    coordination_lock_held=1
  fi
  if [ -f "$marker_file" ] && [ ! -L "$marker_file" ] \
      && [ "$(stat -c '%a:%u:%g:%h' -- "$marker_file" 2>/dev/null)" = '600:0:0:1' ]; then
    {
      IFS= read -r current_state
      IFS= read -r current_unit
    } < "$marker_file"
    if { [ "$current_state" = 'STATE=SUBMITTING' ] \
          || [ "$current_state" = 'STATE=RUNNING' ]; } \
        && [ "$current_unit" = "UNIT_NAME=$unit_name" ]; then
      rm -f -- "$marker_file" || rc=1
    else
      printf '[offline-worker-wrapper][ERROR] 拒绝清理不匹配的 worker 标记\n' >&2
      rc=1
    fi
  else
    printf '[offline-worker-wrapper][ERROR] worker 结束前标记丢失或被替换\n' >&2
    rc=1
  fi
  if [ "$coordination_lock_held" = 1 ]; then
    flock -u 9 || rc=1
    exec 9>&- || rc=1
    coordination_lock_held=0
  fi
  exit "$rc"
}
trap cleanup_marker EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

case "$worker_user" in
  ''|*[!A-Za-z0-9_.-]*) fail '运行用户名非法' ;;
esac
case "$worker_group" in
  ''|*[!A-Za-z0-9_.-]*) fail '运行组名非法' ;;
esac
for command_name in flock runuser stat mktemp chmod chown mv rm; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name"
done

lock_parent="${coordination_lock%/*}"
[ -d "$lock_parent" ] && [ ! -L "$lock_parent" ] \
  || fail "离线 worker 协调锁父目录不存在或不安全：$lock_parent"
if [ -e "$coordination_lock" ] || [ -L "$coordination_lock" ]; then
  [ -f "$coordination_lock" ] && [ ! -L "$coordination_lock" ] \
    || fail "离线 worker 协调锁不是普通文件或是软链：$coordination_lock"
fi
exec 9>"$coordination_lock" || fail '无法打开离线 worker 协调锁'
flock -x 9 || fail '无法取得离线 worker 协调锁'
coordination_lock_held=1

load_marker
[ "$marker_state" = 'STATE=SUBMITTING' ] \
  || fail '离线 worker 启动时标记必须处于 SUBMITTING'

running_marker="$(mktemp "${marker_file}.running.XXXXXX")" \
  || fail '无法创建 RUNNING 临时标记'
if ! printf 'STATE=RUNNING\nUNIT_NAME=%s\n' "$unit_name" > "$running_marker"; then
  rm -f -- "$running_marker"
  fail '无法写入 RUNNING 临时标记'
fi
if ! chmod 0600 "$running_marker"; then
  rm -f -- "$running_marker"
  fail '无法设置 RUNNING 临时标记权限'
fi
if ! chown root:root "$running_marker"; then
  rm -f -- "$running_marker"
  fail '无法设置 RUNNING 临时标记属主'
fi
load_marker
[ "$marker_state" = 'STATE=SUBMITTING' ] \
  || { rm -f -- "$running_marker"; fail '离线 worker 标记在接管前发生变化'; }
if ! mv -f -- "$running_marker" "$marker_file"; then
  rm -f -- "$running_marker"
  fail '无法原子切换离线 worker 标记到 RUNNING'
fi
load_marker
[ "$marker_state" = 'STATE=RUNNING' ] \
  || fail '离线 worker RUNNING 标记自检失败'
flock -u 9 || fail '无法释放离线 worker 协调锁'
exec 9>&-
coordination_lock_held=0

runuser -u "$worker_user" -g "$worker_group" --preserve-environment -- "$@"
