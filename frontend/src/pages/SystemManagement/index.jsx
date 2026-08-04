import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  approveSystemRegistrationApplication,
  createSystemMenu,
  createSystemPermission,
  createSystemRole,
  deleteSystemRole,
  getSystemAuditLogs,
  getSystemMenus,
  getSystemPermissions,
  getSystemRegistrationApplications,
  getSystemRoles,
  getSystemUsers,
  getSystemWechatBindings,
  rejectSystemRegistrationApplication,
  resetSystemUserPassword,
  unbindSystemWechatBinding,
  updateSystemMenuStatus,
  updateSystemMenu,
  updateSystemPermission,
  updateSystemRole,
  updateSystemRoleMenus,
  updateSystemRoleOperationPermissions,
  updateSystemUserProjectRoleAssignments,
  updateSystemUserStatus,
  updateSystemWechatBindingStatus,
} from '../../services/systemManagement';
import {
  getProjectMemberAssignmentOptions,
  getAssignableProjectRoles,
  getProjectUserOptions,
  updateProjectMemberStatus,
  updateProjectRoleAssignments,
} from '../../services/projectMembers';
import {
  approveWechatAccessApplication,
  getWechatAccessApplications,
  rejectWechatAccessApplication,
} from '../../services/wechatAccess';
import {
  hasAssignedProjectMenu,
  hasPermission,
  hasProjectPermission,
  isPlatformAdmin,
} from '../../utils/permissions';
import {
  BUSINESS_MENU_DEFINITIONS,
  buildPermissionActions,
  buildRoleDefinitionRequest,
  buildRoleMenuTree,
  filterActionsByMenus,
  groupPermissionActions,
  menuNodeState,
  permissionIdsForActionKeys,
  selectedActionKeys,
  selectedLogicalMenuCodes,
  toggleActionKey,
  toggleMenuChild,
  toggleMenuNode,
} from '../../utils/roleAuthorization';
import {
  assignmentChanges,
  assignmentChangeSummary,
  assignmentNodeChanged,
  buildUserProjectNodes,
  defaultProjectMemberRoleId,
  filterAssignmentNodes,
  mergeAssignmentDrafts,
} from '../../utils/projectRoleAssignments';
import ProjectRoleAssignmentTree from './ProjectRoleAssignmentTree';
import './index.css';

const STATUS_TEXT = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  ACTIVE: '启用',
  ENABLED: '启用',
  DISABLED: '停用',
  UNBOUND: '已解绑',
};

const TABS = [
  { id: 'registration', label: '注册审核', code: 'SYSTEM_REGISTRATION', permissions: ['system.registration.review'] },
  { id: 'users', label: '用户管理', code: 'SYSTEM_USER', permissions: ['system.user.view'] },
  { id: 'roles', label: '角色与权限', code: 'SYSTEM_ROLE', permissions: ['system.user.view', 'system.role.manage'] },
  { id: 'menus', label: '菜单与功能', code: 'SYSTEM_MENU', permissions: ['system.user.view', 'system.menu.manage'] },
  { id: 'projects', label: '项目成员与权限', code: 'SYSTEM_PROJECT', permissions: ['project.member.manage'] },
  { id: 'wechat', label: '微信绑定', code: 'SYSTEM_WECHAT', permissions: ['system.wechat.manage'] },
  { id: 'audit', label: '操作日志', code: 'SYSTEM_AUDIT', permissions: ['system.audit.view'] },
];

const BUSINESS_MODULES = BUSINESS_MENU_DEFINITIONS.map((module) => ({
  code: module.moduleCode,
  label: module.label,
  description: module.description,
  menuCodes: module.backingMenuCodes,
}));

function extractList(data) {
  if (Array.isArray(data)) return data;
  return data?.records || data?.items || data?.list || data?.content || [];
}

function getId(item) {
  return item?.id ?? item?.userId ?? item?.roleId ?? item?.permissionId ?? item?.menuId;
}

const isEnabledValue = (value) => value === 1 || value === '1' || value === true
  || String(value || '').toUpperCase() === 'ACTIVE'
  || String(value || '').toUpperCase() === 'ENABLED';

const isProjectRole = (role) => String(role?.scopeType || role?.scope || '').toUpperCase() === 'PROJECT';

const businessModuleByMenu = (menu) => BUSINESS_MODULES.find((module) => module.menuCodes
  .includes(String(menu?.menuCode || menu?.code || '').toUpperCase()))?.code || null;

const deriveRoleBusinessModuleCodes = (role, menuItems) => {
  if (Array.isArray(role?.businessModuleCodes)) return role.businessModuleCodes;
  const assigned = new Set(role?.menuIds || []);
  return BUSINESS_MODULES.filter((module) => menuItems.some((menu) => assigned.has(getId(menu))
    && businessModuleByMenu(menu) === module.code)).map((module) => module.code);
};

const roleAssignablePermissions = (role, items) => {
  if (!isProjectRole(role)) return items;
  const isManager = Number(role?.projectManagerRole || 0) === 1;
  return items.filter((permission) => {
    const code = String(permission.permissionCode || permission.code || '');
    return !code.startsWith('system.') && (code !== 'project.member.manage' || isManager);
  });
};

const rolePermissionIdsFor = (role) => (Array.isArray(role?.permissionIds)
  ? role.permissionIds : (Array.isArray(role?.permissions) ? role.permissions.map(getId) : []))
  .filter((id) => id !== undefined && id !== null);

const roleCode = (role) => String(role?.roleCode || role?.code || '').trim();

const roleName = (role) => role?.roleName || role?.name || '未命名角色';

const projectAssignmentRoles = (assignment) => Array.isArray(assignment?.projectRoles)
  ? assignment.projectRoles : [];

const projectAssignmentRoleNames = (assignment) => {
  const names = projectAssignmentRoles(assignment).map(roleName).filter(Boolean);
  if (names.length) return names;
  return assignment?.projectRoleCode ? [assignment.projectRoleCode] : [];
};

const projectAssignmentName = (assignment, projects = []) => assignment?.projectName
  || assignment?.shortName
  || projects.find((project) => Number(project.id) === Number(assignment?.projectId))?.projectName
  || projects.find((project) => Number(project.id) === Number(assignment?.projectId))?.shortName
  || `项目 ${assignment?.projectId || '-'}`;

function RoleSelector({
  roles = [],
  selectedRoleIds = [],
  onChange,
  permissions = [],
  emptyText = '暂无可分配角色',
  disabled = false,
}) {
  const [keyword, setKeyword] = useState('');
  const selectedIds = useMemo(() => new Set((selectedRoleIds || []).map(Number)), [selectedRoleIds]);
  const permissionNameById = useMemo(() => new Map(permissions.map((permission) => [
    Number(getId(permission)), permission.permissionName || permission.name || permission.permissionCode || permission.code,
  ])), [permissions]);
  const visibleRoles = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    return roles.filter((role) => {
      if (!value) return true;
      return [roleName(role), roleCode(role), role.description]
        .filter(Boolean)
        .some((field) => String(field).toLowerCase().includes(value));
    });
  }, [keyword, roles]);

  const selectedRoles = roles.filter((role) => selectedIds.has(Number(getId(role))));
  const toggleRole = (roleId) => {
    const normalizedId = Number(roleId);
    const next = selectedIds.has(normalizedId)
      ? (selectedRoleIds || []).map(Number).filter((id) => id !== normalizedId)
      : [...(selectedRoleIds || []).map(Number), normalizedId];
    onChange(next);
  };

  return (
    <div className="system-role-selector">
      <div className="system-role-selector-toolbar">
        <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索角色名称、编码或说明" />
        <span>已选择 {selectedRoles.length} 个角色</span>
      </div>
      {!!selectedRoles.length && (
        <div className="system-role-selector-selected" aria-label="已选择角色">
          {selectedRoles.map((role) => <span key={getId(role)}>{roleName(role)}</span>)}
        </div>
      )}
      <div className="system-role-selector-grid">
        {visibleRoles.map((role) => {
          const id = Number(getId(role));
          const selected = selectedIds.has(id);
          const moduleLabels = (role.businessModuleCodes || [])
            .map((code) => BUSINESS_MODULES.find((module) => module.code === code)?.label)
            .filter(Boolean);
          const permissionLabels = Array.isArray(role.permissionNames) && role.permissionNames.length
            ? role.permissionNames
            : rolePermissionIdsFor(role)
              .map((permissionId) => permissionNameById.get(Number(permissionId)))
              .filter(Boolean);
          return (
            <button
              type="button"
              key={id}
              className={`system-role-option${selected ? ' selected' : ''}`}
              aria-pressed={selected}
              disabled={disabled}
              onClick={() => toggleRole(id)}
            >
              <span className="system-role-option-check">{selected ? '✓' : ''}</span>
              <span className="system-role-option-content">
                <strong>{roleName(role)}</strong>
                <small>{roleCode(role) || '未设置角色编码'} · 项目角色{Number(role.projectManagerRole || 0) === 1 ? ' · 受保护' : ''}</small>
                <span className="system-role-option-tags">
                  {moduleLabels.length
                    ? moduleLabels.map((label) => <em key={label}>{label}</em>)
                    : <em className="muted">未启用业务模块</em>}
                </span>
                <span className="system-role-option-summary">
                  {permissionLabels.length
                    ? `${permissionLabels.slice(0, 3).join('、')}${permissionLabels.length > 3 ? ` 等 ${permissionLabels.length} 项权限` : ''}`
                    : '未配置细分操作权限'}
                </span>
              </span>
            </button>
          );
        })}
        {!visibleRoles.length && <span className="system-hint">{keyword ? '没有匹配的角色' : emptyText}</span>}
      </div>
    </div>
  );
}

function Pagination({ pageNo, pageSize, total, onPageChange }) {
  const pageCount = Math.max(1, Math.ceil(Number(total || 0) / pageSize));
  return (
    <div className="system-pagination">
      <span>共 {total || 0} 条 · 第 {pageNo}/{pageCount} 页</span>
      <button disabled={pageNo <= 1} onClick={() => onPageChange(pageNo - 1)}>上一页</button>
      <button disabled={pageNo >= pageCount} onClick={() => onPageChange(pageNo + 1)}>下一页</button>
    </div>
  );
}

function formatDate(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 19);
}

function StatusTag({ status }) {
  const normalized = status === 1 || status === '1' ? 'ACTIVE'
    : status === 0 || status === '0' ? 'DISABLED'
      : String(status || '').toUpperCase();
  return <span className={`system-status status-${normalized.toLowerCase()}`}>{STATUS_TEXT[normalized] || status || '-'}</span>;
}

function Empty({ text = '暂无数据' }) {
  return <div className="system-empty">{text}</div>;
}

function Loading() {
  return <div className="system-empty"><span className="system-spinner" /> 数据加载中…</div>;
}

function ErrorState({ text, onRetry }) {
  return (
    <div className="system-empty system-error">
      <span>{text || '数据加载失败'}</span>
      <button onClick={onRetry}>重新加载</button>
    </div>
  );
}

function IndeterminateCheckbox({ indeterminate = false, ...props }) {
  const ref = useRef(null);
  useEffect(() => {
    if (ref.current) ref.current.indeterminate = indeterminate;
  }, [indeterminate]);
  return <input ref={ref} type="checkbox" {...props} />;
}

function ModalFrame({ title, description, wide = false, onClose, children, footer }) {
  return (
    <div className="system-modal-overlay" onClick={onClose}>
      <div className={`system-modal system-role-config-modal${wide ? ' wide' : ''}`} onClick={(event) => event.stopPropagation()}>
        <div className="system-role-config-header">
          <div><h2>{title}</h2>{description && <p>{description}</p>}</div>
          <button type="button" className="system-modal-close" aria-label="关闭" onClick={onClose}>×</button>
        </div>
        <div className="system-role-config-body">{children}</div>
        <div className="system-modal-footer">{footer}</div>
      </div>
    </div>
  );
}

function RoleDefinitionDialog({ role, onClose, onSave }) {
  const existing = Boolean(role);
  const protectedRole = roleCode(role) === 'PLATFORM_ADMIN';
  const protectedManager = Number(role?.projectManagerRole || 0) === 1;
  const [form, setForm] = useState({
    roleName: role?.roleName || role?.name || '',
    description: role?.description || '',
    enabled: Number(role?.enabled ?? 1),
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const submit = async () => {
    if (!form.roleName.trim()) return setError('请填写角色名称');
    setSubmitting(true);
    setError('');
    try {
      await onSave(buildRoleDefinitionRequest(form, existing ? role : null));
      onClose();
    } catch (err) {
      setError(err.message || '角色保存失败');
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <ModalFrame
      title={existing ? `编辑角色 - ${roleName(role)}` : '新建角色'}
      description="角色只定义职责；菜单和操作权限保存后再分别配置。"
      onClose={onClose}
      footer={<><button className="plain" onClick={onClose}>取消</button><button className="primary" disabled={submitting || protectedRole} onClick={submit}>{submitting ? '保存中…' : '保存'}</button></>}
    >
      {protectedRole && <div className="system-inline-notice">平台管理员是系统保护角色，不能在业务界面修改。</div>}
      <div className="system-role-form-grid">
        <label className="system-form-field"><span>角色名称 *</span><input maxLength="50" disabled={protectedRole} value={form.roleName} onChange={(event) => setForm({ ...form, roleName: event.target.value })} /></label>
        <label className="system-form-field"><span>角色类型</span><input disabled value={existing && !isProjectRole(role) ? '平台保护角色' : '项目角色'} /></label>
        <label className="system-form-field"><span>状态</span><select disabled={protectedRole || protectedManager} value={form.enabled} onChange={(event) => setForm({ ...form, enabled: Number(event.target.value) })}><option value={1}>启用</option><option value={0}>停用</option></select></label>
        <label className="system-form-field full"><span>角色说明</span><textarea maxLength="200" rows="4" disabled={protectedRole} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
      </div>
      {!existing && <div className="system-inline-notice">角色编码由系统自动生成，创建后保持不变，无需手工填写。</div>}
      {error && <div className="system-form-error" role="alert">{error}</div>}
    </ModalFrame>
  );
}

function MenuAssignmentDialog({ role, menus, selectedMenuIds, businessModuleCodes, onClose, onSave }) {
  const tree = useMemo(() => buildRoleMenuTree(menus, role), [menus, role]);
  const [menuIds, setMenuIds] = useState(() => selectedMenuIds.map(Number));
  const [moduleCodes, setModuleCodes] = useState(() => [...businessModuleCodes]);
  const [expanded, setExpanded] = useState(() => new Set(tree.map((node) => node.key)));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const legacyTabs = tree.some((node) => node.children?.some((child) => child.legacy));
  const updateNode = (node, checked) => {
    const next = toggleMenuNode({ node, checked, selectedMenuIds: menuIds, businessModuleCodes: moduleCodes });
    setMenuIds(next.menuIds);
    setModuleCodes(next.businessModuleCodes);
  };
  const updateChild = (parent, child, checked) => {
    const next = toggleMenuChild({ parent, child, checked, selectedMenuIds: menuIds, businessModuleCodes: moduleCodes });
    setMenuIds(next.menuIds);
    setModuleCodes(next.businessModuleCodes);
  };
  const toggleAll = (checked) => {
    let next = { menuIds: checked ? [] : menuIds, businessModuleCodes: checked ? [] : moduleCodes };
    tree.forEach((node) => {
      next = toggleMenuNode({ node, checked, selectedMenuIds: next.menuIds, businessModuleCodes: next.businessModuleCodes });
    });
    setMenuIds(next.menuIds);
    setModuleCodes(next.businessModuleCodes);
  };
  const submit = async () => {
    setSubmitting(true);
    setError('');
    try {
      await onSave({ menuIds, businessModuleCodes: moduleCodes });
      onClose();
    } catch (err) {
      setError(err.message || '菜单保存失败');
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <ModalFrame
      title={`分配菜单 - ${roleName(role)}`}
      description="控制角色能看到的模块和页面；取消菜单会同步清除该菜单下不再有效的操作权限。"
      wide
      onClose={onClose}
      footer={<><button className="plain" onClick={onClose}>取消</button><button className="primary" disabled={submitting} onClick={submit}>{submitting ? '保存中…' : '保存'}</button></>}
    >
      <div className="system-tree-toolbar"><span>已启用 {moduleCodes.length} 个业务模块</span><div><button onClick={() => toggleAll(true)}>全选</button><button onClick={() => toggleAll(false)}>清空</button></div></div>
      {legacyTabs && <div className="system-inline-notice warning">当前数据库尚未安装页签菜单迁移；灰色页签按父模块兼容显示，执行迁移后可单独分配。</div>}
      <div className="system-config-tree">
        {tree.map((node) => {
          const state = menuNodeState(node, menuIds, moduleCodes);
          const open = expanded.has(node.key);
          return <section key={node.key}>
            <div className="system-tree-row root">
              <button className="system-tree-expand" onClick={() => setExpanded((current) => { const next = new Set(current); if (next.has(node.key)) next.delete(node.key); else next.add(node.key); return next; })}>{open ? '⌄' : '›'}</button>
              <IndeterminateCheckbox checked={state.checked} indeterminate={state.indeterminate} onChange={(event) => updateNode(node, event.target.checked)} />
              <span><strong>{node.label}</strong><small>{node.description}</small></span>
            </div>
            {open && <div className="system-tree-children">{node.children?.map((child, index) => <label className={`system-tree-row${child.legacy ? ' legacy' : ''}`} key={child.key}>
              <span className="system-tree-spacer" />
              <input type="checkbox" checked={state.childStates[index]} disabled={child.legacy} onChange={(event) => updateChild(node, child, event.target.checked)} />
              <span>{child.label}{child.legacy && <small>随父模块显示</small>}</span>
            </label>)}</div>}
          </section>;
        })}
      </div>
      {error && <div className="system-form-error" role="alert">{error}</div>}
    </ModalFrame>
  );
}

function PermissionAssignmentDialog({ role, menus, permissions, selectedMenuIds, businessModuleCodes, selectedPermissionIds, onClose, onSave }) {
  const tree = useMemo(() => buildRoleMenuTree(menus, role), [menus, role]);
  const menuCodes = useMemo(() => selectedLogicalMenuCodes(tree, selectedMenuIds, businessModuleCodes), [businessModuleCodes, selectedMenuIds, tree]);
  const allActions = useMemo(() => buildPermissionActions(permissions), [permissions]);
  const visibleActions = useMemo(() => filterActionsByMenus(allActions, menuCodes), [allActions, menuCodes]);
  const groups = useMemo(() => groupPermissionActions(visibleActions), [visibleActions]);
  const [selectedKeys, setSelectedKeys] = useState(() => selectedActionKeys(selectedPermissionIds, visibleActions));
  const [expanded, setExpanded] = useState(() => new Set(groups.map((group) => group.label)));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const updateGroup = (group, checked) => {
    let next = new Set(selectedKeys);
    group.items.forEach((action) => { next = toggleActionKey(next, action.key, checked, visibleActions); });
    setSelectedKeys(next);
  };
  const submit = async () => {
    setSubmitting(true);
    setError('');
    try {
      await onSave({ permissionIds: permissionIdsForActionKeys(selectedKeys, visibleActions) });
      onClose();
    } catch (err) {
      setError(err.message || '操作权限保存失败');
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <ModalFrame
      title={`权限配置 - ${roleName(role)}`}
      description="一级分组可整组开启或取消；这里只配置操作权限，保存不会改变菜单可见性。"
      wide
      onClose={onClose}
      footer={<><button className="plain" onClick={onClose}>取消</button><button className="primary" disabled={submitting || !groups.length} onClick={submit}>{submitting ? '保存中…' : '保存'}</button></>}
    >
      <div className="system-tree-toolbar"><span>已选择 {selectedKeys.size} 项操作权限</span><div><button onClick={() => setExpanded(new Set(groups.map((group) => group.label)))}>展开全部</button><button onClick={() => setExpanded(new Set())}>收起全部</button></div></div>
      {!groups.length && <Empty text="请先为该角色分配菜单" />}
      <div className="system-config-tree permission-tree">{groups.map((group) => {
        const checkedCount = group.items.filter((action) => selectedKeys.has(action.key)).length;
        const open = expanded.has(group.label);
        return <section key={group.label}>
          <div className="system-tree-row root">
            <button className="system-tree-expand" onClick={() => setExpanded((current) => { const next = new Set(current); if (next.has(group.label)) next.delete(group.label); else next.add(group.label); return next; })}>{open ? '⌄' : '›'}</button>
            <IndeterminateCheckbox checked={checkedCount === group.items.length && group.items.length > 0} indeterminate={checkedCount > 0 && checkedCount < group.items.length} onChange={(event) => updateGroup(group, event.target.checked)} />
            <span><strong>{group.label}</strong><small>{checkedCount}/{group.items.length} 项</small></span>
          </div>
          {open && <div className="system-tree-children">{group.items.map((action) => <label className="system-tree-row" key={action.key}>
            <span className="system-tree-spacer" />
            <input type="checkbox" checked={selectedKeys.has(action.key)} onChange={(event) => setSelectedKeys(toggleActionKey(selectedKeys, action.key, event.target.checked, visibleActions))} />
            <span>{action.label}<small>{action.description || '权限依赖由系统自动补齐'}</small></span>
          </label>)}</div>}
        </section>;
      })}</div>
      {error && <div className="system-form-error" role="alert">{error}</div>}
    </ModalFrame>
  );
}

function PageBar({ title, description, children }) {
  return (
    <div className="system-page-bar">
      <div>
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      <div className="system-page-actions">{children}</div>
    </div>
  );
}

function SearchBar({ value, onChange, placeholder = '输入关键字查询', onSearch }) {
  return (
    <form className="system-search" onSubmit={(event) => { event.preventDefault(); onSearch?.(); }}>
      <input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />
      <button type="submit">查询</button>
    </form>
  );
}

function ReviewDialog({ application, roles, permissions, projectList, onClose, onApproved }) {
  const [projectAssignments, setProjectAssignments] = useState({});
  const [reviewComment, setReviewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const projectRoles = roles.filter((role) => isProjectRole(role) && isEnabledValue(role.enabled ?? 1));

  const updateProjectRoles = (projectId, roleIds) => setProjectAssignments((current) => ({
    ...current,
    [projectId]: roleIds,
  }));

  const approve = async () => {
    const assignments = Object.entries(projectAssignments)
      .filter(([, ids]) => ids.length)
      .map(([projectId, ids]) => ({ projectId: Number(projectId), roleIds: ids }));
    if (!assignments.length) {
      alert('请至少为一个项目分配角色');
      return;
    }
    if (!reviewComment.trim()) {
      alert('请填写审批意见');
      return;
    }
    setSubmitting(true);
    try {
      const res = await approveSystemRegistrationApplication(application.id, {
        projectAssignments: assignments,
        reviewComment: reviewComment.trim(),
      });
      if (res.code !== 200) throw new Error(res.message || '审核失败');
      onApproved();
    } catch (error) {
      alert(error.message || '审核失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="system-modal-overlay" onClick={onClose}>
      <div className="system-modal" onClick={(event) => event.stopPropagation()}>
        <PageBar title="批准注册申请" description={`${application.realName || '-'} · ${application.username || '-'}`}>
          <button className="plain" onClick={onClose}>关闭</button>
        </PageBar>
        <div className="system-modal-body">
          <div className="system-review-summary">
            <span>手机号：{application.phone || '-'}</span>
            <span>来源：{application.source || '-'}</span>
            <span>申请时间：{formatDate(application.createdAt || application.applyTime)}</span>
          </div>
          <fieldset>
            <legend>项目角色（可多选、可分配到多个项目）</legend>
            <div className="system-desired-projects">
              申请意向：{(application.desiredProjectIds || []).length
                ? application.desiredProjectIds.map((id) => projectList.find((project) => Number(project.id) === Number(id))?.projectName || `项目 ${id}`).join('、')
                : '未指定项目'}
            </div>
            <div className="system-project-assignment-list">
              {projectList.map((project) => (
                <div key={project.id} className={(application.desiredProjectIds || []).map(Number).includes(Number(project.id)) ? 'desired' : ''}>
                  <strong>{project.projectName || project.shortName || `项目 ${project.id}`}</strong>
                  <RoleSelector
                    roles={projectRoles}
                    selectedRoleIds={projectAssignments[project.id] || []}
                    onChange={(roleIds) => updateProjectRoles(project.id, roleIds)}
                    permissions={permissions}
                    emptyText="暂无可分配的项目角色"
                  />
                </div>
              ))}
              {!projectRoles.length && <span className="system-hint">暂无可分配的项目角色</span>}
            </div>
          </fieldset>
          <label className="system-form-field">
            <span>审核说明 *</span>
            <textarea rows="3" value={reviewComment} onChange={(event) => setReviewComment(event.target.value)} placeholder="必填，作为审批留痕" />
          </label>
        </div>
        <div className="system-modal-footer">
          <button className="plain" onClick={onClose}>取消</button>
          <button className="primary" disabled={submitting} onClick={approve}>{submitting ? '处理中…' : '批准并创建账号'}</button>
        </div>
      </div>
    </div>
  );
}

function ProjectAuthorizationDialog({
  mode,
  projectName,
  subject,
  userOptions,
  projectRoles,
  permissions,
  onSearchUsers,
  onClose,
  onSubmit,
}) {
  const initialUserId = subject?.userId
    || subject?.matchedUserId
    || userOptions[0]?.userId
    || userOptions[0]?.id
    || '';
  const [userId, setUserId] = useState(String(initialUserId));
  const [roleIds, setRoleIds] = useState(() => (subject?.projectRoles || subject?.roles || [])
    .map((role) => Number(getId(role))).filter(Number.isFinite));
  const [comment, setComment] = useState(mode === 'approve' ? '同意加入当前项目' : '');
  const [userKeyword, setUserKeyword] = useState('');
  const [candidateUsers, setCandidateUsers] = useState(userOptions);
  const [searchingUsers, setSearchingUsers] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => setCandidateUsers(userOptions), [userOptions]);

  const searchUsers = async () => {
    if (!onSearchUsers) return;
    setSearchingUsers(true);
    try {
      setCandidateUsers(await onSearchUsers(userKeyword));
    } catch (err) {
      alert(err.message || '查询系统用户失败');
    } finally {
      setSearchingUsers(false);
    }
  };

  const save = async () => {
    if (!userId) {
      alert('请选择系统用户');
      return;
    }
    if (!roleIds.length) {
      alert('请至少选择一个项目角色');
      return;
    }
    if (mode === 'approve' && !comment.trim()) {
      alert('请填写审批意见');
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit({
        userId: Number(userId),
        roleIds,
        comment: comment.trim(),
      });
      onClose();
    } catch (err) {
      alert(err.message || '项目授权保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  const title = mode === 'add' ? '新增项目成员' : mode === 'approve' ? '批准项目访问申请' : '调整项目授权';
  const description = mode === 'approve'
    ? '确认账号后，为申请人分配当前项目的一个或多个角色。'
    : '一个成员可拥有多个项目角色，菜单与操作权限按并集合并。';
  const subjectName = subject?.realName
    || subject?.applicantName
    || subject?.matchedUsername
    || subject?.username
    || '当前用户';

  return (
    <div className="system-modal-overlay" onClick={onClose}>
      <div className="system-modal system-project-auth-modal" onClick={(event) => event.stopPropagation()}>
        <PageBar title={title} description={`${projectName || '当前项目'} · ${description}`}>
          <button className="plain" onClick={onClose}>关闭</button>
        </PageBar>
        <div className="system-modal-body">
          {mode === 'add' ? (
            <>
              <div className="system-user-search">
                <input value={userKeyword} onChange={(event) => setUserKeyword(event.target.value)} placeholder="按姓名或账号搜索已启用系统用户" onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); searchUsers(); } }} />
                <button type="button" onClick={searchUsers} disabled={searchingUsers}>{searchingUsers ? '查询中…' : '查询'}</button>
              </div>
              <label className="system-form-field">
                <span>系统用户 *</span>
                <select value={userId} onChange={(event) => setUserId(event.target.value)}>
                  <option value="">请选择系统用户</option>
                  {candidateUsers.map((user) => (
                    <option key={user.userId || user.id} value={user.userId || user.id}>
                      {user.realName || user.username}（{user.username || '-'}）
                    </option>
                  ))}
                </select>
              </label>
            </>
          ) : (
            <div className="system-project-auth-summary">
              <strong>{subjectName}</strong>
              <span>{subject?.matchedUsername || subject?.username || `用户 ${userId}`}</span>
            </div>
          )}
          <fieldset className="system-project-role-selector">
            <legend>项目角色 *</legend>
            <RoleSelector roles={projectRoles} selectedRoleIds={roleIds} onChange={setRoleIds} permissions={permissions} emptyText="暂无可分配项目角色" />
          </fieldset>
          <div className="system-project-auth-help">
            角色中的资料、巡检、质量菜单和操作权限会自动合并；不再单独分配巡检权限模板。
          </div>
          {mode === 'approve' && (
            <label className="system-form-field">
              <span>审批意见 *</span>
              <textarea rows="3" value={comment} onChange={(event) => setComment(event.target.value)} />
            </label>
          )}
        </div>
        <div className="system-modal-footer">
          <button className="plain" disabled={submitting} onClick={onClose}>取消</button>
          <button className="primary" disabled={submitting || !projectRoles.length} onClick={save}>
            {submitting ? '处理中…' : mode === 'approve' ? '批准并授权' : '保存授权'}
          </button>
        </div>
      </div>
    </div>
  );
}

function UserProjectAccessDialog({
  user,
  projectList,
  projectRoles,
  onClose,
  onSave,
  onStatusChange,
  onChanged,
}) {
  const [nodes, setNodes] = useState(() => buildUserProjectNodes(projectList, user?.projectRoles || []));
  const [projectKeyword, setProjectKeyword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const defaultRoleId = defaultProjectMemberRoleId(projectRoles);
  const changes = useMemo(() => assignmentChanges(nodes, 'projectId'), [nodes]);
  const summary = useMemo(() => assignmentChangeSummary(nodes), [nodes]);
  const visibleNodes = useMemo(() => filterAssignmentNodes(nodes, projectKeyword), [nodes, projectKeyword]);

  const safeClose = () => {
    if (changes.length && !window.confirm('存在尚未保存的项目或角色变更，确认放弃吗？')) return;
    onClose();
  };

  const updateNode = (nextNode) => {
    setError('');
    setNodes((current) => current.map((node) => (node.id === nextNode.id ? nextNode : node)));
  };

  const save = async () => {
    if (!changes.length) return;
    if (summary.removed > 0 && !window.confirm(`本次将移出 ${summary.removed} 个项目，相关项目角色会一并移除。确认继续吗？`)) return;
    setSubmitting(true);
    setError('');
    try {
      await onSave({ changes });
      await onChanged?.();
      onClose();
    } catch (error) {
      setError(error.message || '项目与角色批量保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleAccess = async (node) => {
    const active = String(node.accessStatus || 'ACTIVE').toUpperCase() !== 'DISABLED';
    const projectId = Number(node.id);
    const reason = window.prompt(active ? '请输入暂停项目访问原因' : '请输入恢复说明', active ? '管理员暂停项目访问' : '');
    if (reason === null || (active && !reason.trim())) return;
    setSubmitting(true);
    setError('');
    try {
      const saved = await onStatusChange(projectId, {
        status: active ? 'DISABLED' : 'ACTIVE',
        reason,
      });
      setNodes((current) => current.map((item) => (item.id === projectId ? {
        ...item,
        accessStatus: saved?.accessStatus || (active ? 'DISABLED' : 'ACTIVE'),
        statusReason: saved?.statusReason ?? reason,
      } : item)));
      await onChanged?.();
    } catch (error) {
      setError(error.message || '项目访问状态更新失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="system-modal-overlay system-user-project-access-overlay" onClick={safeClose}>
      <div className="system-modal system-user-project-access-modal" onClick={(event) => event.stopPropagation()}>
        <PageBar title="分配项目与角色" description={`${user?.realName || user?.username || '当前用户'} · 勾选项目后再选择该项目内的一个或多个角色`}>
          <div className="system-page-actions">
            <button className="plain" onClick={safeClose}>关闭</button>
          </div>
        </PageBar>
        <div className="system-modal-body">
          <div className="system-project-auth-summary">
            <strong>{user?.realName || user?.username || '-'}</strong>
            <span>{user?.username || '-'}</span>
          </div>
          <div className="system-assignment-toolbar">
            <input value={projectKeyword} onChange={(event) => setProjectKeyword(event.target.value)} placeholder="搜索项目名称或已选角色" />
            <span className="system-hint">显示 {visibleNodes.length} / {nodes.length} 个项目</span>
          </div>
          {defaultRoleId === null && <div className="system-inline-notice warning">内置 USER（项目成员）角色缺失或已停用，项目快捷勾选暂不可用；请先恢复该基础角色。</div>}
          <ProjectRoleAssignmentTree
            nodes={visibleNodes}
            roles={projectRoles}
            defaultRoleId={defaultRoleId}
            onNodeChange={updateNode}
            onStatusChange={toggleAccess}
            onError={setError}
            busy={submitting}
            emptyText={projectKeyword ? '没有匹配的项目或角色' : '暂无可分配项目'}
          />
          <div className="system-project-auth-help">
            暂停访问不会移除项目和角色；角色调整也不会自动恢复已暂停访问。资料、巡检和质量权限按同一项目内的多个角色合并。
          </div>
          {error && <div className="system-form-error" role="alert">{error}</div>}
        </div>
        <div className="system-modal-footer">
          <span className="system-hint">新增 {summary.added} · 调整 {summary.updated} · 移出 {summary.removed}</span>
          <button className="plain" disabled={submitting} onClick={safeClose}>关闭</button>
          <button className="primary" disabled={submitting || !changes.length} onClick={save}>{submitting ? '保存中…' : '保存全部'}</button>
        </div>
      </div>
    </div>
  );
}

export default function SystemManagementPage({ currentUser, currentProject, projectList = [], onBack }) {
  const availableTabs = useMemo(
    () => TABS.filter((tab) => {
      if (isPlatformAdmin(currentUser)) return true;
      if (!hasAssignedProjectMenu(currentUser, currentProject, tab.code)) return false;
      if (tab.id === 'projects') {
        return hasProjectPermission(currentUser, currentProject, ...(tab.permissions || []));
      }
      return hasPermission(currentUser, ...(tab.permissions || []));
    }),
    [currentProject, currentUser],
  );
  const [activeTab, setActiveTab] = useState(availableTabs[0]?.id || '');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const pageSize = activeTab === 'projects' ? 100 : 20;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [roles, setRoles] = useState([]);
  const [permissions, setPermissions] = useState([]);
  const [menus, setMenus] = useState([]);
  const [selectedRole, setSelectedRole] = useState(null);
  const [roleDefinitionDialog, setRoleDefinitionDialog] = useState(undefined);
  const [roleMenuDialog, setRoleMenuDialog] = useState(null);
  const [rolePermissionDialog, setRolePermissionDialog] = useState(null);
  const [reviewing, setReviewing] = useState(null);
  const [projectApplications, setProjectApplications] = useState([]);
  const [projectMembershipFilter, setProjectMembershipFilter] = useState('ALL');
  const [projectDraftNodes, setProjectDraftNodes] = useState([]);
  const [projectSaving, setProjectSaving] = useState(false);
  const [projectAssignmentError, setProjectAssignmentError] = useState('');
  const [projectAuthorizationDialog, setProjectAuthorizationDialog] = useState(null);
  const [userProjectAccessDialog, setUserProjectAccessDialog] = useState(null);
  const requestSequenceRef = useRef(0);
  const projectAssignmentDraftsRef = useRef(new Map());
  const projectRoleOptions = useMemo(() => {
    return roles.filter((role) => isProjectRole(role) && isEnabledValue(role.enabled ?? 1));
  }, [roles]);
  const projectDefaultRoleId = useMemo(() => defaultProjectMemberRoleId(projectRoleOptions), [projectRoleOptions]);
  const projectChangeSummary = useMemo(() => assignmentChangeSummary(projectDraftNodes), [projectDraftNodes]);

  useEffect(() => {
    if (!availableTabs.some((tab) => tab.id === activeTab)) setActiveTab(availableTabs[0]?.id || '');
  }, [activeTab, availableTabs]);

  const loadRolesAndPermissions = useCallback(async () => {
    const [roleRes, permissionRes, menuRes] = await Promise.all([
      getSystemRoles({ pageSize: 200 }),
      getSystemPermissions({ pageSize: 500 }),
      getSystemMenus({}),
    ]);
    if (roleRes.code !== 200) throw new Error(roleRes.message || '角色加载失败');
    if (permissionRes.code !== 200) throw new Error(permissionRes.message || '权限加载失败');
    if (menuRes.code !== 200) throw new Error(menuRes.message || '菜单加载失败');
    const roleList = extractList(roleRes.data);
    setRoles(roleList);
    setPermissions(extractList(permissionRes.data));
    setMenus(extractList(menuRes.data));
    return roleList;
  }, []);

  const loadData = useCallback(async (overrides = {}) => {
    if (!activeTab) return;
    const requestSequence = ++requestSequenceRef.current;
    setLoading(true);
    setError('');
    try {
      let res;
      let nextProjectApplications = null;
      const effectivePage = overrides.pageNo || pageNo;
      const params = {
        keyword: keyword || undefined,
        status: status || undefined,
        page: effectivePage,
        pageNo: effectivePage,
        pageSize,
      };
      if (activeTab === 'registration') res = await getSystemRegistrationApplications(params);
      if (activeTab === 'users') res = await getSystemUsers(params);
      if (activeTab === 'roles') {
        await loadRolesAndPermissions();
        res = await getSystemRoles(params);
      }
      if (activeTab === 'menus') {
        const [menuRes, permissionRes] = await Promise.all([
          getSystemMenus(params),
          getSystemPermissions({ pageSize: 500 }),
        ]);
        if (permissionRes.code !== 200) throw new Error(permissionRes.message || '操作权限目录加载失败');
        setPermissions(extractList(permissionRes.data));
        res = menuRes;
      }
      if (activeTab === 'projects') {
        const [memberRes, applicationRes, roleRes, permissionRes] = await Promise.all([
          getProjectMemberAssignmentOptions(currentProject, {
            keyword: keyword || undefined,
            membership: projectMembershipFilter,
            pageNo: effectivePage,
            pageSize,
          }),
          getWechatAccessApplications({ projectId: currentProject, status: 'PENDING', pageNo: 1, pageSize: 100 }),
          isPlatformAdmin(currentUser) ? getSystemRoles({ pageSize: 200 }) : getAssignableProjectRoles(currentProject),
          isPlatformAdmin(currentUser) ? getSystemPermissions({ pageSize: 500 }) : Promise.resolve(null),
        ]);
        if (applicationRes.code === 200) {
          nextProjectApplications = extractList(applicationRes.data)
            .filter((application) => application.applicationType === 'PROJECT_ACCESS');
        }
        if (roleRes?.code === 200) setRoles(extractList(roleRes.data));
        if (permissionRes?.code !== 200 && permissionRes) {
          throw new Error(permissionRes.message || '操作权限目录加载失败');
        }
        if (permissionRes?.code === 200) setPermissions(extractList(permissionRes.data));
        res = memberRes;
      }
      if (activeTab === 'wechat') res = await getSystemWechatBindings({
        keyword: params.keyword,
        bindingStatus: status || undefined,
        pageNo: effectivePage,
        pageSize,
      });
      if (activeTab === 'audit') res = await getSystemAuditLogs(params);
      if (!res || res.code !== 200) throw new Error(res?.message || '数据加载失败');
      if (requestSequence !== requestSequenceRef.current) return;
      if (nextProjectApplications) setProjectApplications(nextProjectApplications);
      let list = extractList(res.data);
      if (keyword && ['roles', 'menus'].includes(activeTab)) {
        const normalizedKeyword = keyword.trim().toLowerCase();
        list = list.filter((item) => JSON.stringify(item).toLowerCase().includes(normalizedKeyword));
      }
      const isClientPaged = ['roles', 'menus'].includes(activeTab);
      const visibleList = isClientPaged ? list.slice((effectivePage - 1) * pageSize, effectivePage * pageSize) : list;
      setRows(activeTab === 'projects'
        ? mergeAssignmentDrafts(visibleList, projectAssignmentDraftsRef.current)
        : visibleList);
      setTotal(isClientPaged ? list.length : (res.data?.total ?? list.length));
      if (activeTab === 'roles') {
        setSelectedRole((current) => list.find((item) => getId(item) === getId(current)) || list[0] || null);
      }
    } catch (err) {
      if (requestSequence !== requestSequenceRef.current) return;
      setRows([]);
      setTotal(0);
      setError(err.message || '数据加载失败');
    } finally {
      if (requestSequence === requestSequenceRef.current) setLoading(false);
    }
  }, [activeTab, currentProject, currentUser, keyword, loadRolesAndPermissions, pageNo, pageSize, projectMembershipFilter, status]);

  useEffect(() => {
    setKeyword('');
    setStatus('');
    setPageNo(1);
  }, [activeTab]);

  useEffect(() => {
    projectAssignmentDraftsRef.current = new Map();
    setProjectDraftNodes([]);
    setProjectAssignmentError('');
    setPageNo(1);
  }, [currentProject]);

  useEffect(() => {
    loadData();
  }, [activeTab, currentProject, pageNo, pageSize, projectMembershipFilter]);

  const selectTab = (tabId) => {
    if (tabId === activeTab) return;
    const hasProjectChanges = activeTab === 'projects'
      && projectChangeSummary.added + projectChangeSummary.updated + projectChangeSummary.removed > 0;
    if (hasProjectChanges && !window.confirm('当前项目成员与角色存在未保存修改，确定放弃并切换页面吗？')) return;
    if (hasProjectChanges) {
      projectAssignmentDraftsRef.current = new Map();
      setProjectDraftNodes([]);
      setProjectAssignmentError('');
    }
    requestSequenceRef.current += 1;
    setRows([]);
    setTotal(0);
    setError('');
    setLoading(true);
    setActiveTab(tabId);
  };

  const rejectApplication = async (application) => {
    const reviewComment = window.prompt('请输入驳回原因');
    if (reviewComment === null || !reviewComment.trim()) return;
    try {
      const res = await rejectSystemRegistrationApplication(application.id, { reviewComment: reviewComment.trim() });
      if (res.code !== 200) throw new Error(res.message || '驳回失败');
      await loadData();
    } catch (err) {
      alert(err.message || '驳回失败');
    }
  };

  const openReview = async (application) => {
    try {
      if (!roles.length || !permissions.length) await loadRolesAndPermissions();
      setReviewing(application);
    } catch (err) {
      alert(err.message || '角色加载失败');
    }
  };

  const toggleUserStatus = async (user) => {
    const active = isEnabledValue(user.status);
    const nextStatus = active ? 0 : 1;
    const reason = window.prompt(active ? '请输入停用原因' : '请输入恢复说明（可留空）', active ? '管理员停用账号' : '');
    if (reason === null || (active && !reason.trim())) return;
    try {
      const res = await updateSystemUserStatus(getId(user), { status: nextStatus, reason });
      if (res.code !== 200) throw new Error(res.message || '用户状态更新失败');
      await loadData();
    } catch (err) {
      alert(err.message || '用户状态更新失败');
    }
  };

  const resetPassword = async (user) => {
    const newPassword = window.prompt(`为 ${user.realName || user.username} 设置新密码（至少 8 位）`);
    if (newPassword === null) return;
    if (newPassword.length < 8) {
      alert('新密码至少 8 位');
      return;
    }
    try {
      const res = await resetSystemUserPassword(getId(user), { newPassword });
      if (res.code !== 200) throw new Error(res.message || '密码重置失败');
      alert('密码已重置，用户的既有会话已失效');
    } catch (err) {
      alert(err.message || '密码重置失败');
    }
  };

  const openUserProjectAccess = async (user) => {
    try {
      if (!roles.length || !permissions.length) await loadRolesAndPermissions();
      setUserProjectAccessDialog(user);
    } catch (error) {
      alert(error.message || '项目角色加载失败');
    }
  };

  const saveUserProjectAccess = async (user, values) => {
    const res = await updateSystemUserProjectRoleAssignments(getId(user), values);
    if (!res || res.code !== 200) throw new Error(res?.message || '项目与角色批量保存失败');
    return res.data;
  };

  const updateUserProjectAccessStatus = async (user, projectId, values) => {
    const res = await updateProjectMemberStatus(projectId, getId(user), values);
    if (!res || res.code !== 200) throw new Error(res?.message || '项目访问状态更新失败');
    return res.data;
  };

  const saveRoleDefinition = async (values) => {
    const editingRole = roleDefinitionDialog || null;
    const res = editingRole
      ? await updateSystemRole(getId(editingRole), values)
      : await createSystemRole(values);
    if (res.code !== 200) throw new Error(res.message || '角色保存失败');
    if (res.data) setSelectedRole(res.data);
    await loadData();
  };

  const saveRoleMenus = async (targetRole, values) => {
    if (!targetRole) throw new Error('请选择角色');
    const res = await updateSystemRoleMenus(getId(targetRole), values);
    if (res.code !== 200) throw new Error(res.message || '菜单保存失败');
    await loadData();
  };

  const saveRoleOperationPermissions = async (targetRole, values) => {
    if (!targetRole) throw new Error('请选择角色');
    const res = await updateSystemRoleOperationPermissions(getId(targetRole), values);
    if (res.code !== 200) throw new Error(res.message || '操作权限保存失败');
    await loadData();
  };

  const toggleMenu = async (menu) => {
    const current = isEnabledValue(menu.enabled ?? menu.status);
    try {
      const res = await updateSystemMenuStatus(getId(menu), !current);
      if (res.code !== 200) throw new Error(res.message || '菜单状态更新失败');
      await loadData();
    } catch (err) {
      alert(err.message || '菜单状态更新失败');
    }
  };

  const editMenuDefinition = async (menu = null) => {
    const menuName = window.prompt('菜单/功能名称', menu?.menuName || menu?.name || '');
    if (menuName === null || !menuName.trim()) return;
    const menuCode = window.prompt('菜单编码（大写字母、数字和下划线）', menu?.menuCode || menu?.code || '');
    if (menuCode === null || !menuCode.trim()) return;
    const clientType = window.prompt('客户端（WEB / MINI_PROGRAM / COMMON）', menu?.clientType || 'WEB');
    if (clientType === null || !['WEB', 'MINI_PROGRAM', 'COMMON'].includes(clientType.trim().toUpperCase())) {
      if (clientType !== null) alert('客户端只能是 WEB、MINI_PROGRAM 或 COMMON');
      return;
    }
    const routePath = window.prompt('页面路由', menu?.routePath || menu?.path || '');
    if (routePath === null) return;
    const permissionCode = window.prompt('关联查看权限码（可留空）', menu?.permissionCode || '');
    if (permissionCode === null) return;
    const sortOrder = window.prompt('排序号', String(menu?.sortOrder ?? 0));
    if (sortOrder === null || !Number.isFinite(Number(sortOrder))) return;
    const payload = {
      parentId: menu?.parentId || null,
      menuName: menuName.trim(),
      menuCode: menuCode.trim().toUpperCase(),
      clientType: clientType.trim().toUpperCase(),
      resourceType: menu?.resourceType || menu?.type || 'MENU',
      routePath: routePath.trim(),
      permissionCode: permissionCode.trim() || null,
      sortOrder: Number(sortOrder),
      visible: Number(menu?.visible ?? 1),
      enabled: Number(menu?.enabled ?? 1),
    };
    try {
      const res = menu
        ? await updateSystemMenu(getId(menu), payload)
        : await createSystemMenu(payload);
      if (res.code !== 200) throw new Error(res.message || '菜单保存失败');
      await loadData();
    } catch (err) {
      alert(err.message || '菜单保存失败');
    }
  };

  const editPermissionDefinition = async (permission = null) => {
    const permissionName = window.prompt('操作权限名称', permission?.permissionName || permission?.name || '');
    if (permissionName === null || !permissionName.trim()) return;
    const permissionCode = window.prompt('操作权限码（例如 document.upload）', permission?.permissionCode || permission?.code || '');
    if (permissionCode === null || !permissionCode.trim()) return;
    const moduleCode = window.prompt('所属模块编码', permission?.moduleCode || 'CUSTOM');
    if (moduleCode === null || !moduleCode.trim()) return;
    const description = window.prompt('权限说明（可留空）', permission?.description || '');
    if (description === null) return;
    const payload = {
      permissionName: permissionName.trim(),
      permissionCode: permissionCode.trim().toLowerCase(),
      moduleCode: moduleCode.trim().toUpperCase(),
      description: description.trim(),
      enabled: Number(permission?.enabled ?? 1),
    };
    try {
      const res = permission
        ? await updateSystemPermission(getId(permission), payload)
        : await createSystemPermission(payload);
      if (res.code !== 200) throw new Error(res.message || '操作权限保存失败');
      await loadData();
    } catch (err) {
      alert(err.message || '操作权限保存失败');
    }
  };

  const removeRoleDefinition = async (targetRole = selectedRole) => {
    if (!targetRole) return;
    if (!window.confirm(`确认删除角色“${targetRole.roleName || targetRole.name}”？`)) return;
    try {
      const res = await deleteSystemRole(getId(targetRole));
      if (res.code !== 200) throw new Error(res.message || '角色删除失败');
      if (getId(selectedRole) === getId(targetRole)) setSelectedRole(null);
      await loadData();
    } catch (err) {
      alert(err.message || '角色删除失败');
    }
  };

  const updateProjectAssignmentNode = (nextNode) => {
    const drafts = projectAssignmentDraftsRef.current;
    if (assignmentNodeChanged(nextNode)) drafts.set(Number(nextNode.id), nextNode);
    else drafts.delete(Number(nextNode.id));
    setProjectDraftNodes(Array.from(drafts.values()));
    setRows((current) => current.map((node) => (node.id === nextNode.id ? nextNode : node)));
    setProjectAssignmentError('');
  };

  const toggleProjectMember = async (member) => {
    const active = String(member.accessStatus || 'ACTIVE').toUpperCase() !== 'DISABLED';
    const reason = window.prompt(active ? '请输入暂停项目访问原因' : '请输入恢复说明', active ? '管理员暂停项目访问' : '');
    if (reason === null || (active && !reason.trim())) return;
    try {
      const res = await updateProjectMemberStatus(currentProject, member.id, { status: active ? 'DISABLED' : 'ACTIVE', reason });
      if (res.code !== 200) throw new Error(res.message || '项目授权更新失败');
      const nextStatus = res.data?.accessStatus || (active ? 'DISABLED' : 'ACTIVE');
      const nextReason = res.data?.statusReason ?? reason;
      setRows((current) => current.map((node) => (node.id === member.id ? {
        ...node,
        accessStatus: nextStatus,
        statusReason: nextReason,
      } : node)));
      const draft = projectAssignmentDraftsRef.current.get(Number(member.id));
      if (draft) {
        projectAssignmentDraftsRef.current.set(Number(member.id), {
          ...draft,
          accessStatus: nextStatus,
          statusReason: nextReason,
        });
        setProjectDraftNodes(Array.from(projectAssignmentDraftsRef.current.values()));
      }
    } catch (err) {
      setProjectAssignmentError(err.message || '项目授权更新失败');
    }
  };

  const saveProjectAssignments = async () => {
    const changes = assignmentChanges(projectDraftNodes, 'userId');
    const summary = assignmentChangeSummary(projectDraftNodes);
    if (!changes.length) return;
    if (summary.removed > 0 && !window.confirm(`本次将从当前项目移出 ${summary.removed} 名成员，确认继续吗？`)) return;
    setProjectSaving(true);
    setProjectAssignmentError('');
    try {
      const res = await updateProjectRoleAssignments(currentProject, { changes });
      if (!res || res.code !== 200) throw new Error(res?.message || '项目成员批量保存失败');
      projectAssignmentDraftsRef.current = new Map();
      setProjectDraftNodes([]);
      await loadData({ pageNo });
    } catch (err) {
      setProjectAssignmentError(err.message || '项目成员批量保存失败');
    } finally {
      setProjectSaving(false);
    }
  };

  const submitProjectAuthorization = async (values) => {
    if (!projectAuthorizationDialog) return;
    const { mode, subject } = projectAuthorizationDialog;
    let res;
    if (mode === 'approve') {
      res = await approveWechatAccessApplication(subject.id, {
        accountMode: 'EXISTING',
        userId: values.userId,
        projectId: Number(subject.projectId || currentProject),
        roleIds: values.roleIds,
        comment: values.comment,
      });
    }
    if (!res || res.code !== 200) {
      throw new Error(res?.message || '项目访问申请审批失败');
    }
    await loadData();
  };

  const toggleWechat = async (user) => {
    const bindingId = user.bindingId || user.binding?.id || user.bindings?.[0]?.id;
    if (!bindingId) return;
    const active = (user.bindingStatus || user.binding?.status || user.bindings?.[0]?.status) === 'ACTIVE';
    const reason = window.prompt(active ? '请输入停用微信登录原因' : '请输入恢复说明', active ? '管理员停用微信登录' : '');
    if (reason === null || (active && !reason.trim())) return;
    try {
      const res = await updateSystemWechatBindingStatus(user.userId || getId(user), bindingId, {
        status: active ? 'DISABLED' : 'ACTIVE',
        reason,
      });
      if (res.code !== 200) throw new Error(res.message || '微信状态更新失败');
      await loadData();
    } catch (err) {
      alert(err.message || '微信状态更新失败');
    }
  };

  const unbindWechat = async (user) => {
    const bindingId = user.bindingId || user.binding?.id || user.bindings?.[0]?.id;
    if (!bindingId || !window.confirm(`确认解绑 ${user.realName || user.username || '该用户'} 的微信？用户所有会话将失效。`)) return;
    const reason = window.prompt('请输入解绑原因', '管理员解绑微信');
    if (reason === null || !reason.trim()) return;
    try {
      const res = await unbindSystemWechatBinding(user.userId || getId(user), bindingId, { reason });
      if (res.code !== 200) throw new Error(res.message || '微信解绑失败');
      await loadData();
    } catch (err) {
      alert(err.message || '微信解绑失败');
    }
  };

  const approveProjectApplication = (application) => {
    if (!application.matchedUserId) {
      alert('该申请尚未绑定系统账号，请先引导用户完成统一注册或账号绑定。');
      return;
    }
    setProjectAuthorizationDialog({ mode: 'approve', subject: application, userOptions: [] });
  };

  const rejectProjectApplication = async (application) => {
    const comment = window.prompt('请输入驳回原因');
    if (comment === null || !comment.trim()) return;
    try {
      const res = await rejectWechatAccessApplication(application.id, { comment: comment.trim() });
      if (res.code !== 200) throw new Error(res.message || '项目访问申请驳回失败');
      await loadData();
    } catch (err) {
      alert(err.message || '项目访问申请驳回失败');
    }
  };

  const runSearch = () => {
    if (pageNo !== 1) setPageNo(1);
    else loadData({ pageNo: 1 });
  };

  const currentProjectName = projectList.find((project) => Number(project.id) === Number(currentProject))?.projectName;
  const pendingCount = activeTab === 'registration' ? rows.filter((item) => item.status === 'PENDING').length : null;

  const renderRegistration = () => (
    <>
      <PageBar title="注册审核" description="审核 Web 与小程序提交的新账号申请，并在通过时分配角色和项目权限。">
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="">全部状态</option><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option>
        </select>
        <SearchBar value={keyword} onChange={setKeyword} placeholder="姓名、账号或手机号" onSearch={runSearch} />
      </PageBar>
      <div className="system-summary-row">
        <div><span>当前结果</span><strong>{total}</strong></div>
        <div><span>待审核</span><strong className="warning">{pendingCount ?? '-'}</strong></div>
        <div><span>审核规则</span><strong className="text">先授权后启用</strong></div>
      </div>
      <div className="system-table-wrap">
        <table><thead><tr><th>申请人</th><th>账号</th><th>手机/邮箱</th><th>项目意向</th><th>来源</th><th>申请时间</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>{rows.map((item) => <tr key={item.id}>
            <td><strong>{item.realName || '-'}</strong><small>{item.applicationReason || item.reason || '未填写申请说明'}</small></td>
            <td>{item.username || '-'}</td><td>{item.phone || '-'}<small>{item.email || '-'}</small></td>
            <td>{(item.desiredProjectIds || []).map((id) => projectList.find((project) => Number(project.id) === Number(id))?.projectName || `项目 ${id}`).join('、') || item.desiredProjectText || item.desiredProjectName || '未指定'}</td><td>{item.source || item.sourceType || '-'}</td>
            <td>{formatDate(item.createdAt || item.applyTime)}</td><td><StatusTag status={item.status} /></td>
            <td>{item.status === 'PENDING' ? <div className="system-row-actions"><button onClick={() => openReview(item)}>批准</button><button className="danger" onClick={() => rejectApplication(item)}>驳回</button></div> : <span className="system-hint">{item.reviewComment || '已处理'}</span>}</td>
          </tr>)}</tbody></table>
      </div>
    </>
  );

  const renderUsers = () => (
    <>
      <PageBar title="用户管理" description="统一查看用户已加入的项目和项目角色，并可直接分配项目与角色；平台全局身份不在此处配置。">
        <SearchBar value={keyword} onChange={setKeyword} placeholder="姓名、账号或手机号" onSearch={runSearch} />
      </PageBar>
      <div className="system-table-wrap"><table><thead><tr><th>用户</th><th>联系方式</th><th>已分配项目与角色</th><th>登录方式</th><th>状态</th><th>最近登录</th><th>操作</th></tr></thead>
        <tbody>{rows.map((user) => {
          const assignments = Array.isArray(user.projectRoles) ? user.projectRoles : [];
          return <tr key={getId(user)}>
            <td><strong>{user.realName || '-'}</strong><small>{user.username}</small></td><td>{user.phone || '-'}<small>{user.email || '-'}</small></td>
            <td className="system-user-project-cell"><div className="system-user-project-summary">
              {assignments.slice(0, 2).map((assignment) => <span key={assignment.projectId} className={assignment.accessStatus === 'DISABLED' ? 'paused' : ''}>{projectAssignmentName(assignment, projectList)} · {projectAssignmentRoleNames(assignment).join('、') || '未分配角色'}{assignment.accessStatus === 'DISABLED' ? '（暂停）' : ''}</span>)}
              {assignments.length > 2 && <span>+{assignments.length - 2}</span>}
              <button type="button" onClick={() => openUserProjectAccess(user)}>{assignments.length ? '查看并调整' : '尚未分配，立即设置'}</button>
            </div></td>
            <td>{!isEnabledValue(user.passwordLoginEnabled) ? '仅微信' : user.wechatBound ? '密码 + 微信' : '账号密码'}</td><td><StatusTag status={user.status} /></td><td>{formatDate(user.lastLoginAt || user.createTime)}</td>
            <td><div className="system-row-actions">{hasPermission(currentUser, 'system.user.manage') && <button className="primary" onClick={() => openUserProjectAccess(user)}>分配项目与角色</button>}{hasPermission(currentUser, 'system.user.reset_password') && <button onClick={() => resetPassword(user)}>重置密码</button>}{hasPermission(currentUser, 'system.user.status') && <button className={isEnabledValue(user.status) ? 'danger' : ''} onClick={() => toggleUserStatus(user)}>{isEnabledValue(user.status) ? '停用' : '启用'}</button>}{!hasPermission(currentUser, 'system.user.manage', 'system.user.reset_password', 'system.user.status') && <span className="system-hint">只读</span>}</div></td>
          </tr>;
        })}</tbody></table></div>
    </>
  );

  const renderRoles = () => (
    <>
      <PageBar title="角色管理" description="先创建角色，再分别分配菜单和操作权限；用户在具体项目中可同时拥有多个角色。">
        {hasPermission(currentUser, 'system.role.manage') && <button className="primary" onClick={() => setRoleDefinitionDialog(null)}>新增角色</button>}
        <SearchBar value={keyword} onChange={setKeyword} placeholder="角色名称、编码或说明" onSearch={runSearch} />
      </PageBar>
      <div className="system-role-guide">
        <div><strong>1. 分配菜单</strong><span>决定角色能看到哪些模块和页面</span></div>
        <div><strong>2. 配置权限</strong><span>决定页面内可以执行哪些操作</span></div>
        <div><strong>3. 分配用户</strong><span>在用户管理或项目成员中勾选角色</span></div>
      </div>
      <div className="system-table-wrap system-role-table"><table><thead><tr><th>角色</th><th>类型</th><th>业务模块</th><th>菜单</th><th>操作权限</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{rows.map((role) => {
          const protectedRole = roleCode(role) === 'PLATFORM_ADMIN';
          const moduleCodes = role.businessModuleCodes || [];
          const roleMenus = role.menuIds || [];
          const rolePermissions = rolePermissionIdsFor(role);
          const moduleLabels = moduleCodes.map((code) => BUSINESS_MODULES.find((module) => module.code === code)?.label).filter(Boolean);
          return <tr key={getId(role)} className={getId(selectedRole) === getId(role) ? 'selected' : ''} onClick={() => setSelectedRole(role)}>
            <td><strong>{roleName(role)}</strong><small>{roleCode(role)}{Number(role.builtin || 0) === 1 ? ' · 内置' : ''}</small></td>
            <td>{isProjectRole(role) ? (Number(role.projectManagerRole || 0) === 1 ? '项目经理' : '项目角色') : '平台保护角色'}</td>
            <td><div className="system-role-tags">{moduleLabels.length ? moduleLabels.map((label) => <span key={label}>{label}</span>) : <em>未分配</em>}</div></td>
            <td>{roleMenus.length} 项</td><td>{rolePermissions.length} 项</td><td><StatusTag status={isEnabledValue(role.enabled) ? 'ENABLED' : 'DISABLED'} /></td>
            <td><div className="system-row-actions" onClick={(event) => event.stopPropagation()}>
              <button disabled={protectedRole} onClick={() => { setSelectedRole(role); setRoleDefinitionDialog(role); }}>编辑</button>
              <button disabled={protectedRole} onClick={() => { setSelectedRole(role); setRoleMenuDialog(role); }}>分配菜单</button>
              <button className="primary" disabled={protectedRole} onClick={() => { setSelectedRole(role); setRolePermissionDialog(role); }}>权限配置</button>
              <button className="danger" disabled={Number(role.builtin || 0) === 1} onClick={() => removeRoleDefinition(role)}>删除</button>
            </div></td>
          </tr>;
        })}</tbody></table></div>
    </>
  );

  const renderMenus = () => (
    <>
      <PageBar title="菜单与功能" description="统一维护 Web、小程序菜单与操作权限目录；隐藏菜单不会替代后端权限校验。">
        {hasPermission(currentUser, 'system.menu.manage') && <button onClick={() => editMenuDefinition(null)}>新增菜单</button>}
        {hasPermission(currentUser, 'system.menu.manage') && <button onClick={() => editPermissionDefinition(null)}>新增操作权限</button>}
        <SearchBar value={keyword} onChange={setKeyword} placeholder="菜单名称或编码" onSearch={runSearch} />
      </PageBar>
      <div className="system-table-wrap"><table><thead><tr><th>菜单/功能</th><th>编码</th><th>客户端</th><th>类型</th><th>关联权限</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{rows.map((menu) => <tr key={getId(menu)}><td><strong style={{ paddingLeft: `${Number(menu.level || 0) * 16}px` }}>{menu.menuName || menu.name}</strong></td><td>{menu.menuCode || menu.code}</td><td>{menu.clientType || 'WEB'}</td><td>{menu.resourceType || menu.type || 'MENU'}</td><td>{menu.permissionCode || '-'}</td><td>{menu.sortOrder ?? menu.sort ?? '-'}</td><td><StatusTag status={isEnabledValue(menu.enabled ?? menu.status) ? 'ENABLED' : 'DISABLED'} /></td><td>{hasPermission(currentUser, 'system.menu.manage') ? <div className="system-row-actions"><button onClick={() => editMenuDefinition(menu)}>编辑</button><button onClick={() => toggleMenu(menu)}>{isEnabledValue(menu.enabled ?? menu.status) ? '停用' : '启用'}</button></div> : <span className="system-hint">只读</span>}</td></tr>)}</tbody></table></div>
      <div className="system-subsection-title"><strong>操作权限目录</strong><span>{permissions.length} 项</span></div>
      <div className="system-table-wrap"><table><thead><tr><th>权限名称</th><th>权限码</th><th>模块</th><th>说明</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{permissions.map((permission) => <tr key={getId(permission)}><td><strong>{permission.permissionName || permission.name}</strong></td><td>{permission.permissionCode || permission.code}</td><td>{permission.moduleCode || '-'}</td><td>{permission.description || '-'}</td><td><StatusTag status={isEnabledValue(permission.enabled) ? 'ENABLED' : 'DISABLED'} /></td><td>{hasPermission(currentUser, 'system.menu.manage') ? <button onClick={() => editPermissionDefinition(permission)}>编辑</button> : <span className="system-hint">只读</span>}</td></tr>)}</tbody></table></div>
    </>
  );

  const renderProjects = () => (
    <>
      <PageBar title="项目成员与权限" description={`当前作业区域：${currentProjectName || currentProject || '-'}。勾选用户建立成员关系，展开后配置一个或多个项目角色。`}>
        <select value={projectMembershipFilter} onChange={(event) => { setProjectMembershipFilter(event.target.value); setPageNo(1); }}>
          <option value="ALL">全部用户</option>
          <option value="ASSIGNED">已加入</option>
          <option value="UNASSIGNED">未加入</option>
        </select>
        <SearchBar value={keyword} onChange={setKeyword} placeholder="姓名、账号或角色" onSearch={runSearch} />
      </PageBar>
      {projectDefaultRoleId === null && <div className="system-inline-notice warning">内置 USER（项目成员）角色缺失或已停用，用户快捷勾选暂不可用；请先由平台管理员恢复该基础角色。</div>}
      <ProjectRoleAssignmentTree
        nodes={rows}
        roles={projectRoleOptions}
        defaultRoleId={projectDefaultRoleId}
        onNodeChange={updateProjectAssignmentNode}
        onStatusChange={toggleProjectMember}
        onError={setProjectAssignmentError}
        busy={projectSaving}
        emptyText={keyword ? '没有匹配的用户或角色' : '当前筛选下暂无用户'}
      />
      {projectAssignmentError && <div className="system-form-error" role="alert">{projectAssignmentError}</div>}
      <div className="system-assignment-savebar">
        <span>新增 {projectChangeSummary.added} · 调整 {projectChangeSummary.updated} · 移出 {projectChangeSummary.removed}</span>
        <button className="primary" disabled={projectSaving || !projectDraftNodes.length} onClick={saveProjectAssignments}>{projectSaving ? '保存中…' : '保存全部'}</button>
      </div>
      <div className="system-subsection-title"><strong>待审核项目访问申请</strong><span>{projectApplications.length} 条</span></div>
      <div className="system-table-wrap"><table><thead><tr><th>申请人</th>{isPlatformAdmin(currentUser) && <th>手机号</th>}<th>目标项目</th><th>申请类型</th><th>申请时间</th><th>操作</th></tr></thead>
        <tbody>{projectApplications.map((application) => <tr key={application.id}><td><strong>{application.realName || application.applicantName || '-'}</strong><small>{application.matchedUsername || (application.matchedUserId ? `用户 ${application.matchedUserId}` : '未绑定系统账号')}</small></td>{isPlatformAdmin(currentUser) && <td>{application.phone || '-'}</td>}<td>{application.projectName || currentProjectName || '-'}</td><td>{application.applicationType || application.type || 'PROJECT_ACCESS'}</td><td>{formatDate(application.createdAt || application.applyTime || application.createTime)}</td><td><div className="system-row-actions"><button onClick={() => approveProjectApplication(application)}>批准</button><button className="danger" onClick={() => rejectProjectApplication(application)}>驳回</button></div></td></tr>)}</tbody></table>{!projectApplications.length && <Empty text="暂无待审核项目访问申请" />}</div>
    </>
  );

  const renderWechat = () => (
    <>
      <PageBar title="微信绑定" description="管理系统用户与小程序微信身份的一对一绑定；平台管理员查看全部项目范围，页面不展示 OpenID 或 UnionID。"><SearchBar value={keyword} onChange={setKeyword} placeholder="姓名、账号或手机号" onSearch={runSearch} /></PageBar>
      <div className="system-table-wrap"><table><thead><tr><th>用户</th><th>手机号</th><th>绑定时间</th><th>微信登录</th><th>项目数</th><th>操作</th></tr></thead>
        <tbody>{rows.map((user) => {
          const bindingStatus = user.bindingStatus || 'UNBOUND';
          return <tr key={user.bindingId || user.userId || getId(user)}><td><strong>{user.realName || '-'}</strong><small>{user.username || '-'}</small></td><td>{user.phone || '-'}</td><td>{formatDate(user.bindTime)}</td><td><StatusTag status={bindingStatus} /></td><td>{user.projectCount ?? '-'}</td><td><div className="system-row-actions">{user.bindingId && bindingStatus !== 'UNBOUND' ? <><button onClick={() => toggleWechat(user)}>{bindingStatus === 'ACTIVE' ? '停用登录' : '恢复登录'}</button><button className="danger" onClick={() => unbindWechat(user)}>解绑</button></> : <span className="system-hint">未绑定</span>}</div></td></tr>;
        })}</tbody></table></div>
    </>
  );

  const renderAudit = () => (
    <>
      <PageBar title="操作日志" description="查看账号、权限、项目授权及微信绑定等关键管理操作。"><SearchBar value={keyword} onChange={setKeyword} placeholder="操作人、对象或动作" onSearch={runSearch} /></PageBar>
      <div className="system-table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>模块</th><th>动作</th><th>对象</th><th>结果</th><th>说明</th></tr></thead>
        <tbody>{rows.map((log, index) => <tr key={getId(log) || index}><td>{formatDate(log.createdAt || log.operationTime || log.createTime)}</td><td>{log.operatorName || log.operatorUsername || log.username || '-'}</td><td>{log.moduleName || log.module || log.businessType || '-'}</td><td>{log.actionName || log.action || log.operationType || '-'}</td><td>{log.targetName || log.targetType || (log.businessId ? `#${log.businessId}` : '-')}</td><td><StatusTag status={log.result || log.status || 'SUCCESS'} /></td><td>{log.description || log.detail || log.operationDesc || '-'}</td></tr>)}</tbody></table></div>
    </>
  );

  const renderContent = () => {
    if (loading) return <Loading />;
    if (error) return <ErrorState text={error} onRetry={loadData} />;
    let content;
    if (activeTab === 'registration') content = renderRegistration();
    else if (activeTab === 'users') content = renderUsers();
    else if (activeTab === 'roles') content = renderRoles();
    else if (activeTab === 'menus') content = renderMenus();
    else if (activeTab === 'projects') content = renderProjects();
    else if (activeTab === 'wechat') content = renderWechat();
    else if (activeTab === 'audit') content = renderAudit();
    else content = <Empty text="无可访问的系统管理模块" />;
    return <>{content}{!rows.length && activeTab !== 'projects' && <Empty />}{<Pagination pageNo={pageNo} pageSize={pageSize} total={total} onPageChange={setPageNo} />}</>;
  };

  return (
    <div className="system-management">
      <aside className="system-sidebar">
        <nav>{availableTabs.map((tab) => <button key={tab.id} className={activeTab === tab.id ? 'active' : ''} onClick={() => selectTab(tab.id)}><span>{tab.label}</span><small>{tab.code}</small></button>)}</nav>
        <button className="system-back-button" onClick={onBack}>← 返回业务工作台</button>
      </aside>
      <section className="system-content">{availableTabs.length ? renderContent() : <ErrorState text="当前账号没有系统管理权限" onRetry={onBack} />}</section>
      {reviewing && <ReviewDialog application={reviewing} roles={roles} permissions={permissions} projectList={projectList} onClose={() => setReviewing(null)} onApproved={async () => { setReviewing(null); await loadData(); }} />}
      {roleDefinitionDialog !== undefined && <RoleDefinitionDialog role={roleDefinitionDialog} onClose={() => setRoleDefinitionDialog(undefined)} onSave={saveRoleDefinition} />}
      {roleMenuDialog && <MenuAssignmentDialog role={roleMenuDialog} menus={menus} selectedMenuIds={(roleMenuDialog.menuIds || []).filter((id) => id !== undefined)} businessModuleCodes={deriveRoleBusinessModuleCodes(roleMenuDialog, menus)} onClose={() => setRoleMenuDialog(null)} onSave={(values) => saveRoleMenus(roleMenuDialog, values)} />}
      {rolePermissionDialog && <PermissionAssignmentDialog role={rolePermissionDialog} menus={menus} permissions={roleAssignablePermissions(rolePermissionDialog, permissions)} selectedMenuIds={(rolePermissionDialog.menuIds || []).filter((id) => id !== undefined)} businessModuleCodes={deriveRoleBusinessModuleCodes(rolePermissionDialog, menus)} selectedPermissionIds={rolePermissionIdsFor(rolePermissionDialog)} onClose={() => setRolePermissionDialog(null)} onSave={(values) => saveRoleOperationPermissions(rolePermissionDialog, values)} />}
      {userProjectAccessDialog && (
        <UserProjectAccessDialog
          key={getId(userProjectAccessDialog)}
          user={userProjectAccessDialog}
          projectList={projectList}
          projectRoles={projectRoleOptions}
          onClose={() => setUserProjectAccessDialog(null)}
          onSave={(values) => saveUserProjectAccess(userProjectAccessDialog, values)}
          onStatusChange={(projectId, values) => updateUserProjectAccessStatus(userProjectAccessDialog, projectId, values)}
          onChanged={loadData}
        />
      )}
      {projectAuthorizationDialog && (
        <ProjectAuthorizationDialog
          key={`${projectAuthorizationDialog.mode}-${projectAuthorizationDialog.subject?.id || projectAuthorizationDialog.subject?.userId || 'new'}`}
          mode={projectAuthorizationDialog.mode}
          projectName={currentProjectName || currentProject}
          subject={projectAuthorizationDialog.subject}
          userOptions={projectAuthorizationDialog.userOptions}
          projectRoles={projectRoleOptions}
          permissions={permissions}
          onSearchUsers={projectAuthorizationDialog.mode === 'add' ? async (searchKeyword) => {
            const res = await getProjectUserOptions(currentProject, searchKeyword || undefined);
            if (res.code !== 200) throw new Error(res.message || '可选用户加载失败');
            return projectAuthorizationDialog.filterCandidates(extractList(res.data));
          } : undefined}
          onClose={() => setProjectAuthorizationDialog(null)}
          onSubmit={submitProjectAuthorization}
        />
      )}
    </div>
  );
}
