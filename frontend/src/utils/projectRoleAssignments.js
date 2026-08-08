const numericIds = (values = []) => Array.from(new Set(values
  .map((value) => Number(value))
  .filter(Number.isFinite))).sort((left, right) => left - right);

const roleId = (role) => Number(role?.id ?? role?.roleId);
const roleLabel = (role) => role?.roleName || role?.name || role?.roleCode || role?.code || '未命名角色';

export const assignmentRoleIdsForNode = (node) => numericIds(
  Array.isArray(node?.roleIds) ? node.roleIds : [],
).filter((id) => Number.isInteger(id) && id > 0);

export const roleIdsEqual = (left = [], right = []) => {
  const normalizedLeft = numericIds(left);
  const normalizedRight = numericIds(right);
  return normalizedLeft.length === normalizedRight.length
    && normalizedLeft.every((value, index) => value === normalizedRight[index]);
};

const assignmentRoleIds = (assignment) => numericIds((assignment?.projectRoles || []).map(roleId));

const assignmentRoleNames = (assignment) => (assignment?.projectRoles || []).map(roleLabel).filter(Boolean);

const baseNode = ({
  id,
  label,
  secondary,
  assigned,
  roleIds,
  roleNames,
  roleDetails,
  accessStatus,
  statusReason,
  roleEditable = true,
  removable = true,
  protectedManager = false,
  accountStatus = 1,
}) => {
  const normalizedRoles = numericIds(roleIds);
  return {
    id: Number(id),
    label,
    secondary,
    assigned: Boolean(assigned),
    originalAssigned: Boolean(assigned),
    roleIds: normalizedRoles,
    originalRoleIds: normalizedRoles,
    roleNames: roleNames || [],
    roleDetails: roleDetails || [],
    accessStatus: accessStatus || (assigned ? 'ACTIVE' : null),
    statusReason: statusReason || '',
    roleEditable: roleEditable !== false,
    removable: removable !== false,
    protectedManager: Boolean(protectedManager),
    accountStatus,
  };
};

export function buildUserProjectNodes(projects = [], assignments = []) {
  const assignmentsByProject = new Map(assignments.map((assignment) => [Number(assignment.projectId), assignment]));
  const projectsById = new Map(projects.map((project) => [Number(project.id ?? project.projectId), project]));
  assignments.forEach((assignment) => {
    const id = Number(assignment.projectId);
    if (!projectsById.has(id)) projectsById.set(id, assignment);
  });
  return Array.from(projectsById.entries()).map(([id, project]) => {
    const assignment = assignmentsByProject.get(id);
    const label = assignment?.projectName || project?.projectName || project?.shortName || `项目 ${id}`;
    return baseNode({
      id,
      label,
      secondary: assignment?.shortName || project?.shortName || '',
      assigned: Boolean(assignment),
      roleIds: assignmentRoleIds(assignment),
      roleNames: assignmentRoleNames(assignment),
      roleDetails: assignment?.projectRoles || [],
      accessStatus: assignment?.accessStatus,
      statusReason: assignment?.statusReason,
    });
  }).sort((left, right) => Number(right.assigned) - Number(left.assigned)
    || left.label.localeCompare(right.label, 'zh-CN'));
}

export function buildProjectMemberNodes(options = []) {
  return options.map((option) => baseNode({
    id: option.userId ?? option.id,
    label: option.realName || option.username || `用户 ${option.userId ?? option.id}`,
    secondary: option.username || '',
    assigned: option.assigned,
    roleIds: assignmentRoleIds(option),
    roleNames: assignmentRoleNames(option),
    roleDetails: option.projectRoles || [],
    accessStatus: option.accessStatus,
    statusReason: option.statusReason,
    roleEditable: option.roleEditable,
    removable: option.removable,
    protectedManager: option.protectedManager,
    accountStatus: option.accountStatus,
  }));
}

export const assignmentNodeChanged = (node) => Boolean(node)
  && (node.assigned !== node.originalAssigned || !roleIdsEqual(node.roleIds, node.originalRoleIds));

export function toggleAssignmentNode(node, checked) {
  if (!node || !node.roleEditable || (!checked && !node.removable)) return node;
  if (!checked) return { ...node, assigned: false, roleIds: [] };
  if (node.assigned) return node;
  if (node.originalAssigned && node.originalRoleIds.length) {
    return { ...node, assigned: true, roleIds: [...node.originalRoleIds] };
  }
  return { ...node, assigned: true, roleIds: [] };
}

export function toggleAssignmentRole(node, targetRoleId, checked) {
  if (!node || !node.roleEditable) return node;
  const id = Number(targetRoleId);
  if (!Number.isFinite(id)) return node;
  const nextRoleIds = checked
    ? numericIds([...(node.roleIds || []), id])
    : numericIds((node.roleIds || []).filter((value) => Number(value) !== id));
  return { ...node, assigned: nextRoleIds.length > 0, roleIds: nextRoleIds };
}

export function assignmentChanges(nodes = [], idField = 'userId') {
  return nodes.filter(assignmentNodeChanged).map((node) => ({
    [idField]: Number(node.id),
    operation: node.assigned ? 'UPSERT' : 'REMOVE',
    roleIds: node.assigned ? numericIds(node.roleIds) : [],
  }));
}

export function assignmentChangeSummary(nodes = []) {
  return nodes.reduce((summary, node) => {
    if (!assignmentNodeChanged(node)) return summary;
    if (!node.originalAssigned && node.assigned) summary.added += 1;
    else if (node.originalAssigned && !node.assigned) summary.removed += 1;
    else summary.updated += 1;
    return summary;
  }, { added: 0, updated: 0, removed: 0 });
}

export function filterAssignmentNodes(nodes = [], keyword = '') {
  const value = keyword.trim().toLowerCase();
  if (!value) return nodes;
  return nodes.filter((node) => [node.label, node.secondary, ...(node.roleNames || [])]
    .some((field) => String(field || '').toLowerCase().includes(value)));
}

export function mergeAssignmentDrafts(options = [], drafts = new Map()) {
  return buildProjectMemberNodes(options).map((fresh) => {
    const draft = drafts.get(Number(fresh.id));
    if (!draft || !assignmentNodeChanged(draft)) return fresh;
    return {
      ...fresh,
      assigned: draft.assigned,
      roleIds: [...draft.roleIds],
      originalAssigned: draft.originalAssigned,
      originalRoleIds: [...draft.originalRoleIds],
      accessStatus: draft.accessStatus,
      statusReason: draft.statusReason,
    };
  });
}
