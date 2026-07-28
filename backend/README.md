# 电信云平台项目现场综合管理系统 - 后端

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x
- Redis

## 快速启动

### 1. 准备数据库

```bash
# 当前 39 表基线只执行：
# mysql ... < src/main/resources/sql/migrations/20260728_unified_registration_rbac_wechat_login.sql
# init.sql 仅用于全新空库，禁止在已有数据环境重复执行。
# 全新空库执行 init.sql 后仍需继续应用适用的增量迁移。
```

### 2. 配置数据库连接

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/dianxinyun?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: your_password  # 修改为你的密码

  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password  # 如果有密码的话
```

文件默认保存到本地 `./uploads`。工程资料库也支持 MinIO，通过环境变量切换，不要把密钥写入配置文件：

```bash
export FILE_STORAGE_TYPE=minio
export MINIO_ENDPOINT=http://127.0.0.1:9000
export MINIO_BUCKET=site-platform
export MINIO_ACCESS_KEY=your_access_key
export MINIO_SECRET_KEY=your_secret_key
```

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
mvn spring-boot:run
```

正式环境必须提供真实微信配置和 HTTPS 域名，且不得启用 mock：

```bash
mvn clean package
WECHAT_MINI_PROGRAM_APP_ID=正式AppID \
WECHAT_MINI_PROGRAM_APP_SECRET=正式AppSecret \
WECHAT_MINI_PROGRAM_LEGAL_DOMAIN=https://正式域名 \
WECHAT_MINI_PROGRAM_PUBLIC_FALLBACK_URL=https://正式域名/扫码回跳地址 \
java -jar target/site-platform-1.0.0.jar
```

### 5. 访问 API 文档

启动成功后，访问 Swagger UI：
- http://localhost:8080/doc.html

## 管理员凭证

系统不提供默认测试账号或明文密码。历史非 BCrypt 密码会被标记为待重置且禁止登录。
如需恢复平台管理员，可在一次启动中同时提供
`ADMIN_RESET_USERNAME` 与 `ADMIN_RESET_PASSWORD`；启动成功后立即移除这两个环境变量。
全新空库执行 `init.sql` 时也只会预留一个不可登录的管理员主体；必须先应用
`20260728_unified_registration_rbac_wechat_login.sql`，再使用上述变量显式设置密码。

示例（请替换占位值，成功启动后从运行环境中删除变量）：

```bash
ADMIN_RESET_USERNAME='<平台管理员账号>' \
ADMIN_RESET_PASSWORD='<新的高强度密码>' \
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

## 项目结构

```
src/main/java/com/example/siteplatform/
├── auth/           # 密码认证、微信绑定、JWT/Redis 会话和 Web 扫码
├── registration/   # Web/小程序统一注册申请
├── system/         # 菜单、操作权限、角色和系统管理
├── project/        # 项目、成员、项目角色和数据范围
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

- Spring Boot 3.2.5
- MyBatis-Plus 3.5.6
- MySQL 8.x
- Redis
- JWT (jjwt 0.12.6)
- Knife4j (Swagger文档)
