#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
[ "$(id -u)" = 0 ] || { echo '请使用 root 终端'; exit 1; }
sha256sum --quiet -c FILES.sha256
unit=dxy-formal-switch-20260915
current="$(systemctl show "$unit.service" --property=LoadState --value 2>/dev/null || true)"
[ "$current" = not-found ] || { echo '发布任务已经存在，请查看当前输出，不要重复执行'; exit 1; }
systemd-run --quiet --unit="$unit" --property=Type=exec \
  --property="WorkingDirectory=$PWD" --property=UMask=0077 \
  /usr/bin/python3 -B "$PWD/deploy.py" --apply
echo '新版更新任务已独立启动，关闭终端不会中断。下面持续显示进度；Ctrl+C 仅退出查看。'
journalctl -u "$unit.service" -f --no-pager -o cat
