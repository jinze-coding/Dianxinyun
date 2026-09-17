import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('nginx_ops', Path(__file__).with_name('nginx_ops.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

BLOCK = r'''    location ~ "^/api/v1/document-uploads/[^/]+/chunks/[0-9]+$" {
        client_max_body_size 10m;
        proxy_request_buffering off;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        # Comments { location } must not confuse structure.
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Example "value; # } ${host}";
        proxy_pass http://127.0.0.1:8080;
    }'''


def config(block=BLOCK):
    return 'server {\n    listen 443 ssl;\n    client_max_body_size 2m;\n' + block + '''
    location /api/ { proxy_pass http://127.0.0.1:8080; }
}
'''


class RenderTests(unittest.TestCase):
    def test_observed_api_identity_prefix_gets_scoped_chunk_rules(self):
        original = '''server {
    server_name zhihuiyz.xyz;
    client_max_body_size 50m;
    location /api/v1/ {
        proxy_pass http://127.0.0.1:8080/api/v1/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        auth_request /_auth;
        proxy_connect_timeout 10s;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
    }
    location / { try_files $uri $uri/ /index.html; }
    location = /_auth { internal; proxy_pass http://127.0.0.1:8080/auth; }
}'''
        updated, count = m.render(original)
        self.assertEqual(count, 2)
        self.assertEqual(updated.count('client_max_body_size 50m;'), 1)
        self.assertEqual(updated.count('client_max_body_size 10m;'), 2)
        self.assertEqual(updated.count('proxy_pass http://127.0.0.1:8080/api/v1/;'), 1)
        self.assertEqual(updated.count('proxy_pass http://127.0.0.1:8080;'), 2)
        self.assertEqual(updated.count('auth_request /_auth;'), 3)
        self.assertEqual(updated.count('proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;'), 3)
        self.assertEqual(updated.count('proxy_connect_timeout 10s;'), 3)
        self.assertEqual(updated.count('proxy_read_timeout 300s;'), 2)
        self.assertEqual(updated.count('proxy_send_timeout 300s;'), 2)
        self.assertEqual(updated.count('proxy_request_buffering off;'), 2)
        self.assertEqual(m.render(updated), (updated, 0))

    def test_api_without_uri_and_multiple_servers(self):
        original = 'server { location /api/ { proxy_pass http://127.0.0.1:8080; } }'
        updated, count = m.render(original + original)
        self.assertEqual(count, 4)
        self.assertEqual(updated.count('client_max_body_size 10m;'), 4)
        self.assertEqual(m.render(updated), (updated, 0))

    def test_api_rejects_different_uri_rewrite_include_and_priority(self):
        for body in ('proxy_pass http://127.0.0.1:8080/;',
                     'proxy_pass http://127.0.0.1:8080/api/;',
                     'proxy_pass http://127.0.0.1:8080/api/v1/; rewrite ^ /x break;',
                     'proxy_pass http://127.0.0.1:8080/api/v1/; include headers.conf;',
                     'proxy_pass http://127.0.0.1:8080$uri;'):
            with self.subTest(body=body), self.assertRaises(ValueError):
                m.render('server { location /api/v1/ { ' + body + ' } }')
        with self.assertRaises(ValueError):
            m.render('server { location ^~ /api/v1/ { proxy_pass http://127.0.0.1:8080; } }')
        with self.assertRaises(ValueError):
            m.render('server { location /api/v1/ { proxy_pass http://127.0.0.1:8080; }'
                     'location /api/v1/safety-committee/ { auth_request /restricted; } }')

    def test_two_scoped_routes_keep_every_proxy_directive(self):
        original = config()
        new, count = m.render(original)
        self.assertEqual(count, 2)
        self.assertEqual(new.count('client_max_body_size 10m;'), 3)
        self.assertEqual(new.count('client_max_body_size 2m;'), 1)
        self.assertEqual(new.count('proxy_set_header X-Example "value; # } ${host}";'), 3)
        self.assertIn(BLOCK, new)
        for route in m.ROUTES:
            self.assertIn('location ~ ' + route + ' {', new)

    def test_idempotent_and_multiple_servers(self):
        new, count = m.render(config() + config().replace('443 ssl', '80'))
        self.assertEqual(count, 4)
        self.assertEqual(m.render(new), (new, 0))

    def test_quoted_regex_braces_and_case_insensitive_operator(self):
        original = config(BLOCK.replace('[^/]+', '[a-z0-9-]{32,36}').replace('location ~ ', 'location ~* '))
        new, count = m.render(original)
        self.assertEqual(count, 2)
        self.assertEqual(new.count('location ~* '), 3)

    def test_reject_missing_limit_timeouts_proxy_or_nested_body(self):
        replacements = [('10m', '1m'), ('300s', '60s'), ('proxy_pass', 'invalid_proxy'),
                        ('proxy_request_buffering off;', 'include proxy.conf;'),
                        ('proxy_request_buffering off;', 'if ($request_method = PUT) { return 403; }')]
        for old, new in replacements:
            with self.subTest(old=old, new=new), self.assertRaises(ValueError):
                m.render(config(BLOCK.replace(old, new)))

    def test_reject_ambiguous_or_fragment_template(self):
        with self.assertRaises(ValueError):
            m.render(config(BLOCK + '\n' + BLOCK))
        with self.assertRaises(ValueError):
            m.render(BLOCK)
        with self.assertRaises(ValueError):
            m.render(config(BLOCK.replace('^/api/v1/document-uploads', '/api/v1/document-uploads')))

    def test_existing_conflicting_new_rule_rejected(self):
        new, _ = m.render(config())
        # Only alter a newly added location, leaving source template intact.
        position = new.index(m.ROUTES[0])
        bad = new[:position] + new[position:].replace('10m', '20m', 1)
        with self.assertRaises(ValueError):
            m.render(bad)

    def test_comments_never_trigger_match(self):
        original = '# location ~ ^/api/v1/document-uploads/.+$ {\nserver { listen 80; }'
        self.assertEqual(m.render(original), (original, 0))

    def test_bad_quote_brace_fails(self):
        for original in ('server {', 'server }', 'server { add_header X "unterminated; }'):
            with self.subTest(original=original), self.assertRaises(ValueError):
                m.render(original)


class FileTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.logs = self.root / 'logs'
        self.logs.mkdir()
        self.conf = self.root / 'actual.conf'
        self.conf.write_text(config())
        self.conf.chmod(0o644)
        self.logical = self.root / 'enabled.conf'
        self.logical.symlink_to(self.conf)
        self.patch_root = patch.object(m, 'ROOT', self.root)
        self.patch_root.start()
        self.patch_uid = patch.object(m.os, 'geteuid', return_value=0)
        self.patch_uid.start()
        self.real_checked = m._checked_file

        def checked(logical, **kwargs):
            # CI may run unprivileged: preserve real symlink/hash checks, simulate root owner.
            real_stat = Path.stat
            def owner(path, **kw):
                st = real_stat(path, **kw)
                values = list(st)
                values[4] = 0
                return os.stat_result(values)
            with patch.object(Path, 'stat', owner):
                return self.real_checked(logical, **kwargs)
        self.patch_checked = patch.object(m, '_checked_file', side_effect=checked)
        self.patch_checked.start()

    def tearDown(self):
        self.patch_checked.stop()
        self.patch_uid.stop()
        self.patch_root.stop()
        self.temp.cleanup()

    def prepare(self):
        output = ('# configuration file ' + str(self.logical) + ':\n' + config()).encode()
        with patch.object(m.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, output, b'')):
            plan = m.prepare(self.logs)
        self.assertEqual(json.loads(json.dumps(plan)), plan)
        self.assertEqual(self.conf.read_text(), config())
        self.assertEqual(plan['added_locations'], 2)
        return plan

    def test_prepare_resolves_symlink_without_mutating(self):
        plan = self.prepare()
        self.assertEqual(plan['files'][0]['path'], str(self.conf.resolve()))
        self.assertEqual((self.logs / 'nginx-plan.json').stat().st_mode & 0o777, 0o600)

    def test_config_drift_aborts_before_write(self):
        plan = self.prepare()
        self.conf.write_text(config() + '# concurrent\n')
        with self.assertRaises(ValueError), patch.object(m, '_atomic') as atomic:
            m.apply(plan)
        atomic.assert_not_called()

    def test_symlink_outside_config_tree_rejected(self):
        self.logical.unlink()
        self.logical.symlink_to('/etc/hosts')
        with self.assertRaises(ValueError):
            m._checked_file(self.logical)

    def test_ubuntu_readonly_module_symlink_allowed_and_bound_to_hash(self):
        with tempfile.TemporaryDirectory() as module_dir:
            target = Path(module_dir) / 'mod-http-image-filter.conf'
            target.write_text('load_module modules/ngx_http_image_filter_module.so;\n')
            target.chmod(0o644)
            module_link = self.root / '50-mod-http-image-filter.conf'
            module_link.symlink_to(target)
            output = ('# configuration file ' + str(module_link) + ':\n' + target.read_text()
                      + '# configuration file ' + str(self.logical) + ':\n' + config()).encode()
            with patch.object(m, 'SYSTEM_INCLUDE_ROOTS', (Path(module_dir),)), patch.object(
                    m.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, output, b'')):
                plan = m.prepare(self.logs)
            module = next(e for e in plan['files'] if e['path'] == str(target.resolve()))
            self.assertFalse(module['changed'])
            self.assertEqual(module['before_sha256'], hashlib.sha256(target.read_bytes()).hexdigest())
            self.assertEqual(plan['added_locations'], 2)

    def test_document_template_in_system_module_path_still_rejected(self):
        with tempfile.TemporaryDirectory() as module_dir:
            target = Path(module_dir) / 'external.conf'
            target.write_text(config())
            target.chmod(0o644)
            self.logical.unlink()
            self.logical.symlink_to(target)
            output = ('# configuration file ' + str(self.logical) + ':\n' + config()).encode()
            with patch.object(m, 'SYSTEM_INCLUDE_ROOTS', (Path(module_dir),)), patch.object(
                    m.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, output, b'')):
                with self.assertRaisesRegex(ValueError, '实际文件必须位于'):
                    m.prepare(self.logs)

    def test_successful_apply_preserves_symlink_and_does_not_reload(self):
        plan = self.prepare()
        with patch.object(m.os, 'fchown'), patch.object(m.subprocess, 'run',
                 return_value=subprocess.CompletedProcess([], 0, b'', b'ok')) as call:
            result = m.apply(plan)
        self.assertTrue(self.logical.is_symlink())
        self.assertEqual(self.conf.stat().st_mode & 0o777, 0o644)
        self.assertEqual(result['added_locations'], 2)
        self.assertFalse(result['reloaded'])
        self.assertEqual(self.conf.read_text().count('client_max_body_size 10m;'), 3)
        call.assert_called_once_with(['nginx', '-t'], capture_output=True, timeout=30)

    def test_apply_tests_without_reload_and_failed_test_restores(self):
        plan = self.prepare()
        # File ownership operation is the only privileged operation in atomic writer.
        with patch.object(m.os, 'fchown'), patch.object(m.subprocess, 'run',
                 return_value=subprocess.CompletedProcess([], 1, b'', b'bad config')) as call:
            with self.assertRaises(ValueError):
                m.apply(plan)
        call.assert_called_once_with(['nginx', '-t'], capture_output=True, timeout=30)
        self.assertEqual(self.conf.read_text(), config())


if __name__ == '__main__':
    unittest.main()
