# 开发环境恢复快照

该目录保存 2026-07-20 导出的本地开发环境快照，用于恢复当前联调数据。数据均为开发阶段的虚构测试数据，不应直接用于生产环境。

## 数据库

`dianxinyun.sql` 包含 `dianxinyun` 数据库的 39 张表、结构、完整数据、触发器、事件和存储过程定义。

恢复前请确认目标库允许被覆盖，然后执行：

```bash
mysql -u root -p < backups/development/dianxinyun.sql
```

## 文件存储

`file-storage/` 对应后端本地文件存储目录。恢复数据库后，将其复制到后端运行目录：

```bash
mkdir -p backend/uploads
rsync -a backups/development/file-storage/ backend/uploads/
```

数据库中的 `file_resource` 记录与该目录中的 18 个文件相互对应。若使用 MinIO，需要按部署环境上传文件并调整存储配置。

## 注意事项

- 该快照包含开发账号密码哈希和虚构人员信息，只适合开发联调。
- 部署后应立即修改默认账号密码，并设置独立的 `JWT_SECRET`。
- 已有数据环境优先使用 `backend/src/main/resources/sql/migrations/` 中的增量脚本，不要直接运行带清库逻辑的初始化脚本。
