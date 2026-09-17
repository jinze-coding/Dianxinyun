#!/usr/bin/env python3
"""2026-09-17 weekly-return patch for the verified 20260917-1330 installation."""
import fcntl
import grp
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
APP = Path('/opt/site-platform')
JAR = APP / 'backend/site-platform.jar'
WEB = APP / 'frontend'
MAINT = Path('/etc/site-platform/maintenance.lock')
SERVICE = 'site-platform.service'
OLD_JAR_SHA = 'c9aed9bd7bb7bac184efdc0d81b70be0aeff995c0c3db8a1c5b0e95bd411f256'
OLD_HTML_SHA = '4b8eaa4c6bee901f4de6d0996f4bd36804daa03bf86f205a8e5a02d3b5706c5d'


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.sha256(stream.read()).hexdigest()


def run(*args):
    return subprocess.check_output(args, stderr=subprocess.STDOUT, timeout=150).decode().strip()


def prop(name):
    return run('systemctl', 'show', SERVICE, '--property=' + name, '--value')


def checksum_files(root):
    entries = {}
    for line in (root / 'SHA256SUMS').read_text().splitlines():
        digest, relative = line.split('  ', 1)
        path = Path(relative)
        require(not path.is_absolute() and '..' not in path.parts and relative not in entries, '更新包文件清单无效')
        file = root / path
        require(file.is_file() and not any(p.is_symlink() for p in (file, *file.parents)), '更新包文件或路径不正常')
        require(sha(file) == digest, '更新包校验失败：' + relative)
        entries[relative] = digest
    require({'artifacts/backend.jar', 'artifacts/web/index.html', 'RELEASE.json', 'install.py'} <= entries.keys(), '更新包缺少必要文件')
    actual = {p.relative_to(root).as_posix() for p in (root / 'artifacts').rglob('*') if p.is_file()}
    require(actual == {p for p in entries if p.startswith('artifacts/')}, '产物清单不完整')
    return entries


def stage(root, dest, old_web, group_id):
    require(not dest.exists() and not dest.is_symlink(), '版本目录已经存在，请先查看上次更新结果：' + str(dest))
    dest.mkdir(mode=0o755)
    os.chmod(dest, 0o755)
    shutil.copytree(root / 'artifacts/web', dest / 'web')
    # 保留浏览器已打开旧页面所需的带摘要资源，避免切换瞬间请求旧 JS/CSS 返回 404。
    if (old_web / 'assets').is_dir():
        for old in (old_web / 'assets').rglob('*'):
            require(not old.is_symlink(), '旧静态资源含非预期链接')
            if not old.is_file():
                continue
            target = dest / 'web/assets' / old.relative_to(old_web / 'assets')
            if target.exists():
                require(sha(target) == sha(old), '同名静态资源内容不一致：' + old.name)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(old, target)
    for file in (dest / 'web').rglob('*'):
        os.chmod(file, 0o755 if file.is_dir() else 0o644)
    os.chmod(dest / 'web', 0o755)
    (dest / 'backend').mkdir(mode=0o750)
    shutil.copyfile(root / 'artifacts/backend.jar', dest / 'backend/site-platform.jar')
    os.chown(dest / 'backend', 0, group_id)
    os.chmod(dest / 'backend', 0o750)
    os.chown(dest / 'backend/site-platform.jar', 0, group_id)
    os.chmod(dest / 'backend/site-platform.jar', 0o640)
    shutil.copyfile(root / 'RELEASE.json', dest / 'RELEASE.json')
    require(sha(dest / 'backend/site-platform.jar') == sha(root / 'artifacts/backend.jar'), '后端复制校验失败')
    for file in (root / 'artifacts/web').rglob('*'):
        if file.is_file():
            require(sha(file) == sha(dest / 'web' / file.relative_to(root / 'artifacts/web')), 'Web 复制校验失败')


def swap(link, target):
    require(link.is_symlink(), '正式入口不是已核定的软链接：' + str(link))
    temporary = link.with_name(link.name + '.new-' + uuid.uuid4().hex)
    temporary.symlink_to(target)
    try:
        os.replace(temporary, link)
    finally:
        temporary.unlink(missing_ok=True)


def health():
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen('http://127.0.0.1:8080/api/v1/auth/captcha', timeout=3) as response:
                if response.status == 200 and json.load(response).get('code') == 200 and prop('ActiveState') == 'active':
                    return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('后端在 120 秒内未通过健康检查')


def verify_web(web):
    assets = re.findall(r'(?:src|href)="(/assets/[^"?#]+)"', (web / 'index.html').read_text())
    require(assets and all('..' not in Path(path).parts for path in assets), 'Web 首页缺少有效资源引用')
    for relative in ['index.html', *[path.lstrip('/') for path in assets]]:
        url = 'https://zhihuiyz.xyz/' + ('' if relative == 'index.html' else relative)
        data = subprocess.check_output(['curl', '--fail', '--silent', '--show-error', '--max-time', '15',
                                        '--resolve', 'zhihuiyz.xyz:443:127.0.0.1', url], timeout=20)
        require(hashlib.sha256(data).hexdigest() == sha(web / relative), 'Nginx 返回的页面或资源不是当前版本：' + relative)


def activate(dest, old_jar, old_web, old_pid, state_file):
    marker = 'CODE_ONLY_UPDATE=' + dest.name + '\n'
    with MAINT.open('x') as stream:
        stream.write(marker)
    state = {'previousJar': str(old_jar), 'previousWeb': str(old_web), 'newRelease': str(dest)}

    def record(status, error=None):
        state_file.write_text(json.dumps({**state, 'status': status, 'error': error}, ensure_ascii=False, indent=2))

    try:
        record('SWITCHING')
        print('2/3 切换后端和 Web，重启应用服务……', flush=True)
        run('systemctl', 'stop', SERVICE)
        swap(JAR, dest / 'backend/site-platform.jar')
        swap(WEB, dest / 'web')
        run('systemctl', 'start', SERVICE)
        health()
        require(prop('MainPID') not in ('0', old_pid), '后端未切换到新进程')
        verify_web(dest / 'web')
        require(MAINT.read_text() == marker, '维护标记被其他操作改变')
        record('COMPLETE')
        MAINT.unlink()
        print('3/3 更新完成：新版后端、Web 页面和资源检查通过。数据库、附件和配置保持原样。', flush=True)
    except BaseException as error:
        print('新版未通过，正在恢复原代码：' + str(error), flush=True)
        try:
            run('systemctl', 'stop', SERVICE)
            swap(JAR, old_jar)
            swap(WEB, old_web)
            run('systemctl', 'start', SERVICE)
            health()
            verify_web(old_web)
            require(MAINT.read_text() == marker, '维护标记被其他操作改变')
            record('ROLLED_BACK', str(error))
            MAINT.unlink()
            print('已恢复原代码并通过健康检查；本次新版没有上线。', flush=True)
        except BaseException as rollback_error:
            record('RECOVERY_REQUIRED', str(error) + '；恢复失败：' + str(rollback_error))
            print('自动恢复未完成，保留现场与维护标记，请提供本任务日志。', flush=True)
        raise


def main():
    require(sys.argv[1:] == ['--apply'] and os.geteuid() == 0, '请在正式服务器 root 终端执行 run.sh')
    os.umask(0o077)
    with open('/run/site-platform-release.lock', 'a') as release_lock, open('/run/site-platform-watchdog.lock', 'a') as watchdog_lock:
        fcntl.flock(release_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        fcntl.flock(watchdog_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        entries = checksum_files(ROOT)
        manifest = json.loads((ROOT / 'RELEASE.json').read_text())
        release_id = manifest['releaseId']
        require(release_id.startswith('20260917-') and release_id.replace('-', '').isdigit(), '版本标识不正确')
        require(manifest['databaseChanges'] is False and manifest['configurationChanges'] is False, '不是本次纯代码更新包')
        require(not MAINT.exists() and not MAINT.is_symlink(), '服务器已有维护任务，请勿重复更新')
        require(prop('ActiveState') == 'active' and prop('SubState') == 'running', '原服务未正常运行')
        require(prop('WorkingDirectory') == str(APP) and prop('User') == 'site-platform'
                and str(JAR) in prop('ExecStart'), '服务路径或账号与已核定正式部署不一致')
        require(JAR.is_symlink() and WEB.is_symlink(), '正式入口不是已核定的软链接')
        old_jar, old_web, old_pid = JAR.resolve(strict=True), WEB.resolve(strict=True), prop('MainPID')
        if sha(old_jar) == entries['artifacts/backend.jar'] and sha(old_web / 'index.html') == entries['artifacts/web/index.html']:
            health()
            verify_web(old_web)
            print('当前已经是本次新版，健康检查通过，无需重复更新。', flush=True)
            return
        require(sha(old_jar) == OLD_JAR_SHA and sha(old_web / 'index.html') == OLD_HTML_SHA,
                '当前线上代码与 9 月 17 日 1330 正式基线不符，尚未停服，请提供当前版本信息')
        dest = APP / 'releases' / release_id
        print('1/3 正式基线和包校验通过；准备新版文件，无数据库迁移。', flush=True)
        stage(ROOT, dest, old_web, grp.getgrnam('site-platform').gr_gid)
        require(JAR.resolve() == old_jar and WEB.resolve() == old_web and prop('MainPID') == old_pid, '准备期间线上版本发生变化')
        activate(dest, old_jar, old_web, old_pid, ROOT / 'UPDATE-RESULT.json')


if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        print('更新未完成：' + str(failure), file=sys.stderr, flush=True)
        sys.exit(1)
