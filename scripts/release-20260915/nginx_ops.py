"""Clone the enabled document chunk proxy for two new chunk routes.

prepare() only reads Nginx configuration and writes private diagnostic files.
The deployment caller owns service locks and the completed same-point backup.
apply() writes config atomically and runs nginx -t, NEVER reloads Nginx.
Unsupported/ambiguous layouts fail in prepare(), before maintenance starts.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile

ROOT = Path('/etc/nginx')
SYSTEM_INCLUDE_ROOTS = (Path('/usr/share/nginx'), Path('/usr/lib/nginx'))
ROUTES = (
    '^/api/v1/safety-committee/uploads/[^/]+/chunks/[0-9]+$',
    '^/api/v1/site-access/invitations/[0-9]+/material-uploads/[^/]+/chunks/[0-9]+$',
)


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def tokens(text):
    """Nginx word/quote/comment lexer with source offsets; no text substitutions."""
    result, i = [], 0
    while i < len(text):
        if text[i].isspace():
            i += 1
            continue
        if text[i] == '#':
            end = text.find('\n', i)
            i = len(text) if end < 0 else end + 1
            continue
        start = i
        if text[i] in '{};':
            result.append((text[i], i, i + 1))
            i += 1
            continue
        value, quote = '', None
        while i < len(text):
            char = text[i]
            if quote:
                if char == quote:
                    quote = None
                    i += 1
                    continue
                if char == '\\' and i + 1 < len(text):
                    nxt = text[i + 1]
                    # Keep regex escapes, decode the escapes Nginx itself decodes.
                    value += nxt if nxt in ('\\', '"', "'") else '\\' + nxt
                    i += 2
                    continue
                value += char
                i += 1
                continue
            if char in ('"', "'"):
                quote = char
                i += 1
                continue
            if char == '$' and i + 1 < len(text) and text[i + 1] == '{':
                end = text.find('}', i + 2)
                require(end >= 0, 'Nginx 变量缺少结束括号')
                value += text[i:end + 1]
                i = end + 1
                continue
            if char == '\\' and i + 1 < len(text):
                nxt = text[i + 1]
                value += nxt if nxt in ('\\', '"', "'", ' ', '#', ';') else '\\' + nxt
                i += 2
                continue
            if char.isspace() or char in '{};#':
                break
            value += char
            i += 1
        require(quote is None and i > start, 'Nginx 配置引号或语法不受支持')
        result.append((value, start, i))
    return result


def parse(text):
    ts = tokens(text)
    position = 0

    def nodes(nested=False):
        nonlocal position
        result, header = [], []
        while position < len(ts):
            token = ts[position]
            position += 1
            value = token[0]
            if value == '}':
                require(nested and not header, 'Nginx 配置括号或分号不完整')
                return result, token
            if value == '{':
                require(header, 'Nginx 配置块缺少名称')
                children, closing = nodes(True)
                result.append({'header': header, 'children': children,
                               'open': token, 'close': closing})
                header = []
            elif value == ';':
                require(header, 'Nginx 存在空指令')
                result.append({'header': header, 'children': None, 'end': token})
                header = []
            else:
                header.append(token)
        require(not nested and not header, 'Nginx 配置块没有完整结束')
        return result, None

    return nodes()[0]


def _walk(nodes):
    for node in nodes:
        yield node
        if node['children'] is not None:
            yield from _walk(node['children'])


def _body(node):
    return [[token[0] for token in child['header']] for child in node['children']]


def _api_locations(tree):
    return [node for node in _walk(tree) if node['header'][0][0] == 'location'
            and len(node['header']) >= 2 and node['header'][-1][0] in ('/api/', '/api/v1/')]


def _render_api_fallback(text, tree):
    """Use a direct API prefix with unchanged URI semantics.

    The observed production /api/v1/ -> http://127.0.0.1:8080/api/v1/ maps
    the URI identically. Remove ONLY that identical URI prefix in the new regex
    location. Different URI mappings, variables and rewrites remain unsupported.
    """
    candidates = _api_locations(tree)
    if not candidates:
        return text, 0
    servers = [node for node in _walk(tree) if node['header'][0][0] == 'server'
               and node['children'] is not None]
    edits, accounted, count = [], [], 0
    for server in servers:
        local = [node for node in server['children'] if node in candidates]
        if not local:
            continue
        require(len({node['header'][-1][0] for node in local}) == len(local),
                '同一 server 的 API 前缀代理重复，拒绝猜测')
        template = max(local, key=lambda node: len(node['header'][-1][0]))
        require([t[0] for t in template['header']] == ['location', template['header'][-1][0]],
                'API 代理带 ^~ 或 = 修饰，需核对正则匹配优先级')
        require(template['children'] is not None
                and all(node['children'] is None for node in template['children']),
                'API 代理含嵌套配置，需核对后再适配')
        body = _body(template)
        forbidden = {'include', 'rewrite', 'return', 'try_files', 'alias', 'root', 'error_page', 'internal'}
        require(not any(row[0] in forbidden for row in body),
                'API 代理含 URI 改写/include/特殊处理，需核对后再适配')
        proxies = [row for row in body if row[0] == 'proxy_pass']
        require(len(proxies) == 1 and len(proxies[0]) == 2,
                'API proxy_pass 缺失或重复，需核对实际转发语义')
        proxy = re.fullmatch(r'(https?://(?:[A-Za-z0-9_.-]+|\[[0-9a-fA-F:]+\])(?::[0-9]+)?)(/[^?#]*)?', proxies[0][1])
        require(proxy is not None and proxy.group(2) in (None, template['header'][-1][0]),
                'API proxy_pass 含不同 URI、变量或格式不明确，需核对实际转发语义')
        proxy_origin = proxy.group(1)
        # Do not override an existing more-specific authenticated API scope or an
        # earlier regular expression that could currently handle these requests.
        bases = ('/api/v1/safety-committee/uploads/', '/api/v1/site-access/invitations/')
        probes = ('/api/v1/safety-committee/uploads/sample-session/chunks/0',
                  '/api/v1/site-access/invitations/1/material-uploads/sample-session/chunks/0')
        for node in server['children']:
            h = [token[0] for token in node['header']]
            if h[0] != 'location' or node in local or h[-1] in ROUTES:
                continue
            if len(h) == 2 or (len(h) == 3 and h[1] in ('^~', '=')):
                prefix = h[-1]
                if prefix == '/':
                    require(len(h) == 2, '根位置 ^~/= 影响 API 正则优先级')
                    continue
                require(not any(base.startswith(prefix) or prefix.startswith(base) for base in bases),
                        '存在更具体的附件 API 位置，不能从通用代理复制权限')
            elif len(h) == 3 and h[1] in ('~', '~*'):
                try:
                    pattern = re.compile(h[2], re.I if h[1] == '~*' else 0)
                except re.error:
                    raise ValueError('存在无法核对的 API 正则优先级') from None
                require(not any(pattern.search(probe) for probe in probes)
                        and not any(term in h[2] for term in ('safety-committee', 'material-uploads')),
                        '现有正则可能覆盖附件 API，需要核对实际代理')
            else:
                raise ValueError('存在不支持的位置规则，需核对 API 代理优先级')
        replacements = {'client_max_body_size': '10m', 'proxy_request_buffering': 'off',
                        'proxy_read_timeout': '300s', 'proxy_send_timeout': '300s'}
        body_start, body_end = template['open'][2], template['close'][1]
        body_text = text[body_start:body_end]
        changes = []
        proxy_node = next(node for node in template['children'] if node['header'][0][0] == 'proxy_pass')
        changes.append((proxy_node['header'][0][1] - body_start, proxy_node['end'][2] - body_start,
                        'proxy_pass ' + proxy_origin + ';'))
        for key, value in replacements.items():
            nodes = [node for node in template['children'] if node['header'][0][0] == key]
            require(len(nodes) <= 1, 'API 代理的分片参数重复')
            if nodes:
                node = nodes[0]
                require(len(node['header']) == 2, 'API 代理的分片参数格式不符')
                changes.append((node['header'][0][1] - body_start, node['end'][2] - body_start,
                                key + ' ' + value + ';'))
        for start, end, value in sorted(changes, reverse=True):
            body_text = body_text[:start] + value + body_text[end:]
        start = template['header'][0][1]
        indent = text[text.rfind('\n', 0, start) + 1:start]
        if indent.strip():
            indent = '    '
        for key, value in replacements.items():
            if not any(node['header'][0][0] == key for node in template['children']):
                body_text += '\n' + indent + '    ' + key + ' ' + value + ';'
        body_text += '\n' + indent
        expected_body = _body(parse('location ~ ' + ROUTES[0] + ' {' + body_text + '}')[0])
        additions = []
        for route in ROUTES:
            existing = [node for node in server['children'] if node['header'][0][0] == 'location'
                        and node['header'][-1][0] == route]
            if existing:
                require(len(existing) == 1
                        and [t[0] for t in existing[0]['header']] == ['location', '~', route]
                        and existing[0]['children'] is not None
                        and all(node['children'] is None for node in existing[0]['children'])
                        and _body(existing[0]) == expected_body,
                        '已有新分片规则与 API 代理不一致')
                continue
            additions.append('\n' + indent + 'location ~ ' + route + ' {' + body_text + '}')
            count += 1
        if additions:
            edits.append((template['close'][2], ''.join(additions)))
        accounted.extend(local)
    require(len(accounted) == len(candidates), 'API 代理位于独立 include 片段，无法确认所属 server')
    for offset, addition in sorted(edits, reverse=True):
        text = text[:offset] + addition + text[offset:]
    return text, count


def render(text):
    """Pure, tested transformation. Return (new text, number of added blocks)."""
    tree = parse(text)
    candidates = [node for node in _walk(tree)
                  if node['header'][0][0] == 'location'
                  and any('document-uploads' in t[0] for t in node['header'][1:])]
    if not candidates:
        return _render_api_fallback(text, tree)
    servers = [node for node in _walk(tree) if node['header'][0][0] == 'server'
               and node['children'] is not None]
    replacements, accounted, added_count = [], [], 0
    for server in servers:
        matching = [node for node in server['children'] if node in candidates]
        if not matching:
            continue
        require(len(matching) == 1, '同一 server 存在多个图纸分片规则，拒绝猜测')
        template = matching[0]
        header = template['header']
        require(len(header) == 3 and header[1][0] in ('~', '~*')
                and header[2][0].startswith('^/api/v1/document-uploads/')
                and '/chunks/' in header[2][0] and header[2][0].endswith('$'),
                '图纸分片 location 不是独立且有边界的正则规则')
        require(template['children'] is not None
                and all(n['children'] is None for n in template['children']),
                '图纸分片规则包含嵌套配置，不支持自动复制')
        body = _body(template)
        for key, expected in (('client_max_body_size', {'10m', '10M', '10485760'}),
                              ('proxy_request_buffering', {'off'}),
                              ('proxy_read_timeout', {'300', '300s'}),
                              ('proxy_send_timeout', {'300', '300s'})):
            found = [row[1:] for row in body if row[0] == key]
            require(len(found) == 1 and len(found[0]) == 1 and found[0][0] in expected,
                    '图纸分片规则的 ' + key + ' 不符合已验证部署要求')
        proxies = [row for row in body if row[0] == 'proxy_pass']
        require(len(proxies) == 1 and len(proxies[0]) == 2,
                '图纸分片规则缺少唯一 proxy_pass，拒绝猜测 upstream')
        require(not any(row[0] == 'include' for row in body),
                '图纸分片规则有 include，需人工核对有效配置')
        additions = []
        for route in ROUTES:
            locations = [node for node in server['children']
                         if node['header'][0][0] == 'location'
                         and len(node['header']) >= 2
                         and ('safety-committee/uploads' in node['header'][-1][0]
                              if 'safety-committee' in route else
                              'material-uploads' in node['header'][-1][0])]
            if locations:
                require(len(locations) == 1, '新增附件分片存在重复或交叉规则')
                existing = locations[0]
                require([t[0] for t in existing['header']] == ['location', header[1][0], route]
                        and existing['children'] is not None
                        and all(n['children'] is None for n in existing['children'])
                        and _body(existing) == body,
                        '已有附件分片规则与图纸代理配置不一致，需人工核对')
                continue
            start, end = header[0][1], template['close'][2]
            pattern_start, pattern_end = header[2][1], header[2][2]
            line_start = text.rfind('\n', 0, start) + 1
            indent = text[line_start:start]
            if indent.strip():
                indent = '    '
            clone = text[start:pattern_start] + route + text[pattern_end:end]
            additions.append('\n' + indent + clone)
            added_count += 1
        if additions:
            replacements.append((template['close'][2], ''.join(additions)))
        accounted.append(template)
    require(len(accounted) == len(candidates),
            '图纸分片规则位于 include 片段或嵌套位置，无法确定所属 server')
    for offset, addition in sorted(replacements, reverse=True):
        text = text[:offset] + addition + text[offset:]
    return text, added_count


def _checked_file(logical, *, allow_system=False):
    logical = Path(logical)
    require(logical.is_absolute(), 'Nginx 配置路径不是绝对路径')
    path = logical.resolve(strict=True)
    allowed = (ROOT, *SYSTEM_INCLUDE_ROOTS) if allow_system else (ROOT,)
    require(any(path.is_relative_to(root.resolve()) for root in allowed) and path.is_file(),
            'Nginx 配置实际文件不在允许的配置/只读系统目录内')
    info = path.stat()
    require(stat.S_ISREG(info.st_mode) and info.st_uid == 0
            and not (info.st_mode & 0o022), 'Nginx 配置所属用户或写权限不符合要求')
    data = path.read_bytes()
    require(len(data) <= 8 * 1024 * 1024, 'Nginx 单个配置文件过大')
    return path, data, info


def _private_write(path, data):
    with path.open('xb') as stream:
        os.fchmod(stream.fileno(), 0o600)
        stream.write(data)


def prepare(output_log_dir):
    require(os.geteuid() == 0, 'Nginx 配置检查需要 root')
    logs = Path(output_log_dir)
    require(logs.is_dir() and not logs.is_symlink(), '私有诊断目录不存在')
    completed = subprocess.run(['nginx', '-T'], capture_output=True, timeout=30)
    _private_write(logs / 'nginx-before.stdout', completed.stdout)
    _private_write(logs / 'nginx-before.stderr', completed.stderr)
    require(completed.returncode == 0, 'nginx -T 未通过，正式服务尚未停止')
    names = re.findall(r'^# configuration file (/[^\r\n]+):$',
                       completed.stdout.decode('utf-8'), flags=re.M)
    require(names and len(names) == len(set(names)), 'nginx -T 配置文件目录不完整或重复')
    files, seen, count, templates = [], {}, 0, 0
    for name in names:
        path, data, info = _checked_file(name, allow_system=True)
        if str(path) in seen:
            seen[str(path)]['logical_paths'].append(name)
            continue
        original = data.decode('utf-8')
        updated, added, document_locations = original, 0, []
        # Standard Ubuntu module includes resolve into /usr/share/nginx and need
        # no rewriting or full parser traversal. Still bind them to the plan hash.
        if any(term in original for term in ('document-uploads', 'safety-committee/uploads', 'material-uploads', '/api/')):
            tree = parse(original)
            document_locations = [n for n in _walk(tree) if n['header'][0][0] == 'location'
                                  and any('document-uploads' in t[0] for t in n['header'][1:])]
            api_locations = _api_locations(tree) if not document_locations else []
            if document_locations or api_locations:
                require(path.is_relative_to(ROOT.resolve()),
                        '待修改的分片/API 代理实际文件必须位于 /etc/nginx')
                updated, added = render(original)
            templates += len(api_locations)
        templates += len(document_locations)
        entry = {'path': str(path), 'logical_paths': [name], 'before_sha256': sha(data),
                 'uid': info.st_uid, 'gid': info.st_gid, 'mode': stat.S_IMODE(info.st_mode),
                 'changed': updated != original}
        if entry['changed']:
            entry.update(before=original, after=updated,
                         after_sha256=sha(updated.encode('utf-8')))
        seen[str(path)] = entry
        files.append(entry)
        count += added
    require(templates > 0, '未发现可复用的图纸分片或通用 API 代理，正式服务尚未停止')
    plan = {'format': 1, 'files': files, 'added_locations': count, 'templates': templates,
            'log_dir': str(logs)}
    _private_write(logs / 'nginx-plan.json', json.dumps(plan, ensure_ascii=False, indent=2).encode())
    return plan


def _atomic(entry, data):
    path = Path(entry['path'])
    fd, temp = tempfile.mkstemp(prefix='.' + path.name + '.', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            os.fchown(stream.fileno(), entry['uid'], entry['gid'])
            os.fchmod(stream.fileno(), entry['mode'])
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, path)
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        Path(temp).unlink(missing_ok=True)


def apply(plan):
    """Caller must finish same-point config backup before calling. No reload."""
    require(os.geteuid() == 0 and plan.get('format') == 1, 'Nginx 应用计划格式或账号不符')
    for entry in plan['files']:
        for logical in entry['logical_paths']:
            path, data, info = _checked_file(logical, allow_system=not entry['changed'])
            require(str(path) == entry['path'] and sha(data) == entry['before_sha256']
                    and info.st_uid == entry['uid'] and info.st_gid == entry['gid']
                    and stat.S_IMODE(info.st_mode) == entry['mode'],
                    'Nginx 文件或符号链接在备份后发生变化，拒绝覆盖')
        if entry['changed']:
            require(sha(entry['after'].encode()) == entry['after_sha256']
                    and sha(entry['before'].encode()) == entry['before_sha256'],
                    'Nginx 计划内容摘要不符')
    applied = []
    try:
        for entry in plan['files']:
            if entry['changed']:
                require(sha(Path(entry['path']).read_bytes()) == entry['before_sha256'],
                        '写入前 Nginx 配置发生变化')
                _atomic(entry, entry['after'].encode('utf-8'))
                applied.append(entry)
        completed = subprocess.run(['nginx', '-t'], capture_output=True, timeout=30)
        _private_write(Path(plan['log_dir']) / 'nginx-after-test.log',
                       completed.stdout + completed.stderr)
        require(completed.returncode == 0, '新 Nginx 配置检查失败')
    except BaseException:
        for entry in reversed(applied):
            require(sha(Path(entry['path']).read_bytes()) == entry['after_sha256'],
                    'Nginx 配置被并发修改，保留现场及备份')
            _atomic(entry, entry['before'].encode('utf-8'))
        raise
    return {'changed_files': [entry['path'] for entry in applied],
            'added_locations': plan['added_locations'], 'tested': True, 'reloaded': False}
