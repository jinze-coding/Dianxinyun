import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';

const formatBytes = (value) => {
  const bytes = Number(value || 0);
  if (!bytes) return '';
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(2)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
};

function AdministrativeDeletionDialog({ impact, onFinish }) {
  const [acknowledged, setAcknowledged] = useState(false);
  const exactName = String(impact.targetName || '').trim();
  const affectedItems = (impact.items || []).filter((item) => Number(item.count || 0) > 0);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onFinish(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onFinish]);

  return (
    <div
      role="presentation"
      onMouseDown={() => onFinish(null)}
      style={{ position: 'fixed', inset: 0, zIndex: 3000, display: 'grid', placeItems: 'center', padding: 20, background: 'rgba(15, 23, 42, .58)' }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-delete-title"
        onMouseDown={(event) => event.stopPropagation()}
        style={{ width: 'min(560px, 100%)', maxHeight: 'min(760px, 92vh)', overflow: 'auto', borderRadius: 12, background: '#fff', boxShadow: '0 24px 70px rgba(15, 23, 42, .28)', color: '#172033' }}
      >
        <header style={{ display: 'flex', justifyContent: 'space-between', gap: 16, padding: '18px 20px', borderBottom: '1px solid #e5eaf2' }}>
          <div>
            <h2 id="admin-delete-title" style={{ margin: 0, fontSize: 18 }}>管理员永久删除确认</h2>
            <p style={{ margin: '6px 0 0', color: '#7b8aa5', fontSize: 12 }}>删除后不可恢复，系统将在提交时重新校验关联数据。</p>
          </div>
          <button type="button" onClick={() => onFinish(null)} aria-label="关闭" style={{ alignSelf: 'flex-start', border: 0, background: 'transparent', color: '#718096', fontSize: 22, cursor: 'pointer' }}>×</button>
        </header>
        <div style={{ padding: 20 }}>
          <div style={{ padding: 14, borderRadius: 8, border: '1px solid #fecaca', background: '#fff5f5' }}>
            <span style={{ display: 'block', color: '#b91c1c', fontSize: 12 }}>即将永久删除</span>
            <strong style={{ display: 'block', marginTop: 5, overflowWrap: 'anywhere' }}>{exactName || `记录 ${impact.targetId}`}</strong>
          </div>
          <div style={{ marginTop: 16 }}>
            <strong style={{ fontSize: 13 }}>关联影响</strong>
            {affectedItems.length ? (
              <div style={{ display: 'grid', gap: 7, marginTop: 9 }}>
                {affectedItems.map((item) => (
                  <div key={item.code} style={{ display: 'flex', justifyContent: 'space-between', gap: 16, padding: '9px 11px', borderRadius: 7, background: '#f5f7fb', fontSize: 12 }}>
                    <span>{item.label}</span><strong>{item.count}</strong>
                  </div>
                ))}
              </div>
            ) : <p style={{ margin: '9px 0 0', color: '#7b8aa5', fontSize: 12 }}>未检测到其他关联数据。</p>}
            {Number(impact.fileCount || 0) > 0 && (
              <p style={{ margin: '10px 0 0', color: '#7b8aa5', fontSize: 12 }}>
                物理文件 {impact.fileCount} 个{formatBytes(impact.fileBytes) ? `，共 ${formatBytes(impact.fileBytes)}` : ''}
              </p>
            )}
          </div>
          <label style={{ display: 'flex', alignItems: 'flex-start', gap: 9, marginTop: 16, color: '#475569', fontSize: 12, lineHeight: 1.6 }}>
            <input autoFocus type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} style={{ marginTop: 3 }} />
            <span>我已核对上述影响，确认永久删除且了解该操作不可恢复。</span>
          </label>
        </div>
        <footer style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, padding: '14px 20px', borderTop: '1px solid #e5eaf2' }}>
          <button type="button" onClick={() => onFinish(null)} style={{ padding: '9px 16px', border: '1px solid #d8e0ec', borderRadius: 7, background: '#fff', color: '#475569', cursor: 'pointer' }}>取消</button>
          <button
            type="button"
            disabled={!acknowledged}
            onClick={() => onFinish(true)}
            style={{ padding: '9px 16px', border: 0, borderRadius: 7, background: !acknowledged ? '#f1b5b5' : '#dc2626', color: '#fff', cursor: !acknowledged ? 'not-allowed' : 'pointer' }}
          >
            确认永久删除
          </button>
        </footer>
      </section>
    </div>
  );
}

export function requestAdministrativeDeletionConfirmation(impact) {
  return new Promise((resolve) => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const root = createRoot(host);
    let finished = false;
    const finish = (value) => {
      if (finished) return;
      finished = true;
      root.unmount();
      host.remove();
      resolve(value);
    };
    root.render(<AdministrativeDeletionDialog impact={impact} onFinish={finish} />);
  });
}
