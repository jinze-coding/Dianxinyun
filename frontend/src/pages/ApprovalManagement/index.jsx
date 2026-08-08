import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  SEAL_BUSINESS_CODE,
  createSystemSeal,
  getApprovalCandidates,
  getApprovalConfigs,
  getSealEntryMiniCode,
  getSystemSeals,
  rotateSealEntryCode,
  saveApprovalConfig,
  updateSealEntryCodeStatus,
  updateSystemSeal,
} from '../../services/seal';
import { hasPermission, hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import {
  enabledValue,
  isQrEntryEnabled,
  mergeSealQrSnapshot,
  normalizeQrState,
  sameSeal,
} from './qrState';
import './index.css';

const extractList = (data) => Array.isArray(data)
  ? data
  : (data?.records || data?.items || data?.list || data?.content || (data ? [data] : []));

const unwrap = (response, fallback) => {
  if (!response || Number(response.code) !== 200) throw new Error(response?.message || fallback);
  return response.data;
};

const errorText = (error, fallback) => error?.response?.data?.message || error?.message || fallback;
const userIdOf = (user) => user?.userId ?? user?.id;
const DEFAULT_COMPANY_NAME = '上海建工智慧营造有限公司';
const SEAL_TYPES = [
  ['PROJECT_SEAL', '项目印章'],
  ['COMPANY_SEAL', '公司印章'],
  ['CONTRACT_SEAL', '合同印章'],
  ['FINANCE_SEAL', '财务印章'],
  ['OTHER', '其他印章'],
];

function imageSource(data) {
  const value = data?.imageBase64 || data?.miniCodeBase64 || data?.qrCodeBase64 || data?.dataUrl || data?.image;
  if (!value) return '';
  return String(value).startsWith('data:') ? String(value) : `data:image/png;base64,${value}`;
}

function Modal({ title, subtitle, children, footer, onClose, wide = false }) {
  return (
    <div className="approval-modal-mask" onMouseDown={onClose}>
      <section className={`approval-modal ${wide ? 'wide' : ''}`} onMouseDown={(event) => event.stopPropagation()}>
        <header><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button className="icon" onClick={onClose}>×</button></header>
        <div className="approval-modal-body">{children}</div>
        {footer && <footer>{footer}</footer>}
      </section>
    </div>
  );
}

function SealEditor({ value, projectId, busy, onClose, onSave }) {
  const [form, setForm] = useState({
    id: value?.id || null,
    projectId: value?.projectId || projectId,
    sealCode: value?.sealCode || '',
    sealName: value?.sealName || '',
    sealType: value?.sealType || 'PROJECT_SEAL',
    companyName: value?.companyName || DEFAULT_COMPANY_NAME,
    enabled: value ? enabledValue(value.enabled ?? value.status) : true,
    sortOrder: Number(value?.sortOrder || 0),
    version: value?.version,
  });
  const sealCodeValid = Boolean(form.id) || /^[A-Z0-9_-]{2,40}$/.test(form.sealCode);
  return (
    <Modal title={form.id ? '编辑印章' : '新增印章'} subtitle="一枚实体印章对应一条独立审批配置和一个长期扫码入口。" onClose={busy ? undefined : onClose} footer={<><button onClick={onClose} disabled={busy}>取消</button><button className="primary" disabled={busy || !form.sealName.trim() || !sealCodeValid} onClick={() => onSave({ ...form, sealCode: form.sealCode.trim(), sealName: form.sealName.trim() })}>{busy ? '保存中…' : '保存印章'}</button></>}>
      <div className="approval-form-grid">
        <label><span>印章编码 *</span><input autoFocus value={form.sealCode} disabled={Boolean(form.id)} maxLength={40} onChange={(event) => setForm({ ...form, sealCode: event.target.value.toUpperCase().replace(/[^A-Z0-9_-]/g, '') })} placeholder="例如：PROJECT_SEAL" /><small>全局唯一，2–40 位大写字母、数字、_或-，创建后不可修改。</small></label>
        <label><span>印章名称 *</span><input value={form.sealName} maxLength={100} onChange={(event) => setForm({ ...form, sealName: event.target.value })} placeholder="例如：项目部公章" /></label>
        <label><span>印章类型</span><select value={form.sealType} onChange={(event) => setForm({ ...form, sealType: event.target.value })}>{SEAL_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label className="full"><span>所属公司</span><input value={form.companyName} maxLength={200} onChange={(event) => setForm({ ...form, companyName: event.target.value })} placeholder={DEFAULT_COMPANY_NAME} /></label>
        <label><span>显示顺序</span><input type="number" min="0" max="9999" value={form.sortOrder} onChange={(event) => setForm({ ...form, sortOrder: Number(event.target.value || 0) })} /></label>
        <label className="approval-check"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span>启用印章</span></label>
      </div>
    </Modal>
  );
}

function UserPicker({ title, description, users, selectedIds, onChange, disabled = false }) {
  const selected = useMemo(() => new Set((selectedIds || []).map(Number)), [selectedIds]);
  return (
    <section className="approval-user-picker">
      <header><div><strong>{title}</strong><span>{description}</span></div><b>已选 {selected.size} 人</b></header>
      <div>{users.map((user) => {
        const id = Number(userIdOf(user));
        const checked = selected.has(id);
        return <label key={id} className={checked ? 'selected' : ''}><input type="checkbox" checked={checked} disabled={disabled} onChange={() => onChange(checked ? selectedIds.filter((value) => Number(value) !== id) : [...selectedIds, id])} /><div><strong>{user.displayName || user.realName || user.username || `用户 ${id}`}</strong><span>{user.username || '-'} · {user.phone || '-'}</span></div>{user.activeProjectMember === false && <em>非有效成员</em>}</label>;
      })}{!users.length && <div className="approval-empty-inline">没有匹配的有效项目成员</div>}</div>
    </section>
  );
}

function ConfigEditor({ seal, config, candidates, candidatesLoading, busy, readOnly, onSearch, onClose, onSave }) {
  const [form, setForm] = useState({
    id: config?.id,
    businessCode: SEAL_BUSINESS_CODE,
    projectId: seal.projectId,
    sealId: seal.id,
    sealName: seal.sealName,
    approvalMode: config?.approvalMode || 'ANY_ONE',
    enabled: config ? enabledValue(config.enabled) : true,
    approverUserIds: config?.approverUserIds || (config?.approverUsers || []).map(userIdOf).filter(Boolean),
    defaultCcUserIds: config?.defaultCcUserIds || (config?.defaultCcUsers || []).map(userIdOf).filter(Boolean),
    version: config?.version,
  });
  const [keyword, setKeyword] = useState('');
  const canSave = form.approverUserIds.length > 0;
  return (
    <Modal title={`${readOnly ? '查看' : '审批'}配置 · ${seal.sealName}`} subtitle="审批候选人与抄送人均直接选择用户，不从角色自动推导；节点内任意一人审批即完成。" wide onClose={busy ? undefined : onClose} footer={readOnly ? <button onClick={onClose}>关闭</button> : <><button onClick={onClose} disabled={busy}>取消</button><button className="primary" disabled={busy || !canSave} onClick={() => onSave(form)}>{busy ? '保存中…' : '保存审批配置'}</button></>}>
      <div className="approval-config-summary"><div><span>业务类型</span><strong>用印申请</strong></div><div><span>审批方式</span><strong>节点内任意一人</strong></div><label><input type="checkbox" checked={form.enabled} disabled={readOnly} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span>启用该印章审批流程</span></label></div>
      {!readOnly && <div className="approval-candidate-search"><input value={keyword} placeholder="搜索姓名、账号或手机号" onChange={(event) => setKeyword(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && onSearch(keyword)} /><button onClick={() => onSearch(keyword)}>搜索候选用户</button>{candidatesLoading && <span>加载中…</span>}</div>}
      <div className="approval-picker-grid">
        <UserPicker title="候选审批人 *" description="提交申请时为这些用户生成当前印章审批任务" users={candidates} selectedIds={form.approverUserIds} disabled={readOnly} onChange={(ids) => setForm({ ...form, approverUserIds: ids })} />
        <UserPicker title="默认抄送人" description="只接收通知并只读查看，不获得审批或管理权限" users={candidates} selectedIds={form.defaultCcUserIds} disabled={readOnly} onChange={(ids) => setForm({ ...form, defaultCcUserIds: ids })} />
      </div>
      {!readOnly && !canSave && <div className="approval-form-error">至少选择一位候选审批人。</div>}
    </Modal>
  );
}

function QrDialog({ seal, code, image, loading, busy, readOnly, onClose, onReload, onRotate, onStatus }) {
  const active = isQrEntryEnabled(code, seal);
  const download = () => {
    if (!image) return;
    const anchor = document.createElement('a');
    anchor.href = image;
    anchor.download = `${seal.sealName}-用印申请码.png`;
    anchor.click();
  };
  return (
    <Modal title={`用印申请码 · ${seal.sealName}`} subtitle="二维码只绑定当前项目和当前印章；申请人扫码后不能改选其他印章。" onClose={busy ? undefined : onClose} footer={<><button onClick={onClose}>关闭</button><button onClick={download} disabled={!image}>下载二维码</button><button onClick={onReload} disabled={loading}>刷新</button></>}>
      <div className="approval-qr">
        {loading ? <div className="approval-qr-placeholder">二维码加载中…</div> : image ? <img src={image} alt={`${seal.sealName}用印申请小程序码`} /> : <div className="approval-qr-placeholder"><strong>{active ? '尚未取得正式小程序码' : '扫码入口已停用'}</strong><span>{active ? (code?.scene ? `场景码：${code.scene}` : '请确认正式微信配置后重试') : '启用后可立即重新生成并下载二维码'}</span></div>}
        <div className="approval-qr-meta"><span>印章状态：{enabledValue(seal.enabled) ? '启用' : '停用'}</span><span>扫码入口：{active ? '启用' : '停用'}</span>{code?.scene && <code>{code.scene}</code>}</div>
        {!readOnly && <div className="approval-qr-actions"><button className={active ? 'danger' : 'primary'} disabled={busy} onClick={() => onStatus(!active)}>{active ? '停用扫码入口' : '启用扫码入口'}</button><button className="danger" disabled={busy} onClick={onRotate}>轮换二维码</button></div>}
        <p>轮换后旧二维码立即失效，需要重新下载和张贴。停用扫码入口不会删除历史申请或审批记录。</p>
      </div>
    </Modal>
  );
}

export default function ApprovalManagementPage({ currentUser, initialProjectId, projectList = [] }) {
  const visibleProjects = useMemo(() => isPlatformAdmin(currentUser) ? projectList : projectList.filter((project) => hasProjectPermission(currentUser, project.id, 'system.approval.view', 'system.approval.manage')), [currentUser, projectList]);
  const [projectId, setProjectId] = useState(initialProjectId || visibleProjects[0]?.id || '');
  const canManage = isPlatformAdmin(currentUser)
    || hasPermission(currentUser, 'system.approval.manage')
    || hasProjectPermission(currentUser, projectId, 'system.approval.manage');
  const [seals, setSeals] = useState([]);
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [sealEditor, setSealEditor] = useState(undefined);
  const [configEditor, setConfigEditor] = useState(null);
  const [candidates, setCandidates] = useState([]);
  const [candidatesLoading, setCandidatesLoading] = useState(false);
  const [qrState, setQrState] = useState(null);
  const [qrCode, setQrCode] = useState(null);
  const [qrImage, setQrImage] = useState('');
  const [qrLoading, setQrLoading] = useState(false);
  const qrSequenceRef = useRef(0);
  const qrStateRef = useRef(null);

  useEffect(() => {
    if (!visibleProjects.some((item) => Number(item.id) === Number(projectId))) setProjectId(visibleProjects[0]?.id || '');
  }, [projectId, visibleProjects]);

  const loadData = useCallback(async () => {
    if (!projectId) {
      setSeals([]);
      setConfigs([]);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const [sealResponse, configResponse] = await Promise.all([
        getSystemSeals({ projectId }),
        getApprovalConfigs({ businessCode: SEAL_BUSINESS_CODE, projectId }),
      ]);
      setSeals(extractList(unwrap(sealResponse, '印章加载失败')));
      setConfigs(extractList(unwrap(configResponse, '审批配置加载失败')));
    } catch (loadError) {
      setSeals([]);
      setConfigs([]);
      setError(errorText(loadError, '用印审批数据加载失败'));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => { loadData(); }, [loadData]);

  const configForSeal = useCallback((seal) => configs.find((config) => Number(config.sealId) === Number(seal.id)
    || (!config.sealId && String(config.sealName || '') === String(seal.sealName || ''))), [configs]);

  const loadCandidates = useCallback(async (keyword = '') => {
    if (!projectId) return;
    setCandidatesLoading(true);
    try {
      const response = await getApprovalCandidates({ projectId, keyword: keyword.trim() || undefined });
      setCandidates(extractList(unwrap(response, '候选用户加载失败')).filter((user) => user.activeProjectMember !== false));
    } catch (loadError) {
      setCandidates([]);
      setError(errorText(loadError, '候选用户加载失败'));
    } finally {
      setCandidatesLoading(false);
    }
  }, [projectId]);

  const openConfig = async (seal) => {
    const config = configForSeal(seal);
    setConfigEditor({ seal: { ...seal, projectId: seal.projectId || Number(projectId) }, config });
    if (canManage) {
      await loadCandidates('');
    } else {
      const selectedUsers = [...(config?.approvers || config?.approverUsers || []), ...(config?.defaultCcUsers || [])];
      setCandidates([...new Map(selectedUsers.map((user) => [Number(userIdOf(user)), user])).values()]);
    }
  };

  const saveSeal = async (form) => {
    if (!canManage) return;
    setBusy(true);
    setError('');
    try {
      const response = form.id
        ? await updateSystemSeal(form.id, { sealName: form.sealName, sealType: form.sealType, companyName: form.companyName || DEFAULT_COMPANY_NAME, enabled: form.enabled, sortOrder: form.sortOrder, version: form.version })
        : await createSystemSeal({ projectId: Number(projectId), sealCode: form.sealCode, sealName: form.sealName, sealType: form.sealType, companyName: form.companyName || DEFAULT_COMPANY_NAME, enabled: form.enabled, sortOrder: form.sortOrder });
      unwrap(response, '印章保存失败');
      setSealEditor(undefined);
      setNotice(form.id ? '印章信息已更新' : '印章已创建，请继续配置审批人和二维码');
      await loadData();
    } catch (saveError) {
      setError(errorText(saveError, '印章保存失败'));
    } finally {
      setBusy(false);
    }
  };

  const toggleSeal = async (seal) => {
    if (!canManage) return;
    const nextEnabled = !enabledValue(seal.enabled ?? seal.status);
    if (!window.confirm(`${nextEnabled ? '启用' : '停用'}印章“${seal.sealName}”？历史申请不受影响。`)) return;
    setBusy(true);
    try {
      const response = await updateSystemSeal(seal.id, { sealName: seal.sealName, sealType: seal.sealType, companyName: seal.companyName, enabled: nextEnabled, sortOrder: seal.sortOrder, version: seal.version });
      unwrap(response, '印章状态更新失败');
      setNotice(`印章已${nextEnabled ? '启用' : '停用'}`);
      await loadData();
    } catch (saveError) {
      setError(errorText(saveError, '印章状态更新失败'));
    } finally {
      setBusy(false);
    }
  };

  const saveConfig = async (form) => {
    if (!canManage) return;
    setBusy(true);
    setError('');
    try {
      const response = await saveApprovalConfig({
        businessCode: SEAL_BUSINESS_CODE,
        projectId: Number(form.projectId),
        sealId: Number(form.sealId),
        enabled: Boolean(form.enabled),
        approverUserIds: form.approverUserIds.map(Number),
        defaultCcUserIds: form.defaultCcUserIds.map(Number),
      });
      unwrap(response, '审批配置保存失败');
      setConfigEditor(null);
      setNotice('审批配置已保存，新提交申请将按此配置生成用户待办');
      await loadData();
    } catch (saveError) {
      setError(errorText(saveError, '审批配置保存失败'));
    } finally {
      setBusy(false);
    }
  };

  const commitQrState = useCallback((sealId, nextState) => {
    if (!sameSeal(qrStateRef.current, sealId)) return false;
    qrStateRef.current = nextState;
    setQrState((current) => (sameSeal(current, sealId) ? nextState : current));
    setQrCode(nextState);
    return true;
  }, []);

  const updateSealQrSnapshot = useCallback((nextState) => {
    setSeals((current) => current.map((seal) => mergeSealQrSnapshot(seal, nextState)));
  }, []);

  const loadQr = useCallback(async (seal) => {
    const sealId = Number(seal?.id ?? seal?.sealId);
    if (!Number.isFinite(sealId) || !sameSeal(qrStateRef.current, sealId)) return;
    const baseline = normalizeQrState(qrStateRef.current);
    const sequence = ++qrSequenceRef.current;
    commitQrState(sealId, baseline);
    setQrImage('');
    setQrLoading(false);
    setError('');
    if (!baseline.active) return;
    setQrLoading(true);
    try {
      const imageData = unwrap(await getSealEntryMiniCode(sealId), '二维码加载失败');
      if (sequence !== qrSequenceRef.current || !sameSeal(qrStateRef.current, sealId)) return;
      const nextState = normalizeQrState(qrStateRef.current, imageData);
      if (!commitQrState(sealId, nextState)) return;
      setQrImage(imageSource(imageData));
    } catch (loadError) {
      if (sequence !== qrSequenceRef.current || !sameSeal(qrStateRef.current, sealId)) return;
      setQrImage('');
      setError(errorText(loadError, '二维码加载失败'));
    } finally {
      if (sequence === qrSequenceRef.current && sameSeal(qrStateRef.current, sealId)) setQrLoading(false);
    }
  }, [commitQrState]);

  const openQr = async (seal) => {
    const nextState = normalizeQrState(seal);
    qrStateRef.current = nextState;
    setQrState(nextState);
    setQrCode(nextState);
    setQrImage('');
    setQrLoading(false);
    await loadQr(nextState);
  };

  const closeQr = () => {
    qrSequenceRef.current += 1;
    qrStateRef.current = null;
    setQrState(null);
    setQrCode(null);
    setQrImage('');
    setQrLoading(false);
  };

  const rotateQr = async () => {
    const target = qrStateRef.current;
    const sealId = Number(target?.id ?? target?.sealId);
    if (!canManage || !Number.isFinite(sealId)) return;
    if (!window.confirm(`确认轮换“${target.sealName}”用印码？旧二维码将立即失效。`)) return;
    const sequence = ++qrSequenceRef.current;
    setQrLoading(false);
    setBusy(true);
    setError('');
    try {
      const entry = unwrap(await rotateSealEntryCode(sealId), '二维码轮换失败');
      if (sequence !== qrSequenceRef.current || !sameSeal(qrStateRef.current, sealId)) return;
      const nextState = normalizeQrState(target, entry);
      if (!commitQrState(sealId, nextState)) return;
      setQrImage('');
      setQrLoading(false);
      updateSealQrSnapshot(nextState);
      setNotice('二维码已轮换，请重新下载并替换现场旧码');
      if (nextState.active) await loadQr(nextState);
    } catch (saveError) {
      if (sequence === qrSequenceRef.current && sameSeal(qrStateRef.current, sealId)) {
        setError(errorText(saveError, '二维码轮换失败'));
      }
    } finally {
      setBusy(false);
    }
  };

  const updateQrStatus = async (enabled) => {
    const target = qrStateRef.current;
    const sealId = Number(target?.id ?? target?.sealId);
    if (!canManage || !Number.isFinite(sealId)) return;
    const sequence = ++qrSequenceRef.current;
    setQrLoading(false);
    setBusy(true);
    setError('');
    try {
      const entry = unwrap(await updateSealEntryCodeStatus(sealId, enabled, enabled ? '管理员启用用印扫码入口' : '管理员停用用印扫码入口'), '二维码状态更新失败');
      if (sequence !== qrSequenceRef.current || !sameSeal(qrStateRef.current, sealId)) return;
      const nextState = normalizeQrState(target, entry, enabled);
      if (!commitQrState(sealId, nextState)) return;
      setQrImage('');
      setQrLoading(false);
      updateSealQrSnapshot(nextState);
      setNotice(`扫码入口已${enabled ? '启用' : '停用'}`);
      if (nextState.active) await loadQr(nextState);
    } catch (saveError) {
      if (sequence === qrSequenceRef.current && sameSeal(qrStateRef.current, sealId)) {
        setError(errorText(saveError, '二维码状态更新失败'));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="approval-management">
      <section className="approval-page-bar"><div><h2>用印审批</h2><p>按项目和实体印章直接配置审批用户、默认抄送与独立二维码；角色不会自动成为审批人。</p></div><div className="approval-page-actions"><select value={projectId} onChange={(event) => setProjectId(event.target.value)}><option value="">请选择项目</option>{visibleProjects.map((project) => <option key={project.id} value={project.id}>{project.projectName || project.shortName}</option>)}</select><button onClick={loadData} disabled={loading}>刷新</button>{canManage && <button className="primary" disabled={!projectId} onClick={() => setSealEditor(null)}>新增印章</button>}</div></section>
      <div className="approval-guides"><div><strong>1. 建立印章</strong><span>每枚实体章独立维护和启停</span></div><div><strong>2. 选择用户</strong><span>候选审批人与默认抄送均直接选人</span></div><div><strong>3. 张贴二维码</strong><span>每枚章独立生成、轮换和停用</span></div><div><strong>4. 审批转办</strong><span>管理员在申请详情填写原因后转办</span></div></div>
      {notice && <div className="approval-notice" onClick={() => setNotice('')}>{notice}</div>}
      {error && <div className="approval-error"><span>{error}</span><button onClick={() => setError('')}>×</button></div>}
      <section className="approval-table-wrap"><table><thead><tr><th>印章</th><th>审批方式</th><th>候选审批人</th><th>默认抄送</th><th>审批配置</th><th>扫码入口</th><th>印章状态</th><th>操作</th></tr></thead><tbody>
        {seals.map((seal) => {
          const config = configForSeal(seal);
          const approvers = config?.approvers || config?.approverUsers || [];
          const ccUsers = config?.defaultCcUsers || [];
          const configEnabled = config && enabledValue(config.enabled);
          return <tr key={seal.id}><td><strong>{seal.sealName}</strong><small>{seal.sealCode || `Seal ID: ${seal.id}`} · {SEAL_TYPES.find(([value]) => value === seal.sealType)?.[1] || seal.sealType || '项目印章'}</small><small>{seal.companyName || DEFAULT_COMPANY_NAME}</small></td><td>{config?.approvalMode === 'ANY_ONE' ? '任意一人' : config?.approvalMode || '-'}</td><td><div className="approval-user-tags">{approvers.map((user) => <span key={userIdOf(user)}>{user.displayName || user.realName || user.username}</span>)}{!approvers.length && <em>未配置</em>}</div></td><td><div className="approval-user-tags">{ccUsers.map((user) => <span key={userIdOf(user)}>{user.displayName || user.realName || user.username}</span>)}{!ccUsers.length && <em>无默认抄送</em>}</div></td><td><span className={`approval-state ${configEnabled ? 'enabled' : 'disabled'}`}>{config ? (configEnabled ? '已启用' : '已停用') : '待配置'}</span></td><td><button onClick={() => openQr(seal)}>二维码管理</button></td><td><span className={`approval-state ${enabledValue(seal.enabled ?? seal.status) ? 'enabled' : 'disabled'}`}>{enabledValue(seal.enabled ?? seal.status) ? '启用' : '停用'}</span></td><td><div className="approval-row-actions"><button className="primary" onClick={() => openConfig(seal)}>{canManage ? '审批配置' : '查看配置'}</button>{canManage && <button onClick={() => setSealEditor(seal)}>编辑</button>}{canManage && <button className={enabledValue(seal.enabled ?? seal.status) ? 'danger' : ''} disabled={busy} onClick={() => toggleSeal(seal)}>{enabledValue(seal.enabled ?? seal.status) ? '停用' : '启用'}</button>}</div></td></tr>;
        })}
        {!loading && !seals.length && <tr><td colSpan="8" className="approval-empty">{projectId ? (canManage ? '当前项目尚未建立印章，请先新增印章。' : '当前项目暂无可查看的印章配置。') : '请选择项目。'}</td></tr>}
      </tbody></table>{loading && <div className="approval-loading">正在加载审批配置…</div>}</section>
      <div className="approval-boundary"><strong>{canManage ? '审批权限边界' : '只读查看'}</strong><span>{canManage ? '审批任务只分配给管理员直接选择的候选用户；默认抄送人只能查看和接收通知。转办只改变当前任务处理人，不增加任何角色、菜单或项目权限。' : '当前账号可查看印章、审批人、默认抄送人和二维码，不可新增、修改、启停或轮换。'}</span></div>
      {canManage && sealEditor !== undefined && <SealEditor value={sealEditor} projectId={Number(projectId)} busy={busy} onClose={() => setSealEditor(undefined)} onSave={saveSeal} />}
      {configEditor && <ConfigEditor seal={configEditor.seal} config={configEditor.config} candidates={candidates} candidatesLoading={candidatesLoading} busy={busy} readOnly={!canManage} onSearch={loadCandidates} onClose={() => setConfigEditor(null)} onSave={saveConfig} />}
      {qrState && <QrDialog seal={qrState} code={qrCode} image={qrImage} loading={qrLoading} busy={busy} readOnly={!canManage} onClose={closeQr} onReload={() => loadQr(qrStateRef.current)} onRotate={rotateQr} onStatus={updateQrStatus} />}
    </div>
  );
}
