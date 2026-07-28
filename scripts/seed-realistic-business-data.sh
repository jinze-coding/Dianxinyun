#!/usr/bin/env bash

set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8080/api/v1}"
: "${SEED_ADMIN_USERNAME:?请设置 SEED_ADMIN_USERNAME（已显式重置密码的平台管理员账号）}"
: "${SEED_ADMIN_PASSWORD:?请设置 SEED_ADMIN_PASSWORD（仅用于本次种子数据调用）}"
SEED_TMP_DIR="$(mktemp -d /tmp/dianxinyun-single-demo-seed.XXXXXX)"
trap 'rm -rf "$SEED_TMP_DIR"' EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '[ERROR] 缺少命令：%s\n' "$1" >&2
    exit 1
  }
}

for command in curl jq cupsfilter base64; do
  require_command "$command"
done

expect_success() {
  local response="$1"
  local label="$2"
  if ! jq -e '.code == 200' >/dev/null 2>&1 <<<"$response"; then
    printf '[ERROR] %s失败：%s\n' "$label" "$(jq -r '.message // .' <<<"$response")" >&2
    exit 1
  fi
}

post_json() {
  local token="$1"
  local path="$2"
  local payload="$3"
  local label="$4"
  local response
  response="$(curl -sS -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -d "$payload" "$API_BASE$path")"
  expect_success "$response" "$label"
  printf '%s' "$response"
}

upload_file() {
  local token="$1"
  local business_type="$2"
  local file_type="$3"
  local file_name="$4"
  local file_path="$5"
  local response
  response="$(curl -sS -H "Authorization: Bearer $token" \
    -F "file=@$file_path" -F 'projectId=1' \
    -F "businessType=$business_type" -F "fileType=$file_type" \
    -F "fileName=$file_name" "$API_BASE/files")"
  expect_success "$response" "上传附件 $file_name"
  jq -r '.data.id' <<<"$response"
}

create_photo() {
  local output="$1"
  printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAEAAAAAwCAIAAAAuKetIAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAMElEQVR4nO3BAQ0AAADCoPdPbQ43oAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAfg0wAAABHsM8VQAAAABJRU5ErkJggg==' \
    | base64 -D >"$output"
}

printf '[INFO] 登录唯一演示管理员\n'
LOGIN_PAYLOAD="$(jq -n \
  --arg username "$SEED_ADMIN_USERNAME" \
  --arg password "$SEED_ADMIN_PASSWORD" \
  '{username: $username, password: $password}')"
LOGIN_RESPONSE="$(curl -sS -H 'Content-Type: application/json' \
  --data-binary @- "$API_BASE/auth/login" <<<"$LOGIN_PAYLOAD")"
expect_success "$LOGIN_RESPONSE" '管理员登录'
ADMIN_TOKEN="$(jq -r '.data.token' <<<"$LOGIN_RESPONSE")"

printf '[INFO] 生成一份演示资料和三张业务附件\n'
cat >"$SEED_TMP_DIR/demo-plan.txt" <<'EOF'
智慧工地综合演示项目现场管理方案

1. 每日开工前检查演示配电箱外观、接地及漏电保护器。
2. 施工资料、巡检记录和质量问题统一通过系统真实接口留痕。
3. 本文档及相关人员、设备、附件均为虚构演示数据。
EOF
cupsfilter "$SEED_TMP_DIR/demo-plan.txt" \
  >"$SEED_TMP_DIR/智慧工地综合演示方案.pdf" 2>/dev/null

create_photo "$SEED_TMP_DIR/演示电箱外观.png"
create_photo "$SEED_TMP_DIR/演示电箱内部.png"
create_photo "$SEED_TMP_DIR/演示质量问题.png"

printf '[INFO] 创建一份真实工程资料\n'
FOLDER_RESPONSE="$(post_json "$ADMIN_TOKEN" '/document-folders' \
  '{"projectId":1,"parentId":0,"folderName":"演示资料"}' '创建演示资料目录')"
FOLDER_ID="$(jq -r '.data.id' <<<"$FOLDER_RESPONSE")"

DOCUMENT_RESPONSE="$(curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "file=@$SEED_TMP_DIR/智慧工地综合演示方案.pdf" \
  -F 'projectId=1' -F "folderId=$FOLDER_ID" \
  -F 'title=智慧工地综合演示方案' -F 'category=PROJECT_DATA' \
  -F 'documentNo=DEMO-DOC-001' -F 'remark=唯一演示工程资料' \
  -F 'changeNote=初始演示版本' "$API_BASE/project-documents")"
expect_success "$DOCUMENT_RESPONSE" '上传演示工程资料'

printf '[INFO] 创建一条当日真实巡检记录\n'
OUTER_FILE_ID="$(upload_file "$ADMIN_TOKEN" 'inspection_record' \
  'INSPECTION_OUTER_PHOTO' '演示电箱外观.png' "$SEED_TMP_DIR/演示电箱外观.png")"
INNER_FILE_ID="$(upload_file "$ADMIN_TOKEN" 'inspection_record' \
  'INSPECTION_INNER_PHOTO' '演示电箱内部.png' "$SEED_TMP_DIR/演示电箱内部.png")"

CHECK_ITEMS='[
  {"itemCode":"APPEARANCE","itemName":"内外观","result":"NORMAL","description":"箱体和标识完好"},
  {"itemCode":"LEAKAGE_PROTECTOR","itemName":"漏电保护器","result":"NORMAL","description":"试跳动作正常"},
  {"itemCode":"FUSE","itemName":"熔断","result":"NORMAL","description":"熔断器规格匹配"},
  {"itemCode":"PROTECTIVE_ZERO","itemName":"保护接零","result":"NORMAL","description":"接零连接可靠"},
  {"itemCode":"SOCKET_220V","itemName":"220V插座","result":"NORMAL","description":"插座无破损"},
  {"itemCode":"SOCKET_380V","itemName":"380V插座","result":"NORMAL","description":"插座和防护盖完好"}
]'
CHECK_DATE="$(date +%F)"
INSPECTION_PAYLOAD="$(jq -n \
  --arg checkDate "$CHECK_DATE" \
  --argjson outerId "$OUTER_FILE_ID" \
  --argjson innerId "$INNER_FILE_ID" \
  --argjson items "$CHECK_ITEMS" \
  '{
    projectId:1,
    electricBoxId:1,
    templateCode:"ELECTRIC_BOX_DAILY",
    source:"ELECTRICIAN_DAILY",
    checkDate:$checkDate,
    remark:"唯一演示巡检记录，六项检查均正常。",
    outerPhotoFileIds:[$outerId],
    innerPhotoFileIds:[$innerId],
    items:$items
  }')"
post_json "$ADMIN_TOKEN" '/inspection/records' "$INSPECTION_PAYLOAD" \
  '提交演示巡检记录' >/dev/null

printf '[INFO] 创建一个真实质量问题\n'
QUALITY_FILE_ID="$(upload_file "$ADMIN_TOKEN" 'QUALITY_PENDING' \
  '质量问题照片' '演示质量问题.png' "$SEED_TMP_DIR/演示质量问题.png")"
QUALITY_DEADLINE="$(date -v+3d +%F)"
QUALITY_PAYLOAD="$(jq -n \
  --arg deadline "$QUALITY_DEADLINE" \
  --argjson photoId "$QUALITY_FILE_ID" \
  '{
    projectId:1,
    title:"演示区域临边防护标识需补充",
    location:"主体楼一层东侧演示区",
    description:"现场演示点位缺少一处醒目标识，请在期限内补充。",
    severity:"NORMAL",
    assigneeId:1,
    deadline:$deadline,
    photoFileIds:[$photoId]
  }')"
post_json "$ADMIN_TOKEN" '/quality/issues' "$QUALITY_PAYLOAD" \
  '创建演示质量问题' >/dev/null

printf '[SUCCESS] 单项目演示业务数据已通过真实接口生成\n'
