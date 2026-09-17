#!/usr/bin/env python3
"""Package the reviewed 2026-09-16 backend/Web-only update from frozen source."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import tarfile
import tempfile
import zipfile


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser()
    for name in ('source', 'baseline-source', 'jar', 'web', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--release-id', required=True)
    args = parser.parse_args()
    scripts = Path(__file__).resolve().parent
    common = load('package_common', scripts / 'package-update-candidate.py')
    verifier = load('verify_archive', scripts / 'verify-release-archive.py')
    require, sha = common.require, common.sha
    source_raw, source_manifest = common.check_archive(args.source, 'source', verifier)
    baseline_raw, _ = common.check_archive(args.baseline_source, 'source', verifier)
    source_hash = sha(source_raw)
    _, web_manifest = common.check_archive(args.web, 'web', verifier)
    require(web_manifest.get('SOURCE_MANIFEST_SHA256') == source_hash and web_manifest.get('VARIANT') == 'final', 'Web source or variant mismatch')
    require(sha(baseline_raw) == '95e6a1fc24f569b3c9224d01971d3193b4dd40044401023e9b94393ea408c9af', 'wrong production baseline')
    with zipfile.ZipFile(args.jar) as jar:
        require(jar.testzip() is None, 'corrupt backend JAR')
        props = jar.read('META-INF/build-info.properties').decode().splitlines()
        require([line for line in props if line.startswith('build.sourceManifestSha256=')] == ['build.sourceManifestSha256=' + source_hash], 'backend source mismatch')
        require('BOOT-INF/classes/com/example/siteplatform/project/service/ProjectAccessBatchService.class' in jar.namelist(), 'missing new batch service')
    require(args.release_id.startswith('20260916-') and args.release_id.replace('-', '').isdigit(), 'invalid release ID')
    require(not args.output.exists(), 'output already exists')
    bundle_name = 'Dianxinyun-update-' + args.release_id
    require(args.output.name == bundle_name + '.tar.gz', 'output name mismatch')
    with tarfile.open(args.source) as source, tarfile.open(args.baseline_source) as baseline, tarfile.open(args.web) as web, tempfile.TemporaryDirectory(prefix='dxy-code-package-') as temp:
        def files(archive):
            return {m.name.removeprefix('source/'): archive.extractfile(m).read() for m in archive.getmembers() if m.isfile() and m.name.startswith('source/')}
        new_files, old_files = files(source), files(baseline)
        changes = sorted(path for path in new_files.keys() | old_files.keys() if new_files.get(path) != old_files.get(path))
        protected = ('backend/pom.xml', 'backend/.env.example', 'backend/src/main/resources/', 'frontend/package.json', 'frontend/package-lock.json', 'frontend/vite.config.js', 'scripts/meeting-material-convert.sh', 'scripts/meeting-material-preview/')
        require(not [p for p in changes if p.startswith(protected)], 'database, configuration, dependencies or converter changed; cannot use this code-only installer')
        root = Path(temp) / bundle_name
        root.mkdir()
        def write(name, value):
            dest = root / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(value if isinstance(value, bytes) else value.encode())
        write('artifacts/backend.jar', args.jar.read_bytes())
        for member in web.getmembers():
            if member.isfile() and member.name.startswith('dist/'):
                write('artifacts/web/' + member.name.removeprefix('dist/'), web.extractfile(member).read())
        for name in ('install.py', 'run.sh'):
            write(name, new_files['scripts/release-20260916/' + name])
        write('README-更新说明.md', new_files['docs/正式更新准备-20260916.md'])
        write('source-reference/SOURCE_MANIFEST.txt', source_raw)
        write('source-reference/' + args.source.name, args.source.read_bytes())
        write('source-reference/WEB_RELEASE_MANIFEST.txt', web.extractfile('RELEASE_MANIFEST.txt').read())
        write('RELEASE.json', json.dumps({
            'releaseId': args.release_id, 'status': 'PACKAGED_NOT_DEPLOYED',
            'sourceManifestSha256': source_hash, 'sourceArchiveSha256': sha(args.source.read_bytes()),
            'baselineSourceManifestSha256': sha(baseline_raw), 'baseCommit': source_manifest['BASE_COMMIT'],
            'worktree': source_manifest['WORKTREE_STATE'], 'databaseChanges': False,
            'configurationChanges': False, 'dependencyChanges': False, 'miniProgramIncluded': False,
            'runtimeSourceChanges': [p for p in changes if p.startswith(('backend/src/main/', 'frontend/src/'))],
            'backendSha256': sha(args.jar.read_bytes()), 'webIndexSha256': sha((root / 'artifacts/web/index.html').read_bytes()),
            'validation': {'backendRelatedUnitTests': 109, 'backendSyntheticIntegrationTests': 9,
                           'webUnitTests': 196, 'installerOfflineTests': 8,
                           'reusedUnchangedEvidence': True, 'targetDeployment': 'awaiting-user-execution'},
        }, ensure_ascii=False, indent=2) + '\n')
        sums = ''.join(f'{sha(p.read_bytes())}  {p.relative_to(root).as_posix()}\n' for p in sorted(root.rglob('*')) if p.is_file())
        write('SHA256SUMS', sums)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with tarfile.open(args.output, 'w:gz', format=tarfile.PAX_FORMAT) as archive:
            for p in [root, *sorted(root.rglob('*'))]:
                info = archive.gettarinfo(str(p), str(p.relative_to(root.parent)))
                info.uid = info.gid = 0
                info.uname = info.gname = 'root'
                info.pax_headers = {}
                info.mode = 0o755 if p.is_dir() or p.suffix == '.sh' else 0o644
                if p.is_file():
                    with p.open('rb') as stream:
                        archive.addfile(info, stream)
                else:
                    archive.addfile(info)
        with tarfile.open(args.output) as archive:
            for member in archive.getmembers():
                verifier.verify_member(member, 'source')
            for line in sums.splitlines():
                expected, relative = line.split('  ', 1)
                require(sha(archive.extractfile(bundle_name + '/' + relative).read()) == expected, 'sealed artifact mismatch')
        outer_sha = sha(args.output.read_bytes())
        args.output.with_name(args.output.name + '.sha256').write_text(f'{outer_sha}  {args.output.name}\n')
        print(json.dumps({'archive': str(args.output), 'bytes': args.output.stat().st_size, 'sha256': outer_sha,
                          'sourceManifestSha256': source_hash, 'databaseChanges': False}, indent=2))


if __name__ == '__main__':
    main()
