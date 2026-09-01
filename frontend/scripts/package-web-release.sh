#!/usr/bin/env bash

set -euo pipefail
umask 077
export COPYFILE_DISABLE=1

project_root="$(cd "$(dirname "$0")/.." && pwd -P)"
variant="${1:-}"
output_root="${2:-/private/tmp}"

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

case "$variant" in
  transition)
    build_command='build:transition'
    expected_marker='dianxinyun-web-transition-meeting-disabled'
    meeting_enabled='false'
    ;;
  final)
    build_command='build:final'
    expected_marker='dianxinyun-web-final-meeting-enabled'
    meeting_enabled='true'
    ;;
  *) fail '用法：package-web-release.sh transition|final [输出目录]' ;;
esac

for command_name in node npm tar shasum find sort xargs awk rg chmod; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name"
done

source_manifest_sha256="${SOURCE_MANIFEST_SHA256:-}"
case "$source_manifest_sha256" in
  ''|*[!0-9a-fA-F]* ) fail '必须通过 SOURCE_MANIFEST_SHA256 关联不可变源码清单' ;;
esac
[ "${#source_manifest_sha256}" -eq 64 ] || fail 'SOURCE_MANIFEST_SHA256 必须是 64 位 SHA-256'

mkdir -p "$output_root"
output_root="$(cd "$output_root" && pwd -P)"
cd "$project_root"
npm run "$build_command"

build_root="$project_root/dist"
[ -f "$build_root/index.html" ] || fail 'Web 构建缺少 index.html'
javascript_file_count="$(find "$build_root" -type f -name '*.js' | wc -l | tr -d ' ')"
[ "$javascript_file_count" -gt 0 ] || fail 'Web 构建缺少 JavaScript 文件'
rg -l --fixed-strings "$expected_marker" "$build_root" >/dev/null || fail "Web 构建缺少发布标记：$expected_marker"
if [ "$variant" = transition ]; then
  ! rg -l --fixed-strings 'dianxinyun-web-final-meeting-enabled' "$build_root" >/dev/null \
    || fail '过渡 Web 错误包含会议启用标记'
else
  ! rg -l --fixed-strings 'dianxinyun-web-transition-meeting-disabled' "$build_root" >/dev/null \
    || fail '最终 Web 错误包含会议关闭标记'
fi

timestamp="$(date '+%Y%m%d-%H%M%S')"
package_id="Dianxinyun-web-${variant}-prod-${timestamp}"
archive_path="$output_root/${package_id}.tar.gz"
checksum_path="${archive_path}.sha256"
[ ! -e "$archive_path" ] || fail "输出文件已存在：$archive_path"
[ ! -e "$checksum_path" ] || fail "输出文件已存在：$checksum_path"

staging_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-web.XXXXXX")"
cleanup() {
  case "${staging_dir:-}" in
    "${TMPDIR:-/tmp}"/dianxinyun-web.*) rm -rf "$staging_dir" ;;
  esac
}
trap cleanup EXIT

mkdir -p "$staging_dir/dist"
tar --no-xattrs -C "$build_root" -cf - . | tar -C "$staging_dir/dist" -xf -
find "$staging_dir/dist" -type d -exec chmod 0755 {} +
find "$staging_dir/dist" -type f -exec chmod 0644 {} +
(cd "$staging_dir/dist" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256) \
  > "$staging_dir/ARTIFACT_FILES.sha256"

file_count="$(find "$staging_dir/dist" -type f | wc -l | tr -d ' ')"
total_bytes="$(find "$staging_dir/dist" -type f -exec stat -f '%z' {} + | awk '{sum += $1} END {print sum + 0}')"
lock_sha256="$(shasum -a 256 package-lock.json | awk '{print $1}')"
{
  printf 'PACKAGE_ID=%s\n' "$package_id"
  printf 'CREATED_AT=%s\n' "$(date -Iseconds)"
  printf 'VARIANT=%s\n' "$variant"
  printf 'MEETING_CREATION_ENABLED=%s\n' "$meeting_enabled"
  printf 'RELEASE_MARKER=%s\n' "$expected_marker"
  printf 'API_BASE=/api/v1\n'
  printf 'FILE_COUNT=%s\n' "$file_count"
  printf 'TOTAL_BYTES=%s\n' "$total_bytes"
  printf 'NODE_VERSION=%s\n' "$(node --version)"
  printf 'NPM_VERSION=%s\n' "$(npm --version)"
  printf 'PACKAGE_LOCK_SHA256=%s\n' "$lock_sha256"
  printf 'SOURCE_MANIFEST_SHA256=%s\n' "$source_manifest_sha256"
  printf 'BUILD_COMMAND=npm run %s\n' "$build_command"
} > "$staging_dir/RELEASE_MANIFEST.txt"
chmod 0644 "$staging_dir/ARTIFACT_FILES.sha256" "$staging_dir/RELEASE_MANIFEST.txt"

tar --no-xattrs --uid 0 --gid 0 --uname root --gname root -czf "$archive_path" \
  -C "$staging_dir" dist ARTIFACT_FILES.sha256 RELEASE_MANIFEST.txt
(cd "$output_root" && shasum -a 256 "$(basename "$archive_path")") > "$checksum_path"

printf 'Web %s 候选包：%s\n' "$variant" "$archive_path"
printf '外层校验：%s\n' "$checksum_path"
printf '文件：%s；字节：%s；会议创建：%s\n' "$file_count" "$total_bytes" "$meeting_enabled"
