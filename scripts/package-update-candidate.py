#!/usr/bin/env python3
"""Seal current artifacts for user upload; target-specific deployment remains separate."""
import argparse
import hashlib
import importlib.util
import json
import pathlib
import tarfile
import tempfile
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, message):
    if not ok:
        raise ValueError(message)


def check_archive(file, kind, verifier):
    with tarfile.open(file, 'r:gz') as archive:
        members = archive.getmembers()
        require(len({m.name for m in members}) == len(members), 'duplicate archive member')
        for member in members:
            verifier.verify_member(member, kind)
        if kind in ('web', 'mini'):
            verifier.verify_inner(archive, kind, members)
        manifest_name = 'SOURCE_MANIFEST.txt' if kind == 'source' else 'RELEASE_MANIFEST.txt'
        manifest = archive.extractfile(manifest_name).read()
        sums = archive.extractfile('SOURCE_FILES.sha256' if kind == 'source' else 'ARTIFACT_FILES.sha256').read().decode()
        prefix = 'source' if kind == 'source' else ('dist' if kind == 'web' else 'mp-weixin')
        checked = set()
        for line in sums.splitlines():
            digest, name = line.split('  ', 1)
            full_name = f'{prefix}/{name.removeprefix("./")}'
            require(full_name not in checked, 'duplicate checksum entry')
            checked.add(full_name)
            require(sha(archive.extractfile(full_name).read()) == digest, f'hash mismatch: {full_name}')
        actual = {m.name for m in members if m.isfile() and m.name.startswith(prefix + '/')}
        require(actual == checked, 'checksum list does not cover the exact payload')
        return manifest, verifier.parse_manifest(manifest)


def main():
    parser = argparse.ArgumentParser()
    for name in ('source', 'jar', 'transition', 'final', 'mini', 'output'):
        parser.add_argument('--' + name, required=True, type=pathlib.Path)
    args = parser.parse_args()
    script_root = pathlib.Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location('archive_verifier', script_root / 'verify-release-archive.py')
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    source_raw, source_manifest = check_archive(args.source, 'source', verifier)
    source_hash = sha(source_raw)
    for file, kind, variant in ((args.transition, 'web', 'transition'), (args.final, 'web', 'final'), (args.mini, 'mini', None)):
        _, manifest = check_archive(file, kind, verifier)
        require(manifest.get('SOURCE_MANIFEST_SHA256') == source_hash, f'wrong source provenance: {file}')
        if variant:
            require(manifest.get('VARIANT') == variant, 'wrong web variant')
        else:
            require(manifest.get('VERSION') == source_manifest['MINI_PROGRAM_VERSION'], 'wrong mini version')
            require(manifest.get('BUILD_ID') == source_manifest['MINI_PROGRAM_BUILD_ID'], 'wrong mini build')
    with zipfile.ZipFile(args.jar) as jar:
        require(jar.testzip() is None, 'corrupt jar')
        properties = jar.read('META-INF/build-info.properties').decode().splitlines()
        matches = [s.split('=', 1)[1] for s in properties if s.startswith('build.sourceManifestSha256=')]
        require(matches == [source_hash], 'backend source provenance mismatch')
    require(not args.output.exists(), 'output already exists')
    require(args.output.name.endswith('.tar.gz'), 'output must end with .tar.gz')
    release_id = args.output.name.removesuffix('.tar.gz')
    require(release_id.replace('-', '').isalnum(), 'release name must contain only letters, numbers and hyphens')
    with tempfile.TemporaryDirectory(prefix='dxy-candidate-') as temp:
        root = pathlib.Path(temp) / release_id
        root.mkdir()
        def write(name, content):
            p = root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(content if isinstance(content, bytes) else content.encode())
        artifacts = {'backend.jar': args.jar, args.transition.name: args.transition, args.final.name: args.final, args.mini.name: args.mini}
        for name, file in artifacts.items():
            write('artifacts/' + name, file.read_bytes())
        write('source-reference/' + args.source.name, args.source.read_bytes())
        write('source-reference/SOURCE_MANIFEST.txt', source_raw)
        migrations = []
        with tarfile.open(args.source, 'r:gz') as archive:
            # Read only from the same frozen snapshot, never from the later worktree.
            selected = {
                'source/scripts/release-preflight-readonly.sh': 'ops/preflight-readonly.sh',
                'source/scripts/meeting-material-convert.sh': 'runtime/meeting-material-convert.sh',
                'source/scripts/meeting-material-preview/Dockerfile': 'runtime/meeting-material-preview/Dockerfile',
                'source/backend/.env.example': 'runtime/backend.env.example',
                'source/backend/README.md': 'docs/backend-README.md',
                'source/docs/更新包准备与分步操作.md': 'README-先看这里.md',
            }
            for source, dest in selected.items():
                write(dest, archive.extractfile(source).read())
            for member in archive.getmembers():
                if member.isfile() and member.name.startswith('source/backend/src/main/resources/sql/migrations/') and member.name.endswith('.sql'):
                    data = archive.extractfile(member).read()
                    name = pathlib.PurePosixPath(member.name).name
                    write('database/migrations/' + name, data)
                    migrations.append({'file': name, 'sha256': sha(data)})
        write('database/MIGRATION_INVENTORY.json', json.dumps({'status': 'inventory-only-not-an-execution-order', 'note': 'Compare target schema and markers first; never run all SQL by filename order.', 'files': sorted(migrations, key=lambda x: x['file'])}, indent=2))
        write('RELEASE-MANIFEST.json', json.dumps({
            'releaseId': release_id, 'status': 'BUILT_AWAITING_TARGET_PREFLIGHT',
            'sourceManifestSha256': source_hash, 'sourceArchiveSha256': sha(args.source.read_bytes()),
            'baseCommit': source_manifest['BASE_COMMIT'], 'worktree': source_manifest['WORKTREE_STATE'],
            'miniVersion': source_manifest['MINI_PROGRAM_VERSION'], 'miniBuildId': source_manifest['MINI_PROGRAM_BUILD_ID'],
            'targetDatabaseCompatibility': 'not-yet-verified', 'productionUploaded': False,
            'miniUploaded': False, 'runtimeConverterImage': 'build-required-on-target-linux',
            'artifacts': {name: sha(file.read_bytes()) for name, file in artifacts.items()},
        }, ensure_ascii=False, indent=2))
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
                    with p.open('rb') as file:
                        archive.addfile(info, file)
                else:
                    archive.addfile(info)
        # Verify the actual sealed bytes, exact membership, metadata and every checksum.
        with tarfile.open(args.output, 'r:gz') as archive:
            for member in archive.getmembers():
                verifier.verify_member(member, 'source')
            for line in sums.splitlines():
                digest, name = line.split('  ', 1)
                require(sha(archive.extractfile(release_id + '/' + name).read()) == digest, 'sealed checksum mismatch')
        args.output.with_name(args.output.name + '.sha256').write_text(f'{sha(args.output.read_bytes())}  {args.output.name}\n')
        print(json.dumps({'archive': str(args.output), 'bytes': args.output.stat().st_size, 'sourceManifestSha256': source_hash, 'migrationFiles': len(migrations)}, indent=2))


if __name__ == '__main__':
    main()
