#!/usr/bin/env bash
# User-run dependency preparation; no database migrations or application switch.
set -euo pipefail
umask 077
cd /root/releases/20260915-0912/Dianxinyun-update-20260915-0912
sha256sum --quiet -c SHA256SUMS
systemctl is-active --quiet site-platform.service
converter_before="$(systemctl show site-platform.service -p InvocationID --value)"
test -n "$converter_before"
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=l LC_ALL=C
unset DOCKER_CONTEXT
export DOCKER_HOST=unix:///var/run/docker.sock

if ! command -v docker >/dev/null 2>&1; then
  apt-get update
  apt-get -s --no-install-recommends install docker.io docker-buildx > ../converter-install-plan.txt
  if grep -Eq '^Remv |^Inst [^ ]+ \[' ../converter-install-plan.txt; then
    cat ../converter-install-plan.txt
    echo '安装计划涉及已有软件的升级或删除，已停止，请反馈。'
    exit 1
  fi
  apt-get install -y --no-remove --no-install-recommends docker.io docker-buildx
fi
systemctl enable --now docker
systemctl is-active --quiet site-platform.service
test "$converter_before" = "$(systemctl show site-platform.service -p InvocationID --value)"

# Refuse to replace a converter image belonging to a different release.
converter_source=95e6a1fc24f569b3c9224d01971d3193b4dd40044401023e9b94393ea408c9af
if docker image inspect dianxinyun-material-preview:local >/dev/null 2>&1; then
  test "$(docker image inspect --format '{{index .Config.Labels "dxy.source-manifest-sha256"}}' dianxinyun-material-preview:local)" = "$converter_source"
else
  timeout 1800 docker build --progress=plain \
    --label "dxy.source-manifest-sha256=$converter_source" \
    -t dianxinyun-material-preview:local runtime/meeting-material-preview
fi
timeout 120 docker run --rm --network none --read-only --tmpfs /tmp:rw,size=256m \
  --memory 1g --cpus 1 --pids-limit 128 --cap-drop ALL \
  --security-opt no-new-privileges --user 65534:65534 \
  dianxinyun-material-preview:local sh -ec \
  'libreoffice --version; ffmpeg -version; pdftoppm -v; test -d /usr/share/poppler/cMap'
systemctl is-active --quiet site-platform.service
test "$converter_before" = "$(systemctl show site-platform.service -p InvocationID --value)"
docker image inspect --format '{{.Id}}' dianxinyun-material-preview:local > ../converter-image-id.txt
echo '附件预览环境准备完成，原系统服务未重启。'
