#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
OUTPUT_ROOT="${1:-/private/tmp}"

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

for command_name in git tar shasum find sort xargs; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name"
done

mkdir -p "$OUTPUT_ROOT"
OUTPUT_ROOT="$(cd "$OUTPUT_ROOT" && pwd -P)"
case "$OUTPUT_ROOT/" in
  "$ROOT_DIR/"*) fail "源码快照输出目录不能位于项目工作树内" ;;
esac

cd "$ROOT_DIR"

timestamp="$(date '+%Y%m%d-%H%M%S')"
base_commit="$(git rev-parse HEAD)"
short_commit="$(git rev-parse --short=12 HEAD)"
branch="$(git branch --show-current)"
release_id="Dianxinyun-source-${timestamp}-${short_commit}"
archive_path="$OUTPUT_ROOT/${release_id}.tar.gz"
checksum_path="${archive_path}.sha256"

[ ! -e "$archive_path" ] || fail "输出文件已存在：$archive_path"
[ ! -e "$checksum_path" ] || fail "校验文件已存在：$checksum_path"

staging_dir="$(mktemp -d "${TMPDIR:-/tmp}/dianxinyun-source.XXXXXX")"
cleanup() {
  [ -n "${staging_dir:-}" ] && [ -d "$staging_dir" ] && rm -rf "$staging_dir"
}
trap cleanup EXIT

source_dir="$staging_dir/source"
file_list="$staging_dir/source-files.list0"
candidate_file_list="$staging_dir/source-file-candidates.list0"
mkdir -p "$source_dir"

before_state="$(git status --porcelain=v1 -z --untracked-files=all | shasum -a 256 | awk '{print $1}')"
git ls-files --cached --others --exclude-standard -z > "$candidate_file_list"
while IFS= read -r -d '' source_path; do
  if [ -e "$source_path" ] || [ -L "$source_path" ]; then
    printf '%s\0' "$source_path"
  fi
done < "$candidate_file_list" | LC_ALL=C sort -z > "$file_list"

while IFS= read -r -d '' source_path; do
  case "$source_path" in
    .env|*/.env|.env.local|*/.env.local|.env.prod|*/.env.prod|.env.production|*/.env.production)
      fail "源码清单包含私密环境文件：$source_path"
      ;;
  esac
done < "$file_list"

(cd "$ROOT_DIR" && tar --null -T "$file_list" -cf -) | (cd "$source_dir" && tar -xf -)

(cd "$source_dir" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256) \
  > "$staging_dir/SOURCE_FILES.sha256"

after_state="$(git status --porcelain=v1 -z --untracked-files=all | shasum -a 256 | awk '{print $1}')"
[ "$before_state" = "$after_state" ] || fail "打包期间工作树发生变化，请重新生成源码快照"

file_count="$(tr -cd '\000' < "$file_list" | wc -c | tr -d ' ')"
change_count="$(git status --porcelain=v1 --untracked-files=all | wc -l | tr -d ' ')"
if git diff --quiet --ignore-submodules -- && git diff --cached --quiet --ignore-submodules -- \
    && [ -z "$(git ls-files --others --exclude-standard)" ]; then
  worktree_state="clean"
else
  worktree_state="dirty-captured-in-immutable-source-snapshot"
fi

{
  printf 'RELEASE_ID=%s\n' "$release_id"
  printf 'CREATED_AT=%s\n' "$(date -Iseconds)"
  printf 'BRANCH=%s\n' "${branch:-detached}"
  printf 'BASE_COMMIT=%s\n' "$base_commit"
  printf 'WORKTREE_STATE=%s\n' "$worktree_state"
  printf 'WORKTREE_STATUS_SHA256=%s\n' "$after_state"
  printf 'WORKTREE_CHANGE_COUNT=%s\n' "$change_count"
  printf 'SOURCE_FILE_COUNT=%s\n' "$file_count"
  printf 'SOURCE_SCOPE=tracked-and-non-ignored-untracked-files\n'
  printf 'VERIFY_FILES=shasum -a 256 -c SOURCE_FILES.sha256\n'
} > "$staging_dir/SOURCE_MANIFEST.txt"

tar -czf "$archive_path" -C "$staging_dir" source SOURCE_FILES.sha256 SOURCE_MANIFEST.txt
(cd "$OUTPUT_ROOT" && shasum -a 256 "$(basename "$archive_path")") > "$checksum_path"

printf '源码快照：%s\n' "$archive_path"
printf '外层校验：%s\n' "$checksum_path"
printf '文件数量：%s；工作树状态：%s\n' "$file_count" "$worktree_state"
