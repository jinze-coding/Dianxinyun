#!/usr/bin/env bash

set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]:-$0}"
ROOT_DIR="$(cd "$(dirname "$SCRIPT_PATH")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
UPLOAD_ROOT="${DIANXINYUN_UPLOAD_ROOT:-$BACKEND_DIR/uploads}"
DB_HOST="${DIANXINYUN_DB_HOST:-127.0.0.1}"
DB_PORT="${DIANXINYUN_DB_PORT:-3306}"
DB_NAME="${DIANXINYUN_DB_NAME:-dianxinyun}"
DB_USER="${DIANXINYUN_DB_USER:-root}"
DB_PASSWORD="${DIANXINYUN_DB_PASSWORD:-}"
PROJECT_ID=3
EXPECTED_PROJECT_NAME="北蔡镇中界杨桥城中村C10-01地块配套小学新建工程项目"
DEMO_MARKER="[EDGE_DEMO_20260828]"
DEMO_POINT_CODES_SQL="'EDGE-DEMO-01','EDGE-DEMO-02','EDGE-DEMO-03','EDGE-DEMO-04','EDGE-DEMO-05','EDGE-DEMO-06','EDGE-DEMO-07','EDGE-DEMO-08'"
EXPECTED_POINT_COUNT=8
EXPECTED_TASK_COUNT=56
MODE="${1:-preflight}"

fail() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }
info() { printf '[INFO] %s\n' "$*"; }

case "$MODE" in
  preflight|apply|cleanup) ;;
  *) fail "用法：$0 preflight|apply|cleanup" ;;
esac

[ "$DB_HOST" = "127.0.0.1" ] || [ "$DB_HOST" = "localhost" ] \
  || fail "演示种子仅允许连接本机 MySQL"
[ "$DB_NAME" = "dianxinyun" ] || fail "演示种子仅允许数据库 dianxinyun"
command -v mysql >/dev/null 2>&1 || fail "缺少 mysql 客户端"
command -v java >/dev/null 2>&1 || fail "缺少 Java 17 运行时"

MYSQL_ARGS=(--batch --skip-column-names -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME")
mysql_exec() {
  if [ -n "$DB_PASSWORD" ]; then
    MYSQL_PWD="$DB_PASSWORD" mysql "${MYSQL_ARGS[@]}" "$@"
  else
    mysql "${MYSQL_ARGS[@]}" "$@"
  fi
}

project_name="$(mysql_exec -e "SELECT project_name FROM project_info WHERE id=$PROJECT_ID AND deleted=0 LIMIT 1")"
[ "$project_name" = "$EXPECTED_PROJECT_NAME" ] \
  || fail "项目校验失败：ID 3 不是预期的北蔡小学项目"
table_ready="$(mysql_exec -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='general_inspection_export_job_task'")"
[ "$table_ready" = "1" ] || fail "请先执行 20260828_edge_inspection_export.sql"
zhang_id="$(mysql_exec -e "SELECT id FROM sys_user WHERE real_name='张勇' AND status=1 AND deleted=0 ORDER BY id LIMIT 1")"
deng_id="$(mysql_exec -e "SELECT id FROM sys_user WHERE real_name='邓帅' AND status=1 AND deleted=0 ORDER BY id LIMIT 1")"
[ -n "$zhang_id" ] || fail "未找到有效用户：张勇"
[ -n "$deng_id" ] || fail "未找到有效用户：邓帅"
member_count="$(mysql_exec -e "SELECT COUNT(*) FROM sys_user_project WHERE project_id=$PROJECT_ID AND user_id IN ($zhang_id,$deng_id) AND status='ACTIVE'")"
[ "$member_count" = "2" ] || fail "张勇和邓帅必须都是北蔡小学项目的有效成员"
template_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_template WHERE scope_type='SYSTEM' AND template_code LIKE 'EDGE_%' AND template_code<>'EDGE_REFERENCE' AND status='PUBLISHED' AND deleted=0")"
[ "$template_count" = "8" ] || fail "固定式临边巡检 8 套系统模板不完整"
edge_permission_count="$(mysql_exec -e "SELECT COUNT(*) FROM sys_permission WHERE permission_code IN ('EDGE_INSPECTION_VIEW','EDGE_INSPECTION_MANAGE','EDGE_INSPECTION_SUBMIT','EDGE_INSPECTION_RECTIFY','EDGE_INSPECTION_REVIEW','EDGE_INSPECTION_EXPORT') AND enabled=1 AND deleted=0")"
[ "$edge_permission_count" = "6" ] || fail "固定式临边巡检 6 项操作权限不完整，请先执行权限分离迁移"
edge_menu_count="$(mysql_exec -e "SELECT COUNT(*) FROM sys_menu WHERE menu_code IN ('WEB_INSPECTION','MINI_INSPECTION','INSPECTION_EDGE') AND visible=1 AND enabled=1 AND deleted=0")"
[ "$edge_menu_count" = "3" ] || fail "临边巡检跨端根菜单或 Web 子页面不完整，请先执行授权一致性迁移"

existing_points="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_point WHERE project_id=$PROJECT_ID AND point_code IN ($DEMO_POINT_CODES_SQL) AND deleted=0")"
existing_tasks="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE project_id=$PROJECT_ID AND LEFT(plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER'")"
info "预检通过：项目=${project_name}，张勇ID=${zhang_id}，邓帅ID=${deng_id}，现有演示点位=${existing_points}，演示任务=${existing_tasks}"
[ "$MODE" != "preflight" ] || exit 0

[ "${DIANXINYUN_DEMO_CONFIRM:-}" = "LOCAL_PROJECT_3" ] \
  || fail "apply/cleanup 必须显式设置 DIANXINYUN_DEMO_CONFIRM=LOCAL_PROJECT_3"

DEMO_DIR="$UPLOAD_ROOT/edge-inspection/demo/20260828"
mkdir -p "$DEMO_DIR"

remove_export_files() {
  while IFS=$'\t' read -r provider key; do
    [ -n "${key:-}" ] || continue
    [ "${provider:-LOCAL}" = "LOCAL" ] || continue
    case "$key" in
      edge-inspection/exports/3/*)
        target="$UPLOAD_ROOT/$key"
        case "$target" in "$UPLOAD_ROOT"/*) rm -f "$target" ;; esac
        ;;
    esac
  done < <(mysql_exec -e "
    SELECT COALESCE(fr.storage_provider,'LOCAL'), COALESCE(fr.storage_key,fr.file_path)
    FROM file_resource fr
    JOIN general_inspection_export_job ej ON ej.file_resource_id=fr.id
    WHERE ej.id IN (
      SELECT DISTINCT snapshot.job_id
      FROM general_inspection_export_job_task snapshot
      JOIN general_inspection_task task ON task.id=snapshot.task_id
      WHERE task.project_id=$PROJECT_ID AND LEFT(task.plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER'
    ) AND fr.deleted=0")
}

cleanup_database() {
  remove_export_files
  mysql_exec <<SQL
START TRANSACTION;
CREATE TEMPORARY TABLE tmp_edge_demo_point_ids (id BIGINT PRIMARY KEY);
INSERT INTO tmp_edge_demo_point_ids
SELECT id FROM general_inspection_point
WHERE project_id=$PROJECT_ID AND point_code IN ($DEMO_POINT_CODES_SQL);
CREATE TEMPORARY TABLE tmp_edge_demo_task_ids (id BIGINT PRIMARY KEY);
INSERT INTO tmp_edge_demo_task_ids
SELECT id FROM general_inspection_task
WHERE project_id=$PROJECT_ID AND LEFT(plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER';
CREATE TEMPORARY TABLE tmp_edge_demo_rectification_ids (id BIGINT PRIMARY KEY);
INSERT INTO tmp_edge_demo_rectification_ids
SELECT id FROM general_inspection_rectification
WHERE task_id IN (SELECT id FROM tmp_edge_demo_task_ids);
CREATE TEMPORARY TABLE tmp_edge_demo_plan_ids (id BIGINT PRIMARY KEY);
INSERT INTO tmp_edge_demo_plan_ids
SELECT id FROM general_inspection_plan
WHERE project_id=$PROJECT_ID AND plan_code='EDGE_PROJECT_SCHEDULE'
  AND LEFT(plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER';
CREATE TEMPORARY TABLE tmp_edge_demo_export_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO tmp_edge_demo_export_ids
SELECT DISTINCT job_id FROM general_inspection_export_job_task
WHERE task_id IN (SELECT id FROM tmp_edge_demo_task_ids);
DELETE FROM user_notification WHERE business_type='EDGE_INSPECTION_EXPORT'
  AND business_id IN (SELECT id FROM tmp_edge_demo_export_ids);
DELETE FROM file_resource WHERE business_type='EDGE_INSPECTION_EXPORT'
  AND business_id IN (SELECT id FROM tmp_edge_demo_export_ids);
DELETE FROM general_inspection_export_job_task WHERE job_id IN (SELECT id FROM tmp_edge_demo_export_ids);
DELETE FROM general_inspection_export_job WHERE id IN (SELECT id FROM tmp_edge_demo_export_ids);
DELETE FROM file_resource WHERE project_id=$PROJECT_ID
  AND LEFT(remark,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER';
DELETE FROM user_notification WHERE project_id=$PROJECT_ID AND (
  (business_type IN ('EDGE_INSPECTION_TASK','EDGE_INSPECTION_RECTIFICATION','EDGE_INSPECTION_REVIEW')
    AND business_id IN (SELECT id FROM tmp_edge_demo_task_ids))
);
DELETE FROM general_inspection_event_outbox WHERE project_id=$PROJECT_ID AND (
  (business_type IN ('TASK','RECTIFICATION_SHEET') AND business_id IN (SELECT id FROM tmp_edge_demo_task_ids))
  OR (business_type='RECTIFICATION' AND business_id IN (SELECT id FROM tmp_edge_demo_rectification_ids))
);
DELETE FROM general_inspection_action_log WHERE project_id=$PROJECT_ID AND (
  LEFT(comment,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER'
  OR (business_type IN ('TASK','RECTIFICATION_SHEET') AND business_id IN (SELECT id FROM tmp_edge_demo_task_ids))
  OR (business_type='RECTIFICATION' AND business_id IN (SELECT id FROM tmp_edge_demo_rectification_ids))
  OR (business_type='PLAN' AND business_id IN (SELECT id FROM tmp_edge_demo_plan_ids))
  OR (business_type='POINT' AND business_id IN (SELECT id FROM tmp_edge_demo_point_ids))
);
DELETE FROM general_inspection_rectification WHERE task_id IN (SELECT id FROM tmp_edge_demo_task_ids);
DELETE FROM general_inspection_task_item WHERE task_id IN (SELECT id FROM tmp_edge_demo_task_ids);
DELETE FROM general_inspection_task WHERE id IN (SELECT id FROM tmp_edge_demo_task_ids);
DELETE FROM general_inspection_plan_version WHERE plan_id IN (SELECT id FROM tmp_edge_demo_plan_ids);
DELETE FROM general_inspection_plan WHERE id IN (SELECT id FROM tmp_edge_demo_plan_ids);
DELETE FROM general_inspection_point WHERE project_id=$PROJECT_ID
  AND point_code IN ($DEMO_POINT_CODES_SQL);
COMMIT;
SQL
  rm -f "$DEMO_DIR/overview.png" "$DEMO_DIR/evidence.png" "$DEMO_DIR/rectification.png"
}

if [ "$MODE" = "cleanup" ]; then
  cleanup_database
  info "已仅清理 $DEMO_MARKER 及固定编码 EDGE-DEMO-01..08 的演示点位、计划、任务、整改、附件、关联导出和物理测试图片"
  exit 0
fi

non_demo_plan="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_plan WHERE project_id=$PROJECT_ID AND plan_code='EDGE_PROJECT_SCHEDULE' AND deleted=0 AND COALESCE(LEFT(plan_name,CHAR_LENGTH('$DEMO_MARKER')),'')<>'$DEMO_MARKER'")"
[ "$non_demo_plan" = "0" ] || fail "项目 3 已存在非演示临边巡检设置，拒绝覆盖"

cleanup_database

make_demo_image() {
  local label="$1"
  local color="$2"
  local output="$3"
  local temp_dir
  local source_file
  temp_dir="$(mktemp -d -t edge-demo-image)"
  source_file="$temp_dir/EdgeDemoImage.java"
  printf '%s\n' \
    'import java.awt.*;' \
    'import java.awt.image.BufferedImage;' \
    'import java.io.File;' \
    'import javax.imageio.ImageIO;' \
    'class EdgeDemoImage {' \
    '  public static void main(String[] args) throws Exception {' \
    '    BufferedImage image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);' \
    '    Graphics2D g = image.createGraphics();' \
    '    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);' \
    '    g.setColor(Color.decode(args[1])); g.fillRect(0, 0, 960, 540);' \
    '    g.setColor(Color.WHITE); g.setStroke(new BasicStroke(6)); g.drawRoundRect(30, 30, 900, 480, 26, 26);' \
    '    g.setFont(new Font("PingFang SC", Font.BOLD, 70)); drawCentered(g, "演示数据", 235);' \
    '    g.setFont(new Font("Arial", Font.BOLD, 46)); drawCentered(g, "DEMO DATA", 310);' \
    '    g.setFont(new Font("Arial", Font.PLAIN, 30)); drawCentered(g, args[0], 380);' \
    '    g.dispose(); ImageIO.write(image, "png", new File(args[2]));' \
    '  }' \
    '  static void drawCentered(Graphics2D g, String text, int y) {' \
    '    FontMetrics metrics = g.getFontMetrics(); g.drawString(text, (960 - metrics.stringWidth(text)) / 2, y);' \
    '  }' \
    '}' > "$source_file"
  java "$source_file" "$label" "$color" "$output"
  rm -f "$source_file"
  rmdir "$temp_dir"
}

make_demo_image "EDGE INSPECTION OVERVIEW" "#2563eb" "$DEMO_DIR/overview.png"
make_demo_image "ABNORMAL EVIDENCE" "#dc2626" "$DEMO_DIR/evidence.png"
make_demo_image "RECTIFICATION RESULT" "#16a34a" "$DEMO_DIR/rectification.png"

overview_size="$(stat -f%z "$DEMO_DIR/overview.png")"
evidence_size="$(stat -f%z "$DEMO_DIR/evidence.png")"
rectification_size="$(stat -f%z "$DEMO_DIR/rectification.png")"
overview_sha="$(shasum -a 256 "$DEMO_DIR/overview.png" | awk '{print $1}')"
evidence_sha="$(shasum -a 256 "$DEMO_DIR/evidence.png" | awk '{print $1}')"
rectification_sha="$(shasum -a 256 "$DEMO_DIR/rectification.png" | awk '{print $1}')"

mysql_exec <<SQL
START TRANSACTION;
SET @project_id := $PROJECT_ID;
SET @inspector_id := $zhang_id;
SET @reviewer_id := $deng_id;
SET @inspector_name := '张勇';
SET @reviewer_name := '邓帅';
SET @marker := '$DEMO_MARKER';
SET @operator_id := (SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id
  JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='PLATFORM_ADMIN' AND r.scope_type='PLATFORM'
  AND r.enabled=1 AND r.deleted=0 AND u.status=1 AND u.deleted=0 ORDER BY u.id LIMIT 1);
SET @operator_name := (SELECT COALESCE(NULLIF(real_name,''),username) FROM sys_user WHERE id=@operator_id);

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role JOIN sys_permission permission
WHERE role.role_code='ELECTRICIAN' AND role.scope_type='PROJECT' AND role.deleted=0
  AND permission.permission_code IN ('EDGE_INSPECTION_VIEW','EDGE_INSPECTION_SUBMIT','EDGE_INSPECTION_RECTIFY')
  AND permission.enabled=1 AND permission.deleted=0;
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role JOIN sys_permission permission
WHERE role.role_code='SAFETY_OFFICER' AND role.scope_type='PROJECT' AND role.deleted=0
  AND permission.permission_code IN ('EDGE_INSPECTION_VIEW','EDGE_INSPECTION_MANAGE',
                                     'EDGE_INSPECTION_REVIEW','EDGE_INSPECTION_EXPORT')
  AND permission.enabled=1 AND permission.deleted=0;

-- 演示脚本只补临边专属操作权限，不写入、不删除任何电箱权限。补齐临边权限时同步正式
-- INSPECTION 模块、Web/小程序根菜单和唯一临边子页面，避免“接口有权限但页面无入口”。
INSERT IGNORE INTO sys_role_business_module(role_id,module_code)
SELECT DISTINCT role.id,'INSPECTION'
FROM sys_role role
INNER JOIN sys_role_permission role_permission ON role_permission.role_id=role.id
INNER JOIN sys_permission permission ON permission.id=role_permission.permission_id
WHERE role.role_code IN ('ELECTRICIAN','SAFETY_OFFICER')
  AND role.scope_type='PROJECT' AND role.enabled=1 AND role.deleted=0
  AND permission.permission_code='EDGE_INSPECTION_VIEW'
  AND permission.enabled=1 AND permission.deleted=0;

INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT role.id,menu.id
FROM sys_role role
INNER JOIN sys_role_permission role_permission ON role_permission.role_id=role.id
INNER JOIN sys_permission permission ON permission.id=role_permission.permission_id
INNER JOIN sys_menu menu
  ON menu.menu_code IN ('WEB_INSPECTION','MINI_INSPECTION','INSPECTION_EDGE')
  AND menu.visible=1 AND menu.enabled=1 AND menu.deleted=0
WHERE role.role_code IN ('ELECTRICIAN','SAFETY_OFFICER')
  AND role.scope_type='PROJECT' AND role.enabled=1 AND role.deleted=0
  AND permission.permission_code='EDGE_INSPECTION_VIEW'
  AND permission.enabled=1 AND permission.deleted=0;

INSERT INTO general_inspection_point(
  project_id,point_code,point_name,point_type_code,point_type_name,edge_active_since_time,
  category_id,category_name,building_name,floor_name,location_desc,qr_enabled,qr_version,
  public_access_enabled,status,version,created_by_id,created_by_name,updated_by_id,updated_by_name,deleted)
SELECT @project_id, seed.point_code, seed.point_name, seed.type_code, category.category_name,
  CONCAT(DATE_SUB(CURDATE(),INTERVAL 6 DAY),' 00:00:00'), category.id, category.category_name,
  seed.building_name,seed.floor_name,seed.location_desc,0,0,0,'ACTIVE',0,@operator_id,@operator_name,@operator_id,@operator_name,0
FROM (
  SELECT 'EDGE-DEMO-01' point_code,'1号楼东侧临边' point_name,'FLOOR_BALCONY_EAVE_EDGE' type_code,'1号楼' building_name,'3层' floor_name,'东侧走廊尽端' location_desc UNION ALL
  SELECT 'EDGE-DEMO-02','1号楼主楼梯平台','STAIR_PLATFORM_FLIGHT_EDGE','1号楼','2层至3层','主楼梯中间平台' UNION ALL
  SELECT 'EDGE-DEMO-03','教学楼屋面南侧','ROOF_EDGE','教学楼','屋面层','南侧设备区通道' UNION ALL
  SELECT 'EDGE-DEMO-04','地下车库基坑北侧','PIT_TRENCH_EDGE','地下车库','基坑层','北侧施工便道旁' UNION ALL
  SELECT 'EDGE-DEMO-05','2号楼预留洞口','OPENING_RESERVED_HOLE','2号楼','4层','核心筒西侧预留洞' UNION ALL
  SELECT 'EDGE-DEMO-06','1号电梯井口','ELEVATOR_SHAFT','1号楼','5层','1号电梯井道入口' UNION ALL
  SELECT 'EDGE-DEMO-07','施工升降机5层平台','HOIST_LANDING_PLATFORM','1号楼','5层','施工升降机停层平台' UNION ALL
  SELECT 'EDGE-DEMO-08','北侧接料平台','LOADING_UNLOADING_PLATFORM','2号楼','3层','北立面接料平台'
) seed JOIN general_inspection_point_category category
  ON category.project_id IS NULL AND category.category_code=seed.type_code
  AND category.builtin=1 AND category.enabled=1;

SET @routing_template_id := (SELECT id FROM general_inspection_template
  WHERE template_code='EDGE_FLOOR_BALCONY_EAVE_EDGE' AND scope_type='SYSTEM' AND deleted=0 LIMIT 1);
SET @config := JSON_OBJECT(
  'frequency','DAILY','weekdays',JSON_ARRAY(),'monthDays',JSON_ARRAY(),
  'effectiveStart',DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 6 DAY),'%Y-%m-%d'),'earlyMinutes',0,
  'assigneeId',@inspector_id,'backupAssigneeIds',JSON_ARRAY(),
  'defaultRectifierId',@inspector_id,'rectificationDays',3,
  'reviewerId',@reviewer_id,'backupReviewerIds',JSON_ARRAY(),
  'slots',JSON_ARRAY(JSON_OBJECT('slotCode','EDGE_SLOT','slotName','临边巡检时段',
    'startTime','08:00:00','dueTime','18:00:00','dueDayOffset',0)),
  'points',(SELECT JSON_ARRAYAGG(JSON_OBJECT('pointId',id)) FROM general_inspection_point
    WHERE project_id=@project_id AND point_code IN ($DEMO_POINT_CODES_SQL) AND deleted=0)
);
INSERT INTO general_inspection_plan(project_id,template_id,plan_code,plan_name,status,draft_config_json,
  generated_through_time,edge_generation_lower_bound_time,version,created_by_id,created_by_name,
  updated_by_id,updated_by_name,deleted)
VALUES(@project_id,@routing_template_id,'EDGE_PROJECT_SCHEDULE',CONCAT(@marker,' 北蔡临边巡检设置'),
  'PUBLISHED',@config,NOW(),CONCAT(DATE_SUB(CURDATE(),INTERVAL 6 DAY),' 00:00:00'),
  0,@operator_id,@operator_name,@operator_id,@operator_name,0);
SET @plan_id := LAST_INSERT_ID();
INSERT INTO general_inspection_plan_version(plan_id,version_no,effective_time,config_json,published_by_id,published_by_name)
VALUES(@plan_id,1,CONCAT(DATE_SUB(CURDATE(),INTERVAL 6 DAY),' 00:00:00'),@config,@operator_id,@operator_name);
SET @plan_version_id := LAST_INSERT_ID();
UPDATE general_inspection_plan SET current_version_id=@plan_version_id WHERE id=@plan_id;

INSERT INTO general_inspection_task(
  project_id,plan_id,plan_version_id,template_id,template_version_id,point_id,revision_no,
  plan_name,template_name,point_code,point_name,point_type_code,point_type_name,
  building_name,floor_name,location_desc,slot_code,slot_name,occurrence_date,available_time,start_time,due_time,
  assignee_id,assignee_name,default_rectifier_id,default_rectifier_name,default_rectification_days,
  reviewer_id,reviewer_name,qr_required,qr_version,status,submitted_by_id,submitted_by_name,submitted_time,on_time,
  overall_photo_min,overall_photo_max,overall_remark_required,remark,abnormal_count,
  cancel_reason,cancelled_by_id,cancelled_by_name,cancelled_time,version,create_time,update_time)
WITH RECURSIVE demo_dates AS (
  SELECT DATE_SUB(CURDATE(),INTERVAL 6 DAY) AS day_value
  UNION ALL SELECT DATE_ADD(day_value,INTERVAL 1 DAY) FROM demo_dates WHERE day_value<CURDATE()
), demo_candidates AS (
  SELECT dates.day_value,p.*,CAST(RIGHT(p.point_code,2) AS UNSIGNED) point_no,
    DATEDIFF(CURDATE(),dates.day_value) day_offset
  FROM demo_dates dates CROSS JOIN general_inspection_point p
  WHERE p.project_id=@project_id AND p.point_code IN ($DEMO_POINT_CODES_SQL) AND p.deleted=0
), demo_tasks AS (
  SELECT candidate.*,
    CASE
      WHEN candidate.day_offset=0 THEN 'TODAY_PENDING'
      WHEN candidate.day_offset=1 AND candidate.point_no IN (1,2) THEN 'MISSED'
      WHEN candidate.day_offset=2 AND candidate.point_no IN (1,2) THEN 'LATE'
      WHEN candidate.day_offset=3 AND candidate.point_no=1 THEN 'CANCELLED'
      WHEN candidate.day_offset=4 AND candidate.point_no=1 THEN 'RECTIFICATION_PENDING'
      WHEN candidate.day_offset=5 AND candidate.point_no=1 THEN 'RECTIFICATION_REVIEW'
      WHEN candidate.day_offset=6 AND candidate.point_no=1 THEN 'RECTIFICATION_CLOSED'
      ELSE 'ONTIME'
    END demo_state
  FROM demo_candidates candidate
)
SELECT @project_id,@plan_id,@plan_version_id,template.id,template.current_version_id,demo.id,0,
  CONCAT(@marker,' 北蔡临边巡检设置'),template.template_name,demo.point_code,demo.point_name,
  demo.point_type_code,demo.point_type_name,demo.building_name,demo.floor_name,demo.location_desc,
  'EDGE_SLOT','临边巡检时段',demo.day_value,CONCAT(demo.day_value,' 08:00:00'),
  CONCAT(demo.day_value,' 08:00:00'),CONCAT(demo.day_value,' 18:00:00'),
  @inspector_id,@inspector_name,@inspector_id,@inspector_name,3,@reviewer_id,@reviewer_name,0,0,
  CASE demo.demo_state WHEN 'CANCELLED' THEN 'CANCELLED'
    WHEN 'TODAY_PENDING' THEN 'PENDING' WHEN 'MISSED' THEN 'PENDING'
    WHEN 'RECTIFICATION_PENDING' THEN 'RECTIFICATION_PENDING'
    WHEN 'RECTIFICATION_REVIEW' THEN 'RECTIFICATION_PENDING'
    WHEN 'RECTIFICATION_CLOSED' THEN 'CLOSED' ELSE 'COMPLETED' END,
  CASE WHEN demo.demo_state IN ('ONTIME','LATE','RECTIFICATION_PENDING','RECTIFICATION_REVIEW','RECTIFICATION_CLOSED') THEN @inspector_id END,
  CASE WHEN demo.demo_state IN ('ONTIME','LATE','RECTIFICATION_PENDING','RECTIFICATION_REVIEW','RECTIFICATION_CLOSED') THEN @inspector_name END,
  CASE WHEN demo.demo_state='ONTIME' THEN CONCAT(demo.day_value,' 10:00:00')
    WHEN demo.demo_state='LATE' THEN CONCAT(DATE_ADD(demo.day_value,INTERVAL 1 DAY),' 09:00:00')
    WHEN demo.demo_state IN ('RECTIFICATION_PENDING','RECTIFICATION_REVIEW','RECTIFICATION_CLOSED')
      THEN CONCAT(demo.day_value,' 11:00:00') END,
  CASE WHEN demo.demo_state IN ('ONTIME','RECTIFICATION_PENDING','RECTIFICATION_REVIEW','RECTIFICATION_CLOSED') THEN 1
    WHEN demo.demo_state='LATE' THEN 0 END,
  1,9,0,CASE WHEN demo.demo_state IN ('ONTIME','LATE','RECTIFICATION_PENDING','RECTIFICATION_REVIEW','RECTIFICATION_CLOSED')
    THEN '演示巡检记录' END,
  CASE WHEN demo.demo_state IN ('RECTIFICATION_PENDING','RECTIFICATION_REVIEW','RECTIFICATION_CLOSED') THEN 1 ELSE 0 END,
  CASE WHEN demo.demo_state='CANCELLED' THEN '演示取消任务' END,
  CASE WHEN demo.demo_state='CANCELLED' THEN @operator_id END,
  CASE WHEN demo.demo_state='CANCELLED' THEN @operator_name END,
  CASE WHEN demo.demo_state='CANCELLED' THEN CONCAT(demo.day_value,' 09:00:00') END,
  0,CONCAT(demo.day_value,' 07:30:00'),NOW()
FROM demo_tasks demo JOIN general_inspection_template template
  ON template.template_code=CONCAT('EDGE_',demo.point_type_code)
  AND template.scope_type='SYSTEM' AND template.status='PUBLISHED' AND template.deleted=0;

INSERT INTO general_inspection_task_item(task_id,template_item_id,item_key,item_name,guidance,standard_reference,
  allow_na,normal_photo_min,abnormal_photo_min,photo_max,normal_description_required,
  abnormal_description_required,sort_order,result,description,create_time,update_time)
SELECT task.id,item.id,item.item_key,item.item_name,item.guidance,item.standard_reference,0,0,1,9,0,1,item.sort_order,
  CASE WHEN task.submitted_time IS NULL THEN NULL WHEN task.abnormal_count=1 AND item.sort_order=1 THEN 'ABNORMAL' ELSE 'NORMAL' END,
  CASE WHEN task.abnormal_count=1 AND item.sort_order=1 THEN '防护设施局部松动，需立即整改' END,
  COALESCE(task.submitted_time,task.create_time),NOW()
FROM general_inspection_task task JOIN general_inspection_template_item item
  ON item.template_version_id=task.template_version_id
WHERE task.project_id=@project_id AND LEFT(task.plan_name,CHAR_LENGTH(@marker))=@marker;

INSERT INTO file_resource(project_id,file_name,file_type,file_path,storage_provider,storage_key,original_file_name,
  mime_type,file_extension,sha256,file_size,business_type,business_id,uploader_id,status,remark,deleted)
SELECT @project_id,'演示数据-现场全景.png','png','edge-inspection/demo/20260828/overview.png','LOCAL',
  'edge-inspection/demo/20260828/overview.png','演示数据-现场全景.png','image/png','png','$overview_sha',$overview_size,
  'EDGE_INSPECTION_TASK',task.id,@inspector_id,'BOUND',CONCAT(@marker,' 现场全景'),0
FROM general_inspection_task task WHERE task.project_id=@project_id AND LEFT(task.plan_name,CHAR_LENGTH(@marker))=@marker
  AND task.submitted_time IS NOT NULL;
UPDATE general_inspection_task task SET overall_photo_file_ids=(SELECT CAST(MAX(file.id) AS CHAR) FROM file_resource file
  WHERE file.business_type='EDGE_INSPECTION_TASK' AND file.business_id=task.id AND file.remark=CONCAT(@marker,' 现场全景'))
WHERE task.project_id=@project_id AND LEFT(task.plan_name,CHAR_LENGTH(@marker))=@marker AND task.submitted_time IS NOT NULL;

INSERT INTO file_resource(project_id,file_name,file_type,file_path,storage_provider,storage_key,original_file_name,
  mime_type,file_extension,sha256,file_size,business_type,business_id,uploader_id,status,remark,deleted)
SELECT @project_id,'演示数据-异常证据.png','png','edge-inspection/demo/20260828/evidence.png','LOCAL',
  'edge-inspection/demo/20260828/evidence.png','演示数据-异常证据.png','image/png','png','$evidence_sha',$evidence_size,
  'EDGE_INSPECTION_TASK',task.id,@inspector_id,'BOUND',CONCAT(@marker,' 异常证据'),0
FROM general_inspection_task task WHERE task.project_id=@project_id AND LEFT(task.plan_name,CHAR_LENGTH(@marker))=@marker
  AND task.abnormal_count=1;
UPDATE general_inspection_task_item item JOIN general_inspection_task task ON task.id=item.task_id
SET item.photo_file_ids=(SELECT CAST(MAX(file.id) AS CHAR) FROM file_resource file
  WHERE file.business_type='EDGE_INSPECTION_TASK' AND file.business_id=task.id AND file.remark=CONCAT(@marker,' 异常证据'))
WHERE task.project_id=@project_id AND LEFT(task.plan_name,CHAR_LENGTH(@marker))=@marker AND task.abnormal_count=1 AND item.sort_order=1;

INSERT INTO general_inspection_rectification(project_id,task_id,task_item_id,point_id,point_name,item_name,
  problem_desc,requirement,assignee_id,assignee_name,deadline,reviewer_id,reviewer_name,status,feedback,
  completed_time,review_comment,review_time,reject_count,close_time,version,create_time,update_time)
SELECT @project_id,task.id,item.id,task.point_id,task.point_name,item.item_name,item.description,
  '重新固定并检查防护连续性',@inspector_id,@inspector_name,DATE_ADD(task.occurrence_date,INTERVAL 3 DAY),
  @reviewer_id,@reviewer_name,
  CASE DATEDIFF(CURDATE(),task.occurrence_date)
    WHEN 4 THEN 'PENDING' WHEN 5 THEN 'COMPLETED' WHEN 6 THEN 'CLOSED' END,
  CASE WHEN DATEDIFF(CURDATE(),task.occurrence_date) IN (5,6) THEN '已重新紧固并复核' END,
  CASE WHEN DATEDIFF(CURDATE(),task.occurrence_date) IN (5,6) THEN DATE_ADD(task.submitted_time,INTERVAL 5 HOUR) END,
  CASE WHEN DATEDIFF(CURDATE(),task.occurrence_date)=6 THEN '复查通过' END,
  CASE WHEN DATEDIFF(CURDATE(),task.occurrence_date)=6 THEN DATE_ADD(task.submitted_time,INTERVAL 7 HOUR) END,
  0,
  CASE WHEN DATEDIFF(CURDATE(),task.occurrence_date)=6 THEN DATE_ADD(task.submitted_time,INTERVAL 7 HOUR) END,
  0,task.submitted_time,NOW()
FROM general_inspection_task task JOIN general_inspection_task_item item ON item.task_id=task.id AND item.sort_order=1
WHERE task.project_id=@project_id AND LEFT(task.plan_name,CHAR_LENGTH(@marker))=@marker AND task.abnormal_count=1;

INSERT INTO file_resource(project_id,file_name,file_type,file_path,storage_provider,storage_key,original_file_name,
  mime_type,file_extension,sha256,file_size,business_type,business_id,uploader_id,status,remark,deleted)
SELECT @project_id,'演示数据-整改后.png','png','edge-inspection/demo/20260828/rectification.png','LOCAL',
  'edge-inspection/demo/20260828/rectification.png','演示数据-整改后.png','image/png','png','$rectification_sha',$rectification_size,
  'EDGE_INSPECTION_RECTIFICATION',rectification.id,@inspector_id,'BOUND',CONCAT(@marker,' 整改后照片'),0
FROM general_inspection_rectification rectification
JOIN general_inspection_task task ON task.id=rectification.task_id
WHERE task.project_id=@project_id AND LEFT(task.plan_name,CHAR_LENGTH(@marker))=@marker
  AND rectification.feedback IS NOT NULL;
UPDATE general_inspection_rectification rectification
JOIN general_inspection_task task ON task.id=rectification.task_id
SET rectification_photo_file_ids=(SELECT CAST(MAX(file.id) AS CHAR) FROM file_resource file
  WHERE file.business_type='EDGE_INSPECTION_RECTIFICATION' AND file.business_id=rectification.id
    AND file.remark=CONCAT(@marker,' 整改后照片'))
WHERE task.project_id=@project_id AND LEFT(task.plan_name,CHAR_LENGTH(@marker))=@marker
  AND rectification.feedback IS NOT NULL;

INSERT INTO general_inspection_action_log(project_id,business_type,business_id,action_type,operator_id,operator_name,
  from_status,to_status,comment)
VALUES(@project_id,'PLAN',@plan_id,'DEMO_SEED',@operator_id,@operator_name,NULL,'PUBLISHED',
  CONCAT(@marker,' 北蔡临边巡检演示数据幂等种子'));
COMMIT;
SQL

task_scope="project_id=$PROJECT_ID AND LEFT(plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER'"
seeded_task_scope="$task_scope AND occurrence_date BETWEEN DATE_SUB(CURDATE(),INTERVAL 6 DAY) AND CURDATE()"
point_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_point WHERE project_id=$PROJECT_ID AND point_code IN ($DEMO_POINT_CODES_SQL) AND deleted=0")"
task_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE $seeded_task_scope")"
today_pending_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE $task_scope AND occurrence_date=CURDATE() AND status='PENDING' AND submitted_time IS NULL")"
overdue_pending_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE $task_scope AND occurrence_date<CURDATE() AND status='PENDING' AND submitted_time IS NULL")"
late_completed_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE $task_scope AND status='COMPLETED' AND on_time=0")"
cancelled_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE $task_scope AND status='CANCELLED'")"
normal_ontime_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE $task_scope AND status='COMPLETED' AND on_time=1 AND abnormal_count=0")"
future_task_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE $task_scope AND occurrence_date>CURDATE()")"
rectification_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_rectification r JOIN general_inspection_task t ON t.id=r.task_id WHERE t.project_id=$PROJECT_ID AND LEFT(t.plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER'")"
rectification_pending_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_rectification r JOIN general_inspection_task t ON t.id=r.task_id WHERE t.project_id=$PROJECT_ID AND LEFT(t.plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER' AND r.status='PENDING'")"
rectification_review_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_rectification r JOIN general_inspection_task t ON t.id=r.task_id WHERE t.project_id=$PROJECT_ID AND LEFT(t.plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER' AND r.status='COMPLETED'")"
rectification_closed_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_rectification r JOIN general_inspection_task t ON t.id=r.task_id WHERE t.project_id=$PROJECT_ID AND LEFT(t.plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER' AND r.status='CLOSED'")"
missing_photo_count="$(mysql_exec -e "SELECT COUNT(*) FROM general_inspection_task WHERE $task_scope AND submitted_time IS NOT NULL AND (overall_photo_file_ids IS NULL OR overall_photo_file_ids='')")"
visible_marker_count="$(mysql_exec -e "SELECT
  (SELECT COUNT(*) FROM general_inspection_point WHERE project_id=$PROJECT_ID AND point_code IN ($DEMO_POINT_CODES_SQL) AND LOCATE('$DEMO_MARKER',point_name)>0)
  + (SELECT COUNT(*) FROM general_inspection_task WHERE $task_scope AND (LOCATE('$DEMO_MARKER',point_name)>0 OR LOCATE('$DEMO_MARKER',COALESCE(remark,''))>0 OR LOCATE('$DEMO_MARKER',COALESCE(cancel_reason,''))>0))
  + (SELECT COUNT(*) FROM general_inspection_task_item item JOIN general_inspection_task task ON task.id=item.task_id WHERE task.project_id=$PROJECT_ID AND LEFT(task.plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER' AND LOCATE('$DEMO_MARKER',COALESCE(item.description,''))>0)
  + (SELECT COUNT(*) FROM general_inspection_rectification rectification JOIN general_inspection_task task ON task.id=rectification.task_id WHERE task.project_id=$PROJECT_ID AND LEFT(task.plan_name,CHAR_LENGTH('$DEMO_MARKER'))='$DEMO_MARKER' AND (LOCATE('$DEMO_MARKER',COALESCE(rectification.problem_desc,''))>0 OR LOCATE('$DEMO_MARKER',COALESCE(rectification.requirement,''))>0 OR LOCATE('$DEMO_MARKER',COALESCE(rectification.feedback,''))>0 OR LOCATE('$DEMO_MARKER',COALESCE(rectification.review_comment,''))>0))")"
edge_role_authorization_gap_count="$(mysql_exec -e "SELECT COUNT(*) FROM sys_role role
  WHERE role.role_code IN ('ELECTRICIAN','SAFETY_OFFICER') AND role.scope_type='PROJECT'
    AND role.enabled=1 AND role.deleted=0
    AND EXISTS (SELECT 1 FROM sys_role_permission role_permission JOIN sys_permission permission ON permission.id=role_permission.permission_id
      WHERE role_permission.role_id=role.id AND permission.permission_code='EDGE_INSPECTION_VIEW' AND permission.enabled=1 AND permission.deleted=0)
    AND (NOT EXISTS (SELECT 1 FROM sys_role_business_module module WHERE module.role_id=role.id AND module.module_code='INSPECTION')
      OR 3<>(SELECT COUNT(DISTINCT menu.menu_code) FROM sys_role_menu role_menu JOIN sys_menu menu ON menu.id=role_menu.menu_id
        WHERE role_menu.role_id=role.id AND menu.menu_code IN ('WEB_INSPECTION','MINI_INSPECTION','INSPECTION_EDGE')
          AND menu.visible=1 AND menu.enabled=1 AND menu.deleted=0)
      OR (role.role_code='SAFETY_OFFICER' AND NOT EXISTS (
        SELECT 1 FROM sys_role_permission role_permission JOIN sys_permission permission ON permission.id=role_permission.permission_id
        WHERE role_permission.role_id=role.id AND permission.permission_code='EDGE_INSPECTION_EXPORT'
          AND permission.enabled=1 AND permission.deleted=0)))")"

[ "$point_count" = "$EXPECTED_POINT_COUNT" ] || fail "演示点位写入数量异常：$point_count"
[ "$task_count" = "$EXPECTED_TASK_COUNT" ] || fail "最近7天演示任务应为 $EXPECTED_TASK_COUNT 项，实际为 $task_count"
[ "$today_pending_count" = "8" ] || fail "今日待巡检任务应为 8 项，实际为 $today_pending_count"
[ "$overdue_pending_count" = "2" ] || fail "历史逾期未检应为 2 项，实际为 $overdue_pending_count"
[ "$late_completed_count" = "2" ] || fail "逾期补检应为 2 项，实际为 $late_completed_count"
[ "$cancelled_count" = "1" ] || fail "已取消任务应为 1 项，实际为 $cancelled_count"
[ "$normal_ontime_count" = "40" ] || fail "其余按时完成任务应为 40 项，实际为 $normal_ontime_count"
[ "$rectification_count" = "3" ] || fail "演示整改单应为 3 张，实际为 $rectification_count"
[ "$rectification_pending_count" = "1" ] || fail "待整改应为 1 张，实际为 $rectification_pending_count"
[ "$rectification_review_count" = "1" ] || fail "待复查应为 1 张，实际为 $rectification_review_count"
[ "$rectification_closed_count" = "1" ] || fail "已闭环应为 1 张，实际为 $rectification_closed_count"
[ "$missing_photo_count" = "0" ] || fail "存在已提交但缺少现场照片的演示任务"
[ "$visible_marker_count" = "0" ] || fail "业务可见字段仍包含 $DEMO_MARKER：$visible_marker_count 处"
[ "$edge_role_authorization_gap_count" = "0" ] || fail "电工/安全员临边权限对应的模块、菜单或安全员导出授权未完整同步：$edge_role_authorization_gap_count 个角色"
info "演示数据写入完成：点位=${point_count}，最近7天手工种子任务=${task_count}（今日待巡检=8，逾期未检=2，逾期补检=2，取消=1，待整改=1，待复查=1，已闭环=1，其余按时完成=40），调度器内部预生成未来任务=${future_task_count}，已提交任务照片完整，演示角色临边模块/菜单完整，安全员临边导出授权完整"
