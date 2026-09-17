#!/usr/bin/env bash
# Manual recovery only, never called by run.sh or deploy.py.
set -euo pipefail
cd "$(dirname "$0")"
[ "${1:-}" = --confirm-full-restore-after-failure ] || {
  echo '仅在核对发布失败现场和小程序兼容后明确执行同点恢复；不属于正常新版发布步骤。'
  exit 2
}
sha256sum --quiet -c FILES.sha256
unset MYSQL_DEFAULTS_FILE SERVICE_NAME SERVICE_USER SERVICE_GROUP APP_ROOT ENV_FILE MYSQL_DATABASE
unset JAR_LINK WEB_LINK UPLOAD_LINK NGINX_ROOT JAVA_BIN HEALTH_URL LOCAL_HEALTH_URL PUBLIC_BASE_URL
export MYSQL_BIN="$PWD/rollback-ops/mysql-bound"
export MYSQLDUMP_BIN="$PWD/rollback-ops/mysqldump-bound"
exec bash rollback-ops/90-rollback.sh \
  --backup-dir /root/backups/dianxinyun/release-20260915-0912 \
  --failure-snapshot-dir "/root/backups/dianxinyun/failed-release-20260915-$(date +%H%M%S)" \
  --mini-state released \
  --mini-confirm MINI_NEW_VERSION_ROLLBACK_OR_OLD_BACKEND_COMPATIBILITY_APPROVED \
  --confirm RESTORE_DIANXINYUN_FROM_SAMEPOINT_BACKUP
