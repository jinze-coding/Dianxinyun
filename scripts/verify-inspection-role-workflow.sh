#!/usr/bin/env bash

set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]:-$0}"
ROOT_DIR="$(cd "$(dirname "$SCRIPT_PATH")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
PHOTO_PATH="$ROOT_DIR/wechat-miniprogram/site-platform-miniprogram/src/static/brand/zhihui-yingzao-horizontal.png"

SOURCE_DB="${ACCEPTANCE_SOURCE_DB:-dianxinyun}"
MYSQL_HOST="${ACCEPTANCE_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${ACCEPTANCE_MYSQL_PORT:-3306}"
MYSQL_USER="${ACCEPTANCE_MYSQL_USER:-root}"
API_PORT="${ACCEPTANCE_API_PORT:-18080}"
REDIS_PORT="${ACCEPTANCE_REDIS_PORT:-16380}"
TARGET_DB="dianxinyun_acceptance_$(date +%Y%m%d_%H%M%S)_$$"
API_BASE="http://127.0.0.1:${API_PORT}/api/v1"
TEMP_DIR="$(mktemp -d /private/tmp/dianxinyun-role-acceptance.XXXXXX)"
UPLOAD_DIR="$TEMP_DIR/uploads"
BACKEND_LOG="$TEMP_DIR/backend.log"
REDIS_LOG="$TEMP_DIR/redis.log"
BACKEND_PID=''
REDIS_PID=''

MYSQL_ARGS=(-h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER")

info() {
  printf '[OK] %s\n' "$*"
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
  stop_pid "$BACKEND_PID"
  stop_pid "$REDIS_PID"
  if [[ "$TARGET_DB" =~ ^dianxinyun_acceptance_[0-9_]+$ ]]; then
    mysql "${MYSQL_ARGS[@]}" -e "DROP DATABASE IF EXISTS \`$TARGET_DB\`;" >/dev/null 2>&1 || true
  fi
  case "$TEMP_DIR" in
    /private/tmp/dianxinyun-role-acceptance.*) rm -rf -- "$TEMP_DIR" ;;
  esac
}

show_failure() {
  printf '[ERROR] 隔离角色闭环验收失败，后端日志末尾如下：\n' >&2
  tail -n 80 "$BACKEND_LOG" 2>/dev/null >&2 || true
}

trap cleanup EXIT
trap show_failure ERR

for command in mysql mysqldump curl jq lsof mvn openssl; do
  require_command "$command"
done
[[ "$SOURCE_DB" =~ ^[A-Za-z0-9_]+$ ]] || fail '源数据库名只能包含字母、数字和下划线'
[[ "$API_PORT" =~ ^[0-9]+$ && "$REDIS_PORT" =~ ^[0-9]+$ ]] || fail '端口必须是数字'
[ -f "$PHOTO_PATH" ] || fail '缺少隔离验收用 PNG 文件'
[ -z "$(lsof -tiTCP:"$API_PORT" -sTCP:LISTEN 2>/dev/null || true)" ] || fail "$API_PORT 端口已被占用"
[ -z "$(lsof -tiTCP:"$REDIS_PORT" -sTCP:LISTEN 2>/dev/null || true)" ] || fail "$REDIS_PORT 端口已被占用"
REDIS_SERVER="$(resolve_redis_server)" || fail '缺少命令：redis-server'

source_exists="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$SOURCE_DB';")"
[ "$source_exists" -eq 1 ] || fail "源数据库不存在：$SOURCE_DB"

mysql "${MYSQL_ARGS[@]}" -e \
  "CREATE DATABASE \`$TARGET_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysqldump "${MYSQL_ARGS[@]}" --single-transaction --routines --triggers --set-gtid-purged=OFF "$SOURCE_DB" \
  | mysql "${MYSQL_ARGS[@]}" "$TARGET_DB"
source_tables="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SOURCE_DB';")"
target_tables="$(mysql "${MYSQL_ARGS[@]}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TARGET_DB';")"
[ "$source_tables" -eq "$target_tables" ] || fail '临时数据库表数量与源库不一致'
info "临时数据库副本已建立（${target_tables} 张表）"

read -r project_id box_id < <(mysql "${MYSQL_ARGS[@]}" -N "$TARGET_DB" -e \
  "SELECT b.project_id,b.id
     FROM electric_box b
    WHERE b.deleted=0 AND b.status='ACTIVE'
      AND NOT EXISTS (
        SELECT 1 FROM inspection_record ir
         WHERE ir.electric_box_id=b.id AND ir.check_date=CURDATE() AND ir.deleted=0)
      AND EXISTS (
        SELECT 1 FROM sys_user_project_role upr JOIN sys_role r ON r.id=upr.role_id
         WHERE upr.project_id=b.project_id AND r.role_code='ELECTRICIAN' AND r.deleted=0)
      AND EXISTS (
        SELECT 1 FROM sys_user_project_role upr JOIN sys_role r ON r.id=upr.role_id
         WHERE upr.project_id=b.project_id AND r.role_code='SAFETY_OFFICER' AND r.deleted=0)
    ORDER BY b.id LIMIT 1;")
[ -n "${project_id:-}" ] || fail '没有同时具备电工、安全员且今日未巡检的启用电箱'

read -r admin_id admin_user < <(mysql "${MYSQL_ARGS[@]}" -N "$TARGET_DB" -e \
  "SELECT u.id,u.username FROM sys_user u
     JOIN sys_user_role ur ON ur.user_id=u.id
     JOIN sys_role r ON r.id=ur.role_id
    WHERE u.deleted=0 AND u.status=1 AND r.deleted=0 AND r.role_code='PLATFORM_ADMIN'
    ORDER BY u.id LIMIT 1;")
read -r electrician_id electrician_user < <(mysql "${MYSQL_ARGS[@]}" -N "$TARGET_DB" -e \
  "SELECT u.id,u.username FROM sys_user u
     JOIN sys_user_project_role upr ON upr.user_id=u.id AND upr.project_id=$project_id
     JOIN sys_role r ON r.id=upr.role_id
     JOIN sys_user_project up ON up.user_id=u.id AND up.project_id=$project_id AND up.status='ACTIVE'
    WHERE u.deleted=0 AND u.status=1 AND r.deleted=0 AND r.role_code='ELECTRICIAN'
    ORDER BY u.id LIMIT 1;")
read -r safety_id safety_user < <(mysql "${MYSQL_ARGS[@]}" -N "$TARGET_DB" -e \
  "SELECT u.id,u.username FROM sys_user u
     JOIN sys_user_project_role upr ON upr.user_id=u.id AND upr.project_id=$project_id
     JOIN sys_role r ON r.id=upr.role_id
     JOIN sys_user_project up ON up.user_id=u.id AND up.project_id=$project_id AND up.status='ACTIVE'
    WHERE u.deleted=0 AND u.status=1 AND r.deleted=0 AND r.role_code='SAFETY_OFFICER'
    ORDER BY u.id LIMIT 1;")
[ -n "${admin_id:-}" ] && [ -n "${electrician_id:-}" ] && [ -n "${safety_id:-}" ] \
  || fail '缺少可用的平台管理员、电工或安全员角色主体'

admin_password="Aa$(openssl rand -hex 12)9"
electrician_password="Bb$(openssl rand -hex 12)8"
safety_password="Cc$(openssl rand -hex 12)7"
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
    DB_USERNAME="$MYSQL_USER" DB_PASSWORD="${MYSQL_PWD:-}" \
    REDIS_HOST=127.0.0.1 REDIS_PORT="$REDIS_PORT" REDIS_DATABASE=0 \
    JWT_SECRET="$jwt_secret" \
    ADMIN_RESET_USERNAME="$admin_user" ADMIN_RESET_PASSWORD="$admin_password" \
    WECHAT_MINI_PROGRAM_MOCK_ENABLED=true FILE_UPLOAD_PATH="$UPLOAD_DIR" \
    KNIFE4J_ENABLE=false API_DOCS_ENABLED=false SWAGGER_UI_ENABLED=false \
    mvn -q spring-boot:run >"$BACKEND_LOG" 2>&1
) &
BACKEND_PID=$!

ready=0
for _ in $(seq 1 120); do
  if curl -fsS --max-time 2 "$API_BASE/auth/captcha" >/dev/null 2>&1 \
    && grep -q '已显式重置平台管理员' "$BACKEND_LOG"; then
    ready=1
    break
  fi
  kill -0 "$BACKEND_PID" >/dev/null 2>&1 || break
  sleep 0.5
done
[ "$ready" -eq 1 ] || fail '临时后端未完成启动与管理员重置'
info '临时后端启动、启动 runner 和验证码通过'

login() {
  local username="$1"
  local password="$2"
  local response
  response="$(jq -n --arg username "$username" --arg password "$password" \
    '{username:$username,password:$password}' \
    | curl -fsS -H 'Content-Type: application/json' --data-binary @- "$API_BASE/auth/login")"
  jq -e '.code == 200 and (.data.token | type == "string" and length > 20)' >/dev/null <<<"$response"
  jq -r '.data.token' <<<"$response"
}

reset_password() {
  local target_id="$1"
  local new_password="$2"
  local token="$3"
  jq -n --arg newPassword "$new_password" '{newPassword:$newPassword}' \
    | curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
      --data-binary @- "$API_BASE/system/users/$target_id/reset-password" \
    | jq -e '.code == 200' >/dev/null
}

http_status() {
  local method="$1"
  local path="$2"
  local token="$3"
  local body="$4"
  curl -sS -o /dev/null -w '%{http_code}' -X "$method" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data-binary "$body" "$API_BASE$path"
}

admin_token="$(login "$admin_user" "$admin_password")"
reset_password "$electrician_id" "$electrician_password" "$admin_token"
reset_password "$safety_id" "$safety_password" "$admin_token"
electrician_token="$(login "$electrician_user" "$electrician_password")"
safety_token="$(login "$safety_user" "$safety_password")"
info '平台管理员、电工和安全员真实认证通过'

for role_token in "$electrician_token" "$safety_token"; do
  curl -fsS -H "Authorization: Bearer $role_token" "$API_BASE/auth/user-info" \
    | jq -e --argjson projectId "$project_id" \
      '.code == 200 and (.data.accessibleProjectIds | index($projectId) != null)' >/dev/null
done
info '角色项目范围与当前 user-info DTO 一致'

check_date="$(date +%F)"
deadline="$(date -v+2d +%F 2>/dev/null || date -d '+2 days' +%F)"
record_payload="$(jq -n \
  --argjson projectId "$project_id" --argjson boxId "$box_id" --argjson assigneeId "$electrician_id" \
  --arg checkDate "$check_date" --arg deadline "$deadline" \
  '{projectId:$projectId,electricBoxId:$boxId,templateCode:"ELECTRIC_BOX_DAILY",source:"ELECTRICIAN_DAILY",checkDate:$checkDate,assigneeId:$assigneeId,requirement:"隔离验收：修复异常项",deadline:$deadline,remark:"BUG-008 隔离角色闭环验收",outerPhotoFileIds:[],innerPhotoFileIds:[],items:[{itemCode:"APPEARANCE",itemName:"内外观",result:"ABNORMAL",description:"隔离验收异常"},{itemCode:"LEAKAGE_PROTECTOR",itemName:"漏电保护器",result:"NORMAL",description:"正常"},{itemCode:"FUSE",itemName:"熔断",result:"NORMAL",description:"正常"},{itemCode:"PROTECTIVE_ZERO",itemName:"保护接零",result:"NORMAL",description:"正常"},{itemCode:"SOCKET_220V",itemName:"220V插座",result:"NORMAL",description:"正常"},{itemCode:"SOCKET_380V",itemName:"380V插座",result:"NORMAL",description:"正常"}]}' )"
record_response="$(curl -fsS -H "Authorization: Bearer $electrician_token" \
  -H 'Content-Type: application/json' --data-binary "$record_payload" "$API_BASE/inspection/records")"
jq -e --argjson inspectorId "$electrician_id" \
  '.code == 200 and .data.status == "RECTIFICATION_PENDING" and .data.inspectorId == $inspectorId and .data.abnormalCount == 1' \
  >/dev/null <<<"$record_response"
record_id="$(jq -r '.data.id' <<<"$record_response")"
rectifications="$(curl -fsS -H "Authorization: Bearer $electrician_token" \
  "$API_BASE/inspection/rectifications?projectId=$project_id")"
rectification_id="$(jq -r --argjson recordId "$record_id" \
  '.data[] | select(.inspectionRecordId == $recordId) | .id' <<<"$rectifications" | head -n 1)"
[ -n "$rectification_id" ]
jq -e --argjson id "$rectification_id" --argjson assigneeId "$electrician_id" \
  '.code == 200 and (.data[] | select(.id == $id and .assigneeId == $assigneeId and .status == "PENDING" and .canRectify == true))' \
  >/dev/null <<<"$rectifications"
info '电工提交异常巡检并生成本人整改任务'

safety_complete_status="$(http_status POST "/inspection/rectifications/$rectification_id/complete" \
  "$safety_token" '{"feedback":"unauthorized"}')"
electrician_close_status="$(http_status POST "/inspection/rectifications/$rectification_id/close" \
  "$electrician_token" '{"comment":"unauthorized"}')"
[ "$safety_complete_status" = '403' ] && [ "$electrician_close_status" = '403' ]
info '跨角色完成/复查均返回 HTTP 403'

upload_response="$(curl -fsS -H "Authorization: Bearer $electrician_token" \
  -F "file=@$PHOTO_PATH" -F "projectId=$project_id" -F 'businessType=inspection_rectification' \
  -F 'fileType=RECTIFICATION_PHOTO' -F 'fileName=BUG-008隔离验收整改照片.png' "$API_BASE/files")"
jq -e '.code == 200 and (.data.id | type == "number")' >/dev/null <<<"$upload_response"
photo_id="$(jq -r '.data.id' <<<"$upload_response")"
complete_payload="$(jq -n --argjson photoId "$photo_id" \
  '{feedback:"隔离验收整改已完成",photoFileIds:[$photoId]}')"
complete_response="$(curl -fsS -H "Authorization: Bearer $electrician_token" \
  -H 'Content-Type: application/json' --data-binary "$complete_payload" \
  "$API_BASE/inspection/rectifications/$rectification_id/complete")"
jq -e --argjson assigneeId "$electrician_id" \
  '.code == 200 and .data.status == "COMPLETED" and .data.assigneeId == $assigneeId and .data.canRectify == false' \
  >/dev/null <<<"$complete_response"
info '指定电工上传证据并完成整改'

safety_detail="$(curl -fsS -H "Authorization: Bearer $safety_token" \
  "$API_BASE/inspection/rectifications/$rectification_id")"
if ! jq -e '.code == 200 and .data.status == "COMPLETED" and .data.canReview == true' \
  >/dev/null <<<"$safety_detail"; then
  jq '{code,message,status:.data.status,canReview:.data.canReview}' <<<"$safety_detail" >&2
  fail '安全员整改详情未返回可复查状态'
fi
close_response="$(curl -fsS -H "Authorization: Bearer $safety_token" \
  -H 'Content-Type: application/json' --data-binary '{"comment":"隔离验收复查通过"}' \
  "$API_BASE/inspection/rectifications/$rectification_id/close")"
if ! jq -e \
  '.code == 200 and .data.status == "CLOSED" and .data.canReview == false' \
  >/dev/null <<<"$close_response"; then
  jq '{code,message,status:.data.status,canReview:.data.canReview}' <<<"$close_response" >&2
  fail '安全员关闭整改后的状态或审核人不正确'
fi
info '安全员复查并关闭整改'

repeat_complete_status="$(http_status POST "/inspection/rectifications/$rectification_id/complete" \
  "$electrician_token" "$complete_payload")"
repeat_close_status="$(http_status POST "/inspection/rectifications/$rectification_id/close" \
  "$safety_token" '{"comment":"repeat"}')"
[ "$repeat_complete_status" = '409' ] && [ "$repeat_close_status" = '409' ]
record_detail="$(curl -fsS -H "Authorization: Bearer $electrician_token" \
  "$API_BASE/inspection/records/$record_id")"
jq -e '.code == 200 and .data.status == "CLOSED"' >/dev/null <<<"$record_detail"
log_count="$(mysql "${MYSQL_ARGS[@]}" -N "$TARGET_DB" -e \
  "SELECT COUNT(*) FROM inspection_rectification_review_log
    WHERE rectification_id=$rectification_id AND action_type IN ('COMPLETE','CLOSE');")"
db_reviewer_id="$(mysql "${MYSQL_ARGS[@]}" -N "$TARGET_DB" -e \
  "SELECT reviewer_id FROM inspection_rectification WHERE id=$rectification_id;")"
[ "$log_count" -eq 2 ] && [ "$db_reviewer_id" -eq "$safety_id" ]
info '重复状态流转返回 HTTP 409，完成与关闭审计日志完整'

printf '[PASS] 隔离数据库真实角色巡检整改闭环验收通过；源库未写入，临时资源将在退出时清理。\n'
