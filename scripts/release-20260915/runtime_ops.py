"""Frozen 2026-09-15 runtime staging. Caller owns locks, backup and service lifecycle.

No service restart, database action, Docker operation or Nginx mutation is performed.
"""
from __future__ import annotations

import base64
import grp
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import stat
import tarfile
import tempfile

JAR_SHA = '0de87d1833edbaa41040f836a69939aa1aca6bf447863f5aeb4dc3da2a99a921'
CONVERTER_SHA = '09c73c9a0b115563125dc4390ebbf7910ef68bf10b7f63676be52ce50a119477'
SOURCE_SHA = '95e6a1fc24f569b3c9224d01971d3193b4dd40044401023e9b94393ea408c9af'
WEB = {
    'transition': ('Dianxinyun-web-transition-prod-20260915-091028.tar.gz', '2e1fc5bdcba827d1b19b722a94e873cc9181e9aa348e118e2db33a286a16de6e'),
    'final': ('Dianxinyun-web-final-prod-20260915-091028.tar.gz', 'e1463d7a3fbe9efd610639cd8d789cc9376ffbd3933ee2fe97d4dba2a54cfdc1'),
}
CHUNK_PATHS = (
    r'^/api/v1/safety-committee/uploads/[^/]+/chunks/[0-9]+$',
    r'^/api/v1/site-access/invitations/[0-9]+/material-uploads/[^/]+/chunks/[0-9]+$',
)


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def plain_path(path, *, file=False):
    path = Path(path)
    require(path.is_absolute(), '路径必须是绝对路径')
    require('..' not in path.parts, '路径不得包含上级目录')
    for part in (path, *path.parents):
        require(not part.is_symlink(), '路径不得包含符号链接')
    if file:
        require(path.is_file(), '所需文件不存在或不是普通文件')
    return path


def parse_pairs(text):
    result = {}
    for line in text.splitlines():
        if not line or line.startswith('#'):
            continue
        require('=' in line, '发布清单格式不符')
        key, value = line.split('=', 1)
        require(key not in result, '发布清单键重复')
        result[key] = value
    return result


def _verify_web_members(archive, variant):
    """Check archive paths and manifests before writing a single archive byte."""
    members = archive.getmembers()
    names = set()
    files = {}
    require(len(members) <= 2000, 'Web 包成员过多')
    for member in members:
        name = member.name.rstrip('/')
        path = PurePosixPath(name)
        require(name and not path.is_absolute() and '..' not in path.parts,
                'Web 包路径越界')
        require(str(path) == name and '\\' not in name and '\x00' not in name,
                'Web 包路径不规范')
        require(name not in names, 'Web 包存在重复成员')
        names.add(name)
        require(path.parts[0] in {'dist', 'ARTIFACT_FILES.sha256', 'RELEASE_MANIFEST.txt'},
                'Web 包顶层成员不符')
        require(member.isfile() or member.isdir(), 'Web 包含链接或特殊文件')
        require(not any(part.startswith('._') for part in path.parts), 'Web 包含 AppleDouble')
        require(not any(any(x in key.lower() for x in ('xattr', 'acl', 'quarantine'))
                        for key in member.pax_headers), 'Web 包含扩展权限')
        require(member.uid == member.gid == 0 and member.uname == member.gname == 'root',
                'Web 包所属用户不符')
        require(member.mode == (0o755 if member.isdir() else 0o644), 'Web 包权限不符')
        if member.isfile():
            require(member.size <= 16 * 1024 * 1024, 'Web 包单文件过大')
            files[name] = member
    require(sum(m.size for m in files.values()) <= 32 * 1024 * 1024, 'Web 包总大小异常')
    for name in ('ARTIFACT_FILES.sha256', 'RELEASE_MANIFEST.txt'):
        require(name in files and files[name].size < 1024 * 1024, 'Web 校验清单缺失或异常')
    manifest = parse_pairs(archive.extractfile(files['RELEASE_MANIFEST.txt']).read().decode('utf-8'))
    require(manifest.get('SOURCE_MANIFEST_SHA256') == SOURCE_SHA, 'Web 来源摘要不符')
    require(manifest.get('VARIANT') == variant, 'Web 变体不符')
    require(manifest.get('MEETING_CREATION_ENABLED') == ('true' if variant == 'final' else 'false'),
            'Web 会议开关不符')
    require(manifest.get('API_BASE') == '/api/v1', 'Web API 地址不符')
    expected = {}
    for line in archive.extractfile(files['ARTIFACT_FILES.sha256']).read().decode('utf-8').splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  \./(.+)', line)
        require(match is not None, 'Web 文件摘要格式不符')
        checksum, rel = match.groups()
        path = PurePosixPath(rel)
        require(not path.is_absolute() and '..' not in path.parts and str(path) == rel,
                'Web 摘要路径不规范')
        name = 'dist/' + rel
        require(name not in expected, 'Web 文件摘要重复')
        expected[name] = checksum
    dist_files = {name for name in files if name.startswith('dist/')}
    require(set(expected) == dist_files and 'dist/index.html' in dist_files,
            'Web 文件清单不完整')
    require(int(manifest.get('FILE_COUNT', '-1')) == len(dist_files), 'Web 文件数量不符')
    require(int(manifest.get('TOTAL_BYTES', '-1')) == sum(files[x].size for x in dist_files),
            'Web 文件字节数不符')
    for name, checksum in expected.items():
        require(hashlib.sha256(archive.extractfile(files[name]).read()).hexdigest() == checksum,
                'Web 文件内容摘要不符')
    return manifest, files


def verify_web(path, variant='transition'):
    require(variant in WEB, '不支持的 Web 变体')
    path = plain_path(path, file=True)
    require(digest(path) == WEB[variant][1], 'Web 冻结归档摘要不符')
    with tarfile.open(path, 'r:gz') as archive:
        manifest, _ = _verify_web_members(archive, variant)
    return manifest


def _permission(path, mode, gid):
    os.chown(path, 0, gid)
    os.chmod(path, mode)  # Explicit after umask, including every parent created here.


def stage(release_bundle: Path, dest: Path, variant='transition') -> dict:
    """Install into a NEW inactive release directory; never switch live symlinks."""
    require(os.geteuid() == 0, '运行部署暂存需要 root')
    require(variant in WEB, '不支持的 Web 变体')
    bundle, dest = plain_path(release_bundle), plain_path(dest)
    require(bundle.is_dir() and dest.parent.is_dir(), '发布目录或父目录不存在')
    require(not dest.exists(), '新版暂存目录已存在，拒绝覆盖')
    app_gid, web_gid = grp.getgrnam('site-platform').gr_gid, grp.getgrnam('www-data').gr_gid
    jar = plain_path(bundle / 'artifacts/backend.jar', file=True)
    converter = plain_path(bundle / 'runtime/meeting-material-convert.sh', file=True)
    source = plain_path(bundle / 'source-reference/SOURCE_MANIFEST.txt', file=True)
    require(digest(jar) == JAR_SHA, '冻结 JAR 摘要不符')
    require(digest(converter) == CONVERTER_SHA, '冻结转换脚本摘要不符')
    require(digest(source) == SOURCE_SHA, '冻结源码清单摘要不符')
    release_manifest = json.loads(plain_path(bundle / 'RELEASE-MANIFEST.json', file=True).read_text())
    require(release_manifest.get('releaseId') == 'Dianxinyun-update-20260915-0912'
            and release_manifest.get('sourceManifestSha256') == SOURCE_SHA, '更新包来源不符')
    web_path = bundle / 'artifacts' / WEB[variant][0]
    manifest = verify_web(web_path, variant)
    temp = Path(tempfile.mkdtemp(prefix='.' + dest.name + '-', dir=dest.parent))
    try:
        # Public parent traversal is needed by www-data; backend remains group-only.
        _permission(temp, 0o755, app_gid)
        for name, mode, gid in [('backend', 0o750, app_gid), ('runtime', 0o755, app_gid),
                                ('web-' + variant, 0o755, web_gid)]:
            (temp / name).mkdir()
            _permission(temp / name, mode, gid)
        shutil.copyfile(jar, temp / 'backend/site-platform.jar')
        _permission(temp / 'backend/site-platform.jar', 0o640, app_gid)
        shutil.copyfile(converter, temp / 'runtime/meeting-material-convert.sh')
        _permission(temp / 'runtime/meeting-material-convert.sh', 0o755, app_gid)
        web_dir = temp / ('web-' + variant)
        with tarfile.open(web_path, 'r:gz') as archive:
            _, files = _verify_web_members(archive, variant)
            for name, member in files.items():
                target = web_dir / name
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.extractfile(member) as src, target.open('xb') as dst:
                    shutil.copyfileobj(src, dst)
                _permission(target, 0o644, web_gid)
        for directory in web_dir.rglob('*'):
            if directory.is_dir():
                _permission(directory, 0o755, web_gid)
        require(digest(temp / 'backend/site-platform.jar') == JAR_SHA
                and digest(temp / 'runtime/meeting-material-convert.sh') == CONVERTER_SHA,
                '暂存副本摘要不符')
        require(not dest.exists() and not dest.is_symlink(), '新版暂存目标发生变化')
        temp.rename(dest)
    except BaseException:
        shutil.rmtree(temp, ignore_errors=True)
        raise
    return {'release_dir': str(dest), 'jar': str(dest / 'backend/site-platform.jar'),
            'web': str(dest / ('web-' + variant) / 'dist'),
            'converter': str(dest / 'runtime/meeting-material-convert.sh'),
            'variant': variant, 'source_manifest_sha256': SOURCE_SHA,
            'web_release_marker': manifest['RELEASE_MARKER'], 'created_paths': [str(dest)]}


def prepare_env(raw: str, converter_path: Path):
    """Pure renderer: preserve original lines/old secrets, return no secret metadata."""
    require(str(converter_path).startswith('/') and re.fullmatch(r'/[A-Za-z0-9_./-]+', str(converter_path)),
            '转换器路径格式不符')
    require('..' not in converter_path.parts, '转换器路径越界')
    entries = {}
    lines = raw.splitlines(keepends=True)
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith('#') or stripped.startswith(';'):
            continue
        match = re.fullmatch(r'([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)', stripped)
        require(match is not None, '正式环境文件包含不支持的多行或非赋值内容')
        key, value = match.groups()
        require(key not in entries, '正式环境文件存在重复配置键')
        require(not key.startswith('SITE_ACCESS_LEGACY_REENCRYPTION_')
                and key not in {'ADMIN_RESET_USERNAME', 'ADMIN_RESET_PASSWORD'}
                and 'visitor-legacy-reencrypt' not in value, '正式环境包含一次性维护参数')
        entries[key] = (index, value)
    def value(key):
        val = entries.get(key, (None, ''))[1].strip()
        if len(val) >= 2 and val[0] == val[-1] and val[0] in '\"\'':
            val = val[1:-1]
        return val
    scheduling = value('APP_SCHEDULING_ENABLED')
    require('APP_SCHEDULING_ENABLED' not in entries or scheduling == 'true',
            '正式调度配置不是 true，拒绝继承验证环境设置')
    existing = value('USER_IMPORT_CREDENTIAL_KEY')
    generated = not bool(existing)
    if existing:
        try:
            decoded = base64.b64decode(existing, validate=True)
        except (ValueError, TypeError):
            raise ValueError('现有批量导入密钥格式非法') from None
        require(len(decoded) == 32 and base64.b64encode(decoded).decode() == existing,
                '现有批量导入密钥必须是 32 字节 Base64')
    updates = {'MEETING_MATERIAL_CONVERTER': str(converter_path)}
    if generated:
        updates['USER_IMPORT_CREDENTIAL_KEY'] = base64.b64encode(secrets.token_bytes(32)).decode()
    if 'APP_SCHEDULING_ENABLED' not in entries:
        updates['APP_SCHEDULING_ENABLED'] = 'true'
    for key, val in updates.items():
        if key in entries:
            lines[entries[key][0]] = key + '=' + val + '\n'
        else:
            if lines and not lines[-1].endswith('\n'):
                lines[-1] += '\n'
            lines.append(key + '=' + val + '\n')
    return ''.join(lines), {'credential_key_generated': generated}


def configure(env_path: Path, converter_path: Path, dropin_path: Path) -> dict:
    """Call only after the caller stopped the service and backed up all old config."""
    require(os.geteuid() == 0, '配置正式运行环境需要 root')
    env_path = plain_path(env_path, file=True)
    converter_path = plain_path(converter_path, file=True)
    dropin_path = plain_path(dropin_path)
    require(env_path.stat().st_uid == 0 and stat.S_ISREG(env_path.stat().st_mode),
            '正式环境文件所属用户不符')
    require(digest(converter_path) == CONVERTER_SHA, '正式转换脚本摘要不符')
    require(not dropin_path.exists(), '转换器专用服务覆盖文件已存在，拒绝覆盖')
    grp.getgrnam('docker')
    raw = env_path.read_bytes()
    updated, info = prepare_env(raw.decode('utf-8'), converter_path)
    made_directory = not dropin_path.parent.exists()
    require(dropin_path.parent.parent.is_dir(), 'systemd 配置父目录不存在')
    temp_fd, temp_name = tempfile.mkstemp(prefix='.' + env_path.name + '.', dir=env_path.parent)
    created_dropin = False
    try:
        with os.fdopen(temp_fd, 'wb') as stream:
            os.fchown(stream.fileno(), 0, 0)
            os.fchmod(stream.fileno(), 0o600)
            stream.write(updated.encode('utf-8'))
            stream.flush()
            os.fsync(stream.fileno())
        if made_directory:
            dropin_path.parent.mkdir()
            _permission(dropin_path.parent, 0o755, 0)
        with dropin_path.open('xb') as stream:
            created_dropin = True
            os.fchown(stream.fileno(), 0, 0)
            os.fchmod(stream.fileno(), 0o644)
            stream.write(b'[Service]\nSupplementaryGroups=docker\n')
            stream.flush()
            os.fsync(stream.fileno())
        require(env_path.read_bytes() == raw, '正式环境文件发生并发变化，拒绝覆盖')
        os.replace(temp_name, env_path)
    except BaseException:
        if created_dropin:
            dropin_path.unlink(missing_ok=True)
        if made_directory and dropin_path.parent.exists():
            try:
                dropin_path.parent.rmdir()
            except OSError:
                pass
        raise
    finally:
        Path(temp_name).unlink(missing_ok=True)
    return {**info, 'env_path': str(env_path), 'dropin_path': str(dropin_path),
            'created_paths': ([str(dropin_path.parent)] if made_directory else []) + [str(dropin_path)]}
