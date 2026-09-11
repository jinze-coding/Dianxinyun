#!/usr/bin/env bash
set -euo pipefail
work="${1:?}"; kind="${2:?}"; extension="${3:?}"
[[ "$work" = /* && "$extension" =~ ^[a-z0-9]+$ ]] || exit 2
[[ -f "$work/input.$extension" ]] || exit 2
if [[ "$(uname -s)" = Darwin ]]; then
  exec python3 "$(dirname "$0")/meeting-material-preview/macos-convert.py" "$work" "$kind" "$extension"
fi
container_name="meeting-preview-$(basename "$work")"
cleanup_container() { docker rm -f "$container_name" >/dev/null 2>&1 || true; }
trap cleanup_container EXIT TERM INT
case "$kind" in
 OFFICE) command=(sh -c 'mkdir -p /work/out; libreoffice -env:UserInstallation=file:///tmp/lo-profile --headless --convert-to pdf --outdir /work/out "/work/input.$1" >/tmp/convert.log 2>&1; mv /work/out/input.pdf /work/output.pdf' sh "$extension");;
 VIDEO) command=(ffmpeg -nostdin -v error -i "/work/input.$extension" -map 0:v:0 -map '0:a:0?' -c:v libx264 -preset fast -crf 23 -c:a aac -movflags +faststart -y /work/output.mp4);;
 AUDIO) command=(ffmpeg -nostdin -v error -i "/work/input.$extension" -vn -c:a libmp3lame -q:a 4 -y /work/output.mp3);;
 HEIF) command=(ffmpeg -nostdin -v error -i "/work/input.$extension" -frames:v 1 -y /work/output.jpg);;
 *) exit 2;;
esac
docker run --rm --name "$container_name" --network none --memory 1g --cpus 1 --pids-limit 128 \
 --read-only --tmpfs /tmp:rw,size=256m --cap-drop ALL --security-opt no-new-privileges \
 --user "$(id -u):$(id -g)" --mount "type=bind,src=$work,dst=/work" \
 dianxinyun-material-preview:local "${command[@]}"
