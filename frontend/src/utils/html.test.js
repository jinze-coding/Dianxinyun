import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildElectricBoxQrLabelHtml,
  escapeHtml,
  normalizeQrImageSource,
} from './html.js';

test('escapeHtml escapes text and attribute delimiters', () => {
  assert.equal(
    escapeHtml(`<script data-name="x">'&</script>`),
    '&lt;script data-name=&quot;x&quot;&gt;&#39;&amp;&lt;/script&gt;',
  );
});

test('normalizeQrImageSource only accepts generated PNG and encoded SVG data URLs', () => {
  assert.equal(
    normalizeQrImageSource('data:image/png;base64,QUJDRA=='),
    'data:image/png;base64,QUJDRA==',
  );
  const encodedSvg = normalizeQrImageSource('<svg xmlns="http://www.w3.org/2000/svg"></svg>');
  assert.match(encodedSvg, /^data:image\/svg\+xml;charset=utf-8,/);
  assert.equal(encodedSvg.includes('<svg'), false);
  assert.equal(normalizeQrImageSource('javascript:alert(1)'), '');
  assert.equal(normalizeQrImageSource('data:text/html,<script>alert(1)</script>'), '');
  assert.equal(normalizeQrImageSource('data:image/svg+xml;charset=utf-8,<svg onload=alert(1)>'), '');
});

test('QR label HTML never treats electric-box fields as markup', () => {
  const html = buildElectricBoxQrLabelHtml(
    {
      boxCode: `A-01"><img src=x onerror=alert(1)>`,
      boxName: '<script>alert(2)</script>',
      installLocation: `一层 & "配电间"`,
    },
    {
      unifiedSvg: 'javascript:alert(3)',
      unifiedPayload: `B:CODE</small><script>alert(4)</script>`,
    },
  );

  assert.equal(html.includes('<script>'), false);
  assert.equal(html.includes('<img src=x'), false);
  assert.match(html, /A-01&quot;&gt;&lt;img/);
  assert.match(html, /&lt;script&gt;alert\(2\)&lt;\/script&gt;/);
  assert.match(html, /二维码图像不可用/);
});
