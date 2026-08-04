# AGENTS.md

本文件供 Codex / AI 编程助手使用。进入本仓库后，必须先阅读本文件，再阅读 `docs/新对话交接文档.md`、`docs/开发状态.md` 和任务相关专题文档。所有判断以当前代码和当前项目文档为准，不依赖旧聊天记录。

## 项目与正式范围

项目名称：电信云平台项目现场综合管理系统。

当前正式产品是 Web 与微信小程序共用后端、数据库和文件存储的多项目现场管理平台。正式权限目录只包括：

- 资料管理：目录、资料、版本、预览下载、归档和回收站。
- 巡检管理：电箱台账、每日巡检、记录查询、月表和统一二维码。
- 质量管理：问题发起、整改、复查和操作留痕。
- 系统管理：注册审核、用户、角色权限、菜单功能、项目成员与权限、微信绑定和操作日志。

地图、项目概况、人员、安全总览、摄像头、设备和视频等历史代码可能仍在仓库中，但当前入口已隐藏，不属于本期菜单和权限目录。除非任务明确要求，不得恢复这些入口，也不得把历史文档中的旧页面当成当前产品。

## 技术栈与目录

后端：

- Java 17、Spring Boot 3.5.14
- MyBatis-Plus 3.5.6、MySQL 8.x
- Redis、JWT、BCrypt
- Knife4j / OpenAPI、Lombok

客户端：

- Web：React 18、Vite 8、axios（Node.js 20.19+ 或 22.12+）
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
- 新注册账号的登录名固定为已核验手机号；Web、小程序和后端不得再让申请人自定义独立用户名。兼容客户端若仍传 `username`，该值必须与手机号完全一致。
- 小程序微信快捷注册使用 `WECHAT_QUICK`：仅微信授权手机号和真实姓名必填，手机号由服务端解析并作为登录账号，不保存申请密码。审批后账号必须先通过微信登录设置初始密码；完成前只能读取本人状态、设置密码或登出，不能访问业务、解绑微信或使用 Web 扫码登录。
- 小程序标准手工注册继续保留为备用方式，仍要求手机号和符合强度规则的密码；Web 注册流程保持图形验证码与人工手机号核验。
- 标准微信注册的一次性 Redis 临时身份必须与申请数据库事务联动：提交成功后清理临时占用标记，事务回滚后恢复身份会话及剩余重试窗口并释放占用；并发请求仍只能成功消费一次。
- `wechat_access_application` 只用于已存在系统账号申请项目访问，不得创建微信专用随机账号。
- 微信绑定在同一 AppID 下严格保持有效用户、OpenID、UnionID 一对一。手机号相同只能用于提示下一步，绝不能自动绑定账号。
- 小程序绑定已有账号必须同时验证账号密码和新的 `uni.login` code。
- Web 扫码登录使用 Redis challenge、浏览器私有校验密钥和一次性交换码；二维码中不得携带 JWT 或浏览器密钥。
- 微信 mock 默认关闭，只允许在显式 `dev`、`local` 或 `test` Profile 下启用。非开发环境缺少正式 AppID、AppSecret、合法 HTTPS 域名或回跳地址时必须拒绝启动微信能力。
- Knife4j、Swagger UI 和 OpenAPI JSON 生产默认关闭，只有显式开发 Profile 才能开启。生产还必须使用非 root 的独立 MySQL 账号、TLS `DB_URL`、非空数据库/Redis密码和独立 JWT 密钥。

## RBAC 与项目隔离

当前权限由以下部分共同决定：

- 平台角色：`sys_role.scope_type=PLATFORM`。
- 当前项目的项目角色：`sys_role.scope_type=PROJECT` 与 `sys_user_project_role` 多对多关系；旧 `sys_user_project.project_role_code` 只作历史兼容。
- 角色菜单：`sys_menu`、`sys_role_menu`。
- 跨端业务模块：`sys_role_business_module` 中的 `DOCUMENT / INSPECTION / QUALITY`；每项同时覆盖 Web 和小程序入口。
- 操作权限：`sys_permission`、`sys_role_permission`。
- 项目数据范围：`sys_user_project.status=ACTIVE`。

必须遵守：

- `PLATFORM_ADMIN` 只通过角色判断，不允许使用固定用户 ID 兜底。
- 只有 `PLATFORM_ADMIN` 是平台级全局资格；其他业务角色均为项目角色。用户管理仅分配项目与项目角色，受保护的平台全局身份不作为普通业务角色展示或分配。
- 同一成员可在同一项目拥有多个角色，菜单和操作权限按并集计算；`sys_user_project.status=ACTIVE` 是项目角色生效前提。
- 用户管理采用“项目 → 角色”树，项目成员管理采用“用户 → 角色”树；父节点只代表成员关系，首次勾选自动使用启用中的内置 `USER` 角色，不代表全选子角色。
- 跨项目或同项目的多项成员变更必须通过批量接口一次提交并在同一事务内完成；单项校验失败时整批回滚。暂停/恢复访问仍为独立状态操作，不能被角色保存隐式改变。
- `PROJECT_ADMIN` 正式展示为受保护的“项目经理”。只有系统管理员能授予、撤销或调整该角色；项目经理只可管理自己项目的普通成员，不能处理任何项目经理角色、其他项目或平台权限。
- 平台级系统管理接口只认可平台角色提供的平台权限；项目角色不能借聚合权限调用注册、全量用户、角色、菜单、微信全局管理或审计接口。
- 项目角色权限只在目标 `projectId` 的有效成员关系内生效。用户在 A 项目有写权限，不代表能写 B 项目。
- `/api/v1/auth/user-info` 是 Web 和小程序菜单、平台权限码、项目角色及项目权限的统一来源。
- 资料、巡检、质量必须通过一个模块开关统一控制两端入口。关闭模块时保留细分操作权限配置，但 Web、小程序和后端目标项目鉴权都必须拒绝该模块；不得重新在角色页拆成 `WEB_*` 和 `MINI_*` 两次授权。
- 菜单决定入口可见性，操作权限决定页面内动作；前端显隐只用于体验，不能替代后端鉴权。
- 角色基本信息、菜单和操作权限必须分开保存；未传授权字段的角色基本信息修改不得清空现有授权。
- Web 页签菜单固定为资料库/回收站、电箱台账/巡检记录、质量问题/质量资料；页签细分不得将小程序拆成另一套业务授权。
- 新的角色管理 Web 只调用 `/system/roles/{id}/menus` 和
  `/system/roles/{id}/operation-permissions`；旧 `/permissions` 组合接口仅作兼容，不删除。
- 所有业务接口必须以后端 JWT 用户为准，不得信任前端传入的 `userId`、`username`、角色、上传人或操作人。
- 列表接口必须按有权项目过滤；详情、更新和删除接口必须先查业务记录，再按记录真实 `projectId` 校验。
- 项目只允许在没有成员、资料、巡检、质量、设备等任何关联记录时删除；禁止以删除项目主记录代替业务归档或级联清库。
- 巡检汇总的应检、已检、漏检和异常必须按该日期生效的历史巡检范围与到期日期计算，并按箱日去重；建箱前、非巡检范围和未来日期不得计入。
- 电箱所属项目创建后不可通过编辑迁移；`status`、`qrStatus` 只能由停用、拆除或二维码
  生命周期接口修改。传入责任人用户 ID 时必须校验有效系统账号并使用服务端姓名，禁止
  让无效用户 ID 触发项目成员自动补授权。
- 电箱新增、普通编辑、导入、停用和拆除使用 `BOX_MANAGE`；二维码生成、换绑、打印及
  日志查看使用 `BOX_QR_MANAGE`；公开扫码启停使用 `BOX_PUBLIC_ACCESS`。普通编辑如果
  同时修改二维码或公开访问状态，还必须追加对应细分权限，不能借 `BOX_MANAGE` 绕过。
- 公开近 30 天摘要和逐日月表必须一次加载目标日期范围的巡检范围历史，再在内存中解析
  每日应检状态；不得恢复为按自然日逐次查询数据库。
- 文件上传必须调用服务端 `FileUploadPolicy` 校验大小、扩展名和文件头；巡检/质量流程附件只接受真实栅格图片。HTML、SVG、脚本和可执行文件不得上传，非安全预览格式必须强制下载并返回 `nosniff`。
- 本地文件存储的相对键和历史绝对键都必须在配置的上传根目录内；下载、覆盖、回滚清理和永久删除不得接受根目录外路径。
- 永久删除文件时不得在数据库事务提交前删除物理对象；事务回滚必须保留原文件，提交后的物理清理失败必须保留可追踪元数据。
- 通用附件上传的新文件必须在事务回滚时清理；内容替换和删除只能在元数据提交成功后清理旧物理文件。
- 工程资料目录、文件元数据、资料主记录、版本、当前版本指针、归档/回收站状态和资料
  操作日志的单记录写入必须校验影响行数；任一步未生效返回 HTTP `409` 并回滚。资料
  编号最长 100 字符，资料备注和版本说明最长 500 字符，必须在物理文件落盘前校验。
- 无权限访问必须返回真实 HTTP `403`。

## 接口与错误处理

- 后端接口统一放在 `/api/v1/**`，Web 通过 Vite `/api` 代理调用。
- 普通 JSON 接口返回 `Result<T>`；文件流接口可返回 `ResponseEntity<Resource>`。
- 生产反向代理必须配置 `FORWARD_HEADERS_STRATEGY=NATIVE`，Nginx 规范设置
  `X-Forwarded-For` 和 `X-Forwarded-Proto`，后端端口不得直接暴露公网。业务代码只能
  使用容器校验后的 `request.getRemoteAddr()`，不得直接信任客户端请求头中的 IP。
- `BusinessException` 由 `GlobalExceptionHandler` 映射为真实 HTTP `400 / 401 / 403 / 404 / 409` 等状态，同时保留响应体业务 `code`。
- 客户端必须同时处理 HTTP 状态和响应体业务消息。
- 新增公共登录、注册、扫码或状态查询接口时，必须补字段校验、Redis 限流和统一错误提示。
- 免登录公开业务读取也必须在数据库查询前执行基于受信客户端 IP 的 Redis 限流；限流
  主体不得以明文写入 Redis，超限使用真实 HTTP `429`。
- 统一扫码调用者没有目标项目巡检权限且电箱公开访问关闭时，必须在巡检范围和内部记录
  查询前返回 HTTP `403`，不得返回电箱编号、名称、位置等元数据；匿名公开扫码不得
  查询或返回当天内部记录。
- 业务字段写入打印页、下载 HTML 或 `document.write` 前必须做 HTML 文本/属性转义；
  二维码图像只接受受控 Data URL，打印文档保留 CSP 且新窗口切断 `opener`。
- 账号密码类入口必须同时按真实客户端 IP 和规范化账号做独立限流，账号维度不得拼接 IP；
  Redis 限流键不得保存明文用户名、手机号、IP、状态令牌或扫码 challenge。
- 当前账号微信绑定、解绑和项目访问申请也必须同时按受信 IP 与规范化账号限流；兼容
  接口共享业务额度，微信解绑的密码校验必须在限流通过后执行。
- 重要写操作必须记录审计日志；授权、账号状态或微信绑定在事务内变化时，必须在提交前
  立即清理权限缓存和相应会话，并在提交后重复清理，防止并发旧请求回写旧授权。首次
  密码设置会在同一事务签发新令牌，不得使用提交后全量注销。
- 密码、账号状态、微信绑定、成员授权、角色、菜单和权限模板等敏感单记录写入必须校验
  数据库影响行数为 1；写入未生效时返回 HTTP `409` 并回滚，不能继续注销会话、替换
  权限或写入成功审计。
- 电箱主记录和二维码操作日志的单记录写入也必须校验数据库影响行数为 1；写入未生效
  时返回 HTTP `409` 并回滚，不能继续补项目成员授权或返回成功。
- 新建电箱日检时，服务端固定使用 `ELECTRIC_BOX_DAILY / ELECTRICIAN_DAILY`，并锁定
  目标电箱后检查同箱同日唯一性。请求必须恰好包含六个不重复的规范检查项编码，名称由
  服务端写入；异常项必须填写说明。巡检记录、检查项和巡检设置的单记录写入必须校验
  影响行数为 1，失败返回 HTTP `409` 并回滚。
- 摄像头和设备的所属项目、主键、删除标记及创建时间只能以后端原记录为准；名称必填，
  字符串边界必须在写库前校验，摄像头在线状态只允许 `0 / 1`。新增、更新和删除影响
  行数不是 1 时返回 HTTP `409`，不得把并发消失的目标报告为操作成功。
- 人员新增和编辑只复制明确允许的业务字段，主键、项目、状态、删除标记和创建时间不得
  由请求体覆盖；状态流转只走进退场/安全教育流程。人员、进退场流水、证件和附件绑定
  写入必须校验影响行数。未绑定证件附件只能由上传人本人绑定，且业务类型和项目必须匹配。
- 安全教育创建必须先完整校验批次字段、去重后的同项目人员和本人刚上传的未绑定培训资料，
  再开始写入。批次、人员关联、人员教育状态和附件绑定必须核对影响行数；完成或删除任一
  步骤未完整生效时返回 HTTP `409` 并回滚，不得留下部分完成的培训批次。
- 项目新增和基础资料编辑只能复制允许的业务字段，主键、删除标记及创建时间由服务端控制；
  项目状态、日期、坐标和字段长度必须先校验。项目定位主记录与审计日志在同一事务写入，
  任一单记录影响行数不是 1 时返回 HTTP `409` 并回滚。
- 变更电箱巡检范围前必须锁定目标电箱，同箱并发变更串行执行；关闭旧开放区间和新增历史
  区间都必须影响恰好 1 行，失败返回 HTTP `409` 并回滚。原因最长 300 字符，操作人姓名
  使用服务端登录态且最长 50 字符。

## 数据库与迁移

- 当前有数据的本地 `dianxinyun` 已完成统一注册、RBAC、微信快捷登录、项目多角色、
  跨端业务模块和质量并发迁移，以 47 表为当前基线。只有从迁移前 39 表旧副本升级时，
  才按顺序使用以下增量迁移；不得在当前库盲目重跑或改用 `init.sql`：

```text
backend/src/main/resources/sql/migrations/20260728_unified_registration_rbac_wechat_login.sql
backend/src/main/resources/sql/migrations/20260729_project_multi_role_member_management.sql
backend/src/main/resources/sql/migrations/20260729_project_multi_role_permission_cleanup.sql
backend/src/main/resources/sql/migrations/20260729_shared_business_module_access.sql
backend/src/main/resources/sql/migrations/20260729_wechat_quick_registration_initial_password.sql
backend/src/main/resources/sql/migrations/20260730_quality_issue_resilience.sql
backend/src/main/resources/sql/migrations/20260801_inspection_template_item_code_alignment.sql
backend/src/main/resources/sql/migrations/20260803_role_menu_permission_hierarchy.sql
```

`20260803_role_menu_permission_hierarchy.sql` 只增加 6 条页签菜单并一次性回填角色菜单关联，
不增加表或字段。已在隔离副本双跑，但未经备份和明确升级授权不得直接应用到长期本地库或生产库。

- `backend/src/main/resources/sql/init.sql` 是禁用兼容入口，直接执行必须失败。全新空库只能
  使用 `scripts/init-empty-database.sh`；工具要求精确确认并拒绝已有任何表的目标库。
- 空库基线模板不包含 `DROP TABLE / DROP DATABASE`。不得绕过工具直接执行模板，不得用
  空库初始化代替增量迁移，也不得为了方便测试重建用户现有数据库。
- `backend/src/main/resources/sql/migrations/` 只允许保存可审计的正式增量迁移，禁止放入
  清库、重置或演示数据脚本。破坏性本地测试夹具只能放在
  `backend/src/test/resources/sql/fixtures/`，且不得对生产库或长期数据环境执行。
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
- Web：`cd frontend && npm run check && npm run audit`（全量源码 ESLint、权限/项目列表核心 JS 类型检查、Node 单测、生产构建和完整依赖审计）
- 小程序：在 `wechat-miniprogram/site-platform-miniprogram` 执行 `npm run type-check`、`npm run build:h5`、`npm run build:mp-weixin`
- 提交前执行 `git diff --check`。

小程序当前 DCloud 编译套件仍要求 Vite 5.2.8。`npm audit` 中剩余的 1 条高风险 Vite
公告和由 esbuild 上卷的中风险项属于上游编译链，不能通过强装 Vite 8 或
`npm audit fix --force` 处理。开发服务器只允许 localhost/私有局域网 Origin，禁止
暴露到公网；DCloud 正式支持安全版 Vite 后再做整套升级。

同步规则：

- 新增或修改模块：更新 `docs/模块清单.md`
- 新增或修改接口：更新 `docs/接口规则.md`
- 新增或修改表字段：更新 `docs/数据库结构.md`
- 修改权限：更新 `docs/权限规则.md`
- 推进重要功能或完成验收：更新 `docs/开发状态.md`
- 改变交接、启动或部署方式：更新 `docs/新对话交接文档.md` 和相关 README
- 生产升级或回滚：必须遵循 `docs/生产升级与回滚手册.md`，先确认授权、可追溯源码、
  数据库/文件/JAR/配置备份、目标库迁移状态和回滚负责人；不得从无法归属的脏工作树
  直接发布，也不得以关闭安全门禁代替正确配置。
