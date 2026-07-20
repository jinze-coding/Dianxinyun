-- 将项目展示数据从公司/城市级项目切换为项目内部区域/施工板块口径。
-- 只更新 project_info 展示字段，保留 id、权限关系、业务关联、经纬度和坐标系。

UPDATE project_info
SET project_name = 'A1作业区域',
    short_name = 'A1区域',
    area = '12000',
    period = '2026.01-2026.12',
    phase = '主体施工',
    project_status = 'normal',
    safety_goal = '零事故',
    quality_goal = '区域样板',
    manager = '张区域',
    contractor = '土建一队',
    description = '项目内部A1作业区域，覆盖主入口、材料通道和临电巡检点位。',
    address = '项目现场A1作业区域'
WHERE id = 1 AND deleted = 0;

UPDATE project_info
SET project_name = 'A2作业区域',
    short_name = 'A2区域',
    area = '9800',
    period = '2026.02-2026.11',
    phase = '机电安装',
    project_status = 'normal',
    safety_goal = '零事故',
    quality_goal = '一次成优',
    manager = '李区域',
    contractor = '安装一队',
    description = '项目内部A2作业区域，覆盖机电安装、材料堆放和交叉作业面。',
    address = '项目现场A2作业区域'
WHERE id = 2 AND deleted = 0;

UPDATE project_info
SET project_name = 'B1施工板块',
    short_name = 'B1板块',
    area = '15000',
    period = '2026.01-2027.03',
    phase = '结构施工',
    project_status = 'normal',
    safety_goal = '零事故',
    quality_goal = '优良',
    manager = '王板块',
    contractor = '结构一队',
    description = '项目内部B1施工板块，覆盖主体结构、塔吊作业和周界管理区域。',
    address = '项目现场B1施工板块'
WHERE id = 3 AND deleted = 0;

UPDATE project_info
SET project_name = 'B2施工板块',
    short_name = 'B2板块',
    area = '13200',
    period = '2026.03-2027.02',
    phase = '交叉施工',
    project_status = 'warning',
    safety_goal = '零事故',
    quality_goal = '优良',
    manager = '陈板块',
    contractor = '结构二队',
    description = '项目内部B2施工板块，覆盖二次结构、临电设施和安全复核重点区域。',
    address = '项目现场B2施工板块'
WHERE id = 4 AND deleted = 0;

UPDATE project_info
SET project_name = 'C1作业区域',
    short_name = 'C1区域',
    area = '8600',
    period = '2026.04-2026.12',
    phase = '基础施工',
    project_status = 'normal',
    safety_goal = '零事故',
    quality_goal = '区域达标',
    manager = '赵区域',
    contractor = '基础一队',
    description = '项目内部C1作业区域，覆盖基础施工、排水沟和临时道路作业面。',
    address = '项目现场C1作业区域'
WHERE id = 5 AND deleted = 0;

UPDATE project_info
SET project_name = 'C2作业区域',
    short_name = 'C2区域',
    area = '9100',
    period = '2026.05-2027.01',
    phase = '装饰施工',
    project_status = 'warning',
    safety_goal = '零事故',
    quality_goal = '过程精品',
    manager = '刘区域',
    contractor = '装饰一队',
    description = '项目内部C2作业区域，覆盖装饰施工、成品保护和高频巡检点位。',
    address = '项目现场C2作业区域'
WHERE id = 6 AND deleted = 0;

UPDATE project_info
SET project_name = 'C3作业区域',
    short_name = 'C3区域',
    area = '7600',
    period = '2026.06-2026.10',
    phase = '设备安装',
    project_status = 'normal',
    safety_goal = '零事故',
    quality_goal = '安装达标',
    manager = '黄区域',
    contractor = '机电二队',
    description = '项目内部C3作业区域，覆盖设备安装、配电箱巡检和通道管理。',
    address = '项目现场C3作业区域'
WHERE id = 7 AND deleted = 0;

UPDATE project_info
SET project_name = 'D1施工板块',
    short_name = 'D1板块',
    area = '11000',
    period = '2026.03-2027.01',
    phase = '综合收尾',
    project_status = 'normal',
    safety_goal = '零事故',
    quality_goal = '移交达标',
    manager = '周板块',
    contractor = '综合保障队',
    description = '项目内部D1施工板块，覆盖综合收尾、资料移交和现场整改闭环区域。',
    address = '项目现场D1施工板块'
WHERE id = 8 AND deleted = 0;
