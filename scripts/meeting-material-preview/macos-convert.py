#!/usr/bin/env python3
"""Local macOS converter. Untrusted parsers run in a no-network, restricted-file sandbox."""
import os
from pathlib import Path
import resource
import shutil
import signal
import subprocess
import sys
import time
from xml.sax.saxutils import escape

work = Path(sys.argv[1]).resolve(strict=True)
kind, extension = sys.argv[2:4]
if not extension.isalnum() or kind not in {"OFFICE", "VIDEO", "AUDIO", "HEIF"}:
    raise SystemExit(2)
source = work / ("input." + extension)
if not source.is_file():
    raise SystemExit(2)
if kind == "OFFICE":
    tool = Path(os.environ.get("MEETING_SOFFICE_BIN") or shutil.which("soffice") or "/Applications/LibreOffice.app/Contents/MacOS/soffice").resolve(strict=True)
    # Bundled development runtimes may expose a small wrapper alongside the actual app.
    if tool.read_bytes()[:2] == b"#!":
        apps = list((tool.parent / "../../native/libreoffice-headless/libreoffice").resolve().glob("*.app/Contents/MacOS/soffice"))
        if not apps:
            raise RuntimeError("Set MEETING_SOFFICE_BIN to the LibreOffice executable")
        tool = apps[0].resolve()
    tool_root = next((p for p in tool.parents if p.suffix == ".app"), tool.parent)
    command = [str(tool), "-env:UserInstallation=" + (work / "profile").as_uri(), "--headless", "--convert-to", "pdf", "--outdir", str(work), str(source)]
    output_ext = "pdf"
else:
    tool = Path(os.environ.get("MEETING_FFMPEG_BIN") or shutil.which("ffmpeg") or "/opt/homebrew/bin/ffmpeg").resolve(strict=True)
    tool_root = tool.parent.parent
    command = [str(tool), "-nostdin", "-v", "error", "-threads", "1", "-protocol_whitelist", "file,pipe", "-i", str(source)]
    if kind == "VIDEO":
        output_ext = "mp4"
        command += ["-map", "0:v:0", "-map", "0:a:0?", "-c:v", "libx264", "-threads", "1", "-preset", "fast", "-crf", "23", "-c:a", "aac", "-movflags", "+faststart"]
    elif kind == "AUDIO":
        output_ext = "mp3"
        command += ["-vn", "-c:a", "libmp3lame", "-q:a", "4"]
    else:
        output_ext = "jpg"
        command += ["-frames:v", "1"]
    command += ["-y", str(work / ("output." + output_ext))]

profile = Path(__file__).with_name("macos.sb").resolve()
env = {"PATH": "/usr/bin:/bin", "HOME": str(work), "TMPDIR": str(work / "tmp"), "LANG": "en_US.UTF-8"}
(work / "tmp").mkdir(exist_ok=True)
if kind == "OFFICE":
    # Headless distributions may have a build-machine Fontconfig default path.
    # Pin readable local fonts and a per-job cache, including Chinese fallbacks.
    font_dirs = [Path("/System/Library/Fonts"), Path("/Library/Fonts"), tool_root / "Contents/Resources/fonts"]
    font_config = work / "fonts.conf"
    font_config.write_text('<?xml version="1.0"?><fontconfig>' + ''.join('<dir>' + escape(str(p)) + '</dir>' for p in font_dirs)
        + '<cachedir>' + escape(str(work / "font-cache")) + '</cachedir></fontconfig>')
    env["FONTCONFIG_FILE"] = str(font_config)
    env["FONTCONFIG_PATH"] = str(work)
def limits():
    resource.setrlimit(resource.RLIMIT_CPU, (1500, 1500))
    resource.setrlimit(resource.RLIMIT_FSIZE, (1073741824, 1073741824))
    resource.setrlimit(resource.RLIMIT_NOFILE, (256, 256))

process = subprocess.Popen(["/usr/bin/sandbox-exec", "-D", "WORK=" + str(work), "-D", "TOOL_ROOT=" + str(tool_root), "-f", str(profile), *command],
                           cwd=work, env=env, preexec_fn=limits, start_new_session=True)
def stop(*_):
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)
deadline = time.monotonic() + 1700
try:
    while process.poll() is None:
        rows = subprocess.check_output(["/bin/ps", "-axo", "pgid=,rss="], text=True)
        usage = [int(row.split()[1]) for row in rows.splitlines() if row.split() and int(row.split()[0]) == process.pid]
        if sum(usage) > 1024 * 1024 or len(usage) > 128 or time.monotonic() > deadline:
            stop()
            raise RuntimeError("Preview process resource limit exceeded")
        time.sleep(0.5)
    if process.returncode:
        raise SystemExit(process.returncode)
    if kind == "OFFICE":
        (work / "input.pdf").replace(work / "output.pdf")
finally:
    stop()
