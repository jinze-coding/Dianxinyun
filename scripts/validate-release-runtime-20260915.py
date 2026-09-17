#!/usr/bin/env python3
"""One-shot, isolated runtime rehearsal for the already uploaded 20260915 package.

Run manually as root on the target server. Never switches the production service,
changes its configuration/groups, migrates a database, or reads business attachments.
Only synthetic media is converted. Credentials remain in private server files.
"""
import base64
import configparser
import datetime
import grp
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import secrets
import shutil
import signal
import socket
import stat
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
import zlib

PARENT = Path('/root/releases/20260915-0912')
RELEASE = PARENT / 'Dianxinyun-update-20260915-0912'
STATE = PARENT / 'validation-093640'
REPORT = STATE / 'runtime-validation-r2'
ROOT = Path('/var/lib/dxy-runtime-check-20260915-0912-r2')
WORK = ROOT / 'work'
DB = 'dxycheck20260915093640'
ACCOUNT = 'dxycheck0915093640'
SERVICE = 'site-platform.service'
PREFIX = 'dxy-runtime-check-20260915-0912-r2'
APP_PORT, REDIS_PORT = 18085, 16385
SOURCE_SHA = '95e6a1fc24f569b3c9224d01971d3193b4dd40044401023e9b94393ea408c9af'
JAR_SHA = '0de87d1833edbaa41040f836a69939aa1aca6bf447863f5aeb4dc3da2a99a921'
CONVERTER_SHA = '09c73c9a0b115563125dc4390ebbf7910ef68bf10b7f63676be52ce50a119477'
IMAGE = 'dianxinyun-material-preview:20260915-0912'
JOBS = ('photo', 'rotate', 'video', 'video-thumb', 'office', 'pdf')


class CheckFailed(Exception):
    pass


def require(condition, message):
    if not condition:
        raise CheckFailed(message)


def digest(path):
    with path.open('rb') as stream:
        h = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
        return h.hexdigest()


def run(argv, label, *, check=True, timeout=30, data=None):
    result = subprocess.run(argv, input=data, capture_output=True, timeout=timeout)
    if REPORT.is_dir():
        with (REPORT / 'commands.log').open('ab') as stream:
            stream.write(('\n[' + label + ']\n').encode())
            stream.write(result.stdout + result.stderr)
    require(not check or result.returncode == 0, label + '未通过，原始输出已保留在服务器私有日志')
    return result


def prop(unit, name):
    return run(['systemctl', 'show', unit, '--property=' + name, '--value'],
               '读取服务状态').stdout.decode().strip()


def private_file(path):
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and not info.st_mode & 0o077,
            '私有配置文件属主或权限不符')


def create_work_directories(uid, gid):
    ROOT.mkdir(mode=0o750)
    os.chown(ROOT, 0, gid)
    # The parent process uses umask 077 for credentials. mkdir(0750) alone
    # therefore creates 0700; explicitly grant only the service group access.
    ROOT.chmod(0o750)
    WORK.mkdir(mode=0o700)
    os.chown(WORK, uid, gid)
    run(['runuser', '-u', 'site-platform', '-g', 'site-platform', '--', '/bin/sh', '-ec',
         'cd "$1" && test -w .', 'sh', str(WORK)], '核对服务账号可进入验证工作目录')


def qa_url(value):
    require(value.startswith('jdbc:mysql://'), '当前 DB_URL 不是预期的 MySQL URL')
    parsed = urllib.parse.urlsplit(value[5:])
    require(parsed.hostname in {'localhost', '127.0.0.1'} and (parsed.port or 3306) == 3306
            and parsed.path == '/dianxinyun' and not parsed.username and not parsed.password
            and not parsed.fragment, '数据库不是已确认的本机正式库，请反馈后再继续')
    query = urllib.parse.parse_qs(parsed.query, keep_blank_values=True)
    lowered = {key.lower(): values for key, values in query.items()}
    require(len(lowered) == len(query) and all(len(values) == 1 for values in lowered.values()), 'DB_URL 含重复参数')
    require(not any(key in lowered for key in ('user', 'password', 'socketfactory')),
            'DB_URL 含需要单独核对的连接参数')
    require(lowered.get('allowpublickeyretrieval', ['false'])[0].lower() != 'true',
            '当前 DB_URL 未满足生产安全门禁')
    tls = lowered.get('sslmode', [''])[0].upper() in {'REQUIRED', 'VERIFY_CA', 'VERIFY_IDENTITY'}
    tls = tls or lowered.get('usessl', ['false'])[0].lower() == 'true'
    require(tls, '当前 DB_URL 没有显式启用 TLS')
    return 'jdbc:' + urllib.parse.urlunsplit(parsed._replace(path='/' + DB))


def rehearsal_complete(report):
    return (report.get('complete') is True and report.get('tables') == 114
            and report.get('markers') == 37 and report.get('originalRowsPreserved') is True
            and report.get('repeatDataAndSchemaUnchanged') is True
            and report.get('productionMigrated') is False)


def isolated_environment(live, password, redis_password):
    required = ('DB_URL', 'JWT_SECRET', 'VISITOR_DATA_ENCRYPTION_KEY',
                'SEAL_SCENE_ENCRYPTION_KEY', 'DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY',
                'WECHAT_MINI_PROGRAM_APP_ID', 'WECHAT_MINI_PROGRAM_APP_SECRET',
                'WECHAT_MINI_PROGRAM_LEGAL_DOMAIN', 'WECHAT_MINI_PROGRAM_PUBLIC_FALLBACK_URL')
    for key in required:
        require(bool(live.get(key, '').strip()), '运行中的服务缺少配置项：' + key)
    env = {key: live[key] for key in required}
    env.update(DB_URL=qa_url(live['DB_URL']), DB_USERNAME=ACCOUNT, DB_PASSWORD=password,
               REDIS_HOST='127.0.0.1', REDIS_PORT=str(REDIS_PORT), REDIS_PASSWORD=redis_password,
               REDIS_DATABASE='0', SPRING_PROFILES_ACTIVE='prod', APP_SCHEDULING_ENABLED='false',
               FILE_STORAGE_TYPE='local', FORWARD_HEADERS_STRATEGY='NATIVE',
               WECHAT_MINI_PROGRAM_MOCK_ENABLED='false', WECHAT_MINI_PROGRAM_PRODUCTION='true',
               KNIFE4J_ENABLE='false', API_DOCS_ENABLED='false', SWAGGER_UI_ENABLED='false',
               MEETING_MATERIAL_CONVERTER=str(ROOT / 'converter.sh'),
               USER_IMPORT_CREDENTIAL_KEY=base64.b64encode(secrets.token_bytes(32)).decode())
    env['SPRING_APPLICATION_JSON'] = json.dumps({
        'server.address': '127.0.0.1', 'server.port': APP_PORT,
        'file.upload.path': str(WORK / 'uploads'), 'app.scheduling.enabled': False,
        'spring.datasource.hikari.maximum-pool-size': 3,
        'spring.datasource.hikari.minimum-idle': 1,
        'site-access.legacy-reencryption.enabled': False,
        'logging.level.root': 'INFO',
        'logging.level.org.springframework.boot.availability.ApplicationAvailabilityBean': 'DEBUG',
    })
    return env


def port_free(port):
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', port))


def health(port, path='/api/v1/auth/captcha'):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open('http://127.0.0.1:%d%s' % (port, path), timeout=5) as response:
            body = response.read(65536)
            return response.status, json.loads(body) if path.endswith('/captcha') else None
    except urllib.error.HTTPError as error:
        return error.code, None


def production_healthy(invocation, pid):
    require(prop(SERVICE, 'ActiveState') == 'active' and prop(SERVICE, 'InvocationID') == invocation
            and prop(SERVICE, 'MainPID') == pid, '原服务状态或进程已变化，请反馈')
    status, body = health(8080)
    require(status == 200 and body and body.get('code') == 200, '原系统验证码健康检查未通过')


def start_unit(kind, extra=()):
    unit = PREFIX + '-' + kind + '.service'
    require(prop(unit, 'LoadState') == 'not-found', '验证 unit 已存在，请勿重复执行')
    properties = [
        'User=site-platform', 'Group=site-platform', 'WorkingDirectory=' + str(WORK),
        'ProtectSystem=strict', 'ProtectHome=yes', 'PrivateTmp=yes', 'NoNewPrivileges=yes',
        'ReadWritePaths=' + str(WORK), 'RestrictSUIDSGID=yes',
        'InaccessiblePaths=/opt/site-platform /etc/site-platform' + (' /run/docker.sock' if kind in {'redis', 'app'} else ''),
        'IPAddressDeny=any', 'IPAddressAllow=127.0.0.1/32 ::1/128',
        'CPUQuota=100%', 'TasksMax=192', 'TimeoutStopSec=15s', 'KillMode=control-group',
        'Restart=no', 'RuntimeMaxSec=300s', 'UMask=0077',
        'StandardOutput=append:' + str(REPORT / (kind + '.log')),
        'StandardError=append:' + str(REPORT / (kind + '.log')),
    ]
    if kind in {'redis', 'app'}:
        properties += ['LoadCredential=runtime-config:' + str(REPORT / 'child-config.json')]
    properties += ['MemoryMax=' + ('64M' if kind == 'redis' else '704M')]
    properties += list(extra)
    args = ['systemd-run', '--quiet', '--collect', '--unit=' + unit]
    for item in properties:
        args += ['--property=' + item]
    args += ['/usr/bin/python3', str(ROOT / 'runner.py'), '--child', kind]
    run(args, '启动隔离' + kind)
    return unit


def child(kind):
    os.umask(0o077)
    require(os.geteuid() != 0 and os.geteuid() == pwd.getpwnam('site-platform').pw_uid,
            '验证子进程必须使用已确认的非 root 服务账号')
    if kind == 'media':
        media_check()
        return
    settings = json.loads((Path(os.environ['CREDENTIALS_DIRECTORY']) / 'runtime-config').read_text())
    env = {'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8', **settings}
    if kind == 'redis':
        config = WORK / 'redis.conf'
        config.write_text('bind 127.0.0.1\nport %d\nprotected-mode yes\nrequirepass %s\n'
                          'save ""\nappendonly no\ndatabases 1\nmaxmemory 32mb\n'
                          'maxmemory-policy noeviction\nloglevel warning\n'
                          'dir %s\n' % (REDIS_PORT, settings['REDIS_PASSWORD'], WORK))
        os.execve('/usr/bin/redis-server', ['redis-server', str(config)], env)
    require(kind == 'app', '未知验证子进程')
    os.execve('/usr/bin/java', ['java', '-Xms96m', '-Xmx320m', '-Xss512k',
                             '-XX:MaxMetaspaceSize=160m', '-XX:ReservedCodeCacheSize=64m',
                             '-XX:MaxDirectMemorySize=32m', '-XX:ActiveProcessorCount=1',
                             '-XX:+ExitOnOutOfMemoryError', '-jar', str(ROOT / 'backend.jar')], env)


def make_png(path):
    def chunk(name, data):
        return struct.pack('>I', len(data)) + name + data + struct.pack('>I', zlib.crc32(name + data))
    pixels = b''.join(b'\0' + bytes((40, 120, 210)) * 160 for _ in range(96))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 160, 96, 8, 2, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b''))


def media_check():
    os.environ.pop('DOCKER_CONTEXT', None)
    os.environ['DOCKER_HOST'] = 'unix:///var/run/docker.sock'
    os.environ['DOCKER_CONFIG'] = str(WORK / 'docker-config')
    (WORK / 'docker-config').mkdir(mode=0o700)
    locations = {}
    for name in JOBS:
        path = WORK / (PREFIX + '-' + name)
        path.mkdir(mode=0o700)
        locations[name] = path
    def convert(name, kind, ext, rotation=0):
        path = locations[name]
        before = digest(path / ('input.' + ext))
        subprocess.run(['bash', str(ROOT / 'converter.sh'), str(path), kind, ext, str(rotation)],
                       check=True, timeout=120)
        require(digest(path / ('input.' + ext)) == before, '合成原件发生变化')
    make_png(locations['photo'] / 'input.png')
    convert('photo', 'THUMBNAIL', 'png')
    shutil.copyfile(locations['photo'] / 'input.png', locations['rotate'] / 'input.png')
    convert('rotate', 'IMAGE', 'png', 90)
    with zipfile.ZipFile(locations['office'] / 'input.docx', 'w') as document:
        document.writestr('[Content_Types].xml', '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        document.writestr('_rels/.rels', '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        document.writestr('word/document.xml', '<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>安委会巡检 附件预览验证 20260915</w:t></w:r></w:p></w:body></w:document>')
    convert('office', 'OFFICE', 'docx')
    shutil.copyfile(locations['office'] / 'output.pdf', locations['pdf'] / 'input.pdf')
    convert('pdf', 'PDF_THUMBNAIL', 'pdf')
    subprocess.run(['docker', 'run', '--rm', '--name', 'meeting-preview-' + PREFIX + '-fixture',
                    '--pull=never', '--network=none', '--read-only', '--memory=256m', '--cpus=1',
                    '--pids-limit=64', '--cap-drop=ALL', '--security-opt=no-new-privileges',
                    '--user=%d:%d' % (os.getuid(), os.getgid()),
                    '--mount=type=bind,src=%s,dst=/work' % locations['video'], IMAGE,
                    'ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i',
                    'color=c=blue:s=160x96:d=1:r=10', '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                    '-threads', '1', '/work/input.mp4'], check=True, timeout=60)
    convert('video', 'VIDEO', 'mp4', 90)
    shutil.copyfile(locations['video'] / 'output.mp4', locations['video-thumb'] / 'input.mp4')
    convert('video-thumb', 'THUMBNAIL', 'mp4')
    for name, filename, signature in [('photo', 'output.jpg', b'\xff\xd8'),
                                      ('rotate', 'output.png', b'\x89PNG'),
                                      ('office', 'output.pdf', b'%PDF'),
                                      ('pdf', 'output.jpg', b'\xff\xd8'),
                                      ('video-thumb', 'output.jpg', b'\xff\xd8')]:
        output = locations[name] / filename
        require(output.stat().st_size > 100 and output.read_bytes().startswith(signature), '转换结果无效')
    require((locations['video'] / 'output.mp4').stat().st_size > 100, '视频转换结果无效')
    (WORK / 'media-result.json').write_text(json.dumps({'complete': True, 'conversions': 6,
                                                       'syntheticOnly': True, 'originalsPreserved': True}))
    print('照片缩略图、旋转、视频转码及封面、中文 Word/PDF 转换共 6 项通过。', flush=True)


def main():
    os.umask(0o077)
    require(os.geteuid() == 0 and sys.platform.startswith('linux'), '请在服务器 root 终端运行')
    require(not REPORT.exists() and not REPORT.is_symlink() and not ROOT.exists() and not ROOT.is_symlink(),
            '验证目录已存在，请反馈，不要重复执行')
    for command in ('systemctl', 'systemd-run', 'mysql', 'docker', 'java', 'redis-server', 'runuser'):
        require(shutil.which(command) is not None, '缺少工具：' + command)
    private_file(STATE / 'client.cnf')
    private_file(Path('/etc/site-platform/site-platform.env'))
    require(rehearsal_complete(json.loads((STATE / 'migration-rehearsal/result.json').read_text())),
            '缺少已通过的 114 表迁移双跑报告')
    require(digest(RELEASE / 'artifacts/backend.jar') == JAR_SHA, '新 JAR 摘要不符')
    require(digest(RELEASE / 'runtime/meeting-material-convert.sh') == CONVERTER_SHA, '转换脚本摘要不符')
    require(prop(SERVICE, 'User') == 'site-platform' and prop(SERVICE, 'Group') == 'site-platform',
            '正式服务账号发生变化')
    invocation, pid = prop(SERVICE, 'InvocationID'), prop(SERVICE, 'MainPID')
    require(invocation and pid.isdecimal() and int(pid) > 0, '正式服务没有有效进程')
    production_healthy(invocation, pid)
    available = int(re.search(r'^MemAvailable:\s+(\d+)', Path('/proc/meminfo').read_text(), re.M).group(1))
    require(available >= 1024 * 1024, '可用内存低于 1 GiB，未启动副本，请反馈')
    port_free(APP_PORT)
    port_free(REDIS_PORT)
    live = dict(item.split('=', 1) for item in Path('/proc/' + pid + '/environ').read_bytes().decode().split('\0') if '=' in item)
    cnf = configparser.ConfigParser(interpolation=None)
    cnf.read(STATE / 'client.cnf')
    require(set(cnf.sections()) == {'client'} and cnf['client'].get('user') == ACCOUNT,
            '隔离账号配置不符')
    env = isolated_environment(live, cnf['client']['password'], secrets.token_hex(32))
    os.environ.pop('DOCKER_CONTEXT', None)
    os.environ['DOCKER_HOST'] = 'unix:///var/run/docker.sock'
    image = json.loads(run(['docker', 'image', 'inspect', IMAGE], '读取转换镜像').stdout)[0]
    require(image['Architecture'] == 'amd64' and image['Os'] == 'linux'
            and image['Config']['Labels'].get('dxy.source-manifest-sha256') == SOURCE_SHA,
            '转换镜像归属或架构不符')
    alias = json.loads(run(['docker', 'image', 'inspect', 'dianxinyun-material-preview:local'], '读取转换镜像别名').stdout)[0]
    require(alias['Id'] == image['Id'], '转换镜像兼容标签指向不同版本')
    for kind in ('redis', 'app', 'media'):
        require(prop(PREFIX + '-' + kind + '.service', 'LoadState') == 'not-found',
                '验证 unit 已存在，请反馈，不自动覆盖或停止')
    for name in (*JOBS, 'fixture'):
        existing = run(['docker', 'container', 'inspect', 'meeting-preview-' + PREFIX + '-' + name],
                       '核对合成容器名称', check=False)
        require(existing.returncode != 0 and b'No such' in existing.stderr,
                '合成容器名称被占用或无法检查，请反馈')
    REPORT.mkdir(mode=0o700)
    mysql = ['mysql', '--defaults-file=' + str(STATE / 'client.cnf'), '--user=' + ACCOUNT,
             '--protocol=TCP', '--host=127.0.0.1', '--port=3306', '--ssl-mode=REQUIRED',
             '--database=' + DB, '--connect-timeout=5', '-N', '-B']
    rows = run(mysql + ['-e', 'SELECT DATABASE(),CURRENT_USER(); SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type="BASE TABLE"; SELECT COUNT(*) FROM sys_data_migration; SHOW STATUS LIKE "Ssl_cipher";'], '核对副本 TCP/TLS 连接').stdout.decode().splitlines()
    require(len(rows) == 4 and rows[0] == DB + '\t' + ACCOUNT + '@localhost'
            and rows[1:3] == ['114', '37'] and rows[3].startswith('Ssl_cipher\t')
            and len(rows[3].split('\t', 1)[1]) > 0, '隔离库、账号、表数、标记或 TLS 不符')
    denied = run(mysql + ['-e', 'SELECT 1 FROM dianxinyun.sys_user LIMIT 1'], '复核正式库隔离', check=False)
    require(denied.returncode != 0 and b'ERROR 1142' in denied.stderr and b'SELECT command denied' in denied.stderr,
            '副本账号未能证明与正式库隔离')
    print('1/3 配置与隔离通过；使用 114 表副本、独立 Redis、正式 prod 门禁。', flush=True)
    uid, gid = pwd.getpwnam('site-platform').pw_uid, grp.getgrnam('site-platform').gr_gid
    require(uid != 0, '服务账号不能映射为 root')
    create_work_directories(uid, gid)
    for source, target in ((RELEASE / 'artifacts/backend.jar', ROOT / 'backend.jar'),
                           (RELEASE / 'runtime/meeting-material-convert.sh', ROOT / 'converter.sh'),
                           (Path(__file__).resolve(), ROOT / 'runner.py')):
        shutil.copyfile(source, target)
        target.chmod(0o444)
    (REPORT / 'child-config.json').write_text(json.dumps(env))
    units = []
    checks = {}
    try:
        for kind in ('redis', 'app'):
            unit = start_unit(kind)
            units.append(unit)
            if kind == 'redis':
                for attempt in range(30):
                    try:
                        with socket.create_connection(('127.0.0.1', REDIS_PORT), timeout=1):
                            break
                    except OSError:
                        time.sleep(1)
                else:
                    raise CheckFailed('独立 Redis 未就绪')
        for attempt in range(90):
            require(prop(units[-1], 'ActiveState') in {'active', 'activating'}, '新后端副本启动失败，请保留私有 app.log')
            try:
                status, body = health(APP_PORT)
                ready_log = (REPORT / 'app.log').read_text(errors='replace') if (REPORT / 'app.log').exists() else ''
                if status == 200 and body and body.get('code') == 200 and 'ACCEPTING_TRAFFIC' in ready_log:
                    break
            except (OSError, ValueError):
                pass
            if attempt % 15 == 0:
                print('正在等待副本启动（原系统继续运行）…', flush=True)
                production_healthy(invocation, pid)
            time.sleep(2)
        else:
            raise CheckFailed('副本启动超时，请保留 app.log')
        log = (REPORT / 'app.log').read_text(errors='replace')
        require('Started SitePlatformApplication' in log and 'HikariPool-1 - Start completed' in log,
                '副本缺少 Spring/Hikari 就绪证据')
        for path in ('/doc.html', '/swagger-ui/index.html', '/v3/api-docs'):
            require(health(APP_PORT, path)[0] == 404, '副本 API 文档关闭检查未通过')
        for unit in reversed(units):
            run(['systemctl', 'stop', unit], '停止已完成的副本验证')
        require(all(prop(unit, 'ActiveState') in {'inactive', 'failed'} for unit in units), '副本进程未完全停止')
        checks['backendProdStartup'] = True
        print('2/3 新后端副本启动、数据库/Redis 与 API 文档关闭检查通过；已停止副本释放内存。', flush=True)
        production_healthy(invocation, pid)
        media_unit = start_unit('media', ['SupplementaryGroups=docker'])
        units.append(media_unit)
        for attempt in range(150):
            if (WORK / 'media-result.json').is_file():
                checks['media'] = json.loads((WORK / 'media-result.json').read_text())
                break
            require(prop(media_unit, 'ActiveState') in {'active', 'activating'}, '服务账号附件转换未通过，请保留 media.log')
            time.sleep(2)
        else:
            raise CheckFailed('附件转换验证超时')
        require(checks['media'].get('complete') is True, '附件转换结果不完整')
        print('3/3 服务账号、PrivateTmp 与只读系统限制下的 6 项合成附件转换通过。', flush=True)
        production_healthy(invocation, pid)
        checks.update(productionChanged=False, sourceManifest=SOURCE_SHA,
                      jarSha256=JAR_SHA, imageId=image['Id'], completedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
    finally:
        cleanup_errors = []
        for unit in reversed(units):
            try:
                run(['systemctl', 'stop', unit], '清理验证进程', check=False)
            except (OSError, subprocess.SubprocessError):
                cleanup_errors.append('验证进程')
        # Remove only this script's fixed, synthetic conversion containers.
        for name in (*JOBS, 'fixture'):
            try:
                run(['docker', 'rm', '-f', 'meeting-preview-' + PREFIX + '-' + name], '清理合成转换容器', check=False)
            except (OSError, subprocess.SubprocessError):
                cleanup_errors.append('合成转换容器')
        for path in (REPORT / 'child-config.json', WORK / 'redis.conf'):
            if path.is_file():
                path.unlink()
    require(not cleanup_errors, '验证清理命令未全部结束，请保留目录并反馈')
    require(all(prop(unit, 'ActiveState') in {'inactive', 'failed'} for unit in units),
            '验证进程尚未完全停止，请反馈')
    production_healthy(invocation, pid)
    checks['complete'] = True
    (REPORT / 'result.json').write_text(json.dumps(checks, indent=2))
    print('副本启动和转换验证完成；正式服务、正式数据库和配置均未切换。', flush=True)
    print('结果目录：' + str(REPORT), flush=True)
    print('正式批量导入密钥：' + ('已配置' if live.get('USER_IMPORT_CREDENTIAL_KEY') else '待配置'), flush=True)
    print('正式转换器入口：' + ('已配置，待核对版本' if live.get('MEETING_MATERIAL_CONVERTER') else '待配置'), flush=True)


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(CheckFailed('操作中断')))
    try:
        if len(sys.argv) == 3 and sys.argv[1] == '--child':
            child(sys.argv[2])
        else:
            require(len(sys.argv) == 1, '此脚本不接受服务器或数据库覆盖参数')
            main()
    except (Exception, KeyboardInterrupt) as error:
        # Never print exception details from libraries: they may include connection data.
        print('未完成：' + (str(error) if isinstance(error, CheckFailed) else '系统操作未通过，请反馈结果并保留验证目录'), file=sys.stderr)
        sys.exit(1)
