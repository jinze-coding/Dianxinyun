# AGENTS.md

本文件供 Codex / AI 编程助手使用。进入本仓库后，必须先阅读本文件，再阅读 `docs/新对话交接文档.md`、`docs/开发状态.md` 和任务相关专题文档。所有判断以当前代码和当前项目文档为准，不依赖旧聊天记录。

## 项目与正式范围

项目名称：电信云平台项目现场综合管理系统。

当前正式产品是 Web 与微信小程序共用后端、数据库和文件存储的多项目现场管理平台。正式权限目录只包括：

- 资料管理：目录、资料、版本、预览下载、归档和回收站。
- 巡检管理：电箱台账、每日巡检、记录查询、月表和统一二维码。
- 质量管理：问题发起、整改、复查和操作留痕。
- 系统管理：注册审核、用户、角色权限、菜单功能、项目授权、微信绑定和操作日志。

地图、项目概况、人员、安全总览、摄像头、设备和视频等历史代码可能仍在仓库中，但当前入口已隐藏，不属于本期菜单和权限目录。除非任务明确要求，不得恢复这些入口，也不得把历史文档中的旧页面当成当前产品。

## 技术栈与目录

后端：

- Java 17、Spring Boot 3.2.5
- MyBatis-Plus 3.5.6、MySQL 8.x
- Redis、JWT、BCrypt
- Knife4j / OpenAPI、Lombok

客户端：

- Web：React 18、Vite 5、axios
- 小程序：uni-app、Vue 3、TypeScript

主要目录：

```text
backend/
  src/main/java/com/example/siteplatform/
    auth/          账号登录、微信绑定、会话、Web 扫码登录
    registration/  Web/小程序统一注册申请与审批
    system/        菜单、操作权限、角色和系统管理
    project/       项目、项目成员、项目范围和兼容巡检权限
    document/      工程资料目录、资料版本和回收站
    electricbox/   电箱台账和二维码
    inspection/    电箱巡检记录与月表
    quality/       质量问题、整改和复查
    file/          通用文件与存储
    common/        统一响应、异常和限流
    config/        JWT、Redis、跨域、拦截器和接口文档
  src/main/resources/sql/
    init.sql
    migrations/

frontend/
  src/App.jsx
  src/pages/Login/
  src/pages/DocumentManagement/
  src/pages/QualityManagement/
  src/pages/SystemManagement/
  src/services/
  src/utils/permissions.js

wechat-miniprogram/site-platform-miniprogram/
  src/pages/
  src/api/
  src/stores/auth.ts
  src/utils/navigation.ts

docs/
  当前项目文档和历史兼容说明
```

本地默认地址：

- Web：`http://localhost:3002`
- 后端：`http://localhost:8080`
- Knife4j：`http://localhost:8080/doc.html`

## 认证、注册与微信登录

- 密码必须使用 BCrypt 校验和保存。非 BCrypt 历史密码禁止登录，并标记为待管理员重置。
- 系统不提供默认测试账号或明文密码。平台管理员密码只能通过系统管理，或一次性环境变量 `ADMIN_RESET_USERNAME`、`ADMIN_RESET_PASSWORD` 显式重置。
- JWT 与 Redis 会话有效期统一读取 `JWT_EXPIRATION_MILLIS`，默认 7 天。
- JWT 带凭证版本和独立会话标识。同一账号可保留多个会话；普通退出只注销当前会话，改密、停用、微信解绑和关键授权变化必须注销该用户全部会话。
- Web 与小程序新用户统一写入 `registration_application`。提交申请时不得创建 `sys_user`；只有审批通过后才能在事务内创建账号、分配角色和项目范围、建立申请中的微信绑定并写审计日志。
- `wechat_access_application` 只用于已存在系统账号申请项目访问，不得创建微信专用随机账号。
- 微信绑定在同一 AppID 下严格保持有效用户、OpenID、UnionID 一对一。手机号相同只能用于提示下一步，绝不能自动绑定账号。
- 小程序绑定已有账号必须同时验证账号密码和新的 `uni.login` code。
- Web 扫码登录使用 Redis challenge、浏览器私有校验密钥和一次性交换码；二维码中不得携带 JWT 或浏览器密钥。
- 微信 mock 默认关闭，只允许在显式 `dev`、`local` 或 `test` Profile 下启用。非开发环境缺少正式 AppID、AppSecret、合法 HTTPS 域名或回跳地址时必须拒绝启动微信能力。

## RBAC 与项目隔离

当前权限由以下部分共同决定：

- 平台角色：`sys_role.scope_type=PLATFORM`。
- 当前项目的项目角色：`sys_role.scope_type=PROJECT` 与 `sys_user_project.project_role_code`。
- 角色菜单：`sys_menu`、`sys_role_menu`。
- 操作权限：`sys_permission`、`sys_role_permission`。
- 项目数据范围：`sys_user_project.status=ACTIVE`。

必须遵守：

- `PLATFORM_ADMIN` 只通过角色判断，不允许使用固定用户 ID 兜底。
- 平台级系统管理接口只认可平台角色提供的平台权限；项目角色不能借聚合权限调用注册、全量用户、角色、菜单、微信全局管理或审计接口。
- 项目角色权限只在目标 `projectId` 的有效成员关系内生效。用户在 A 项目有写权限，不代表能写 B 项目。
- `/api/v1/auth/user-info` 是 Web 和小程序菜单、平台权限码、项目角色及项目权限的统一来源。
- 菜单决定入口可见性，操作权限决定页面内动作；前端显隐只用于体验，不能替代后端鉴权。
- 所有业务接口必须以后端 JWT 用户为准，不得信任前端传入的 `userId`、`username`、角色、上传人或操作人。
- 列表接口必须按有权项目过滤；详情、更新和删除接口必须先查业务记录，再按记录真实 `projectId` 校验。
- 无权限访问必须返回真实 HTTP `403`。

## 接口与错误处理

- 后端接口统一放在 `/api/v1/**`，Web 通过 Vite `/api` 代理调用。
- 普通 JSON 接口返回 `Result<T>`；文件流接口可返回 `ResponseEntity<Resource>`。
- `BusinessException` 由 `GlobalExceptionHandler` 映射为真实 HTTP `400 / 401 / 403 / 404 / 409` 等状态，同时保留响应体业务 `code`。
- 客户端必须同时处理 HTTP 状态和响应体业务消息。
- 新增公共登录、注册、扫码或状态查询接口时，必须补字段校验、Redis 限流和统一错误提示。
- 重要写操作必须记录审计日志，并在授权或凭证变化后清理权限缓存和相应会话。

## 数据库与迁移

- 当前有数据的本地库以 39 表基线为准；统一注册、RBAC 和微信快捷登录使用增量迁移：

```text
backend/src/main/resources/sql/migrations/20260728_unified_registration_rbac_wechat_login.sql
```

- `backend/src/main/resources/sql/init.sql` 包含 `DROP TABLE`，只允许用于明确确认的全新空库初始化。严禁在当前有数据环境、生产环境或长期开发库执行。
- 不得用 `init.sql` 代替增量迁移，也不得为了方便测试重建用户现有数据库。
- 执行迁移前必须备份数据库和相关文件，检查重复手机号、旧角色、微信绑定、项目授权和至少一个可恢复的平台管理员。
- 迁移必须可重复检查；重复执行不得恢复管理员已经停用的菜单或撤销的角色授权。
- 数据库字段变更必须同步 SQL、Entity、DTO/VO、Service/Controller、前端表单与展示、`docs/数据库结构.md` 和 `docs/接口规则.md`。

## 开发规范

- 修改前先用 `rg`、`rg --files` 和 `sed` 确认真实入口与调用链。
- 当前 Web 根流程仍主要由 `frontend/src/App.jsx` 组织；独立业务页在 `frontend/src/pages/`。不要误改未挂载的旧拆分页。
- 小程序导航和页面守卫必须继续由 `/auth/user-info` 返回的菜单与项目权限驱动，不得恢复固定原生权限入口。
- 新增前端 API 方法放在相应 `services` / `api` 文件，不在页面中重复拼请求。
- 保持现有模块化单体结构和 MyBatis-Plus 写法，修改应小步、聚焦。
- 工作区可能已有用户或其他工具的未提交变更；不得回退、覆盖或清理无关修改。
- 不要读取或修改 `node_modules/`、`dist/`、`backend/target/`、日志、构建产物和无关大文件。
- 不要把真实 AppSecret、数据库密码、JWT、百度地图 AK、生产 token 或用户业务数据写入源码和文档。
- 不做只有前端显隐、没有后端权限校验的功能。
- 不恢复已隐藏旧模块，不恢复巡检管理中的“用户与权限”双入口。

## 开发前必须阅读

最少阅读：

1. `AGENTS.md`
2. `docs/项目总览.md`
3. `docs/新对话交接文档.md`
4. `docs/开发状态.md`

按任务补充阅读：

- 模块：`docs/模块清单.md`
- 接口：`docs/接口规则.md`
- 数据库：`docs/数据库结构.md`
- 权限：`docs/权限规则.md`
- 小程序与巡检：`docs/小程序需求说明书.md`、`docs/小程序现场检查与电箱检查规格.md`

## 修改后验证与文档同步

- 后端：`cd backend && mvn test`
- Web：`cd frontend && npm run build`
- 小程序：在 `wechat-miniprogram/site-platform-miniprogram` 执行 `npm run type-check`、`npm run build:h5`、`npm run build:mp-weixin`
- 提交前执行 `git diff --check`。

同步规则：

- 新增或修改模块：更新 `docs/模块清单.md`
- 新增或修改接口：更新 `docs/接口规则.md`
- 新增或修改表字段：更新 `docs/数据库结构.md`
- 修改权限：更新 `docs/权限规则.md`
- 推进重要功能或完成验收：更新 `docs/开发状态.md`
- 改变交接、启动或部署方式：更新 `docs/新对话交接文档.md` 和相关 README
