# 电信云平台项目现场综合管理系统

本仓库包含当前开发环境中的完整项目资料：

- `backend/`：Spring Boot 3 后端。
- `frontend/`：React 18 PC Web 管理端。
- `wechat-miniprogram/`：uni-app 微信小程序与 H5 预览端。
- `docs/`：需求、接口、数据库、权限和开发交接文档。
- `backend/src/main/resources/sql/migrations/`：数据库增量迁移脚本。
- `backups/development/`：`dianxinyun` 开发库数据与对应文件存储快照。
- `scripts/dev-services.sh`：本地开发服务统一启动脚本。

## 本地启动

前置依赖：Java 17、Maven、Node.js、MySQL 8、Redis。

```bash
./scripts/dev-services.sh start
```

启动后可访问：

- PC Web：`http://localhost:3002`
- 后端接口文档：`http://localhost:8080/doc.html`
- 小程序 H5：`http://localhost:3003`

微信开发者工具可通过以下命令打开：

```bash
./scripts/dev-services.sh open-miniprogram
```

详细开发口径和注意事项请先阅读 `AGENTS.md` 与 `docs/新对话交接文档.md`。
生产升级必须先阅读 `docs/生产升级与回滚手册.md`，完成授权、备份、数据库版本预检和
隔离演练后才能执行；不得直接把本地 JAR 覆盖线上服务。
本轮全局检查的问题状态与验证边界见 `docs/系统全局检查修复状态.md`。

## 安全说明

仓库不包含本机 `.env`、百度地图密钥、微信密钥、数据库密码、日志、依赖目录或构建产物。生产部署必须通过环境变量配置数据库密码、JWT 密钥、地图密钥和微信小程序密钥。
