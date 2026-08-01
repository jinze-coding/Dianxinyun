const HTML_ESCAPE_MAP = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => HTML_ESCAPE_MAP[character]);
}

export function normalizeQrImageSource(imageContent) {
  const content = String(imageContent || '').trim();
  if (!content) return '';
  if (content.startsWith('<svg')) {
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(content)}`;
  }
  if (/^data:image\/png;base64,[A-Za-z0-9+/=]+$/i.test(content)) {
    return content;
  }
  const svgPrefix = 'data:image/svg+xml;charset=utf-8,';
  if (content.toLowerCase().startsWith(svgPrefix)
      && content.length > svgPrefix.length
      && !/[<>"&\s]/.test(content.slice(svgPrefix.length))) {
    return content;
  }
  return '';
}

export function buildElectricBoxQrLabelHtml(box = {}, qrData = {}) {
  const imageSource = normalizeQrImageSource(qrData.unifiedSvg);
  const image = imageSource
    ? `<img src="${escapeHtml(imageSource)}" alt="统一电箱巡检码" />`
    : '<span class="qr-missing">二维码图像不可用</span>';
  return `
    <section class="qr-label">
      <header>
        <strong>${escapeHtml(box.boxCode)}</strong>
        <span>${escapeHtml(box.boxName || '现场电箱')}</span>
      </header>
      <p>${escapeHtml(box.installLocation)}</p>
      <div class="qr-grid">
        <div class="qr-block">
          ${image}
          <b>统一电箱巡检码</b>
          <small>内部人员巡检 / 外部人员查看月度记录共用</small>
          <small>${escapeHtml(qrData.unifiedPayload)}</small>
        </div>
      </div>
      <footer>请勿覆盖、撕毁或转贴。二维码换绑后旧码不可继续巡检。</footer>
    </section>
  `;
}
