#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
BASELINE_DIR="$SCRIPT_DIR/baseline"

fail() { printf '[production-release-self-test][ERROR] %s\n' "$*" >&2; exit 1; }

for command_name in bash awk sort diff wc sha256sum grep find unzip; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name"
done

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

for script in "$SCRIPT_DIR"/*.sh "$SCRIPT_DIR/lib/common.sh"; do
  bash -n "$script" || fail "Bash 语法失败：$script"
done

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

health_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-health-self-test.XXXXXX")"
trap 'rm -rf -- "$health_test_dir"' EXIT
(
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
) || fail '有界健康轮询未按本机先行、公网随后顺序重试并成功'

(
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
) || fail '本机健康超时未在准确次数内停止，或错误地继续请求公网'

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

(
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
) || fail '配置恢复提交边界错误：健康失败可能撤销已完成的同点 Nginx/systemd 恢复'
rm -rf -- "$health_test_dir"
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

printf '[production-release-self-test] PASS：context=%s；68 表、13 标记、11 项顺序/SHA/标记和全部 Bash 语法均通过\n' "$run_context"
