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
  updateSystemRolePermissions,
  updateSystemUserStatus,
  updateSystemWechatBindingStatus,
} from '../../services/systemManagement';
import {
  getProjectMembers,
  getAssignableProjectRoles,
  getProjectUserOptions,
  removeProjectMember,
  saveProjectMember,
  updateProjectMember,
  updateProjectMemberStatus,
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

const BUSINESS_MODULES = [
  { code: 'DOCUMENT', label: '资料管理', description: 'Web 端与小程序端同步显示', menuCodes: ['WEB_DOCUMENT', 'MINI_DOCUMENT'] },
  { code: 'INSPECTION', label: '巡检管理', description: 'Web 端与小程序端同步显示', menuCodes: ['WEB_INSPECTION', 'MINI_INSPECTION'] },
  { code: 'QUALITY', label: '质量管理', description: 'Web 端与小程序端同步显示', menuCodes: ['WEB_QUALITY', 'MINI_QUALITY'] },
];

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

const businessModuleByPermission = (permission) => {
  const code = String(permission?.permissionCode || permission?.code || '').toUpperCase();
  if (code.startsWith('DOCUMENT.')) return 'DOCUMENT';
  if (code.startsWith('QUALITY.')) return 'QUALITY';
  if (code.startsWith('INSPECTION.') || code.startsWith('BOX_') || code.startsWith('INSPECTION_')
    || code.startsWith('SUMMARY_') || code.startsWith('RECTIFICATION_')) return 'INSPECTION';
  return null;
};

const deriveRoleBusinessModuleCodes = (role, menuItems) => {
  if (Array.isArray(role?.businessModuleCodes)) return role.businessModuleCodes;
  const assigned = new Set(role?.menuIds || []);
  return BUSINESS_MODULES.filter((module) => menuItems.some((menu) => assigned.has(getId(menu))
    && businessModuleByMenu(menu) === module.code)).map((module) => module.code);
};

const roleAssignableMenus = (role, items) => {
  const withoutBusinessMenus = items.filter((menu) => !businessModuleByMenu(menu));
  if (!isProjectRole(role)) return withoutBusinessMenus;
  const isManager = Number(role?.projectManagerRole || 0) === 1;
  return withoutBusinessMenus.filter((menu) => {
    const code = String(menu.menuCode || menu.code || '').toUpperCase();
    return !code.startsWith('SYSTEM_') || (isManager && (code === 'SYSTEM_PROJECT' || code === 'WEB_SYSTEM'));
  });
};

const roleAssignablePermissions = (role, items) => {
  if (!isProjectRole(role)) return items;
  const isManager = Number(role?.projectManagerRole || 0) === 1;
  return items.filter((permission) => {
    const code = String(permission.permissionCode || permission.code || '');
    return !code.startsWith('system.') && (code !== 'project.member.manage' || isManager);
  });
};

const rolePermissionIds = (role) => (Array.isArray(role?.permissionIds)
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
            : rolePermissionIds(role)
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
  permissions,
  onClose,
  onSave,
  onRemove,
  onStatusChange,
  onChanged,
}) {
  const initialAssignments = Array.isArray(user?.projectRoles) ? user.projectRoles : [];
  const initialProjectId = initialAssignments[0]?.projectId || projectList[0]?.id || '';
  const initialRoleIds = projectAssignmentRoles(initialAssignments[0])
    .map((role) => Number(getId(role))).filter(Number.isFinite);
  const [assignments, setAssignments] = useState(initialAssignments);
  const [selectedProjectId, setSelectedProjectId] = useState(String(initialProjectId));
  const [roleIds, setRoleIds] = useState(initialRoleIds);
  const [projectKeyword, setProjectKeyword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const selectableProjects = useMemo(() => {
    const byId = new Map(projectList.map((project) => [Number(project.id), project]));
    assignments.forEach((assignment) => {
      const id = Number(assignment.projectId);
      if (Number.isFinite(id) && !byId.has(id)) {
        byId.set(id, {
          id,
          projectName: projectAssignmentName(assignment, projectList),
          shortName: assignment.shortName,
        });
      }
    });
    return Array.from(byId.values());
  }, [assignments, projectList]);

  const selectedAssignment = assignments.find((assignment) => Number(assignment.projectId) === Number(selectedProjectId));
  const selectedProjectName = projectAssignmentName(selectedAssignment || {
    projectId: selectedProjectId,
  }, selectableProjects);
  const visibleAssignments = useMemo(() => {
    const keyword = projectKeyword.trim().toLowerCase();
    if (!keyword) return assignments;
    return assignments.filter((assignment) => [
      projectAssignmentName(assignment, selectableProjects),
      ...projectAssignmentRoleNames(assignment),
    ].some((value) => String(value || '').toLowerCase().includes(keyword)));
  }, [assignments, projectKeyword, selectableProjects]);
  const unassignedProjects = useMemo(() => selectableProjects.filter((project) => !assignments
    .some((assignment) => Number(assignment.projectId) === Number(project.id))), [assignments, selectableProjects]);

  const changeProject = (value) => {
    const assignment = assignments.find((item) => Number(item.projectId) === Number(value));
    setSelectedProjectId(value);
    setRoleIds(projectAssignmentRoles(assignment).map((role) => Number(getId(role))).filter(Number.isFinite));
  };

  const startAddProject = () => {
    const project = unassignedProjects[0];
    if (!project) {
      alert('该用户已加入全部可选项目');
      return;
    }
    changeProject(String(project.id));
  };

  const save = async () => {
    if (!selectedProjectId) {
      alert('请选择项目');
      return;
    }
    if (!roleIds.length) {
      alert('请至少选择一个项目角色');
      return;
    }
    setSubmitting(true);
    try {
      const saved = await onSave({
        projectId: Number(selectedProjectId),
        roleIds,
        existing: Boolean(selectedAssignment),
      });
      const selectedRoles = projectRoles.filter((role) => roleIds.includes(Number(getId(role))));
      const nextAssignment = {
        ...selectedAssignment,
        ...(saved || {}),
        projectId: Number(selectedProjectId),
        projectName: selectedAssignment?.projectName || selectedProjectName,
        projectRoles: saved?.projectRoles || selectedRoles,
        accessStatus: saved?.accessStatus || selectedAssignment?.accessStatus || 'ACTIVE',
      };
      setAssignments((current) => {
        const rest = current.filter((item) => Number(item.projectId) !== Number(selectedProjectId));
        return [...rest, nextAssignment].sort((left, right) => Number(left.projectId) - Number(right.projectId));
      });
      await onChanged?.();
    } catch (error) {
      alert(error.message || '项目与角色保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  const remove = async (assignment = selectedAssignment) => {
    if (!assignment) return;
    const projectId = Number(assignment.projectId);
    const projectName = projectAssignmentName(assignment, selectableProjects);
    if (!window.confirm(`确认移出“${projectName}”？该用户在此项目的所有角色将一并移除。`)) return;
    setSubmitting(true);
    try {
      await onRemove(projectId);
      const remaining = assignments.filter((item) => Number(item.projectId) !== projectId);
      setAssignments(remaining);
      const nextProjectId = remaining[0]?.projectId
        || selectableProjects.find((project) => Number(project.id) !== projectId)?.id || '';
      const nextAssignment = remaining.find((item) => Number(item.projectId) === Number(nextProjectId));
      setSelectedProjectId(String(nextProjectId));
      setRoleIds(projectAssignmentRoles(nextAssignment).map((role) => Number(getId(role))).filter(Number.isFinite));
      await onChanged?.();
    } catch (error) {
      alert(error.message || '移出项目失败');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleAccess = async (assignment) => {
    const active = String(assignment?.accessStatus || 'ACTIVE').toUpperCase() !== 'DISABLED';
    const projectId = Number(assignment?.projectId);
    const reason = window.prompt(active ? '请输入暂停项目访问原因' : '请输入恢复说明', active ? '管理员暂停项目访问' : '');
    if (reason === null || (active && !reason.trim())) return;
    setSubmitting(true);
    try {
      const saved = await onStatusChange(projectId, {
        status: active ? 'DISABLED' : 'ACTIVE',
        reason,
      });
      setAssignments((current) => current.map((item) => (Number(item.projectId) === projectId ? {
        ...item,
        ...(saved || {}),
        accessStatus: saved?.accessStatus || (active ? 'DISABLED' : 'ACTIVE'),
        statusReason: saved?.statusReason ?? reason,
      } : item)));
      await onChanged?.();
    } catch (error) {
      alert(error.message || '项目访问状态更新失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="system-modal-overlay system-user-project-access-overlay" onClick={onClose}>
      <div className="system-modal system-user-project-access-modal" onClick={(event) => event.stopPropagation()}>
        <PageBar title="项目与角色详情" description={`${user?.realName || user?.username || '当前用户'} · 项目角色仅在对应项目内生效`}>
          <div className="system-page-actions">
            <button className="primary" onClick={startAddProject} disabled={!unassignedProjects.length}>加入项目</button>
            <button className="plain" onClick={onClose}>关闭</button>
          </div>
        </PageBar>
        <div className="system-modal-body">
          <div className="system-project-auth-summary">
            <strong>{user?.realName || user?.username || '-'}</strong>
            <span>{user?.username || '-'}</span>
          </div>
          <div className="system-user-project-current">
            <div className="system-user-project-list-header">
              <strong>已分配项目与角色</strong>
              <span>{assignments.length} 个项目</span>
            </div>
            {assignments.length ? <>
              <div className="system-user-project-search">
                <input value={projectKeyword} onChange={(event) => setProjectKeyword(event.target.value)} placeholder="搜索项目或角色" />
                <span>显示 {visibleAssignments.length} 个</span>
              </div>
              <div className="system-user-project-list">{visibleAssignments.map((assignment) => (
                <article key={assignment.projectId} className={Number(selectedProjectId) === Number(assignment.projectId) ? 'active' : ''}>
                  <button type="button" className="system-user-project-select" onClick={() => changeProject(String(assignment.projectId))}>
                    <strong>{projectAssignmentName(assignment, selectableProjects)}</strong>
                    <small>{projectAssignmentRoleNames(assignment).join('、') || '未分配角色'} · {assignment.accessStatus === 'DISABLED' ? '访问已暂停' : '访问启用'}</small>
                  </button>
                  <div className="system-user-project-list-actions">
                    <button type="button" onClick={() => changeProject(String(assignment.projectId))}>调整角色</button>
                    <button type="button" className={assignment.accessStatus === 'DISABLED' ? '' : 'danger'} disabled={submitting} onClick={() => toggleAccess(assignment)}>{assignment.accessStatus === 'DISABLED' ? '恢复访问' : '暂停访问'}</button>
                    <button type="button" className="danger" disabled={submitting} onClick={() => remove(assignment)}>移出</button>
                  </div>
                </article>
              ))}</div>
              {!visibleAssignments.length && <span className="system-hint">未找到匹配的项目或角色</span>}
            </> : <span className="system-hint">该用户尚未加入任何项目，请使用右上角“加入项目”。</span>}
          </div>
          <section className="system-user-project-editor">
            <div className="system-user-project-editor-title">
              <div>
                <strong>项目与角色配置</strong>
                <small>先选择项目，再为该项目勾选一个或多个角色</small>
              </div>
              <span className={!selectedAssignment ? 'pending' : selectedAssignment.accessStatus === 'DISABLED' ? 'disabled' : ''}>
                {selectedAssignment ? (selectedAssignment.accessStatus === 'DISABLED' ? '访问已暂停' : '访问启用') : '待加入项目'}
              </span>
            </div>
            <label className="system-form-field system-user-project-picker">
              <span className="system-user-project-picker-label">
                <strong>选择项目 *</strong>
                <small>{selectedAssignment ? '当前项目已加入，可在下方调整角色' : '该项目尚未加入，保存后建立项目关系'}</small>
              </span>
              <select value={selectedProjectId} onChange={(event) => changeProject(event.target.value)}>
                <option value="">请选择需要配置的项目</option>
                {selectableProjects.map((project) => <option key={project.id} value={project.id}>{project.projectName || project.shortName || `项目 ${project.id}`}</option>)}
              </select>
            </label>
            <fieldset className="system-project-role-selector">
              <legend>{selectedProjectName} · 项目角色 *</legend>
              <RoleSelector roles={projectRoles} selectedRoleIds={roleIds} onChange={setRoleIds} permissions={permissions} emptyText="暂无启用的项目角色" />
            </fieldset>
          </section>
          <div className="system-project-auth-help">
            资料、巡检和质量的菜单与操作权限按当前项目内的多个角色并集合并。此处不提供平台全局身份分配。
          </div>
        </div>
        <div className="system-modal-footer">
          <span className="system-hint">{selectedAssignment ? `正在调整：${selectedProjectName}` : '选择项目和角色后保存即可加入项目'}</span>
          <button className="plain" disabled={submitting} onClick={onClose}>关闭</button>
          <button className="primary" disabled={submitting || !projectRoles.length} onClick={save}>{submitting ? '保存中…' : selectedAssignment ? '保存项目角色' : '加入项目并保存角色'}</button>
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
  const [pageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [roles, setRoles] = useState([]);
  const [permissions, setPermissions] = useState([]);
  const [menus, setMenus] = useState([]);
  const [selectedRole, setSelectedRole] = useState(null);
  const [rolePermissionIds, setRolePermissionIds] = useState([]);
  const [roleMenuIds, setRoleMenuIds] = useState([]);
  const [roleBusinessModuleCodes, setRoleBusinessModuleCodes] = useState([]);
  const [reviewing, setReviewing] = useState(null);
  const [projectApplications, setProjectApplications] = useState([]);
  const [projectAuthorizationDialog, setProjectAuthorizationDialog] = useState(null);
  const [userProjectAccessDialog, setUserProjectAccessDialog] = useState(null);
  const requestSequenceRef = useRef(0);
  const projectRoleOptions = useMemo(() => {
    return roles.filter((role) => isProjectRole(role) && isEnabledValue(role.enabled ?? 1));
  }, [roles]);

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
          getProjectMembers(currentProject),
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
      const isClientPaged = ['roles', 'menus', 'projects'].includes(activeTab);
      const visibleList = isClientPaged ? list.slice((effectivePage - 1) * pageSize, effectivePage * pageSize) : list;
      setRows(visibleList);
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
  }, [activeTab, currentProject, currentUser, keyword, loadRolesAndPermissions, pageNo, pageSize, status]);

  useEffect(() => {
    setKeyword('');
    setStatus('');
    setPageNo(1);
  }, [activeTab]);

  useEffect(() => {
    loadData();
  }, [activeTab, currentProject, pageNo, pageSize]);

  useEffect(() => {
    if (!selectedRole) {
      setRolePermissionIds([]);
      setRoleMenuIds([]);
      setRoleBusinessModuleCodes([]);
      return;
    }
    const ids = selectedRole.permissionIds
      || (selectedRole.permissions || []).map(getId)
      || [];
    setRolePermissionIds(ids.filter((id) => id !== undefined));
    setRoleMenuIds((selectedRole.menuIds || []).filter((id) => id !== undefined));
    setRoleBusinessModuleCodes(deriveRoleBusinessModuleCodes(selectedRole, menus));
  }, [menus, selectedRole]);

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
    const res = values.existing
      ? await updateProjectMember(values.projectId, getId(user), { roleIds: values.roleIds })
      : await saveProjectMember({ projectId: values.projectId, userId: getId(user), roleIds: values.roleIds });
    if (!res || res.code !== 200) throw new Error(res?.message || '项目与角色保存失败');
    return res.data;
  };

  const removeUserProjectAccess = async (user, projectId) => {
    const res = await removeProjectMember(projectId, getId(user));
    if (!res || res.code !== 200) throw new Error(res?.message || '移出项目失败');
  };

  const updateUserProjectAccessStatus = async (user, projectId, values) => {
    const res = await updateProjectMemberStatus(projectId, getId(user), values);
    if (!res || res.code !== 200) throw new Error(res?.message || '项目访问状态更新失败');
    return res.data;
  };

  const saveRolePermissions = async () => {
    if (!selectedRole) return;
    try {
      const assignablePermissionIds = new Set(roleAssignablePermissions(selectedRole, permissions).map(getId));
      const assignableMenuIds = new Set(roleAssignableMenus(selectedRole, menus).map(getId));
      const res = await updateSystemRolePermissions(getId(selectedRole), {
        permissionIds: rolePermissionIds.filter((id) => assignablePermissionIds.has(id)),
        menuIds: roleMenuIds.filter((id) => assignableMenuIds.has(id)),
        businessModuleCodes: roleBusinessModuleCodes,
      });
      if (res.code !== 200) throw new Error(res.message || '角色权限保存失败');
      alert('角色权限已保存，相关用户会话将按后端策略刷新');
      await loadData();
    } catch (err) {
      alert(err.message || '角色权限保存失败');
    }
  };

  const editRoleDefinition = async (role = null) => {
    const roleName = window.prompt('角色名称', role?.roleName || role?.name || '');
    if (roleName === null || !roleName.trim()) return;
    const roleCode = window.prompt('角色编码（大写字母、数字和下划线）', role?.roleCode || role?.code || '');
    if (roleCode === null || !roleCode.trim()) return;
    const scopeType = role?.scopeType || role?.scope || 'PROJECT';
    const description = window.prompt('角色说明（可留空）', role?.description || '');
    if (description === null) return;
    const normalizedScope = scopeType.trim().toUpperCase();
    const roleWithRequestedScope = { ...role, scopeType: normalizedScope };
    const assignableMenuIds = new Set(roleAssignableMenus(roleWithRequestedScope, menus).map(getId));
    const assignablePermissionIds = new Set(roleAssignablePermissions(roleWithRequestedScope, permissions).map(getId));
    const payload = {
      roleName: roleName.trim(),
      roleCode: roleCode.trim().toUpperCase(),
      scopeType: normalizedScope,
      description: description.trim(),
      enabled: Number(role?.enabled ?? 1),
      menuIds: role ? roleMenuIds.filter((id) => assignableMenuIds.has(id)) : [],
      permissionIds: role ? rolePermissionIds.filter((id) => assignablePermissionIds.has(id)) : [],
      businessModuleCodes: role ? roleBusinessModuleCodes : [],
    };
    try {
      const res = role ? await updateSystemRole(getId(role), payload) : await createSystemRole(payload);
      if (res.code !== 200) throw new Error(res.message || '角色保存失败');
      await loadData();
    } catch (err) {
      alert(err.message || '角色保存失败');
    }
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

  const removeRoleDefinition = async () => {
    if (!selectedRole) return;
    if (!window.confirm(`确认删除角色“${selectedRole.roleName || selectedRole.name}”？`)) return;
    try {
      const res = await deleteSystemRole(getId(selectedRole));
      if (res.code !== 200) throw new Error(res.message || '角色删除失败');
      setSelectedRole(null);
      await loadData();
    } catch (err) {
      alert(err.message || '角色删除失败');
    }
  };

  const toggleProjectMember = async (member) => {
    const active = (member.accessStatus || member.status || 'ACTIVE') === 'ACTIVE';
    const reason = window.prompt(active ? '请输入暂停项目访问原因' : '请输入恢复说明', active ? '管理员暂停项目访问' : '');
    if (reason === null || (active && !reason.trim())) return;
    try {
      const res = await updateProjectMemberStatus(currentProject, member.userId, { status: active ? 'DISABLED' : 'ACTIVE', reason });
      if (res.code !== 200) throw new Error(res.message || '项目授权更新失败');
      await loadData();
    } catch (err) {
      alert(err.message || '项目授权更新失败');
    }
  };

  const submitProjectAuthorization = async (values) => {
    if (!projectAuthorizationDialog) return;
    const { mode, subject } = projectAuthorizationDialog;
    let res;
    if (mode === 'add') {
      res = await saveProjectMember({
        projectId: currentProject,
        userId: values.userId,
        roleIds: values.roleIds,
      });
    } else if (mode === 'edit') {
      res = await updateProjectMember(currentProject, subject.userId, {
        roleIds: values.roleIds,
      });
    } else if (mode === 'approve') {
      res = await approveWechatAccessApplication(subject.id, {
        accountMode: 'EXISTING',
        userId: values.userId,
        projectId: Number(subject.projectId || currentProject),
        roleIds: values.roleIds,
        comment: values.comment,
      });
    }
    if (!res || res.code !== 200) {
      const fallback = mode === 'approve' ? '项目访问申请审批失败' : mode === 'add' ? '新增项目成员失败' : '项目授权调整失败';
      throw new Error(res?.message || fallback);
    }
    await loadData();
  };

  const addProjectMember = async () => {
    try {
      const optionRes = await getProjectUserOptions(currentProject, '');
      if (optionRes.code !== 200) throw new Error(optionRes.message || '可选用户加载失败');
      const filterCandidates = (items) => extractList(items).filter((option) => !rows
        .some((member) => Number(member.userId) === Number(option.userId || option.id)));
      const options = filterCandidates(optionRes.data);
      if (!options.length) {
        alert('当前没有可新增的系统用户');
        return;
      }
      setProjectAuthorizationDialog({ mode: 'add', subject: {}, userOptions: options, filterCandidates });
    } catch (err) {
      alert(err.message || '新增项目成员失败');
    }
  };

  const editProjectMember = (member) => {
    setProjectAuthorizationDialog({ mode: 'edit', subject: member, userOptions: [] });
  };

  const deleteProjectMember = async (member) => {
    if (!window.confirm(`确认移除 ${member.realName || member.username} 的当前项目访问权限？`)) return;
    try {
      const res = await removeProjectMember(currentProject, member.userId);
      if (res.code !== 200) throw new Error(res.message || '移除项目成员失败');
      await loadData();
    } catch (err) {
      alert(err.message || '移除项目成员失败');
    }
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
          const activeProjectCount = assignments.filter((assignment) => assignment.accessStatus !== 'DISABLED').length;
          const disabledProjectCount = assignments.length - activeProjectCount;
          return <tr key={getId(user)}>
            <td><strong>{user.realName || '-'}</strong><small>{user.username}</small></td><td>{user.phone || '-'}<small>{user.email || '-'}</small></td>
            <td className="system-user-project-cell"><button type="button" className={`system-user-project-collection${assignments.length ? '' : ' empty'}`} onClick={() => openUserProjectAccess(user)}><span className="system-user-project-collection-icon" aria-hidden="true">项</span><span className="system-user-project-collection-content"><strong>{assignments.length ? `${assignments.length} 个项目` : '尚未分配项目'}</strong><small>{assignments.length ? `启用 ${activeProjectCount} · 暂停 ${disabledProjectCount}` : '点击添加项目与角色'}</small></span><span className="system-user-project-collection-action">{assignments.length ? '查看详情' : '立即分配'}<b aria-hidden="true">›</b></span></button></td>
            <td>{!isEnabledValue(user.passwordLoginEnabled) ? '仅微信' : user.wechatBound ? '密码 + 微信' : '账号密码'}</td><td><StatusTag status={user.status} /></td><td>{formatDate(user.lastLoginAt || user.createTime)}</td>
            <td><div className="system-row-actions">{hasPermission(currentUser, 'system.user.manage') && <button className="primary" onClick={() => openUserProjectAccess(user)}>分配项目与角色</button>}{hasPermission(currentUser, 'system.user.reset_password') && <button onClick={() => resetPassword(user)}>重置密码</button>}{hasPermission(currentUser, 'system.user.status') && <button className={isEnabledValue(user.status) ? 'danger' : ''} onClick={() => toggleUserStatus(user)}>{isEnabledValue(user.status) ? '停用' : '启用'}</button>}{!hasPermission(currentUser, 'system.user.manage', 'system.user.reset_password', 'system.user.status') && <span className="system-hint">只读</span>}</div></td>
          </tr>;
        })}</tbody></table></div>
    </>
  );

  const renderRoles = () => {
    const assignableMenus = roleAssignableMenus(selectedRole, menus);
    const assignablePermissions = roleAssignablePermissions(selectedRole, permissions);
    const assignableMenuIds = new Set(assignableMenus.map(getId));
    const assignablePermissionIds = new Set(assignablePermissions.map(getId));
    const managementPermissions = assignablePermissions.filter((permission) => !businessModuleByPermission(permission));
    return (
      <>
      <PageBar title="角色与权限" description="系统管理员预设项目角色；资料、巡检、质量和项目成员权限均由同一角色统一配置。">
        {hasPermission(currentUser, 'system.role.manage') && <button onClick={() => editRoleDefinition(null)}>新增角色</button>}
        {hasPermission(currentUser, 'system.role.manage') && <button onClick={() => editRoleDefinition(selectedRole)} disabled={!selectedRole}>编辑角色</button>}
        {hasPermission(currentUser, 'system.role.manage') && <button className="danger" onClick={removeRoleDefinition} disabled={!selectedRole}>删除角色</button>}
        {hasPermission(currentUser, 'system.role.manage') && <button className="primary" onClick={saveRolePermissions} disabled={!selectedRole}>保存权限配置</button>}
      </PageBar>
      <div className="system-role-layout">
        <div className="system-role-list">{rows.map((role) => <button key={getId(role)} className={getId(selectedRole) === getId(role) ? 'active' : ''} onClick={() => setSelectedRole(role)}><strong>{role.roleName || role.name}</strong><small>{role.roleCode || role.code} · {role.scopeType || role.scope || 'PLATFORM'}</small></button>)}</div>
        <div className="system-permission-panel">
          {!selectedRole ? <Empty text="请选择角色" /> : <>
            <div className="system-permission-title"><strong>{selectedRole.roleName || selectedRole.name}</strong><span>业务模块 {roleBusinessModuleCodes.length} 项 · 管理菜单 {roleMenuIds.filter((id) => assignableMenuIds.has(id)).length} 项 · 权限 {rolePermissionIds.filter((id) => assignablePermissionIds.has(id)).length} 项</span></div>
            <h3 className="system-group-title">业务模块</h3>
            <p className="system-module-hint">关闭模块后，该角色不再向资料、巡检或质量贡献 Web/小程序入口和操作权限；已勾选的细分权限会保留。若同一用户还有其他启用该模块的项目角色，权限会按并集继续生效。</p>
            <div className="system-permission-grid system-business-module-grid">{BUSINESS_MODULES.map((module) => <label key={module.code} className={roleBusinessModuleCodes.includes(module.code) ? 'module-enabled' : 'module-disabled'}><input type="checkbox" disabled={!hasPermission(currentUser, 'system.role.manage')} checked={roleBusinessModuleCodes.includes(module.code)} onChange={() => setRoleBusinessModuleCodes((current) => current.includes(module.code) ? current.filter((item) => item !== module.code) : [...current, module.code])} /><span>{module.label}<small>{module.description}</small></span></label>)}</div>
            {assignableMenus.length > 0 && <>
            <h3 className="system-group-title">管理菜单</h3>
            <div className="system-permission-grid">{assignableMenus.map((menu) => {
              const id = getId(menu);
              return <label key={`menu-${id}`}><input type="checkbox" disabled={!hasPermission(currentUser, 'system.role.manage')} checked={roleMenuIds.includes(id)} onChange={() => setRoleMenuIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])} /><span>{menu.menuName || menu.name}<small>系统管理页面入口</small></span></label>;
            })}</div>
            </>}
            <h3 className="system-group-title">操作权限</h3>
            {BUSINESS_MODULES.map((module) => {
              const modulePermissions = assignablePermissions.filter((permission) => businessModuleByPermission(permission) === module.code);
              if (!modulePermissions.length) return null;
              return <section className="system-operation-group" key={module.code}><div><strong>{module.label}操作</strong><small>{roleBusinessModuleCodes.includes(module.code) ? '已启用模块' : '模块关闭时不生效'}</small></div><div className="system-permission-grid">{modulePermissions.map((permission) => {
              const id = getId(permission);
              return <label key={id}><input type="checkbox" disabled={!hasPermission(currentUser, 'system.role.manage')} checked={rolePermissionIds.includes(id)} onChange={() => setRolePermissionIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])} /><span>{permission.permissionName || permission.name}<small>{permission.description || '模块内操作权限'}</small></span></label>;
              })}</div></section>;
            })}
            {managementPermissions.length > 0 && <section className="system-operation-group"><div><strong>项目成员管理</strong><small>仅项目经理角色可配置</small></div><div className="system-permission-grid">{managementPermissions.map((permission) => {
              const id = getId(permission);
              return <label key={id}><input type="checkbox" disabled={!hasPermission(currentUser, 'system.role.manage')} checked={rolePermissionIds.includes(id)} onChange={() => setRolePermissionIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])} /><span>{permission.permissionName || permission.name}<small>{permission.description || '管理操作权限'}</small></span></label>;
            })}</div></section>}
          </>}
        </div>
      </div>
      </>
    );
  };

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
      <PageBar title="项目成员与权限" description={`当前作业区域：${currentProjectName || currentProject || '-'}。角色在当前项目内生效，多个角色的菜单与操作权限自动合并。`}>
        <button className="primary" onClick={addProjectMember}>加入项目成员</button>
      </PageBar>
      <div className="system-table-wrap"><table><thead><tr><th>成员</th><th>账号</th><th>项目角色</th><th>有效巡检权限</th><th>状态</th><th>变更原因</th><th>操作</th></tr></thead>
        <tbody>{rows.map((member) => {
          const protectedManager = !isPlatformAdmin(currentUser) && (member.projectRoles || []).some((role) => Number(role.projectManagerRole || 0) === 1);
          return <tr key={member.userId}><td><strong>{member.realName || member.username}</strong></td><td>{member.username || '-'}</td><td>{(member.projectRoles || []).map((role) => role.roleName || role.roleCode).join('、') || '未分配'}</td><td>{(member.permissionCodes || []).join('、') || '-'}</td><td><StatusTag status={member.accessStatus || member.status || 'ACTIVE'} /></td><td>{member.statusReason || '-'}</td><td>{protectedManager ? <span className="system-hint">项目经理仅系统管理员可调整</span> : <div className="system-row-actions"><button onClick={() => editProjectMember(member)}>调整角色</button><button className={(member.accessStatus || member.status) === 'DISABLED' ? '' : 'danger'} onClick={() => toggleProjectMember(member)}>{(member.accessStatus || member.status) === 'DISABLED' ? '恢复访问' : '暂停访问'}</button><button className="danger" onClick={() => deleteProjectMember(member)}>移出项目</button></div>}</td></tr>;
        })}</tbody></table></div>
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
        <nav>{availableTabs.map((tab) => <button key={tab.id} className={activeTab === tab.id ? 'active' : ''} onClick={() => setActiveTab(tab.id)}><span>{tab.label}</span><small>{tab.code}</small></button>)}</nav>
        <button className="system-back-button" onClick={onBack}>← 返回业务工作台</button>
      </aside>
      <section className="system-content">{availableTabs.length ? renderContent() : <ErrorState text="当前账号没有系统管理权限" onRetry={onBack} />}</section>
      {reviewing && <ReviewDialog application={reviewing} roles={roles} permissions={permissions} projectList={projectList} onClose={() => setReviewing(null)} onApproved={async () => { setReviewing(null); await loadData(); }} />}
      {userProjectAccessDialog && (
        <UserProjectAccessDialog
          key={getId(userProjectAccessDialog)}
          user={userProjectAccessDialog}
          projectList={projectList}
          projectRoles={projectRoleOptions}
          permissions={permissions}
          onClose={() => setUserProjectAccessDialog(null)}
          onSave={(values) => saveUserProjectAccess(userProjectAccessDialog, values)}
          onRemove={(projectId) => removeUserProjectAccess(userProjectAccessDialog, projectId)}
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
