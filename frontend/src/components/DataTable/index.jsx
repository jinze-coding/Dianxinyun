import React from 'react';

// 数据表格组件
export function DataTable({
  columns,
  data,
  onRowClick,
  theme: T,
  emptyText = '暂无数据',
}) {
  return (
    <div style={{ width: '100%', overflow: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ borderBottom: `1px solid ${T.borderColor}` }}>
            {columns.map(col => (
              <th
                key={col.key}
                style={{
                  padding: '10px 8px',
                  fontSize: 11,
                  color: T.textMuted,
                  fontWeight: 400,
                  textAlign: col.align || 'left',
                  minWidth: col.width || 'auto',
                }}
              >
                {col.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.length === 0 ? (
            <tr>
              <td
                colSpan={columns.length}
                style={{
                  padding: '40px 0',
                  textAlign: 'center',
                  color: T.textMuted,
                  fontSize: 13,
                }}
              >
                {emptyText}
              </td>
            </tr>
          ) : (
            data.map((row, index) => (
              <tr
                key={row.id || index}
                onClick={() => onRowClick && onRowClick(row)}
                style={{
                  borderBottom: `1px solid ${T.borderColor}`,
                  cursor: onRowClick ? 'pointer' : 'default',
                  transition: 'background 0.15s',
                }}
                onMouseEnter={e => {
                  if (onRowClick) e.currentTarget.style.background = T.hoverBg;
                }}
                onMouseLeave={e => {
                  if (onRowClick) e.currentTarget.style.background = 'transparent';
                }}
              >
                {columns.map(col => (
                  <td
                    key={col.key}
                    style={{
                      padding: '10px 8px',
                      fontSize: 12,
                      color: T.textPrimary,
                      textAlign: col.align || 'left',
                    }}
                  >
                    {col.render ? col.render(row[col.key], row) : row[col.key]}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default DataTable;
