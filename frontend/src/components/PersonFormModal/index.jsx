import React from 'react';

const ID_CARD_REGEX = /^\d{17}[\dXx]$/;
const PHONE_REGEX = /^1[3-9]\d{9}$/;

function PersonFormModal({ title, form, setForm, onCancel, onConfirm, confirmLabel, theme: T }) {
  const validateForm = (f) => {
    if (!f.name?.trim()) return '请填写姓名';
    if (!ID_CARD_REGEX.test(f.idcard)) return '身份证号格式不正确（18位数字，最后一位可为X）';
    if (!PHONE_REGEX.test(f.phone)) return '手机号格式不正确';
    return null;
  };

  const handleConfirm = () => {
    const err = validateForm(form);
    if (err) { alert(err); return; }
    onConfirm();
  };

  const inputStyle = {
    width: '100%',
    background: T.surface2,
    border: `1px solid ${T.borderColor}`,
    borderRadius: 6,
    padding: '8px 10px',
    fontSize: 13,
    color: T.textPrimary,
    outline: 'none',
    boxSizing: 'border-box',
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(0,0,0,0.7)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 1000,
    }} onClick={onCancel}>
      <div style={{
        background: T.modalBg,
        border: `1px solid ${T.borderColor}`,
        borderRadius: 12,
        padding: 24,
        width: 480,
        maxWidth: '90vw',
      }} onClick={e => e.stopPropagation()}>
        <div style={{ fontSize: 15, fontWeight: 600, color: T.textPrimary, marginBottom: 20 }}>{title}</div>

        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>姓名 *</div>
          <input
            placeholder="请输入姓名"
            value={form.name || ''}
            onChange={e => setForm({ ...form, name: e.target.value })}
            style={inputStyle}
          />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 14 }}>
          <div>
            <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>性别</div>
            <select
              value={form.gender || '男'}
              onChange={e => setForm({ ...form, gender: e.target.value })}
              style={inputStyle}
            >
              <option>男</option>
              <option>女</option>
            </select>
          </div>
          <div>
            <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>工种</div>
            <select
              value={form.role || '普工'}
              onChange={e => setForm({ ...form, role: e.target.value })}
              style={inputStyle}
            >
              <option>普工</option>
              <option>特种工</option>
              <option>管理人员</option>
            </select>
          </div>
        </div>

        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>身份证号 *</div>
          <input
            placeholder="请输入18位身份证号"
            value={form.idcard || ''}
            onChange={e => setForm({ ...form, idcard: e.target.value })}
            style={inputStyle}
          />
        </div>

        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>手机号 *</div>
          <input
            placeholder="请输入11位手机号"
            value={form.phone || ''}
            onChange={e => setForm({ ...form, phone: e.target.value })}
            style={inputStyle}
          />
        </div>

        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>所属单位</div>
          <input
            placeholder="请输入所属单位"
            value={form.unit || ''}
            onChange={e => setForm({ ...form, unit: e.target.value })}
            style={inputStyle}
          />
        </div>

        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>备注</div>
          <input
            placeholder="可选"
            value={form.note || ''}
            onChange={e => setForm({ ...form, note: e.target.value })}
            style={inputStyle}
          />
        </div>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <button onClick={onCancel} style={{
            padding: '8px 20px',
            borderRadius: 6,
            border: `1px solid ${T.borderColor}`,
            background: 'none',
            color: T.textSecondary,
            cursor: 'pointer',
            fontSize: 13,
          }}>取消</button>
          <button onClick={handleConfirm} style={{
            padding: '8px 20px',
            borderRadius: 6,
            border: 'none',
            background: T.accent,
            color: '#fff',
            cursor: 'pointer',
            fontSize: 13,
          }}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}

export default PersonFormModal;