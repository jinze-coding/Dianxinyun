import React from 'react';

// 视频窗口组件 - 匹配原始UI
export function VideoCell({ cam, theme: T, onFullscreen, fullscreen }) {
  const baseStyle = fullscreen ? { width: '100%', height: '100%' } : { aspectRatio: '16/9' };

  return (
    <div
      style={{
        background: '#060e1c',
        border: `1px solid ${T.borderColor}`,
        borderRadius: 6,
        overflow: 'hidden',
        position: 'relative',
        ...baseStyle,
      }}
    >
      {cam.online ? (
        <>
          {/* 视频区域 */}
          <div
            style={{
              width: '100%',
              height: '100%',
              background: 'linear-gradient(135deg, #060e1c 0%, #0a1a2e 40%, #071520 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <div style={{ textAlign: 'center', opacity: 0.3 }}>
              <svg
                width={fullscreen ? 64 : 32}
                height={fullscreen ? 64 : 32}
                viewBox="0 0 24 24"
                fill="none"
                stroke={T.accent}
                strokeWidth="1.5"
              >
                <path d="M15 10l4.553-2.069A1 1 0 0121 8.82v6.36a1 1 0 01-1.447.889L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
              </svg>
              <div style={{ fontSize: fullscreen ? 14 : 10, color: T.textMuted, marginTop: 4 }}>
                实时视频流
              </div>
            </div>
          </div>

          {/* LIVE标签 - 左上角 */}
          <div
            style={{
              position: 'absolute',
              top: 6,
              left: 6,
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              background: 'rgba(0,0,0,0.6)',
              padding: '2px 6px',
              borderRadius: 3,
            }}
          >
            <span
              style={{
                width: 5,
                height: 5,
                borderRadius: '50%',
                background: T.success,
                display: 'block',
              }}
            ></span>
            <span style={{ fontSize: 10, color: '#fff' }}>LIVE</span>
          </div>

          {/* 底部信息 - 左右分布 */}
          <div
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              background: 'linear-gradient(transparent, rgba(0,0,0,0.7))',
              padding: '8px 6px 4px',
              fontSize: fullscreen ? 13 : 10,
              color: 'rgba(255,255,255,0.7)',
              display: 'flex',
              justifyContent: 'space-between',
            }}
          >
            <span>{cam.name}</span>
            <span style={{ color: T.textMuted }}>{cam.area}</span>
          </div>

          {/* 全屏按钮 - 非全屏时显示 */}
          {!fullscreen && (
            <button
              onClick={onFullscreen}
              title="全屏"
              style={{
                position: 'absolute',
                top: 6,
                right: 6,
                background: 'rgba(0,0,0,0.5)',
                border: 'none',
                borderRadius: 3,
                padding: '2px 5px',
                cursor: 'pointer',
                color: '#ccc',
                fontSize: 10,
              }}
            >
              ⛶
            </button>
          )}
        </>
      ) : (
        /* 离线状态 */
        <div
          style={{
            width: '100%',
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6,
            background: '#08111e',
          }}
        >
          <svg
            width={fullscreen ? 48 : 24}
            height={fullscreen ? 48 : 24}
            viewBox="0 0 24 24"
            fill="none"
            stroke="#444"
            strokeWidth="1.5"
          >
            <line x1="1" y1="1" x2="23" y2="23" />
            <path d="M16.5 16.5A7 7 0 0 1 5.5 5.5M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h4" />
          </svg>
          <span style={{ fontSize: fullscreen ? 14 : 10, color: '#444' }}>离线</span>
        </div>
      )}
    </div>
  );
}

export default VideoCell;
