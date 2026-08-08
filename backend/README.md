# 电信云平台项目现场综合管理系统 - 后端

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x
- Redis

## 快速启动

### 1. 准备数据库

```bash
# 当前本地 dianxinyun 已是 47 表基线，不执行 init.sql。
# 只有明确创建全新空库时才运行仓库根目录工具：
DIANXINYUN_INIT_CONFIRM='CREATE_EMPTY_DATABASE_ONLY:dianxinyun' \
  ../scripts/init-empty-database.sh
# 工具拒绝已有任何表的数据库；完成后仍需继续应用适用的增量迁移。
```

### 2. 配置数据库连接

本地默认连接 `localhost:3306/dianxinyun` 和 `localhost:6379`。需要覆盖时使用
`DB_URL / DB_USERNAME / DB_PASSWORD` 和
`REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DATABASE`，不要把密码写入 YAML。
完整生产变量模板见 `.env.example`；Spring Boot 不会自动读取该文件，应由部署平台注入。

文件默认保存到本地 `./uploads`。工程资料库也支持 MinIO，通过环境变量切换，不要把密钥写入配置文件：

```bash
export FILE_STORAGE_TYPE=minio
export MINIO_ENDPOINT=http://127.0.0.1:9000
export MINIO_BUCKET=site-platform
export MINIO_ACCESS_KEY=your_access_key
export MINIO_SECRET_KEY=your_secret_key
```

上传由后端同时校验大小、扩展名和文件头。巡检/质量流程照片最大 15MB；工程资料最大
50MB，支持 PDF、栅格图片、Office/WPS、CAD、安全文本和常见压缩包；HTML、SVG、脚本
及可执行文件会被拒绝。电箱导入只接受模板生成的 `.xlsx`，最大 10MB。

### 3. 启动 Redis（如果未运行）

```bash
redis-server
```

### 4. 编译并运行

本地开发环境显式启用 `local` Profile 后才允许使用微信 mock：

```bash
mvn clean compile
SPRING_PROFILES_ACTIVE=local \
WECHAT_MINI_PROGRAM_MOCK_ENABLED=true \
KNIFE4J_ENABLE=true \
API_DOCS_ENABLED=true \
SWAGGER_UI_ENABLED=true \
mvn spring-boot:run
```

正式环境必须提供真实微信配置和 HTTPS 域名，且不得启用 mock：

```bash
mvn clean package
SPRING_PROFILES_ACTIVE=prod \
FORWARD_HEADERS_STRATEGY=NATIVE \
DB_URL='jdbc:mysql://数据库地址:3306/dianxinyun?sslMode=VERIFY_IDENTITY' \
DB_USERNAME=site_platform \
DB_PASSWORD=<database-secret> \
REDIS_HOST=<redis-host> \
REDIS_PASSWORD=<redis-secret> \
JWT_SECRET=<至少32字节且仅属于该环境的随机密钥> \
VISITOR_DATA_ENCRYPTION_KEY=<独立且至少32字节的外访数据随机密钥> \
SEAL_SCENE_ENCRYPTION_KEY=<另一把独立且至少32字节的用印二维码随机密钥> \
WECHAT_MINI_PROGRAM_APP_ID=正式AppID \
WECHAT_MINI_PROGRAM_APP_SECRET=<wechat-app-secret> \
WECHAT_MINI_PROGRAM_PRODUCTION=true \
WECHAT_MINI_PROGRAM_LEGAL_DOMAIN=https://正式域名 \
WECHAT_MINI_PROGRAM_PUBLIC_FALLBACK_URL=https://正式域名/扫码回跳地址 \
KNIFE4J_ENABLE=false \
API_DOCS_ENABLED=false \
SWAGGER_UI_ENABLED=false \
java -jar target/site-platform-1.0.0.jar
```

`local/dev/test` Profile 可使用仓库内仅供联调的开发 JWT 密钥。其他环境若未配置
`JWT_SECRET`、仍使用开发默认值，或密钥不足 32 字节，后端会在监听端口前直接拒绝启动。
外访手机号、身份证号和邀请令牌使用独立 AES-GCM 密钥；非开发环境缺少至少 32 字节的
`VISITOR_DATA_ENCRYPTION_KEY` 或 `SEAL_SCENE_ENCRYPTION_KEY` 时同样拒绝启动；两把密钥还必须彼此独立，且不能与 JWT、数据库或微信密钥共用。
生产还必须通过受信 Nginx 设置 `X-Forwarded-For` 和 `X-Forwarded-Proto`，并配置
`FORWARD_HEADERS_STRATEGY=NATIVE`；否则后端拒绝启动，避免所有用户共享代理 IP 限流。

### 5. 访问 API 文档

本地开发显式开启文档后，访问 Knife4j UI：
- http://localhost:8080/doc.html

生产环境的 Knife4j、Swagger UI 和 `/v3/api-docs` 固定关闭；尝试开启会导致启动失败。
关闭时 `/doc.html`、`/webjars/**`、`/swagger-ui/**` 和 `/v3/api-docs/**` 均返回 `404`。

## 管理员凭证

系统不提供默认测试账号或明文密码。历史非 BCrypt 密码会被标记为待重置且禁止登录。
如需恢复平台管理员，可在一次启动中同时提供
`ADMIN_RESET_USERNAME` 与 `ADMIN_RESET_PASSWORD`；启动成功后立即移除这两个环境变量。
全新空库通过 `scripts/init-empty-database.sh` 建立基线时只会预留一个不可登录的管理员主体；必须先应用
`20260728_unified_registration_rbac_wechat_login.sql`，再使用上述变量显式设置密码。

示例（请替换占位值，成功启动后从运行环境中删除变量）：

```bash
ADMIN_RESET_USERNAME='<平台管理员账号>' \
ADMIN_RESET_PASSWORD=<one-time-strong-password>
SPRING_PROFILES_ACTIVE=local \
mvn spring-boot:run
```

## 主要接口

### 认证接口
- POST `/api/v1/auth/login` - 用户登录
- GET `/api/v1/auth/user-info` - 获取用户信息
- POST `/api/v1/auth/logout` - 用户登出
- `/api/v1/auth/wechat/mini/**` - 小程序快捷登录及显式绑定
- `/api/v1/auth/web-qr/challenges/**` - Web 微信扫码 challenge、确认和一次性交换

### 注册与系统管理接口
- `/api/v1/registration-applications` - Web/小程序统一注册申请、状态令牌查询和取消
- `/api/v1/system/registration-applications` - 注册审核
- `/api/v1/system/users` - 全量用户、状态、密码和角色
- `/api/v1/system/roles`、`/menus`、`/permissions` - RBAC 目录
- `/api/v1/system/wechat-bindings`、`/audit-logs` - 微信绑定和审计

### 项目接口
- GET `/api/v1/projects` - 获取项目列表
- GET `/api/v1/projects/{projectId}` - 获取项目详情

### 工程资料库接口
- `/api/v1/document-folders` - 工程资料目录
- `/api/v1/project-documents` - 分页、上传、版本、归档和回收站

### 场内管理接口
- `/api/v1/site-access/invitations` - 项目外访邀请分页、详情、创建与修改
- `/api/v1/site-access/invitations/{id}/void|mini-code` - 作废邀请、生成专属小程序码
- `/api/v1/site-access/visitors/export` - 按计划到场日期范围导出逐人 Excel
- `/api/v1/public/site-access/invitations/resolve|submit` - 小程序免登录解析及一次性提交；邀请令牌只放请求体

## 项目结构

```
src/main/java/com/example/siteplatform/
├── auth/           # 密码认证、微信绑定、JWT/Redis 会话和 Web 扫码
├── registration/   # Web/小程序统一注册申请
├── system/         # 菜单、操作权限、角色和系统管理
├── project/        # 项目、成员、项目角色和数据范围
├── siteaccess/     # 场内外访邀请、实名人员、加密审计和导出
├── document/       # 工程资料目录、版本和回收站
├── electricbox/    # 电箱台账与二维码
├── inspection/     # 巡检记录与月表
├── quality/        # 质量问题、整改与复查
├── file/           # 文件资料管理
├── log/            # 操作日志
├── common/         # 公共响应、异常、限流和工具类
└── config/         # 系统配置
```

`person/`、`camera/`、`device/` 等历史模块仍可能保留代码，但当前产品入口已隐藏，
不在本期 RBAC 目录内。

## 技术栈

- Spring Boot 3.5.14
- MyBatis-Plus 3.5.6
- MySQL 8.x
- Redis
- JWT (jjwt 0.11.5)
- springdoc-openapi 2.8.17 + Knife4j UI 4.5.0
