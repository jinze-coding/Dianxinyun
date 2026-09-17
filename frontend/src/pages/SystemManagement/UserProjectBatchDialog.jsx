import { useEffect, useRef, useState } from 'react';
import { previewProjectAccessBatch, confirmProjectAccessBatch } from '../../services/systemManagement';
import { responsibilityImpactLines } from '../../utils/responsibilityImpact';
import { ROLE_CHANGE_OPTIONS, accessBatchExplanation, isTransferOperation, buildAccessBatchRequest, accessBatchFormError } from './projectAccessBatch';
import './projectAccessBatch.css';

const ACTIONS = { ADDED: '加入项目', UPDATED: '调整角色', REMOVED: '移出项目', UNCHANGED: '保持不变' };
const roleText = (roles) => roles.map((role) => role.name).join('、') || '无';
const statusText = (status) => status === 'DISABLED' ? '访问暂停' : status === 'ACTIVE' ? '访问启用' : '未加入';

export default function UserProjectBatchDialog({ users, roles, projects, initialProjectId, ModalFrame, onClose, onRemoveUser, onSaved }) {
  const [form, setForm] = useState({ operation: 'ADD_ROLES', projectId: initialProjectId || '', sourceProjectId: initialProjectId || '', targetProjectId: '', roleSource: 'SOURCE', roleIds: [] });
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [acknowledged, setAcknowledged] = useState(false);
  const [onlyErrors, setOnlyErrors] = useState(false);
  const [done, setDone] = useState(null);
  const sequence = useRef(0);
  useEffect(() => () => { sequence.current += 1; }, []);
  const transfer = isTransferOperation(form.operation);
  const explanation = accessBatchExplanation(form, users.length, projects, roles);
  const update = (name, value) => {
    setForm((current) => ({ ...current, [name]: value,
      ...(name === 'operation' && !isTransferOperation(value) ? { roleIds: [] } : {}),
    }));
    setError('');
  };
  const changePurpose = (toTransfer) => {
    setForm((current) => ({ ...current, operation: toTransfer ? 'COPY_PROJECT' : 'ADD_ROLES', roleIds: [], roleSource: 'SOURCE',
      ...(toTransfer ? { sourceProjectId: current.projectId || current.sourceProjectId } : { projectId: current.sourceProjectId || current.projectId }),
    }));
    setError('');
  };
  const back = () => { setPreview(null); setAcknowledged(false); setOnlyErrors(false); setError(''); };
  const close = () => { if (!busy) onClose(); };

  const runPreview = async () => {
    const validation = accessBatchFormError(users.length, form);
    if (validation) { setError(validation); return; }
    const current = ++sequence.current;
    setBusy(true); setError(''); setAcknowledged(false);
    try {
      const response = await previewProjectAccessBatch(buildAccessBatchRequest(users.map((user) => user.id), form));
      if (current !== sequence.current) return;
      if (response.code !== 200) throw new Error(response.message || '预览失败');
      setPreview(response.data);
    } catch (failure) { if (current === sequence.current) setError(failure.message || '预览失败，请重试'); }
    finally { if (current === sequence.current) setBusy(false); }
  };

  const confirm = async () => {
    if (!preview?.confirmationToken || (preview.responsibilityCount > 0 && !acknowledged)) return;
    const current = ++sequence.current;
    setBusy(true); setError('');
    try {
      const response = await confirmProjectAccessBatch({ confirmationToken: preview.confirmationToken, confirmResponsibilityRelease: acknowledged });
      if (current !== sequence.current) return;
      if (response.code !== 200) throw new Error(response.message || '保存失败');
      setDone(response.data);
      await onSaved();
    } catch (failure) {
      if (current === sequence.current) {
        setPreview((value) => value ? { ...value, confirmationToken: null } : value);
        setError(`${failure.message || '未能确认保存结果'}。请重新预览核对当前授权后再操作。`);
      }
    } finally { if (current === sequence.current) setBusy(false); }
  };
  const projectSelect = (name, label) => <label className="batch-field">{label}<select aria-label={label} value={form[name]} disabled={busy} onChange={(event) => update(name, event.target.value)}>
    <option value="">请选择项目</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.projectName || project.name}</option>)}
  </select></label>;

  return <ModalFrame wide title={done ? '批量更改已完成' : '批量更改项目权限'} description={done ? '成员与角色已保存，相关用户重新登录后使用最新权限。' : `已选 ${users.length} 人 · ${preview ? '核对每个人保存后的角色' : '先选择要做的事，下一步核对每个人的变化'}`} onClose={close}
    footer={done ? <button className="primary" disabled={busy} onClick={close}>完成</button> : <>
      <button className="plain" disabled={busy} onClick={close}>取消</button>
      {preview && <button className="plain" disabled={busy} onClick={back}>返回修改</button>}
      {!preview ? <button className="primary" disabled={busy || !users.length} onClick={runPreview}>{busy ? '正在预览…' : '下一步：核对变化'}</button>
        : preview.confirmationToken ? <button className="primary" disabled={busy || (preview.responsibilityCount > 0 && !acknowledged)} onClick={confirm}>{busy ? '正在保存…' : `确认更改 ${preview.changedUserCount} 人`}</button>
          : <button className="primary" disabled={busy || !users.length} onClick={runPreview}>{busy ? '正在预览…' : '重新预览'}</button>}
    </>}>
    <div className="project-access-batch">
      {done ? <div className="batch-complete" role="status"><strong>已更改 {done.changedUserCount} 人</strong><p>加入项目 {done.addedCount} 项 · 调整角色 {done.updatedCount} 项 · 移出项目 {done.removedCount} 项 · 无变化 {done.unchangedUserCount} 人</p></div> : !preview ? <>
        <details className="batch-selected"><summary>查看已选用户（{users.length} 人）</summary><div>{users.map((user) => <span key={user.id}>{user.realName || user.username}<button aria-label={`取消选择 ${user.realName || user.username}`} disabled={busy} onClick={() => onRemoveUser(user.id)}>×</button></span>)}</div></details>
        <fieldset className="batch-purpose-grid" disabled={busy}><legend>你要做什么？</legend>
          <label className={!transfer ? 'selected' : ''}><input type="radio" name="batchPurpose" checked={!transfer} onChange={() => changePurpose(false)} /><span><strong>调整项目内的角色</strong><small>在一个项目中，增加或取消角色</small></span></label>
          <label className={transfer ? 'selected' : ''}><input type="radio" name="batchPurpose" checked={transfer} onChange={() => changePurpose(true)} /><span><strong>加入另一个项目</strong><small>选择新项目，再决定是否保留原项目</small></span></label>
        </fieldset>
        <div className="batch-projects">{transfer ? <>{projectSelect('sourceProjectId', '原项目')}{projectSelect('targetProjectId', '要加入的新项目')}</> : <>
          {projectSelect('projectId', '要调整的项目')}
          <label className="batch-field">角色怎么改？<select aria-label="角色怎么改" value={form.operation} disabled={busy} onChange={(event) => update('operation', event.target.value)}>{ROLE_CHANGE_OPTIONS.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
        </>}</div>
        {transfer && <>
          <fieldset className="batch-purpose-grid" disabled={busy}><legend>加入新项目后，原项目怎么办？</legend>
            <label className={form.operation === 'COPY_PROJECT' ? 'selected' : ''}><input type="radio" name="sourceMembership" checked={form.operation === 'COPY_PROJECT'} onChange={() => update('operation', 'COPY_PROJECT')} /><span><strong>保留原项目</strong><small>原项目的角色继续保留</small></span></label>
            <label className={form.operation === 'MOVE_PROJECT' ? 'selected' : ''}><input type="radio" name="sourceMembership" checked={form.operation === 'MOVE_PROJECT'} onChange={() => update('operation', 'MOVE_PROJECT')} /><span><strong>从原项目移出</strong><small>取消原项目的全部角色</small></span></label>
          </fieldset>
          <label className="batch-field">在新项目使用什么角色？<select aria-label="新项目的角色" value={form.roleSource} disabled={busy} onChange={(event) => update('roleSource', event.target.value)}><option value="SOURCE">和各自在原项目的角色相同</option><option value="SELECTED">为这些人统一选择角色</option></select></label>
        </>}
        {(!transfer || form.roleSource === 'SELECTED') && <fieldset className="batch-role-grid" disabled={busy}><legend>{form.operation === 'REMOVE_ROLES' ? '选择要取消的角色' : form.operation === 'REPLACE_ROLES' ? '保存后只保留这些角色' : '选择要增加的角色'}（可多选）</legend>
          {roles.map((role) => <label key={role.id} className={form.roleIds.includes(Number(role.id)) ? 'selected' : ''}><input type="checkbox" checked={form.roleIds.includes(Number(role.id))} onChange={(event) => update('roleIds', event.target.checked ? [...form.roleIds, Number(role.id)] : form.roleIds.filter((id) => id !== Number(role.id)))} />{role.roleName || role.name}</label>)}
          {!roles.length && <p>暂无可分配的启用项目角色。</p>}
        </fieldset>}
        <div className={`batch-effect${['REMOVE_ROLES', 'REPLACE_ROLES', 'MOVE_PROJECT'].includes(form.operation) ? ' changes-existing' : ''}`} aria-live="polite">
          <strong>保存后会怎样</strong><p>{explanation.summary}</p><p>{explanation.detail}</p><small>{explanation.example}</small>
        </div>
      </> : <>
        <div className="batch-effect"><strong>本次要做的事</strong><p>{explanation.summary}</p></div>
        <div className="batch-summary" role="status"><strong>变更 {preview.changedUserCount} 人</strong><span>无变化 {preview.unchangedUserCount} 人</span><span className={preview.blockedUserCount ? 'batch-error' : ''}>需修正 {preview.blockedUserCount} 人</span><span>加入 {preview.addedCount} · 调整 {preview.updatedCount} · 移出 {preview.removedCount} 项</span></div>
        {preview.blockedUserCount > 0 && <div className="batch-warning">存在未通过校验的用户，整批不会保存。请返回修改或取消选择这些用户。<label><input type="checkbox" checked={onlyErrors} onChange={(event) => setOnlyErrors(event.target.checked)} />只看需修正用户</label></div>}
        {preview.changedUserCount === 0 && preview.blockedUserCount === 0 && <p>当前选择与已有授权一致，无需保存。</p>}
        <div className="batch-preview-users">{preview.users.filter((user) => !onlyErrors || user.errors.length).map((user) => <section key={user.userId} className="batch-user-card">
          <header><strong>{user.realName || user.username || `用户 ${user.userId}`}</strong><span>{user.username}</span>{user.errors.length > 0 && <button disabled={busy} onClick={() => { onRemoveUser(user.userId); back(); }}>取消选择此人</button>}</header>
          {user.errors.map((message) => <p key={message} className="batch-error">{message}</p>)}
          {user.projects.map((project) => <div key={project.projectId} className="batch-project-change">
            <strong>{project.projectName}<em>{ACTIONS[project.action]}</em></strong>
            <p>现在的角色：{roleText(project.beforeRoles)} <small>（{statusText(project.beforeStatus)}）</small></p>
            <p>保存后角色：{roleText(project.afterRoles)} <small>（{statusText(project.afterStatus)}）</small></p>
            {project.removedPermissions.length > 0 && <details><summary>减少的权限（{project.removedPermissions.length} 项）</summary><p>{project.removedPermissions.join('、')}</p></details>}
            {responsibilityImpactLines([project.responsibilityImpact]).map((line) => <p className="batch-error" key={line}>解除责任：{line}</p>)}
          </div>)}
        </section>)}</div>
        {preview.responsibilityCount > 0 && <label className="batch-acknowledge"><input type="checkbox" disabled={busy || preview.blockedUserCount > 0} checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />我已核对以上 {preview.responsibilityCount} 项责任影响，同意解除相应责任绑定；业务记录保留，后续由管理员重新分配。</label>}
      </>}
      {error && <p className="system-form-error" role="alert">{error}</p>}
    </div>
  </ModalFrame>;
}
