import React from 'react';

function StatusBadge({ status, theme: T }) {
  const map = {
    '已教育': T.success,
    '待教育': T.warning,
    '已离场': T.textMuted,
    '已完成': T.success,
    '进行中': T.accent,
    '未开始': T.textMuted,
  };
  return (
    <span style={{
      fontSize: 10,
      padding: '2px 7px',
      borderRadius: 3,
      border: `1px solid ${map[status] || T.borderColor}`,
      color: map[status] || T.textMuted,
      background: 'transparent',
    }}>{status}</span>
  );
}

export default StatusBadge;