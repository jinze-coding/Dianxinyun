#!/usr/bin/env bash
# User-run import of the separately sealed Linux amd64 runtime archive.
set -euo pipefail
cd /root/releases/20260915-0912/Dianxinyun-preview-offline-20260915
unset DOCKER_CONTEXT
export DOCKER_HOST=unix:///var/run/docker.sock
sha256sum -c SHA256SUMS
systemctl is-active --quiet site-platform.service
converter_before="$(systemctl show site-platform.service -p InvocationID --value)"
test -n "$converter_before"
docker load -i runtime-image.tar
converter_image=dianxinyun-material-preview:20260915-0912
test "$(docker image inspect --format '{{index .Config.Labels "dxy.source-manifest-sha256"}}' "$converter_image")" = 95e6a1fc24f569b3c9224d01971d3193b4dd40044401023e9b94393ea408c9af
if docker image inspect dianxinyun-material-preview:local >/dev/null 2>&1; then
  if [ "$(docker image inspect --format '{{.Id}}' dianxinyun-material-preview:local)" != "$(docker image inspect --format '{{.Id}}' "$converter_image")" ]; then
    echo '发现不同的已有转换镜像，已停止，请反馈。'
    exit 1
  fi
fi
docker tag "$converter_image" dianxinyun-material-preview:local
timeout 120 docker run --rm --pull=never --platform linux/amd64 --network none \
  --read-only --tmpfs /tmp:rw,size=256m --memory 1g --cpus 1 --pids-limit 128 \
  --cap-drop ALL --security-opt no-new-privileges --user 65534:65534 \
  "$converter_image" sh -ec \
  'test "$(uname -m)" = x86_64; libreoffice --version; ffmpeg -version >/dev/null; pdftoppm -v; test -d /usr/share/poppler/cMap'
systemctl is-active --quiet site-platform.service
test "$converter_before" = "$(systemctl show site-platform.service -p InvocationID --value)"
python3 - <<'PY'
import json, urllib.request
with urllib.request.urlopen('http://127.0.0.1:8080/api/v1/auth/captcha', timeout=10) as response:
    assert response.status == 200 and json.load(response).get('code') == 200
print('离线预览环境导入成功，原系统健康检查通过。')
PY
docker image inspect --format '{{.Id}}' "$converter_image" > ../converter-image-id.txt
