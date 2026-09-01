#!/usr/bin/env bash

set -euo pipefail
umask 077
export COPYFILE_DISABLE=1

project_root="$(cd "$(dirname "$0")/.." && pwd -P)"
output_root="${1:-/private/tmp}"
build_root="$project_root/dist/build/mp-weixin"

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

for command_name in node npm tar shasum find sort xargs awk; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name"
done

[ -d "$build_root" ] || fail "正式小程序构建目录不存在：$build_root"
mkdir -p "$output_root"
output_root="$(cd "$output_root" && pwd -P)"

cd "$project_root"
npm run verify:mp-weixin:real

version="$(node -p "require('./package.json').version")"
build_id="$(node -e "const fs=require('fs');const s=fs.readFileSync('src/constants/release.ts','utf8');const m=s.match(/MINI_PROGRAM_BUILD_ID\s*=\s*'([^']+)'/);if(!m)process.exit(1);process.stdout.write(m[1])")"
appid="$(node -p "require('./dist/build/mp-weixin/project.config.json').appid")"
timestamp="$(date '+%Y%m%d-%H%M%S')"
package_id="Dianxinyun-mini-${version}-prod-${timestamp}"
archive_path="$output_root/${package_id}.tar.gz"
checksum_path="${archive_path}.sha256"

[ ! -e "$archive_path" ] || fail "输出文件已存在：$archive_path"
[ ! -e "$checksum_path" ] || fail "输出文件已存在：$checksum_path"

staging_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-mini.XXXXXX")"
cleanup() {
  case "${staging_dir:-}" in
    "${TMPDIR:-/tmp}"/dianxinyun-mini.*) rm -rf "$staging_dir" ;;
  esac
}
trap cleanup EXIT

mkdir -p "$staging_dir/mp-weixin"
tar --no-xattrs --exclude './project.private.config.json' -C "$build_root" -cf - . | tar -C "$staging_dir/mp-weixin" -xf -
find "$staging_dir/mp-weixin" -type d -exec chmod 0755 {} +
find "$staging_dir/mp-weixin" -type f -exec chmod 0644 {} +
(cd "$staging_dir/mp-weixin" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256) \
  > "$staging_dir/ARTIFACT_FILES.sha256"

file_count="$(find "$staging_dir/mp-weixin" -type f | wc -l | tr -d ' ')"
total_bytes="$(find "$staging_dir/mp-weixin" -type f -exec stat -f '%z' {} + | awk '{sum += $1} END {print sum + 0}')"
lock_sha256="$(shasum -a 256 package-lock.json | awk '{print $1}')"
source_manifest_sha256="${SOURCE_MANIFEST_SHA256:-}"
case "$source_manifest_sha256" in
  ''|*[!0-9a-fA-F]* ) fail "必须通过 SOURCE_MANIFEST_SHA256 关联不可变源码清单" ;;
esac
[ "${#source_manifest_sha256}" -eq 64 ] || fail "SOURCE_MANIFEST_SHA256 必须是 64 位 SHA-256"

{
  printf 'PACKAGE_ID=%s\n' "$package_id"
  printf 'CREATED_AT=%s\n' "$(date -Iseconds)"
  printf 'VERSION=%s\n' "$version"
  printf 'BUILD_ID=%s\n' "$build_id"
  printf 'APPID=%s\n' "$appid"
  printf 'API_BASE=https://zhihuiyz.xyz/api/v1\n'
  printf 'URL_CHECK=true\n'
  printf 'UNI_STATISTICS_ENABLED=false\n'
  printf 'PAGE_COUNT=43\n'
  printf 'FILE_COUNT=%s\n' "$file_count"
  printf 'TOTAL_BYTES=%s\n' "$total_bytes"
  printf 'NODE_VERSION=%s\n' "$(node --version)"
  printf 'NPM_VERSION=%s\n' "$(npm --version)"
  printf 'PACKAGE_LOCK_SHA256=%s\n' "$lock_sha256"
  printf 'SOURCE_MANIFEST_SHA256=%s\n' "$source_manifest_sha256"
  printf 'BUILD_COMMAND=npm run build:mp-weixin:real\n'
  printf 'VERIFY_COMMAND=npm run verify:mp-weixin:real\n'
  printf 'UPLOAD_STATUS=not-uploaded\n'
} > "$staging_dir/RELEASE_MANIFEST.txt"

chmod 0644 "$staging_dir/ARTIFACT_FILES.sha256" "$staging_dir/RELEASE_MANIFEST.txt"
tar --no-xattrs --uid 0 --gid 0 --uname root --gname root -czf "$archive_path" \
  -C "$staging_dir" mp-weixin ARTIFACT_FILES.sha256 RELEASE_MANIFEST.txt
(cd "$output_root" && shasum -a 256 "$(basename "$archive_path")") > "$checksum_path"

printf '小程序正式候选包：%s\n' "$archive_path"
printf '外层校验：%s\n' "$checksum_path"
printf '构建编号：%s；页面：43；文件：%s；字节：%s\n' "$build_id" "$file_count" "$total_bytes"
