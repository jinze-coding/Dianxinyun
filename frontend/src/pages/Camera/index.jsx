import React, { useState, useEffect } from 'react';
import { getCameraList } from '@/services/monitor';
import SectionCard from '@/components/SectionCard';

// 镜头管理页面
export function CameraPage({ projectId, theme: T, onBack, cameraConfig, onSaveConfig }) {
  const [cameras, setCameras] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedCamera, setSelectedCamera] = useState(null);
  const [cameraForm, setCameraForm] = useState({
    name: '',
    ip: '',
    port: '',
    area: '',
    rtspUrl: '',
  });
  const [showAddModal, setShowAddModal] = useState(false);

  // 视频布局配置
  const [videoLayout, setVideoLayout] = useState(cameraConfig?.videoLayout || 4);
  const [cameraAssignments, setCameraAssignments] = useState(cameraConfig?.cameraAssignments || []);

  const layouts = { 1: { cols: 1, rows: 1 }, 4: { cols: 2, rows: 2 }, 8: { cols: 4, rows: 2 }, 16: { cols: 4, rows: 4 } };
  const layoutOptions = [
    { n: 1, label: '单屏' },
    { n: 4, label: '四屏' },
    { n: 8, label: '八窗口' },
    { n: 16, label: '十六窗口' },
  ];

  useEffect(() => {
    const fetchCameras = async () => {
      setLoading(true);
      try {
        const res = await getCameraList(projectId);
        if (res.code === 200 && res.data) {
          setCameras(res.data);
          // 初始化相机分配：默认按顺序分配
          if (cameraConfig?.cameraAssignments?.length !== res.data.length) {
            const defaultAssignments = res.data.slice(0, videoLayout).map((c, i) => c?.id || null);
            setCameraAssignments(defaultAssignments);
          }
        }
      } catch (e) {
        console.error('获取摄像头列表失败', e);
      } finally {
        setLoading(false);
      }
    };
    fetchCameras();
  }, [projectId]);

  // 当布局变化时，调整分配的摄像头数量
  useEffect(() => {
    setCameraAssignments(prev => {
      const newAssignments = [...prev];
      while (newAssignments.length < videoLayout) {
        newAssignments.push(null);
      }
      return newAssignments.slice(0, videoLayout);
    });
  }, [videoLayout]);

  const onlineCount = cameras.filter(c => c.online).length;
  const offlineCount = cameras.length - onlineCount;

  // 分配摄像头到某个窗口
  const assignCameraToCell = (cellIndex, cameraId) => {
    setCameraAssignments(prev => {
      const newAssignments = [...prev];
      newAssignments[cellIndex] = cameraId;
      return newAssignments;
    });
  };

  // 获取某个窗口显示的摄像头
  const getCameraForCell = (cellIndex) => {
    const camId = cameraAssignments[cellIndex];
    return cameras.find(c => c.id === camId) || null;
  };

  // 保存配置并返回
  const handleSaveAndBack = () => {
    const config = {
      videoLayout,
      cameraAssignments,
    };
    if (onSaveConfig) {
      onSaveConfig(config);
    }
    onBack();
  };

  const { cols: gridCols, rows: gridRows } = layouts[videoLayout] || layouts[4];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 16, gap: 12, overflow: 'hidden' }}>
      {/* 顶部操作栏 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button onClick={handleSaveAndBack} style={{
            padding: '6px 12px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
            background: T.cardBg, border: `1px solid ${T.borderColor}`, color: T.textSecondary,
          }}>← 返回项目概况</button>
          <span style={{ fontSize: 14, fontWeight: 600, color: T.textPrimary }}>镜头管理</span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => setShowAddModal(true)} style={{
            padding: '6px 16px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
            background: T.surface2, border: `1px solid ${T.borderColor}`, color: T.textPrimary,
          }}>+ 添加镜头</button>
          <button onClick={handleSaveAndBack} style={{
            padding: '6px 16px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
            background: T.accent, border: 'none', color: '#fff',
          }}>保存配置 →</button>
        </div>
      </div>

      {/* StatCards */}
      <div style={{ display: 'flex', gap: 10, flexShrink: 0 }}>
        <div style={{
          flex: 1, background: T.cardBg, border: `1px solid ${T.borderColor}`,
          borderRadius: T.radius, padding: '14px 16px',
        }}>
          <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>镜头总数</div>
          <div style={{ fontSize: 26, fontWeight: 700, color: T.textPrimary, lineHeight: 1 }}>{cameras.length}</div>
        </div>
        <div style={{
          flex: 1, background: T.cardBg, border: `1px solid ${T.borderColor}`,
          borderRadius: T.radius, padding: '14px 16px',
        }}>
          <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>在线数量</div>
          <div style={{ fontSize: 26, fontWeight: 700, color: T.success, lineHeight: 1 }}>{onlineCount}</div>
        </div>
        <div style={{
          flex: 1, background: T.cardBg, border: `1px solid ${T.borderColor}`,
          borderRadius: T.radius, padding: '14px 16px',
        }}>
          <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>离线数量</div>
          <div style={{ fontSize: 26, fontWeight: 700, color: T.danger, lineHeight: 1 }}>{offlineCount}</div>
        </div>
        <div style={{
          flex: 1, background: T.cardBg, border: `1px solid ${T.borderColor}`,
          borderRadius: T.radius, padding: '14px 16px',
        }}>
          <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>已配置</div>
          <div style={{ fontSize: 26, fontWeight: 700, color: T.accent, lineHeight: 1 }}>{cameras.filter(c => c.rtspUrl).length}</div>
        </div>
      </div>

      {/* 摄像头列表 + 视频布局配置 */}
      <div style={{ display: 'flex', gap: 12, flex: 1, minHeight: 0 }}>
        {/* 左侧：摄像头列表 */}
        <div style={{ width: 280, flexShrink: 0, overflow: 'auto' }}>
          <SectionCard title="摄像头列表" theme={T}>
            {loading ? (
              <div style={{ textAlign: 'center', padding: 40, color: T.textMuted }}>加载中...</div>
            ) : cameras.length === 0 ? (
              <div style={{ textAlign: 'center', padding: 40, color: T.textMuted }}>暂无镜头，请添加</div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {cameras.map(cam => (
                  <div key={cam.id} onClick={() => setSelectedCamera(cam)} style={{
                    padding: 10, borderRadius: 6, border: `1px solid ${selectedCamera?.id === cam.id ? T.accent : T.borderColor}`,
                    background: selectedCamera?.id === cam.id ? T.activeItemBg : T.surface2,
                    cursor: 'pointer',
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                      <span style={{ fontSize: 12, color: T.textPrimary, fontWeight: 500 }}>{cam.name}</span>
                      <span style={{
                        width: 8, height: 8, borderRadius: '50%',
                        background: cam.online ? T.success : T.danger,
                      }}></span>
                    </div>
                    <div style={{ fontSize: 10, color: T.textMuted }}>
                      {cam.area && <span>区域：{cam.area}</span>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </SectionCard>
        </div>

        {/* 中间：视频布局配置 */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 10, overflow: 'hidden' }}>
          <div style={{
            background: T.cardBg, border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius, padding: 12,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: T.textPrimary }}>窗口布局配置</span>
              <div style={{ display: 'flex', gap: 4 }}>
                {layoutOptions.map(opt => (
                  <button key={opt.n} onClick={() => setVideoLayout(opt.n)} style={{
                    padding: '4px 12px', fontSize: 11, borderRadius: 4, cursor: 'pointer',
                    border: `1px solid ${videoLayout === opt.n ? T.accent : T.borderColor}`,
                    background: videoLayout === opt.n ? T.accent : 'transparent',
                    color: videoLayout === opt.n ? '#fff' : T.textMuted,
                  }}>{opt.label}</button>
                ))}
              </div>
            </div>
            {/* 窗口网格 */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: `repeat(${gridCols}, 1fr)`,
              gap: 8,
              flex: 1,
            }}>
              {Array.from({ length: videoLayout }).map((_, cellIndex) => {
                const cam = getCameraForCell(cellIndex);
                return (
                  <div key={cellIndex} style={{
                    background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: 8, minHeight: 100,
                    display: 'flex', flexDirection: 'column', gap: 6,
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: 10, color: T.textMuted }}>窗口 {cellIndex + 1}</span>
                      {cam && (
                        <span style={{
                          width: 6, height: 6, borderRadius: '50%',
                          background: cam.online ? T.success : T.danger,
                        }}></span>
                      )}
                    </div>
                    {/* 摄像头选择下拉 */}
                    <select
                      value={cameraAssignments[cellIndex] || ''}
                      onChange={e => assignCameraToCell(cellIndex, e.target.value ? Number(e.target.value) : null)}
                      style={{
                        width: '100%', background: T.cardBg, border: `1px solid ${T.borderColor}`,
                        borderRadius: 4, padding: '4px 6px', fontSize: 11, color: T.textPrimary, outline: 'none',
                      }}
                    >
                      <option value="">-- 不选镜头 --</option>
                      {cameras.map(cam => (
                        <option key={cam.id} value={cam.id}>
                          {cam.name} {cam.online ? '✓' : '○'}
                        </option>
                      ))}
                    </select>
                    {/* 预览 */}
                    <div style={{
                      flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                      background: '#060e1c', borderRadius: 4, minHeight: 50,
                    }}>
                      {cam ? (
                        <div style={{ textAlign: 'center' }}>
                          <div style={{ fontSize: 10, color: '#fff' }}>{cam.name}</div>
                          <div style={{ fontSize: 9, color: cam.online ? T.success : T.danger }}>{cam.online ? '在线' : '离线'}</div>
                        </div>
                      ) : (
                        <span style={{ fontSize: 10, color: '#444' }}>未分配</span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* 右侧：摄像头详情 */}
        <div style={{ width: 320, flexShrink: 0, overflow: 'auto' }}>
          <SectionCard title={selectedCamera ? `详情 - ${selectedCamera.name}` : '摄像头详情'} theme={T}>
            {selectedCamera ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div>
                  <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>状态</div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{
                      width: 8, height: 8, borderRadius: '50%',
                      background: selectedCamera.online ? T.success : T.danger,
                    }}></span>
                    <span style={{ fontSize: 12, color: T.textPrimary }}>
                      {selectedCamera.online ? '在线' : '离线'}
                    </span>
                  </div>
                </div>
                <div>
                  <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>区域位置</div>
                  <div style={{ fontSize: 12, color: T.textPrimary }}>{selectedCamera.area || '未配置'}</div>
                </div>
                <div>
                  <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>RTSP地址</div>
                  <div style={{ fontSize: 12, color: T.textPrimary, wordBreak: 'break-all' }}>
                    {selectedCamera.rtspUrl || '未配置'}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                  <button style={{
                    flex: 1, padding: '8px 0', fontSize: 12, borderRadius: 5, cursor: 'pointer',
                    background: T.accent, border: 'none', color: '#fff',
                  }}>编辑配置</button>
                  <button style={{
                    flex: 1, padding: '8px 0', fontSize: 12, borderRadius: 5, cursor: 'pointer',
                    background: 'transparent', border: `1px solid ${T.danger}`, color: T.danger,
                  }}>删除镜头</button>
                </div>
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: T.textMuted }}>
                请从左侧列表选择要查看的镜头
              </div>
            )}
          </SectionCard>
        </div>
      </div>

      {/* 添加镜头弹窗 */}
      {showAddModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1001,
        }} onClick={() => setShowAddModal(false)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 10, padding: 24, width: 420,
          }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: 14, fontWeight: 600, color: T.textPrimary, marginBottom: 16 }}>添加镜头</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>镜头名称 *</label>
                <input placeholder="请输入镜头名称" value={cameraForm.name} onChange={e => setCameraForm({ ...cameraForm, name: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>IP地址</label>
                <input placeholder="如：192.168.1.64" value={cameraForm.ip} onChange={e => setCameraForm({ ...cameraForm, ip: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>端口</label>
                <input placeholder="如：8000" value={cameraForm.port} onChange={e => setCameraForm({ ...cameraForm, port: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>区域位置</label>
                <input placeholder="如：主入口" value={cameraForm.area} onChange={e => setCameraForm({ ...cameraForm, area: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>RTSP完整地址</label>
                <input placeholder="rtsp://user:password@ip:port/path" value={cameraForm.rtspUrl} onChange={e => setCameraForm({ ...cameraForm, rtspUrl: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowAddModal(false)} style={{
                padding: '8px 16px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
                background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textSecondary,
              }}>取消</button>
              <button onClick={() => { alert('添加镜头功能待实现'); setShowAddModal(false); }} style={{
                padding: '8px 16px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
                background: T.accent, border: 'none', color: '#fff',
              }}>保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}