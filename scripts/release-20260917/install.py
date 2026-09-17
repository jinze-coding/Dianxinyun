#!/usr/bin/env python3
"""Update the confirmed Sep 15 deployment with code and one additive migration."""
import fcntl
import grp
import importlib.util
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
base_path = ROOT / 'code_install.py'
if not base_path.exists():
    base_path = ROOT.parent / 'release-20260916/install.py'
spec = importlib.util.spec_from_file_location('code_install', base_path)
code = importlib.util.module_from_spec(spec)
spec.loader.exec_module(code)
from database import Database, MIGRATION


def activate(dest, old_jar, old_web, old_pid, state_file, database):
    marker = 'ADDITIVE_UPDATE=' + dest.name + '\n'
    with code.MAINT.open('x') as stream:
        stream.write(marker)
    state = {'previousJar': str(old_jar), 'previousWeb': str(old_web), 'newRelease': str(dest),
             'databaseMigration': 'NOT_STARTED', 'databaseRollback': 'NOT_NEEDED_ADDITIVE_ONLY'}

    def record(status, error=None):
        state_file.write_text(json.dumps({**state, 'status': status, 'error': error}, ensure_ascii=False, indent=2))

    try:
        record('STOPPING')
        code.run('systemctl', 'stop', code.SERVICE)
        state['databaseMigration'] = 'STARTED'
        record('MIGRATING')
        print('2/4 仅应用尚未执行的数据纠错增量；保留业务数据和附件……', flush=True)
        state['databaseMigration'] = database.apply()
        record('SWITCHING')
        print('3/4 切换后端与 Web，启动并核验新版……', flush=True)
        code.swap(code.JAR, dest / 'backend/site-platform.jar')
        code.swap(code.WEB, dest / 'web')
        code.run('systemctl', 'start', code.SERVICE)
        code.health()
        code.require(code.prop('MainPID') not in ('0', old_pid), '后端未切换到新进程')
        code.verify_web(dest / 'web')
        code.require(code.MAINT.read_text() == marker, '维护标记被其他操作改变')
        record('COMPLETE')
        code.MAINT.unlink()
        print('4/4 更新完成：数据纠错增量、新后端、Web 页面及资源核验通过。', flush=True)
    except BaseException as error:
        print('新版未完成，恢复原代码；已新增的纠错表和历史保留。', flush=True)
        try:
            code.run('systemctl', 'stop', code.SERVICE)
            code.swap(code.JAR, old_jar)
            code.swap(code.WEB, old_web)
            code.run('systemctl', 'start', code.SERVICE)
            code.health()
            code.verify_web(old_web)
            code.require(code.MAINT.read_text() == marker, '维护标记被其他操作改变')
            record('ROLLED_BACK', str(error))
            code.MAINT.unlink()
            print('原代码已恢复并通过健康检查；本次新版未上线，不删除任何新增表或附件。', flush=True)
        except BaseException as recovery:
            record('RECOVERY_REQUIRED', str(error) + '；恢复失败：' + str(recovery))
            print('恢复未完成，保留维护标记和现场，请提供任务日志。', flush=True)
        raise


def main():
    code.require(sys.argv[1:] == ['--apply'] and os.geteuid() == 0, '请在正式服务器 root 终端执行 run.sh')
    os.umask(0o077)
    with open('/run/site-platform-release.lock', 'a') as release_lock, open('/run/site-platform-watchdog.lock', 'a') as watchdog_lock:
        fcntl.flock(release_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        fcntl.flock(watchdog_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        entries = code.checksum_files(ROOT)
        code.require({'database.py', 'code_install.py', 'database/' + MIGRATION} <= entries.keys(), '更新包缺少增量或安装模块')
        manifest = json.loads((ROOT / 'RELEASE.json').read_text())
        release_id = manifest['releaseId']
        code.require(release_id.startswith('20260917-') and release_id.replace('-', '').isdigit(), '版本标识不正确')
        code.require(manifest['databaseMigrations'] == [MIGRATION] and manifest['configurationChanges'] is False, '不是本次必要增量更新包')
        code.require(not code.MAINT.exists() and not code.MAINT.is_symlink(), '服务器已有维护任务，请勿重复更新')
        code.require(code.prop('ActiveState') == 'active' and code.prop('SubState') == 'running', '原服务未正常运行')
        code.require(code.prop('WorkingDirectory') == str(code.APP) and code.prop('User') == 'site-platform'
                     and str(code.JAR) in code.prop('ExecStart'), '正式服务路径或账号不符')
        code.require(code.JAR.is_symlink() and code.WEB.is_symlink(), '正式入口不是已核定的软链接')
        old_jar, old_web, old_pid = code.JAR.resolve(strict=True), code.WEB.resolve(strict=True), code.prop('MainPID')
        database = Database(ROOT, old_pid)
        already_applied = database.preflight()
        if code.sha(old_jar) == entries['artifacts/backend.jar'] and code.sha(old_web / 'index.html') == entries['artifacts/web/index.html']:
            code.require(already_applied, '当前代码已更新但数据纠错迁移不完整，请保留现场')
            code.health()
            code.verify_web(old_web)
            print('当前已经是本次新版，迁移及健康核验通过。', flush=True)
            return
        code.require(code.sha(old_jar) == code.OLD_JAR_SHA and code.sha(old_web / 'index.html') == code.OLD_HTML_SHA,
                     '当前线上代码与 9 月 15 日正式基线不符，尚未停服，请提供版本信息')
        dest = code.APP / 'releases' / release_id
        print('1/4 正式基线、数据库目标和更新包核验通过；准备新版文件……', flush=True)
        code.stage(ROOT, dest, old_web, grp.getgrnam('site-platform').gr_gid)
        code.require(code.JAR.resolve() == old_jar and code.WEB.resolve() == old_web and code.prop('MainPID') == old_pid, '准备期间线上版本发生变化')
        activate(dest, old_jar, old_web, old_pid, ROOT / 'UPDATE-RESULT.json', database)


if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        print('更新未完成：' + str(failure), file=sys.stderr, flush=True)
        sys.exit(1)
