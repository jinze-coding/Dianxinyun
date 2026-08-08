import React, { useEffect, useMemo, useState } from 'react';
import DocumentManagementPage from '../DocumentManagement';
import SealManagementPage from '../SealManagement';
import { collectProjectMenuCodes, hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import { pageMenuAllowed } from '../../utils/roleAuthorization';
import './index.css';

const TABS = [
  { id: 'library', label: '工程资料', description: '目录、版本、预览、归档与回收站' },
  { id: 'seal', label: '用印申请', description: '发起、审批、盖章、归档全过程' },
  { id: 'ledger', label: '用印台账', description: '按审批完成时间查询和导出' },
];

export default function DocumentCenterPage(props) {
  const { currentUser, projectId, theme: T, sealApplicationTarget } = props;
  const [activeTab, setActiveTab] = useState('library');
  const projectMenuCodes = collectProjectMenuCodes(currentUser, projectId);
  const canViewLibrary = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ['DOCUMENT_LIBRARY'], ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT']);
  const canViewSeal = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ['DOCUMENT_SEAL'], ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT']);
  const canViewLedger = isPlatformAdmin(currentUser)
    || (canViewSeal && hasProjectPermission(currentUser, projectId, 'seal.view', 'seal.manage', 'seal.export'));
  const directSealAccess = Boolean(sealApplicationTarget?.id);
  const visibleTabs = useMemo(
    () => TABS.filter((tab) => {
      if (tab.id === 'library') return canViewLibrary;
      if (tab.id === 'seal') return canViewSeal;
      if (tab.id === 'ledger') return canViewLedger;
      return false;
    }),
    [canViewLedger, canViewLibrary, canViewSeal],
  );

  useEffect(() => {
    if (activeTab === 'seal' && directSealAccess) return;
    if (!visibleTabs.some((tab) => tab.id === activeTab)) setActiveTab(visibleTabs[0]?.id || 'library');
  }, [activeTab, directSealAccess, visibleTabs]);

  useEffect(() => {
    if (sealApplicationTarget?.id) setActiveTab('seal');
  }, [sealApplicationTarget]);

  const variables = {
    '--document-center-bg': T.pageBg,
    '--document-center-card': T.cardBg,
    '--document-center-border': T.borderColor,
    '--document-center-text': T.textPrimary,
    '--document-center-secondary': T.textSecondary,
    '--document-center-muted': T.textMuted,
    '--document-center-accent': T.accent,
    '--document-center-active': T.activeItemBg,
  };

  return (
    <div className="document-center" style={variables}>
      <div className="document-center-tabs" role="tablist" aria-label="资料管理功能">
        {visibleTabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            className={activeTab === tab.id ? 'active' : ''}
            onClick={() => setActiveTab(tab.id)}
          >
            <strong>{tab.label}</strong>
            <span>{tab.description}</span>
          </button>
        ))}
      </div>
      <div className="document-center-content">
        {activeTab === 'library' && canViewLibrary && <DocumentManagementPage {...props} />}
        {activeTab === 'seal' && (canViewSeal || directSealAccess) && <SealManagementPage {...props} initialApplicationId={sealApplicationTarget?.id} initialScope="INITIATED" mode="applications" />}
        {activeTab === 'ledger' && canViewLedger && <SealManagementPage {...props} initialScope="ALL" mode="ledger" />}
        {!visibleTabs.length && !directSealAccess && <div className="document-center-empty">当前角色没有可访问的资料管理页签</div>}
      </div>
    </div>
  );
}
