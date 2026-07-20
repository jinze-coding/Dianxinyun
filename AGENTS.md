# AGENTS.md

本文件给 Codex / AI 编程助手使用。进入本仓库后，先阅读本文件，再阅读 `docs/新对话交接文档.md` 和相关专题文档。

## 项目简介

项目名称：电信云平台项目现场综合管理系统。

系统定位：面向多个建设项目现场的综合管理平台，承载地图总览、项目概况、临时人员与安全教育、资料管理、摄像头与设备监控等能力。

当前阶段：前后端主体已搭建，部分业务接口已接入真实后端，部分页面仍保留 mock 数据或前端内存状态。权限和项目隔离尚未全部完成。

## 技术栈

后端：

- Java 17
- Spring Boot 3.2.5
- MyBatis-Plus 3.5.6
- MySQL 8.x
- Redis
- JWT：`io.jsonwebtoken`
- Knife4j / OpenAPI
- Lombok

前端：

- React 18
- Vite 5
- axios
- zustand 当前存在但主入口未大量使用
- 百度地图 JavaScript API GL，通过 `VITE_BAIDU_MAP_AK` 配置

本地默认端口：

- 前端：`http://localhost:3002`
- 后端：`http://localhost:8080`
- 后端接口文档：`http://localhost:8080/doc.html`

## 目录结构说明

```text
backend/
  pom.xml
  src/main/resources/application.yml
  src/main/resources/sql/init.sql
  src/main/java/com/example/siteplatform/
    auth/       登录、JWT、用户实体、角色查询
    project/    项目管理、地图点位、项目权限服务
    person/     临时人员管理
    safety/     安全三级教育
    file/       文件上传、下载、归档、删除
    camera/     摄像头资源管理
    device/     设备与塔吊基础信息
    external/   外部系统配置实体和 mapper
    log/        操作日志实体和 mapper
    common/     Result、BusinessException、全局异常处理
    config/     JWT、Redis、MyBatis-Plus、Swagger 配置

frontend/
  package.json
  vite.config.js
  .env.example
  src/
    main.jsx
    App.jsx
    services/       API 封装
    components/     地图、通用组件、表单组件
    pages/Login     登录页，当前被 App 使用
    pages/Camera    镜头管理页，当前被 App 使用
    pages/Overview  旧拆分页，当前 App 未使用
    pages/Personnel 旧拆分页，当前 App 未使用
    pages/Monitor   旧拆分页，当前 App 未使用
    constants/      字典、主题、mock 数据
    hooks/          部分旧 hook，当前主流程使用有限

docs/
  项目开发文档和新对话交接文档
```

## 开发规范

- 开发前先读 `AGENTS.md`、`docs/新对话交接文档.md`、`docs/开发状态.md`。
- 涉及模块开发时，再读 `docs/模块清单.md`、`docs/接口规则.md`、`docs/数据库结构.md`、`docs/权限规则.md`。
- 以当前代码为准，PRD 文档仅作为业务背景，不能把 PRD 中规划能力当成已实现能力。
- 修改应小步、聚焦，不做无关重构。
- 不要改动用户或其他工具已有的未提交变更，除非任务明确要求。
- 后端保持当前模块化单体结构，优先使用 MyBatis-Plus 既有写法。
- 前端当前真实入口主要在 `frontend/src/App.jsx`，不要误改未被使用的旧拆分页面后以为功能已生效。
- 新增接口应保持 `/api/v1/**` 后端路径，前端通过 Vite `/api` 代理访问。
- 新增前端 API 方法放在 `frontend/src/services/*.js`。
- 百度地图 AK 只能放在 `frontend/.env`，不能写死进源码或文档。

## 命名规范

后端：

- 包名使用小写模块名，例如 `project`、`camera`、`device`。
- Entity 对应数据库表，使用 PascalCase 类名和 camelCase 字段名。
- 表字段使用 snake_case，MyBatis-Plus 开启 `map-underscore-to-camel-case`。
- Controller 方法命名体现业务动作，例如 `getProjectMapPoints`、`updateProjectLocation`。
- DTO / VO 放在对应模块 `dto/` 目录。

前端：

- 页面组件使用 PascalCase。
- API 方法使用动词开头，例如 `getProjectList`、`updateProjectLocation`。
- 常量放在 `frontend/src/constants/`。
- 本地存储 token key 当前为 `site_platform_token`，用户信息 key 为 `site_platform_user`。

## 接口规范

- 后端统一返回 `Result<T>`：
  - `code`: 业务状态码，成功为 `200`
  - `message`: 消息
  - `data`: 业务数据
- 注意：当前 `GlobalExceptionHandler` 对 `BusinessException` 返回 `Result.error(code, message)`，HTTP 状态通常仍为 200。前端应检查业务 `code`，不能只看 HTTP 状态。
- 文件下载接口 `GET /api/v1/files/{id}/download` 返回 `ResponseEntity<Resource>`，不是 `Result<T>`。
- 需要登录的接口从 `Authorization: Bearer <token>` 取当前用户。
- 后端不能信任前端传入的 `userId`、`username`、`role` 等身份字段；涉及当前登录用户的数据必须以后端 token 解析出的用户为准。
- 涉及 `projectId` 的接口必须校验当前用户是否有该项目权限。当前代码中只有项目模块做得较多，其他模块仍待完善。

## 权限规则

当前角色表规划：

- `PLATFORM_ADMIN`：平台管理员
- `PROJECT_ADMIN`：项目管理员
- `SAFETY_ADMIN`：安全管理员
- `USER`：普通用户

当前已实现：

- `ProjectPermissionService.isPlatformAdmin(userId)`：`userId == 1` 兜底，或拥有 `PLATFORM_ADMIN` 角色。
- `ProjectPermissionService.isProjectAdmin(userId)`：拥有 `PROJECT_ADMIN` 角色。
- 项目列表、详情、地图点位、地图详情和定位更新会走当前用户逻辑。
- 项目新增、更新、删除只允许平台管理员。
- 项目定位更新允许平台管理员或对该项目有权限的项目管理员。

当前待完善：

- `ProjectPermissionService.getUserProjects` 注释说明应查询 `sys_user_project`，但实际仍是简化逻辑：非 `userId=1` 用户只看项目 1。
- 人员、安全、文件、摄像头、设备接口当前大多只校验登录态，未严格校验 `projectId` 权限。
- 前端当前没有基于角色隐藏菜单或禁用按钮。

## 数据库操作注意事项

- 当前初始化脚本：`backend/src/main/resources/sql/init.sql`。
- 该脚本包含大量 `DROP TABLE IF EXISTS`，执行会重建表并清空已有数据。不要在已有数据环境直接运行。
- 当前没有 Flyway / Liquibase 迁移体系。新增字段应优先写增量 SQL 文档或迁移脚本，不要只改 `init.sql`。
- 逻辑删除字段为 `deleted`，多个实体使用 `@TableLogic`。
- 新增字段时必须同步：
  - SQL
  - Entity
  - DTO / VO
  - Controller / Service 入参出参
  - 前端表单和展示
  - `docs/数据库结构.md`
  - `docs/接口规则.md`

## 禁止事项

- 不要读取或修改 `frontend/node_modules/`、`frontend/dist/`、`backend/target/`、构建产物、日志和无关大文件。
- 不要把真实百度地图 AK、数据库密码、生产 token 写进源码或文档。
- 不要随意改数据库结构，尤其不要在有数据环境执行带 `DROP TABLE` 的初始化脚本。
- 不要绕过后端权限校验，只在前端做权限判断。
- 不要相信前端传入的用户身份字段。
- 不要大范围重构 `frontend/src/App.jsx`，除非任务明确要求拆分页面。
- 不要把 `frontend/src/pages/Overview|Personnel|Monitor` 的旧拆分页当作当前真实页面入口。
- 不要删除现有功能或 mock 兜底，除非任务明确要求。

## 每次开发前必须阅读

最少阅读：

1. `AGENTS.md`
2. `docs/新对话交接文档.md`
3. `docs/开发状态.md`

按任务补充阅读：

- 模块开发：`docs/模块清单.md`
- 接口开发：`docs/接口规则.md`
- 数据库修改：`docs/数据库结构.md`
- 权限相关：`docs/权限规则.md`
- 地图相关：`百度地图接入说明.md`
- 摄像头字段增强：`摄像头资源管理字段增强.md`

## 每次修改后必须更新

- 新增或改模块：更新 `docs/模块清单.md`
- 新增或改接口：更新 `docs/接口规则.md`
- 新增或改表字段：更新 `docs/数据库结构.md`
- 改权限逻辑：更新 `docs/权限规则.md`
- 修复重要问题或推进功能：更新 `docs/开发状态.md`
- 改变项目交接方式或启动步骤：更新 `docs/新对话交接文档.md`
