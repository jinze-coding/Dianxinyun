import React from 'react';

// 统计卡片组件
export function StatCard({ label, value, sub, color, theme: T }) {
  return (
    <div
      style={{
        background: T.cardBg,
        border: `1px solid ${T.borderColor}`,
        borderRadius: T.radius,
        padding: '14px 16px',
        flex: 1,
      }}
    >
      <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 700, color: color || T.textPrimary, lineHeight: 1 }}>
        {value}
      </div>
      {sub && (
        <div style={{ fontSize: 11, color: T.textMuted, marginTop: 4 }}>{sub}</div>
      )}
    </div>
  );
}

export default StatCard;
