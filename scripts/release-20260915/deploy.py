#!/usr/bin/env python3
"""One fixed, user-started deployment of the rehearsed 2026-09-15 release.

No rebuilding, remote execution, repeat rehearsal, or automatic DB rollback.
Run via the supplied systemd launcher so closing Workbench cannot kill migration.
"""
import datetime
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.request

HERE = Path(__file__).resolve().parent
PARENT = Path('/root/releases/20260915-0912')
BUNDLE = PARENT / 'Dianxinyun-update-20260915-0912'
STATE = PARENT / 'formal-switch'
BACKUP = Path('/root/backups/dianxinyun/release-20260915-0912')
APP = Path('/opt/site-platform')
DEST = APP / 'releases/20260915-0912'
JAR_LINK = APP / 'backend/site-platform.jar'
WEB_LINK = APP / 'frontend'
UPLOAD_LINK = APP / 'uploads'
ENV = Path('/etc/site-platform/site-platform.env')
MAINT = Path('/etc/site-platform/maintenance.lock')
DROPIN = Path('/etc/systemd/system/site-platform.service.d/70-material-preview-20260915.conf')
SERVICE = 'site-platform.service'
JAR_SHA = '0de87d1833edbaa41040f836a69939aa1aca6bf447863f5aeb4dc3da2a99a921'
SOURCE_SHA = '95e6a1fc24f569b3c9224d01971d3193b4dd40044401023e9b94393ea408c9af'
sequence = 0


class Stop(RuntimeError):
    pass


def require(ok, message):
    if not ok:
        raise Stop(message)


def emit(message):
    print(message, flush=True)


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def run(args, label, timeout=60, check=True, output=None):
    global sequence
    sequence += 1
    log = STATE / 'logs' / ('%04d-%s.log' % (sequence, label))
    with log.open('wb') as stream:
        try:
            result = subprocess.run([str(a) for a in args], stdout=output or stream,
                                    stderr=stream, timeout=timeout, check=False)
        except subprocess.TimeoutExpired:
            raise Stop(label + ' 超时；保留维护状态，日志：' + str(log)) from None
    require(not check or result.returncode == 0, label + ' 未通过，日志：' + str(log))
    return result.returncode


def prop(name, service=SERVICE):
    result = subprocess.run(['systemctl', 'show', service, '--property=' + name, '--value'],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=15)
    require(result.returncode == 0, '无法读取服务状态：' + name)
    return result.stdout.strip()


def state(phase, **fields):
    path = STATE / 'state.json'
    data = json.loads(path.read_text()) if path.exists() else {}
    data.update(phase=phase, updatedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(), **fields)
    temporary = STATE / '.state.next'
    temporary.write_text(json.dumps(data, indent=2))
    os.replace(temporary, path)


def lock(path, posix=False):
    fd = os.open(str(path), os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    handle = os.fdopen(fd, 'r+')
    info = os.fstat(fd)
    require(stat.S_ISREG(info.st_mode) and info.st_uid == 0, '锁文件类型/属主异常')
    try:
        (fcntl.lockf if posix else fcntl.flock)(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        handle.close()
        raise Stop('另一个操作占用：' + str(path)) from None
    return handle


def health(base, documents=False):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def request(path):
        try:
            with opener.open(base + path, timeout=8) as response:
                return response.status, response.read(2 * 1024 * 1024)
        except urllib.error.HTTPError as error:
            return error.code, b''
    try:
        status, body = request('/api/v1/auth/captcha')
        if status != 200 or json.loads(body).get('code') != 200:
            return False
        if documents:
            for path in ('/doc.html', '/swagger-ui/index.html', '/v3/api-docs',
                         '/api/v1/doc.html', '/api/v1/swagger-ui/index.html', '/api/v1/v3/api-docs'):
                if request(path)[0] != 404:
                    return False
        return True
    except (OSError, ValueError):
        return False


def wait_health():
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if prop('ActiveState') == 'failed':
            raise Stop('新后端启动失败，请查看本次日志')
        if prop('ActiveState') == 'active' and health('http://127.0.0.1:8080', True):
            for _ in range(6):
                if health('https://zhihuiyz.xyz', True):
                    return
                time.sleep(2)
        time.sleep(3)
    raise Stop('新后端健康检查超时')


def verify_web(web_root):
    import re
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    expected = (web_root / 'index.html').read_bytes()
    match = re.search(rb'src="(/assets/[^"?]+\.js)"', expected)
    require(match is not None, '新 Web 首页缺少入口脚本')
    path = match.group(1).decode()
    for attempt in range(5):
        try:
            request = urllib.request.Request('https://zhihuiyz.xyz/?release=20260915-0912',
                                             headers={'Cache-Control': 'no-cache'})
            with opener.open(request, timeout=10) as response:
                require(response.status == 200 and response.read(1024 * 1024) == expected,
                        '公网首页尚未匹配新版')
            with opener.open('https://zhihuiyz.xyz' + path, timeout=10) as response:
                require(response.status == 200
                        and hashlib.sha256(response.read(16 * 1024 * 1024)).hexdigest()
                        == sha(web_root / path.lstrip('/')), '公网新版脚本内容不匹配')
            return
        except (OSError, Stop):
            if attempt == 4:
                raise Stop('公网 Web 首页/脚本未通过新版内容核验') from None
            time.sleep(2)


def maintenance(create=False):
    if create:
        content = ('FORMAT_VERSION=1\nPURPOSE=DIANXINYUN_PRODUCTION_RELEASE\n'
                   'SERVICE_NAME_SHA256=' + hashlib.sha256(SERVICE.encode()).hexdigest() + '\n'
                   'BACKUP_DIR_SHA256=' + hashlib.sha256(str(BACKUP).encode()).hexdigest() + '\n'
                   'CREATED_AT=' + datetime.datetime.now(datetime.timezone.utc).isoformat() + '\n'
                   'CREATED_BY_UID=0\nCREATOR_PID=' + str(os.getpid()) + '\n')
        fd = os.open(MAINT, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'w') as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        (STATE / 'maintenance.original').write_text(content)
    require(MAINT.is_file() and not MAINT.is_symlink(), '维护锁丢失或被替换')
    info = MAINT.stat()
    require(info.st_uid == 0 and stat.S_IMODE(info.st_mode) == 0o600,
            '维护锁权限异常')
    require(MAINT.read_bytes() == (STATE / 'maintenance.original').read_bytes(), '维护锁内容被替换')


def stopped():
    require(prop('ActiveState') in {'inactive', 'failed'} and prop('MainPID') == '0'
            and prop('ControlPID') == '0' and not prop('Job'), '后端尚未完全停止')
    result = subprocess.run(['systemctl', 'is-enabled', SERVICE], stdout=subprocess.PIPE, text=True)
    require(result.stdout.strip() == 'disabled', '维护期服务自启未关闭')


def tar_backup(source, destination):
    run(['tar', '--numeric-owner', '-C', source.parent, '-czf', destination, source.name],
        'backup-' + destination.stem, timeout=900)
    run(['tar', '-tzf', destination], 'verify-' + destination.stem, timeout=300)


def tree_hashes(root, output):
    lines = []
    for path in sorted(root.rglob('*')):
        require(not path.is_symlink(), '文件树含软链，需先核对：' + str(root))
        if path.is_file():
            relative = path.relative_to(root).as_posix()
            require('\n' not in relative and '\r' not in relative and '\\' not in relative,
                    '文件名含校验清单不支持的字符')
            lines.append(sha(path) + '  ./' + relative + '\n')
    output.write_text(''.join(lines))


def backup(db, targets):
    BACKUP.mkdir(mode=0o700)
    for name in ('database', 'files', 'runtime', 'config', 'inventory'):
        (BACKUP / name).mkdir(mode=0o700)
    (BACKUP / 'inventory/targets.env').write_text(''.join(k + '=' + str(v) + '\n' for k, v in targets.items()))
    dump = BACKUP / 'database/dianxinyun.sql.gz'
    raw_dump = BACKUP / 'database/.dianxinyun.sql.partial'
    with raw_dump.open('xb') as output, (STATE / 'logs/mysqldump.log').open('wb') as err:
        result = subprocess.run(['mysqldump', '--no-defaults', '--user=root', '--protocol=SOCKET', '--socket=' + db.socket,
            '--single-transaction', '--quick', '--routines', '--triggers', '--events', '--hex-blob',
            '--set-gtid-purged=OFF', '--no-tablespaces', '--add-drop-database', '--databases', 'dianxinyun'],
            stdout=output, stderr=err, env=db.environment, timeout=180)
    require(result.returncode == 0, '数据库备份失败')
    with raw_dump.open('rb') as original, gzip.open(dump, 'wb', compresslevel=1) as zipped:
        shutil.copyfileobj(original, zipped, 1024 * 1024)
    run(['gzip', '-t', dump], 'verify-database', timeout=120)
    raw_dump.unlink()
    tar_backup(Path(targets['UPLOAD_TARGET']), BACKUP / 'files/uploads.tar.gz')
    tree_hashes(Path(targets['UPLOAD_TARGET']), BACKUP / 'inventory/uploads-files.sha256')
    shutil.copy2(targets['JAR_TARGET'], BACKUP / 'runtime/site-platform.jar')
    os.chmod(BACKUP / 'runtime/site-platform.jar', 0o600)
    tar_backup(Path(targets['WEB_TARGET']), BACKUP / 'runtime/frontend.tar.gz')
    shutil.copy2(ENV, BACKUP / 'config/site-platform.env')
    os.chmod(BACKUP / 'config/site-platform.env', 0o600)
    run(['tar', '--numeric-owner', '-C', '/', '-czf', BACKUP / 'config/nginx.tar.gz', 'etc/nginx'], 'backup-nginx')
    run(['tar', '-tzf', BACKUP / 'config/nginx.tar.gz'], 'verify-nginx')
    fragment = Path(targets['SYSTEMD_FRAGMENT'])
    members = [str(fragment).lstrip('/')]
    if Path(str(fragment) + '.d').exists():
        members.append(str(fragment).lstrip('/') + '.d')
    for dropin in prop('DropInPaths').split():
        if not dropin.startswith(str(fragment) + '.d/'):
            members.append(dropin.lstrip('/'))
    run(['tar', '--numeric-owner', '-C', '/', '-czf', BACKUP / 'config/systemd.tar.gz', *members], 'backup-systemd')
    run(['tar', '-tzf', BACKUP / 'config/systemd.tar.gz'], 'verify-systemd')
    (BACKUP / 'inventory/tables.txt').write_text('\n'.join(sorted(db.tables())) + '\n')
    (BACKUP / 'inventory/migration-markers.txt').write_text('\n'.join(sorted(db.markers())) + '\n')
    (BACKUP / 'BACKUP_COMPLETE').write_text('BACKUP_STATE=COMPLETE\n')
    sums = ''.join(sha(p) + '  ' + p.relative_to(BACKUP).as_posix() + '\n'
                   for p in sorted(BACKUP.rglob('*')) if p.is_file())
    (BACKUP / 'SHA256SUMS').write_text(sums)
    with (STATE / 'logs/verify-backup.log').open('wb') as stream:
        result = subprocess.run(['sha256sum', '--quiet', '-c', 'SHA256SUMS'], cwd=BACKUP,
                                stdout=stream, stderr=stream, timeout=600)
    require(result.returncode == 0, '同点备份校验失败')


def swap(target, link):
    require(link.is_symlink(), '正式入口不是预期软链：' + str(link))
    temporary = link.parent / ('.' + link.name + '.20260915-next')
    require(not temporary.exists() and not temporary.is_symlink(), '新软链路径已存在')
    temporary.symlink_to(target)
    os.replace(temporary, link)


def main():
    require(sys.argv[1:] in (['--apply'], ['--apply-no-new-backup'], ['--apply', '--apply-no-new-backup']),
            '用法：deploy.py --apply 或 --apply-no-new-backup；由用户启动本次固定新版发布')
    skip_new_backup = '--apply-no-new-backup' in sys.argv[1:]
    require(os.getuid() == 0, '请使用服务器 root 终端')
    os.umask(0o077)
    if STATE.exists() and not STATE.is_symlink():
        previous = json.loads((STATE / 'state.json').read_text())
        # Only a preflight failure with no staged or production write may retry.
        require(previous.get('phase') == 'FAILED'
                and previous.get('databaseMayHaveChanged') is False
                and previous.get('maintenanceCreated') is False
                and not DEST.exists() and not BACKUP.exists() and not MAINT.exists(),
                '本次已进入正式执行阶段，请反馈，不能盲目重跑')
        STATE.rename(STATE.with_name('formal-switch-precheck-' + str(time.time_ns())))
    require(not STATE.exists() and not STATE.is_symlink(), '正式执行目录异常')
    STATE.mkdir(mode=0o700)
    (STATE / 'logs').mkdir(mode=0o700)
    state('PREFLIGHT', newBackupSkippedByUser=skip_new_backup, newBackupCreated=False)
    handles = []
    maintained = changed_db = False
    try:
        handles.append(lock(Path('/run/site-platform-release.lock')))
        require(not MAINT.exists() and not MAINT.is_symlink(), '已有维护锁，不能开始新发布')
        require(not Path('/run/site-platform-offline-worker.state').exists(), '已有离线操作未完成')
        require(not Path('/run/reboot-required').exists(), '请先完成上一步服务器重启并重新连接')
        for p in ('/var/lib/dpkg/lock-frontend', '/var/lib/dpkg/lock', '/var/lib/apt/lists/lock', '/var/cache/apt/archives/lock'):
            if Path(p).exists():
                handles.append(lock(Path(p), posix=True))
        apt = subprocess.check_output(['apt-config', 'dump'], text=True, timeout=15)
        import re
        require(not re.search(r'Unattended-Upgrade::Automatic-Reboot\s+"(?:true|1|yes|on)"', apt, re.I), 'APT 自动重启已启用')
        require(prop('ActiveState') == 'active', '当前正式服务未运行')
        require(subprocess.check_output(['systemctl', 'is-enabled', SERVICE], text=True).strip() == 'enabled', '正式服务未开启自启')
        require(prop('User') == 'site-platform' and prop('Group') == 'site-platform', '服务账号不符')
        require(prop('FragmentPath') == '/etc/systemd/system/site-platform.service', '正式 unit 路径不符')
        require(prop('WorkingDirectory') == str(APP), '正式工作目录不符')
        require(ENV.is_file() and not ENV.is_symlink() and ENV.stat().st_uid == 0
                and stat.S_IMODE(ENV.stat().st_mode) == 0o600, '正式环境文件权限不符')
        require(not BACKUP.exists() and not DEST.exists() and not DROPIN.exists(), '本次备份/版本/配置路径已存在')
        report = json.loads((PARENT / 'validation-093640/runtime-validation-r2/result.json').read_text())
        require(report.get('complete') is True and report.get('productionChanged') is False
                and report.get('jarSha256') == JAR_SHA and report.get('sourceManifest') == SOURCE_SHA, 'r2 验证报告归属不符')
        migration = json.loads((PARENT / 'validation-093640/migration-rehearsal/result.json').read_text())
        require(migration.get('complete') is True and migration.get('tables') == 114
                and migration.get('markers') == 37 and migration.get('originalRowsPreserved') is True, '副本迁移结果不符')
        image = json.loads(subprocess.check_output(['docker', 'image', 'inspect', 'dianxinyun-material-preview:local'], text=True))[0]
        require(image['Id'] == report['imageId'], '转换镜像与已验证版本不一致')
        require(health('http://127.0.0.1:8080') and health('https://zhihuiyz.xyz'), '原系统健康检查未通过')
        run(['nginx', '-t'], 'nginx-before')
        targets = dict(SERVICE_NAME=SERVICE, MYSQL_DATABASE='dianxinyun', JAR_LINK=JAR_LINK,
                       WEB_LINK=WEB_LINK, UPLOAD_LINK=UPLOAD_LINK, ENV_FILE=ENV,
                       NGINX_ROOT='/etc/nginx', SYSTEMD_FRAGMENT=prop('FragmentPath'))
        for key, link in (('JAR_TARGET', JAR_LINK), ('WEB_TARGET', WEB_LINK), ('UPLOAD_TARGET', UPLOAD_LINK)):
            require(link.is_symlink(), '入口不是已核定软链：' + str(link))
            actual = link.resolve(strict=True)
            require(APP in actual.parents, '部署目标不在正式目录内')
            targets[key] = actual
        # The schema is tiny; allow upload copies, compressed backups and logs
        # without assuming the preflight's earlier free-space value is current.
        if not skip_new_backup:
            upload_bytes = sum(p.stat().st_size for p in targets['UPLOAD_TARGET'].rglob('*') if p.is_file())
            require(shutil.disk_usage(BACKUP.parent).free > 2 * upload_bytes + 2 * 1024**3, '完整备份空间不足')
        from database_ops import ProductionDB, reference
        import runtime_ops
        import nginx_ops
        reference.load_payload(BUNDLE)
        db = ProductionDB(STATE / 'database-logs')
        (STATE / 'mysql-socket').write_text(db.socket + '\n')
        db.preflight()
        nginx_plan = nginx_ops.prepare(STATE / 'logs')
        (STATE / 'nginx-plan.json').write_text(json.dumps(nginx_plan, indent=2))
        # Validate env syntax before creating a maintenance window; the real
        # secret is generated only by configure after stopping the service.
        runtime_ops.prepare_env(ENV.read_text(), DEST / 'runtime/meeting-material-convert.sh')
        emit('1/5 已确认当前 98 表基线和新版验证结果，开始暂存新版本。')
        staged = runtime_ops.stage(BUNDLE, DEST, variant='final')
        state('STAGED', targets={k: str(v) for k, v in targets.items()}, staged={k: str(v) for k, v in staged.items()})
        watchdog = None
        for attempt in range(15):
            try:
                watchdog = lock(Path('/run/site-platform-watchdog.lock'))
                break
            except Stop:
                time.sleep(1)
        require(watchdog is not None, 'watchdog 检查持续占用，未进入停服')
        handles.append(watchdog)
        maintenance(create=True)
        maintained = True
        state('STOPPING')
        run(['systemctl', 'disable', SERVICE], 'disable-startup')
        run(['systemctl', 'stop', SERVICE], 'stop-backend', timeout=150)
        stopped()
        watchdog.close()
        handles.remove(watchdog)
        db.preflight()
        if skip_new_backup:
            emit('2/5 按用户明确要求跳过本次新备份，继续正式迁移切换。')
            (STATE / 'NEW_BACKUP_SKIPPED_USER_REQUEST').write_text(
                'NEW_BACKUP_SKIPPED_USER_REQUEST=true\n')
            state('NEW_BACKUP_SKIPPED_USER_REQUEST', newBackupCreated=False)
        else:
            emit('2/5 正式服务已停止，正在生成并校验同点完整备份。')
            backup(db, targets)
            state('BACKUP_COMPLETE', newBackupCreated=True)
        original = db.snapshot()
        maintenance()
        stopped()
        runtime_ops.configure(ENV, DEST / 'runtime/meeting-material-convert.sh', DROPIN)
        nginx_ops.apply(nginx_plan)
        run(['systemctl', 'daemon-reload'], 'reload-unit')
        require('docker' in prop('SupplementaryGroups').split(), '转换器服务组配置未生效')
        require(prop('PrivateTmp') == 'yes' and prop('ProtectSystem') == 'strict', '服务保护配置发生变化')
        emit('3/5 正式配置已完成，执行已演练的 12 项增量迁移。')
        state('MIGRATING')
        changed_db = True
        db.migrate(BUNDLE, original)
        state('MIGRATED')
        maintenance()
        stopped()
        swap(DEST / 'backend/site-platform.jar', JAR_LINK)
        swap(DEST / 'web-final/dist', WEB_LINK)
        state('ACTIVATING')
        run(['nginx', '-t'], 'nginx-new')
        run(['systemctl', 'reload', 'nginx.service'], 'reload-nginx')
        emit('4/5 已切换新版 JAR 和 Web，启动并检查正式接口。')
        run(['systemctl', 'start', SERVICE], 'start-backend', timeout=150)
        wait_health()
        verify_web(DEST / 'web-final/dist')
        pid = prop('MainPID')
        require(pid.isdigit() and int(pid) > 0 and sha(JAR_LINK.resolve()) == JAR_SHA, '运行 JAR 归属不符')
        args = Path('/proc/' + pid + '/cmdline').read_bytes().split(b'\0')
        require(str(JAR_LINK).encode() in args or str(JAR_LINK.resolve()).encode() in args, '运行进程未引用新版 JAR')
        journal_file = STATE / 'new-backend.log'
        with journal_file.open('wb') as stream:
            run(['journalctl', '-u', SERVICE, '_SYSTEMD_INVOCATION_ID=' + prop('InvocationID'), '--no-pager', '-o', 'cat'], 'new-backend-journal', output=stream)
        journal = journal_file.read_text(errors='replace')
        require('Started SitePlatformApplication' in journal and 'HikariPool-1 - Start completed' in journal
                and 'APPLICATION FAILED TO START' not in journal and 'OutOfMemoryError' not in journal,
                '新后端启动日志未通过，请查看私有日志')
        run(['systemctl', 'enable', SERVICE], 'enable-startup')
        require(subprocess.check_output(['systemctl', 'is-enabled', SERVICE], text=True).strip() == 'enabled', '未恢复开机自启')
        maintenance()
        MAINT.unlink()
        maintained = False
        state('COMPLETE', jarSha256=JAR_SHA, tables=114, markers=37, mainPid=pid,
              completedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
        emit('5/5 新版正式切换完成：114 表；本机/公网接口正常，API 文档已关闭。')
        if skip_new_backup:
            emit('本次未创建新备份（用户明确要求），已记录 NEW_BACKUP_SKIPPED_USER_REQUEST。')
        else:
            emit('备份目录：' + str(BACKUP))
        emit('请刷新正式网站，验收登录、安委会提交与附件预览、刷新保留项目和页面。')
    except BaseException as error:
        if STATE.exists():
            (STATE / 'exception.log').write_text(traceback.format_exc())
            state('FAILED', databaseMayHaveChanged=changed_db, maintenanceCreated=maintained,
                  errorType=type(error).__name__)
        if maintained:
            # Never run old code against a partly migrated database. Preserve
            # diagnostic state for explicit recovery; never claim a skipped backup exists.
            try:
                run(['systemctl', 'disable', SERVICE], 'failure-disable', check=False)
                run(['systemctl', 'stop', SERVICE], 'failure-stop', timeout=150, check=False)
            except Exception:
                pass
            emit('发布暂停，维护锁与本次记录保留；不要重复执行或手工替换旧 JAR。')
        if isinstance(error, (Stop, RuntimeError, ValueError)):
            emit(str(error))
        else:
            emit('执行未完成：' + type(error).__name__ + '；私有日志目录：' + str(STATE))
        raise SystemExit(1)
    finally:
        for handle in reversed(handles):
            handle.close()


if __name__ == '__main__':
    main()
