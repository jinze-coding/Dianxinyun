#!/usr/bin/env python3
"""Exercise the frozen Linux launcher locally with synthetic media only.

On macOS the harness selects the launcher's Linux branch and replaces only the
image tag. The checked-in launcher itself is unchanged. Requires Pillow.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid
import zipfile
from PIL import Image, ImageChops


def run(command, log, timeout=240):
    with log.open('w') as stream:
        result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT,
                                text=True, timeout=timeout, check=False)
    if result.returncode:
        raise RuntimeError('Command failed: ' + str(log))


def nonblank(path):
    image = Image.open(path).convert('RGB')
    if max(image.size) > 640 or not ImageChops.difference(image, Image.new('RGB', image.size, 'white')).getbbox():
        raise RuntimeError('Invalid or blank thumbnail: ' + str(path))
    return image.size


def chinese_pdf(path):
    text = '中文巡检附件预览验证'
    stream = ('BT /F1 28 Tf 40 120 Td <' + text.encode('utf-16-be').hex() + '> Tj ET').encode()
    objects = [
        b'<< /Type /Catalog /Pages 2 0 R >>',
        b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 180] /Resources << /Font << /F1 4 0 R >> >> /Contents 7 0 R >>',
        b'<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H /DescendantFonts [5 0 R] >>',
        b'<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light /CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 4 >> /FontDescriptor 6 0 R /DW 1000 >>',
        b'<< /Type /FontDescriptor /FontName /STSong-Light /Flags 4 /FontBBox [-25 -254 1000 880] /ItalicAngle 0 /Ascent 752 /Descent -271 /CapHeight 737 /StemV 58 >>',
        b'<< /Length ' + str(len(stream)).encode() + b' >>\nstream\n' + stream + b'\nendstream',
    ]
    data = bytearray(b'%PDF-1.4\n'); offsets = [0]
    for i, obj in enumerate(objects, 1):
        offsets.append(len(data)); data.extend(str(i).encode() + b' 0 obj\n' + obj + b'\nendobj\n')
    start = len(data)
    data.extend(('xref\n0 %d\n0000000000 65535 f \n' % len(offsets)).encode())
    for offset in offsets[1:]:
        data.extend(('%010d 00000 n \n' % offset).encode())
    data.extend(('trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n' % (len(offsets), start)).encode())
    path.write_bytes(data)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--image', required=True)
    parser.add_argument('--launcher', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(mode=0o700)
    run_id = uuid.uuid4().hex[:10]
    info = json.loads(subprocess.check_output(['docker', 'image', 'inspect', '--platform', 'linux/amd64', args.image], text=True))[0]
    assert info['Os'] == 'linux' and info['Architecture'] == 'amd64'
    assert info['Config']['Labels']['dxy.source-manifest-sha256'] == '95e6a1fc24f569b3c9224d01971d3193b4dd40044401023e9b94393ea408c9af'
    fixtures = args.output / 'fixtures'; fixtures.mkdir()
    image = Image.new('RGB', (160, 96), '#2d80d3')
    image.paste('#e84e4e', (0, 0, 80, 48)); image.paste('#40aa61', (80, 48, 160, 96))
    image.save(fixtures / 'sample.png'); image.save(fixtures / 'sample.jpg', quality=95)
    chinese_pdf(fixtures / 'chinese.pdf')
    with zipfile.ZipFile(fixtures / 'chinese.docx', 'w') as archive:
        archive.writestr('[Content_Types].xml', '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        archive.writestr('_rels/.rels', '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        archive.writestr('word/document.xml', '<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>巡检附件预览验证</w:t></w:r></w:p><w:p><w:r><w:t>这是离线转换组件的合成测试文件，不含业务数据。</w:t></w:r></w:p><w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr></w:body></w:document>')
    flags = ['docker', 'run', '--rm', '--platform', 'linux/amd64', '--network', 'none', '--memory', '1g', '--cpus', '1', '--pids-limit', '128', '--read-only', '--tmpfs', '/tmp:rw,size=256m', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--user', '%d:%d' % (os.getuid(), os.getgid())]
    run(flags + ['--mount', 'type=bind,src=%s,dst=/work' % fixtures, args.image, 'ffmpeg', '-v', 'error', '-threads', '1', '-loop', '1', '-framerate', '5', '-i', '/work/sample.png', '-t', '1', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '/work/sample.mp4'], args.output / 'create-video.log')
    launcher = args.launcher.resolve()
    # A harness-only function selects the existing Linux branch on the Mac.
    harness = '''
uname() { if [ "$1" = -s ]; then printf 'Linux\\n'; else command uname "$@"; fi; }
docker() {
  local value
  local replaced=()
  for value in "$@"; do
    if [ "$value" = dianxinyun-material-preview:local ]; then value="$DXY_QA_IMAGE"; fi
    replaced+=("$value")
  done
  command docker "${replaced[@]}"
}
export -f uname docker
export DXY_QA_IMAGE="$1"
bash "$2" "$3" "$4" "$5" "$6"
'''
    results = []
    def convert(name, source, kind, extension, rotation=0, output='output.jpg'):
        work = args.output / ('qa-' + run_id + '-' + name); work.mkdir()
        target = work / ('input.' + extension); shutil.copyfile(source, target)
        before = hashlib.sha256(target.read_bytes()).hexdigest()
        run(['bash', '-c', harness, 'runtime-qa', args.image, str(launcher), str(work), kind, extension, str(rotation)], args.output / (name + '.log'))
        assert hashlib.sha256(target.read_bytes()).hexdigest() == before
        converted = work / output; assert converted.is_file() and converted.stat().st_size > 0
        results.append({'test': name, 'inputUnchanged': True, 'outputBytes': converted.stat().st_size})
        return converted
    for extension in ('png', 'jpg'):
        for rotation in (0, 90, 180, 270):
            result = convert('%s-%d' % (extension, rotation), fixtures / ('sample.' + extension), 'THUMBNAIL', extension, rotation)
            dimensions = nonblank(result)
            assert dimensions == ((384, 640) if rotation in (90, 270) else (640, 384))
            if extension == 'png':
                actual = Image.open(result).convert('RGB')
                expected = image.rotate(-rotation, expand=True).resize(dimensions)
                for point in ((20, 20), (actual.width - 20, actual.height - 20)):
                    assert max(abs(a-b) for a,b in zip(actual.getpixel(point), expected.getpixel(point))) < 30
    thumbnail = convert('video-thumbnail', fixtures / 'sample.mp4', 'THUMBNAIL', 'mp4')
    assert nonblank(thumbnail) == (640, 384)
    assert max(abs(a-b) for a,b in zip(Image.open(thumbnail).convert('RGB').getpixel((20, 20)), image.getpixel((20, 20)))) < 30
    video = convert('video-transcode-90', fixtures / 'sample.mp4', 'VIDEO', 'mp4', 90, 'output.mp4')
    probe = subprocess.check_output(flags + ['--mount', 'type=bind,src=%s,dst=/work,readonly' % video.parent, args.image, 'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=codec_name,width,height', '-of', 'json', '/work/output.mp4'], text=True)
    stream = json.loads(probe)['streams'][0]; assert stream == {'codec_name': 'h264', 'width': 96, 'height': 160}
    rotated = convert('video-transcoded-frame', video, 'THUMBNAIL', 'mp4')
    assert nonblank(rotated) == (384, 640)
    assert max(abs(a-b) for a,b in zip(Image.open(rotated).convert('RGB').getpixel((20, 20)), image.rotate(-90, expand=True).getpixel((20, 20)))) < 30
    nonblank(convert('chinese-unembedded-pdf', fixtures / 'chinese.pdf', 'PDF_THUMBNAIL', 'pdf'))
    document = convert('chinese-docx-pdf', fixtures / 'chinese.docx', 'OFFICE', 'docx', output='output.pdf')
    nonblank(convert('chinese-docx-thumbnail', document, 'PDF_THUMBNAIL', 'pdf'))
    report = {'passed': True, 'platform': 'linux/amd64', 'executionHost': 'macOS via Docker emulation', 'imageId': info['Id'], 'sourceManifestSha256': info['Config']['Labels']['dxy.source-manifest-sha256'], 'launcherSha256': hashlib.sha256(launcher.read_bytes()).hexdigest(), 'tests': results}
    (args.output / 'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({'passed': True, 'tests': len(results), 'report': str(args.output / 'result.json')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
