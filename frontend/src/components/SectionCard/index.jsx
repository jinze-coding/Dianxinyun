import React from 'react';

function SectionCard({ title, children, action, theme: T }) {
  return (
    <div style={{
      background: T.cardBg,
      border: `1px solid ${T.borderColor}`,
      borderRadius: T.radius,
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
      height: '100%',
    }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '12px 16px',
        borderBottom: `1px solid ${T.borderColor}`,
        flexShrink: 0,
      }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: T.textPrimary }}>{title}</span>
        {action}
      </div>
      <div style={{ flex: 1, overflow: 'auto', padding: 12 }}>{children}</div>
    </div>
  );
}

export default SectionCard;