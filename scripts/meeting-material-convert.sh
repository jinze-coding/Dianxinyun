#!/usr/bin/env bash
set -euo pipefail
work="${1:?}"; kind="${2:?}"; extension="${3:?}"
rotation="${4:-0}"
case "$rotation" in
 0) rotation_filter="null";;
 90) rotation_filter="transpose=clock";;
 180) rotation_filter="hflip,vflip";;
 270) rotation_filter="transpose=cclock";;
 *) exit 2;;
esac
[[ "$work" = /* && "$extension" =~ ^[a-z0-9]+$ ]] || exit 2
[[ -f "$work/input.$extension" ]] || exit 2
if [[ "$(uname -s)" = Darwin ]]; then
  exec python3 "$(dirname "$0")/meeting-material-preview/macos-convert.py" "$work" "$kind" "$extension" "$rotation"
fi
container_name="meeting-preview-$(basename "$work")"
cleanup_container() { docker rm -f "$container_name" >/dev/null 2>&1 || true; }
trap cleanup_container EXIT TERM INT
case "$kind" in
 OFFICE) command=(sh -c 'mkdir -p /work/out; libreoffice -env:UserInstallation=file:///tmp/lo-profile --headless --convert-to pdf --outdir /work/out "/work/input.$1" >/tmp/convert.log 2>&1; mv /work/out/input.pdf /work/output.pdf' sh "$extension");;
 VIDEO) command=(ffmpeg -nostdin -v error -protocol_whitelist file,pipe -i "/work/input.$extension" -map 0:v:0 -map '0:a:0?' -vf "$rotation_filter,pad=ceil(iw/2)*2:ceil(ih/2)*2" -map_metadata -1 -metadata:s:v:0 rotate=0 -c:v libx264 -pix_fmt yuv420p -preset fast -crf 23 -c:a aac -movflags +faststart -y /work/output.mp4);;
 AUDIO) command=(ffmpeg -nostdin -v error -i "/work/input.$extension" -vn -c:a libmp3lame -q:a 4 -y /work/output.mp3);;
 HEIF) command=(ffmpeg -nostdin -v error -protocol_whitelist file,pipe -i "/work/input.$extension" -frames:v 1 -vf "$rotation_filter" -map_metadata -1 -y /work/output.jpg);;
 IMAGE) image_ext=png; image_frames=(-frames:v 1); if [[ "$extension" = gif ]]; then image_ext=gif; image_frames=(); fi
  command=(ffmpeg -nostdin -v error -threads 1 -protocol_whitelist file,pipe -i "/work/input.$extension" "${image_frames[@]}" -vf "$rotation_filter" -map_metadata -1 -y "/work/output.$image_ext");;
 THUMBNAIL) command=(ffmpeg -nostdin -v error -threads 1 -protocol_whitelist file,pipe -i "/work/input.$extension" -map 0:v:0 -frames:v 1 -vf "$rotation_filter,scale=640:640:force_original_aspect_ratio=decrease" -map_metadata -1 -q:v 3 -y /work/output.jpg);;
 PDF_THUMBNAIL) command=(pdftoppm -f 1 -singlefile -scale-to 640 -jpeg -jpegopt quality=80 /work/input.pdf /work/output);;
 *) exit 2;;
esac
docker run --rm --name "$container_name" --network none --memory 1g --cpus 1 --pids-limit 128 \
 --read-only --tmpfs /tmp:rw,size=256m --cap-drop ALL --security-opt no-new-privileges \
 --user "$(id -u):$(id -g)" --mount "type=bind,src=$work,dst=/work" \
 dianxinyun-material-preview:local "${command[@]}"
