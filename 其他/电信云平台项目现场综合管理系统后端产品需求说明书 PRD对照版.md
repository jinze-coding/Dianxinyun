下面给你一版 **具体可实施的后端 PRD 说明文件**。
这版不再是“指导建议”，而是按开发可以直接拆任务、建表、写接口、部署联调的方式整理。

------

# 电信云平台项目现场综合管理系统后端产品需求说明书 PRD对照版



### 高并发 / 多项目 / 云部署实施版

**版本：V1.0**
**对应前端页面：项目概况、人员与安全、设备与监控**
**系统定位：多项目现场综合管理平台**
**预估规模：高峰期 50 个以上项目同时运行**
**部署方式：云服务器部署**

------

# 1. 后端建设目标

本系统后端需要支撑前端 3 个核心页面：

```text
1. 项目概况
2. 人员与安全
3. 设备与监控
```

后端需要完成以下具体能力：

1. 支持多项目统一管理。
2. 支持用户、角色、项目权限控制。
3. 支持项目概况数据查询。
4. 支持原人员管理系统跳转入口配置。
5. 支持临时人员新增、编辑、查询、状态管理。
6. 支持安全三级教育批次管理。
7. 支持培训资料、签字文件、证书、施工日志、会议纪要等文件上传管理。
8. 支持资料查询、下载、归档、删除。
9. 支持海康摄像头资源管理。
10. 支持实时视频播放地址获取。
11. 支持视频窗口布局配置：1路、4路、8路、16路。
12. 支持塔吊等设备基础信息管理。
13. 支持设备状态同步与展示。
14. 支持操作日志、视频访问日志、文件操作日志。
15. 支持云端多实例部署和高并发访问。

------

# 2. 后端实施架构

## 2.1 后端技术选型

本系统后端采用以下技术栈实施：

```text
Java 17
Spring Boot 3.x
Spring Security / Sa-Token / JWT
MyBatis-Plus
MySQL 8.x
Redis
RabbitMQ
MinIO 或 云对象存储 OSS
Nginx
Swagger / Knife4j
Docker
```

## 2.2 部署结构

```text
用户浏览器
    ↓
云负载均衡 SLB
    ↓
Nginx
    ↓
后端应用服务 x 2+
    ↓
MySQL 云数据库
Redis 缓存
MinIO / OSS 文件存储
RabbitMQ 消息队列
第三方系统接入服务
海康视频平台 / 视频网关
塔吊系统
原人员管理系统
```

## 2.3 服务形态

本期采用：

```text
模块化单体架构
```

但代码结构必须按服务边界拆分，后续可平滑升级为微服务。

后端包结构建议：

```text
backend
├── auth                登录认证与权限
├── project             项目管理
├── external            外部系统入口
├── temp-person         临时人员管理
├── safety              安全三级教育
├── file                文件资料管理
├── camera              摄像头与视频资源
├── video-layout        视频窗口布局
├── device              设备与塔吊管理
├── integration         第三方系统接入
├── log                 操作日志
├── common              公共工具
└── config              系统配置
```

------

# 3. 核心业务模块

## 3.1 项目概况模块

对应前端页面：

```text
项目概况
```

后端负责：

- 项目列表查询
- 项目详情查询
- 项目统计数据汇总
- 多项目切换
- 用户可访问项目控制
- 原人员管理系统入口返回
- 当前项目视频总览数据返回
- 当前项目资料统计返回

------

## 3.2 人员与安全模块

对应前端页面：

```text
人员与安全
```

后端负责：

- 临时人员登记
- 临时人员编辑
- 临时人员查询
- 临时人员删除
- 临时人员状态变更
- 安全三级教育批次创建
- 安全教育人员关联
- 培训完成状态管理
- 培训文件上传
- 签字文件上传
- 教育记录留档查询

------

## 3.3 设备与监控模块

对应前端页面：

```text
设备与监控
```

后端负责：

- 摄像头资源管理
- 摄像头与项目绑定
- 摄像头在线状态查询
- 获取实时视频播放地址
- 视频窗口布局保存与查询
- 塔吊设备管理
- 设备状态查询
- 设备状态记录
- 第三方塔吊系统接入预留

------

## 3.4 文件资料模块

资料管理并入项目概况，同时也服务人员与安全页面。

后端负责：

- 文件上传
- 文件下载
- 文件预览地址返回
- 文件归档
- 文件删除
- 文件分类查询
- 文件与业务数据绑定

文件类型包括：

```text
安全培训资料
工人签字文件
证书文件
会议纪要
施工日志
凭证文件
其他资料
```

------

# 4. 数据库设计

以下为本期必须建设的数据表。

------

## 4.1 项目表 project_info

```sql
CREATE TABLE project_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  project_code VARCHAR(64) NOT NULL COMMENT '项目编号',
  project_name VARCHAR(255) NOT NULL COMMENT '项目名称',
  project_short_name VARCHAR(128) COMMENT '项目简称',
  description TEXT COMMENT '项目简介',
  address VARCHAR(255) COMMENT '项目地址',
  building_area DECIMAL(18,2) COMMENT '建筑面积',
  start_date DATE COMMENT '开始日期',
  end_date DATE COMMENT '结束日期',
  current_stage VARCHAR(64) COMMENT '当前阶段',
  safety_goal VARCHAR(255) COMMENT '安全目标',
  quality_goal VARCHAR(255) COMMENT '质量目标',
  project_status VARCHAR(32) DEFAULT 'NORMAL' COMMENT '项目状态',
  sort_order INT DEFAULT 0 COMMENT '排序',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除'
);
```

索引：

```sql
CREATE INDEX idx_project_code ON project_info(project_code);
CREATE INDEX idx_project_status ON project_info(project_status);
```

------

## 4.2 用户表 sys_user

```sql
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL COMMENT '登录账号',
  password VARCHAR(255) NOT NULL COMMENT '加密密码',
  real_name VARCHAR(64) COMMENT '真实姓名',
  phone VARCHAR(32) COMMENT '手机号',
  email VARCHAR(128) COMMENT '邮箱',
  status TINYINT DEFAULT 1 COMMENT '状态：1启用，0禁用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT DEFAULT 0
);
```

------

## 4.3 角色表 sys_role

```sql
CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
  role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
  description VARCHAR(255) COMMENT '说明',
  status TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

------

## 4.4 用户角色表 sys_user_role

```sql
CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

------

## 4.5 用户项目权限表 sys_user_project

```sql
CREATE TABLE sys_user_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

用途：

```text
控制用户可访问哪些项目。
后端所有 projectId 查询都必须校验该表。
```

------

## 4.6 外部系统配置表 external_system_config

```sql
CREATE TABLE external_system_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT COMMENT '项目ID，可为空',
  system_code VARCHAR(64) NOT NULL COMMENT '系统编码',
  system_name VARCHAR(128) NOT NULL COMMENT '系统名称',
  system_url VARCHAR(500) NOT NULL COMMENT '跳转地址',
  open_type VARCHAR(32) DEFAULT 'NEW_WINDOW' COMMENT '打开方式',
  status TINYINT DEFAULT 1 COMMENT '是否启用',
  remark VARCHAR(255),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

用于：

```text
原人员管理系统跳转入口。
```

------

## 4.7 临时人员表 temporary_person

```sql
CREATE TABLE temporary_person (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '所属项目',
  name VARCHAR(64) NOT NULL COMMENT '姓名',
  gender VARCHAR(16) COMMENT '性别',
  id_card VARCHAR(32) COMMENT '身份证号',
  phone VARCHAR(32) COMMENT '手机号',
  company_name VARCHAR(128) COMMENT '所属单位',
  work_type VARCHAR(64) COMMENT '工种',
  entry_time DATETIME COMMENT '入场时间',
  leave_time DATETIME COMMENT '离场时间',
  person_status VARCHAR(32) DEFAULT 'WAIT_EDUCATION' COMMENT '人员状态',
  remark VARCHAR(255),
  create_user_id BIGINT COMMENT '创建人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT DEFAULT 0
);
```

人员状态：

```text
WAIT_EDUCATION  待教育
EDUCATED        已教育
LEFT            已离场
DISABLED        禁用
```

索引：

```sql
CREATE INDEX idx_temp_person_project ON temporary_person(project_id);
CREATE INDEX idx_temp_person_status ON temporary_person(person_status);
CREATE INDEX idx_temp_person_id_card ON temporary_person(id_card);
CREATE INDEX idx_temp_person_phone ON temporary_person(phone);
```

------

## 4.8 安全教育批次表 safety_education_batch

```sql
CREATE TABLE safety_education_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  batch_name VARCHAR(128) NOT NULL COMMENT '批次名称',
  education_type VARCHAR(64) DEFAULT 'THREE_LEVEL' COMMENT '教育类型',
  training_time DATETIME COMMENT '培训时间',
  training_location VARCHAR(128) COMMENT '培训地点',
  trainer VARCHAR(64) COMMENT '培训讲师',
  education_status VARCHAR(32) DEFAULT 'NOT_STARTED' COMMENT '教育状态',
  remark VARCHAR(255),
  create_user_id BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT DEFAULT 0
);
```

教育状态：

```text
NOT_STARTED   未开始
IN_PROGRESS   进行中
COMPLETED     已完成
ARCHIVED      已归档
```

------

## 4.9 安全教育人员关联表 safety_education_person

```sql
CREATE TABLE safety_education_person (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL COMMENT '教育批次ID',
  person_id BIGINT NOT NULL COMMENT '临时人员ID',
  education_result VARCHAR(32) DEFAULT 'UNFINISHED' COMMENT '教育结果',
  sign_status VARCHAR(32) DEFAULT 'UNSIGNED' COMMENT '签字状态',
  complete_time DATETIME COMMENT '完成时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

------

## 4.10 文件资料表 file_resource

```sql
CREATE TABLE file_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
  business_id BIGINT COMMENT '关联业务ID',
  file_name VARCHAR(255) NOT NULL COMMENT '原文件名',
  file_suffix VARCHAR(32) COMMENT '文件后缀',
  file_category VARCHAR(64) COMMENT '资料分类',
  file_url VARCHAR(500) NOT NULL COMMENT '文件地址',
  file_size BIGINT COMMENT '文件大小',
  storage_type VARCHAR(32) DEFAULT 'OSS' COMMENT '存储方式',
  file_status VARCHAR(32) DEFAULT 'UPLOADED' COMMENT '文件状态',
  upload_user_id BIGINT COMMENT '上传人ID',
  upload_user_name VARCHAR(64) COMMENT '上传人姓名',
  upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  archive_time DATETIME COMMENT '归档时间',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0
);
```

business_type：

```text
PROJECT_DOC         项目资料
SAFETY_TRAINING     安全培训资料
SIGN_FILE           签字文件
CERTIFICATE         证书文件
MEETING_MINUTES     会议纪要
CONSTRUCTION_LOG    施工日志
OTHER               其他
```

file_status：

```text
UPLOADED       已上传
WAIT_CONFIRM   待确认
ARCHIVED       已归档
DELETED        已删除
```

索引：

```sql
CREATE INDEX idx_file_project ON file_resource(project_id);
CREATE INDEX idx_file_business ON file_resource(business_type, business_id);
CREATE INDEX idx_file_category ON file_resource(file_category);
CREATE INDEX idx_file_status ON file_resource(file_status);
CREATE INDEX idx_file_upload_time ON file_resource(upload_time);
```

------

## 4.11 摄像头资源表 camera_resource

```sql
CREATE TABLE camera_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  camera_code VARCHAR(128) NOT NULL COMMENT '摄像头编号',
  camera_name VARCHAR(128) NOT NULL COMMENT '摄像头名称',
  area_name VARCHAR(128) COMMENT '所属区域',
  vendor VARCHAR(64) DEFAULT 'HIKVISION' COMMENT '厂商',
  stream_type VARCHAR(32) COMMENT '视频流类型',
  stream_url VARCHAR(500) COMMENT '视频流地址或第三方标识',
  online_status VARCHAR(32) DEFAULT 'UNKNOWN' COMMENT '在线状态',
  sort_order INT DEFAULT 0,
  last_online_time DATETIME COMMENT '最近在线时间',
  remark VARCHAR(255),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT DEFAULT 0
);
```

online_status：

```text
ONLINE     在线
OFFLINE    离线
UNKNOWN    未知
```

索引：

```sql
CREATE INDEX idx_camera_project ON camera_resource(project_id);
CREATE INDEX idx_camera_code ON camera_resource(camera_code);
CREATE INDEX idx_camera_status ON camera_resource(online_status);
```

------

## 4.12 视频布局配置表 video_layout_config

```sql
CREATE TABLE video_layout_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  layout_type VARCHAR(16) NOT NULL COMMENT '布局类型：1/4/8/16',
  window_index INT NOT NULL COMMENT '窗口序号',
  camera_id BIGINT COMMENT '摄像头ID',
  is_default TINYINT DEFAULT 0 COMMENT '是否默认',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

------

## 4.13 设备信息表 device_info

```sql
CREATE TABLE device_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  device_code VARCHAR(128) NOT NULL COMMENT '设备编号',
  device_name VARCHAR(128) NOT NULL COMMENT '设备名称',
  device_type VARCHAR(64) NOT NULL COMMENT '设备类型',
  vendor VARCHAR(64) COMMENT '厂商',
  area_name VARCHAR(128) COMMENT '所属区域',
  device_status VARCHAR(32) DEFAULT 'UNKNOWN' COMMENT '设备状态',
  last_report_time DATETIME COMMENT '最近上报时间',
  remark VARCHAR(255),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT DEFAULT 0
);
```

device_type：

```text
TOWER_CRANE     塔吊
ELEVATOR        人货梯
ROBOT           施工机器人
OTHER           其他
```

device_status：

```text
RUNNING     运行中
STOPPED     停机
ALARM       异常
OFFLINE     离线
UNKNOWN     未知
```

------

## 4.14 设备状态记录表 device_status_record

```sql
CREATE TABLE device_status_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  status_desc VARCHAR(255),
  raw_data TEXT COMMENT '第三方原始数据',
  report_time DATETIME NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

索引：

```sql
CREATE INDEX idx_device_record_project ON device_status_record(project_id);
CREATE INDEX idx_device_record_device ON device_status_record(device_id);
CREATE INDEX idx_device_record_time ON device_status_record(report_time);
```

------

## 4.15 视频访问日志表 video_access_log

```sql
CREATE TABLE video_access_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  camera_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  username VARCHAR(64),
  access_type VARCHAR(32) COMMENT '播放/刷新/全屏',
  access_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  request_ip VARCHAR(64)
);
```

------

## 4.16 操作日志表 sys_operation_log

```sql
CREATE TABLE sys_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  username VARCHAR(64),
  module_name VARCHAR(64),
  operation_type VARCHAR(64),
  operation_content TEXT,
  request_ip VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

------

# 5. 接口统一规范

## 5.1 接口前缀

```text
/api/v1
```

## 5.2 返回格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 5.3 分页返回格式

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "pageNo": 1,
    "pageSize": 10,
    "total": 100,
    "records": []
  }
}
```

## 5.4 错误码

```text
200 成功
400 参数错误
401 未登录
403 无权限
404 数据不存在
500 系统异常
```

------

# 6. 项目概况接口

------

## 6.1 获取当前用户可访问项目

```http
GET /api/v1/projects
```

请求参数：

| 参数    | 必填 | 说明           |
| ------- | ---- | -------------- |
| keyword | 否   | 项目名称关键字 |
| status  | 否   | 项目状态       |

返回：

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "projectCode": "P001",
      "projectName": "测试项目一",
      "projectStatus": "NORMAL",
      "currentStage": "施工中"
    }
  ]
}
```

后端要求：

```text
只能返回当前用户有权限访问的项目。
```

------

## 6.2 获取项目详情

```http
GET /api/v1/projects/{projectId}
```

返回：

```json
{
  "id": 1,
  "projectName": "测试项目一",
  "description": "项目简介",
  "buildingArea": 20000,
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "currentStage": "施工中",
  "safetyGoal": "零事故",
  "qualityGoal": "合格",
  "projectStatus": "NORMAL"
}
```

后端要求：

```text
必须校验当前用户是否有 projectId 权限。
```

------

## 6.3 获取项目概况聚合数据

```http
GET /api/v1/dashboard/project-overview?projectId=1
```

返回：

```json
{
  "project": {
    "projectName": "测试项目一",
    "currentStage": "施工中",
    "projectStatus": "NORMAL"
  },
  "fileSummary": {
    "total": 120,
    "uploaded": 80,
    "archived": 30,
    "waitConfirm": 10
  },
  "cameraSummary": {
    "total": 32,
    "online": 28,
    "offline": 4
  },
  "personSummary": {
    "temporaryPersonTotal": 300,
    "waitEducation": 20,
    "educated": 280
  },
  "deviceSummary": {
    "total": 10,
    "running": 8,
    "alarm": 1,
    "offline": 1
  }
}
```

用途：

```text
减少前端打开首页时的接口数量。
```

------

## 6.4 获取原人员管理系统入口

```http
GET /api/v1/external-systems/personnel?projectId=1
```

返回：

```json
{
  "systemName": "原人员管理系统",
  "systemUrl": "https://xxx.com/personnel",
  "openType": "NEW_WINDOW",
  "status": 1
}
```

------

# 7. 资料管理接口

------

## 7.1 文件上传

```http
POST /api/v1/files/upload
Content-Type: multipart/form-data
```

请求参数：

| 参数         | 必填 | 说明       |
| ------------ | ---- | ---------- |
| projectId    | 是   | 项目ID     |
| businessType | 是   | 业务类型   |
| businessId   | 否   | 关联业务ID |
| fileCategory | 是   | 文件分类   |
| remark       | 否   | 备注       |
| file         | 是   | 文件       |

返回：

```json
{
  "id": 1001,
  "fileName": "三级教育签字表.pdf",
  "fileUrl": "/files/xxx.pdf",
  "fileStatus": "UPLOADED"
}
```

上传限制：

```text
单文件最大 50MB
支持 PDF、Word、Excel、JPG、PNG、ZIP
```

------

## 7.2 文件分页查询

```http
GET /api/v1/files
```

请求参数：

| 参数         | 必填 | 说明     |
| ------------ | ---- | -------- |
| projectId    | 是   | 项目ID   |
| businessType | 否   | 业务类型 |
| fileCategory | 否   | 文件分类 |
| fileStatus   | 否   | 文件状态 |
| keyword      | 否   | 文件名称 |
| startTime    | 否   | 开始时间 |
| endTime      | 否   | 结束时间 |
| pageNo       | 是   | 页码     |
| pageSize     | 是   | 每页数量 |

------

## 7.3 文件下载

```http
GET /api/v1/files/{fileId}/download
```

后端要求：

```text
下载前必须校验用户项目权限。
记录下载日志。
```

------

## 7.4 文件归档

```http
PUT /api/v1/files/{fileId}/archive
```

处理逻辑：

```text
file_status 从 UPLOADED 或 WAIT_CONFIRM 更新为 ARCHIVED。
记录 archive_time。
```

------

## 7.5 文件删除

```http
DELETE /api/v1/files/{fileId}
```

处理逻辑：

```text
逻辑删除。
is_deleted = 1。
```

------

# 8. 人员与安全接口

------

## 8.1 临时人员分页查询

```http
GET /api/v1/temporary-persons
```

请求参数：

| 参数         | 必填 | 说明               |
| ------------ | ---- | ------------------ |
| projectId    | 是   | 项目ID             |
| keyword      | 否   | 姓名/手机号/身份证 |
| personStatus | 否   | 人员状态           |
| workType     | 否   | 工种               |
| pageNo       | 是   | 页码               |
| pageSize     | 是   | 每页数量           |

返回字段：

```json
{
  "id": 1,
  "name": "张三",
  "gender": "男",
  "phone": "13800000000",
  "companyName": "某施工单位",
  "workType": "电工",
  "entryTime": "2026-04-24 10:00:00",
  "personStatus": "WAIT_EDUCATION"
}
```

------

## 8.2 新增临时人员

```http
POST /api/v1/temporary-persons
```

请求体：

```json
{
  "projectId": 1,
  "name": "张三",
  "gender": "男",
  "idCard": "310xxxxxxxxxxxxxxx",
  "phone": "13800000000",
  "companyName": "某施工单位",
  "workType": "电工",
  "entryTime": "2026-04-24 10:00:00",
  "remark": "临时进场"
}
```

校验规则：

```text
姓名必填。
projectId 必填。
身份证号如填写需校验格式。
手机号如填写需校验格式。
```

------

## 8.3 编辑临时人员

```http
PUT /api/v1/temporary-persons/{personId}
```

------

## 8.4 删除临时人员

```http
DELETE /api/v1/temporary-persons/{personId}
```

逻辑删除：

```text
is_deleted = 1
```

------

## 8.5 临时人员状态变更

```http
PUT /api/v1/temporary-persons/{personId}/status
```

请求体：

```json
{
  "personStatus": "EDUCATED"
}
```

------

## 8.6 新建安全教育批次

```http
POST /api/v1/safety-education/batches
```

请求体：

```json
{
  "projectId": 1,
  "batchName": "2026年4月临时人员三级教育",
  "educationType": "THREE_LEVEL",
  "trainingTime": "2026-04-24 14:00:00",
  "trainingLocation": "项目部会议室",
  "trainer": "安全员王工",
  "personIds": [1, 2, 3],
  "remark": "临时进场人员培训"
}
```

处理逻辑：

```text
1. 新增 safety_education_batch。
2. 批量新增 safety_education_person。
3. 被关联人员状态可保持 WAIT_EDUCATION，完成后再变更。
```

------

## 8.7 查询安全教育批次

```http
GET /api/v1/safety-education/batches
```

请求参数：

| 参数            | 必填 | 说明         |
| --------------- | ---- | ------------ |
| projectId       | 是   | 项目ID       |
| keyword         | 否   | 批次名称     |
| educationStatus | 否   | 状态         |
| startTime       | 否   | 培训开始时间 |
| endTime         | 否   | 培训结束时间 |
| pageNo          | 是   | 页码         |
| pageSize        | 是   | 每页数量     |

------

## 8.8 查看安全教育详情

```http
GET /api/v1/safety-education/batches/{batchId}
```

返回内容：

```json
{
  "batchInfo": {},
  "persons": [],
  "files": []
}
```

------

## 8.9 标记教育完成

```http
PUT /api/v1/safety-education/batches/{batchId}/complete
```

处理逻辑：

```text
1. 更新批次状态为 COMPLETED。
2. 更新关联人员 education_result 为 FINISHED。
3. 更新临时人员 person_status 为 EDUCATED。
4. 写入完成时间 complete_time。
```

------

## 8.10 上传教育文件

```http
POST /api/v1/safety-education/batches/{batchId}/files
Content-Type: multipart/form-data
```

参数：

| 参数     | 必填 | 说明                                    |
| -------- | ---- | --------------------------------------- |
| fileType | 是   | TRAINING_FILE / SIGN_FILE / CERTIFICATE |
| file     | 是   | 文件                                    |

处理逻辑：

```text
调用统一文件上传服务。
businessType 根据 fileType 写入。
businessId 写 batchId。
```

------

# 9. 设备与监控接口

------

## 9.1 摄像头列表查询

```http
GET /api/v1/cameras
```

请求参数：

| 参数         | 必填 | 说明     |
| ------------ | ---- | -------- |
| projectId    | 是   | 项目ID   |
| onlineStatus | 否   | 在线状态 |
| areaName     | 否   | 区域     |
| pageNo       | 否   | 页码     |
| pageSize     | 否   | 每页数量 |

------

## 9.2 获取摄像头播放地址

```http
GET /api/v1/cameras/{cameraId}/play-url
```

返回：

```json
{
  "cameraId": 1,
  "cameraName": "东门摄像头",
  "playUrl": "https://video.xxx.com/live/xxx.flv",
  "streamType": "FLV",
  "expireTime": "2026-04-24 18:00:00"
}
```

处理逻辑：

```text
1. 校验用户是否有该摄像头所属项目权限。
2. 查询摄像头信息。
3. 调用海康适配器获取播放地址。
4. 写入 video_access_log。
5. 返回播放地址。
```

------

## 9.3 获取视频布局

```http
GET /api/v1/video-layouts
```

请求参数：

| 参数       | 必填 | 说明     |
| ---------- | ---- | -------- |
| projectId  | 是   | 项目ID   |
| layoutType | 是   | 1/4/8/16 |

返回：

```json
{
  "layoutType": "4",
  "windows": [
    {
      "windowIndex": 1,
      "cameraId": 1,
      "cameraName": "东门摄像头"
    }
  ]
}
```

------

## 9.4 保存视频布局

```http
POST /api/v1/video-layouts
```

请求体：

```json
{
  "projectId": 1,
  "layoutType": "4",
  "windows": [
    {
      "windowIndex": 1,
      "cameraId": 1
    },
    {
      "windowIndex": 2,
      "cameraId": 2
    }
  ]
}
```

处理逻辑：

```text
删除原布局配置。
重新写入新布局配置。
```

------

## 9.5 获取设备列表

```http
GET /api/v1/devices
```

请求参数：

| 参数         | 必填 | 说明          |
| ------------ | ---- | ------------- |
| projectId    | 是   | 项目ID        |
| deviceType   | 否   | 设备类型      |
| deviceStatus | 否   | 设备状态      |
| keyword      | 否   | 设备名称/编号 |
| pageNo       | 是   | 页码          |
| pageSize     | 是   | 每页数量      |

------

## 9.6 获取设备详情

```http
GET /api/v1/devices/{deviceId}
```

------

## 9.7 获取设备状态记录

```http
GET /api/v1/devices/{deviceId}/status-records
```

请求参数：

| 参数      | 必填 | 说明     |
| --------- | ---- | -------- |
| startTime | 否   | 开始时间 |
| endTime   | 否   | 结束时间 |
| pageNo    | 是   | 页码     |
| pageSize  | 是   | 每页数量 |

------

## 9.8 获取塔吊数据

```http
GET /api/v1/devices/tower-cranes
```

请求参数：

| 参数      | 必填 | 说明   |
| --------- | ---- | ------ |
| projectId | 是   | 项目ID |

返回：

```json
[
  {
    "deviceId": 1,
    "deviceName": "1号塔吊",
    "deviceCode": "TD001",
    "deviceStatus": "RUNNING",
    "lastReportTime": "2026-04-24 15:30:00"
  }
]
```

------

# 10. 视频接入实施要求

## 10.1 视频接入原则

本系统后端不直接转发视频流。

正确方式：

```text
后端负责：
1. 管理摄像头资源
2. 校验权限
3. 获取播放地址
4. 返回前端播放信息

视频流由：
海康平台 / 视频网关 / 流媒体服务 承载
```

禁止方式：

```text
摄像头视频流 → 业务后端 → 前端
```

原因：

```text
50+项目并发时，业务后端无法承载大量视频流。
```

------

## 10.2 海康适配器设计

后端需要新增：

```text
HikvisionAdapter
```

接口定义：

```java
public interface HikvisionAdapter {
    VideoPlayUrlDTO getPlayUrl(String cameraCode);
    CameraStatusDTO getCameraStatus(String cameraCode);
}
```

当前阶段可以先实现 Mock：

```text
返回模拟播放地址。
等海康接口确认后再替换真实实现。
```

------

# 11. 高并发设计要求

## 11.1 必须使用 Redis 的数据

```text
1. 用户登录 Token
2. 用户项目权限
3. 项目基础信息
4. 摄像头列表
5. 设备最新状态
6. 字典数据
7. 视频播放地址短期缓存
```

## 11.2 缓存 Key 设计

```text
user:permission:{userId}
project:info:{projectId}
project:cameras:{projectId}
device:latest:{projectId}
video:playurl:{cameraId}
```

## 11.3 缓存时间

| 数据         | 时间         |
| ------------ | ------------ |
| 项目信息     | 10分钟       |
| 用户权限     | 登录周期     |
| 摄像头列表   | 1分钟        |
| 设备状态     | 30秒         |
| 视频播放地址 | 按实际有效期 |

------

# 12. 文件存储实施要求

## 12.1 存储方式

正式环境必须使用：

```text
MinIO 或 云对象存储
```

应用服务器只保存文件元数据。

## 12.2 文件路径规则

```text
/files/{projectId}/{businessType}/{yyyyMMdd}/{uuid}_{originalFileName}
```

示例：

```text
/files/1/SAFETY_TRAINING/20260424/uuid_三级教育签字表.pdf
```

------

# 13. 权限控制要求

## 13.1 用户必须绑定项目权限

任何涉及 projectId 的接口，后端都必须执行：

```text
当前用户是否拥有该 projectId 权限
```

## 13.2 操作权限

| 操作         | 权限              |
| ------------ | ----------------- |
| 查看项目     | project:view      |
| 上传文件     | file:upload       |
| 删除文件     | file:delete       |
| 归档文件     | file:archive      |
| 新增临时人员 | tempPerson:add    |
| 编辑临时人员 | tempPerson:edit   |
| 删除临时人员 | tempPerson:delete |
| 新建安全教育 | safety:add        |
| 查看视频     | camera:view       |
| 修改视频布局 | camera:layout     |
| 查看设备     | device:view       |

------

# 14. 日志要求

必须记录以下日志：

```text
1. 登录日志
2. 项目查看日志
3. 文件上传日志
4. 文件下载日志
5. 文件删除日志
6. 文件归档日志
7. 临时人员新增/编辑/删除日志
8. 安全教育创建/完成日志
9. 视频播放访问日志
10. 设备详情查看日志
11. 第三方接口调用异常日志
```

------

# 15. 开发任务拆分

## 第一阶段：基础能力

```text
1. 项目初始化
2. 用户登录
3. JWT认证
4. 用户角色权限
5. 用户项目权限
6. 项目管理接口
7. 操作日志
```

## 第二阶段：项目概况

```text
1. 项目详情接口
2. 项目概况聚合接口
3. 原人员管理系统入口接口
4. 资料上传
5. 资料查询
6. 资料下载
7. 资料归档
```

## 第三阶段：人员与安全

```text
1. 临时人员新增
2. 临时人员编辑
3. 临时人员查询
4. 临时人员删除
5. 安全教育批次新增
6. 安全教育人员关联
7. 安全教育完成
8. 教育文件上传
```

## 第四阶段：设备与监控

```text
1. 摄像头资源管理
2. 摄像头列表查询
3. 视频播放地址接口
4. 视频布局查询
5. 视频布局保存
6. 设备列表
7. 设备详情
8. 塔吊数据接口
```

## 第五阶段：高并发与云部署

```text
1. Redis缓存接入
2. 文件对象存储接入
3. RabbitMQ日志异步写入
4. Nginx配置
5. Docker部署
6. 多实例部署测试
7. 接口压测
8. 视频播放地址并发测试
```

------

# 16. 验收标准

## 16.1 功能验收

```text
1. 用户可登录系统。
2. 用户只能查看授权项目。
3. 项目概况页可加载项目详情和聚合数据。
4. 原人员管理系统入口可返回并跳转。
5. 资料可上传、查询、下载、归档、删除。
6. 临时人员可新增、编辑、查询、删除。
7. 安全教育批次可创建、查询、完成。
8. 安全教育可上传培训资料和签字文件。
9. 摄像头可按项目查询。
10. 视频播放地址可返回。
11. 视频布局可保存和查询。
12. 设备列表和塔吊数据可查询。
```

## 16.2 性能验收

```text
1. 支持 50 个以上项目数据管理。
2. 普通查询接口响应时间 ≤ 1秒。
3. 项目概况聚合接口响应时间 ≤ 2秒。
4. 分页查询必须稳定返回。
5. 文件上传失败需有明确提示。
6. 视频播放地址接口响应时间 ≤ 1秒。
7. 第三方接口异常时主系统不崩溃。
8. 多实例部署时用户登录状态正常。
```

## 16.3 安全验收

```text
1. 未登录用户不能访问接口。
2. 无项目权限用户不能访问项目数据。
3. 无权限用户不能上传、删除、归档文件。
4. 视频播放地址获取必须校验项目权限。
5. 文件下载必须校验项目权限。
```

------

# 17. 最终交付物

后端开发最终需要交付：

```text
1. 后端源码
2. 数据库建表SQL
3. 接口文档 Swagger / Knife4j
4. 后端部署文档
5. Nginx配置说明
6. Redis配置说明
7. 文件存储配置说明
8. 第三方系统接入说明
9. 测试账号与权限说明
10. 接口联调说明
```

------

# 18. 最终实施结论

本系统后端按照以下原则实施：

```text
1. 三个页面对应三个核心业务域。
2. 所有业务数据必须绑定 project_id。
3. 所有接口必须做用户权限和项目权限校验。
4. 文件存储必须使用对象存储，不依赖应用服务器本地磁盘。
5. 视频流不经过业务后端，后端只返回播放地址或播放令牌。
6. 摄像头、设备、第三方系统统一通过接入层封装。
7. 高频数据使用 Redis 缓存。
8. 日志和第三方同步任务使用异步机制。
9. 系统部署时支持多实例横向扩展。
```

这一版可以直接作为后端开发依据。