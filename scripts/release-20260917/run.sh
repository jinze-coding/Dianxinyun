#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
[ "$(id -u)" = 0 ] || { echo '请使用服务器 root 终端'; exit 1; }
sha256sum --quiet -c SHA256SUMS
unit="dxy-update-$(python3 -c 'import json; print(json.load(open("RELEASE.json"))["releaseId"])')"
[ "$(systemctl show "$unit.service" --property=LoadState --value 2>/dev/null || true)" = not-found ] \
  || { echo "任务已经存在，请查看：journalctl -u $unit.service -n 30 --no-pager"; exit 1; }
echo '开始应用必要增量并更新后端和 Web，完成后自动显示结果；关闭终端不会中断服务器任务。'
result=0
systemd-run --quiet --wait --unit="$unit" --property=Type=exec --property=UMask=0077 \
  --property="WorkingDirectory=$PWD" /usr/bin/python3 -Bu "$PWD/install.py" --apply || result=$?
journalctl -u "$unit.service" -n 30 --no-pager -o cat
exit "$result"
