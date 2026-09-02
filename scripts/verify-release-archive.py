#!/usr/bin/env python3
"""Reject release archives that are unsafe or non-portable on Linux."""

from __future__ import annotations

import argparse
import pathlib
import re
import sys
import tarfile


SELF_HEAL_REQUIRED_MEMBERS = (
    "ops/05-self-heal-control.sh",
    "ops/06-offline-worker-recovery.sh",
    "ops/assets/self-heal/60-self-heal.conf",
    "ops/assets/self-heal/site-platform-watchdog.service",
    "ops/assets/self-heal/site-platform-watchdog.timer",
    "ops/assets/self-heal/site-platform-watchdog.sh",
    "ops/lib/offline-worker-wrapper.sh",
)


def fail(message: str) -> None:
    raise ValueError(message)


def parse_manifest(raw: bytes) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in raw.decode("utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            result[key] = value
    return result


def is_appledouble(name: str) -> bool:
    return any(part.startswith("._") for part in pathlib.PurePosixPath(name).parts)


def verify_member(member: tarfile.TarInfo, kind: str) -> None:
    name = member.name
    path = pathlib.PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts:
        fail(f"unsafe archive path: {name}")
    if is_appledouble(name):
        fail(f"AppleDouble member is forbidden: {name}")
    if member.issym() or member.islnk() or member.isdev() or member.isfifo():
        fail(f"links and special files are forbidden: {name}")
    metadata_keys = " ".join(member.pax_headers).lower()
    if any(token in metadata_keys for token in ("xattr", "acl", "quarantine", "provenance")):
        fail(f"extended metadata is forbidden: {name}")
    if member.uid != 0 or member.gid != 0 or member.uname != "root" or member.gname != "root":
        fail(f"non-portable owner on {name}: {member.uid}:{member.gid} {member.uname}:{member.gname}")
    if member.isdir() and member.mode != 0o755:
        fail(f"directory mode must be 0755: {name} has {member.mode:04o}")
    if member.isfile() and kind in {"web", "mini", "outer"}:
        expected = 0o755 if kind == "outer" and "/ops/" in f"/{name}" and name.endswith(".sh") else 0o644
        if member.mode != expected:
            fail(f"file mode must be {expected:04o}: {name} has {member.mode:04o}")


def verify_inner(archive: tarfile.TarFile, kind: str, members: list[tarfile.TarInfo]) -> None:
    data_root = "dist" if kind == "web" else "mp-weixin"
    allowed_roots = {data_root, "ARTIFACT_FILES.sha256", "RELEASE_MANIFEST.txt"}
    for member in members:
        if pathlib.PurePosixPath(member.name).parts[0] not in allowed_roots:
            fail(f"unexpected top-level member: {member.name}")
    manifest_member = archive.getmember("RELEASE_MANIFEST.txt")
    manifest_file = archive.extractfile(manifest_member)
    if manifest_file is None:
        fail("cannot read RELEASE_MANIFEST.txt")
    manifest = parse_manifest(manifest_file.read())
    expected_count = int(manifest.get("FILE_COUNT", "-1"))
    actual_count = sum(1 for member in members if member.isfile() and member.name.startswith(f"{data_root}/"))
    if actual_count != expected_count:
        fail(f"{data_root} file count mismatch: expected {expected_count}, got {actual_count}")
    if kind == "mini":
        if manifest.get("UNI_STATISTICS_ENABLED") != "false":
            fail("mini archive does not prove uni statistics are disabled")
        forbidden = {"project.private.config.json"}
        if any(pathlib.PurePosixPath(member.name).name in forbidden for member in members):
            fail("mini archive contains project.private.config.json")
    if kind == "web":
        variant = manifest.get("VARIANT")
        enabled = manifest.get("MEETING_CREATION_ENABLED")
        if (variant, enabled) not in {("transition", "false"), ("final", "true")}:
            fail(f"invalid Web release phase: variant={variant}, meeting={enabled}")


def verify_outer(members: list[tarfile.TarInfo]) -> None:
    roots = {pathlib.PurePosixPath(member.name).parts[0] for member in members if member.name}
    if len(roots) != 1:
        fail(f"outer archive must contain exactly one release root, got {sorted(roots)}")
    root = next(iter(roots))
    required = {
        f"{root}/README-先看这里.md",
        f"{root}/RELEASE-MANIFEST.md",
        f"{root}/SHA256SUMS",
        f"{root}/artifacts",
        f"{root}/database",
        f"{root}/docs",
        f"{root}/ops",
        f"{root}/verification",
        f"{root}/source-reference",
    }
    required.update(f"{root}/{relative_path}" for relative_path in SELF_HEAL_REQUIRED_MEMBERS)
    names = {member.name.rstrip("/") for member in members}
    missing = sorted(required - names)
    if missing:
        fail(f"outer archive is missing required members: {missing}")
    artifact_names = [pathlib.PurePosixPath(member.name).name for member in members if member.isfile() and "/artifacts/" in member.name]
    patterns = {
        "jar": r"^site-platform-1\.0\.0\.jar$",
        "transition": r"^Dianxinyun-web-transition-prod-.*\.tar\.gz$",
        "final": r"^Dianxinyun-web-final-prod-.*\.tar\.gz$",
        "mini": r"^Dianxinyun-mini-0\.1\.8-prod-.*\.tar\.gz$",
    }
    for label, pattern in patterns.items():
        matches = [name for name in artifact_names if re.fullmatch(pattern, name)]
        if len(matches) != 1:
            fail(f"outer archive must contain exactly one {label} artifact, got {matches}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=("source", "web", "mini", "outer"))
    parser.add_argument("archive")
    args = parser.parse_args()
    with tarfile.open(args.archive, "r:gz") as archive:
        members = archive.getmembers()
        if not members:
            fail("archive is empty")
        for member in members:
            verify_member(member, args.kind)
        if args.kind in {"web", "mini"}:
            verify_inner(archive, args.kind, members)
        elif args.kind == "outer":
            verify_outer(members)
    print(f"portable archive verified: kind={args.kind}; members={len(members)}; archive={args.archive}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, tarfile.TarError, ValueError, KeyError) as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        raise SystemExit(1)
