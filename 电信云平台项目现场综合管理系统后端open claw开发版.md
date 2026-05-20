------

# 电信云平台项目现场综合管理系统 open claw开发版

## 后端开发执行说明书

### OpenClaw 执行版

## 一、开发目标

请基于以下需求开发一个后端系统，系统名称为：

**电信云平台项目现场综合管理系统**

本系统用于支持前端 3 个页面：

```text
1. 项目概况
2. 人员与安全
3. 设备与监控
```

系统需要支持：

```text
1. 多项目管理
2. 用户登录与权限控制
3. 用户可访问项目权限控制
4. 项目基础信息管理
5. 原人员管理系统跳转入口配置
6. 临时人员管理
7. 安全三级教育管理
8. 文件上传、下载、查询、归档、删除
9. 摄像头资源管理
10. 视频播放地址获取接口
11. 视频窗口布局配置
12. 设备基础信息管理
13. 塔吊设备数据接口预留
14. 操作日志记录
15. Redis 缓存预留
16. 云服务器多实例部署预留
```

------

# 二、技术栈要求

后端采用以下技术栈：

```text
Java 17
Spring Boot 3.x
MyBatis-Plus
MySQL 8.x
Redis
MinIO 或本地文件存储抽象接口
JWT 登录认证
Swagger / Knife4j 接口文档
Maven
```

本期可以先做 **模块化单体架构**，不要直接拆微服务。

------

# 三、项目结构要求

请按以下包结构创建后端项目：

```text
src/main/java/com/example/siteplatform
├── auth                登录认证、JWT、用户权限
├── project             项目管理
├── external            外部系统入口
├── person              临时人员管理
├── safety              安全三级教育
├── file                文件资料管理
├── camera              摄像头资源与视频播放地址
├── videolayout         视频窗口布局
├── device              设备与塔吊管理
├── log                 操作日志
├── common              公共响应、异常、工具类
└── config              系统配置
```

------

# 四、重要开发边界

## 1. 海康视频不要直接真实接入

当前阶段不要直接写死真实海康接口。

请先实现：

```text
HikvisionAdapter 接口
MockHikvisionAdapter 模拟实现
```

后端只需要返回模拟播放地址，例如：

```json
{
  "cameraId": 1,
  "cameraName": "东门摄像头",
  "playUrl": "https://example.com/mock/live.flv",
  "streamType": "FLV",
  "expireTime": "2026-12-31 23:59:59"
}
```

后续真实海康接入时，再替换 Adapter 实现。

------

## 2. 视频流不能经过业务后端

禁止实现这种方式：

```text
摄像头视频流 → 业务后端 → 前端
```

正确方式是：

```text
后端校验权限
→ 后端返回播放地址或播放令牌
→ 前端播放器直接拉取视频流
```

------

## 3. 塔吊系统先做接口预留

当前阶段不要真实对接塔吊系统。

请先实现：

```text
TowerCraneAdapter 接口
MockTowerCraneAdapter 模拟实现
```

设备管理页面可以先返回模拟塔吊状态数据。

------

## 4. 所有业务数据必须绑定 project_id

以下所有业务表必须包含 `project_id`：

```text
临时人员
安全教育批次
文件资料
摄像头
视频布局
设备
设备状态记录
视频访问日志
```

------

## 5. 所有涉及 projectId 的接口必须校验项目权限

不能只依赖前端传入 `projectId`。

后端必须校验：

```text
当前登录用户是否拥有该 projectId 的访问权限
```

------

# 五、数据库表要求

请创建以下数据库表，并提供完整 SQL 文件：

```text
project_info                  项目表
sys_user                      用户表
sys_role                      角色表
sys_user_role                 用户角色表
sys_user_project              用户项目权限表
external_system_config        外部系统入口配置表
temporary_person              临时人员表
safety_education_batch        安全教育批次表
safety_education_person       安全教育人员关联表
file_resource                 文件资料表
camera_resource               摄像头资源表
video_layout_config           视频窗口布局表
device_info                   设备信息表
device_status_record          设备状态记录表
video_access_log              视频访问日志表
sys_operation_log             操作日志表
```

------

# 六、接口统一规范

## 1. 接口前缀

```text
/api/v1
```

## 2. 统一返回格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 3. 分页返回格式

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

------

# 七、必须实现的接口清单

## 1. 登录认证

```http
POST /api/v1/auth/login
GET  /api/v1/auth/user-info
POST /api/v1/auth/logout
```

------

## 2. 项目概况

```http
GET /api/v1/projects
GET /api/v1/projects/{projectId}
GET /api/v1/dashboard/project-overview?projectId=1
GET /api/v1/external-systems/personnel?projectId=1
```

------

## 3. 文件资料

```http
POST   /api/v1/files/upload
GET    /api/v1/files
GET    /api/v1/files/{fileId}/download
PUT    /api/v1/files/{fileId}/archive
DELETE /api/v1/files/{fileId}
```

------

## 4. 临时人员

```http
GET    /api/v1/temporary-persons
POST   /api/v1/temporary-persons
PUT    /api/v1/temporary-persons/{personId}
DELETE /api/v1/temporary-persons/{personId}
GET    /api/v1/temporary-persons/{personId}
PUT    /api/v1/temporary-persons/{personId}/status
```

------

## 5. 安全三级教育

```http
GET  /api/v1/safety-education/batches
POST /api/v1/safety-education/batches
GET  /api/v1/safety-education/batches/{batchId}
PUT  /api/v1/safety-education/batches/{batchId}/complete
POST /api/v1/safety-education/batches/{batchId}/files
GET  /api/v1/safety-education/batches/{batchId}/files
```

------

## 6. 摄像头与视频

```http
GET  /api/v1/cameras
GET  /api/v1/cameras/{cameraId}/play-url
GET  /api/v1/video-layouts
POST /api/v1/video-layouts
```

------

## 7. 设备与塔吊

```http
GET /api/v1/devices
GET /api/v1/devices/{deviceId}
GET /api/v1/devices/{deviceId}/status-records
GET /api/v1/devices/tower-cranes
```

------

# 八、接口业务要求

## 1. 项目列表接口

```http
GET /api/v1/projects
```

要求：

```text
只返回当前登录用户有权限访问的项目。
```

------

## 2. 项目概况聚合接口

```http
GET /api/v1/dashboard/project-overview?projectId=1
```

需要返回：

```text
项目基础信息
资料统计
摄像头统计
临时人员统计
安全教育统计
设备统计
```

目的：

```text
减少前端首页一次性请求多个接口造成的压力。
```

------

## 3. 文件上传接口

```http
POST /api/v1/files/upload
```

要求：

```text
支持 multipart/form-data
支持 PDF、Word、Excel、JPG、PNG、ZIP
单文件大小默认限制 50MB
文件保存后写入 file_resource 表
文件必须绑定 project_id
上传前必须校验项目权限
```

------

## 4. 临时人员新增接口

```http
POST /api/v1/temporary-persons
```

要求：

```text
姓名必填
projectId 必填
手机号格式校验
身份证号格式校验
默认状态为 WAIT_EDUCATION
```

------

## 5. 安全教育完成接口

```http
PUT /api/v1/safety-education/batches/{batchId}/complete
```

处理逻辑：

```text
1. 更新 safety_education_batch 状态为 COMPLETED
2. 更新 safety_education_person 中关联人员为 FINISHED
3. 更新 temporary_person 状态为 EDUCATED
4. 记录完成时间
5. 写入操作日志
```

------

## 6. 视频播放地址接口

```http
GET /api/v1/cameras/{cameraId}/play-url
```

处理逻辑：

```text
1. 查询摄像头信息
2. 校验当前用户是否有摄像头所属项目权限
3. 调用 HikvisionAdapter 获取播放地址
4. 写入 video_access_log
5. 返回播放地址
```

------

# 九、缓存要求

请预留 Redis 使用，至少实现以下缓存 Key：

```text
user:permission:{userId}
project:info:{projectId}
project:cameras:{projectId}
device:latest:{projectId}
video:playurl:{cameraId}
```

当前阶段可以先实现项目基础信息、摄像头列表缓存。

------

# 十、文件存储要求

本期文件存储需要抽象成接口：

```java
public interface FileStorageService {
    FileUploadResult upload(MultipartFile file, String path);
    InputStream download(String filePath);
    void delete(String filePath);
}
```

先实现：

```text
LocalFileStorageService
```

并预留：

```text
MinioFileStorageService
```

正式云服务器部署时切换 MinIO 或对象存储。

------

# 十一、操作日志要求

以下操作必须写入操作日志：

```text
登录
新增临时人员
编辑临时人员
删除临时人员
新建安全教育批次
完成安全教育
上传文件
下载文件
删除文件
归档文件
获取视频播放地址
保存视频布局
查看设备详情
```

日志表：

```text
sys_operation_log
```

------

# 十二、异常处理要求

请实现全局异常处理。

错误码：

```text
200 成功
400 参数错误
401 未登录
403 无权限
404 数据不存在
500 系统异常
```

示例：

```json
{
  "code": 403,
  "message": "无项目访问权限",
  "data": null
}
```

------

# 十三、开发阶段要求

不要一次性全部写完。
请按以下阶段逐步开发，每个阶段完成后保证项目可运行。

## 第一阶段：基础框架

完成：

```text
Spring Boot 项目初始化
统一返回格式
全局异常处理
数据库连接
MyBatis-Plus 配置
Swagger / Knife4j
用户登录
JWT 认证
用户项目权限校验
```

------

## 第二阶段：项目与资料

完成：

```text
项目表
项目列表接口
项目详情接口
项目概况聚合接口
外部人员系统入口接口
文件上传
文件查询
文件下载
文件归档
文件删除
```

------

## 第三阶段：人员与安全

完成：

```text
临时人员新增
临时人员编辑
临时人员查询
临时人员删除
临时人员状态变更
安全教育批次创建
安全教育人员关联
安全教育完成
教育文件上传
```

------

## 第四阶段：设备与监控

完成：

```text
摄像头资源管理
摄像头列表查询
模拟视频播放地址接口
视频布局查询
视频布局保存
设备列表
设备详情
塔吊模拟数据接口
```

------

## 第五阶段：高并发与云部署预留

完成：

```text
Redis 缓存
文件存储抽象
MinIO 预留实现
操作日志
视频访问日志
Dockerfile
Nginx 反向代理说明
部署文档
```

------

# 十四、禁止事项

OpenClaw 开发时必须注意以下事项：

```text
1. 不要把视频流通过业务后端转发。
2. 不要把海康接口写死，必须使用 HikvisionAdapter。
3. 不要把塔吊接口写死，必须使用 TowerCraneAdapter。
4. 不要忽略 project_id 权限校验。
5. 不要把文件直接存数据库。
6. 不要只做本地文件存储，必须预留对象存储接口。
7. 不要一次性生成所有代码后不验证。
8. 不要省略 Swagger 接口文档。
9. 不要省略数据库建表 SQL。
10. 不要把账号密码、密钥写死在代码里。
```

------

# 十五、交付物要求

开发完成后需要输出：

```text
1. 后端源码
2. 数据库建表 SQL
3. 初始测试数据 SQL
4. Swagger / Knife4j 接口文档
5. application.yml 配置文件示例
6. Dockerfile
7. README 部署说明
8. 接口联调说明
9. Mock 视频地址说明
10. Mock 塔吊数据说明
```

------

# 十六、你给 OpenClaw 的总提示词

你可以直接这样发给 OpenClaw：

```text
你是一个资深 Java 后端工程师。请根据我提供的《电信云平台项目现场综合管理系统后端开发执行说明书》开发一个 Spring Boot 3.x 后端项目。

要求：
1. 使用 Java 17、Spring Boot 3.x、MyBatis-Plus、MySQL、Redis、JWT、Swagger/Knife4j。
2. 采用模块化单体架构。
3. 按 auth、project、external、person、safety、file、camera、videolayout、device、log、common、config 分包。
4. 先不要真实对接海康和塔吊系统，分别使用 HikvisionAdapter 和 TowerCraneAdapter 做接口抽象，并提供 Mock 实现。
5. 所有涉及 projectId 的接口必须做当前用户项目权限校验。
6. 文件上传先支持本地存储，但必须通过 FileStorageService 抽象，预留 MinIO 实现。
7. 视频流不能经过业务后端，后端只返回播放地址。
8. 请先完成基础框架、数据库 SQL、实体类、Mapper、Service、Controller、统一返回、全局异常、Swagger 文档。
9. 请分阶段开发，每完成一个阶段保证项目可以启动运行。
10. 最终输出完整源码、建表 SQL、测试数据 SQL、README 部署说明。
```

------

# 十七、我建议你实际操作时这样用 OpenClaw

你不要一次性让它做完整系统。
你应该按阶段下命令。

## 第一次给它的任务

```text
先根据后端开发执行说明书，创建 Spring Boot 3.x 项目基础结构，完成统一返回格式、全局异常处理、MyBatis-Plus 配置、Swagger/Knife4j 配置、数据库建表 SQL、基础实体类和包结构。暂时不要实现具体业务逻辑。
```

## 第二次任务

```text
继续实现登录认证、JWT、用户表、角色表、用户项目权限表，并实现项目权限校验工具类。所有涉及 projectId 的接口后续都要调用该权限校验。
```

## 第三次任务

```text
继续实现项目管理和项目概况聚合接口，包括项目列表、项目详情、项目概况 dashboard 聚合数据、原人员管理系统入口接口。
```

## 第四次任务

```text
继续实现文件资料模块，包括 FileStorageService 抽象、本地文件存储实现、文件上传、文件查询、文件下载、文件归档、文件删除。
```

## 第五次任务

```text
继续实现临时人员管理和安全三级教育模块，包括临时人员 CRUD、安全教育批次创建、人员关联、教育完成、教育文件上传。
```

## 第六次任务

```text
继续实现摄像头资源、视频布局、Mock 视频播放地址、设备管理和 Mock 塔吊数据接口。注意视频流不能经过业务后端。
```

------

# 十八、最终建议

你现在可以用我上面这版 **OpenClaw 执行版** 去开发。
重点是：**不要一次性让它把整个系统写完**。

你要按阶段推进：

```text
先框架
再权限
再项目
再文件
再人员安全
再设备视频
最后部署优化
```

这样 OpenClaw 出来的代码才比较稳，也方便你每一步检查。