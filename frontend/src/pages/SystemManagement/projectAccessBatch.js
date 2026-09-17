export const MAX_BATCH_USERS = 200;
export const ROLE_CHANGE_OPTIONS = [
  ['ADD_ROLES', '增加角色，原来的保留'],
  ['REMOVE_ROLES', '取消选中的角色，其他的保留'],
  ['REPLACE_ROLES', '重新设置，只保留本次选中的角色'],
];

export function accessBatchExplanation(form, userCount, projects, roles) {
  const projectName = (id, fallback) => {
    const project = projects.find((item) => String(item.id) === String(id));
    return project ? `「${project.projectName || project.name}」` : fallback;
  };
  const selectedRoles = roles.filter((role) => form.roleIds.includes(Number(role.id))).map((role) => role.roleName || role.name);
  const roleNames = selectedRoles.length ? `「${selectedRoles.join('、')}」` : '下面勾选的角色';
  const project = projectName(form.projectId, '所选项目');
  const people = `选中的 ${userCount} 人`;
  if (form.operation === 'ADD_ROLES') return {
    summary: `给${people}在${project}增加${roleNames}，原有角色继续保留。`,
    detail: '已拥有的角色不会重复添加；尚未加入这个项目的人会一并加入。',
    example: '例如：项目经理 + 新增安委会巡检 → 同时拥有这两个角色。',
  };
  if (form.operation === 'REMOVE_ROLES') return {
    summary: `取消${people}在${project}的${roleNames}，其他角色继续保留。`,
    detail: '所选人员须已加入该项目。没有这些角色的人保持原样；如果有人将不剩任何角色，会阻止保存。',
    example: '例如：原有项目经理、安委会巡检 → 取消安委会巡检 → 仍是项目经理。',
  };
  if (form.operation === 'REPLACE_ROLES') return {
    summary: `把${people}在${project}的角色统一设为${roleNames}。`,
    detail: '原角色中，本次没有勾选的都会取消。所选人员必须已经加入这个项目。',
    example: '例如：原有项目经理、安委会巡检 → 本次只选安全员 → 最后只剩安全员。',
  };
  const source = projectName(form.sourceProjectId, '原项目');
  const target = projectName(form.targetProjectId, '新项目');
  return {
    summary: `让${people}加入${target}，${form.operation === 'MOVE_PROJECT' ? `同时从${source}移出，取消在原项目的全部角色` : `并保留在${source}的角色`}。`,
    detail: form.roleSource === 'SOURCE' ? '新项目沿用每个人在原项目的角色；新项目中已经有的角色也会保留。' : `在新项目增加${roleNames}，新项目中已经有的角色也会保留。`,
    example: '原来的业务记录和附件仍留在原项目。访问暂停状态也会保留。',
  };
}

export const isTransferOperation = (operation) => ['COPY_PROJECT', 'MOVE_PROJECT'].includes(operation);
export const canSelectBatchUser = (user) => Number(user?.status) === 1
  && !(user.roles || []).some((role) => (typeof role === 'string' ? role : role.roleCode) === 'PLATFORM_ADMIN');

export function changeBatchSelection(current, candidates, checked) {
  const next = new Map(current.map((user) => [Number(user.id), user]));
  for (const user of candidates) {
    if (checked && canSelectBatchUser(user)) next.set(Number(user.id), user);
    else if (!checked) next.delete(Number(user.id));
  }
  if (next.size > MAX_BATCH_USERS) return { users: current, error: `单批最多选择 ${MAX_BATCH_USERS} 人，请分批操作` };
  return { users: [...next.values()], error: '' };
}

export function buildAccessBatchRequest(userIds, form) {
  const common = { userIds: [...new Set(userIds.map(Number))], operation: form.operation };
  if (isTransferOperation(form.operation)) {
    return { ...common, sourceProjectId: Number(form.sourceProjectId), targetProjectId: Number(form.targetProjectId),
      roleSource: form.roleSource, roleIds: form.roleSource === 'SELECTED' ? form.roleIds.map(Number) : [] };
  }
  return { ...common, projectId: Number(form.projectId), roleIds: form.roleIds.map(Number) };
}

export function accessBatchFormError(userCount, form) {
  if (!userCount || userCount > MAX_BATCH_USERS) return '请选择 1 至 200 名用户';
  if (isTransferOperation(form.operation)) {
    if (!form.sourceProjectId || !form.targetProjectId) return '请选择原项目和要加入的新项目';
    if (String(form.sourceProjectId) === String(form.targetProjectId)) return '原项目和新项目不能相同';
  } else if (!form.projectId) return '请选择要更改的项目';
  if ((!isTransferOperation(form.operation) || form.roleSource === 'SELECTED') && !form.roleIds.length) return '请至少选择一个角色';
  return '';
}
