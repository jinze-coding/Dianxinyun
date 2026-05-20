import React, { useState } from 'react';
import { DATA_BY_PROJECT } from '@/constants/mockData';
import StatCard from '@/components/StatCard';

// 人员与安全页面
export function PersonnelPage({ projectId, theme: T, compactMode }) {
  const data = DATA_BY_PROJECT[projectId];
  const personnel = data.personnel;
  const trainings = data.trainings;
  const files = data.files;

  const [activeTab, setActiveTab] = useState('personnel');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  const filteredPersonnel = personnel.filter(p => {
    const matchKeyword = p.name.includes(searchKeyword) || p.unit.includes(searchKeyword);
    const matchStatus = statusFilter === 'all' || p.status === statusFilter;
    return matchKeyword && matchStatus;
  });

  const getStatusColor = status => {
    if (status === '已教育') return T.success;
    if (status === '待教育') return T.warning;
    if (status === '已离场') return T.textMuted;
    return T.textSecondary;
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 12, gap: 12 }}>
      {/* 顶部统计 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
        <StatCard label="在场总人数" value={personnel.length} sub="人" color={T.accent} theme={T} />
        <StatCard
          label="待教育人数"
          value={personnel.filter(p => p.status === '待教育').length}
          sub="人"
          color={T.warning}
          theme={T}
        />
        <StatCard label="培训批次" value={trainings.length} sub="批次" color={T.accent2} theme={T} />
        <StatCard label="今日上传" value={files.length} sub="份" color={T.success} theme={T} />
      </div>

      {/* 主内容区 */}
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 320px', gap: 12, overflow: 'hidden' }}>
        {/* 左侧列表 */}
        <div
          style={{
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          {/* Tab切换 */}
          <div style={{ display: 'flex', borderBottom: `1px solid ${T.borderColor}`, padding: '0 16px' }}>
            {[['人员管理', 'personnel'], ['安全教育', 'training']].map(([label, v]) => (
              <button
                key={v}
                onClick={() => setActiveTab(v)}
                style={{
                  padding: '12px 16px',
                  border: 'none',
                  background: 'none',
                  cursor: 'pointer',
                  fontSize: 13,
                  color: activeTab === v ? T.accent : T.textSecondary,
                  borderBottom: `2px solid ${activeTab === v ? T.accent : 'transparent'}`,
                  marginBottom: -1,
                }}
              >
                {label}
              </button>
            ))}
          </div>

          {/* 搜索筛选 */}
          <div
            style={{
              padding: '12px 16px',
              display: 'flex',
              gap: 10,
              borderBottom: `1px solid ${T.borderColor}`,
            }}
          >
            <input
              type="text"
              placeholder="搜索姓名、单位..."
              value={searchKeyword}
              onChange={e => setSearchKeyword(e.target.value)}
              style={{
                flex: 1,
                padding: '6px 10px',
                borderRadius: 6,
                border: `1px solid ${T.borderColor}`,
                background: T.surface2,
                color: T.textPrimary,
                fontSize: 12,
              }}
            />
            <select
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
              style={{
                padding: '6px 10px',
                borderRadius: 6,
                border: `1px solid ${T.borderColor}`,
                background: T.surface2,
                color: T.textPrimary,
                fontSize: 12,
              }}
            >
              <option value="all">全部状态</option>
              <option value="已教育">已教育</option>
              <option value="待教育">待教育</option>
              <option value="已离场">已离场</option>
            </select>
          </div>

          {/* 列表内容 */}
          <div style={{ flex: 1, overflow: 'auto', padding: '8px 16px' }}>
            {activeTab === 'personnel' ? (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ borderBottom: `1px solid ${T.borderColor}` }}>
                    {['姓名', '性别', '身份证', '单位', '工种', '入场时间', '状态'].map(h => (
                      <th
                        key={h}
                        style={{
                          padding: '8px 4px',
                          fontSize: 11,
                          color: T.textMuted,
                          fontWeight: 400,
                          textAlign: 'left',
                        }}
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filteredPersonnel.map(p => (
                    <tr key={p.id} style={{ borderBottom: `1px solid ${T.borderColor}` }}>
                      <td style={{ padding: '8px 4px', fontSize: 12, color: T.textPrimary }}>{p.name}</td>
                      <td style={{ padding: '8px 4px', fontSize: 12, color: T.textSecondary }}>{p.gender}</td>
                      <td style={{ padding: '8px 4px', fontSize: 12, color: T.textMuted }}>{p.idcard}</td>
                      <td style={{ padding: '8px 4px', fontSize: 12, color: T.textSecondary }}>{p.unit}</td>
                      <td style={{ padding: '8px 4px', fontSize: 12, color: T.textSecondary }}>{p.role}</td>
                      <td style={{ padding: '8px 4px', fontSize: 12, color: T.textMuted }}>{p.entryTime}</td>
                      <td style={{ padding: '8px 4px' }}>
                        <span
                          style={{
                            fontSize: 10,
                            padding: '2px 6px',
                            borderRadius: 3,
                            background: `${getStatusColor(p.status)}22`,
                            color: getStatusColor(p.status),
                          }}
                        >
                          {p.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {trainings.map(t => (
                  <div
                    key={t.id}
                    style={{ padding: 12, borderRadius: 6, border: `1px solid ${T.borderColor}` }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ fontSize: 12, color: T.textPrimary, fontWeight: 500 }}>{t.name}</span>
                      <span
                        style={{
                          fontSize: 10,
                          padding: '2px 6px',
                          borderRadius: 3,
                          background:
                            t.status === '已完成'
                              ? `${T.success}22`
                              : t.status === '进行中'
                              ? `${T.warning}22`
                              : `${T.textMuted}22`,
                          color:
                            t.status === '已完成'
                              ? T.success
                              : t.status === '进行中'
                              ? T.warning
                              : T.textMuted,
                        }}
                      >
                        {t.status}
                      </span>
                    </div>
                    <div style={{ fontSize: 11, color: T.textMuted }}>
                      {t.eduType} | {t.time} | {t.place} | 讲师: {t.trainer}
                    </div>
                    <div style={{ fontSize: 11, color: T.textSecondary, marginTop: 4 }}>
                      参与人员: {t.personIds.length}人
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* 右侧：上传与记录 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div
            style={{
              background: T.cardBg,
              border: `1px solid ${T.borderColor}`,
              borderRadius: T.radius,
              padding: 16,
            }}
          >
            <div style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary, marginBottom: 12 }}>
              上传培训资料
            </div>
            <div
              style={{
                border: `2px dashed ${T.borderColor}`,
                borderRadius: 8,
                padding: '24px 16px',
                textAlign: 'center',
                marginBottom: 12,
              }}
            >
              <svg
                width="32"
                height="32"
                viewBox="0 0 24 24"
                fill="none"
                stroke={T.textMuted}
                strokeWidth="1.5"
                style={{ margin: '0 auto 8px' }}
              >
                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12" />
              </svg>
              <div style={{ fontSize: 11, color: T.textMuted }}>点击或拖拽文件到此区域</div>
            </div>
            <button
              style={{
                width: '100%',
                padding: '8px',
                borderRadius: 6,
                border: 'none',
                background: T.accent,
                color: '#fff',
                fontSize: 12,
                cursor: 'pointer',
              }}
            >
              选择文件
            </button>
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
            <div style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary, marginBottom: 12 }}>
              最近上传
            </div>
            {files.map(f => (
              <div key={f.id} style={{ padding: '8px 0', borderBottom: `1px solid ${T.borderColor}` }}>
                <div style={{ fontSize: 11, color: T.textPrimary }}>{f.name}</div>
                <div style={{ fontSize: 10, color: T.textMuted, marginTop: 2 }}>
                  {f.uploader} · {f.time}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default PersonnelPage;
