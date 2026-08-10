-- 项目基础信息档案、乐观锁和项目效果图查询索引。
-- 仅扩展结构，不写入任何具体项目资料；可重复执行。

DROP PROCEDURE IF EXISTS add_project_profile_column;
DELIMITER $$
CREATE PROCEDURE add_project_profile_column(IN column_name_value VARCHAR(64), IN column_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'project_info'
          AND column_name = column_name_value
    ) THEN
        SET @project_profile_sql = CONCAT(
            'ALTER TABLE project_info ADD COLUMN `', column_name_value, '` ', column_definition
        );
        PREPARE project_profile_statement FROM @project_profile_sql;
        EXECUTE project_profile_statement;
        DEALLOCATE PREPARE project_profile_statement;
    END IF;
END$$
DELIMITER ;

CALL add_project_profile_column('direct_company', 'VARCHAR(200) NULL COMMENT ''直属公司''');
CALL add_project_profile_column('manager_phone', 'VARCHAR(30) NULL COMMENT ''项目经理联系方式''');
CALL add_project_profile_column('space_capacity', 'VARCHAR(100) NULL COMMENT ''空间容量''');
CALL add_project_profile_column('engineering_type', 'VARCHAR(100) NULL COMMENT ''工程类型''');
CALL add_project_profile_column('actual_start_date', 'DATE NULL COMMENT ''实际开工日期''');
CALL add_project_profile_column('actual_end_date', 'DATE NULL COMMENT ''实际竣工日期''');
CALL add_project_profile_column('owner_unit', 'VARCHAR(200) NULL COMMENT ''建设单位''');
CALL add_project_profile_column('supervision_unit', 'VARCHAR(200) NULL COMMENT ''监理单位''');
CALL add_project_profile_column('design_unit', 'VARCHAR(200) NULL COMMENT ''设计单位''');
CALL add_project_profile_column('contractor_credit_code', 'VARCHAR(32) NULL COMMENT ''施工单位统一社会信用代码''');
CALL add_project_profile_column('contractor_license_number', 'VARCHAR(100) NULL COMMENT ''施工单位安全生产许可证号''');
CALL add_project_profile_column('general_contract_number', 'VARCHAR(100) NULL COMMENT ''施工总承包合同编号''');
CALL add_project_profile_column('project_classification', 'VARCHAR(100) NULL COMMENT ''项目分类''');
CALL add_project_profile_column('investment_entity', 'VARCHAR(100) NULL COMMENT ''投资主体''');
CALL add_project_profile_column('contracting_mode', 'VARCHAR(100) NULL COMMENT ''承建模式''');
CALL add_project_profile_column('contract_amount', 'DECIMAL(18,2) NULL COMMENT ''合同金额''');
CALL add_project_profile_column('building_area', 'DECIMAL(14,2) NULL COMMENT ''建筑面积数值''');
CALL add_project_profile_column('project_scale', 'VARCHAR(100) NULL COMMENT ''项目规模''');
CALL add_project_profile_column('project_target', 'VARCHAR(200) NULL COMMENT ''项目目标''');
CALL add_project_profile_column('land_area', 'DECIMAL(14,2) NULL COMMENT ''用地面积''');
CALL add_project_profile_column('building_height', 'DECIMAL(10,3) NULL COMMENT ''建筑高度''');
CALL add_project_profile_column('project_category', 'VARCHAR(100) NULL COMMENT ''项目类别''');
CALL add_project_profile_column('excavation_depth', 'DECIMAL(10,3) NULL COMMENT ''基坑开挖深度''');
CALL add_project_profile_column('underground_floor_count', 'INT NULL COMMENT ''地下层数''');
CALL add_project_profile_column('aboveground_floor_count', 'INT NULL COMMENT ''地上层数''');
CALL add_project_profile_column('green_construction_goal', 'VARCHAR(500) NULL COMMENT ''绿色建造目标''');
CALL add_project_profile_column('project_level', 'VARCHAR(100) NULL COMMENT ''项目级别''');
CALL add_project_profile_column('management_staff_count', 'INT NULL COMMENT ''管理人员数''');
CALL add_project_profile_column('attendance_count', 'INT NULL COMMENT ''考勤人数''');
CALL add_project_profile_column('party_member_count', 'INT NULL COMMENT ''党员人数''');
CALL add_project_profile_column('fixed_ip_address', 'VARCHAR(45) NULL COMMENT ''固定IPv4或IPv6地址''');
CALL add_project_profile_column('profile_version', 'INT NOT NULL DEFAULT 0 COMMENT ''项目档案乐观锁版本''');

ALTER TABLE project_info
    MODIFY COLUMN safety_goal VARCHAR(500) NULL COMMENT '安全目标',
    MODIFY COLUMN quality_goal VARCHAR(500) NULL COMMENT '质量目标';

DROP PROCEDURE IF EXISTS add_project_profile_column;

DROP PROCEDURE IF EXISTS add_project_profile_file_index;
DELIMITER $$
CREATE PROCEDURE add_project_profile_file_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'file_resource'
          AND index_name = 'idx_file_project_profile'
    ) THEN
        CREATE INDEX idx_file_project_profile
            ON file_resource(project_id, business_type, business_id, status, deleted, create_time);
    END IF;
END$$
DELIMITER ;
CALL add_project_profile_file_index();
DROP PROCEDURE IF EXISTS add_project_profile_file_index;
