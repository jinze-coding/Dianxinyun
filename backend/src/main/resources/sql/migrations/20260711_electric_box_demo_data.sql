-- 本地演示数据：电箱日检、复核、现场抽查、整改、复查完整状态。
-- 幂等插入，使用 [DEMO] 标识；仅用于开发/演示环境。
USE dianxinyun;

INSERT INTO electric_box (project_id, box_code, box_name, install_location, responsible_electrician_id,
  responsible_electrician_name, safety_manager_id, safety_manager_name, qr_code, qr_status, status,
  public_code, public_access_enabled, remark, deleted)
SELECT 1, 'EB-A1-003', '地下室临时照明电箱', 'A区地下室通道入口', 2, '项目经理', 2, '项目经理',
  'EBQR-A1-003', 'BOUND', 'ACTIVE', 'PUB-A1-003', 1, '[DEMO] 待复查演示电箱', 0
WHERE NOT EXISTS (SELECT 1 FROM electric_box WHERE project_id=1 AND box_code='EB-A1-003');

INSERT INTO electric_box (project_id, box_code, box_name, install_location, responsible_electrician_id,
  responsible_electrician_name, safety_manager_id, safety_manager_name, qr_code, qr_status, status,
  public_code, public_access_enabled, remark, deleted)
SELECT 1, 'EB-A1-004', '加工区备用电箱', 'A区钢筋加工棚西侧', 2, '项目经理', 2, '项目经理',
  'EBQR-A1-004', 'BOUND', 'ACTIVE', 'PUB-A1-004', 1, '[DEMO] 复核退回演示电箱', 0
WHERE NOT EXISTS (SELECT 1 FROM electric_box WHERE project_id=1 AND box_code='EB-A1-004');

SET @b1=(SELECT id FROM electric_box WHERE project_id=1 AND box_code='EB-A1-001' LIMIT 1);
SET @b2=(SELECT id FROM electric_box WHERE project_id=1 AND box_code='EB-A1-002' LIMIT 1);
SET @b3=(SELECT id FROM electric_box WHERE project_id=1 AND box_code='EB-A1-003' LIMIT 1);
SET @b4=(SELECT id FROM electric_box WHERE project_id=1 AND box_code='EB-A1-004' LIMIT 1);

INSERT INTO electric_box_inspection_scope (project_id,electric_box_id,included,effective_date,reason,operator_id,operator_name)
SELECT 1,id,1,DATE_SUB(CURDATE(),INTERVAL 30 DAY),'[DEMO] 纳入每日巡检',1,'系统管理员'
FROM electric_box b WHERE b.project_id=1 AND b.box_code IN ('EB-A1-001','EB-A1-002','EB-A1-003','EB-A1-004')
AND NOT EXISTS (SELECT 1 FROM electric_box_inspection_scope s WHERE s.electric_box_id=b.id);

INSERT INTO project_inspection_setting (project_id,daily_cutoff_time,pre_due_reminder_minutes,review_due_hours,rectification_days,enabled)
VALUES (1,'18:00:00',60,24,3,1) ON DUPLICATE KEY UPDATE daily_cutoff_time=VALUES(daily_cutoff_time);

INSERT INTO inspection_record (project_id,electric_box_id,template_code,source,check_date,inspector_id,inspector_name,
  status,review_status,review_due_time,assigned_reviewer_id,assigned_reviewer_name,review_overdue,
  abnormal_count,remark,deleted,create_time,update_time)
SELECT 1,@b1,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',CURDATE(),2,'项目经理','REVIEW_PENDING','PENDING',
  DATE_ADD(NOW(),INTERVAL 24 HOUR),2,'项目经理',0,0,'[DEMO] 今日检查正常，等待安全员复核',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_record WHERE remark='[DEMO] 今日检查正常，等待安全员复核');
SET @r1=(SELECT id FROM inspection_record WHERE remark='[DEMO] 今日检查正常，等待安全员复核' LIMIT 1);

INSERT INTO inspection_record (project_id,electric_box_id,template_code,source,problem_category,check_date,inspector_id,inspector_name,
  status,review_status,review_overdue,abnormal_count,remark,deleted,create_time,update_time)
SELECT 1,@b2,'ELECTRIC_BOX_DAILY','SAFETY_SPOT_CHECK','ELECTRICAL_PROTECTION',CURDATE(),2,'项目经理',
  'RECTIFICATION_PENDING','NOT_REQUIRED',0,1,'[DEMO] 漏电保护器测试按钮无响应，现场抽查直接派发整改',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_record WHERE remark='[DEMO] 漏电保护器测试按钮无响应，现场抽查直接派发整改');
SET @r2=(SELECT id FROM inspection_record WHERE remark='[DEMO] 漏电保护器测试按钮无响应，现场抽查直接派发整改' LIMIT 1);

INSERT INTO inspection_record (project_id,electric_box_id,template_code,source,check_date,inspector_id,inspector_name,
  status,review_status,reviewer_id,reviewer_name,review_time,review_overdue,abnormal_count,remark,deleted,create_time,update_time)
SELECT 1,@b3,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',DATE_SUB(CURDATE(),INTERVAL 1 DAY),2,'项目经理',
  'REVIEW_PASSED','PASSED',2,'项目经理',NOW(),0,0,'[DEMO] 日检复核已通过',0,DATE_SUB(NOW(),INTERVAL 1 DAY),NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_record WHERE remark='[DEMO] 日检复核已通过');
SET @r3=(SELECT id FROM inspection_record WHERE remark='[DEMO] 日检复核已通过' LIMIT 1);

INSERT INTO inspection_record (project_id,electric_box_id,template_code,source,check_date,inspector_id,inspector_name,
  status,review_status,reviewer_id,reviewer_name,review_time,review_comment,review_overdue,abnormal_count,remark,deleted,create_time,update_time)
SELECT 1,@b4,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',DATE_SUB(CURDATE(),INTERVAL 2 DAY),2,'项目经理',
  'REVIEW_REJECTED','REJECTED',2,'项目经理',NOW(),'内部照片不清晰，请重新上传',0,1,'[DEMO] 复核退回待电工修改',0,DATE_SUB(NOW(),INTERVAL 2 DAY),NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_record WHERE remark='[DEMO] 复核退回待电工修改');
SET @r4=(SELECT id FROM inspection_record WHERE remark='[DEMO] 复核退回待电工修改' LIMIT 1);

INSERT INTO inspection_record_item (record_id,item_code,item_name,result,description,deleted)
SELECT r.id,c.item_code,c.item_name,
  IF(r.id=@r2 AND c.item_code='LEAKAGE_PROTECTOR','ABNORMAL',IF(r.id=@r4 AND c.item_code='APPEARANCE','ABNORMAL','NORMAL')),
  IF(r.id=@r2 AND c.item_code='LEAKAGE_PROTECTOR','测试按钮无响应',IF(r.id=@r4 AND c.item_code='APPEARANCE','箱门未可靠关闭','')),0
FROM inspection_record r
JOIN (SELECT 'APPEARANCE' item_code,'内外观' item_name UNION ALL SELECT 'LEAKAGE_PROTECTOR','漏电保护器'
 UNION ALL SELECT 'FUSE','熔断' UNION ALL SELECT 'PROTECTIVE_ZERO','保护接零'
 UNION ALL SELECT 'SOCKET_220V','220V插座' UNION ALL SELECT 'SOCKET_380V','380V插座') c
WHERE r.id IN (@r1,@r2,@r3,@r4)
AND NOT EXISTS (SELECT 1 FROM inspection_record_item i WHERE i.record_id=r.id AND i.item_code=c.item_code);

INSERT INTO inspection_rectification (project_id,electric_box_id,inspection_record_id,box_code,problem_desc,problem_category,
 requirement,assignee_id,assignee_name,deadline,status,reject_count,escalation_status,deleted,create_time,update_time)
SELECT 1,@b2,@r2,'EB-A1-002','漏电保护器测试按钮无响应','ELECTRICAL_PROTECTION',
 '更换漏电保护器并上传整改照片',2,'项目经理',DATE_ADD(CURDATE(),INTERVAL 3 DAY),'PENDING',0,'NONE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_rectification WHERE problem_desc='漏电保护器测试按钮无响应');

INSERT INTO inspection_rectification (project_id,electric_box_id,inspection_record_id,box_code,problem_desc,requirement,
 assignee_id,assignee_name,deadline,status,feedback,completed_time,reject_count,escalation_status,deleted,create_time,update_time)
SELECT 1,@b3,@r3,'EB-A1-003','插座防护盖破损','更换防护盖并复测',2,'项目经理',DATE_ADD(CURDATE(),INTERVAL 2 DAY),
 'COMPLETED','已更换防护盖并完成绝缘测试',NOW(),0,'NONE',0,DATE_SUB(NOW(),INTERVAL 1 DAY),NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_rectification WHERE problem_desc='插座防护盖破损');

INSERT INTO inspection_rectification (project_id,electric_box_id,inspection_record_id,box_code,problem_desc,requirement,
 assignee_id,assignee_name,deadline,status,feedback,reviewer_id,reviewer_name,review_time,review_comment,reject_count,
 recheck_deadline,escalation_status,deleted,create_time,update_time)
SELECT 1,@b4,@r4,'EB-A1-004','线缆端头裸露','使用端子重新压接并做好绝缘防护',2,'项目经理',CURDATE(),
 'REJECTED','使用绝缘胶带临时包扎',2,'项目经理',NOW(),'临时包扎不符合要求',1,DATE_ADD(CURDATE(),INTERVAL 3 DAY),'NONE',0,DATE_SUB(NOW(),INTERVAL 2 DAY),NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_rectification WHERE problem_desc='线缆端头裸露');

INSERT INTO inspection_rectification (project_id,electric_box_id,box_code,problem_desc,requirement,assignee_id,assignee_name,
 deadline,status,feedback,completed_time,reviewer_id,reviewer_name,review_time,review_comment,reject_count,escalation_status,
 close_time,deleted,create_time,update_time)
SELECT 1,@b1,'EB-A1-001','电箱周边堆放杂物','清理电箱周边一米范围',2,'项目经理',DATE_SUB(CURDATE(),INTERVAL 1 DAY),
 'CLOSED','已清理周边杂物并恢复安全通道',DATE_SUB(NOW(),INTERVAL 1 DAY),2,'项目经理',NOW(),'现场复查通过，整改关闭',
 0,'NONE',NOW(),0,DATE_SUB(NOW(),INTERVAL 3 DAY),NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_rectification WHERE problem_desc='电箱周边堆放杂物');
