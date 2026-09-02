# 正式生产发布脚本

本目录只服务于 `production-backup-20260901-004700` 所证明的旧正式基线。脚本不会猜测或修补其他
数据库状态：实时库必须逐表等于 `baseline/expected-tables.txt` 的 68 张表，并逐项等于
`baseline/expected-migration-markers.txt` 的 13 个标记，否则在任何迁移前停止。

所有数据库认证都使用服务器现有受控 MySQL 配置，或通过只读环境变量
`MYSQL_DEFAULTS_FILE=/root/.my.cnf` 指定权限为 `600` 的客户端配置；禁止把密码写入参数或聊天。

## 固定阶段

1. `00-preflight-readonly.sh`：精确只读预检。
2. `05-self-heal-control.sh install`：主服务健康时安装/核验保守自恢复；不得停止或重启主服务。
3. `10-stop-and-backup.sh`：创建与备份目录绑定的持久维护锁，停服并验证同一点完整备份。
4. `15-configure-production-env.sh`：在旧 env 已备份后生成独立图纸密钥，并显式启用正常调度。
5. `20-run-migrations.sh`：严格 11 项，68/13 → 98/24。
6. `25-stage-backend.sh`：以 `root:site-platform 0640` 暂存新 JAR，不切换。
7. `30-visitor-reencrypt.sh`：依次 `verify`、`apply`、`post-verify`，结束为 98/25。
8. `50-install-web.sh --stage transition`：后端仍停止时先切会议创建关闭的过渡 Web。
9. `40-activate-backend.sh`：只有 transition Web 已就绪才启动新后端；完整健康门禁通过后解除维护锁。
10. 完成真实业务冒烟后，用 `55-record-transition-compatibility.sh` 固化兼容停点证据。
11. 只把封存的小程序 `mp-weixin/` 上传为 `0.1.8`；审核发布并确认用户端生效后，运行
    `60-record-mini-live.sh`。正式构建已显式关闭 DCloud `uniStatistics`。
12. `50-install-web.sh --stage final`：同时具有 transition 和小程序证明时才开放会议创建。
13. 任一阶段失败使用 `90-rollback.sh`；它先保存故障现场，再做数据库/uploads/JAR/Web/env/Nginx/
    systemd 同点恢复，不执行反向 DDL。

`06-offline-worker-recovery.sh` 不是正常发布阶段，只用于 `30` 的 transient worker 已确定退出、但
`SUBMITTING/RUNNING` 标记因 OOM、`SIGKILL` 或断连残留的故障恢复。它必须在同一维护窗口内以固定确认语
执行，并同时核对同点备份、主服务 `inactive + disabled`、worker 无 job/PID 及数据库精确状态；禁止直接
删除 `/run/site-platform-offline-worker.state` 或 `/run/site-platform-offline-worker.lock`。后者是状态切换、
复核和受控删除共用的内核租约载体，文件长期存在不表示仍被占用。

所有非 dry-run 写阶段在动态校验前等待并全程持有 `/run/site-platform-release.lock`，确保同一时刻只有一个
生产安装、迁移、激活、证明写入或回滚进程。等待锁是正常串行化；锁文件可以长期存在，真正的租约由
内核在持锁进程退出时释放。禁止删除该文件或并行执行第二个生产命令来绕过门禁。

后端激活和同点回滚启动均先轮询本机验证码接口（默认最多 45 次、间隔 2 秒），本机就绪后再轮询
公网入口（默认最多 15 次、间隔 2 秒），不再使用固定 3 秒后单次判断。回滚在数据库、文件和备份
Nginx/systemd 已恢复后若健康超时，会停止后端并写入版本目录的 `ROLLBACK_FAILED`（含失败阶段和
最终服务状态）；已经成功解包的同点 Nginx/systemd 配置保持不动，故障现场配置继续保留在
`.failed-<时间>` 路径，禁止自动换回线上。只有两级健康检查都通过才写 `ROLLBACK_COMPLETE`。

维护锁固定为 `/etc/site-platform/maintenance.lock`。`10` 取得 `/run/site-platform-watchdog.lock` 后创建，
并持有协调锁直到主服务确认停止；随后先关闭主服务开机自启，所有离线阶段都核对维护锁与本次备份目录
一致且主服务保持 `inactive + disabled`。`40` 或 `90` 只有在恢复服务、通过完整健康门禁并重新启用开机
自启后才解除锁。失败时锁继续保留，watchdog 不会在迁移或回滚中途误拉起后端。常驻 watchdog 只对
`inactive/failed` 尝试一次启动，不重启正在运行但健康异常的后端。详细边界见
`docs/生产服务自恢复保护.md`。
离线阶段的“停稳”还会严格要求主服务无 MainPID/ControlPID/job，拒绝 `activating/deactivating`；
开机保护只接受精确的持久 `enabled`，不把 runtime/static 等状态当作已启用。

`10` 正式停服前还会拒绝 APT 自动重启已启用或 `/run/reboot-required` 已存在的主机。维护锁存在期间
禁止 ECS/系统重启；若意外重启，主服务应因 `disabled` 保持停止，仍须保持流量关闭并按半迁移状态
核对或同点回滚；如果意外成为 `active`，立即停止，不能把它当作发布成功。

正常发布写脚本支持 `--dry-run`，且正式执行要求脚本帮助中列出的固定确认语。`06` 是故障恢复例外，
故意不提供 dry-run，并要求数据库复核后的专用固定确认语。确认语不是密码，可以进入审计日志；任何
真实密钥、密码、AppSecret 或用户数据都不得写入命令行。

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
