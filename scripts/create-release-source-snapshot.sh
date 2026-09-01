#!/usr/bin/env bash

set -euo pipefail
umask 077
export COPYFILE_DISABLE=1

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
OUTPUT_ROOT="${1:-/private/tmp}"

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

for command_name in git tar shasum find sort xargs node; do
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

before_status_state="$(git status --porcelain=v1 -z --untracked-files=all | shasum -a 256 | awk '{print $1}')"
git ls-files --cached --others --exclude-standard -z > "$candidate_file_list"
while IFS= read -r -d '' source_path; do
  case "$source_path" in
    backups/*|需求方预览.zip)
      continue
      ;;
  esac
  if [ -e "$source_path" ] || [ -L "$source_path" ]; then
    printf '%s\0' "$source_path"
  fi
done < "$candidate_file_list" | LC_ALL=C sort -z > "$file_list"

while IFS= read -r -d '' source_path; do
  case "$source_path" in
    .env.example|*/.env.example|.env.*.example|*/.env.*.example)
      ;;
    .env|*/.env|.env.*|*/.env.*)
      fail "源码清单包含私密环境文件：$source_path"
      ;;
    *.pem|*.key|*.p12|*.pfx|*.jks|*.keystore|*.sql.gz|*.tar.gz)
      fail "源码清单包含疑似密钥或数据归档：$source_path"
      ;;
  esac
done < "$file_list"

before_source_state="$({
  while IFS= read -r -d '' source_path; do
    shasum -a 256 "$source_path"
  done < "$file_list"
} | shasum -a 256 | awk '{print $1}')"

(cd "$ROOT_DIR" && tar --no-xattrs --null -T "$file_list" -cf -) | (cd "$source_dir" && tar -xf -)

(cd "$source_dir" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256) \
  > "$staging_dir/SOURCE_FILES.sha256"

after_status_state="$(git status --porcelain=v1 -z --untracked-files=all | shasum -a 256 | awk '{print $1}')"
after_source_state="$({
  while IFS= read -r -d '' source_path; do
    shasum -a 256 "$source_path"
  done < "$file_list"
} | shasum -a 256 | awk '{print $1}')"
[ "$before_status_state" = "$after_status_state" ] || fail "打包期间工作树状态发生变化，请重新生成源码快照"
[ "$before_source_state" = "$after_source_state" ] || fail "打包期间源码内容发生变化，请重新生成源码快照"

file_count="$(tr -cd '\000' < "$file_list" | wc -c | tr -d ' ')"
change_count="$(git status --porcelain=v1 --untracked-files=all | wc -l | tr -d ' ')"
mini_program_root="$ROOT_DIR/wechat-miniprogram/site-platform-miniprogram"
mini_program_version="$(node -p "require('$mini_program_root/package.json').version")"
mini_program_build_id="$(node -e "const fs=require('fs');const s=fs.readFileSync('$mini_program_root/src/constants/release.ts','utf8');const m=s.match(/MINI_PROGRAM_BUILD_ID\\s*=\\s*'([^']+)'/);if(!m)process.exit(1);process.stdout.write(m[1])")"
mini_program_lock_sha256="$(shasum -a 256 "$mini_program_root/package-lock.json" | awk '{print $1}')"
frontend_lock_sha256="$(shasum -a 256 "$ROOT_DIR/frontend/package-lock.json" | awk '{print $1}')"
backend_pom_sha256="$(shasum -a 256 "$ROOT_DIR/backend/pom.xml" | awk '{print $1}')"
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
  printf 'WORKTREE_STATUS_SHA256=%s\n' "$after_status_state"
  printf 'SOURCE_TREE_SHA256=%s\n' "$after_source_state"
  printf 'WORKTREE_CHANGE_COUNT=%s\n' "$change_count"
  printf 'SOURCE_FILE_COUNT=%s\n' "$file_count"
  printf 'SOURCE_SCOPE=tracked-and-non-ignored-untracked-files-excluding-backups-and-legacy-preview-zip\n'
  printf 'MINI_PROGRAM_VERSION=%s\n' "$mini_program_version"
  printf 'MINI_PROGRAM_BUILD_ID=%s\n' "$mini_program_build_id"
  printf 'MINI_PROGRAM_LOCK_SHA256=%s\n' "$mini_program_lock_sha256"
  printf 'FRONTEND_LOCK_SHA256=%s\n' "$frontend_lock_sha256"
  printf 'BACKEND_POM_SHA256=%s\n' "$backend_pom_sha256"
  printf 'VERIFY_FILES=(cd source && shasum -a 256 -c ../SOURCE_FILES.sha256)\n'
} > "$staging_dir/SOURCE_MANIFEST.txt"

find "$source_dir" -type d -exec chmod 0755 {} +
find "$source_dir" -type f ! -perm -0100 -exec chmod 0644 {} +
find "$source_dir" -type f -perm -0100 -exec chmod 0755 {} +
chmod 0644 "$staging_dir/SOURCE_FILES.sha256" "$staging_dir/SOURCE_MANIFEST.txt"
tar --no-xattrs --uid 0 --gid 0 --uname root --gname root -czf "$archive_path" \
  -C "$staging_dir" source SOURCE_FILES.sha256 SOURCE_MANIFEST.txt
(cd "$OUTPUT_ROOT" && shasum -a 256 "$(basename "$archive_path")") > "$checksum_path"

printf '源码快照：%s\n' "$archive_path"
printf '外层校验：%s\n' "$checksum_path"
printf '文件数量：%s；工作树状态：%s\n' "$file_count" "$worktree_state"
