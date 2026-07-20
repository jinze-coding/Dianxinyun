#!/usr/bin/env bash

set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8080/api/v1}"
TMP_DIR="$(mktemp -d /tmp/dianxinyun-realistic-seed.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '[ERROR] 缺少命令：%s\n' "$1" >&2
    exit 1
  }
}

for command in curl jq cupsfilter textutil ffmpeg; do
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

login() {
  local username="$1"
  local response
  response="$(curl -sS -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"admin123\"}" \
    "$API_BASE/auth/login")"
  expect_success "$response" "账号 $username 登录"
  jq -r '.data.token' <<<"$response"
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

create_folder() {
  local token="$1"
  local project_id="$2"
  local folder_name="$3"
  local response
  response="$(post_json "$token" '/document-folders' \
    "$(jq -n --argjson projectId "$project_id" --arg folderName "$folder_name" \
      '{projectId:$projectId,parentId:0,folderName:$folderName}')" \
    "创建目录 $folder_name")"
  jq -r '.data.id' <<<"$response"
}

create_document() {
  local token="$1"
  local project_id="$2"
  local folder_id="$3"
  local title="$4"
  local category="$5"
  local document_no="$6"
  local remark="$7"
  local file_path="$8"
  local response
  response="$(curl -sS -H "Authorization: Bearer $token" \
    -F "file=@$file_path" -F "projectId=$project_id" -F "folderId=$folder_id" \
    -F "title=$title" -F "category=$category" -F "documentNo=$document_no" \
    -F "remark=$remark" -F 'changeNote=初始版本' \
    "$API_BASE/project-documents")"
  expect_success "$response" "上传资料 $title"
  jq -r '.data.document.id' <<<"$response"
}

upload_document_version() {
  local token="$1"
  local document_id="$2"
  local file_path="$3"
  local change_note="$4"
  local response
  response="$(curl -sS -H "Authorization: Bearer $token" \
    -F "file=@$file_path" -F "changeNote=$change_note" \
    "$API_BASE/project-documents/$document_id/versions")"
  expect_success "$response" "上传资料 V2"
}

upload_file() {
  local token="$1"
  local project_id="$2"
  local business_type="$3"
  local file_type="$4"
  local file_name="$5"
  local file_path="$6"
  local response
  response="$(curl -sS -H "Authorization: Bearer $token" \
    -F "file=@$file_path" -F "projectId=$project_id" \
    -F "businessType=$business_type" -F "fileType=$file_type" \
    -F "fileName=$file_name" "$API_BASE/files")"
  expect_success "$response" "上传附件 $file_name"
  jq -r '.data.id' <<<"$response"
}

create_photo() {
  local output="$1"
  local color="$2"
  ffmpeg -hide_banner -loglevel error -y -f lavfi \
    -i "color=c=$color:s=960x540:d=1" \
    -vf 'drawgrid=width=120:height=90:thickness=2:color=white@0.20,drawbox=x=120:y=90:w=720:h=360:color=white@0.12:t=fill' \
    -frames:v 1 "$output"
}

create_inspection() {
  local token="$1"
  local project_id="$2"
  local box_id="$3"
  local check_date="$4"
  local remark="$5"
  local items="$6"
  local outer_ids="${7:-[]}"
  local inner_ids="${8:-[]}"
  local payload
  payload="$(jq -n \
    --argjson projectId "$project_id" --argjson electricBoxId "$box_id" \
    --arg checkDate "$check_date" --arg remark "$remark" \
    --argjson items "$items" --argjson outerPhotoFileIds "$outer_ids" \
    --argjson innerPhotoFileIds "$inner_ids" \
    '{projectId:$projectId,electricBoxId:$electricBoxId,templateCode:"ELECTRIC_BOX_DAILY",source:"ELECTRICIAN_DAILY",checkDate:$checkDate,remark:$remark,outerPhotoFileIds:$outerPhotoFileIds,innerPhotoFileIds:$innerPhotoFileIds,items:$items}')"
  post_json "$token" '/inspection/records' "$payload" "提交电箱 $box_id 巡检" >/dev/null
}

create_quality_issue() {
  local token="$1"
  local project_id="$2"
  local title="$3"
  local location="$4"
  local description="$5"
  local severity="$6"
  local deadline="$7"
  local photo_id="$8"
  local response
  response="$(post_json "$token" '/quality/issues' \
    "$(jq -n --argjson projectId "$project_id" --arg title "$title" \
      --arg location "$location" --arg description "$description" \
      --arg severity "$severity" --arg deadline "$deadline" \
      --argjson photoId "$photo_id" \
      '{projectId:$projectId,title:$title,location:$location,description:$description,severity:$severity,assigneeId:4,deadline:$deadline,photoFileIds:[$photoId]}')" \
    "发起质量问题 $title")"
  jq -r '.data.id' <<<"$response"
}

printf '[INFO] 生成可预览测试文件\n'
cat >"$TMP_DIR/electric-plan-v1.txt" <<'EOF'
1号楼主体结构作业区临时用电巡检方案

1. 每日开工前检查配电箱外观、接地、漏电保护器和插座。
2. 巡检发现异常时立即停用相关回路，并通知项目电工处理。
3. 检查记录由现场巡检员当天提交，项目管理人员按月归档。
EOF
cat >"$TMP_DIR/electric-plan-v2.txt" <<'EOF'
1号楼主体结构作业区临时用电巡检方案（第二版）

1. 每日开工前检查配电箱外观、接地、漏电保护器和插座。
2. 增加塔吊专用配电箱雨后复检要求。
3. 巡检发现异常时立即停用相关回路，并通知项目电工处理。
4. 检查记录由现场巡检员当天提交，项目管理人员按月归档。
EOF
cupsfilter "$TMP_DIR/electric-plan-v1.txt" >"$TMP_DIR/1号楼临时用电巡检方案-V1.pdf" 2>/dev/null
cupsfilter "$TMP_DIR/electric-plan-v2.txt" >"$TMP_DIR/1号楼临时用电巡检方案-V2.pdf" 2>/dev/null

cat >"$TMP_DIR/mep-briefing.txt" <<'EOF'
地下室机电安装技术交底

作业范围：地下二层设备机房及管线综合区域。
控制要点：支吊架间距、桥架接地、洞口封堵、成品保护。
检查要求：施工班组自检、专业工程师复核、影像资料同步归档。
EOF
textutil -convert docx -output "$TMP_DIR/地下室机电安装技术交底.docx" \
  "$TMP_DIR/mep-briefing.txt" >/dev/null

cat >"$TMP_DIR/材料进场验收台账.csv" <<'EOF'
进场日期,材料名称,规格型号,批次,数量,验收结论
2026-07-16,三级钢筋,HRB400E-20,GC2026071601,32吨,合格
2026-07-17,盘扣式支架,B型,ZJ2026071701,860套,合格
2026-07-18,阻燃电缆,WDZ-YJY-5x16,DL2026071801,1200米,待复检
EOF

cat >"$TMP_DIR/周例会纪要.txt" <<'EOF'
施工协调周例会纪要
时间：2026-07-18 15:00
事项：塔吊作业协调、地下室材料转运、临电雨后复检、质量问题销项。
责任人：项目管理团队
EOF

create_photo "$TMP_DIR/配电箱外观照片.jpg" '#315f7d'
create_photo "$TMP_DIR/配电箱内部照片.jpg" '#536b42'
create_photo "$TMP_DIR/模板拼缝问题照片.jpg" '#8a5a44'
create_photo "$TMP_DIR/桥架支架问题照片.jpg" '#765f8d'
create_photo "$TMP_DIR/保护层问题照片.jpg" '#8a7040'
create_photo "$TMP_DIR/洞口尺寸问题照片.jpg" '#7d4b53'
create_photo "$TMP_DIR/整改完成照片.jpg" '#39715b'
create_photo "$TMP_DIR/复查照片.jpg" '#3f6687'

ADMIN_TOKEN="$(login admin)"
PROJECT_TOKEN="$(login project_admin)"
INSPECTOR_TOKEN="$(login inspector)"
QUALITY_TOKEN="$(login quality_manager)"
DOCUMENT_TOKEN="$(login document_manager)"

printf '[INFO] 创建工程资料目录和真实文件版本\n'
FOLDER_PLAN="$(create_folder "$ADMIN_TOKEN" 1 '施工方案')"
FOLDER_FORM="$(create_folder "$ADMIN_TOKEN" 1 '检查表格')"
FOLDER_MEETING="$(create_folder "$ADMIN_TOKEN" 1 '会议纪要')"
FOLDER_MEP="$(create_folder "$ADMIN_TOKEN" 2 '技术交底')"
FOLDER_MATERIAL="$(create_folder "$ADMIN_TOKEN" 3 '材料验收')"

PLAN_DOCUMENT_ID="$(create_document "$ADMIN_TOKEN" 1 "$FOLDER_PLAN" \
  '1号楼临时用电巡检方案' 'PROJECT_DATA' 'FA-LD-2026-001' \
  '适用于主体结构阶段临时用电日常检查' "$TMP_DIR/1号楼临时用电巡检方案-V1.pdf")"
upload_document_version "$ADMIN_TOKEN" "$PLAN_DOCUMENT_ID" \
  "$TMP_DIR/1号楼临时用电巡检方案-V2.pdf" '增加塔吊配电箱雨后复检要求'
MATERIAL_DOCUMENT_ID="$(create_document "$DOCUMENT_TOKEN" 1 "$FOLDER_FORM" '材料进场验收台账' 'FORM' \
  'BG-CL-2026-007' '记录本周主要材料进场验收结果' "$TMP_DIR/材料进场验收台账.csv")"
create_document "$DOCUMENT_TOKEN" 1 "$FOLDER_MEETING" '第16周施工协调会纪要' 'MEETING' \
  'HY-2026-016' '项目周例会决议及责任事项' "$TMP_DIR/周例会纪要.txt" >/dev/null
create_document "$ADMIN_TOKEN" 2 "$FOLDER_MEP" '地下室机电安装技术交底' 'PROJECT_DATA' \
  'JD-MEP-2026-003' '地下二层机房及管线综合安装交底' "$TMP_DIR/地下室机电安装技术交底.docx" >/dev/null
create_document "$DOCUMENT_TOKEN" 3 "$FOLDER_MATERIAL" '材料进场验收台账' 'FORM' \
  'BG-YD-2026-004' '场区材料进场验收记录' "$TMP_DIR/材料进场验收台账.csv" >/dev/null

# 生成不同用户的真实下载审计记录。
curl -fsS -H "Authorization: Bearer $PROJECT_TOKEN" \
  "$API_BASE/project-documents/$PLAN_DOCUMENT_ID/download" -o /dev/null
curl -fsS -H "Authorization: Bearer $DOCUMENT_TOKEN" \
  "$API_BASE/project-documents/$MATERIAL_DOCUMENT_ID/download" -o /dev/null

upload_file "$ADMIN_TOKEN" 1 'QUALITY_DOCUMENT' '质量检查标准' \
  '主体结构质量检查要点.pdf' "$TMP_DIR/1号楼临时用电巡检方案-V1.pdf" >/dev/null

printf '[INFO] 创建今日及历史巡检记录\n'
TODAY="$(date +%F)"
YESTERDAY="$(date -v-1d +%F)"
TWO_DAYS_AGO="$(date -v-2d +%F)"

NORMAL_ITEMS='[
  {"itemCode":"APPEARANCE","itemName":"内外观","result":"NORMAL","description":"箱体、防护棚及标识完好"},
  {"itemCode":"LEAKAGE_PROTECTOR","itemName":"漏电保护器","result":"NORMAL","description":"试跳动作正常"},
  {"itemCode":"FUSE","itemName":"熔断","result":"NORMAL","description":"熔断器规格匹配"},
  {"itemCode":"PROTECTIVE_ZERO","itemName":"保护接零","result":"NORMAL","description":"接零连接可靠"},
  {"itemCode":"SOCKET_220V","itemName":"220V插座","result":"NORMAL","description":"插座无破损"},
  {"itemCode":"SOCKET_380V","itemName":"380V插座","result":"NORMAL","description":"插座及防护盖完好"}
]'
ABNORMAL_ITEMS='[
  {"itemCode":"APPEARANCE","itemName":"内外观","result":"NORMAL","description":"箱体和防护棚完好"},
  {"itemCode":"LEAKAGE_PROTECTOR","itemName":"漏电保护器","result":"ABNORMAL","description":"测试按钮动作后复位不顺畅，已挂牌提醒"},
  {"itemCode":"FUSE","itemName":"熔断","result":"NORMAL","description":"熔断器规格匹配"},
  {"itemCode":"PROTECTIVE_ZERO","itemName":"保护接零","result":"NORMAL","description":"接零连接可靠"},
  {"itemCode":"SOCKET_220V","itemName":"220V插座","result":"NORMAL","description":"插座无破损"},
  {"itemCode":"SOCKET_380V","itemName":"380V插座","result":"NORMAL","description":"插座及防护盖完好"}
]'

OUTER_ID="$(upload_file "$INSPECTOR_TOKEN" 1 'inspection_record' 'INSPECTION_OUTER_PHOTO' \
  '配电箱外观照片.jpg' "$TMP_DIR/配电箱外观照片.jpg")"
INNER_ID="$(upload_file "$INSPECTOR_TOKEN" 1 'inspection_record' 'INSPECTION_INNER_PHOTO' \
  '配电箱内部照片.jpg' "$TMP_DIR/配电箱内部照片.jpg")"
create_inspection "$INSPECTOR_TOKEN" 1 1 "$TODAY" \
  '开工前检查完成，箱体、接地和保护装置正常。' "$NORMAL_ITEMS" "[$OUTER_ID]" "[$INNER_ID]"
create_inspection "$INSPECTOR_TOKEN" 1 2 "$TODAY" \
  '漏电保护器复位不顺畅，已通知电工班组当日处理。' "$ABNORMAL_ITEMS"
create_inspection "$INSPECTOR_TOKEN" 1 3 "$TODAY" \
  '地下室照明回路检查正常，通道无积水。' "$NORMAL_ITEMS"
create_inspection "$INSPECTOR_TOKEN" 1 4 "$YESTERDAY" \
  '塔吊专用配电箱检查正常，防雨措施有效。' "$NORMAL_ITEMS"
create_inspection "$INSPECTOR_TOKEN" 1 1 "$TWO_DAYS_AGO" \
  '日常巡检完成，无异常。' "$NORMAL_ITEMS"
create_inspection "$INSPECTOR_TOKEN" 2 5 "$TODAY" \
  '机房临时配电箱检查正常。' "$NORMAL_ITEMS"

printf '[INFO] 创建质量问题、整改和复查记录\n'
OVERDUE_DATE="$TODAY"
FUTURE_DATE="$(date -v+3d +%F)"

PHOTO_PENDING_A="$(upload_file "$ADMIN_TOKEN" 1 'QUALITY_PENDING' '质量问题照片' \
  '模板拼缝问题照片.jpg' "$TMP_DIR/模板拼缝问题照片.jpg")"
create_quality_issue "$ADMIN_TOKEN" 1 '1号楼西侧模板拼缝存在漏浆风险' \
  '1号楼六层西侧剪力墙' '模板拼缝局部大于控制值，浇筑前需重新封堵并复核。' \
  'WARNING' "$OVERDUE_DATE" "$PHOTO_PENDING_A" >/dev/null

PHOTO_PENDING_B="$(upload_file "$ADMIN_TOKEN" 1 'QUALITY_PENDING' '质量问题照片' \
  '保护层问题照片.jpg' "$TMP_DIR/保护层问题照片.jpg")"
create_quality_issue "$ADMIN_TOKEN" 1 '梁底钢筋保护层垫块间距不均' \
  '1号楼六层3-5轴梁板' '局部垫块间距偏大，钢筋班组应按方案补设并自检。' \
  'NORMAL' "$FUTURE_DATE" "$PHOTO_PENDING_B" >/dev/null

PHOTO_RECHECK="$(upload_file "$ADMIN_TOKEN" 2 'QUALITY_PENDING' '质量问题照片' \
  '桥架支架问题照片.jpg' "$TMP_DIR/桥架支架问题照片.jpg")"
RECHECK_ISSUE_ID="$(create_quality_issue "$ADMIN_TOKEN" 2 '地下室桥架支架间距偏大' \
  '地下二层制冷机房北侧' '两处桥架支架间距超过技术交底要求，需要增设支架。' \
  'WARNING' "$FUTURE_DATE" "$PHOTO_RECHECK")"
RECTIFY_PHOTO="$(upload_file "$QUALITY_TOKEN" 2 'QUALITY_RECTIFICATION_PENDING' '质量整改照片' \
  '整改完成照片.jpg' "$TMP_DIR/整改完成照片.jpg")"
post_json "$QUALITY_TOKEN" "/quality/issues/$RECHECK_ISSUE_ID/rectify" \
  "$(jq -n --argjson photoId "$RECTIFY_PHOTO" '{description:"已按交底要求增设两组支架，并完成紧固和防腐处理。",photoFileIds:[$photoId]}')" \
  '提交桥架整改' >/dev/null

PHOTO_CLOSED="$(upload_file "$ADMIN_TOKEN" 1 'QUALITY_PENDING' '质量问题照片' \
  '洞口尺寸问题照片.jpg' "$TMP_DIR/洞口尺寸问题照片.jpg")"
CLOSED_ISSUE_ID="$(create_quality_issue "$ADMIN_TOKEN" 1 '楼梯间预留洞口尺寸偏差' \
  '1号楼五层东楼梯间' '预留洞口宽度比图纸要求小20毫米，需修整后复测。' \
  'WARNING' "$FUTURE_DATE" "$PHOTO_CLOSED")"
FIRST_RECTIFY_PHOTO="$(upload_file "$QUALITY_TOKEN" 1 'QUALITY_RECTIFICATION_PENDING' '质量整改照片' \
  '整改完成照片.jpg' "$TMP_DIR/整改完成照片.jpg")"
post_json "$QUALITY_TOKEN" "/quality/issues/$CLOSED_ISSUE_ID/rectify" \
  "$(jq -n --argjson photoId "$FIRST_RECTIFY_PHOTO" '{description:"已完成洞口边缘修整。",photoFileIds:[$photoId]}')" \
  '提交首次洞口整改' >/dev/null
post_json "$ADMIN_TOKEN" "/quality/issues/$CLOSED_ISSUE_ID/review" \
  '{"passed":false,"comment":"现场复测仍差5毫米，请继续修整。","photoFileIds":[]}' \
  '退回洞口整改' >/dev/null
SECOND_RECTIFY_PHOTO="$(upload_file "$QUALITY_TOKEN" 1 'QUALITY_RECTIFICATION_PENDING' '质量整改照片' \
  '整改复测照片.jpg' "$TMP_DIR/复查照片.jpg")"
post_json "$QUALITY_TOKEN" "/quality/issues/$CLOSED_ISSUE_ID/rectify" \
  "$(jq -n --argjson photoId "$SECOND_RECTIFY_PHOTO" '{description:"已二次修整并复测，尺寸满足图纸要求。",photoFileIds:[$photoId]}')" \
  '提交二次洞口整改' >/dev/null
REVIEW_PHOTO="$(upload_file "$ADMIN_TOKEN" 1 'QUALITY_REVIEW_PENDING' '质量复查照片' \
  '质量复查照片.jpg' "$TMP_DIR/复查照片.jpg")"
post_json "$ADMIN_TOKEN" "/quality/issues/$CLOSED_ISSUE_ID/review" \
  "$(jq -n --argjson photoId "$REVIEW_PHOTO" '{passed:true,comment:"复测尺寸符合图纸要求，同意关闭。",photoFileIds:[$photoId]}')" \
  '关闭洞口质量问题' >/dev/null

printf '[SUCCESS] 拟真业务测试数据已通过真实接口生成\n'
