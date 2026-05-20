import React, { useEffect } from 'react';

// 模态框组件
export function Modal({
  visible,
  onClose,
  title,
  children,
  theme: T,
  width = 520,
  showFooter = true,
  footer,
}) {
  useEffect(() => {
    const handleEsc = (e) => {
      if (e.key === 'Escape' && visible) {
        onClose();
      }
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [visible, onClose]);

  if (!visible) return null;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        animation: 'fadeIn 0.2s ease',
      }}
      onClick={onClose}
    >
      <div
        style={{
          background: T.modalBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: T.radius,
          width: width,
          maxWidth: '90vw',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          animation: 'slideUp 0.2s ease',
        }}
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '16px 20px',
            borderBottom: `1px solid ${T.borderColor}`,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span style={{ fontSize: 14, fontWeight: 600, color: T.textPrimary }}>{title}</span>
          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: T.textMuted,
              fontSize: 18,
              padding: 4,
              lineHeight: 1,
            }}
          >
            ×
          </button>
        </div>

        {/* Content */}
        <div style={{ padding: 20, flex: 1, overflow: 'auto' }}>{children}</div>

        {/* Footer */}
        {showFooter && (
          <div
            style={{
              padding: '12px 20px',
              borderTop: `1px solid ${T.borderColor}`,
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 10,
            }}
          >
            {footer || (
              <>
                <button
                  onClick={onClose}
                  style={{
                    padding: '8px 16px',
                    borderRadius: 6,
                    border: `1px solid ${T.borderColor}`,
                    background: 'transparent',
                    color: T.textSecondary,
                    fontSize: 13,
                    cursor: 'pointer',
                  }}
                >
                  取消
                </button>
                <button
                  style={{
                    padding: '8px 16px',
                    borderRadius: 6,
                    border: 'none',
                    background: T.accent,
                    color: '#fff',
                    fontSize: 13,
                    cursor: 'pointer',
                  }}
                >
                  确认
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default Modal;
