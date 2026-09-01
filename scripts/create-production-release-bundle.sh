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
  printf '用法：%s <源码归档> <JAR> <过渡Web归档> <最终Web归档> <小程序归档> <输出目录>\n' "$0" >&2
  exit 2
}

[ "$#" -eq 6 ] || usage
source_archive="$1"
backend_jar="$2"
transition_web="$3"
final_web="$4"
mini_archive="$5"
output_root="$6"

for command_name in tar shasum find sort xargs awk sed install jar unzip grep python3 gzip; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name"
done
for path in "$source_archive" "$backend_jar" "$transition_web" "$final_web" "$mini_archive"; do
  [ -f "$path" ] || fail "输入文件不存在：$path"
done
for archive in "$source_archive" "$transition_web" "$final_web" "$mini_archive"; do
  [ -f "$archive.sha256" ] || fail "缺少同名校验文件：$archive.sha256"
  (
    cd "$(dirname "$archive")"
    shasum -a 256 -c "$(basename "$archive.sha256")"
  ) >/dev/null || fail "外层校验失败：$archive"
  gzip -t "$archive"
done
jar tf "$backend_jar" >/dev/null || fail 'JAR 结构校验失败'

python3 "$root_dir/scripts/verify-release-archive.py" source "$source_archive"
python3 "$root_dir/scripts/verify-release-archive.py" web "$transition_web"
python3 "$root_dir/scripts/verify-release-archive.py" web "$final_web"
python3 "$root_dir/scripts/verify-release-archive.py" mini "$mini_archive"

mkdir -p "$output_root"
output_root="$(cd "$output_root" && pwd -P)"
case "$output_root/" in
  "$root_dir/"*) fail '生产包输出目录不能位于项目工作树内' ;;
esac

timestamp="$(date '+%Y%m%d-%H%M%S')"
release_name="智慧营造生产更新-${timestamp}"
outer_archive="$output_root/${release_name}.tar.gz"
[ ! -e "$outer_archive" ] || fail "输出文件已存在：$outer_archive"
[ ! -e "$outer_archive.sha256" ] || fail "输出文件已存在：$outer_archive.sha256"

staging_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-production-release.XXXXXX")"
cleanup() {
  case "${staging_dir:-}" in
    "${TMPDIR:-/tmp}"/dianxinyun-production-release.*) rm -rf "$staging_dir" ;;
  esac
}
trap cleanup EXIT

# Freeze the already-verified source archive before reading any release input from it.
# Every SQL migration, operation script, document, and packaged verifier below must
# come from this one extracted snapshot, never from the mutable working tree.
source_input_sha256="$(shasum -a 256 "$source_archive" | awk '{print $1}')"
verified_source_archive="$staging_dir/verified-source.tar.gz"
install -m 0644 "$source_archive" "$verified_source_archive"
[ "$(shasum -a 256 "$verified_source_archive" | awk '{print $1}')" = "$source_input_sha256" ] \
  || fail '冻结源码归档时内容发生变化，请重新生成源码快照'
python3 "$root_dir/scripts/verify-release-archive.py" source "$verified_source_archive"

source_snapshot_dir="$staging_dir/source-snapshot"
install -d -m 0755 "$source_snapshot_dir"
tar -xzf "$verified_source_archive" -C "$source_snapshot_dir"
immutable_source_root="$source_snapshot_dir/source"
source_manifest_tmp="$source_snapshot_dir/SOURCE_MANIFEST.txt"
source_files_manifest="$source_snapshot_dir/SOURCE_FILES.sha256"

[ -d "$immutable_source_root" ] || fail '源码归档缺少 source/ 目录'
[ -f "$source_manifest_tmp" ] || fail '源码归档缺少 SOURCE_MANIFEST.txt'
[ -f "$source_files_manifest" ] || fail '源码归档缺少 SOURCE_FILES.sha256'
(
  cd "$immutable_source_root"
  shasum -a 256 -c "$source_files_manifest"
) >/dev/null || fail '源码归档的 SOURCE_FILES.sha256 校验失败'

immutable_migration_root="$immutable_source_root/backend/src/main/resources/sql/migrations"
immutable_ops_root="$immutable_source_root/scripts/production-release"
immutable_docs_root="$immutable_source_root/docs"
immutable_archive_verifier="$immutable_source_root/scripts/verify-release-archive.py"
[ -d "$immutable_migration_root" ] || fail '源码快照缺少数据库迁移目录'
[ -d "$immutable_ops_root" ] || fail '源码快照缺少 scripts/production-release 运维脚本集'
[ -d "$immutable_docs_root" ] || fail '源码快照缺少 docs 目录'
[ -f "$immutable_archive_verifier" ] || fail '源码快照缺少归档校验器'

# Revalidate every input with the verifier that will actually ship in this release.
python3 "$immutable_archive_verifier" source "$verified_source_archive"
python3 "$immutable_archive_verifier" web "$transition_web"
python3 "$immutable_archive_verifier" web "$final_web"
python3 "$immutable_archive_verifier" mini "$mini_archive"

release_dir="$staging_dir/$release_name"
install -d -m 0755 \
  "$release_dir/artifacts" \
  "$release_dir/database/migrations" \
  "$release_dir/database/baseline" \
  "$release_dir/docs" \
  "$release_dir/ops" \
  "$release_dir/source-reference" \
  "$release_dir/verification"

source_manifest_sha256="$(shasum -a 256 "$source_manifest_tmp" | awk '{print $1}')"

jar_build_info="$staging_dir/JAR_BUILD_INFO.properties"
unzip -p "$backend_jar" META-INF/build-info.properties > "$jar_build_info" \
  || fail '后端 JAR 缺少 META-INF/build-info.properties'
jar_provenance_count="$(grep -Ec '^build\.sourceManifestSha256=' "$jar_build_info" || true)"
[ "$jar_provenance_count" = 1 ] || fail '后端 JAR 的源码清单标识不是唯一值'
jar_source_manifest="$(awk -F= '$1 == "build.sourceManifestSha256" {print $2}' "$jar_build_info")"
[ "$jar_source_manifest" = "$source_manifest_sha256" ] \
  || fail '后端 JAR 未关联本次源码清单'

artifact_source_manifest() {
  tar -xOf "$1" RELEASE_MANIFEST.txt | awk -F= '$1 == "SOURCE_MANIFEST_SHA256" {print $2}'
}
for archive in "$transition_web" "$final_web" "$mini_archive"; do
  [ "$(artifact_source_manifest "$archive")" = "$source_manifest_sha256" ] \
    || fail "产物未关联本次源码清单：$archive"
done

install -m 0644 "$backend_jar" "$release_dir/artifacts/site-platform-1.0.0.jar"
install -m 0644 "$transition_web" "$release_dir/artifacts/$(basename "$transition_web")"
install -m 0644 "$transition_web.sha256" "$release_dir/artifacts/$(basename "$transition_web.sha256")"
install -m 0644 "$final_web" "$release_dir/artifacts/$(basename "$final_web")"
install -m 0644 "$final_web.sha256" "$release_dir/artifacts/$(basename "$final_web.sha256")"
install -m 0644 "$mini_archive" "$release_dir/artifacts/$(basename "$mini_archive")"
install -m 0644 "$mini_archive.sha256" "$release_dir/artifacts/$(basename "$mini_archive.sha256")"
(cd "$release_dir/artifacts" && shasum -a 256 site-platform-1.0.0.jar) > "$release_dir/artifacts/site-platform-1.0.0.jar.sha256"

migration_plan="$immutable_ops_root/baseline/migration-plan.tsv"
[ -f "$migration_plan" ] || fail '源码快照缺少 migration-plan.tsv'
migrations=()
while IFS='|' read -r order filename _; do
  case "$order" in ''|'#'*) continue ;; esac
  [ -n "$filename" ] || fail "源码快照迁移计划第 $order 项缺少文件名"
  migrations+=("$filename")
done < "$migration_plan"
[ "${#migrations[@]}" -eq 11 ] || fail '源码快照迁移计划不是精确 11 项'
for migration in "${migrations[@]}"; do
  install -m 0644 "$immutable_migration_root/$migration" \
    "$release_dir/database/migrations/$migration"
  [ "$(shasum -a 256 "$immutable_migration_root/$migration" | awk '{print $1}')" = \
    "$(shasum -a 256 "$release_dir/database/migrations/$migration" | awk '{print $1}')" ] \
    || fail "生产包迁移未保持源码快照内容：$migration"
done
printf '%s\n' "${migrations[@]}" > "$release_dir/database/MIGRATION_ORDER.txt"

tree_content_sha256() {
  local tree_root="$1"
  (
    cd "$tree_root"
    find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256
  ) | shasum -a 256 | awk '{print $1}'
}

ops_source="$immutable_ops_root"
cp -R "$ops_source/." "$release_dir/ops/"
[ "$(tree_content_sha256 "$ops_source")" = "$(tree_content_sha256 "$release_dir/ops")" ] \
  || fail '生产包运维脚本未保持源码快照内容'
if [ -d "$ops_source/baseline" ]; then
  cp -R "$ops_source/baseline/." "$release_dir/database/baseline/"
  [ "$(tree_content_sha256 "$ops_source/baseline")" = \
    "$(tree_content_sha256 "$release_dir/database/baseline")" ] \
    || fail '生产包数据库基线未保持源码快照内容'
fi
install -m 0644 "$immutable_archive_verifier" "$release_dir/ops/verify-release-archive.py"
[ "$(shasum -a 256 "$immutable_archive_verifier" | awk '{print $1}')" = \
  "$(shasum -a 256 "$release_dir/ops/verify-release-archive.py" | awk '{print $1}')" ] \
  || fail '生产包归档校验器未保持源码快照内容'

for document in \
  '阿里云正式更新步骤.md' \
  '生产升级与回滚手册.md' \
  '生产数据兼容测试报告-20260901.md'; do
  install -m 0644 "$immutable_docs_root/$document" "$release_dir/docs/$document"
  [ "$(shasum -a 256 "$immutable_docs_root/$document" | awk '{print $1}')" = \
    "$(shasum -a 256 "$release_dir/docs/$document" | awk '{print $1}')" ] \
    || fail "生产包文档未保持源码快照内容：$document"
done
install -m 0644 "$immutable_docs_root/生产数据兼容测试报告-20260901.md" \
  "$release_dir/verification/COMPATIBILITY-TEST-SUMMARY.md"

source_reference_archive="$release_dir/source-reference/$(basename "$source_archive")"
install -m 0644 "$verified_source_archive" "$source_reference_archive"
(
  cd "$release_dir/source-reference"
  shasum -a 256 "$(basename "$source_reference_archive")"
) > "$source_reference_archive.sha256"
install -m 0644 "$source_manifest_tmp" "$release_dir/source-reference/SOURCE_MANIFEST.txt"
install -m 0644 "$source_files_manifest" "$release_dir/source-reference/SOURCE_FILES.sha256"

jar_sha256="$(shasum -a 256 "$backend_jar" | awk '{print $1}')"
transition_sha256="$(shasum -a 256 "$transition_web" | awk '{print $1}')"
final_sha256="$(shasum -a 256 "$final_web" | awk '{print $1}')"
mini_sha256="$(shasum -a 256 "$mini_archive" | awk '{print $1}')"
source_sha256="$(shasum -a 256 "$verified_source_archive" | awk '{print $1}')"

{
  printf '# 先看这里\n\n'
  printf '这是已通过正式数据副本兼容测试的生产更新候选，尚未部署。\n\n'
  printf '解包后先执行：\n\n```bash\n'
  printf 'cd %s\n' "$release_name"
  printf 'sha256sum -c SHA256SUMS\n'
  printf 'python3 ops/verify-release-archive.py outer ../%s.tar.gz\n' "$release_name"
  printf 'bash ops/00-preflight-readonly.sh\n'
  printf '```\n\n'
  printf '正式顺序固定为：实时预检与停服备份 → 11 项数据库迁移 → 历史密文 VERIFY/APPLY/VERIFY → '
  printf '过渡 Web → 新后端 → 兼容停点 → 小程序 0.1.8 审核并确认生效 → 最终 Web。\n\n'
  printf '过渡 Web 已适配新后端但关闭会议创建；小程序生效前禁止切最终 Web。真实密钥、数据库和 uploads '
  printf '均不在本包内。\n'
} > "$release_dir/README-先看这里.md"

{
  printf '# Release manifest\n\n'
  printf -- '- Release: `%s`\n' "$release_name"
  printf -- '- Created: `%s`\n' "$(date -Iseconds)"
  printf -- '- Source archive SHA-256: `%s`\n' "$source_sha256"
  printf -- '- Source manifest SHA-256: `%s`\n' "$source_manifest_sha256"
  printf -- '- Backend JAR SHA-256: `%s`\n' "$jar_sha256"
  printf -- '- Backend embedded source manifest SHA-256: `%s`\n' "$jar_source_manifest"
  printf -- '- Transition Web SHA-256: `%s`\n' "$transition_sha256"
  printf -- '- Final Web SHA-256: `%s`\n' "$final_sha256"
  printf -- '- Mini Program SHA-256: `%s`\n' "$mini_sha256"
  printf -- '- Database baseline: `68 tables / 13 exact markers`\n'
  printf -- '- Database target before data repair: `98 tables / 24 exact markers`\n'
  printf -- '- Database target after data repair: `98 tables / 25 exact markers`\n'
  printf -- '- Mini Program: `0.1.8 / versionCode 108 / 43 pages / uniStatistics=false`\n'
  printf -- '- Release order: `transition Web -> backend -> compatibility gate -> mini effective -> final Web`\n'
} > "$release_dir/RELEASE-MANIFEST.md"

find "$release_dir" -type d -exec chmod 0755 {} +
find "$release_dir" -type f -exec chmod 0644 {} +
find "$release_dir/ops" -type f -name '*.sh' -exec chmod 0755 {} +
(cd "$release_dir" && find . -type f ! -name SHA256SUMS -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256) \
  > "$release_dir/SHA256SUMS"
chmod 0644 "$release_dir/SHA256SUMS"

tar --no-xattrs --uid 0 --gid 0 --uname root --gname root -czf "$outer_archive" \
  -C "$staging_dir" "$release_name"
(cd "$output_root" && shasum -a 256 "$(basename "$outer_archive")") > "$outer_archive.sha256"
python3 "$immutable_archive_verifier" outer "$outer_archive"

extract_dir="$staging_dir/extracted"
install -d -m 0755 "$extract_dir"
tar -xzf "$outer_archive" -C "$extract_dir"
(cd "$extract_dir/$release_name" && shasum -a 256 -c SHA256SUMS >/dev/null)

printf '生产更新包：%s\n' "$outer_archive"
printf '外层校验：%s\n' "$outer_archive.sha256"
printf '源码清单 SHA-256：%s\n' "$source_manifest_sha256"
printf '后端/过渡 Web/最终 Web/小程序已关联同一不可变源码快照。\n'
