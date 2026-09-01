import React, { useEffect, useMemo, useState } from 'react';
import DocumentManagementPage from '../DocumentManagement';
import SealManagementPage from '../SealManagement';
import DocumentCirculationPage from '../DocumentCirculation';
import { collectProjectMenuCodes, hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import { pageMenuAllowed } from '../../utils/roleAuthorization';
import './index.css';

const TABS = [
  { id: 'library', label: '工程资料', description: '目录、版本、预览、归档与回收站' },
  { id: 'seal', label: '用印管理', description: '申请、审批、盖章、台账与归档' },
  { id: 'circulation', label: '图纸收发', description: '收文、发放、扫码签收与版本追溯' },
];

const SEAL_TABS = [
  { id: 'applications', label: '用印申请', description: '发起、审批、盖章与归档' },
  { id: 'ledger', label: '用印台账', description: '查询审批结果并导出' },
];

export default function DocumentCenterPage(props) {
  const { currentUser, projectId, theme: T, sealApplicationTarget, documentDistributionTarget } = props;
  const [activeTab, setActiveTab] = useState('library');
  const [activeSealTab, setActiveSealTab] = useState('applications');
  const projectMenuCodes = collectProjectMenuCodes(currentUser, projectId);
  const canViewLibrary = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ['DOCUMENT_LIBRARY'], ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT']);
  const canViewSeal = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ['DOCUMENT_SEAL'], ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT']);
  const canViewLedger = isPlatformAdmin(currentUser)
    || (canViewSeal && hasProjectPermission(currentUser, projectId, 'seal.view', 'seal.manage', 'seal.export'));
  const directSealAccess = Boolean(sealApplicationTarget?.id);
  const directCirculationAccess = Boolean(documentDistributionTarget?.id);
  const canViewCirculation = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ['DOCUMENT_CIRCULATION'], ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT'])
    || hasProjectPermission(currentUser, projectId, 'document.circulation.view', 'document.receive', 'document.issue');
  const visibleTabs = useMemo(
    () => TABS.filter((tab) => {
      if (tab.id === 'library') return canViewLibrary;
      if (tab.id === 'seal') return canViewSeal;
      if (tab.id === 'circulation') return canViewCirculation;
      return false;
    }),
    [canViewCirculation, canViewLibrary, canViewSeal],
  );
  const visibleSealTabs = useMemo(
    () => SEAL_TABS.filter((tab) => {
      if (tab.id === 'applications') return canViewSeal || directSealAccess;
      if (tab.id === 'ledger') return canViewLedger;
      return false;
    }),
    [canViewLedger, canViewSeal, directSealAccess],
  );

  useEffect(() => {
    if ((activeTab === 'seal' && directSealAccess) || (activeTab === 'circulation' && directCirculationAccess)) return;
    if (!visibleTabs.some((tab) => tab.id === activeTab)) setActiveTab(visibleTabs[0]?.id || 'library');
  }, [activeTab, directCirculationAccess, directSealAccess, visibleTabs]);

  useEffect(() => {
    if (sealApplicationTarget?.id) {
      setActiveTab('seal');
      setActiveSealTab('applications');
    }
  }, [sealApplicationTarget]);

  useEffect(() => {
    if (!visibleSealTabs.some((tab) => tab.id === activeSealTab)) {
      setActiveSealTab(visibleSealTabs[0]?.id || 'applications');
    }
  }, [activeSealTab, visibleSealTabs]);

  useEffect(() => {
    if (documentDistributionTarget?.id) setActiveTab('circulation');
  }, [documentDistributionTarget]);

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
        {activeTab === 'seal' && (canViewSeal || directSealAccess) && (
          <div className="document-center-seal">
            <nav className="document-center-subtabs" role="tablist" aria-label="用印管理功能">
              {visibleSealTabs.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  role="tab"
                  aria-selected={activeSealTab === tab.id}
                  className={activeSealTab === tab.id ? 'active' : ''}
                  onClick={() => setActiveSealTab(tab.id)}
                >
                  <strong>{tab.label}</strong>
                  <span>{tab.description}</span>
                </button>
              ))}
            </nav>
            {activeSealTab === 'applications' && <SealManagementPage {...props} initialApplicationId={sealApplicationTarget?.id} initialScope="INITIATED" mode="applications" />}
            {activeSealTab === 'ledger' && canViewLedger && <SealManagementPage {...props} initialScope="ALL" mode="ledger" />}
          </div>
        )}
        {activeTab === 'circulation' && (canViewCirculation || directCirculationAccess) && <DocumentCirculationPage {...props} distributionTarget={documentDistributionTarget} />}
        {!visibleTabs.length && !directSealAccess && !directCirculationAccess && <div className="document-center-empty">当前角色没有可访问的资料管理页签</div>}
      </div>
    </div>
  );
}
