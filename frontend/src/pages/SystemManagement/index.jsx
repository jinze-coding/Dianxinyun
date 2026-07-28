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
  updateSystemUserRoles,
  updateSystemUserStatus,
  updateSystemWechatBindingStatus,
} from '../../services/systemManagement';
import {
  getProjectMembers,
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
  createInspectionPermissionTemplate,
  getInspectionPermissionCatalog,
  getInspectionPermissionTemplates,
  updateInspectionPermissionTemplate,
  updateInspectionPermissionTemplateStatus,
} from '../../services/inspectionPermissionTemplates';
import {
  hasAssignedMenu,
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
  { id: 'projects', label: '项目授权', code: 'SYSTEM_PROJECT', permissions: ['system.project.manage'] },
  { id: 'wechat', label: '微信绑定', code: 'SYSTEM_WECHAT', permissions: ['system.wechat.manage'] },
  { id: 'audit', label: '操作日志', code: 'SYSTEM_AUDIT', permissions: ['system.audit.view'] },
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

const roleAssignableMenus = (role, items) => {
  if (!isProjectRole(role)) return items;
  return items.filter((menu) => {
    const code = String(menu.menuCode || menu.code || '').toUpperCase();
    return !code.startsWith('SYSTEM_') || code === 'SYSTEM_PROJECT' || code === 'WEB_SYSTEM';
  });
};

const roleAssignablePermissions = (role, items) => {
  if (!isProjectRole(role)) return items;
  return items.filter((permission) => {
    const code = String(permission.permissionCode || permission.code || '').toLowerCase();
    return !code.startsWith('system.') || code === 'system.project.manage';
  });
};

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

function ReviewDialog({ application, roles, projectList, onClose, onApproved }) {
  const [roleIds, setRoleIds] = useState([]);
  const [projectAssignments, setProjectAssignments] = useState({});
  const [reviewComment, setReviewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const globalRoles = roles.filter((role) => String(role.scopeType || role.scope || 'PLATFORM').toUpperCase() !== 'PROJECT');
  const projectRoles = roles.filter((role) => String(role.scopeType || role.scope || '').toUpperCase() === 'PROJECT');

  const toggle = (id, setter) => setter((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  const toggleProjectRole = (projectId, roleId) => {
    setProjectAssignments((current) => {
      const selected = current[projectId] || [];
      return {
        ...current,
        [projectId]: selected.includes(roleId) ? [] : [roleId],
      };
    });
  };

  const approve = async () => {
    const assignments = Object.entries(projectAssignments)
      .filter(([, ids]) => ids.length)
      .map(([projectId, ids]) => ({ projectId: Number(projectId), roleIds: ids }));
    if (!roleIds.length && !assignments.length) {
      alert('请至少分配一个平台角色或项目角色');
      return;
    }
    if (!reviewComment.trim()) {
      alert('请填写审批意见');
      return;
    }
    setSubmitting(true);
    try {
      const res = await approveSystemRegistrationApplication(application.id, {
        roleIds,
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
            <legend>平台角色</legend>
            <div className="system-checkbox-grid">
              {globalRoles.map((role) => (
                <label key={getId(role)}>
                  <input type="checkbox" checked={roleIds.includes(getId(role))} onChange={() => toggle(getId(role), setRoleIds)} />
                  <span>{role.roleName || role.name}<small>{role.roleCode || role.code}</small></span>
                </label>
              ))}
              {!globalRoles.length && <span className="system-hint">暂无可分配的平台角色</span>}
            </div>
          </fieldset>
          <fieldset>
            <legend>项目角色（可多项目）</legend>
            <div className="system-desired-projects">
              申请意向：{(application.desiredProjectIds || []).length
                ? application.desiredProjectIds.map((id) => projectList.find((project) => Number(project.id) === Number(id))?.projectName || `项目 ${id}`).join('、')
                : '未指定项目'}
            </div>
            <div className="system-project-assignment-list">
              {projectList.map((project) => (
                <div key={project.id} className={(application.desiredProjectIds || []).map(Number).includes(Number(project.id)) ? 'desired' : ''}>
                  <strong>{project.projectName || project.shortName || `项目 ${project.id}`}</strong>
                  <div className="system-checkbox-grid">
                    {projectRoles.map((role) => (
                      <label key={`${project.id}-${getId(role)}`}>
                        <input type="radio" name={`project-role-${project.id}`} checked={(projectAssignments[project.id] || []).includes(getId(role))} onChange={() => toggleProjectRole(project.id, getId(role))} />
                        <span>{role.roleName || role.name}<small>{role.roleCode || role.code}</small></span>
                      </label>
                    ))}
                  </div>
                </div>
              ))}
              {!projectRoles.length && <span className="system-hint">暂无项目角色；可只分配平台角色</span>}
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

function InspectionTemplateDialog({ template, catalog, onClose, onSaved }) {
  const editing = Boolean(template?.id);
  const builtin = Number(template?.builtin || 0) === 1;
  const [form, setForm] = useState({
    templateName: template?.templateName || '',
    templateCode: template?.templateCode || '',
    description: template?.description || '',
    permissionCodes: template?.permissionCodes || [],
    enabled: isEnabledValue(template?.enabled ?? 1) ? 1 : 0,
  });
  const [submitting, setSubmitting] = useState(false);

  const togglePermission = (code) => {
    setForm((current) => ({
      ...current,
      permissionCodes: current.permissionCodes.includes(code)
        ? current.permissionCodes.filter((item) => item !== code)
        : [...current.permissionCodes, code],
    }));
  };

  const save = async () => {
    if (!form.templateName.trim()) {
      alert('请填写巡检权限角色名称');
      return;
    }
    if (!editing && !form.templateCode.trim()) {
      alert('请填写巡检权限角色编码');
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        templateName: form.templateName.trim(),
        templateCode: form.templateCode.trim().toUpperCase(),
        description: form.description.trim(),
        permissionCodes: form.permissionCodes,
        enabled: Number(form.enabled) === 1 ? 1 : 0,
      };
      const res = editing
        ? await updateInspectionPermissionTemplate(template.id, payload)
        : await createInspectionPermissionTemplate(payload);
      if (res.code !== 200) throw new Error(res.message || '巡检权限角色保存失败');
      await onSaved();
      onClose();
    } catch (err) {
      alert(err.message || '巡检权限角色保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="system-modal-overlay" onClick={onClose}>
      <div className="system-modal system-template-modal" onClick={(event) => event.stopPropagation()}>
        <PageBar
          title={editing ? '编辑巡检权限角色模板' : '新建巡检权限角色模板'}
          description="设置项目成员在电箱台账、巡检提交、记录查看和汇总导出中的细分权限。"
        >
          <button className="plain" onClick={onClose}>关闭</button>
        </PageBar>
        <div className="system-modal-body">
          <div className="system-template-form-grid">
            <label className="system-form-field">
              <span>模板名称 *</span>
              <input value={form.templateName} onChange={(event) => setForm({ ...form, templateName: event.target.value })} placeholder="如：外部检查只读" />
            </label>
            <label className="system-form-field">
              <span>模板编码 *</span>
              <input
                value={form.templateCode}
                disabled={editing}
                onChange={(event) => setForm({ ...form, templateCode: event.target.value })}
                placeholder="如：EXTERNAL_READONLY"
              />
            </label>
            <label className="system-form-field">
              <span>状态</span>
              <select
                value={form.enabled}
                disabled={editing && builtin}
                onChange={(event) => setForm({ ...form, enabled: Number(event.target.value) })}
              >
                <option value={1}>启用</option>
                <option value={0}>停用</option>
              </select>
            </label>
            <label className="system-form-field system-template-field-wide">
              <span>说明</span>
              <textarea rows="3" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} placeholder="说明这个模板适合哪些项目成员" />
            </label>
          </div>
          <div className="system-template-catalog">
            {catalog.map((group) => (
              <fieldset key={group.groupCode}>
                <legend>{group.groupName}</legend>
                <div className="system-template-permission-list">
                  {(group.items || []).map((item) => (
                    <label key={item.code}>
                      <input type="checkbox" checked={form.permissionCodes.includes(item.code)} onChange={() => togglePermission(item.code)} />
                      <span><strong>{item.name}</strong><small>{item.description || item.code}</small></span>
                    </label>
                  ))}
                </div>
              </fieldset>
            ))}
            {!catalog.length && <div className="system-hint">暂无可配置的巡检权限目录</div>}
          </div>
        </div>
        <div className="system-modal-footer system-template-modal-footer">
          <span>已选择 {form.permissionCodes.length} 项细分权限</span>
          <div>
            <button className="plain" disabled={submitting} onClick={onClose}>取消</button>
            <button className="primary" disabled={submitting} onClick={save}>{submitting ? '保存中…' : '保存模板'}</button>
          </div>
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
  templates,
  onClose,
  onSubmit,
}) {
  const roleCodes = projectRoles.map((role) => role.roleCode || role.code).filter(Boolean);
  const requestedRoleCode = String(
    subject?.projectRoleCode || subject?.requestedProjectRoleCode || 'USER',
  ).toUpperCase();
  const initialRoleCode = roleCodes.includes(requestedRoleCode)
    ? requestedRoleCode
    : (roleCodes.includes('USER') ? 'USER' : roleCodes[0] || '');
  const enabledTemplates = templates.filter((template) => isEnabledValue(template.enabled ?? 1));
  const preferredTemplate = enabledTemplates.find((template) => Number(template.id) === Number(subject?.permissionTemplateId))
    || enabledTemplates.find((template) => template.templateCode === initialRoleCode)
    || enabledTemplates[0];
  const initialUserId = subject?.userId
    || subject?.matchedUserId
    || userOptions[0]?.userId
    || userOptions[0]?.id
    || '';
  const [userId, setUserId] = useState(String(initialUserId));
  const [projectRoleCode, setProjectRoleCode] = useState(initialRoleCode);
  const [permissionTemplateId, setPermissionTemplateId] = useState(String(preferredTemplate?.id || ''));
  const [comment, setComment] = useState(mode === 'approve' ? '同意加入当前项目' : '');
  const [submitting, setSubmitting] = useState(false);

  const changeRole = (nextRoleCode) => {
    setProjectRoleCode(nextRoleCode);
    const matchedTemplate = enabledTemplates.find((template) => template.templateCode === nextRoleCode);
    if (matchedTemplate) setPermissionTemplateId(String(matchedTemplate.id));
  };

  const save = async () => {
    if (!userId) {
      alert('请选择系统用户');
      return;
    }
    if (!projectRoleCode) {
      alert('请选择项目角色');
      return;
    }
    if (!permissionTemplateId) {
      alert('请选择巡检权限角色模板');
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
        projectRoleCode,
        permissionTemplateId: Number(permissionTemplateId),
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
    ? '确认账号后，为申请人分配项目角色和巡检细分权限。'
    : '通用项目角色控制菜单与操作，巡检模板控制电箱和巡检细分权限。';
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
            <label className="system-form-field">
              <span>系统用户 *</span>
              <select value={userId} onChange={(event) => setUserId(event.target.value)}>
                <option value="">请选择系统用户</option>
                {userOptions.map((user) => (
                  <option key={user.userId || user.id} value={user.userId || user.id}>
                    {user.realName || user.username}（{user.username || '-'}）
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <div className="system-project-auth-summary">
              <strong>{subjectName}</strong>
              <span>{subject?.matchedUsername || subject?.username || `用户 ${userId}`}</span>
            </div>
          )}
          <div className="system-project-auth-grid">
            <label className="system-form-field">
              <span>项目角色 *</span>
              <select value={projectRoleCode} onChange={(event) => changeRole(event.target.value)}>
                <option value="">请选择项目角色</option>
                {projectRoles.map((role) => {
                  const code = role.roleCode || role.code;
                  return <option key={getId(role) || code} value={code}>{role.roleName || role.name || code}（{code}）</option>;
                })}
              </select>
            </label>
            <label className="system-form-field">
              <span>巡检权限角色模板 *</span>
              <select value={permissionTemplateId} onChange={(event) => setPermissionTemplateId(event.target.value)}>
                <option value="">请选择巡检权限模板</option>
                {enabledTemplates.map((template) => (
                  <option key={template.id} value={template.id}>{template.templateName}（{template.templateCode}）</option>
                ))}
              </select>
            </label>
          </div>
          <div className="system-project-auth-help">
            项目角色来自 PROJECT 范围的系统角色；巡检模板用于电箱台账、巡检提交、记录查看和汇总导出等兼容细分权限。
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
          <button className="primary" disabled={submitting || !projectRoles.length || !enabledTemplates.length} onClick={save}>
            {submitting ? '处理中…' : mode === 'approve' ? '批准并授权' : '保存授权'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function SystemManagementPage({ currentUser, currentProject, projectList = [], onBack }) {
  const availableTabs = useMemo(
    () => TABS.filter((tab) => {
      if (isPlatformAdmin(currentUser)) return true;
      if (!hasAssignedMenu(currentUser, tab.code)) return false;
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
  const [reviewing, setReviewing] = useState(null);
  const [projectApplications, setProjectApplications] = useState([]);
  const [inspectionTemplates, setInspectionTemplates] = useState([]);
  const [inspectionPermissionCatalog, setInspectionPermissionCatalog] = useState([]);
  const [editingInspectionTemplate, setEditingInspectionTemplate] = useState(null);
  const [projectAuthorizationDialog, setProjectAuthorizationDialog] = useState(null);
  const requestSequenceRef = useRef(0);
  const projectRoleOptions = useMemo(() => {
    const configured = roles.filter((role) => isProjectRole(role) && isEnabledValue(role.enabled ?? 1));
    if (configured.length) return configured;
    const names = {
      PROJECT_ADMIN: '项目管理员',
      SAFETY_ADMIN: '巡检记录管理员',
      USER: '项目成员',
    };
    const codes = new Set(['PROJECT_ADMIN', 'SAFETY_ADMIN', 'USER']);
    rows.forEach((member) => {
      if (member?.projectRoleCode) codes.add(String(member.projectRoleCode).toUpperCase());
    });
    (currentUser?.projectContexts || currentUser?.projectRoles || []).forEach((context) => {
      if (Number(context?.projectId) === Number(currentProject) && context?.projectRoleCode) {
        codes.add(String(context.projectRoleCode).toUpperCase());
      }
    });
    return [...codes].map((code) => ({
      roleName: names[code] || code,
      roleCode: code,
      scopeType: 'PROJECT',
      enabled: 1,
    }));
  }, [currentProject, currentUser, roles, rows]);

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

  const loadInspectionTemplateDefinitions = useCallback(async () => {
    const [templateRes, catalogRes] = await Promise.all([
      getInspectionPermissionTemplates(),
      getInspectionPermissionCatalog(),
    ]);
    if (templateRes.code !== 200) throw new Error(templateRes.message || '巡检权限角色模板加载失败');
    if (catalogRes.code !== 200) throw new Error(catalogRes.message || '巡检权限目录加载失败');
    setInspectionTemplates(extractList(templateRes.data));
    setInspectionPermissionCatalog(extractList(catalogRes.data));
  }, []);

  const loadData = useCallback(async (overrides = {}) => {
    if (!activeTab) return;
    const requestSequence = ++requestSequenceRef.current;
    setLoading(true);
    setError('');
    try {
      let res;
      let nextProjectApplications = null;
      let nextInspectionTemplates = null;
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
        await Promise.all([
          loadRolesAndPermissions(),
          loadInspectionTemplateDefinitions(),
        ]);
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
        const [memberRes, applicationRes, templateRes, roleRes] = await Promise.all([
          getProjectMembers(currentProject),
          getWechatAccessApplications({ projectId: currentProject, status: 'PENDING', pageNo: 1, pageSize: 100 }),
          getInspectionPermissionTemplates(),
          isPlatformAdmin(currentUser) ? getSystemRoles({ pageSize: 200 }) : Promise.resolve(null),
        ]);
        if (applicationRes.code === 200) {
          nextProjectApplications = extractList(applicationRes.data)
            .filter((application) => application.applicationType === 'PROJECT_ACCESS');
        }
        if (templateRes.code === 200) nextInspectionTemplates = extractList(templateRes.data);
        if (roleRes?.code === 200) setRoles(extractList(roleRes.data));
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
      if (nextInspectionTemplates) setInspectionTemplates(nextInspectionTemplates);
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
  }, [activeTab, currentProject, currentUser, keyword, loadInspectionTemplateDefinitions, loadRolesAndPermissions, pageNo, pageSize, status]);

  useEffect(() => {
    setKeyword('');
    setStatus('');
    setPageNo(1);
  }, [activeTab]);

  useEffect(() => {
    loadData();
  }, [activeTab, currentProject, pageNo, pageSize]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedRole) {
      setRolePermissionIds([]);
      return;
    }
    const ids = selectedRole.permissionIds
      || (selectedRole.permissions || []).map(getId)
      || [];
    setRolePermissionIds(ids.filter((id) => id !== undefined));
    setRoleMenuIds((selectedRole.menuIds || []).filter((id) => id !== undefined));
  }, [selectedRole]);

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
      if (!roles.length) {
        const res = await getSystemRoles({ page: 1, pageSize: 200, status: 'ACTIVE' });
        if (res.code !== 200) throw new Error(res.message || '角色加载失败');
        setRoles(extractList(res.data));
      }
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

  const editUserRoles = async (user) => {
    try {
      const loadedRoles = roles.length ? roles : await loadRolesAndPermissions();
      const roleList = loadedRoles.filter((role) => String(role.scopeType || role.scope || 'PLATFORM').toUpperCase() === 'PLATFORM');
      const roleText = roleList.map((role) => `${getId(role)}. ${role.roleName || role.name}（${role.roleCode || role.code}）`).join('\n');
      const currentIds = user.roleIds || (user.roles || []).map((role) => {
        if (typeof role === 'object') return getId(role);
        return getId(roleList.find((candidate) => (candidate.roleCode || candidate.code) === role));
      }).filter(Boolean);
      const input = window.prompt(`输入角色 ID，多个用英文逗号分隔：\n${roleText}`, currentIds.join(','));
      if (input === null) return;
      const roleIds = input.split(',').map((item) => Number(item.trim())).filter(Number.isFinite);
      const res = await updateSystemUserRoles(getId(user), { roleIds });
      if (res.code !== 200) throw new Error(res.message || '角色更新失败');
      await loadData();
    } catch (err) {
      alert(err.message || '角色更新失败');
    }
  };

  const saveRolePermissions = async () => {
    if (!selectedRole) return;
    try {
      const assignablePermissionIds = new Set(roleAssignablePermissions(selectedRole, permissions).map(getId));
      const assignableMenuIds = new Set(roleAssignableMenus(selectedRole, menus).map(getId));
      const res = await updateSystemRolePermissions(getId(selectedRole), {
        permissionIds: rolePermissionIds.filter((id) => assignablePermissionIds.has(id)),
        menuIds: roleMenuIds.filter((id) => assignableMenuIds.has(id)),
      });
      if (res.code !== 200) throw new Error(res.message || '角色权限保存失败');
      alert('角色权限已保存，相关用户会话将按后端策略刷新');
      await loadData();
    } catch (err) {
      alert(err.message || '角色权限保存失败');
    }
  };

  const toggleInspectionTemplateStatus = async (template) => {
    if (!isPlatformAdmin(currentUser) || Number(template.builtin || 0) === 1) return;
    const nextEnabled = !isEnabledValue(template.enabled);
    try {
      const res = await updateInspectionPermissionTemplateStatus(template.id, nextEnabled);
      if (res.code !== 200) throw new Error(res.message || '巡检权限角色状态更新失败');
      await loadData();
    } catch (err) {
      alert(err.message || '巡检权限角色状态更新失败');
    }
  };

  const editRoleDefinition = async (role = null) => {
    const roleName = window.prompt('角色名称', role?.roleName || role?.name || '');
    if (roleName === null || !roleName.trim()) return;
    const roleCode = window.prompt('角色编码（大写字母、数字和下划线）', role?.roleCode || role?.code || '');
    if (roleCode === null || !roleCode.trim()) return;
    const scopeType = window.prompt('角色范围（PLATFORM 或 PROJECT）', role?.scopeType || role?.scope || 'PLATFORM');
    if (scopeType === null || !['PLATFORM', 'PROJECT'].includes(scopeType.trim().toUpperCase())) {
      if (scopeType !== null) alert('角色范围只能是 PLATFORM 或 PROJECT');
      return;
    }
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
    const active = (member.status || 'ACTIVE') === 'ACTIVE';
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
        projectRoleCode: values.projectRoleCode,
        permissionTemplateId: values.permissionTemplateId,
      });
    } else if (mode === 'edit') {
      res = await updateProjectMember(currentProject, subject.userId, {
        projectRoleCode: values.projectRoleCode,
        permissionTemplateId: values.permissionTemplateId,
      });
    } else if (mode === 'approve') {
      res = await approveWechatAccessApplication(subject.id, {
        accountMode: 'EXISTING',
        userId: values.userId,
        projectId: Number(subject.projectId || currentProject),
        projectRoleCode: values.projectRoleCode,
        permissionTemplateId: values.permissionTemplateId,
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
      const options = extractList(optionRes.data).filter((option) => !option.inProject
        && !rows.some((member) => Number(member.userId) === Number(option.userId || option.id)));
      if (!options.length) {
        alert('当前没有可新增的系统用户');
        return;
      }
      setProjectAuthorizationDialog({ mode: 'add', subject: {}, userOptions: options });
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
      <PageBar title="用户管理" description="统一管理账号状态、登录方式和平台角色；停用与重置密码会使既有会话失效。">
        <SearchBar value={keyword} onChange={setKeyword} placeholder="姓名、账号或手机号" onSearch={runSearch} />
      </PageBar>
      <div className="system-table-wrap"><table><thead><tr><th>用户</th><th>联系方式</th><th>平台角色</th><th>登录方式</th><th>状态</th><th>最近登录</th><th>操作</th></tr></thead>
        <tbody>{rows.map((user) => <tr key={getId(user)}>
          <td><strong>{user.realName || '-'}</strong><small>{user.username}</small></td><td>{user.phone || '-'}<small>{user.email || '-'}</small></td>
          <td>{(user.roleNames || user.roles || []).map((role) => typeof role === 'string' ? role : role.roleName).filter(Boolean).join('、') || '未分配'}</td>
          <td>{!isEnabledValue(user.passwordLoginEnabled) ? '仅微信' : user.wechatBound ? '密码 + 微信' : '账号密码'}</td><td><StatusTag status={user.status} /></td><td>{formatDate(user.lastLoginAt || user.createTime)}</td>
          <td><div className="system-row-actions">{hasPermission(currentUser, 'system.user.manage') && <button onClick={() => editUserRoles(user)}>分配角色</button>}{hasPermission(currentUser, 'system.user.reset_password') && <button onClick={() => resetPassword(user)}>重置密码</button>}{hasPermission(currentUser, 'system.user.status') && <button className={isEnabledValue(user.status) ? 'danger' : ''} onClick={() => toggleUserStatus(user)}>{isEnabledValue(user.status) ? '停用' : '启用'}</button>}{!hasPermission(currentUser, 'system.user.manage', 'system.user.reset_password', 'system.user.status') && <span className="system-hint">只读</span>}</div></td>
        </tr>)}</tbody></table></div>
    </>
  );

  const renderRoles = () => {
    const assignableMenus = roleAssignableMenus(selectedRole, menus);
    const assignablePermissions = roleAssignablePermissions(selectedRole, permissions);
    const assignableMenuIds = new Set(assignableMenus.map(getId));
    const assignablePermissionIds = new Set(assignablePermissions.map(getId));
    return (
      <>
      <PageBar title="角色与权限" description="角色定义功能权限，项目数据范围仍由项目授权单独控制。">
        {hasPermission(currentUser, 'system.role.manage') && <button onClick={() => editRoleDefinition(null)}>新增角色</button>}
        {hasPermission(currentUser, 'system.role.manage') && <button onClick={() => editRoleDefinition(selectedRole)} disabled={!selectedRole}>编辑角色</button>}
        {hasPermission(currentUser, 'system.role.manage') && <button className="danger" onClick={removeRoleDefinition} disabled={!selectedRole}>删除角色</button>}
        {hasPermission(currentUser, 'system.role.manage') && <button className="primary" onClick={saveRolePermissions} disabled={!selectedRole}>保存权限配置</button>}
      </PageBar>
      <div className="system-role-layout">
        <div className="system-role-list">{rows.map((role) => <button key={getId(role)} className={getId(selectedRole) === getId(role) ? 'active' : ''} onClick={() => setSelectedRole(role)}><strong>{role.roleName || role.name}</strong><small>{role.roleCode || role.code} · {role.scopeType || role.scope || 'PLATFORM'}</small></button>)}</div>
        <div className="system-permission-panel">
          {!selectedRole ? <Empty text="请选择角色" /> : <>
            <div className="system-permission-title"><strong>{selectedRole.roleName || selectedRole.name}</strong><span>菜单 {roleMenuIds.filter((id) => assignableMenuIds.has(id)).length} 项 · 权限 {rolePermissionIds.filter((id) => assignablePermissionIds.has(id)).length} 项</span></div>
            <h3 className="system-group-title">可见菜单</h3>
            <div className="system-permission-grid">{assignableMenus.map((menu) => {
              const id = getId(menu);
              return <label key={`menu-${id}`}><input type="checkbox" disabled={!hasPermission(currentUser, 'system.role.manage')} checked={roleMenuIds.includes(id)} onChange={() => setRoleMenuIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])} /><span>{menu.menuName || menu.name}<small>{menu.menuCode || menu.code}</small></span></label>;
            })}</div>
            <h3 className="system-group-title">操作权限</h3>
            <div className="system-permission-grid">{assignablePermissions.map((permission) => {
              const id = getId(permission);
              return <label key={id}><input type="checkbox" disabled={!hasPermission(currentUser, 'system.role.manage')} checked={rolePermissionIds.includes(id)} onChange={() => setRolePermissionIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])} /><span>{permission.permissionName || permission.name}<small>{permission.permissionCode || permission.code}</small></span></label>;
            })}</div>
          </>}
        </div>
      </div>
      <section className="system-template-section">
        <div className="system-template-section-header">
          <div>
            <h3>巡检权限角色模板</h3>
            <p>用于项目成员的电箱台账、巡检提交、记录查看和汇总导出等细分权限；上方通用角色负责系统菜单和操作权限。</p>
          </div>
          <div className="system-page-actions">
            <span className="system-hint">{inspectionTemplates.length} 个模板</span>
            {isPlatformAdmin(currentUser) && <button className="primary" onClick={() => setEditingInspectionTemplate({})}>新建巡检模板</button>}
          </div>
        </div>
        {!isPlatformAdmin(currentUser) && <div className="system-template-readonly">当前为只读视图；只有平台管理员可以新增、编辑和启停巡检权限角色模板。</div>}
        <div className="system-template-card-grid">
          {inspectionTemplates.map((template) => {
            const enabled = isEnabledValue(template.enabled);
            const builtin = Number(template.builtin || 0) === 1;
            return (
              <article className="system-template-card" key={template.id}>
                <div className="system-template-card-title">
                  <div><strong>{template.templateName}</strong><small>{template.templateCode}</small></div>
                  <div><StatusTag status={enabled ? 'ENABLED' : 'DISABLED'} />{builtin && <span className="system-template-builtin">内置</span>}</div>
                </div>
                <p>{template.description || '暂无模板说明'}</p>
                <div className="system-template-permission-codes">
                  {(template.permissionCodes || []).map((code) => <span key={code}>{code}</span>)}
                  {!(template.permissionCodes || []).length && <span>未配置细分权限</span>}
                </div>
                {isPlatformAdmin(currentUser) && (
                  <div className="system-row-actions system-template-card-actions">
                    <button onClick={() => setEditingInspectionTemplate(template)}>编辑</button>
                    {!builtin && <button className={enabled ? 'danger' : ''} onClick={() => toggleInspectionTemplateStatus(template)}>{enabled ? '停用' : '启用'}</button>}
                    {builtin && <span className="system-hint">内置模板不可启停</span>}
                  </div>
                )}
              </article>
            );
          })}
          {!inspectionTemplates.length && <div className="system-template-empty">暂无巡检权限角色模板</div>}
        </div>
      </section>
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
      <PageBar title="项目授权" description={`当前作业区域：${currentProjectName || currentProject || '-'}。切换顶部作业区域可管理对应成员。`}>
        {isPlatformAdmin(currentUser) && <button className="primary" onClick={addProjectMember}>新增项目成员</button>}
      </PageBar>
      <div className="system-table-wrap"><table><thead><tr><th>成员</th><th>账号</th><th>项目职责</th><th>权限角色</th><th>状态</th><th>变更原因</th><th>操作</th></tr></thead>
        <tbody>{rows.map((member) => <tr key={member.userId}><td><strong>{member.realName || member.username}</strong></td><td>{member.username || '-'}</td><td>{member.projectRoleName || member.projectRoleCode || '-'}</td><td>{member.permissionTemplateName || (member.roleNames || []).join('、') || '-'}</td><td><StatusTag status={member.status || 'ACTIVE'} /></td><td>{member.statusReason || '-'}</td><td><div className="system-row-actions"><button onClick={() => editProjectMember(member)}>调整授权</button><button className={member.status === 'DISABLED' ? '' : 'danger'} onClick={() => toggleProjectMember(member)}>{member.status === 'DISABLED' ? '恢复访问' : '暂停访问'}</button><button className="danger" onClick={() => deleteProjectMember(member)}>移除</button></div></td></tr>)}</tbody></table></div>
      <div className="system-subsection-title"><strong>待审核项目访问申请</strong><span>{projectApplications.length} 条</span></div>
      <div className="system-table-wrap"><table><thead><tr><th>申请人</th><th>手机号</th><th>目标项目</th><th>申请类型</th><th>申请时间</th><th>操作</th></tr></thead>
        <tbody>{projectApplications.map((application) => <tr key={application.id}><td><strong>{application.realName || application.applicantName || '-'}</strong><small>{application.matchedUsername || (application.matchedUserId ? `用户 ${application.matchedUserId}` : '未绑定系统账号')}</small></td><td>{application.phone || '-'}</td><td>{application.projectName || currentProjectName || '-'}</td><td>{application.applicationType || application.type || 'PROJECT_ACCESS'}</td><td>{formatDate(application.createdAt || application.applyTime || application.createTime)}</td><td><div className="system-row-actions"><button onClick={() => approveProjectApplication(application)}>批准</button><button className="danger" onClick={() => rejectProjectApplication(application)}>驳回</button></div></td></tr>)}</tbody></table>{!projectApplications.length && <Empty text="暂无待审核项目访问申请" />}</div>
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
      {reviewing && <ReviewDialog application={reviewing} roles={roles} projectList={projectList} onClose={() => setReviewing(null)} onApproved={async () => { setReviewing(null); await loadData(); }} />}
      {editingInspectionTemplate && isPlatformAdmin(currentUser) && (
        <InspectionTemplateDialog
          key={editingInspectionTemplate.id || 'new'}
          template={editingInspectionTemplate}
          catalog={inspectionPermissionCatalog}
          onClose={() => setEditingInspectionTemplate(null)}
          onSaved={loadData}
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
          templates={inspectionTemplates}
          onClose={() => setProjectAuthorizationDialog(null)}
          onSubmit={submitProjectAuthorization}
        />
      )}
    </div>
  );
}
