import { useCallback, useEffect, useRef, useState } from 'react';
import { getProjectModules, listProjectModules, saveProjectModules } from '../../services/projectModules';
import { PROJECT_MODULES } from '../../utils/projectModules';
import './projectModules.css';

const labels = (codes = []) => PROJECT_MODULES.filter((module) => codes.includes(module.code)).map((module) => module.label).join('、') || '全部关闭';

export default function ProjectModuleSettings({ ModalFrame, PageBar, Pagination }) {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState({ records: [], total: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState(null);
  const [selected, setSelected] = useState([]);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [conflict, setConflict] = useState(false);
  const sequence = useRef(0);
  const load = useCallback(async () => {
    const request = ++sequence.current;
    setLoading(true); setError('');
    try {
      const result = await listProjectModules({ keyword: query || undefined, pageNo: page, pageSize: 20 });
      if (request === sequence.current) setData(result);
    } catch (failure) {
      if (request === sequence.current) setError(failure.message);
    } finally { if (request === sequence.current) setLoading(false); }
  }, [query, page]);
  useEffect(() => { void load(); return () => { sequence.current += 1; }; }, [load]);
  const open = (row) => { setEditing(row); setSelected([...row.enabledBusinessModules]); setFormError(''); setConflict(false); };
  const save = async () => {
    if (saving || conflict) return;
    setSaving(true); setFormError('');
    try {
      const state = await saveProjectModules(editing.projectId, { moduleCodes: selected, expectedVersion: editing.moduleConfigVersion });
      setEditing(null); setMessage(`${editing.projectName}的模块配置已保存`);
      window.dispatchEvent(new CustomEvent('project-modules-changed', { detail: state }));
      await load();
    } catch (failure) {
      setFormError(failure.message || '保存失败，请重试');
      setConflict(failure?.response?.status === 409);
    } finally { setSaving(false); }
  };
  const reviewConflict = async () => {
    setSaving(true);
    try {
      const latest = await getProjectModules(editing.projectId);
      setEditing((current) => ({ ...current, ...latest }));
      setConflict(false);
      setFormError(`当前已保存：${labels(latest.enabledBusinessModules)}。已保留您的选择，请核对后保存。`);
    } catch (failure) { setFormError(failure.message); }
    finally { setSaving(false); }
  };
  return <>
    <PageBar title="项目模块" description="按项目控制 Web 与小程序启用的业务模块，角色权限继续单独配置。">
      <form className="system-search project-module-search" onSubmit={(event) => { event.preventDefault(); setPage(1); setQuery(keyword.trim()); if (page === 1 && query === keyword.trim()) void load(); }}>
        <input aria-label="项目名称" placeholder="搜索项目名称或简称" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
        <button className="primary" type="submit">查询</button>
      </form>
    </PageBar>
    {message && <div className="system-inline-notice" role="status">{message}</div>}
    {error && <div className="system-form-error" role="alert">{error}<button onClick={load}>重试</button></div>}
    <div className="system-table-wrap"><table><thead><tr><th>项目名称</th><th>启用模块</th><th>已启用</th><th>操作</th></tr></thead>
      <tbody>{data.records.map((row) => <tr key={row.projectId}><td><strong>{row.projectName}</strong></td><td><div className="project-module-tags">{PROJECT_MODULES.filter((module) => row.enabledBusinessModules.includes(module.code)).map((module) => <span key={module.code}>{module.label}</span>)}{!row.enabledBusinessModules.length && <span className="none">全部关闭</span>}</div></td><td>{row.enabledBusinessModules.length} / 5</td><td><div className="system-row-actions"><button onClick={() => open(row)}>配置模块</button></div></td></tr>)}
        {!data.records.length && <tr><td colSpan={4} className="project-module-empty">{loading ? '加载中…' : '暂无匹配项目'}</td></tr>}
      </tbody></table></div>
    <Pagination pageNo={page} pageSize={20} total={data.total} onPageChange={setPage} />
    {editing && <ModalFrame title={`配置模块 - ${editing.projectName}`} description="关闭后暂停该项目对应业务，历史数据与角色授权保留，重新启用后恢复。" onClose={() => { if (!saving) setEditing(null); }} footer={<><button className="plain" disabled={saving} onClick={() => setEditing(null)}>取消</button><button className="primary" disabled={saving || conflict} onClick={save}>{saving ? '处理中…' : '保存配置'}</button></>}>
      <div className="system-tree-toolbar project-module-actions"><span>已启用 {selected.length} 个业务模块</span><div><button disabled={saving} onClick={() => setSelected(PROJECT_MODULES.map((module) => module.code))}>全选</button><button disabled={saving} onClick={() => setSelected([])}>清空</button></div></div>
      <div className="project-module-options">{PROJECT_MODULES.map((module) => <label key={module.code}><input type="checkbox" disabled={saving} checked={selected.includes(module.code)} onChange={(event) => setSelected((current) => event.target.checked ? [...current, module.code] : current.filter((code) => code !== module.code))} /><span>{module.label}</span></label>)}</div>
      <div className="system-inline-notice">系统管理、项目信息、个人待办和“我的”保留。停用前的未完成任务按原截止时间恢复。</div>
      {formError && <div className="system-form-error project-module-feedback" role="alert"><span>{formError}</span>{conflict && <button disabled={saving} onClick={reviewConflict}>重新核对最新配置</button>}</div>}
    </ModalFrame>}
  </>;
}
