# 正式生产发布脚本

本目录只服务于 `production-backup-20260901-004700` 所证明的旧正式基线。脚本不会猜测或修补其他
数据库状态：实时库必须逐表等于 `baseline/expected-tables.txt` 的 68 张表，并逐项等于
`baseline/expected-migration-markers.txt` 的 13 个标记，否则在任何迁移前停止。

所有数据库认证都使用服务器现有受控 MySQL 配置，或通过只读环境变量
`MYSQL_DEFAULTS_FILE=/root/.my.cnf` 指定权限为 `600` 的客户端配置；禁止把密码写入参数或聊天。

## 固定阶段

1. `00-preflight-readonly.sh`：精确只读预检。
2. `10-stop-and-backup.sh`：确认后停服，生成并验证同一点完整备份；成功后服务保持停止。
3. `15-configure-production-env.sh`：在旧 env 已备份后生成独立图纸密钥，并显式启用正常调度。
4. `20-run-migrations.sh`：严格 11 项，68/13 → 98/24。
5. `25-stage-backend.sh`：以 `root:site-platform 0640` 暂存新 JAR，不切换。
6. `30-visitor-reencrypt.sh`：依次 `verify`、`apply`、`post-verify`，结束为 98/25。
7. `50-install-web.sh --stage transition`：后端仍停止时先切会议创建关闭的过渡 Web。
8. `40-activate-backend.sh`：只有 transition Web 已就绪才启动新后端。
9. 完成真实业务冒烟后，用 `55-record-transition-compatibility.sh` 固化兼容停点证据。
10. 只把封存的小程序 `mp-weixin/` 上传为 `0.1.8`；审核发布并确认用户端生效后，运行
    `60-record-mini-live.sh`。正式构建已显式关闭 DCloud `uniStatistics`。
11. `50-install-web.sh --stage final`：同时具有 transition 和小程序证明时才开放会议创建。
12. 任一阶段失败使用 `90-rollback.sh`；它先保存故障现场，再做数据库/uploads/JAR/Web/env/Nginx/
    systemd 同点恢复，不执行反向 DDL。

后端激活和同点回滚启动均先轮询本机验证码接口（默认最多 45 次、间隔 2 秒），本机就绪后再轮询
公网入口（默认最多 15 次、间隔 2 秒），不再使用固定 3 秒后单次判断。回滚在数据库、文件和备份
Nginx/systemd 已恢复后若健康超时，会停止后端并写入版本目录的 `ROLLBACK_FAILED`（含失败阶段和
最终服务状态）；已经成功解包的同点 Nginx/systemd 配置保持不动，故障现场配置继续保留在
`.failed-<时间>` 路径，禁止自动换回线上。只有两级健康检查都通过才写 `ROLLBACK_COMPLETE`。

写脚本均支持 `--dry-run`，且正式执行要求脚本帮助中列出的固定确认语。确认语不是密码，可以进入
审计日志；任何真实密钥、密码、AppSecret 或用户数据都不得写入命令行。

在源码仓库中运行离线自检：

```bash
bash scripts/production-release/99-self-test.sh
```

从生产更新包解包后运行同一自检：

```bash
bash ops/99-self-test.sh
```

生产操作必须逐步执行和验收，不能把本目录脚本一次性串联，也不能使用 `init.sql`、测试夹具、演示数据、
工作区 `dist/dev/mp-weixin` 或 `dist/build/mp-weixin`。
