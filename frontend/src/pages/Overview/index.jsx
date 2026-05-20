import React, { useState } from 'react';
import { PROJECT_INFO, DATA_BY_PROJECT } from '@/constants/mockData';
import StatCard from '@/components/StatCard';
import VideoCell from '@/components/VideoCell';

// 项目概况页面
export function OverviewPage({ projectId, theme: T, compactMode }) {
  const info = PROJECT_INFO[projectId];
  const data = DATA_BY_PROJECT[projectId];
  const stats = data.stats;
  const docs = data.docs;
  const onlineCount = data.cameras.filter(c => c.online).length;
  const totalCameras = data.cameras.length;

  const [videoLayout, setVideoLayout] = useState('quad');
  const [fullscreenCam, setFullscreenCam] = useState(null);

  const layouts = { single: 1, quad: 4, eight: 8, sixteen: 16 };

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: compactMode ? '240px 1fr 260px' : '280px 1fr 300px',
        gridTemplateRows: 'auto 1fr auto',
        gap: 12,
        padding: 12,
        height: '100%',
        overflow: 'hidden',
      }}
    >
      {/* 左侧：项目基础信息 */}
      <div style={{ gridColumn: '1', gridRow: '1 / 4', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            padding: 16,
          }}
        >
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: T.textPrimary,
              marginBottom: 12,
              paddingBottom: 10,
              borderBottom: `1px solid ${T.borderColor}`,
            }}
          >
            项目基础信息
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {[
              ['项目名称', info.name],
              ['建筑面积', info.area],
              ['工期', info.period],
              ['当前阶段', info.phase],
              ['项目状态', info.projectStatus],
              ['安全目标', info.safetyGoal],
              ['质量目标', info.qualityGoal],
              ['项目经理', info.manager],
              ['施工单位', info.contractor],
            ].map(([label, val]) => (
              <div key={label} style={{ display: 'flex', gap: 8 }}>
                <span style={{ fontSize: 11, color: T.textMuted, minWidth: 60 }}>{label}</span>
                <span style={{ fontSize: 11, color: T.textPrimary, flex: 1 }}>{val}</span>
              </div>
            ))}
          </div>
        </div>

        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            padding: 16,
          }}
        >
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: T.textPrimary,
              marginBottom: 12,
              paddingBottom: 10,
              borderBottom: `1px solid ${T.borderColor}`,
            }}
          >
            原人员管理系统入口
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
            <div style={{ width: 10, height: 10, borderRadius: '50%', background: T.success }}></div>
            <span style={{ fontSize: 12, color: T.textSecondary }}>系统正常</span>
          </div>
          <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 12, lineHeight: 1.5 }}>
            点击下方按钮跳转至原人员管理系统，进行正式员工的相关管理操作。
          </div>
          <button
            style={{
              width: '100%',
              padding: '8px 12px',
              borderRadius: 6,
              border: 'none',
              background: T.accent,
              color: '#fff',
              fontSize: 12,
              cursor: 'pointer',
            }}
          >
            进入人员管理系统
          </button>
        </div>
      </div>

      {/* 中间：视频总览 */}
      <div style={{ gridColumn: '2', gridRow: '1 / 3', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            padding: 16,
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary }}>视频总览</div>
            <div style={{ display: 'flex', gap: 6 }}>
              {[['单屏', 'single'], ['四宫格', 'quad'], ['八窗口', 'eight'], ['十六窗口', 'sixteen']].map(
                ([label, v]) => (
                  <button
                    key={v}
                    onClick={() => {
                      setVideoLayout(v);
                      setFullscreenCam(null);
                    }}
                    style={{
                      padding: '4px 10px',
                      borderRadius: 4,
                      border: 'none',
                      fontSize: 11,
                      cursor: 'pointer',
                      background: videoLayout === v ? T.accent : T.surface2,
                      color: videoLayout === v ? '#fff' : T.textSecondary,
                    }}
                  >
                    {label}
                  </button>
                )
              )}
            </div>
          </div>
          <div
            style={{
              flex: 1,
              display: 'grid',
              gridTemplateColumns: `repeat(${videoLayout === 'single' ? 1 : videoLayout === 'quad' ? 2 : 4}, 1fr)`,
              gridTemplateRows: `repeat(${
                videoLayout === 'single' ? 1 : videoLayout === 'quad' ? 2 : videoLayout === 'eight' ? 2 : 4
              }, 1fr)`,
              gap: 8,
              overflow: 'hidden',
            }}
          >
            {data.cameras.slice(0, layouts[videoLayout]).map(cam => (
              <VideoCell
                key={cam.id}
                cam={cam}
                theme={T}
                fullscreen={fullscreenCam === cam.id}
                onFullscreen={() => setFullscreenCam(fullscreenCam ? null : cam.id)}
              />
            ))}
          </div>
        </div>
      </div>

      {/* 右侧：统计与资料 */}
      <div style={{ gridColumn: '3', gridRow: '1 / 3', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <StatCard label="在场人数" value={stats.onsite} sub="人" color={T.accent} theme={T} />
          <StatCard label="今日新增" value={stats.todayNewOnsite} sub="人" color={T.success} theme={T} />
        </div>

        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            padding: 16,
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: T.textPrimary,
              marginBottom: 12,
              paddingBottom: 10,
              borderBottom: `1px solid ${T.borderColor}`,
            }}
          >
            资料管理
          </div>
          <div style={{ flex: 1, overflow: 'auto' }}>
            {docs.map(doc => (
              <div
                key={doc.id}
                style={{
                  padding: '8px 0',
                  borderBottom: `1px solid ${T.borderColor}`,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ fontSize: 11, color: T.textPrimary }}>{doc.name}</div>
                  <div style={{ fontSize: 10, color: T.textMuted, marginTop: 2 }}>
                    {doc.uploader} · {doc.time}
                  </div>
                </div>
                <span
                  style={{
                    fontSize: 10,
                    padding: '2px 6px',
                    borderRadius: 3,
                    background:
                      doc.status === '已归档'
                        ? `${T.success}22`
                        : doc.status === '待确认'
                        ? `${T.warning}22`
                        : `${T.accent}22`,
                    color:
                      doc.status === '已归档'
                        ? T.success
                        : doc.status === '待确认'
                        ? T.warning
                        : T.accent,
                  }}
                >
                  {doc.status}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 底部：动态信息 */}
      <div
        style={{
          gridColumn: '2 / 4',
          gridRow: '3',
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: T.radius,
          padding: '10px 16px',
        }}
      >
        <div style={{ fontSize: 11, color: T.textMuted }}>
          <span style={{ marginRight: 16 }}>
            视频在线: {onlineCount}/{totalCameras}
          </span>
          <span style={{ marginRight: 16 }}>施工进度: {stats.progressPercent}%</span>
          <span>数据更新: {new Date().toLocaleTimeString()}</span>
        </div>
      </div>
    </div>
  );
}

export default OverviewPage;
