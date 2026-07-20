# 电信云平台项目现场综合管理系统 - 后端

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x
- Redis

## 快速启动

### 1. 初始化数据库

```bash
# 登录 MySQL
mysql -u root -p

# 执行数据库初始化脚本
source src/main/resources/sql/init.sql
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

```bash
mvn clean compile
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package
java -jar target/site-platform-1.0.0.jar
```

### 5. 访问 API 文档

启动成功后，访问 Swagger UI：
- http://localhost:8080/doc.html

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | 平台管理员 |
| manager | 123456 | 项目管理员 |

## 主要接口

### 认证接口
- POST `/api/v1/auth/login` - 用户登录
- GET `/api/v1/auth/user-info` - 获取用户信息
- POST `/api/v1/auth/logout` - 用户登出

### 项目接口
- GET `/api/v1/projects` - 获取项目列表
- GET `/api/v1/projects/{projectId}` - 获取项目详情

### 工程资料库接口
- `/api/v1/document-folders` - 工程资料目录
- `/api/v1/project-documents` - 分页、上传、版本、归档和回收站

## 项目结构

```
src/main/java/com/example/siteplatform/
├── auth/           # 登录认证、JWT、用户权限
├── project/        # 项目管理
├── external/       # 外部系统入口
├── person/         # 临时人员管理
├── safety/        # 安全三级教育
├── file/          # 文件资料管理
├── document/      # 工程资料目录、版本和回收站
├── camera/        # 摄像头资源与视频播放地址
├── videolayout/   # 视频窗口布局
├── device/        # 设备与塔吊管理
├── log/           # 操作日志
├── common/        # 公共响应、异常、工具类
└── config/        # 系统配置
```

## 技术栈

- Spring Boot 3.2.5
- MyBatis-Plus 3.5.6
- MySQL 8.x
- Redis
- JWT (jjwt 0.12.6)
- Knife4j (Swagger文档)
