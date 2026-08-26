#!/usr/bin/env bash

set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]:-$0}"
ROOT_DIR="$(cd "$(dirname "$SCRIPT_PATH")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
MIGRATION_PATH="$BACKEND_DIR/src/main/resources/sql/migrations/20260826_quality_weekly_inspection.sql"
PHOTO_PATH="$ROOT_DIR/wechat-miniprogram/site-platform-miniprogram/src/static/brand/zhihui-yingzao-horizontal.png"

SOURCE_DB="${ACCEPTANCE_SOURCE_DB:-dianxinyun}"
MYSQL_HOST="${ACCEPTANCE_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${ACCEPTANCE_MYSQL_PORT:-3306}"
MYSQL_USER="${ACCEPTANCE_MYSQL_USER:-root}"
API_PORT="${ACCEPTANCE_API_PORT:-18082}"
REDIS_PORT="${ACCEPTANCE_REDIS_PORT:-16382}"
TARGET_DB="dianxinyun_quality_weekly_acceptance_$(date +%Y%m%d_%H%M%S)_$$"
API_BASE="http://127.0.0.1:${API_PORT}/api/v1"
TEMP_DIR="$(mktemp -d /private/tmp/dianxinyun-quality-weekly-acceptance.XXXXXX)"
UPLOAD_DIR="$TEMP_DIR/uploads"
BACKEND_LOG="$TEMP_DIR/backend.log"
REDIS_LOG="$TEMP_DIR/redis.log"
MIGRATION_FIRST_LOG="$TEMP_DIR/migration-first.log"
MIGRATION_SECOND_LOG="$TEMP_DIR/migration-second.log"
BACKEND_PID=''
REDIS_PID=''

MYSQL_ARGS=(-h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER")

info() {
  printf '[OK] %s\n' "$*"
}

warn() {
  printf '[WARN] %s\n' "$*" >&2
}

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"
}

resolve_redis_server() {
  if command -v redis-server >/dev/null 2>&1; then
    command -v redis-server
    return 0
  fi
  local bundled='/Users/js/.local/opt/redis-stable/bin/redis-server'
  [ -x "$bundled" ] && { printf '%s\n' "$bundled"; return 0; }
  return 1
}

stop_pid() {
  local pid="$1"
  [ -z "$pid" ] && return 0
  kill -0 "$pid" >/dev/null 2>&1 || return 0
  kill "$pid" >/dev/null 2>&1 || true
  for _ in $(seq 1 20); do
    kill -0 "$pid" >/dev/null 2>&1 || return 0
    sleep 0.25
  done
  kill -9 "$pid" >/dev/null 2>&1 || true
}

cleanup() {
  local original_status=$?
  local cleanup_failed=0
  local database_exists_after_cleanup=''
  trap - EXIT
  set +e
  stop_pid "$BACKEND_PID"
  stop_pid "$REDIS_PID"
  if [ -n "$BACKEND_PID" ] && [ -n "$(lsof -tiTCP:"$API_PORT" -sTCP:LISTEN 2>/dev/null || true)" ]; then
    printf '[ERROR] 临时后端进程停止后端口仍被占用：%s\n' "$API_PORT" >&2
    cleanup_failed=1
  fi
  if [ -n "$REDIS_PID" ] && [ -n "$(lsof -tiTCP:"$REDIS_PORT" -sTCP:LISTEN 2>/dev/null || true)" ]; then
    printf '[ERROR] 临时 Redis 进程停止后端口仍被占用：%s\n' "$REDIS_PORT" >&2
    cleanup_failed=1
  fi
  if [[ "$TARGET_DB" =~ ^dianxinyun_quality_weekly_acceptance_[0-9_]+$ ]]; then
    mysql "${MYSQL_ARGS[@]}" -e "DROP DATABASE IF EXISTS \`$TARGET_DB\`;" >/dev/null 2>&1
    database_exists_after_cleanup="$(mysql "${MYSQL_ARGS[@]}" -N -e \
      "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$TARGET_DB';" 2>/dev/null)"
    if [ "$database_exists_after_cleanup" = '0' ]; then
      printf '[OK] 已复核删除临时数据库：%s\n' "$TARGET_DB"
    else
      printf '[ERROR] 临时数据库自动清理或复核失败：%s\n' "$TARGET_DB" >&2
      cleanup_failed=1
    fi
  else
    printf '[ERROR] 拒绝清理不符合隔离命名规则的数据库：%s\n' "$TARGET_DB" >&2
    cleanup_failed=1
  fi
  case "$TEMP_DIR" in
    /private/tmp/dianxinyun-quality-weekly-acceptance.*) rm -rf -- "$TEMP_DIR" ;;
  esac
  if [ -e "$TEMP_DIR" ]; then
    printf '[ERROR] 临时验收目录未能清理：%s\n' "$TEMP_DIR" >&2
    cleanup_failed=1
  fi
  if [ "$cleanup_failed" -ne 0 ] && [ "$original_status" -eq 0 ]; then
    original_status=1
  fi
  exit "$original_status"
}

show_failure() {
  printf '[ERROR] 隔离质量周检验收失败，后端日志末尾如下：\n' >&2
  tail -n 100 "$BACKEND_LOG" 2>/dev/null >&2 || true
}

mysql_value() {
  mysql "${MYSQL_ARGS[@]}" -N -B "$TARGET_DB" -e "$1"
}

request_to_files() {
  local prefix="$1"
  local method="$2"
  local path="$3"
  local token="$4"
  local body="$5"
  local response_file="$TEMP_DIR/$prefix.body"
  local status_file="$TEMP_DIR/$prefix.status"
  local curl_args=(-sS --max-time 45 -o "$response_file" -w '%{http_code}' -X "$method")
  if [ -n "$token" ]; then
    curl_args+=(-H "Authorization: Bearer $token")
  fi
  if [ "$body" != '__NO_BODY__' ]; then
    curl_args+=(-H 'Content-Type: application/json' --data-binary "$body")
  fi
  curl "${curl_args[@]}" "$API_BASE$path" >"$status_file"
}

assert_http() {
  local prefix="$1"
  local expected="$2"
  local message="$3"
  local actual
  actual="$(tr -d '\r\n' <"$TEMP_DIR/$prefix.status")"
  if [ "$actual" != "$expected" ]; then
    printf '[ERROR] %s；期望 HTTP %s，实际 HTTP %s，响应：\n' "$message" "$expected" "$actual" >&2
    sed -n '1,80p' "$TEMP_DIR/$prefix.body" >&2 || true
    exit 1
  fi
}

assert_jq() {
  local prefix="$1"
  local expression="$2"
  local message="$3"
  if ! jq -e "$expression" "$TEMP_DIR/$prefix.body" >/dev/null; then
    printf '[ERROR] %s，响应：\n' "$message" >&2
    sed -n '1,100p' "$TEMP_DIR/$prefix.body" >&2 || true
    exit 1
  fi
}

assert_mysql_equals() {
  local expected="$1"
  local query="$2"
  local message="$3"
  local actual
  actual="$(mysql_value "$query")"
  [ "$actual" = "$expected" ] || fail "$message；期望 $expected，实际 $actual"
}

login() {
  local username="$1"
  local password="$2"
  local response
  response="$(jq -n --arg username "$username" --arg password "$password" \
    '{username:$username,password:$password}' \
    | curl -fsS --max-time 20 -H 'Content-Type: application/json' --data-binary @- "$API_BASE/auth/login")"
  jq -e '.code == 200 and (.data.token | type == "string" and length > 20)' >/dev/null <<<"$response"
  jq -r '.data.token' <<<"$response"
}

upload_photo() {
  local token="$1"
  local project_id="$2"
  local business_type="$3"
  local label="$4"
  local response_file="$TEMP_DIR/upload-${label}.body"
  local status_file="$TEMP_DIR/upload-${label}.status"
  curl -sS --max-time 45 -o "$response_file" -w '%{http_code}' \
    -H "Authorization: Bearer $token" \
    -F "file=@$PHOTO_PATH;type=image/png" \
    -F "projectId=$project_id" \
    -F "businessType=$business_type" \
    -F 'fileType=质量周检验收照片' \
    -F "fileName=${label}.png" \
    "$API_BASE/files" >"$status_file"
  if [ "$(tr -d '\r\n' <"$status_file")" != '200' \
      ] || ! jq -e '.code == 200 and (.data.id | type == "number")' "$response_file" >/dev/null; then
    printf '[ERROR] 周检照片上传失败：%s\n' "$label" >&2
    sed -n '1,80p' "$response_file" >&2 || true
    return 1
  fi
  jq -r '.data.id' "$response_file"
}

create_draft() {
  local prefix="$1"
  local token="$2"
  local project_id="$3"
  local week_start="$4"
  local payload
  payload="$(jq -n --argjson projectId "$project_id" --arg weekStart "$week_start" \
    '{projectId:$projectId,weekStart:$weekStart}')"
  request_to_files "$prefix" POST '/quality/weekly-inspections/drafts' "$token" "$payload"
  assert_http "$prefix" 200 "创建周检草稿失败：$prefix"
  assert_jq "$prefix" '.code == 200 and .data.status == "DRAFT" and .data.version == 0' \
    "周检草稿响应不完整：$prefix"
  jq -r '.data.id' "$TEMP_DIR/$prefix.body"
}

trap cleanup EXIT
trap show_failure ERR

for command in mysql mysqldump curl jq lsof mvn openssl sed; do
  require_command "$command"
done
[[ "$SOURCE_DB" =~ ^[A-Za-z0-9_]+$ ]] || fail '源数据库名只能包含字母、数字和下划线'
[[ "$API_PORT" =~ ^[0-9]+$ && "$REDIS_PORT" =~ ^[0-9]+$ ]] || fail '端口必须是数字'
[ -f "$MIGRATION_PATH" ] || fail '缺少质量周检迁移脚本'
[ -f "$PHOTO_PATH" ] || fail '缺少隔离验收用 PNG 文件'
[ -z "$(lsof -tiTCP:"$API_PORT" -sTCP:LISTEN 2>/dev/null || true)" ] || fail "$API_PORT 端口已被占用"
[ -z "$(lsof -tiTCP:"$REDIS_PORT" -sTCP:LISTEN 2>/dev/null || true)" ] || fail "$REDIS_PORT 端口已被占用"
REDIS_SERVER="$(resolve_redis_server)" || fail '缺少命令：redis-server'

source_exists="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$SOURCE_DB';")"
[ "$source_exists" -eq 1 ] || fail "源数据库不存在：$SOURCE_DB"
source_tables_before="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SOURCE_DB';")"
source_weekly_tables_before="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='$SOURCE_DB'
      AND table_name IN ('quality_weekly_inspection','quality_weekly_inspection_draft_item');")"

mysql "${MYSQL_ARGS[@]}" -e \
  "CREATE DATABASE \`$TARGET_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysqldump "${MYSQL_ARGS[@]}" --single-transaction --routines --triggers --set-gtid-purged=OFF "$SOURCE_DB" \
  | mysql "${MYSQL_ARGS[@]}" "$TARGET_DB"
target_tables_before_migration="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TARGET_DB';")"
[ "$source_tables_before" -eq "$target_tables_before_migration" ] \
  || fail '临时数据库表数量与源库不一致'
info "临时数据库副本已建立（${target_tables_before_migration} 张表），源库保持只读"

mysql "${MYSQL_ARGS[@]}" "$TARGET_DB" <"$MIGRATION_PATH" >"$MIGRATION_FIRST_LOG"
first_table_count="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TARGET_DB';")"
mysql "${MYSQL_ARGS[@]}" "$TARGET_DB" <"$MIGRATION_PATH" >"$MIGRATION_SECOND_LOG"
second_table_count="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TARGET_DB';")"
[ "$first_table_count" -eq "$second_table_count" ] || fail '质量周检迁移第二次执行改变了表数量'
assert_mysql_equals 1 \
  "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='20260826_QUALITY_WEEKLY_INSPECTION_V1';" \
  '质量周检迁移标记不唯一'
assert_mysql_equals 2 \
  "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='$TARGET_DB'
      AND table_name IN ('quality_weekly_inspection','quality_weekly_inspection_draft_item');" \
  '质量周检表未完整创建'
assert_mysql_equals 1 \
  "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
    WHERE table_schema='$TARGET_DB' AND table_name='quality_weekly_inspection'
      AND index_name='uk_quality_weekly_project_week';" \
  '同项目同周唯一索引缺失或重复'
info '20260826 迁移已在临时库连续执行两次，幂等结构与迁移标记通过'

project_id="$(mysql_value "
  SELECT p.id
    FROM project_info p
    LEFT JOIN sys_user_project up
      ON up.project_id=p.id AND up.status='ACTIVE'
   WHERE p.deleted=0
   GROUP BY p.id
   ORDER BY COUNT(DISTINCT up.user_id) DESC,p.id
   LIMIT 1;")"
[ -n "$project_id" ] || fail '临时副本中没有可用于质量周检验收的项目'

read -r admin_id admin_user < <(mysql "${MYSQL_ARGS[@]}" -N "$TARGET_DB" -e \
  "SELECT u.id,u.username FROM sys_user u
     JOIN sys_user_role ur ON ur.user_id=u.id
     JOIN sys_role r ON r.id=ur.role_id
    WHERE u.deleted=0 AND u.status=1 AND r.deleted=0 AND r.enabled=1
      AND r.scope_type='PLATFORM' AND r.role_code='PLATFORM_ADMIN'
    ORDER BY u.id LIMIT 1;")
[ -n "${admin_id:-}" ] && [ -n "${admin_user:-}" ] || fail '缺少可恢复的平台管理员'

admin_password="Qw$(openssl rand -hex 14)9!"
jwt_secret="$(openssl rand -hex 32)"

"$REDIS_SERVER" --bind 127.0.0.1 --port "$REDIS_PORT" --save '' --appendonly no --daemonize no \
  >"$REDIS_LOG" 2>&1 &
REDIS_PID=$!
for _ in $(seq 1 40); do
  [ -n "$(lsof -tiTCP:"$REDIS_PORT" -sTCP:LISTEN 2>/dev/null || true)" ] && break
  sleep 0.25
done
[ -n "$(lsof -tiTCP:"$REDIS_PORT" -sTCP:LISTEN 2>/dev/null || true)" ] || fail '临时 Redis 启动失败'

(
  cd "$BACKEND_DIR"
  exec env \
    SERVER_PORT="$API_PORT" SPRING_PROFILES_ACTIVE=local \
    DB_URL="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${TARGET_DB}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
    DB_USERNAME="$MYSQL_USER" DB_PASSWORD="${ACCEPTANCE_MYSQL_PASSWORD:-${MYSQL_PWD:-}}" \
    REDIS_HOST=127.0.0.1 REDIS_PORT="$REDIS_PORT" REDIS_DATABASE=0 \
    JWT_SECRET="$jwt_secret" \
    ADMIN_RESET_USERNAME="$admin_user" ADMIN_RESET_PASSWORD="$admin_password" \
    WECHAT_MINI_PROGRAM_MOCK_ENABLED=true FILE_UPLOAD_PATH="$UPLOAD_DIR" \
    KNIFE4J_ENABLE=false API_DOCS_ENABLED=false SWAGGER_UI_ENABLED=false \
    mvn -q spring-boot:run >"$BACKEND_LOG" 2>&1
) &
BACKEND_PID=$!

ready=0
for _ in $(seq 1 180); do
  if curl -fsS --max-time 2 "$API_BASE/auth/captcha" >/dev/null 2>&1 \
    && grep -q '已显式重置平台管理员' "$BACKEND_LOG"; then
    ready=1
    break
  fi
  kill -0 "$BACKEND_PID" >/dev/null 2>&1 || break
  sleep 0.5
done
[ "$ready" -eq 1 ] || fail '临时后端未完成启动与管理员重置'
admin_token="$(login "$admin_user" "$admin_password")"
info '临时 Redis、后端、上传目录和平台管理员真实认证通过'

assignee_response="$(curl -fsS --max-time 20 -H "Authorization: Bearer $admin_token" \
  "$API_BASE/quality/issues/assignees?projectId=$project_id")"
jq -e '.code == 200 and (.data | type == "array") and (.data | length) >= 1' \
  >/dev/null <<<"$assignee_response" || fail '当前项目没有合格质量整改候选人'
assignee_count="$(jq -r '.data | length' <<<"$assignee_response")"
assignee_one="$(jq -r '.data[0].userId' <<<"$assignee_response")"
if [ "$assignee_count" -ge 2 ]; then
  assignee_two="$(jq -r '.data[1].userId' <<<"$assignee_response")"
  info "真实候选接口返回 ${assignee_count} 名合格整改人，批量问题使用两名不同整改人"
else
  assignee_two="$assignee_one"
  warn '真实候选接口仅返回 1 名合格整改人；隔离验收不补造源数据授权，两条问题复用该整改人'
fi

TEST_WEEKS=()
while IFS= read -r week_start; do
  [ -n "$week_start" ] && TEST_WEEKS+=("$week_start")
done < <(mysql_value "
  WITH RECURSIVE seq(n) AS (
    SELECT 4
    UNION ALL
    SELECT n+1 FROM seq WHERE n<520
  )
  SELECT DATE_FORMAT(
           DATE_SUB(DATE_SUB(CURDATE(),INTERVAL WEEKDAY(CURDATE()) DAY),INTERVAL n WEEK),
           '%Y-%m-%d') AS week_start
    FROM seq
   WHERE NOT EXISTS (
     SELECT 1 FROM quality_weekly_inspection q
      WHERE q.project_id=$project_id
        AND q.week_start=DATE_SUB(
          DATE_SUB(CURDATE(),INTERVAL WEEKDAY(CURDATE()) DAY),INTERVAL n WEEK)
   )
   ORDER BY n
   LIMIT 5;")
[ "${#TEST_WEEKS[@]}" -eq 5 ] || fail '无法找到 5 个互不冲突的往期自然周'
concurrent_week="${TEST_WEEKS[0]}"
rollback_week="${TEST_WEEKS[1]}"
discard_week="${TEST_WEEKS[2]}"
zero_week="${TEST_WEEKS[3]}"
bulk_week="${TEST_WEEKS[4]}"
future_week="$(mysql_value "SELECT DATE_FORMAT(
  DATE_ADD(DATE_SUB(CURDATE(),INTERVAL WEEKDAY(CURDATE()) DAY),INTERVAL 1 WEEK),'%Y-%m-%d');")"
deadline="$(mysql_value "SELECT DATE_FORMAT(DATE_ADD(CURDATE(),INTERVAL 14 DAY),'%Y-%m-%d');")"

future_payload="$(jq -n --argjson projectId "$project_id" --arg weekStart "$future_week" \
  '{projectId:$projectId,weekStart:$weekStart}')"
request_to_files future-week POST '/quality/weekly-inspections/drafts' "$admin_token" "$future_payload"
assert_http future-week 400 '未来周周检未被拒绝'
assert_jq future-week '.code == 400 and (.message | contains("未来周"))' '未来周错误响应不正确'
assert_mysql_equals 0 \
  "SELECT COUNT(*) FROM quality_weekly_inspection
    WHERE project_id=$project_id AND week_start='$future_week';" \
  '未来周拒绝后仍写入了周检记录'
info '未来自然周创建返回 HTTP 400 且数据库无写入'

concurrent_create_payload="$(jq -n --argjson projectId "$project_id" --arg weekStart "$concurrent_week" \
  '{projectId:$projectId,weekStart:$weekStart}')"
request_to_files concurrent-create-a POST '/quality/weekly-inspections/drafts' \
  "$admin_token" "$concurrent_create_payload" &
create_pid_a=$!
request_to_files concurrent-create-b POST '/quality/weekly-inspections/drafts' \
  "$admin_token" "$concurrent_create_payload" &
create_pid_b=$!
wait "$create_pid_a"
wait "$create_pid_b"
assert_http concurrent-create-a 200 '第一路同周并发创建失败'
assert_http concurrent-create-b 200 '第二路同周并发创建失败'
concurrent_id_a="$(jq -r '.data.id' "$TEMP_DIR/concurrent-create-a.body")"
concurrent_id_b="$(jq -r '.data.id' "$TEMP_DIR/concurrent-create-b.body")"
[ "$concurrent_id_a" = "$concurrent_id_b" ] || fail '同周并发创建返回了不同周检ID'
concurrent_id="$concurrent_id_a"
assert_mysql_equals 1 \
  "SELECT COUNT(*) FROM quality_weekly_inspection
    WHERE project_id=$project_id AND week_start='$concurrent_week';" \
  '同项目同周并发创建产生了重复周检'
info '同项目同周两路并发创建均成功恢复同一草稿，数据库仅一条记录'

concurrent_photo_one="$(upload_photo "$admin_token" "$project_id" \
  QUALITY_WEEKLY_ITEM_PENDING concurrent-item-one)"
concurrent_photo_two="$(upload_photo "$admin_token" "$project_id" \
  QUALITY_WEEKLY_ITEM_PENDING concurrent-item-two)"
concurrent_save_payload="$(jq -n \
  --arg inspectionDate "$concurrent_week" --arg deadline "$deadline" \
  --argjson assigneeOne "$assignee_one" --argjson assigneeTwo "$assignee_two" \
  --argjson photoOne "$concurrent_photo_one" --argjson photoTwo "$concurrent_photo_two" \
  '{expectedVersion:0,inspectionDate:$inspectionDate,conclusion:"并发双提交隔离验收",overviewPhotoFileIds:[],items:[
    {itemKey:"concurrent-one",itemOrder:1,title:"并发提交问题一",location:"验收区域一",description:"独立问题一",severity:"WARNING",assigneeId:$assigneeOne,deadline:$deadline,beforePhotoFileIds:[$photoOne]},
    {itemKey:"concurrent-two",itemOrder:2,title:"并发提交问题二",location:"验收区域二",description:"独立问题二",severity:"DANGER",assigneeId:$assigneeTwo,deadline:$deadline,beforePhotoFileIds:[$photoTwo]}
  ]}')"
request_to_files concurrent-save PUT "/quality/weekly-inspections/$concurrent_id/draft" \
  "$admin_token" "$concurrent_save_payload"
assert_http concurrent-save 200 '并发提交草稿保存失败'
assert_jq concurrent-save '.code == 200 and .data.version == 1 and (.data.draftItems | length) == 2' \
  '并发提交草稿保存响应不正确'
concurrent_version="$(jq -r '.data.version' "$TEMP_DIR/concurrent-save.body")"
concurrent_submit_payload="$(jq -n --argjson expectedVersion "$concurrent_version" \
  '{expectedVersion:$expectedVersion}')"
request_to_files concurrent-submit-a POST "/quality/weekly-inspections/$concurrent_id/submit" \
  "$admin_token" "$concurrent_submit_payload" &
submit_pid_a=$!
request_to_files concurrent-submit-b POST "/quality/weekly-inspections/$concurrent_id/submit" \
  "$admin_token" "$concurrent_submit_payload" &
submit_pid_b=$!
wait "$submit_pid_a"
wait "$submit_pid_b"
assert_http concurrent-submit-a 200 '第一路并发提交失败'
assert_http concurrent-submit-b 200 '第二路并发提交失败'
assert_jq concurrent-submit-a '.code == 200 and .data.status == "SUBMITTED" and .data.submittedIssueCount == 2 and (.data.issues | length) == 2' \
  '第一路并发提交响应不完整'
assert_jq concurrent-submit-b '.code == 200 and .data.status == "SUBMITTED" and .data.submittedIssueCount == 2 and (.data.issues | length) == 2' \
  '第二路并发提交未幂等返回已有结果'
inspection_no_a="$(jq -r '.data.inspectionNo' "$TEMP_DIR/concurrent-submit-a.body")"
inspection_no_b="$(jq -r '.data.inspectionNo' "$TEMP_DIR/concurrent-submit-b.body")"
[ "$inspection_no_a" = "$inspection_no_b" ] || fail '并发双提交返回了不同周检编号'
assert_mysql_equals 2 \
  "SELECT COUNT(*) FROM quality_issue WHERE weekly_inspection_id=$concurrent_id AND deleted=0;" \
  '并发双提交重复或遗漏生成质量问题'
assert_mysql_equals 2 \
  "SELECT COUNT(*) FROM quality_issue_log l
    JOIN quality_issue i ON i.id=l.issue_id
   WHERE i.weekly_inspection_id=$concurrent_id AND i.deleted=0 AND l.action_type='CREATE';" \
  '并发双提交的问题创建日志数量不正确'
assert_mysql_equals 1 \
  "SELECT COUNT(*) FROM sys_operation_log
    WHERE business_type='QUALITY_WEEKLY_INSPECTION' AND business_id=$concurrent_id
      AND operation_type='QUALITY_WEEKLY_SUBMIT';" \
  '并发双提交重复写入周检提交审计'
info '两路并发提交均返回同一正式周检，问题、创建日志和提交审计均未重复'

rollback_id="$(create_draft rollback-create "$admin_token" "$project_id" "$rollback_week")"
rollback_photo_one="$(upload_photo "$admin_token" "$project_id" \
  QUALITY_WEEKLY_ITEM_PENDING rollback-item-one)"
rollback_photo_two="$(upload_photo "$admin_token" "$project_id" \
  QUALITY_WEEKLY_ITEM_PENDING rollback-item-two)"
rollback_save_payload="$(jq -n \
  --arg inspectionDate "$rollback_week" --arg deadline "$deadline" \
  --argjson assigneeOne "$assignee_one" --argjson assigneeTwo "$assignee_two" \
  --argjson photoOne "$rollback_photo_one" --argjson photoTwo "$rollback_photo_two" \
  '{expectedVersion:0,inspectionDate:$inspectionDate,conclusion:"整批回滚隔离验收",overviewPhotoFileIds:[],items:[
    {itemKey:"rollback-one",itemOrder:1,title:"回滚问题一",severity:"NORMAL",assigneeId:$assigneeOne,deadline:$deadline,beforePhotoFileIds:[$photoOne]},
    {itemKey:"rollback-two",itemOrder:2,title:"回滚问题二",severity:"WARNING",assigneeId:$assigneeTwo,deadline:$deadline,beforePhotoFileIds:[$photoTwo]}
  ]}')"
request_to_files rollback-save PUT "/quality/weekly-inspections/$rollback_id/draft" \
  "$admin_token" "$rollback_save_payload"
assert_http rollback-save 200 '整批回滚草稿保存失败'
rollback_version="$(jq -r '.data.version' "$TEMP_DIR/rollback-save.body")"

mysql "${MYSQL_ARGS[@]}" "$TARGET_DB" <<SQL
DROP TRIGGER IF EXISTS trg_quality_weekly_acceptance_fail_second;
DELIMITER //
CREATE TRIGGER trg_quality_weekly_acceptance_fail_second
BEFORE INSERT ON quality_issue
FOR EACH ROW
BEGIN
  IF NEW.weekly_inspection_id = $rollback_id THEN
    IF COALESCE(@quality_weekly_acceptance_inspection_id, 0) <> NEW.weekly_inspection_id THEN
      SET @quality_weekly_acceptance_inspection_id = NEW.weekly_inspection_id;
      SET @quality_weekly_acceptance_insert_count = 0;
    END IF;
    SET @quality_weekly_acceptance_insert_count = COALESCE(@quality_weekly_acceptance_insert_count, 0) + 1;
    IF @quality_weekly_acceptance_insert_count = 2 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'QUALITY_WEEKLY_ACCEPTANCE_SECOND_INSERT_FAILURE';
    END IF;
  END IF;
END//
DELIMITER ;
SQL
rollback_submit_payload="$(jq -n --argjson expectedVersion "$rollback_version" \
  '{expectedVersion:$expectedVersion}')"
request_to_files rollback-submit-failed POST "/quality/weekly-inspections/$rollback_id/submit" \
  "$admin_token" "$rollback_submit_payload"
assert_http rollback-submit-failed 500 '第二条问题插入触发器未制造整批提交失败'
assert_jq rollback-submit-failed '.code == 500' '触发器失败响应不是统一 HTTP 500 业务体'
assert_mysql_equals "DRAFT:$rollback_version:0" \
  "SELECT CONCAT(status,':',version,':',submitted_issue_count)
     FROM quality_weekly_inspection WHERE id=$rollback_id;" \
  '第二条问题插入失败后周检主记录未完整回滚'
assert_mysql_equals 2 \
  "SELECT COUNT(*) FROM quality_weekly_inspection_draft_item WHERE inspection_id=$rollback_id;" \
  '第二条问题插入失败后草稿问题未保留'
assert_mysql_equals 0 \
  "SELECT COUNT(*) FROM quality_issue WHERE weekly_inspection_id=$rollback_id AND deleted=0;" \
  '第二条问题插入失败后遗留了部分正式问题'
assert_mysql_equals 2 \
  "SELECT COUNT(*) FROM file_resource f
    JOIN quality_weekly_inspection_draft_item d ON d.id=f.business_id
   WHERE d.inspection_id=$rollback_id AND f.business_type='QUALITY_WEEKLY_DRAFT_ITEM'
     AND f.deleted=0 AND f.status='UPLOADED';" \
  '第二条问题插入失败后草稿附件未回滚到原绑定'
assert_mysql_equals 0 \
  "SELECT COUNT(*) FROM sys_operation_log
    WHERE business_type='QUALITY_WEEKLY_INSPECTION' AND business_id=$rollback_id
      AND operation_type='QUALITY_WEEKLY_SUBMIT';" \
  '第二条问题插入失败后遗留成功提交审计'
mysql "${MYSQL_ARGS[@]}" "$TARGET_DB" -e \
  'DROP TRIGGER IF EXISTS trg_quality_weekly_acceptance_fail_second;'
request_to_files rollback-submit-recovered POST "/quality/weekly-inspections/$rollback_id/submit" \
  "$admin_token" "$rollback_submit_payload"
assert_http rollback-submit-recovered 200 '触发器移除后草稿无法恢复提交'
assert_jq rollback-submit-recovered '.code == 200 and .data.status == "SUBMITTED" and .data.submittedIssueCount == 2' \
  '恢复提交结果不正确'
assert_mysql_equals 2 \
  "SELECT COUNT(*) FROM quality_issue WHERE weekly_inspection_id=$rollback_id AND deleted=0;" \
  '恢复提交未生成两条正式问题'
info '第二条问题插入失败使主记录、问题、日志和附件整批回滚；原草稿随后成功恢复提交'

discard_id="$(create_draft discard-create "$admin_token" "$project_id" "$discard_week")"
discard_overview_photo="$(upload_photo "$admin_token" "$project_id" \
  QUALITY_WEEKLY_PENDING discard-overview)"
discard_item_photo="$(upload_photo "$admin_token" "$project_id" \
  QUALITY_WEEKLY_ITEM_PENDING discard-item)"
discard_save_payload="$(jq -n \
  --arg inspectionDate "$discard_week" \
  --argjson overviewPhoto "$discard_overview_photo" --argjson itemPhoto "$discard_item_photo" \
  '{expectedVersion:0,inspectionDate:$inspectionDate,conclusion:"待放弃共享草稿",overviewPhotoFileIds:[$overviewPhoto],items:[
    {itemKey:"discard-item",itemOrder:1,title:"尚未整理完成",severity:"NORMAL",beforePhotoFileIds:[$itemPhoto]}
  ]}')"
request_to_files discard-save PUT "/quality/weekly-inspections/$discard_id/draft" \
  "$admin_token" "$discard_save_payload"
assert_http discard-save 200 '待放弃草稿保存失败'
discard_version="$(jq -r '.data.version' "$TEMP_DIR/discard-save.body")"
discard_overview_path="$(mysql_value "SELECT file_path FROM file_resource WHERE id=$discard_overview_photo;")"
discard_item_path="$(mysql_value "SELECT file_path FROM file_resource WHERE id=$discard_item_photo;")"
discard_payload="$(jq -n --argjson expectedVersion "$discard_version" \
  '{expectedVersion:$expectedVersion}')"
request_to_files discard-action POST "/quality/weekly-inspections/$discard_id/discard" \
  "$admin_token" "$discard_payload"
assert_http discard-action 200 '共享草稿放弃失败'
assert_mysql_equals 0 \
  "SELECT COUNT(*) FROM quality_weekly_inspection WHERE id=$discard_id;" \
  '放弃后周检主记录仍存在'
assert_mysql_equals 0 \
  "SELECT COUNT(*) FROM quality_weekly_inspection_draft_item WHERE inspection_id=$discard_id;" \
  '放弃后问题草稿仍存在'
assert_mysql_equals 0 \
  "SELECT COUNT(*) FROM file_resource WHERE id IN ($discard_overview_photo,$discard_item_photo);" \
  '放弃后草稿附件元数据未清理'
[ ! -e "$discard_overview_path" ] && [ ! -e "$discard_item_path" ] \
  || fail '放弃后草稿物理照片未在事务提交后清理'
rebuilt_id="$(create_draft discard-rebuild "$admin_token" "$project_id" "$discard_week")"
[ "$rebuilt_id" != "$discard_id" ] || fail '放弃后重新创建仍复用了已删除草稿主键'
assert_mysql_equals 1 \
  "SELECT COUNT(*) FROM quality_weekly_inspection
    WHERE project_id=$project_id AND week_start='$discard_week' AND status='DRAFT';" \
  '放弃后未能重建同周共享草稿'
info '共享草稿放弃后数据库与物理附件均清理，同周可以重新创建'

zero_id="$(create_draft zero-create "$admin_token" "$project_id" "$zero_week")"
zero_overview_photo="$(upload_photo "$admin_token" "$project_id" \
  QUALITY_WEEKLY_PENDING zero-overview)"
zero_save_payload="$(jq -n --arg inspectionDate "$zero_week" --argjson overviewPhoto "$zero_overview_photo" \
  '{expectedVersion:0,inspectionDate:$inspectionDate,conclusion:"本周检查未发现质量问题",overviewPhotoFileIds:[$overviewPhoto],items:[]}')"
request_to_files zero-save PUT "/quality/weekly-inspections/$zero_id/draft" \
  "$admin_token" "$zero_save_payload"
assert_http zero-save 200 '零问题周检凭证草稿保存失败'
zero_version="$(jq -r '.data.version' "$TEMP_DIR/zero-save.body")"
zero_submit_payload="$(jq -n --argjson expectedVersion "$zero_version" \
  '{expectedVersion:$expectedVersion}')"
request_to_files zero-submit POST "/quality/weekly-inspections/$zero_id/submit" \
  "$admin_token" "$zero_submit_payload"
assert_http zero-submit 200 '零问题周检提交失败'
if ! jq -e --argjson photoId "$zero_overview_photo" \
  '.code == 200 and .data.status == "SUBMITTED" and .data.submittedIssueCount == 0
    and (.data.issues | length) == 0 and (.data.overviewPhotoFileIds | index($photoId) != null)' \
  "$TEMP_DIR/zero-submit.body" >/dev/null; then
  sed -n '1,100p' "$TEMP_DIR/zero-submit.body" >&2
  fail '零问题周检返回内容缺少结论或现场凭证'
fi
assert_mysql_equals 0 \
  "SELECT COUNT(*) FROM quality_issue WHERE weekly_inspection_id=$zero_id AND deleted=0;" \
  '零问题周检错误生成了质量问题'
assert_mysql_equals "QUALITY_WEEKLY_INSPECTION:$zero_id" \
  "SELECT CONCAT(business_type,':',business_id)
     FROM file_resource WHERE id=$zero_overview_photo;" \
  '零问题周检现场照片未迁移为正式附件'
info '零问题周检凭结论和现场照片成功提交，未生成质量问题'

bulk_id="$(create_draft bulk-create "$admin_token" "$project_id" "$bulk_week")"
bulk_fifty_payload="$(jq -n --arg inspectionDate "$bulk_week" '
  {expectedVersion:0,inspectionDate:$inspectionDate,conclusion:"50项草稿边界验收",overviewPhotoFileIds:[],items:[
    range(1;51) as $order |
    {itemKey:("bulk-"+($order|tostring)),itemOrder:$order,title:("草稿问题"+($order|tostring)),severity:"NORMAL",beforePhotoFileIds:[]}
  ]}')"
request_to_files bulk-fifty PUT "/quality/weekly-inspections/$bulk_id/draft" \
  "$admin_token" "$bulk_fifty_payload"
assert_http bulk-fifty 200 '50项问题草稿保存失败'
assert_jq bulk-fifty '.code == 200 and .data.version == 1 and (.data.draftItems | length) == 50' \
  '50项问题草稿响应数量不正确'
bulk_version="$(jq -r '.data.version' "$TEMP_DIR/bulk-fifty.body")"
bulk_fifty_one_payload="$(jq -n --arg inspectionDate "$bulk_week" --argjson expectedVersion "$bulk_version" '
  {expectedVersion:$expectedVersion,inspectionDate:$inspectionDate,conclusion:"51项拒绝边界验收",overviewPhotoFileIds:[],items:[
    range(1;52) as $order |
    {itemKey:("bulk-over-"+($order|tostring)),itemOrder:$order,title:("超限问题"+($order|tostring)),severity:"NORMAL",beforePhotoFileIds:[]}
  ]}')"
request_to_files bulk-fifty-one PUT "/quality/weekly-inspections/$bulk_id/draft" \
  "$admin_token" "$bulk_fifty_one_payload"
assert_http bulk-fifty-one 400 '51项问题草稿未被拒绝'
assert_jq bulk-fifty-one '.code == 400 and (.message | contains("50"))' '51项拒绝响应不正确'
assert_mysql_equals '1:50' \
  "SELECT CONCAT(q.version,':',COUNT(d.id))
     FROM quality_weekly_inspection q
     LEFT JOIN quality_weekly_inspection_draft_item d ON d.inspection_id=q.id
    WHERE q.id=$bulk_id GROUP BY q.id,q.version;" \
  '51项拒绝后覆盖了已保存的50项草稿或版本'
info '50项草稿保存成功，51项请求返回 HTTP 400 且未覆盖服务端草稿'

legacy_valid_payload="$(jq -n --argjson projectId "$project_id" --arg deadline "$deadline" \
  '{projectId:$projectId,title:"旧单问题入口",severity:"NORMAL",assigneeId:1,deadline:$deadline,photoFileIds:[1]}')"
request_to_files legacy-empty POST '/quality/issues' "$admin_token" ''
request_to_files legacy-empty-object POST '/quality/issues' "$admin_token" '{}'
request_to_files legacy-valid POST '/quality/issues' "$admin_token" "$legacy_valid_payload"
request_to_files legacy-malformed POST '/quality/issues' "$admin_token" '{'
for legacy_case in legacy-empty legacy-empty-object legacy-valid legacy-malformed; do
  assert_http "$legacy_case" 410 "旧单问题入口未稳定返回 410：$legacy_case"
  assert_jq "$legacy_case" '.code == 410 and (.message | contains("质量周检"))' \
    "旧单问题入口 410 响应不正确：$legacy_case"
done
info '旧 POST /quality/issues 对空体、空对象、合法旧载荷和畸形 JSON 均返回 HTTP 410'

source_tables_after="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SOURCE_DB';")"
source_weekly_tables_after="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='$SOURCE_DB'
      AND table_name IN ('quality_weekly_inspection','quality_weekly_inspection_draft_item');")"
[ "$source_tables_after" = "$source_tables_before" ] \
  && [ "$source_weekly_tables_after" = "$source_weekly_tables_before" ] \
  || fail '源数据库结构在隔离验收期间发生变化'

printf '[PASS] 质量周检隔离数据库真实 HTTP/MySQL 验收通过；源库未写入，临时库、Redis、后端、上传目录将在退出时清理。\n'
