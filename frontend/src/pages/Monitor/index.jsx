import React, { useState } from 'react';
import { DATA_BY_PROJECT } from '@/constants/mockData';
import StatCard from '@/components/StatCard';
import VideoCell from '@/components/VideoCell';

// 设备与监控页面
export function MonitorPage({ projectId, theme: T, compactMode }) {
  const data = DATA_BY_PROJECT[projectId];
  const cameras = data.cameras;
  const devices = data.devices;

  const [videoLayout, setVideoLayout] = useState('quad');
  const [fullscreenCam, setFullscreenCam] = useState(null);

  const layouts = { single: 1, quad: 4, eight: 8, sixteen: 16 };
  const onlineCount = cameras.filter(c => c.online).length;
  const runningDevices = devices.filter(d => d.status === '运行中').length;
  const abnormalDevices = devices.filter(d => d.status === '异常').length;

  const getDeviceStatusColor = status => {
    if (status === '运行中') return T.success;
    if (status === '停机') return T.warning;
    if (status === '异常') return T.danger;
    return T.textMuted;
  };

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: compactMode ? '200px 1fr 260px' : '240px 1fr 300px',
        gridTemplateRows: 'auto 1fr',
        gap: 12,
        padding: 12,
        height: '100%',
        overflow: 'hidden',
      }}
    >
      {/* 左侧：设备统计 */}
      <div style={{ gridColumn: '1', gridRow: '1 / 3', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            padding: 16,
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary, marginBottom: 12 }}>设备总览</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: 11, color: T.textMuted }}>设备总数</span>
              <span style={{ fontSize: 14, fontWeight: 600, color: T.textPrimary }}>{devices.length}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: 11, color: T.textMuted }}>运行中</span>
              <span style={{ fontSize: 14, fontWeight: 600, color: T.success }}>{runningDevices}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: 11, color: T.textMuted }}>异常</span>
              <span style={{ fontSize: 14, fontWeight: 600, color: T.danger }}>{abnormalDevices}</span>
            </div>
          </div>
        </div>

        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            padding: 16,
            flex: 1,
            overflow: 'auto',
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary, marginBottom: 12 }}>设备列表</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {devices.map(d => (
              <div key={d.id} style={{ padding: 10, borderRadius: 6, border: `1px solid ${T.borderColor}` }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                  <span style={{ fontSize: 12, color: T.textPrimary }}>{d.name}</span>
                  <span
                    style={{
                      fontSize: 10,
                      padding: '2px 6px',
                      borderRadius: 3,
                      background: `${getDeviceStatusColor(d.status)}22`,
                      color: getDeviceStatusColor(d.status),
                    }}
                  >
                    {d.status}
                  </span>
                </div>
                <div style={{ fontSize: 10, color: T.textMuted }}>
                  {d.code} | {d.type}
                </div>
                <div style={{ fontSize: 10, color: T.textMuted, marginTop: 2 }}>上报: {d.lastReport}</div>
                {d.note && (
                  <div style={{ fontSize: 10, color: T.warning, marginTop: 2 }}>{d.note}</div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 中间：视频监控 */}
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
            <div>
              <div style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary }}>实时视频监控</div>
              <div style={{ fontSize: 11, color: T.textMuted, marginTop: 4 }}>
                在线: {onlineCount}/{cameras.length}
              </div>
            </div>
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
            {cameras.slice(0, layouts[videoLayout]).map(cam => (
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

      {/* 右侧：塔吊系统 */}
      <div style={{ gridColumn: '3', gridRow: '1 / 3', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            padding: 16,
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary, marginBottom: 12 }}>塔吊系统接入</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <StatCard
              label="塔吊数量"
              value={devices.filter(d => d.type === '塔吊').length}
              theme={T}
            />
            <StatCard
              label="运行中"
              value={devices.filter(d => d.type === '塔吊' && d.status === '运行中').length}
              color={T.success}
              theme={T}
            />
          </div>
        </div>

        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            padding: 16,
            flex: 1,
            overflow: 'auto',
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary, marginBottom: 12 }}>重点设备</div>
          {devices
            .filter(d => d.type === '塔吊')
            .map(d => (
              <div
                key={d.id}
                style={{
                  padding: 10,
                  borderRadius: 6,
                  border: `1px solid ${T.borderColor}`,
                  marginBottom: 8,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: 6,
                  }}
                >
                  <span style={{ fontSize: 12, color: T.textPrimary }}>{d.name}</span>
                  <span
                    style={{
                      fontSize: 10,
                      padding: '2px 6px',
                      borderRadius: 3,
                      background: `${getDeviceStatusColor(d.status)}22`,
                      color: getDeviceStatusColor(d.status),
                    }}
                  >
                    {d.status}
                  </span>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                  <div>
                    <div style={{ fontSize: 10, color: T.textMuted }}>高度</div>
                    <div style={{ fontSize: 11, color: T.textPrimary }}>{d.height}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10, color: T.textMuted }}>最大载重</div>
                    <div style={{ fontSize: 11, color: T.textPrimary }}>{d.maxLoad}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10, color: T.textMuted }}>设备编号</div>
                    <div style={{ fontSize: 11, color: T.textPrimary }}>{d.code}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10, color: T.textMuted }}>最近上报</div>
                    <div style={{ fontSize: 11, color: T.textPrimary }}>{d.lastReport}</div>
                  </div>
                </div>
                {d.note && (
                  <div
                    style={{
                      fontSize: 10,
                      color: T.warning,
                      marginTop: 6,
                      paddingTop: 6,
                      borderTop: `1px solid ${T.borderColor}`,
                    }}
                  >
                    {d.note}
                  </div>
                )}
              </div>
            ))}
        </div>
      </div>
    </div>
  );
}

export default MonitorPage;
