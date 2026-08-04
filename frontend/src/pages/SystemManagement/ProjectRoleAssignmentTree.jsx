import React, { useMemo, useState } from 'react';
import {
  assignmentNodeChanged,
  assignmentRoleIdsForNode,
  toggleAssignmentNode,
  toggleAssignmentRole,
} from '../../utils/projectRoleAssignments';

const itemId = (item) => Number(item?.id ?? item?.roleId);
const itemCode = (item) => String(item?.roleCode || item?.code || '');
const itemName = (item) => item?.roleName || item?.name || itemCode(item) || '未命名角色';

const pendingText = (node) => {
  if (!assignmentNodeChanged(node)) return '';
  if (!node.originalAssigned && node.assigned) return '待新增';
  if (node.originalAssigned && !node.assigned) return '待移出';
  return '待调整';
};

export default function ProjectRoleAssignmentTree({
  nodes = [],
  roles = [],
  defaultRoleId,
  onNodeChange,
  onStatusChange,
  onError,
  busy = false,
  emptyText = '暂无可分配对象',
}) {
  const [expanded, setExpanded] = useState(() => new Set());
  const roleById = useMemo(() => new Map(roles.map((role) => [itemId(role), role])), [roles]);

  const rolesForNode = (node) => {
    const selectedRoleIds = assignmentRoleIdsForNode(node);
    const combined = new Map(roles.map((role) => [itemId(role), role]));
    (node.roleDetails || []).forEach((role) => {
      const id = itemId(role);
      if (Number.isFinite(id) && !combined.has(id)) combined.set(id, role);
    });
    return Array.from(combined.values()).sort((left, right) => {
      const leftSelected = selectedRoleIds.includes(itemId(left));
      const rightSelected = selectedRoleIds.includes(itemId(right));
      return Number(rightSelected) - Number(leftSelected) || itemName(left).localeCompare(itemName(right), 'zh-CN');
    });
  };

  const updateParent = (node, checked) => {
    try {
      onNodeChange(toggleAssignmentNode(node, checked, defaultRoleId));
    } catch (error) {
      onError?.(error.message || '成员关系更新失败');
    }
  };

  const updateRole = (node, role, checked) => {
    const next = toggleAssignmentRole(node, itemId(role), checked);
    const roleNames = next.roleIds.map((id) => itemName(roleById.get(id)
      || (node.roleDetails || []).find((item) => itemId(item) === id)));
    onNodeChange({ ...next, roleNames });
  };

  const toggleExpanded = (id) => setExpanded((current) => {
    const next = new Set(current);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  if (!nodes.length) return <div className="system-assignment-empty">{emptyText}</div>;

  return (
    <div className="system-assignment-tree">
      <div className="system-assignment-tree-actions">
        <span>父节点代表成员关系，展开后勾选一个或多个项目角色</span>
        <div>
          <button type="button" onClick={() => setExpanded(new Set(nodes.map((node) => node.id)))}>展开全部</button>
          <button type="button" onClick={() => setExpanded(new Set())}>收起全部</button>
        </div>
      </div>
      <div className="system-assignment-tree-list">
        {nodes.map((node) => {
          const open = expanded.has(node.id);
          const pending = pendingText(node);
          const selectedRoleIds = assignmentRoleIdsForNode(node);
          const selectedRoleNames = selectedRoleIds.map((id) => itemName(roleById.get(id)
            || (node.roleDetails || []).find((role) => itemId(role) === id)));
          const statusDisabled = String(node.accessStatus || '').toUpperCase() === 'DISABLED';
          const canToggleMembership = node.roleEditable && (!node.assigned || node.removable);
          return (
            <section key={node.id} className={`${node.assigned ? 'assigned' : ''}${pending ? ' changed' : ''}${!node.roleEditable ? ' locked' : ''}`}>
              <div className="system-assignment-node">
                <button type="button" className="system-tree-expand" aria-label={open ? '收起角色' : '展开角色'} onClick={() => toggleExpanded(node.id)}>{open ? '⌄' : '›'}</button>
                <input
                  type="checkbox"
                  checked={node.assigned}
                  disabled={busy || !canToggleMembership}
                  onChange={(event) => updateParent(node, event.target.checked)}
                />
                <button type="button" className="system-assignment-node-main" onClick={() => toggleExpanded(node.id)}>
                  <span><strong>{node.label}</strong>{node.secondary && <small>{node.secondary}</small>}</span>
                  <span className="system-assignment-node-summary">
                    {node.assigned ? (selectedRoleNames.join('、') || '未选择角色') : '未加入'}
                  </span>
                </button>
                <div className="system-assignment-node-state">
                  {node.assigned && <span className={statusDisabled ? 'disabled' : 'active'}>{statusDisabled ? '访问已暂停' : '访问启用'}</span>}
                  {Number(node.accountStatus) === 0 && <span className="disabled">账号停用</span>}
                  {pending && <span className={`pending ${pending === '待移出' ? 'remove' : ''}`}>{pending}</span>}
                  {!node.roleEditable && <span className="locked">只读保护</span>}
                </div>
                {node.assigned && node.originalAssigned && onStatusChange && node.roleEditable && (
                  <button
                    type="button"
                    className={statusDisabled ? 'system-access-action' : 'system-access-action danger'}
                    disabled={busy}
                    onClick={() => onStatusChange(node)}
                  >
                    {statusDisabled ? '恢复访问' : '暂停访问'}
                  </button>
                )}
              </div>
              {open && (
                <div className="system-assignment-roles">
                  {rolesForNode(node).map((role) => {
                    const id = itemId(role);
                    const checked = selectedRoleIds.includes(id);
                    const roleDisabled = busy || !node.roleEditable;
                    return (
                      <label key={id} className={checked ? 'selected' : ''}>
                        <input type="checkbox" checked={checked} disabled={roleDisabled} onChange={(event) => updateRole(node, role, event.target.checked)} />
                        <span>
                          <strong>{itemName(role)}</strong>
                          <small>{itemCode(role) || '项目角色'}{Number(role.projectManagerRole || 0) === 1 ? ' · 项目经理角色' : ''}</small>
                        </span>
                      </label>
                    );
                  })}
                  {!rolesForNode(node).length && <span className="system-hint">暂无启用的项目角色</span>}
                  {node.assigned && !node.roleEditable && <p>该成员或角色受保护，当前账号只能查看。</p>}
                </div>
              )}
            </section>
          );
        })}
      </div>
    </div>
  );
}
