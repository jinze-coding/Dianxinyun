#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
BASELINE_DIR="$SCRIPT_DIR/baseline"

fail() { printf '[production-release-self-test][ERROR] %s\n' "$*" >&2; exit 1; }

assert_first_before() {
  local file="$1" first_pattern="$2" second_pattern="$3" label="$4"
  local first_line second_line
  first_line="$(grep -nF -- "$first_pattern" "$file" | awk -F: 'NR == 1 {print $1; exit}' || true)"
  second_line="$(grep -nF -- "$second_pattern" "$file" | awk -F: 'NR == 1 {print $1; exit}' || true)"
  [ -n "$first_line" ] && [ -n "$second_line" ] \
    || fail "$label 缺少顺序断言所需语句"
  [ "$first_line" -lt "$second_line" ] \
    || fail "$label 执行顺序错误：$first_pattern 必须早于 $second_pattern"
}

for command_name in bash awk chmod cmp date sort diff wc sha256sum grep find ln mktemp sed sleep unzip; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name"
done

if ! command -v flock >/dev/null 2>&1; then
  command -v python3 >/dev/null 2>&1 || fail '宿主缺少 flock，且没有 Python 3 可提供离线 fcntl.flock 兼容层'
  flock() {
    python3 - "$@" <<'PY'
import fcntl
import sys

arguments = sys.argv[1:]
fd = int(arguments[-1])
operation = fcntl.LOCK_UN if "-u" in arguments else fcntl.LOCK_EX
if "-n" in arguments:
    operation |= fcntl.LOCK_NB
try:
    fcntl.flock(fd, operation)
except BlockingIOError:
    raise SystemExit(1)
PY
  }
fi

if [ -d "$REPO_ROOT/backend/src/main/resources/sql/migrations" ]; then
  run_context='source'
  release_root=''
  migration_dir="$REPO_ROOT/backend/src/main/resources/sql/migrations"
elif [ -d "$SCRIPT_DIR/../database/migrations" ]; then
  run_context='bundle'
  release_root="$(cd "$SCRIPT_DIR/.." && pwd -P)"
  migration_dir="$release_root/database/migrations"
else
  fail '既不是完整源码目录，也不是解包后的生产更新目录'
fi

self_heal_control="$SCRIPT_DIR/05-self-heal-control.sh"
offline_worker_recovery="$SCRIPT_DIR/06-offline-worker-recovery.sh"
self_heal_assets="$SCRIPT_DIR/assets/self-heal"
self_heal_dropin="$self_heal_assets/60-self-heal.conf"
self_heal_watchdog_service="$self_heal_assets/site-platform-watchdog.service"
self_heal_watchdog_timer="$self_heal_assets/site-platform-watchdog.timer"
self_heal_watchdog_script="$self_heal_assets/site-platform-watchdog.sh"
offline_worker_wrapper="$SCRIPT_DIR/lib/offline-worker-wrapper.sh"
self_heal_required_paths=(
  "$self_heal_control"
  "$offline_worker_recovery"
  "$self_heal_dropin"
  "$self_heal_watchdog_service"
  "$self_heal_watchdog_timer"
  "$self_heal_watchdog_script"
  "$offline_worker_wrapper"
)
for self_heal_path in "${self_heal_required_paths[@]}"; do
  [ -f "$self_heal_path" ] && [ ! -L "$self_heal_path" ] \
    || fail "缺少生产自恢复普通文件或路径是软链：${self_heal_path#"$SCRIPT_DIR"/}"
done

tables="$BASELINE_DIR/expected-tables.txt"
markers="$BASELINE_DIR/expected-migration-markers.txt"
plan="$BASELINE_DIR/migration-plan.tsv"
[ "$(wc -l < "$tables" | tr -d ' ')" = 68 ] || fail '精确旧表清单不是 68 行'
[ "$(wc -l < "$markers" | tr -d ' ')" = 13 ] || fail '精确旧迁移标记不是 13 行'
diff -u "$tables" <(LC_ALL=C sort -u "$tables") >/dev/null || fail '旧表清单未排序或有重复'
diff -u "$markers" <(LC_ALL=C sort -u "$markers") >/dev/null || fail '旧迁移标记未排序或有重复'

expected_orders=$'01\n02\n03\n04\n05\n06\n07\n08\n09\n10\n11'
actual_orders="$(awk -F'|' '!/^#/ && NF {print $1}' "$plan")"
[ "$actual_orders" = "$expected_orders" ] || fail '迁移序号不是严格 01..11'
expected_counts=$'68\n70\n77\n91\n91\n94\n96\n97\n97\n97\n98'
actual_counts="$(awk -F'|' '!/^#/ && NF {print $3}' "$plan")"
[ "$actual_counts" = "$expected_counts" ] || fail '逐项预期表数发生漂移'

while IFS='|' read -r order filename expected_tables expected_marker expected_sha; do
  case "$order" in ''|'#'*) continue ;; esac
  sql_file="$migration_dir/$filename"
  [ -f "$sql_file" ] || fail "缺少迁移：$filename"
  actual_sha="$(sha256sum "$sql_file" | awk '{print $1}')"
  [ "$actual_sha" = "$expected_sha" ] || fail "$filename SHA-256 与计划不一致"
  grep -Fq "$expected_marker" "$sql_file" || fail "$filename 不含预期标记 $expected_marker"
  if grep -EIn '=[[:space:]]*VALUES[[:space:]]*\(' "$sql_file"; then
    fail "$filename 仍含 MySQL 已弃用的 VALUES(col) 引用"
  fi
done < "$plan"

document_circulation_migration="$migration_dir/20260826_document_circulation.sql"
if grep -Fq 'CREATE TABLE IF NOT EXISTS sys_data_migration' "$document_circulation_migration"; then
  fail '图纸收发迁移会在精确旧基线上产生 sys_data_migration 已存在提示'
fi
grep -Fq 'SET @create_sys_data_migration_sql = IF(' "$document_circulation_migration" \
  || fail '图纸收发迁移缺少无提示的迁移标记表兼容门禁'
for warning_source in \
  'INSERT IGNORE INTO sys_role_menu' \
  'INSERT IGNORE INTO sys_role_permission' \
  'INSERT IGNORE INTO sys_data_migration'; do
  if grep -Fq "$warning_source" "$document_circulation_migration"; then
    fail "图纸收发迁移仍含重复执行时会产生 1062 Warning 的语句：$warning_source"
  fi
done
grep -Fq 'ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id' "$document_circulation_migration" \
  || fail '图纸收发菜单授权缺少无告警幂等写入'
grep -Fq 'ON DUPLICATE KEY UPDATE role_id = sys_role_permission.role_id' "$document_circulation_migration" \
  || fail '图纸收发操作权限授权缺少无告警幂等写入'
grep -Fq 'ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key' "$document_circulation_migration" \
  || fail '图纸收发迁移标记缺少无告警幂等写入'

shell_script_count=0
while IFS= read -r script; do
  [ -n "$script" ] || continue
  bash -n "$script" || fail "Bash 语法失败：$script"
  shell_script_count=$((shell_script_count + 1))
done < <(find "$SCRIPT_DIR" -type f -name '*.sh' -print | LC_ALL=C sort)
[ "$shell_script_count" -gt 0 ] || fail 'production-release 下没有可检查的 Bash 脚本'

if grep -Eiq '^[[:space:]]*ConditionPathExists[[:space:]]*=.*maintenance\.lock' "$self_heal_dropin"; then
  fail '主 site-platform.service drop-in 不得用维护锁阻止 40/90 显式启动'
fi
grep -Fq 'ConditionPathExists=!/etc/site-platform/maintenance.lock' "$self_heal_watchdog_service" \
  || fail 'watchdog oneshot 未使用固定生产维护锁 ConditionPathExists 门禁'
grep -Fq "PRODUCTION_MAINTENANCE_LOCK='/etc/site-platform/maintenance.lock'" "$self_heal_control" \
  || fail '自恢复管理脚本未固定使用生产维护锁路径'
grep -Fq '[ -e "$PRODUCTION_MAINTENANCE_LOCK" ] || [ -L "$PRODUCTION_MAINTENANCE_LOCK" ]' \
  "$self_heal_control" || fail '自恢复管理脚本未对普通锁和悬空软链 fail-closed'
grep -Fq 'MAINTENANCE_LOCK="${MAINTENANCE_LOCK:-/etc/site-platform/maintenance.lock}"' \
  "$self_heal_watchdog_script" || fail 'watchdog 未默认使用固定生产维护锁路径'
grep -Fq 'WATCHDOG_LOCK="${WATCHDOG_LOCK:-/run/site-platform-watchdog.lock}"' \
  "$self_heal_watchdog_script" || fail 'watchdog 未与发布脚本共用固定协调锁路径'
grep -Fq 'exec 9>"$WATCHDOG_LOCK"' "$self_heal_watchdog_script" \
  || fail 'watchdog 未在检查前打开固定协调锁'
grep -Fq 'if ! flock -n 9; then' "$self_heal_watchdog_script" \
  || fail 'watchdog 未使用非阻塞排他协调锁'
watchdog_maintenance_guard_count="$(grep -Fc \
  '[ -e "$MAINTENANCE_LOCK" ] || [ -L "$MAINTENANCE_LOCK" ]' \
  "$self_heal_watchdog_script" || true)"
[ "$watchdog_maintenance_guard_count" = 3 ] \
  || fail "watchdog 必须在初检、启动前和健康等待三处 fail-closed 检查维护锁，当前为 $watchdog_maintenance_guard_count"
grep -Fq 'inactive|failed)' "$self_heal_watchdog_script" \
  || fail 'watchdog 缺少仅针对 inactive/failed 的修复状态门禁'
watchdog_main_start_count="$(grep -Ec \
  'systemctl([[:space:]]+--[^[:space:]]+)*[[:space:]]+start([[:space:]]+--[^[:space:]]+)*[[:space:]]+.*SERVICE_NAME' \
  "$self_heal_watchdog_script" || true)"
[ "$watchdog_main_start_count" = 1 ] \
  || fail "watchdog 对主服务的 start 动作必须恰好一次，当前为 $watchdog_main_start_count"
assert_first_before "$self_heal_watchdog_script" \
  'inactive|failed)' 'systemctl start --no-block "$SERVICE_NAME"' \
  'watchdog inactive/failed 保守修复'
if grep -Eq 'systemctl([[:space:]]+--[^[:space:]]+)*[[:space:]]+(restart|try-restart)([[:space:]]|$)' \
    "$self_heal_watchdog_script"; then
  fail 'watchdog 不得 restart/try-restart 主服务'
fi
if grep -Eq 'systemctl([[:space:]]+--[^[:space:]]+)*[[:space:]]+(stop|restart|try-restart)[[:space:]].*SERVICE_NAME' \
    "$self_heal_control"; then
  fail '自恢复管理脚本不得 stop/restart/try-restart 主服务'
fi

watchdog_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-watchdog-self-test.XXXXXX")"
trap 'rm -rf -- "$watchdog_test_dir"' EXIT
run_watchdog_scenario() {
  local scenario="$1" initial_state="$2" healthy="$3" apt_active="$4"
  local scenario_dir="$watchdog_test_dir/$scenario"
  mkdir -p "$scenario_dir"
  printf '%s\n' "$initial_state" > "$scenario_dir/state"
  (
    export TEST_WATCHDOG_STATE="$scenario_dir/state"
    export TEST_WATCHDOG_ACTIONS="$scenario_dir/actions"
    export TEST_WATCHDOG_HEALTHY="$healthy"
    export TEST_WATCHDOG_APT_ACTIVE="$apt_active"
    export WATCHDOG_LOCK="$scenario_dir/watchdog.lock"
    export MAINTENANCE_LOCK="$scenario_dir/maintenance.lock"
    export HEALTH_ATTEMPTS=2
    export HEALTH_INTERVAL_SECONDS=0
    systemctl() {
      local action="$1" unit
      shift
      case "$action" in
        is-active)
          unit="$1"
          case "$unit" in
            site-platform.service) cat "$TEST_WATCHDOG_STATE" ;;
            mysql.service|redis-server.service) printf 'active\n' ;;
            apt-daily.service|apt-daily-upgrade.service)
              if [ "$TEST_WATCHDOG_APT_ACTIVE" = 1 ]; then printf 'active\n'; else printf 'inactive\n'; return 3; fi
              ;;
            *) printf 'inactive\n'; return 3 ;;
          esac
          ;;
        start)
          printf 'start %s\n' "$*" >> "$TEST_WATCHDOG_ACTIONS"
          printf 'active\n' > "$TEST_WATCHDOG_STATE"
          ;;
        *) return 1 ;;
      esac
    }
    curl() {
      [ "$TEST_WATCHDOG_HEALTHY" = 1 ] || return 1
      printf '{"code":200}\n200'
    }
    flock() { return 0; }
    export -f systemctl curl flock
    bash "$self_heal_watchdog_script" --repair-inactive
  )
}

run_watchdog_scenario active-unhealthy active 0 0 \
  || fail 'watchdog 对 active-but-unhealthy 场景不应执行失败退出或重启'
[ ! -s "$watchdog_test_dir/active-unhealthy/actions" ] \
  || fail 'watchdog 错误修改了 active-but-unhealthy 主服务'

run_watchdog_scenario inactive-repair inactive 1 0 \
  || fail 'watchdog 未能完成 inactive 单次启动模型'
[ "$(wc -l < "$watchdog_test_dir/inactive-repair/actions" | tr -d ' ')" = 1 ] \
  || fail 'watchdog 对 inactive 主服务的启动次数不是 1'
grep -qx 'start --no-block site-platform.service' "$watchdog_test_dir/inactive-repair/actions" \
  || fail 'watchdog 未使用唯一允许的 start --no-block 动作'

run_watchdog_scenario apt-active inactive 1 1 \
  || fail 'watchdog 在 apt 升级期间应保守跳过'
[ ! -s "$watchdog_test_dir/apt-active/actions" ] \
  || fail 'watchdog 在 apt 升级期间错误启动主服务'

mkdir -p "$watchdog_test_dir/coordination-busy"
printf 'inactive\n' > "$watchdog_test_dir/coordination-busy/state"
(
  exec 6>"$watchdog_test_dir/coordination-busy/watchdog.lock"
  flock -x 6
  export TEST_WATCHDOG_STATE="$watchdog_test_dir/coordination-busy/state"
  export TEST_WATCHDOG_ACTIONS="$watchdog_test_dir/coordination-busy/actions"
  export WATCHDOG_LOCK="$watchdog_test_dir/coordination-busy/watchdog.lock"
  export MAINTENANCE_LOCK="$watchdog_test_dir/coordination-busy/maintenance.lock"
  systemctl() {
    local action="$1" unit
    shift
    case "$action" in
      is-active)
        unit="$1"
        case "$unit" in
          site-platform.service) printf 'inactive\n'; return 3 ;;
          mysql.service|redis-server.service) printf 'active\n' ;;
          apt-daily.service|apt-daily-upgrade.service) printf 'inactive\n'; return 3 ;;
          *) printf 'inactive\n'; return 3 ;;
        esac
        ;;
      start)
        printf 'start %s\n' "$*" >> "$TEST_WATCHDOG_ACTIONS"
        ;;
      *) return 1 ;;
    esac
  }
  curl() { printf '{"code":200}\n200'; }
  export -f systemctl curl
  if declare -F flock >/dev/null 2>&1; then
    export -f flock
  fi
  bash "$self_heal_watchdog_script" --repair-inactive 6>&-
) || fail 'watchdog 在协调锁已占用时未保守跳过'
[ ! -s "$watchdog_test_dir/coordination-busy/actions" ] \
  || fail 'watchdog 在发布脚本持有协调锁时错误启动主服务'

mkdir -p "$watchdog_test_dir/dangling-lock"
ln -s -- "$watchdog_test_dir/dangling-lock/missing-target" \
  "$watchdog_test_dir/dangling-lock/maintenance.lock"
printf 'inactive\n' > "$watchdog_test_dir/dangling-lock/state"
set +e
(
  set -e
  export TEST_WATCHDOG_STATE="$watchdog_test_dir/dangling-lock/state"
  export TEST_WATCHDOG_ACTIONS="$watchdog_test_dir/dangling-lock/actions"
  export WATCHDOG_LOCK="$watchdog_test_dir/dangling-lock/watchdog.lock"
  export MAINTENANCE_LOCK="$watchdog_test_dir/dangling-lock/maintenance.lock"
  systemctl() { printf 'inactive\n'; return 3; }
  curl() { printf '{"code":200}\n200'; }
  flock() { return 0; }
  export -f systemctl curl flock
  bash "$self_heal_watchdog_script" --repair-inactive
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] || fail 'watchdog 对悬空维护锁未保守跳过'
[ ! -s "$watchdog_test_dir/dangling-lock/actions" ] \
  || fail 'watchdog 在悬空维护锁存在时错误启动主服务'
rm -rf -- "$watchdog_test_dir"
trap - EXIT

grep -Fq 'wait_for_backend_health' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '回滚脚本未使用有界本机/公网健康轮询'
grep -Fq 'trap on_restore_exit EXIT' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '回滚脚本未使用可覆盖显式 exit 的 EXIT 安全兜底'
grep -Fq 'ROLLBACK_FAILED' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '回滚失败未保留阶段和服务状态标记'
grep -Fq 'nginx_restore_committed=1' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '回滚脚本未在 Nginx 同点配置解包后提交恢复状态'
grep -Fq 'systemd_restore_committed=1' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '回滚脚本未在 systemd 同点配置解包后提交恢复状态'
grep -Fq 'restore_displaced_path_before_commit "$nginx_restore_committed"' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '回滚脚本未按提交状态决定是否撤销 Nginx 移动动作'
grep -Fq 'restore_displaced_path_before_commit "$systemd_restore_committed"' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '回滚脚本未按提交状态决定是否撤销 systemd 移动动作'
if grep -Fq 'sleep 3' "$SCRIPT_DIR/90-rollback.sh"; then
  fail '回滚脚本仍使用固定 3 秒等待后单次检查'
fi
grep -Fq 'wait_for_backend_health' "$SCRIPT_DIR/40-activate-backend.sh" \
  || fail '后端激活脚本未使用有界本机/公网健康轮询'
grep -Fq 'trap on_activation_exit EXIT' "$SCRIPT_DIR/40-activate-backend.sh" \
  || fail '后端激活脚本未覆盖显式 exit 的失败清理'
grep -Fq 'acquire_watchdog_coordination_lock' "$SCRIPT_DIR/10-stop-and-backup.sh" \
  || fail '停服备份脚本未在维护窗口前取得 watchdog 协调锁'
grep -Fq 'create_maintenance_lock "$backup_dir"' "$SCRIPT_DIR/10-stop-and-backup.sh" \
  || fail '停服备份脚本未在停服前创建生产维护锁'
grep -Fq 'assert_maintenance_lock "$backup_dir"' "$SCRIPT_DIR/40-activate-backend.sh" \
  || fail '后端激活脚本未要求与同点备份匹配的生产维护锁'
grep -Fq 'release_maintenance_lock "$backup_dir"' "$SCRIPT_DIR/40-activate-backend.sh" \
  || fail '后端激活成功路径未解除生产维护锁'
grep -Fq 'acquire_watchdog_coordination_lock' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '同点回滚脚本未在维护窗口前取得 watchdog 协调锁'
grep -Fq 'ensure_maintenance_lock "$backup_dir"' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '同点回滚脚本未在停服前确保生产维护锁存在'
grep -Fq 'release_maintenance_lock "$backup_dir"' "$SCRIPT_DIR/90-rollback.sh" \
  || fail '同点回滚成功路径未解除生产维护锁'
assert_first_before "$SCRIPT_DIR/10-stop-and-backup.sh" \
  'create_maintenance_lock "$backup_dir"' 'systemctl stop "$SERVICE_NAME"' \
  '停服备份维护锁'
assert_first_before "$SCRIPT_DIR/40-activate-backend.sh" \
  'assert_maintenance_lock "$backup_dir"' 'systemctl start "$SERVICE_NAME"' \
  '后端激活维护锁'
assert_first_before "$SCRIPT_DIR/40-activate-backend.sh" \
  'activation_complete=1' 'release_maintenance_lock "$backup_dir"' \
  '后端激活成功解锁'
assert_first_before "$SCRIPT_DIR/90-rollback.sh" \
  'ensure_maintenance_lock "$backup_dir"' 'systemctl stop "$SERVICE_NAME"' \
  '同点回滚维护锁'
assert_first_before "$SCRIPT_DIR/90-rollback.sh" \
  'restore_complete=1' 'release_maintenance_lock "$backup_dir"' \
  '同点回滚成功解锁'
if sed -n '/on_activation_exit()/,/^}/p' "$SCRIPT_DIR/40-activate-backend.sh" \
    | grep -Fq 'release_maintenance_lock'; then
  fail '后端激活失败兜底不得解除生产维护锁'
fi
if sed -n '/on_restore_exit()/,/^}/p' "$SCRIPT_DIR/90-rollback.sh" \
    | grep -Fq 'release_maintenance_lock'; then
  fail '同点回滚失败兜底不得解除生产维护锁'
fi
for locked_stage in \
  15-configure-production-env.sh \
  20-run-migrations.sh \
  25-stage-backend.sh \
  30-visitor-reencrypt.sh; do
  grep -Fq 'assert_maintenance_lock "$backup_dir"' "$SCRIPT_DIR/$locked_stage" \
    || fail "$locked_stage 未在离线操作前核对生产维护锁"
done
grep -Fq 'assert_maintenance_lock "$backup_dir"' "$SCRIPT_DIR/50-install-web.sh" \
  || fail 'transition Web 安装未核对生产维护锁'
grep -Fq 'maintenance_lock_present' "$SCRIPT_DIR/50-install-web.sh" \
  || fail 'final Web 安装未拒绝未闭合的生产维护锁'
grep -Fq "validate_maintenance_lock_path || die '生产维护锁路径校验失败'" \
  "$SCRIPT_DIR/50-install-web.sh" \
  || fail 'final Web 安装未先固定生产维护锁路径，环境覆盖可能绕过真实锁'

for serialized_stage in \
  10-stop-and-backup.sh \
  15-configure-production-env.sh \
  20-run-migrations.sh \
  25-stage-backend.sh \
  30-visitor-reencrypt.sh \
  40-activate-backend.sh \
  50-install-web.sh \
  55-record-transition-compatibility.sh \
  60-record-mini-live.sh \
  90-rollback.sh; do
  grep -Fq 'acquire_release_operation_lock' "$SCRIPT_DIR/$serialized_stage" \
    || fail "$serialized_stage 未取得生产发布全程互斥锁"
done
grep -Fq "RELEASE_OPERATION_LOCK='/run/site-platform-release.lock'" "$self_heal_control" \
  || fail '自恢复安装/核验未使用固定生产发布全程互斥锁'
grep -Fq 'acquire_release_operation_lock' "$self_heal_control" \
  || fail '自恢复安装/核验未取得生产发布全程互斥锁'
sed -n '/^  install)/,/^    ;;/p' "$self_heal_control" \
  | grep -Fq 'acquire_release_operation_lock' \
  || fail '自恢复 install 分支未取得生产发布全程互斥锁'
sed -n '/^  verify)/,/^    ;;/p' "$self_heal_control" \
  | grep -Fq 'acquire_release_operation_lock' \
  || fail '自恢复 verify 分支未取得生产发布全程互斥锁'
grep -Fq "OFFLINE_WORKER_MARKER='/run/site-platform-offline-worker.state'" "$self_heal_control" \
  || fail '自恢复控制未拒绝仍在运行的离线数据库 worker'
grep -Fq 'assert_unit_persistently_enabled site-platform-watchdog.timer' "$self_heal_control" \
  || fail '自恢复核验未精确要求 watchdog timer 持久 enabled'
grep -Fq 'assert_unit_persistently_enabled "$SERVICE_NAME"' "$self_heal_control" \
  || fail '自恢复核验未精确要求主服务持久 enabled'
grep -Fq '[ "$enablement_state" = enabled ]' "$self_heal_control" \
  || fail '自恢复持久启用门禁未精确匹配 enabled'
if grep -Fq 'systemctl is-enabled --quiet' "$self_heal_control"; then
  fail '自恢复核验不得用 is-enabled --quiet 把 enabled-runtime/static 误当持久开机自启'
fi
grep -Fq -- '--property=Requires --value' "$self_heal_control" \
  || fail '自恢复核验未检查会传播依赖停机的 Requires'
grep -Fq -- '--property=BindsTo --value' "$self_heal_control" \
  || fail '自恢复核验未检查会传播依赖停机的 BindsTo'
grep -Fq 'create_offline_worker_marker "$unit_name"' "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  || fail '访客离线修复未在提交 transient worker 前创建存活标记'
grep -Fq '"$worker_wrapper" "$OFFLINE_WORKER_MARKER_FILE" "$unit_name"' \
  "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  || fail '访客离线修复未通过 root 包装器覆盖 worker 完整生命周期'
grep -Fq "PRODUCTION_MARKER='/run/site-platform-offline-worker.state'" "$offline_worker_wrapper" \
  || fail '离线 worker 包装器未固定使用生产存活标记'
grep -Fq "PRODUCTION_COORDINATION_LOCK='/run/site-platform-offline-worker.lock'" \
  "$offline_worker_wrapper" \
  || fail '离线 worker 包装器未使用固定 sidecar 协调锁'
grep -Fq "PRODUCTION_OFFLINE_WORKER_COORDINATION_LOCK_FILE='/run/site-platform-offline-worker.lock'" \
  "$SCRIPT_DIR/lib/common.sh" \
  || fail '生产脚本未固定离线 worker sidecar 协调锁'
grep -Fq 'acquire_release_operation_lock ALLOW_OFFLINE_WORKER_RECOVERY' "$offline_worker_recovery" \
  || fail '离线 worker 故障恢复未使用专用受控发布锁策略'
grep -Fq 'acquire_offline_worker_coordination_lock' "$offline_worker_recovery" \
  || fail '离线 worker 故障恢复未取得 sidecar 协调锁'
grep -Fq 'remove_offline_worker_marker_if_matches' "$offline_worker_recovery" \
  || fail '离线 worker 故障恢复未精确清理匹配标记'
grep -Fq '[ "$confirmation" = CLEAR_STALE_OFFLINE_WORKER_AFTER_DATABASE_REVIEW ]' \
  "$offline_worker_recovery" \
  || fail '离线 worker 故障恢复确认语可能被 DRY_RUN 环境绕过'
grep -Fq 'pipeline_status=("${PIPESTATUS[@]}")' "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  || fail '访客离线修复未分别捕获 systemd-run 与日志管道状态'
grep -Fq 'reconcile_submitting_offline_worker_marker "$unit_name"' \
  "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  || fail '访客离线修复提交失败后没有受控核对 SUBMITTING 标记'
grep -Fq -- '--property="EnvironmentFile=$ENV_FILE"' "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  || fail '访客离线修复未继续从正式 EnvironmentFile 继承密钥'
if grep -Fq -- '--setenv=' "$SCRIPT_DIR/30-visitor-reencrypt.sh"; then
  fail '访客离线控制量不得只用会被 EnvironmentFile 覆盖的 systemd --setenv'
fi
grep -Fq '"APP_SCHEDULING_ENABLED=$APP_SCHEDULING_ENABLED"' \
  "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  || fail '访客离线 wrapper 命令未在 Java exec 前最终关闭在线调度'
grep -Fq '"$SERVICE_USER" "$SERVICE_GROUP" "${worker_command[@]}"' \
  "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  || fail '访客离线控制量没有通过受控 wrapper 命令传递'
grep -Fq 'cleanup_unsubmitted_marker' "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  || fail '访客离线修复缺少 systemd 提交前失败的标记清理兜底'
assert_first_before "$SCRIPT_DIR/30-visitor-reencrypt.sh" \
  'create_offline_worker_marker "$unit_name"' 'worker_submission_started=1' \
  '访客离线 worker 提交前标记'
assert_first_before "$offline_worker_wrapper" \
  'trap cleanup_marker EXIT' 'case "$worker_user" in' \
  '离线 worker 包装器前置失败清理'
assert_first_before "$offline_worker_wrapper" \
  'if ! mv -f -- "$running_marker" "$marker_file"; then' 'runuser -u "$worker_user"' \
  '离线 worker RUNNING 原子标记'
assert_first_before "$offline_worker_wrapper" \
  '[ "$marker_state" = '\''STATE=RUNNING'\'' ]' 'runuser -u "$worker_user"' \
  '离线 worker RUNNING 标记自检'
assert_first_before "$offline_worker_wrapper" \
  'flock -x 9' 'if ! mv -f -- "$running_marker" "$marker_file"; then' \
  '离线 worker 状态切换 sidecar 租约'
assert_first_before "$offline_worker_wrapper" \
  'if ! mv -f -- "$running_marker" "$marker_file"; then' \
  'flock -u 9 || fail '\''无法释放离线 worker 协调锁'\''' \
  '离线 worker RUNNING 切换后释放 sidecar 租约'
assert_first_before "$offline_worker_wrapper" \
  'flock -u 9 || fail '\''无法释放离线 worker 协调锁'\''' \
  'runuser -u "$worker_user"' \
  '离线 worker 启动前 sidecar 租约释放'
assert_first_before "$offline_worker_recovery" \
  'acquire_offline_worker_coordination_lock' 'load_offline_worker_marker' \
  '离线 worker 故障恢复 sidecar 锁定后读标记'
grep -Fq '[ "$OFFLINE_WORKER_COORDINATION_LOCK_HELD" = 1 ]' \
  "$SCRIPT_DIR/lib/common.sh" \
  || fail '离线 worker 标记删除未强制要求 sidecar 租约'

release_lock_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-release-lock-self-test.XXXXXX")"
trap 'rm -rf -- "$release_lock_test_dir"' EXIT
set +e
(
  set -e
  export PRODUCTION_RELEASE_SELF_TEST=1
  export RELEASE_OPERATION_LOCK_FILE="$release_lock_test_dir/release.lock"
  export OFFLINE_WORKER_MARKER_FILE="$release_lock_test_dir/offline-worker.state"
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"

  (
    set -e
    acquire_release_operation_lock
    : > "$release_lock_test_dir/holder-ready"
    for ((holder_attempt = 1; holder_attempt <= 500; holder_attempt++)); do
      [ ! -e "$release_lock_test_dir/holder-release" ] || exit 0
      sleep 0.01
    done
    exit 1
  ) &
  holder_pid=$!
  for ((holder_ready_attempt = 1; holder_ready_attempt <= 200; holder_ready_attempt++)); do
    [ ! -e "$release_lock_test_dir/holder-ready" ] || break
    sleep 0.01
  done
  [ -e "$release_lock_test_dir/holder-ready" ] || exit 1

  (
    set -e
    acquire_release_operation_lock
    : > "$release_lock_test_dir/waiter-acquired"
  ) &
  waiter_pid=$!
  sleep 0.05
  early_acquire=0
  if [ -e "$release_lock_test_dir/waiter-acquired" ] \
      || ! kill -0 "$waiter_pid" 2>/dev/null; then
    early_acquire=1
  fi
  : > "$release_lock_test_dir/holder-release"
  wait "$holder_pid" || exit 1
  wait "$waiter_pid" || exit 1
  [ "$early_acquire" = 0 ] || exit 1
  [ -e "$release_lock_test_dir/waiter-acquired" ] || exit 1
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] \
  || fail '生产发布全程互斥锁未真实阻塞第二个写进程直到首个进程退出'
rm -rf -- "$release_lock_test_dir"
trap - EXIT

marker_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-offline-marker-self-test.XXXXXX")"
trap 'rm -rf -- "$marker_test_dir"' EXIT
set +e
PRODUCTION_RELEASE_SELF_TEST=1 \
RELEASE_OPERATION_LOCK_FILE="$marker_test_dir/forbidden-release.lock" \
OFFLINE_WORKER_MARKER_FILE="$marker_test_dir/forbidden-worker.state" \
bash -c 'source "$1"; validate_release_operation_lock_path' \
  _ "$SCRIPT_DIR/lib/common.sh" >/dev/null 2>&1
checked_rc=$?
set -e
[ "$checked_rc" -ne 0 ] \
  || fail '正式入口可因遗留 PRODUCTION_RELEASE_SELF_TEST 环境覆盖固定生产锁路径'

set +e
(
  set -e
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"
  apt-config() { return 42; }
  if assert_release_window_reboot_safe; then
    exit 1
  fi
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] \
  || fail 'APT 配置读取失败时重启门禁没有 fail-closed'

strict_inactive_good=$'LoadState=loaded\nActiveState=inactive\nSubState=dead\nMainPID=0\nControlPID=0\nJob='
set +e
(
  set -e
  export STRICT_INACTIVE_PROPERTIES="$strict_inactive_good"
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"
  systemctl() { printf '%s\n' "$STRICT_INACTIVE_PROPERTIES"; }
  assert_service_inactive
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] \
  || fail '主服务已 loaded + inactive 且无 PID/job 时未能通过严格停稳门禁'

strict_inactive_bad_cases=(
  $'LoadState=loaded\nActiveState=activating\nSubState=start\nMainPID=0\nControlPID=0\nJob=/org/freedesktop/systemd1/job/1'
  $'LoadState=loaded\nActiveState=deactivating\nSubState=stop\nMainPID=123\nControlPID=0\nJob='
  $'LoadState=loaded\nActiveState=inactive\nSubState=dead\nMainPID=123\nControlPID=0\nJob='
  $'LoadState=loaded\nActiveState=failed\nSubState=failed\nMainPID=0\nControlPID=456\nJob='
  $'LoadState=loaded\nActiveState=inactive\nSubState=dead\nMainPID=0\nControlPID=0\nJob=/org/freedesktop/systemd1/job/2'
  $'LoadState=not-found\nActiveState=inactive\nSubState=dead\nMainPID=0\nControlPID=0\nJob='
)
for strict_inactive_bad in "${strict_inactive_bad_cases[@]}"; do
  set +e
  (
    export STRICT_INACTIVE_PROPERTIES="$strict_inactive_bad"
    # shellcheck source=lib/common.sh
    source "$SCRIPT_DIR/lib/common.sh"
    systemctl() { printf '%s\n' "$STRICT_INACTIVE_PROPERTIES"; }
    assert_service_inactive
  ) >/dev/null 2>&1
  checked_rc=$?
  set -e
  [ "$checked_rc" -ne 0 ] \
    || fail '严格停稳门禁误放行 activating/deactivating、残留 PID/job 或缺失 unit'
done

set +e
(
  set -e
  export PRODUCTION_RELEASE_SELF_TEST=1
  export RELEASE_OPERATION_LOCK_FILE="$marker_test_dir/create-failure-release.lock"
  export OFFLINE_WORKER_MARKER_FILE="$marker_test_dir/create-failure.state"
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"
  RELEASE_OPERATION_LOCK_HELD=1
  chown() { return 1; }
  if create_offline_worker_marker 'dianxinyun-create-failure-test'; then
    exit 1
  fi
  [ ! -e "$OFFLINE_WORKER_MARKER_FILE" ]
  [ -z "$(find "$marker_test_dir" -maxdepth 1 -name 'create-failure.state.tmp.*' -print -quit)" ]
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] \
  || fail '离线 worker 标记 chmod/chown 前置失败可能留下空或不安全标记'

set +e
(
  set -e
  export PRODUCTION_RELEASE_SELF_TEST=1
  export RELEASE_OPERATION_LOCK_FILE="$marker_test_dir/reconcile-release.lock"
  export OFFLINE_WORKER_MARKER_FILE="$marker_test_dir/reconcile.state"
  export OFFLINE_WORKER_COORDINATION_LOCK_FILE="$marker_test_dir/reconcile.lock"
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"
  RELEASE_OPERATION_LOCK_HELD=1
  chown() { :; }
  stat_mode_uid_gid_links() { printf '600:0:0:1\n'; }
  marker_systemd_scenario='queued'
  systemctl() {
    [ "$1" = show ] || return 1
    case "$marker_systemd_scenario" in
      queued)
        printf 'LoadState=not-found\nActiveState=inactive\nSubState=dead\nMainPID=0\nJob=/org/freedesktop/systemd1/job/123\n'
        ;;
      loaded)
        printf 'LoadState=loaded\nActiveState=inactive\nSubState=dead\nMainPID=0\nJob=\n'
        ;;
      not-found)
        printf 'LoadState=not-found\nActiveState=inactive\nSubState=dead\nMainPID=0\nJob=\n'
        ;;
      *) return 1 ;;
    esac
  }

  create_offline_worker_marker 'dianxinyun-queued-test'
  if assert_no_offline_worker; then
    exit 1
  fi
  [ -f "$OFFLINE_WORKER_MARKER_FILE" ]
  acquire_offline_worker_coordination_lock
  remove_offline_worker_marker_if_matches 'dianxinyun-queued-test' SUBMITTING
  release_offline_worker_coordination_lock

  create_offline_worker_marker 'dianxinyun-loaded-test'
  marker_systemd_scenario='loaded'
  if assert_no_offline_worker; then
    exit 1
  fi
  [ -f "$OFFLINE_WORKER_MARKER_FILE" ]
  acquire_offline_worker_coordination_lock
  remove_offline_worker_marker_if_matches 'dianxinyun-loaded-test' SUBMITTING
  release_offline_worker_coordination_lock

  create_offline_worker_marker 'dianxinyun-not-submitted-test'
  marker_systemd_scenario='not-found'
  assert_no_offline_worker
  [ ! -e "$OFFLINE_WORKER_MARKER_FILE" ]
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] \
  || fail 'SUBMITTING worker 的排队/已加载 unit 被误清理，或确定未提交时无法自动闭合'

race_marker="$marker_test_dir/race.state"
race_lock="$marker_test_dir/race.lock"
race_holder_ready="$marker_test_dir/race-holder-ready"
race_holder_release="$marker_test_dir/race-holder-release"
race_reconcile_done="$marker_test_dir/race-reconcile-done"
printf 'STATE=SUBMITTING\nUNIT_NAME=dianxinyun-race-test\n' > "$race_marker"
chmod 0600 "$race_marker"
(
  set -e
  exec 9>"$race_lock"
  flock -x 9
  : > "$race_holder_ready"
  for ((race_wait = 1; race_wait <= 500; race_wait++)); do
    [ ! -e "$race_holder_release" ] || break
    sleep 0.01
  done
  [ -e "$race_holder_release" ] || exit 1
  race_running_marker="$(mktemp "${race_marker}.running.XXXXXX")"
  printf 'STATE=RUNNING\nUNIT_NAME=dianxinyun-race-test\n' > "$race_running_marker"
  chmod 0600 "$race_running_marker"
  mv -f -- "$race_running_marker" "$race_marker"
  flock -u 9
) &
race_holder_pid=$!
for ((race_ready_attempt = 1; race_ready_attempt <= 200; race_ready_attempt++)); do
  [ ! -e "$race_holder_ready" ] || break
  sleep 0.01
done
if [ ! -e "$race_holder_ready" ]; then
  : > "$race_holder_release"
  wait "$race_holder_pid" || true
  fail '离线 worker 竞态自测未能取得 sidecar 租约'
fi

(
  set -e
  export PRODUCTION_RELEASE_SELF_TEST=1
  export RELEASE_OPERATION_LOCK_FILE="$marker_test_dir/race-release.lock"
  export OFFLINE_WORKER_MARKER_FILE="$race_marker"
  export OFFLINE_WORKER_COORDINATION_LOCK_FILE="$race_lock"
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"
  RELEASE_OPERATION_LOCK_HELD=1
  stat_mode_uid_gid_links() { printf '600:0:0:1\n'; }
  systemctl() {
    printf 'LoadState=not-found\nActiveState=inactive\nSubState=dead\nMainPID=0\nJob=\n'
  }
  if assert_no_offline_worker; then
    exit 1
  fi
  load_offline_worker_marker
  [ "$OFFLINE_WORKER_MARKER_STATE" = RUNNING ]
  [ "$OFFLINE_WORKER_MARKER_UNIT" = dianxinyun-race-test ]
  : > "$race_reconcile_done"
) &
race_reconciler_pid=$!
sleep 0.05
if ! kill -0 "$race_reconciler_pid" 2>/dev/null \
    || [ -e "$race_reconcile_done" ]; then
  : > "$race_holder_release"
  wait "$race_holder_pid" || true
  wait "$race_reconciler_pid" || true
  fail 'SUBMITTING 复核未在 wrapper 状态切换持锁时阻塞'
fi
: > "$race_holder_release"
wait "$race_holder_pid" \
  || fail '离线 worker 竞态自测未能切换到 RUNNING'
wait "$race_reconciler_pid" \
  || fail 'SUBMITTING 复核在 wrapper 切换 RUNNING 后未 fail-closed'
[ -e "$race_reconcile_done" ] \
  || fail '离线 worker 竞态复核未完成'
grep -Fxq 'STATE=RUNNING' "$race_marker" \
  || fail 'SUBMITTING 复核误删了 wrapper 刚切换的 RUNNING 标记'
grep -Fxq 'UNIT_NAME=dianxinyun-race-test' "$race_marker" \
  || fail '离线 worker 竞态复核后 unit 标记发生变化'

wrapper_marker="$marker_test_dir/wrapper-preflight.state"
printf 'STATE=SUBMITTING\nUNIT_NAME=dianxinyun-wrapper-preflight-test\n' > "$wrapper_marker"
chmod 0600 "$wrapper_marker"
set +e
(
  export PRODUCTION_RELEASE_SELF_TEST=1
  export OFFLINE_WORKER_COORDINATION_LOCK_FILE="$marker_test_dir/wrapper-preflight.lock"
  stat() { printf '600:0:0:1\n'; }
  set -- "$wrapper_marker" dianxinyun-wrapper-preflight-test '' service-group /bin/true unused
  # shellcheck source=lib/offline-worker-wrapper.sh
  source "$offline_worker_wrapper"
)
wrapper_rc=$?
set -e
[ "$wrapper_rc" -ne 0 ] && [ ! -e "$wrapper_marker" ] \
  || fail '离线 worker 包装器在运行用户等前置校验失败时未清理已认证 SUBMITTING 标记'

wrapper_marker="$marker_test_dir/wrapper-env-override.state"
printf 'STATE=SUBMITTING\nUNIT_NAME=dianxinyun-wrapper-env-test\n' > "$wrapper_marker"
chmod 0600 "$wrapper_marker"
set +e
(
  export PRODUCTION_RELEASE_SELF_TEST=1
  export OFFLINE_WORKER_COORDINATION_LOCK_FILE="$marker_test_dir/wrapper-env.lock"
  export APP_SCHEDULING_ENABLED=true
  stat() { printf '600:0:0:1\n'; }
  chown() { :; }
  runuser() {
    while [ "$#" -gt 0 ] && [ "$1" != -- ]; do shift; done
    [ "${1:-}" = -- ] || return 1
    shift
    "$@"
  }
  set -- \
    "$wrapper_marker" dianxinyun-wrapper-env-test service-user service-group \
    /usr/bin/env APP_SCHEDULING_ENABLED=false /bin/sh -c \
    '[ "$APP_SCHEDULING_ENABLED" = false ]'
  # shellcheck source=lib/offline-worker-wrapper.sh
  source "$offline_worker_wrapper"
)
wrapper_rc=$?
set -e
[ "$wrapper_rc" -eq 0 ] && [ ! -e "$wrapper_marker" ] \
  || fail '离线 worker wrapper 未能在正式 EnvironmentFile 生效后最终覆盖在线调度配置'

cleanup_race_marker="$marker_test_dir/wrapper-cleanup-race.state"
cleanup_race_lock="$marker_test_dir/wrapper-cleanup-race.lock"
cleanup_worker_ready="$marker_test_dir/wrapper-cleanup-worker-ready"
cleanup_worker_release="$marker_test_dir/wrapper-cleanup-worker-release"
printf 'STATE=SUBMITTING\nUNIT_NAME=dianxinyun-wrapper-cleanup-old\n' \
  > "$cleanup_race_marker"
chmod 0600 "$cleanup_race_marker"
(
  export PRODUCTION_RELEASE_SELF_TEST=1
  export OFFLINE_WORKER_COORDINATION_LOCK_FILE="$cleanup_race_lock"
  stat() { printf '600:0:0:1\n'; }
  chown() { :; }
  runuser() {
    while [ "$#" -gt 0 ] && [ "$1" != -- ]; do shift; done
    [ "${1:-}" = -- ] || return 1
    shift
    "$@"
  }
  set -- \
    "$cleanup_race_marker" dianxinyun-wrapper-cleanup-old service-user service-group \
    /bin/sh -c \
    ': > "$1"; attempt=0; while [ ! -e "$2" ] && [ "$attempt" -lt 500 ]; do sleep 0.01; attempt=$((attempt + 1)); done; [ -e "$2" ]' \
    _ "$cleanup_worker_ready" "$cleanup_worker_release"
  # shellcheck source=lib/offline-worker-wrapper.sh
  source "$offline_worker_wrapper"
) &
cleanup_wrapper_pid=$!
for ((cleanup_ready_attempt = 1; cleanup_ready_attempt <= 200; cleanup_ready_attempt++)); do
  [ ! -e "$cleanup_worker_ready" ] || break
  sleep 0.01
done
if [ ! -e "$cleanup_worker_ready" ]; then
  : > "$cleanup_worker_release"
  wait "$cleanup_wrapper_pid" || true
  fail '离线 worker 退出清理竞态自测未能启动 worker'
fi

exec 8>"$cleanup_race_lock"
flock -x 8
: > "$cleanup_worker_release"
sleep 0.05
if ! kill -0 "$cleanup_wrapper_pid" 2>/dev/null; then
  flock -u 8
  exec 8>&-
  wait "$cleanup_wrapper_pid" || true
  fail '离线 worker 退出清理未在 sidecar 被占用时阻塞'
fi
grep -Fxq 'STATE=RUNNING' "$cleanup_race_marker" \
  || fail '离线 worker 退出清理竞态自测未进入 RUNNING'
cleanup_replacement="$(mktemp "${cleanup_race_marker}.new.XXXXXX")"
printf 'STATE=SUBMITTING\nUNIT_NAME=dianxinyun-wrapper-cleanup-new\n' \
  > "$cleanup_replacement"
chmod 0600 "$cleanup_replacement"
mv -f -- "$cleanup_replacement" "$cleanup_race_marker"
flock -u 8
exec 8>&-
set +e
wait "$cleanup_wrapper_pid"
wrapper_rc=$?
set -e
[ "$wrapper_rc" -ne 0 ] \
  || fail '旧离线 worker 清理遇到新标记时未 fail-closed'
grep -Fxq 'STATE=SUBMITTING' "$cleanup_race_marker" \
  || fail '旧离线 worker 退出清理误删了新的 SUBMITTING 标记'
grep -Fxq 'UNIT_NAME=dianxinyun-wrapper-cleanup-new' "$cleanup_race_marker" \
  || fail '旧离线 worker 退出清理改写了新 worker 标记'
rm -rf -- "$marker_test_dir"
trap - EXIT

for serialized_backup_stage in \
  15-configure-production-env.sh \
  20-run-migrations.sh \
  25-stage-backend.sh \
  30-visitor-reencrypt.sh \
  40-activate-backend.sh \
  50-install-web.sh \
  90-rollback.sh; do
  assert_first_before "$SCRIPT_DIR/$serialized_backup_stage" \
    'acquire_release_operation_lock' 'verify_backup_directory "$backup_dir"' \
    "$serialized_backup_stage 发布互斥锁"
done
assert_first_before "$SCRIPT_DIR/10-stop-and-backup.sh" \
  'acquire_release_operation_lock' '"$SCRIPT_DIR/00-preflight-readonly.sh"' \
  '停服备份发布互斥锁'
grep -Fq 'assert_release_window_reboot_safe' "$SCRIPT_DIR/10-stop-and-backup.sh" \
  || fail '停服备份未拒绝 APT 自动重启或已有 reboot-required 的主机'
reboot_gate_lines="$(grep -nF 'assert_release_window_reboot_safe' \
  "$SCRIPT_DIR/10-stop-and-backup.sh" | cut -d: -f1)"
[ "$(printf '%s\n' "$reboot_gate_lines" | wc -l | tr -d ' ')" = 2 ] \
  || fail '停服备份必须在发布锁前后各执行一次重启门禁'
first_reboot_gate_line="$(printf '%s\n' "$reboot_gate_lines" | sed -n '1p')"
second_reboot_gate_line="$(printf '%s\n' "$reboot_gate_lines" | sed -n '2p')"
release_acquire_line="$(grep -nF 'acquire_release_operation_lock' \
  "$SCRIPT_DIR/10-stop-and-backup.sh" | awk -F: 'NR == 1 {print $1}')"
maintenance_create_line="$(grep -nF 'create_maintenance_lock "$backup_dir"' \
  "$SCRIPT_DIR/10-stop-and-backup.sh" | awk -F: 'NR == 1 {print $1}')"
[ "$first_reboot_gate_line" -lt "$release_acquire_line" ] \
  && [ "$release_acquire_line" -lt "$second_reboot_gate_line" ] \
  && [ "$second_reboot_gate_line" -lt "$maintenance_create_line" ] \
  || fail '停服备份重启门禁必须在锁前预检，并在取得发布锁后、创建维护锁前复检'
assert_first_before "$SCRIPT_DIR/10-stop-and-backup.sh" \
  'create_maintenance_lock "$backup_dir"' 'systemctl disable "$SERVICE_NAME"' \
  '停服备份关闭开机自启'
assert_first_before "$SCRIPT_DIR/10-stop-and-backup.sh" \
  'systemctl disable "$SERVICE_NAME"' 'systemctl stop "$SERVICE_NAME"' \
  '停服备份关闭开机自启'
assert_first_before "$SCRIPT_DIR/90-rollback.sh" \
  'ensure_maintenance_lock "$backup_dir"' 'systemctl disable "$SERVICE_NAME"' \
  '同点回滚关闭开机自启'
for boot_disabled_stage in \
  15-configure-production-env.sh \
  20-run-migrations.sh \
  25-stage-backend.sh \
  30-visitor-reencrypt.sh; do
  grep -Fq 'assert_service_boot_disabled' "$SCRIPT_DIR/$boot_disabled_stage" \
    || fail "$boot_disabled_stage 未拒绝维护期主服务意外恢复开机自启"
done
grep -Fq 'assert_service_boot_disabled' "$SCRIPT_DIR/50-install-web.sh" \
  || fail 'transition Web 未拒绝维护期主服务意外恢复开机自启'
assert_first_before "$SCRIPT_DIR/40-activate-backend.sh" \
  'systemctl enable "$SERVICE_NAME"' 'release_maintenance_lock "$backup_dir"' \
  '新后端恢复开机自启后解锁'
assert_first_before "$SCRIPT_DIR/90-rollback.sh" \
  'systemctl enable "$SERVICE_NAME"' 'release_maintenance_lock "$backup_dir"' \
  '旧后端恢复开机自启后解锁'
for serialized_runtime_stage in \
  55-record-transition-compatibility.sh \
  60-record-mini-live.sh; do
  assert_first_before "$SCRIPT_DIR/$serialized_runtime_stage" \
    'acquire_release_operation_lock' 'systemctl is-active --quiet "$SERVICE_NAME"' \
    "$serialized_runtime_stage 发布互斥锁"
done
assert_first_before "$SCRIPT_DIR/10-stop-and-backup.sh" \
  'acquire_watchdog_coordination_lock' 'create_maintenance_lock "$backup_dir"' \
  '停服备份 watchdog 协调锁'
assert_first_before "$SCRIPT_DIR/10-stop-and-backup.sh" \
  'create_maintenance_lock "$backup_dir"' 'systemctl stop "$SERVICE_NAME"' \
  '停服备份 watchdog 竞态闭合'
assert_first_before "$SCRIPT_DIR/10-stop-and-backup.sh" \
  'systemctl stop "$SERVICE_NAME"' 'release_watchdog_coordination_lock' \
  '停服备份 watchdog 协调锁释放'
assert_first_before "$SCRIPT_DIR/90-rollback.sh" \
  'acquire_watchdog_coordination_lock' 'ensure_maintenance_lock "$backup_dir"' \
  '同点回滚 watchdog 协调锁'
assert_first_before "$SCRIPT_DIR/90-rollback.sh" \
  'ensure_maintenance_lock "$backup_dir"' 'systemctl stop "$SERVICE_NAME"' \
  '同点回滚 watchdog 竞态闭合'
assert_first_before "$SCRIPT_DIR/90-rollback.sh" \
  'systemctl stop "$SERVICE_NAME"' 'release_watchdog_coordination_lock' \
  '同点回滚 watchdog 协调锁释放'

health_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-health-self-test.XXXXXX")"
trap 'rm -rf -- "$health_test_dir"' EXIT
set +e
(
  set -e
  export LOCAL_HEALTH_URL='http://local.test/api/v1/auth/captcha'
  export HEALTH_URL='https://public.test/api/v1/auth/captcha'
  export BACKEND_LOCAL_HEALTH_ATTEMPTS=4
  export BACKEND_PUBLIC_HEALTH_ATTEMPTS=3
  export BACKEND_HEALTH_INTERVAL_SECONDS=0
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"

  local_health_calls=0
  public_health_calls=0
  systemctl() { return 0; }
  sleep() { :; }
  health_check_url() {
    if [ "$1" = "$LOCAL_HEALTH_URL" ]; then
      local_health_calls=$((local_health_calls + 1))
      [ "$local_health_calls" -ge 3 ]
    else
      public_health_calls=$((public_health_calls + 1))
      [ "$public_health_calls" -ge 2 ]
    fi
  }

  wait_for_backend_health
  [ "$local_health_calls" = 3 ]
  [ "$public_health_calls" = 2 ]
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] || fail '有界健康轮询未按本机先行、公网随后顺序重试并成功'

set +e
(
  set -e
  export LOCAL_HEALTH_URL='http://local-timeout.test/api/v1/auth/captcha'
  export HEALTH_URL='https://public-must-not-run.test/api/v1/auth/captcha'
  export BACKEND_LOCAL_HEALTH_ATTEMPTS=3
  export BACKEND_PUBLIC_HEALTH_ATTEMPTS=2
  export BACKEND_HEALTH_INTERVAL_SECONDS=0
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"
  local_health_calls=0
  public_health_calls=0
  systemctl() { return 0; }
  sleep() { :; }
  health_check_url() {
    if [ "$1" = "$LOCAL_HEALTH_URL" ]; then
      local_health_calls=$((local_health_calls + 1))
    else
      public_health_calls=$((public_health_calls + 1))
    fi
    return 1
  }
  if wait_for_backend_health; then
    exit 1
  fi
  [ "$local_health_calls" = 3 ]
  [ "$public_health_calls" = 0 ]
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] || fail '本机健康超时未在准确次数内停止，或错误地继续请求公网'

set +e
(
  export HEALTH_URL='https://public.test/api/v1/auth/captcha'
  export HEALTH_TRAP_MARKER="$health_test_dir/err-trap-fired"
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"
  health_check_url() { return 1; }
  trap 'printf "ERR_TRAP_FIRED\n" > "$HEALTH_TRAP_MARKER"; exit 77' ERR
  health_check_backend
)
health_trap_rc=$?
set -e
[ "$health_trap_rc" = 77 ] || fail '健康检查失败未返回非零并触发 ERR trap'
[ -f "$health_test_dir/err-trap-fired" ] || fail '健康检查失败仍可能被显式 exit 绕过 ERR trap'

set +e
(
  restore_test_complete=0
  trap 'rc=$?; trap - EXIT; if [ "$restore_test_complete" != 1 ]; then printf "EXIT_SAFETY_FIRED\n" > "$health_test_dir/exit-safety-fired"; fi; exit "$rc"' EXIT
  exit 23
)
exit_safety_rc=$?
set -e
[ "$exit_safety_rc" = 23 ] || fail 'EXIT 安全兜底未保留原始失败码'
[ -f "$health_test_dir/exit-safety-fired" ] || fail '显式 exit 未触发回滚安全兜底模型'

set +e
(
  set -e
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"
  committed_root="$health_test_dir/committed"
  mkdir -p "$committed_root/current" "$committed_root/displaced"
  printf 'samepoint\n' > "$committed_root/current/sentinel"
  printf 'failure\n' > "$committed_root/displaced/sentinel"
  restore_displaced_path_before_commit 1 "$committed_root/current" \
    "$committed_root/displaced" "$committed_root/partial" 'committed-test'
  grep -qx 'samepoint' "$committed_root/current/sentinel"
  grep -qx 'failure' "$committed_root/displaced/sentinel"
  [ ! -e "$committed_root/partial" ]

  uncommitted_root="$health_test_dir/uncommitted"
  mkdir -p "$uncommitted_root/current" "$uncommitted_root/displaced"
  printf 'partial\n' > "$uncommitted_root/current/sentinel"
  printf 'pre-move\n' > "$uncommitted_root/displaced/sentinel"
  restore_displaced_path_before_commit 0 "$uncommitted_root/current" \
    "$uncommitted_root/displaced" "$uncommitted_root/partial" 'uncommitted-test'
  grep -qx 'pre-move' "$uncommitted_root/current/sentinel"
  grep -qx 'partial' "$uncommitted_root/partial/sentinel"
  [ ! -e "$uncommitted_root/displaced" ]
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] || fail '配置恢复提交边界错误：健康失败可能撤销已完成的同点 Nginx/systemd 恢复'
rm -rf -- "$health_test_dir"
trap - EXIT

maintenance_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-maintenance-lock-self-test.XXXXXX")"
trap 'rm -rf -- "$maintenance_test_dir"' EXIT
set +e
(
  set -e
  export PRODUCTION_RELEASE_SELF_TEST=1
  export MAINTENANCE_LOCK_FILE="$maintenance_test_dir/maintenance.lock"
  export WATCHDOG_COORDINATION_LOCK_FILE="$maintenance_test_dir/watchdog.lock"
  # shellcheck source=lib/common.sh
  source "$SCRIPT_DIR/lib/common.sh"

  chown() { :; }
  stat_mode_uid_gid_links() { printf '600:0:0:1\n'; }

  backup_a="$maintenance_test_dir/backup-a"
  backup_b="$maintenance_test_dir/backup-b"

  (
    exec 9>"$WATCHDOG_COORDINATION_LOCK_FILE"
    flock -x 9
    : > "$maintenance_test_dir/watchdog-holder-ready"
    for ((hold_attempt = 1; hold_attempt <= 500; hold_attempt++)); do
      [ ! -e "$maintenance_test_dir/watchdog-holder-release" ] || exit 0
      sleep 0.01
    done
    exit 1
  ) &
  watchdog_holder_pid=$!
  for ((ready_attempt = 1; ready_attempt <= 200; ready_attempt++)); do
    [ ! -e "$maintenance_test_dir/watchdog-holder-ready" ] || break
    sleep 0.01
  done
  [ -e "$maintenance_test_dir/watchdog-holder-ready" ] || exit 1

  (
    set -e
    acquire_watchdog_coordination_lock
    create_maintenance_lock "$backup_a"
    : > "$maintenance_test_dir/maintenance-created"
    for ((creator_attempt = 1; creator_attempt <= 500; creator_attempt++)); do
      [ ! -e "$maintenance_test_dir/maintenance-creator-release" ] || {
        release_watchdog_coordination_lock
        exit 0
      }
      sleep 0.01
    done
    exit 1
  ) &
  maintenance_creator_pid=$!
  sleep 0.05
  [ ! -e "$MAINTENANCE_LOCK_FILE" ] || exit 1
  kill -0 "$maintenance_creator_pid" || exit 1

  : > "$maintenance_test_dir/watchdog-holder-release"
  wait "$watchdog_holder_pid" || exit 1
  for ((created_attempt = 1; created_attempt <= 200; created_attempt++)); do
    [ ! -e "$maintenance_test_dir/maintenance-created" ] || break
    sleep 0.01
  done
  [ -e "$maintenance_test_dir/maintenance-created" ] || exit 1
  assert_maintenance_lock "$backup_a" || exit 1

  # Model 10/90 holding the coordination lease from maintenance-lock creation
  # through service stop. A watchdog must not re-enter that interval.
  (
    exec 6>"$WATCHDOG_COORDINATION_LOCK_FILE"
    if flock -n 6; then
      exit 1
    fi
  ) || exit 1
  : > "$maintenance_test_dir/maintenance-creator-release"
  wait "$maintenance_creator_pid" || exit 1
  release_maintenance_lock "$backup_a" || exit 1

  create_maintenance_lock "$backup_a"
  assert_maintenance_lock "$backup_a"
  original_lock_sha="$(sha256sum "$MAINTENANCE_LOCK_FILE" | awk '{print $1}')"

  if create_maintenance_lock "$backup_a"; then
    exit 1
  fi
  [ "$(sha256sum "$MAINTENANCE_LOCK_FILE" | awk '{print $1}')" = "$original_lock_sha" ]

  if assert_maintenance_lock "$backup_b"; then
    exit 1
  fi
  if release_maintenance_lock "$backup_b"; then
    exit 1
  fi
  [ -f "$MAINTENANCE_LOCK_FILE" ]

  printf 'TAMPERED=1\n' >> "$MAINTENANCE_LOCK_FILE"
  if assert_maintenance_lock "$backup_a"; then
    exit 1
  fi
  if release_maintenance_lock "$backup_a"; then
    exit 1
  fi
  rm -f -- "$MAINTENANCE_LOCK_FILE"

  printf 'not-a-lock\n' > "$maintenance_test_dir/symlink-target"
  ln -s -- "$maintenance_test_dir/symlink-target" "$MAINTENANCE_LOCK_FILE"
  if assert_maintenance_lock "$backup_a"; then
    exit 1
  fi
  if create_maintenance_lock "$backup_a"; then
    exit 1
  fi
  rm -f -- "$MAINTENANCE_LOCK_FILE"

  create_maintenance_lock "$backup_a"
  assert_maintenance_lock "$backup_a"
  release_maintenance_lock "$backup_a"
  ! maintenance_lock_present

  mkdir "$maintenance_test_dir/real-backup"
  ln -s -- "$maintenance_test_dir/real-backup" "$maintenance_test_dir/backup-link"
  if create_maintenance_lock "$maintenance_test_dir/backup-link"; then
    exit 1
  fi
  ! maintenance_lock_present
)
checked_rc=$?
set -e
[ "$checked_rc" -eq 0 ] \
  || fail '生产维护锁 create/assert/release、绑定、防篡改、软链、协调租约或重复创建隔离测试失败'
rm -rf -- "$maintenance_test_dir"
trap - EXIT

grep -Fq 'mysql_run_file "$sql_file" 2>&1 | tee "$step_log"' "$SCRIPT_DIR/20-run-migrations.sh" \
  || fail '迁移脚本缺少逐项 fail-fast 管道'
grep -Fq -- '--show-warnings' "$SCRIPT_DIR/lib/common.sh" || fail '迁移未启用 mysql --show-warnings'
grep -Eq "export APP_SCHEDULING_ENABLED=('false'|false)" "$SCRIPT_DIR/30-visitor-reencrypt.sh" || fail '离线工具未显式禁用调度'
grep -Fq 'EnvironmentFile=$ENV_FILE' "$SCRIPT_DIR/30-visitor-reencrypt.sh" || fail '离线工具未使用 systemd EnvironmentFile'
grep -Fq '$${#DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY}' "$SCRIPT_DIR/15-configure-production-env.sh" \
  || fail '正式环境门禁未转义 Bash 长度展开，systemd 249 会提前展开'
[ "$(grep -Fc '$$DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY' "$SCRIPT_DIR/15-configure-production-env.sh")" = 3 ] \
  || fail '正式环境门禁中的图纸密钥比较未完整转义 systemd 美元符号展开'
for env_name in APP_SCHEDULING_ENABLED JWT_SECRET VISITOR_DATA_ENCRYPTION_KEY SEAL_SCENE_ENCRYPTION_KEY; do
  escaped_ref='$${'"$env_name"':-}'
  grep -Fq "$escaped_ref" "$SCRIPT_DIR/15-configure-production-env.sh" \
    || fail "正式环境门禁未转义 $env_name 的 systemd 美元符号展开"
done
grep -Fq 'DROP/CREATE' "$SCRIPT_DIR/90-rollback.sh" || fail '回滚脚本未声明完整数据库恢复'
grep -Fq 'bash scripts/production-release/99-self-test.sh' "$SCRIPT_DIR/README.md" \
  || fail '运维说明缺少源码仓库自检命令'
grep -Fq 'bash ops/99-self-test.sh' "$SCRIPT_DIR/README.md" \
  || fail '运维说明缺少生产包自检命令'
if [ "$run_context" = 'source' ]; then
  bundle_script="$REPO_ROOT/scripts/create-production-release-bundle.sh"
  if grep -Eq "^[[:space:]]*printf[[:space:]]+'-" "$bundle_script"; then
    fail '生产组包脚本存在未使用 printf -- 的连字符开头格式串'
  fi

  grep -Fq 'install -m 0644 "$source_archive" "$verified_source_archive"' "$bundle_script" \
    || fail '生产组包未先冻结已校验源码归档'
  grep -Fq '[ "$(shasum -a 256 "$verified_source_archive" | awk '\''{print $1}'\'')" = "$source_input_sha256" ]' \
    "$bundle_script" || fail '生产组包未复核冻结源码归档与输入摘要一致'
  grep -Fq 'tar -xzf "$verified_source_archive" -C "$source_snapshot_dir"' "$bundle_script" \
    || fail '生产组包未从冻结源码归档解包快照'
  grep -Fq 'immutable_source_root="$source_snapshot_dir/source"' "$bundle_script" \
    || fail '生产组包未把 source/ 作为不可变组包来源'
  grep -Fq 'shasum -a 256 -c "$source_files_manifest"' "$bundle_script" \
    || fail '生产组包未验证源码快照逐文件清单'
  grep -Fq 'immutable_migration_root="$immutable_source_root/backend/src/main/resources/sql/migrations"' \
    "$bundle_script" || fail '生产组包迁移来源不是源码快照'
  grep -Fq 'immutable_ops_root="$immutable_source_root/scripts/production-release"' "$bundle_script" \
    || fail '生产组包运维脚本来源不是源码快照'
  grep -Fq 'immutable_docs_root="$immutable_source_root/docs"' "$bundle_script" \
    || fail '生产组包文档来源不是源码快照'
  grep -Fq 'immutable_archive_verifier="$immutable_source_root/scripts/verify-release-archive.py"' \
    "$bundle_script" || fail '生产组包校验器来源不是源码快照'

  grep -Fq 'migration_plan="$immutable_ops_root/baseline/migration-plan.tsv"' "$bundle_script" \
    || fail '生产组包迁移计划未从源码快照读取'
  grep -Fq '[ "${#migrations[@]}" -eq 11 ]' "$bundle_script" \
    || fail '生产组包未锁定源码快照中的 11 项迁移'
  grep -Fq 'install -m 0644 "$immutable_migration_root/$migration"' "$bundle_script" \
    || fail '生产组包未从源码快照复制迁移 SQL'
  grep -Fq '生产包迁移未保持源码快照内容' "$bundle_script" \
    || fail '生产组包未核对 11 项迁移与源码快照内容一致'
  grep -Fq 'ops_source="$immutable_ops_root"' "$bundle_script" \
    || fail '生产组包 ops 来源未绑定源码快照'
  grep -Fq 'cp -R "$ops_source/." "$release_dir/ops/"' "$bundle_script" \
    || fail '生产组包未复制源码快照内的完整 ops'
  grep -Fq 'cp -R "$ops_source/baseline/." "$release_dir/database/baseline/"' "$bundle_script" \
    || fail '生产组包未从源码快照复制数据库基线'
  grep -Fq '生产包数据库基线未保持源码快照内容' "$bundle_script" \
    || fail '生产组包未核对数据库基线与源码快照内容一致'
  grep -Fq 'install -m 0644 "$immutable_docs_root/$document" "$release_dir/docs/$document"' \
    "$bundle_script" || fail '生产组包未从源码快照复制发布文档'
  grep -Fq '生产包文档未保持源码快照内容' "$bundle_script" \
    || fail '生产组包未核对发布文档与源码快照内容一致'
  grep -Fq 'install -m 0644 "$immutable_archive_verifier" "$release_dir/ops/verify-release-archive.py"' \
    "$bundle_script" || fail '生产包未以 0644 封装源码快照内的校验器'
  grep -Fq '生产包归档校验器未保持源码快照内容' "$bundle_script" \
    || fail '生产组包未核对包内校验器与源码快照内容一致'
  grep -Fq 'install -m 0644 "$verified_source_archive" "$source_reference_archive"' "$bundle_script" \
    || fail '生产包源码引用未使用冻结且复核后的源码归档'
  grep -Fq 'python3 "$immutable_archive_verifier" outer "$outer_archive"' "$bundle_script" \
    || fail '最终 outer 归档未使用源码快照内校验器'

  for forbidden_workspace_source in \
    'ops_source="$root_dir/scripts/production-release"' \
    'migration_plan="$root_dir/scripts/production-release/baseline/migration-plan.tsv"' \
    'migration_root="$root_dir/backend/src/main/resources/sql/migrations"' \
    'docs_root="$root_dir/docs"' \
    'install -m 0644 "$root_dir/scripts/verify-release-archive.py"'; do
    if grep -Fq "$forbidden_workspace_source" "$bundle_script"; then
      fail "生产组包仍会混入源码快照生成后的工作区漂移：$forbidden_workspace_source"
    fi
  done
  grep -Fq '[ "$(tree_content_sha256 "$ops_source")" = "$(tree_content_sha256 "$release_dir/ops")" ]' \
    "$bundle_script" || fail '生产组包未逐树核对快照 ops 与包内 ops'
  grep -Fq '[ "$(shasum -a 256 "$immutable_archive_verifier" | awk '\''{print $1}'\'')" = ' \
    "$bundle_script" || fail '生产组包未核对快照校验器与包内校验器内容一致'

  if grep -Fq -- "-o -name '*.py'" "$bundle_script"; then
    fail '生产组包脚本不得把 Python 校验器提升为可执行权限'
  fi
  grep -Fq 'backups/*|需求方预览.zip)' "$REPO_ROOT/scripts/create-release-source-snapshot.sh" \
    || fail '源码快照未排除含 AppleDouble 的历史预览 ZIP'
  grep -Fq 'excluding-backups-and-legacy-preview-zip' "$REPO_ROOT/scripts/create-release-source-snapshot.sh" \
    || fail '源码快照清单未声明历史预览 ZIP 排除范围'
  bash -n "$REPO_ROOT/scripts/build-backend-release.sh" \
    || fail '后端正式构建脚本 Bash 语法失败'
  grep -Fq '<goal>build-info</goal>' "$REPO_ROOT/backend/pom.xml" \
    || fail '后端 Maven 构建未生成 build-info'
  grep -Fq '<sourceManifestSha256>${source.manifest.sha256}</sourceManifestSha256>' \
    "$REPO_ROOT/backend/pom.xml" || fail '后端 build-info 未绑定源码清单属性'
  grep -Fq 'JAR 未关联本次源码清单' "$REPO_ROOT/scripts/create-production-release-bundle.sh" \
    || fail '生产组包脚本未强制校验 JAR 源码清单标识'
else
  [ -f "$release_root/SHA256SUMS" ] || fail '生产包缺少 SHA256SUMS'
  [ -f "$SCRIPT_DIR/verify-release-archive.py" ] || fail '生产包缺少归档校验器'
  [ "$(find "$migration_dir" -maxdepth 1 -type f -name '*.sql' | wc -l | tr -d ' ')" = 11 ] \
    || fail '生产包迁移目录不是精确 11 项'
  packaged_order="$(cat "$release_root/database/MIGRATION_ORDER.txt")"
  expected_filenames="$(awk -F'|' '!/^#/ && NF {print $2}' "$plan")"
  [ "$packaged_order" = "$expected_filenames" ] || fail '生产包迁移顺序与计划不一致'
  [ -f "$release_root/artifacts/site-platform-1.0.0.jar" ] || fail '生产包缺少后端 JAR'
  [ -f "$release_root/source-reference/SOURCE_MANIFEST.txt" ] || fail '生产包缺少源码清单'
  source_manifest_sha256="$(sha256sum "$release_root/source-reference/SOURCE_MANIFEST.txt" | awk '{print $1}')"
  jar_build_info="$(unzip -p "$release_root/artifacts/site-platform-1.0.0.jar" \
    META-INF/build-info.properties)" || fail '生产 JAR 缺少 build-info'
  jar_provenance_count="$(printf '%s\n' "$jar_build_info" \
    | grep -Ec '^build\.sourceManifestSha256=' || true)"
  [ "$jar_provenance_count" = 1 ] || fail '生产 JAR 源码清单标识不是唯一值'
  jar_source_manifest="$(printf '%s\n' "$jar_build_info" \
    | awk -F= '$1 == "build.sourceManifestSha256" {print $2}')"
  [ "$jar_source_manifest" = "$source_manifest_sha256" ] \
    || fail '生产 JAR 源码清单标识与包内源码清单不一致'
fi

printf '[production-release-self-test] PASS：context=%s；68 表、13 标记、11 项迁移、7 项生产保护文件、维护/互斥/离线 worker 生命周期和递归 Bash 语法均通过\n' \
  "$run_context"
