import { useCallback, useEffect, useRef, useState } from 'react';
import { confirmUserImport, downloadUserImportCredentials, downloadUserImportTemplate, getUserImport, listUserImports, previewUserImport, regenerateTemporaryPassword, downloadTemporaryPassword } from '../../services/userImports';
import './userImport.css';
import { temporaryPasswordError } from './temporaryPassword';

const LABELS = { PREVIEW: '待确认', INVALID: '校验未通过', QUEUED: '排队中', PROCESSING: '正在准备账号', SUCCEEDED: '导入完成', FAILED: '导入失败', CONFLICT: '需要重新校验', NEW: '拟新增', SKIPPED: '已跳过', ERROR: '需修正', DUPLICATE: '合并授权', CREATED: '已创建' };
const activeJob = (batch) => ['QUEUED', 'PROCESSING'].includes(batch?.status);
const date = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '—';

export default function UserImportDialog({ ModalFrame, Pagination, onClose }) {
  const [batch, setBatch] = useState(null);
  const [history, setHistory] = useState({ records: [], total: 0 });
  const [historyPage, setHistoryPage] = useState(1);
  const [rowPage, setRowPage] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [onlyErrors, setOnlyErrors] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const fileInput = useRef(null);
  const confirmKeys = useRef(new Map());
  const sequence = useRef(0);
  const loadHistory = useCallback(async () => {
    const result = await listUserImports({ pageNo: historyPage, pageSize: 10 }); setHistory(result);
  }, [historyPage]);
  useEffect(() => { let active = true; listUserImports({ pageNo: historyPage, pageSize: 10 }).then((result) => { if (active) setHistory(result); }).catch((e) => { if (active) setError(e.message); }); return () => { active = false; }; }, [historyPage]);
  useEffect(() => () => { sequence.current += 1; }, []);
  useEffect(() => {
    if (!activeJob(batch)) return undefined;
    let active = true; let fetching = false;
    const poll = async () => {
      if (fetching || document.visibilityState === 'hidden') return;
      fetching = true;
      try {
        const next = await getUserImport(batch.id, false);
        if (!active) return;
        if (!activeJob(next)) {
          const full = await getUserImport(batch.id);
          if (active) { setBatch(full); void loadHistory().catch(() => {}); }
        } else setBatch((current) => ({ ...current, ...next, rows: current.rows }));
        if (active) setError('');
      } catch (failure) { if (active) setError(failure.message || '连接中断，恢复后自动重试'); }
      finally { fetching = false; }
    };
    const timer = window.setInterval(poll, 2000); void poll();
    document.addEventListener('visibilitychange', poll); window.addEventListener('online', poll);
    return () => { active = false; window.clearInterval(timer); document.removeEventListener('visibilitychange', poll); window.removeEventListener('online', poll); };
  }, [batch?.id, batch?.status, loadHistory]);

  const run = async (operation) => {
    if (busy) return;
    const request = ++sequence.current; setBusy(true); setError(''); setNotice('');
    try { await operation(() => request === sequence.current); }
    catch (failure) { if (request === sequence.current) setError(failure.message || '操作失败，请重试'); }
    finally { if (request === sequence.current) setBusy(false); }
  };
  const upload = (file) => {
    if (!file) return;
    void run(async (current) => {
      if (!/\.xlsx$/i.test(file.name) || file.size > 5 * 1024 * 1024) throw new Error('请选择5MiB以内的.xlsx文件');
      const result = await previewUserImport(file);
      if (current()) { setBatch(result); setRowPage(1); setOnlyErrors(false); setPassword(''); setConfirmation(''); await loadHistory(); }
    });
  };
  const confirm = () => run(async (current) => {
    const validation = temporaryPasswordError(password, confirmation);
    if (validation) throw new Error(validation);
    let key = confirmKeys.current.get(batch.id);
    if (!key) { key = crypto.randomUUID(); confirmKeys.current.set(batch.id, key); }
    const result = await confirmUserImport(batch.id, key, password);
    if (current()) { setPassword(''); setConfirmation(''); setBatch((previous) => ({ ...previous, ...result, rows: previous.rows })); await loadHistory(); }
  });
  const openBatch = (id) => run(async (current) => { const result = await getUserImport(id); if (current()) { setBatch(result); setRowPage(1); setOnlyErrors(false); setPassword(''); setConfirmation(''); } });
  const visibleRows = (batch?.rows || []).filter((row) => !onlyErrors || row.status === 'ERROR');
  return <ModalFrame wide title="批量导入用户" description="管理员确认建号并分配项目角色；已有账号保持原样，用户首次登录改密后自行绑定微信。" onClose={() => { if (!busy) onClose(); }} footer={<>
    <button disabled={busy} onClick={onClose}>关闭</button>
    {batch?.status === 'PREVIEW' && batch.owned && <button className="primary" disabled={busy || batch.errorCount > 0 || !batch.newCount} onClick={confirm}>{busy ? '正在确认…' : `确认导入 ${batch.newCount} 人`}</button>}
    {batch?.status === 'SUCCEEDED' && batch.owned && <button className="primary" disabled={busy} onClick={() => run(async () => { await downloadUserImportCredentials(batch.id); setNotice('账号发放表已下载，请将每个人的凭据分别发给本人。'); })}>下载账号发放表</button>}
  </>}>
    <div className="user-import-content">
      <div className="user-import-steps"><span>1 下载并填写模板</span><span>2 上传校验名单</span><span>3 设置临时密码并导入</span></div>
      <div className="user-import-toolbar"><button disabled={busy} onClick={() => run(downloadUserImportTemplate)}>下载导入模板</button><button className="primary" disabled={busy} onClick={() => fileInput.current?.click()}>{busy ? '处理中…' : '上传名单校验'}</button><input hidden ref={fileInput} type="file" accept=".xlsx" onChange={(e) => { const file = e.target.files?.[0]; e.target.value = ''; upload(file); }} /><span>.xlsx · 最多500人 / 5000行 · 5MiB</span></div>
      <div className="system-inline-notice">同一人多个项目或角色填写多行。临时密码30天内有效，发放表仅24小时可下载；首次改密后不能再查看临时密码。</div>
      {error && <div className="system-form-error" role="alert">{error}</div>}
      {notice && <div className="system-inline-notice" role="status">{notice}</div>}
      {batch && <section aria-label="名单校验结果">
        <div className="user-import-result-title"><strong>批次 #{batch.id} · {LABELS[batch.status]}</strong><span>{date(batch.createdAt)}</span></div>
        <div className="user-import-counts"><div><strong>{batch.personCount}</strong>名单人数</div><div><strong>{batch.newCount}</strong>新增账号</div><div><strong>{batch.skippedCount}</strong>跳过人数</div><div className={batch.errorCount ? 'error' : ''}><strong>{batch.errorCount}</strong>错误行</div></div>
        <p className="user-import-message" role="status">{batch.message}{activeJob(batch) && `（已准备 ${batch.preparedCount || 0} / ${batch.newCount} 人）`}</p>
        {batch.status === 'PREVIEW' && batch.owned && batch.newCount > 0 && !batch.errorCount && <TemporaryPasswordFields password={password} confirmation={confirmation} setPassword={setPassword} setConfirmation={setConfirmation} disabled={busy} batch />}
        {activeJob(batch) && <progress aria-label="账号准备进度" max={batch.newCount || 1} value={batch.preparedCount || 0} />}
        <label className="user-import-filter"><input type="checkbox" checked={onlyErrors} onChange={(e) => { setOnlyErrors(e.target.checked); setRowPage(1); }} />只看错误行</label>
        <div className="system-table-wrap user-import-table"><table><thead><tr><th>行号</th><th>姓名</th><th>手机号</th><th>项目</th><th>角色</th><th>结果 / 原因</th></tr></thead><tbody>{visibleRows.slice((rowPage - 1) * 20, rowPage * 20).map((row) => <tr key={row.rowNumber}><td>{row.rowNumber}</td><td>{row.realName || '—'}</td><td>{row.phone || '—'}</td><td>{row.projectLabel || '—'}</td><td>{row.roleLabel || '—'}</td><td><span className={row.status === 'ERROR' ? 'user-import-error' : ''}>{LABELS[row.status]}</span><small>{row.message}</small></td></tr>)}{!visibleRows.length && <tr><td colSpan={6}>暂无{onlyErrors ? '错误' : ''}记录</td></tr>}</tbody></table></div>
        <Pagination pageNo={rowPage} pageSize={20} total={visibleRows.length} onPageChange={setRowPage} />
      </section>}
      <section aria-label="导入记录"><div className="user-import-result-title"><strong>导入记录</strong><span>可关闭窗口，稍后从这里查看结果</span></div>
        <div className="system-table-wrap"><table><thead><tr><th>批次 / 时间</th><th>操作人</th><th>人数</th><th>状态</th><th>操作</th></tr></thead><tbody>{history.records.map((row) => <tr key={row.id}><td>#{row.id}<small>{date(row.createdAt)}</small></td><td>{row.createdByName}</td><td>{row.personCount}</td><td>{LABELS[row.status]}</td><td><button disabled={busy} onClick={() => openBatch(row.id)}>查看</button></td></tr>)}{!history.records.length && <tr><td colSpan={5}>暂无导入记录</td></tr>}</tbody></table></div>
        <Pagination pageNo={historyPage} pageSize={10} total={history.total} onPageChange={setHistoryPage} />
      </section>
    </div>
  </ModalFrame>;
}

export function TemporaryPasswordDialog({ user, ModalFrame, onClose }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [regenerated, setRegenerated] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [notice, setNotice] = useState('');
  const run = async (regenerate) => {
    if (busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      if (regenerate) {
        const validation = temporaryPasswordError(password, confirmation);
        if (validation) throw new Error(validation);
        await regenerateTemporaryPassword(user.id, password); setRegenerated(true); setPassword(''); setConfirmation('');
        setNotice('新临时密码已设置，原密码及会话已失效。');
      }
      await downloadTemporaryPassword(user.id);
      setNotice('账号发放表已下载，请单独发给本人；用户首次登录必须改密。');
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  };
  return <ModalFrame title={`临时密码 - ${user.realName || user.username}`} description="仅用于尚未完成首次改密的导入账号。" onClose={() => { if (!busy) onClose(); }} footer={<><button disabled={busy} onClick={onClose}>关闭</button><button disabled={busy} onClick={() => run(false)}>下载当前发放表</button>{!regenerated && <button className="primary" disabled={busy} onClick={() => run(true)}>{busy ? '处理中…' : '确认设置并下载'}</button>}</>}>
    <div className="user-import-content"><p>账号：{user.username}</p><p>重新设置会立即使原临时密码及登录会话失效，新密码有效期重新计算30天，发放表24小时内可下载。</p>{!regenerated && <><p>当前密码有效期至：{date(user.temporaryPasswordExpiresAt)}</p><TemporaryPasswordFields password={password} confirmation={confirmation} setPassword={setPassword} setConfirmation={setConfirmation} disabled={busy} /></>}{error && <div className="system-form-error" role="alert">{error}</div>}{notice && <div className="system-inline-notice" role="status">{notice}</div>}</div>
  </ModalFrame>;
}

function TemporaryPasswordFields({ password, confirmation, setPassword, setConfirmation, disabled, batch = false }) {
  const [visible, setVisible] = useState(false);
  return <fieldset className="user-import-password" disabled={disabled}>
    <legend>{batch ? '设置本批统一临时密码' : '设置新临时密码'}</legend>
    <p>{batch ? '本批新增账号使用同一个临时密码，已有账号保持原密码。' : '请填写与原临时密码不同的新密码。'}8–72位，同时包含字母和数字。</p>
    <div className="user-import-password-fields">
      <label>临时密码<input type={visible ? 'text' : 'password'} autoComplete="new-password" maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="由管理员填写" /></label>
      <label>确认临时密码<input type={visible ? 'text' : 'password'} autoComplete="new-password" maxLength={72} value={confirmation} onChange={(e) => setConfirmation(e.target.value)} placeholder="再次输入临时密码" /></label>
    </div>
    <label className="user-import-password-visibility"><input type="checkbox" checked={visible} onChange={(e) => setVisible(e.target.checked)} />显示密码</label>
  </fieldset>;
}
