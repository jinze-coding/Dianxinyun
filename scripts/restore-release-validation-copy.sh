#!/usr/bin/env bash
# User-run server step. Restore the verified backup using an account confined to a fresh QA database.
set -euo pipefail
umask 077
backup_dir=/root/backups/dianxinyun/precheck-20260915-093640
qa_dir=/root/releases/20260915-0912/validation-093640
qa_db=dxycheck20260915093640
qa_user=dxycheck0915093640

fail() { printf '%s\n' "$*" >&2; exit 1; }
[ "$(id -u)" = 0 ] || fail '请在服务器 root 终端执行'
[ ! -e "$qa_dir" ] && [ ! -L "$qa_dir" ] || fail '验证目录已存在，请反馈结果，不要重复运行'
(cd "$backup_dir" && sha256sum -c SHA256SUMS)
gzip -t "$backup_dir/database.sql.gz"

existing="$(mysql -N -B -e "SELECT (SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$qa_db')+(SELECT COUNT(*) FROM mysql.user WHERE User='$qa_user');")"
[ "$existing" = 0 ] || fail '验证库或专用账号已存在，请反馈结果'
read -r qa_charset qa_collation <<< "$(mysql -N -B -e "SELECT default_character_set_name,default_collation_name FROM information_schema.schemata WHERE schema_name='dianxinyun';")"
[[ "$qa_charset" =~ ^[a-zA-Z0-9_]+$ && "$qa_collation" =~ ^[a-zA-Z0-9_]+$ ]] || fail '无法确认原库字符集'
qa_socket="$(mysql -N -B -e 'SELECT @@socket;')"
[[ "$qa_socket" =~ ^/[a-zA-Z0-9_./-]+$ ]] && [ -S "$qa_socket" ] || fail '当前 MySQL 不是可访问的本机 socket，请反馈结果'
qa_password="$(python3 -c 'import secrets; print("Aa1!"+secrets.token_hex(24))')"
mkdir "$qa_dir"
printf '[client]\nuser=%s\npassword=%s\nsocket=%s\nprotocol=SOCKET\n' \
  "$qa_user" "$qa_password" "$qa_socket" > "$qa_dir/client.cnf"

# Administrative access is used only to create the isolated database/account.
# The backup is never passed to this administrator connection.
mysql <<SQL
CREATE DATABASE $qa_db CHARACTER SET $qa_charset COLLATE $qa_collation;
CREATE USER '$qa_user'@'localhost' IDENTIFIED BY '$qa_password';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,DROP,INDEX,REFERENCES,LOCK TABLES
ON $qa_db.* TO '$qa_user'@'localhost';
SQL
unset qa_password
qa_mysql=(mysql "--defaults-file=$qa_dir/client.cnf" --binary-mode=1 --local-infile=0 --default-character-set=utf8mb4 "$qa_db")
[ "$("${qa_mysql[@]}" -N -B -e 'SELECT DATABASE();')" = "$qa_db" ] || fail '验证库连接检查失败'
if "${qa_mysql[@]}" -e 'SELECT 1 FROM dianxinyun.sys_user LIMIT 1;' > /dev/null 2> "$qa_dir/isolation-denied.log"; then
  fail '隔离账号意外可访问正式库，已停止'
fi
grep -Eq '^ERROR 1142 .*SELECT command denied' "$qa_dir/isolation-denied.log" || fail '未取得明确的跨库拒绝结果，已停止'

gzip -dc "$backup_dir/database.sql.gz" | "${qa_mysql[@]}" > "$qa_dir/restore.log" 2>&1
table_count="$("${qa_mysql[@]}" -N -B -e 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type="BASE TABLE";')"
[ "$table_count" = 98 ] || fail "恢复后的表数是 $table_count，请反馈结果，暂不迁移"
"${qa_mysql[@]}" -N -B -e 'SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type="BASE TABLE" ORDER BY table_name;' > "$qa_dir/tables-before.txt"
"${qa_mysql[@]}" -N -B -e 'SELECT migration_key FROM sys_data_migration ORDER BY migration_key;' > "$qa_dir/markers-before.txt"
printf 'QA_DATABASE=%s\nBACKUP_DIR=%s\nRESTORED_TABLES=%s\nRESTORE_COMPLETE=true\n' \
  "$qa_db" "$backup_dir" "$table_count" > "$qa_dir/restore-status.env"
printf '隔离恢复成功：%s，表数：%s\n验证资料目录：%s\n' "$qa_db" "$table_count" "$qa_dir"
printf '专用账号访问正式库：已明确拒绝；尚未执行增量迁移。\n'
