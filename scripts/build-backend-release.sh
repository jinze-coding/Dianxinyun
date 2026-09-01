#!/usr/bin/env bash

set -euo pipefail
umask 077
export COPYFILE_DISABLE=1

root_dir="$(cd "$(dirname "$0")/.." && pwd -P)"

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

usage() {
  printf '用法：%s <源码归档> <输出目录>\n' "$0" >&2
  exit 2
}

[ "$#" -eq 2 ] || usage
source_archive="$1"
output_root="$2"

for command_name in tar shasum mvn jar unzip install awk grep python3 gzip mktemp; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name"
done
[ -f "$source_archive" ] || fail "源码归档不存在：$source_archive"
[ -f "$source_archive.sha256" ] || fail "缺少源码归档校验文件：$source_archive.sha256"
(
  cd "$(dirname "$source_archive")"
  shasum -a 256 -c "$(basename "$source_archive.sha256")"
) >/dev/null || fail '源码归档 SHA-256 校验失败'
gzip -t "$source_archive"
python3 "$root_dir/scripts/verify-release-archive.py" source "$source_archive"

mkdir -p "$output_root"
output_root="$(cd "$output_root" && pwd -P)"
case "$output_root/" in
  "$root_dir/"*) fail '后端产物输出目录不能位于项目工作树内' ;;
esac

staging_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-backend-release.XXXXXX")"
cleanup() {
  case "${staging_dir:-}" in
    "${TMPDIR:-/tmp}"/dianxinyun-backend-release.*) rm -rf "$staging_dir" ;;
  esac
}
trap cleanup EXIT

tar -xzf "$source_archive" -C "$staging_dir"
source_dir="$staging_dir/source"
source_manifest="$staging_dir/SOURCE_MANIFEST.txt"
source_files_manifest="$staging_dir/SOURCE_FILES.sha256"
[ -d "$source_dir/backend" ] || fail '源码归档缺少 backend 目录'
[ -f "$source_manifest" ] || fail '源码归档缺少 SOURCE_MANIFEST.txt'
[ -f "$source_files_manifest" ] || fail '源码归档缺少 SOURCE_FILES.sha256'

source_manifest_sha256="$(shasum -a 256 "$source_manifest" | awk '{print $1}')"
case "$source_manifest_sha256" in
  ''|*[!0-9a-f]*) fail '源码清单 SHA-256 格式非法' ;;
esac
[ "${#source_manifest_sha256}" -eq 64 ] || fail '源码清单 SHA-256 必须是 64 位'
(
  cd "$source_dir"
  shasum -a 256 -c ../SOURCE_FILES.sha256 >/dev/null
)

(
  cd "$source_dir/backend"
  mvn -Dsource.manifest.sha256="$source_manifest_sha256" clean test package
)

built_jar="$source_dir/backend/target/site-platform-1.0.0.jar"
[ -f "$built_jar" ] || fail 'Maven 构建后未生成 site-platform-1.0.0.jar'
jar tf "$built_jar" >/dev/null || fail 'JAR 结构校验失败'
unzip -t "$built_jar" >/dev/null || fail 'JAR ZIP CRC 校验失败'

build_info="$staging_dir/build-info.properties"
unzip -p "$built_jar" META-INF/build-info.properties > "$build_info" \
  || fail 'JAR 缺少 META-INF/build-info.properties'
provenance_count="$(grep -Ec '^build\.sourceManifestSha256=' "$build_info" || true)"
[ "$provenance_count" = 1 ] || fail 'JAR 源码清单标识不是唯一值'
jar_source_manifest="$(awk -F= '$1 == "build.sourceManifestSha256" {print $2}' "$build_info")"
[ "$jar_source_manifest" = "$source_manifest_sha256" ] \
  || fail 'JAR 内嵌源码清单标识与输入源码归档不一致'

(
  cd "$source_dir"
  shasum -a 256 -c ../SOURCE_FILES.sha256 >/dev/null
)

timestamp="$(date '+%Y%m%d-%H%M%S')"
output_jar="$output_root/site-platform-1.0.0-${timestamp}.jar"
[ ! -e "$output_jar" ] || fail "输出文件已存在：$output_jar"
[ ! -e "$output_jar.sha256" ] || fail "输出文件已存在：$output_jar.sha256"
install -m 0644 "$built_jar" "$output_jar"
(
  cd "$output_root"
  shasum -a 256 "$(basename "$output_jar")"
) > "$output_jar.sha256"
chmod 0644 "$output_jar.sha256"

printf '后端正式 JAR：%s\n' "$output_jar"
printf 'JAR 校验：%s\n' "$output_jar.sha256"
printf '内嵌源码清单 SHA-256：%s\n' "$source_manifest_sha256"
