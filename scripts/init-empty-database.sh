#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BASELINE_TEMPLATE="$ROOT_DIR/backend/src/main/resources/sql/empty-database-baseline.sql.template"
DATABASE_NAME="${DIANXINYUN_INIT_DATABASE:-dianxinyun}"
EXPECTED_CONFIRMATION="CREATE_EMPTY_DATABASE_ONLY:${DATABASE_NAME}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQL_HOST="${DIANXINYUN_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${DIANXINYUN_MYSQL_PORT:-3306}"
MYSQL_USER="${DIANXINYUN_MYSQL_USER:-root}"

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

command -v "$MYSQL_BIN" >/dev/null 2>&1 || fail "缺少 MySQL 客户端：$MYSQL_BIN"
[ -f "$BASELINE_TEMPLATE" ] || fail "空库基线模板不存在：$BASELINE_TEMPLATE"

if [[ ! "$DATABASE_NAME" =~ ^dianxinyun(_[A-Za-z0-9_]+)?$ ]]; then
  fail "数据库名只允许 dianxinyun 或 dianxinyun_ 前缀的字母、数字、下划线名称"
fi

if [ "${DIANXINYUN_INIT_CONFIRM:-}" != "$EXPECTED_CONFIRMATION" ]; then
  fail "拒绝初始化。请仅在确认目标为空库后设置 DIANXINYUN_INIT_CONFIRM=$EXPECTED_CONFIRMATION"
fi

MYSQL_COMMAND=(
  "$MYSQL_BIN"
  --connect-timeout=5
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USER"
  --batch
  --skip-column-names
)

TABLE_COUNT="$(
  "${MYSQL_COMMAND[@]}" --execute="
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = '${DATABASE_NAME}';
  "
)"

case "$TABLE_COUNT" in
  ''|*[!0-9]*)
    fail "无法确认目标数据库表数量"
    ;;
  0)
    ;;
  *)
    fail "拒绝初始化：数据库 ${DATABASE_NAME} 已有 ${TABLE_COUNT} 张表"
    ;;
esac

printf '[INFO] 已确认目标数据库 %s 当前为 0 张表，开始建立空库基线\n' "$DATABASE_NAME"
sed "s/__DIANXINYUN_DATABASE__/${DATABASE_NAME}/g" "$BASELINE_TEMPLATE" \
  | "${MYSQL_COMMAND[@]}"
printf '[INFO] 空库基线创建完成：%s\n' "$DATABASE_NAME"
printf '[INFO] 请按 backend/README.md 继续执行适用的增量迁移和显式管理员密码重置\n'
