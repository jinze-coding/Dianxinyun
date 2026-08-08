import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  createSiteVisitInvitation,
  exportSiteVisitVisitors,
  getSiteVisitHostOptions,
  getSiteVisitInvitation,
  getSiteVisitInvitations,
  getSiteVisitMiniCode,
  updateSiteVisitInvitation,
  voidSiteVisitInvitation,
} from '../../services/siteAccess';
import { confirmAdministrativeDeletion } from '../../services/administrativeDeletion';
import { hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import {
  formatLocalDate,
  siteVisitDateRange,
  validateSiteVisitDateRange,
} from '../../utils/siteAccessDates';
import { filterSiteVisitHosts } from '../../utils/siteAccessHosts';
import './index.css';
import './siteAccessExport.css';
import './siteAccessHostPicker.css';

const PAGE_SIZE = 20;
const STATUS_LABELS = {
  PENDING: '待填写',
  SUBMITTED: '已提交',
  EXPIRED: '已过期',
  VOIDED: '已作废',
};
const PERIOD_LABELS = {
  DAY: '指定日期',
  WEEK: '自然周',
  MONTH: '自然月',
  CUSTOM: '自定义范围',
};
const AUDIT_LABELS = {
  CREATE: '创建邀请',
  SUBMIT: '访客提交',
  UPDATE: '内部修改',
  VOID: '作废邀请',
  EXPORT: '导出数据',
};

const responseData = (response, fallback) => {
  if (response?.code !== 200) throw new Error(response?.message || fallback);
  return response.data;
};
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const inputDateTime = (value) => value ? String(value).slice(0, 16) : '';
const defaultTimes = () => {
  const start = new Date();
  start.setMinutes(0, 0, 0);
  start.setHours(start.getHours() + 1);
  const end = new Date(start);
  end.setHours(end.getHours() + 2);
  const local = (value) => {
    const offset = value.getTimezoneOffset() * 60000;
    return new Date(value.getTime() - offset).toISOString().slice(0, 16);
  };
  return { visitStartTime: local(start), visitEndTime: local(end) };
};
const emptyForm = () => ({
  ...defaultTimes(),
  purpose: '',
  visitLocation: '',
  hostUserId: '',
  internalRemark: '',
  visitorCompany: '',
  contactName: '',
  contactPhone: '',
  contactIdCard: '',
  companions: [],
  travelMode: 'OTHER',
  vehiclePlate: '',
  visitorRemark: '',
});

function Modal({ title, onClose, children, width = 720 }) {
  return (
    <div className="site-access-modal-mask" onMouseDown={onClose}>
      <div className="site-access-modal" style={{ width }} onMouseDown={(event) => event.stopPropagation()}>
        <div className="site-access-modal-head">
          <strong>{title}</strong>
          <button type="button" onClick={onClose}>×</button>
        </div>
        {children}
      </div>
    </div>
  );
}

function FormField({ label, required, full, children }) {
  return (
    <label className={`site-access-field${full ? ' full' : ''}`}>
      <span>{label}{required && <em>*</em>}</span>
      {children}
    </label>
  );
}

const hostOptionLabel = (host) => host
  ? `${host.realName || '未命名成员'}${host.phone ? ` · ${host.phone}` : ''}`
  : '';

function HostCombobox({ hosts, value, onChange }) {
  const rootRef = useRef(null);
  const inputRef = useRef(null);
  const selectedHost = hosts.find((host) => String(host.userId) === String(value));
  const selectedLabel = hostOptionLabel(selectedHost);
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState(selectedLabel);
  const [activeIndex, setActiveIndex] = useState(-1);
  const searchKeyword = keyword === selectedLabel ? '' : keyword;
  const matchedHosts = useMemo(
    () => filterSiteVisitHosts(hosts, searchKeyword),
    [hosts, searchKeyword],
  );

  const closeAndRestore = useCallback(() => {
    setOpen(false);
    setKeyword(selectedLabel);
    setActiveIndex(-1);
  }, [selectedLabel]);

  useEffect(() => {
    if (!open) setKeyword(selectedLabel);
  }, [open, selectedLabel]);

  useEffect(() => {
    if (!open) return undefined;
    const handleOutsideClick = (event) => {
      if (!rootRef.current?.contains(event.target)) closeAndRestore();
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [closeAndRestore, open]);

  useEffect(() => {
    setActiveIndex((current) => Math.min(current, matchedHosts.length - 1));
  }, [matchedHosts.length]);

  const chooseHost = (host) => {
    onChange(String(host.userId));
    setKeyword(hostOptionLabel(host));
    setOpen(false);
    setActiveIndex(-1);
  };

  const handleKeyDown = (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((current) => matchedHosts.length
        ? Math.min(matchedHosts.length - 1, current + 1)
        : -1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((current) => matchedHosts.length ? Math.max(0, current - 1) : -1);
    } else if (event.key === 'Enter' && open && activeIndex >= 0 && matchedHosts[activeIndex]) {
      event.preventDefault();
      chooseHost(matchedHosts[activeIndex]);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      closeAndRestore();
    } else if (event.key === 'Tab') {
      closeAndRestore();
    }
  };

  return (
    <div className={`site-access-host-picker${open ? ' open' : ''}`} ref={rootRef}>
      <input
        ref={inputRef}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls="site-access-host-options"
        aria-activedescendant={open && activeIndex >= 0 ? `site-access-host-option-${matchedHosts[activeIndex]?.userId}` : undefined}
        autoComplete="off"
        value={keyword}
        placeholder="输入姓名或手机号搜索"
        onFocus={(event) => {
          setOpen(true);
          setActiveIndex(-1);
          event.currentTarget.select();
        }}
        onClick={() => setOpen(true)}
        onChange={(event) => {
          setKeyword(event.target.value);
          setOpen(true);
          setActiveIndex(-1);
        }}
        onKeyDown={handleKeyDown}
      />
      <button className="site-access-host-toggle" type="button" aria-label={open ? '收起接待人列表' : '展开接待人列表'} onClick={() => {
        if (open) {
          closeAndRestore();
        } else {
          setOpen(true);
          window.setTimeout(() => inputRef.current?.focus(), 0);
        }
      }}>⌄</button>
      {open && <div id="site-access-host-options" className="site-access-host-options" role="listbox" aria-label="接待人搜索结果">
        {matchedHosts.map((host, index) => <button
          id={`site-access-host-option-${host.userId}`}
          key={host.userId}
          type="button"
          role="option"
          aria-selected={String(host.userId) === String(value)}
          className={`${index === activeIndex ? 'active' : ''}${String(host.userId) === String(value) ? ' selected' : ''}`}
          onMouseDown={(event) => event.preventDefault()}
          onMouseEnter={() => setActiveIndex(index)}
          onClick={() => chooseHost(host)}
        >
          <span>{host.realName || '未命名成员'}</span>
          <small>{host.phone || '未填写手机号'}</small>
          {String(host.userId) === String(value) && <b>已选择</b>}
        </button>)}
        {!matchedHosts.length && <div className="site-access-host-empty">没有匹配的项目成员</div>}
      </div>}
    </div>
  );
}

export default function SiteAccessManagementPage({ projectId, theme: T, currentUser }) {
  const today = useMemo(() => formatLocalDate(new Date()), []);
  const [periodMode, setPeriodMode] = useState('DAY');
  const [anchorDate, setAnchorDate] = useState(today);
  const [customStart, setCustomStart] = useState(today);
  const [customEnd, setCustomEnd] = useState(today);
  const [status, setStatus] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [pageData, setPageData] = useState({ records: [], total: 0, pageNo: 1, pageSize: PAGE_SIZE });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [hosts, setHosts] = useState([]);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [detail, setDetail] = useState(null);
  const [qrCode, setQrCode] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [exportFilters, setExportFilters] = useState(null);

  const canManage = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, 'site_access.manage');
  const canExport = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, 'site_access.export');
  const range = useMemo(() => siteVisitDateRange(
    periodMode, anchorDate, customStart, customEnd,
  ), [anchorDate, customEnd, customStart, periodMode]);
  const exportRange = useMemo(() => exportFilters ? siteVisitDateRange(
    exportFilters.periodMode,
    exportFilters.anchorDate,
    exportFilters.customStart,
    exportFilters.customEnd,
  ) : null, [exportFilters]);
  const exportRangeError = exportRange
    ? validateSiteVisitDateRange(exportRange.startDate, exportRange.endDate)
    : '';

  const load = useCallback(async (targetPage = pageNo) => {
    if (!projectId) return;
    const rangeError = validateSiteVisitDateRange(range.startDate, range.endDate);
    if (rangeError) {
      setError(rangeError);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await getSiteVisitInvitations({
        projectId,
        status: status || undefined,
        keyword: keyword || undefined,
        startDate: range.startDate,
        endDate: range.endDate,
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
      });
      setPageData(responseData(response, '外访列表加载失败') || { records: [], total: 0 });
      setPageNo(targetPage);
    } catch (loadError) {
      setError(loadError.message || '外访列表加载失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, projectId, range.endDate, range.startDate, status]);

  useEffect(() => {
    setPageNo(1);
    setKeyword('');
    setKeywordInput('');
    setDetail(null);
    setEditing(null);
    setQrCode(null);
    setExportFilters(null);
  }, [projectId]);

  useEffect(() => {
    load(1);
  }, [projectId, periodMode, anchorDate, customStart, customEnd, status, keyword]);

  const loadHosts = async () => {
    if (!canManage) return [];
    const response = await getSiteVisitHostOptions(projectId);
    const values = responseData(response, '接待人加载失败') || [];
    setHosts(values);
    return values;
  };

  const openCreate = async () => {
    setError('');
    try {
      const values = await loadHosts();
      const next = emptyForm();
      next.hostUserId = String(values.find((item) => Number(item.userId) === Number(currentUser?.id))?.userId
        || values[0]?.userId || '');
      setForm(next);
      setEditing({ mode: 'CREATE' });
    } catch (openError) {
      setError(openError.message || '无法创建邀请');
    }
  };

  const openDetail = async (id) => {
    setError('');
    try {
      const response = await getSiteVisitInvitation(id);
      setDetail(responseData(response, '外访详情加载失败'));
    } catch (detailError) {
      setError(detailError.message || '外访详情加载失败');
    }
  };

  const openEdit = async (id) => {
    setError('');
    try {
      const [detailResponse] = await Promise.all([getSiteVisitInvitation(id), loadHosts()]);
      const value = responseData(detailResponse, '外访详情加载失败');
      const people = value.visitors || [];
      const contact = people.find((person) => person.personType === 'CONTACT') || {};
      setForm({
        visitStartTime: inputDateTime(value.visitStartTime),
        visitEndTime: inputDateTime(value.visitEndTime),
        purpose: value.purpose || '',
        visitLocation: value.visitLocation || '',
        hostUserId: String(value.hostUserId || ''),
        internalRemark: value.internalRemark || '',
        visitorCompany: value.visitorCompany || '',
        contactName: value.contactName || contact.personName || '',
        contactPhone: value.contactPhone || '',
        contactIdCard: contact.idCard || '',
        companions: people.filter((person) => person.personType === 'COMPANION')
          .map((person) => ({ personName: person.personName || '', idCard: person.idCard || '' })),
        travelMode: value.travelMode || 'OTHER',
        vehiclePlate: value.vehiclePlate || '',
        visitorRemark: value.visitorRemark || '',
      });
      setEditing({ mode: 'EDIT', value });
    } catch (editError) {
      setError(editError.message || '无法修改邀请');
    }
  };

  const updateCompanion = (index, field, value) => {
    setForm((current) => ({
      ...current,
      companions: current.companions.map((item, itemIndex) => itemIndex === index
        ? { ...item, [field]: value } : item),
    }));
  };

  const save = async () => {
    setSaving(true);
    setError('');
    try {
      const payload = {
        projectId,
        visitStartTime: form.visitStartTime,
        visitEndTime: form.visitEndTime,
        purpose: form.purpose.trim(),
        visitLocation: form.visitLocation.trim(),
        hostUserId: Number(form.hostUserId),
        internalRemark: form.internalRemark.trim() || null,
      };
      let saved;
      if (editing.mode === 'CREATE') {
        saved = responseData(await createSiteVisitInvitation(payload), '邀请创建失败');
      } else {
        const submitted = editing.value.status === 'SUBMITTED';
        saved = responseData(await updateSiteVisitInvitation(editing.value.id, {
          ...payload,
          visitorCompany: submitted ? form.visitorCompany.trim() : null,
          contactName: submitted ? form.contactName.trim() : null,
          contactPhone: submitted ? form.contactPhone.trim() : null,
          contactIdCard: submitted ? form.contactIdCard.trim() : null,
          companions: submitted ? form.companions.map((item) => ({
            personName: item.personName.trim(),
            idCard: item.idCard.trim(),
          })) : [],
          travelMode: submitted ? form.travelMode : null,
          vehiclePlate: submitted && form.travelMode === 'DRIVING' ? form.vehiclePlate.trim() : null,
          visitorRemark: submitted ? form.visitorRemark.trim() || null : null,
        }), '邀请修改失败');
      }
      const created = editing.mode === 'CREATE';
      setEditing(null);
      setNotice(created ? '邀请已创建，请转发专属小程序码' : '外访信息已保存并记录审计');
      await load(1);
      if (created) await showQr(saved.id);
    } catch (saveError) {
      setError(saveError.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const showQr = async (id) => {
    setError('');
    try {
      const response = await getSiteVisitMiniCode(id);
      setQrCode(responseData(response, '小程序码生成失败'));
    } catch (qrError) {
      setError(qrError.message || '小程序码生成失败');
    }
  };

  const voidInvitation = async (item) => {
    const reason = window.prompt(`请输入作废邀请 ${item.inviteNo} 的原因`);
    if (!reason?.trim()) return;
    try {
      await voidSiteVisitInvitation(item.id, reason.trim());
      setNotice('邀请已作废');
      await load(pageNo);
      if (detail?.id === item.id) setDetail(null);
    } catch (voidError) {
      setError(voidError.message || '作废失败');
    }
  };

  const deleteInvitation = async (item) => {
    setError('');
    try {
      const deleted = await confirmAdministrativeDeletion('SITE_ACCESS_INVITATION', item.id);
      if (!deleted) return;
      if (detail?.id === item.id) setDetail(null);
      setNotice('外访邀请及关联人员信息已永久删除');
      const targetPage = records.length === 1 && pageNo > 1 ? pageNo - 1 : pageNo;
      await load(targetPage);
    } catch (deleteError) {
      setError(deleteError.message || '外访邀请删除失败');
    }
  };

  const downloadQr = () => {
    if (!qrCode?.imageContent) return;
    const link = document.createElement('a');
    link.href = qrCode.imageContent;
    link.download = `外访邀请_${qrCode.inviteNo}.png`;
    link.click();
  };

  const openExport = () => {
    setError('');
    setExportFilters({
      periodMode,
      anchorDate,
      customStart,
      customEnd,
      status: status || 'SUBMITTED',
      keyword,
    });
  };

  const exportVisitors = async () => {
    if (!exportFilters || !exportRange) return;
    if (exportRangeError) {
      setError(exportRangeError);
      return;
    }
    setExporting(true);
    setError('');
    try {
      const blob = await exportSiteVisitVisitors({
        projectId,
        status: exportFilters.status,
        keyword: exportFilters.keyword.trim() || undefined,
        startDate: exportRange.startDate,
        endDate: exportRange.endDate,
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `场内管理_外访人员_${exportRange.startDate}-${exportRange.endDate}.xlsx`;
      link.click();
      URL.revokeObjectURL(url);
      setExportFilters(null);
      setNotice(`已按${PERIOD_LABELS[exportFilters.periodMode]}导出 ${exportRange.startDate} 至 ${exportRange.endDate} 的外访人员`);
    } catch (exportError) {
      setError(exportError.message || '导出失败');
    } finally {
      setExporting(false);
    }
  };

  const records = pageData.records || pageData.items || [];
  const totalPages = Math.max(1, Math.ceil(Number(pageData.total || 0) / PAGE_SIZE));

  return (
    <div className="site-access-page" style={{ '--sa-accent': T.accent, '--sa-border': T.borderColor, '--sa-card': T.cardBg, '--sa-page': T.pageBg, '--sa-text': T.textPrimary, '--sa-secondary': T.textSecondary, '--sa-muted': T.textMuted }}>
      <section className="site-access-title-card">
        <div>
          <h1>场内管理</h1>
          <p>创建单次外访邀请，通过免登录小程序提前收集人员和车辆信息。</p>
        </div>
        <div className="site-access-head-actions">
          {canExport && <button className="secondary" type="button" disabled={exporting} onClick={openExport}>{exporting ? '导出中...' : '导出外访人员'}</button>}
          {canManage && <button className="primary" type="button" onClick={openCreate}>新建邀请</button>}
        </div>
      </section>

      <section className="site-access-filter-card">
        <div className="site-access-period-tabs">
          {[['DAY', '日'], ['WEEK', '周'], ['MONTH', '月'], ['CUSTOM', '自定义']].map(([value, label]) => (
            <button type="button" key={value} className={periodMode === value ? 'active' : ''} onClick={() => setPeriodMode(value)}>{label}</button>
          ))}
        </div>
        {periodMode === 'CUSTOM' ? (
          <div className="site-access-date-pair">
            <input type="date" value={customStart} onChange={(event) => setCustomStart(event.target.value)} />
            <span>至</span>
            <input type="date" value={customEnd} onChange={(event) => setCustomEnd(event.target.value)} />
          </div>
        ) : <input type="date" value={anchorDate} onChange={(event) => setAnchorDate(event.target.value)} />}
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="">全部状态</option>
          <option value="PENDING">待填写</option>
          <option value="SUBMITTED">已提交</option>
          <option value="EXPIRED">已过期</option>
          <option value="VOIDED">已作废</option>
        </select>
        <div className="site-access-keyword">
          <input value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} onKeyDown={(event) => {
            if (event.key === 'Enter') setKeyword(keywordInput.trim());
          }} placeholder="邀请编号、单位、联系人、车牌、接待人" />
          <button type="button" onClick={() => setKeyword(keywordInput.trim())}>查询</button>
        </div>
        <div className="site-access-range-label">{range.startDate} 至 {range.endDate}</div>
      </section>

      {notice && <div className="site-access-notice" onClick={() => setNotice('')}>{notice}</div>}
      {error && <div className="site-access-error" onClick={() => setError('')}>{error}</div>}

      <section className="site-access-table-card">
        <div className="site-access-table-wrap">
          <table>
            <thead><tr><th>邀请编号</th><th>计划来访时间</th><th>外访单位 / 联系人</th><th>人数</th><th>出行</th><th>接待人</th><th>状态</th><th>创建 / 提交</th><th>操作</th></tr></thead>
            <tbody>
              {!loading && records.map((item) => (
                <tr key={item.id}>
                  <td><button className="link" type="button" onClick={() => openDetail(item.id)}>{item.inviteNo}</button></td>
                  <td>{formatDateTime(item.visitStartTime)}<small>至 {formatDateTime(item.visitEndTime)}</small></td>
                  <td>{item.visitorCompany || '等待访客填写'}<small>{item.contactName || '-'}</small></td>
                  <td>{item.visitorCount || 0}</td>
                  <td>{item.travelMode === 'DRIVING' ? `驾车 · ${item.vehiclePlate || '-'}` : item.travelMode ? '非驾车' : '-'}</td>
                  <td>{item.hostName || '-'}</td>
                  <td><span className={`site-access-status ${String(item.status || '').toLowerCase()}`}>{STATUS_LABELS[item.status] || item.status}</span></td>
                  <td>{formatDateTime(item.createTime)}<small>{item.submittedTime ? `提交 ${formatDateTime(item.submittedTime)}` : '尚未提交'}</small></td>
                  <td><div className="site-access-row-actions">
                    <button type="button" onClick={() => openDetail(item.id)}>详情</button>
                    {canManage && item.status === 'PENDING' && <button type="button" onClick={() => showQr(item.id)}>小程序码</button>}
                    {canManage && ['PENDING', 'SUBMITTED'].includes(item.status) && <button type="button" onClick={() => openEdit(item.id)}>修改</button>}
                    {canManage && ['PENDING', 'SUBMITTED'].includes(item.status) && <button className="danger" type="button" onClick={() => voidInvitation(item)}>作废</button>}
                    {isPlatformAdmin(currentUser) && <button className="danger" type="button" onClick={() => deleteInvitation(item)}>删除</button>}
                  </div></td>
                </tr>
              ))}
              {!loading && !records.length && <tr><td colSpan="9" className="site-access-empty">当前日期范围没有外访邀请</td></tr>}
              {loading && <tr><td colSpan="9" className="site-access-empty">正在加载...</td></tr>}
            </tbody>
          </table>
        </div>
        <div className="site-access-pagination">
          <span>共 {pageData.total || 0} 条 · 第 {pageNo}/{totalPages} 页</span>
          <button type="button" disabled={pageNo <= 1 || loading} onClick={() => load(pageNo - 1)}>上一页</button>
          <button type="button" disabled={pageNo >= totalPages || loading} onClick={() => load(pageNo + 1)}>下一页</button>
        </div>
      </section>

      {exportFilters && <Modal title="筛选并导出外访人员" onClose={() => !exporting && setExportFilters(null)} width={620}>
        <div className="site-access-export-form">
          <FormField label="日期单位" full>
            <div className="site-access-period-tabs">
              {[['DAY', '日'], ['WEEK', '周'], ['MONTH', '月'], ['CUSTOM', '自定义']].map(([value, label]) => (
                <button type="button" key={value} className={exportFilters.periodMode === value ? 'active' : ''} onClick={() => setExportFilters({ ...exportFilters, periodMode: value })}>{label}</button>
              ))}
            </div>
          </FormField>
          {exportFilters.periodMode === 'CUSTOM' ? <>
            <FormField label="开始日期" required><input type="date" value={exportFilters.customStart} onChange={(event) => setExportFilters({ ...exportFilters, customStart: event.target.value })} /></FormField>
            <FormField label="结束日期" required><input type="date" value={exportFilters.customEnd} onChange={(event) => setExportFilters({ ...exportFilters, customEnd: event.target.value })} /></FormField>
          </> : <FormField label={exportFilters.periodMode === 'DAY' ? '指定日期' : exportFilters.periodMode === 'WEEK' ? '所在周日期' : '所在月日期'} required full>
            <input type="date" value={exportFilters.anchorDate} onChange={(event) => setExportFilters({ ...exportFilters, anchorDate: event.target.value })} />
          </FormField>}
          <FormField label="导出状态" required>
            <select value={exportFilters.status} onChange={(event) => setExportFilters({ ...exportFilters, status: event.target.value })}>
              <option value="SUBMITTED">已提交</option>
              <option value="VOIDED">已作废</option>
              <option value="PENDING">待填写</option>
              <option value="EXPIRED">已过期</option>
            </select>
          </FormField>
          <FormField label="关键词">
            <input value={exportFilters.keyword} onChange={(event) => setExportFilters({ ...exportFilters, keyword: event.target.value })} placeholder="单位、联系人、车牌、接待人" />
          </FormField>
          <div className="site-access-export-summary full">
            <strong>{PERIOD_LABELS[exportFilters.periodMode]}</strong>
            <span>{exportRange?.startDate || '-'} 至 {exportRange?.endDate || '-'}</span>
            <small className={exportRangeError ? 'error' : ''}>{exportRangeError || '日期按计划到场时间计算；一名来访人员一行。默认导出已提交记录，最长可自定义 366 天。'}</small>
          </div>
        </div>
        <div className="site-access-modal-actions"><button type="button" disabled={exporting} onClick={() => setExportFilters(null)}>取消</button><button className="primary" type="button" disabled={exporting || Boolean(exportRangeError)} onClick={exportVisitors}>{exporting ? '正在导出...' : '导出 Excel'}</button></div>
      </Modal>}

      {editing && <Modal title={editing.mode === 'CREATE' ? '新建外访邀请' : `修改 ${editing.value.inviteNo}`} onClose={() => setEditing(null)}>
        <div className="site-access-form-grid">
          <FormField label="计划到场" required><input type="datetime-local" value={form.visitStartTime} onChange={(event) => setForm({ ...form, visitStartTime: event.target.value })} /></FormField>
          <FormField label="计划离场" required><input type="datetime-local" value={form.visitEndTime} onChange={(event) => setForm({ ...form, visitEndTime: event.target.value })} /></FormField>
          <FormField label="来访事由" required full><input value={form.purpose} maxLength="300" onChange={(event) => setForm({ ...form, purpose: event.target.value })} /></FormField>
          <FormField label="到访地点" required><input value={form.visitLocation} maxLength="200" onChange={(event) => setForm({ ...form, visitLocation: event.target.value })} /></FormField>
          <FormField label="接待人" required><HostCombobox hosts={hosts} value={form.hostUserId} onChange={(hostUserId) => setForm({ ...form, hostUserId })} /></FormField>
          <FormField label="内部备注" full><textarea value={form.internalRemark} maxLength="500" onChange={(event) => setForm({ ...form, internalRemark: event.target.value })} /></FormField>

          {editing.value?.status === 'SUBMITTED' && <>
            <div className="site-access-form-section full">访客已提交信息（修改会写入加密审计）</div>
            <FormField label="外访单位" required full><input value={form.visitorCompany} onChange={(event) => setForm({ ...form, visitorCompany: event.target.value })} /></FormField>
            <FormField label="主联系人" required><input value={form.contactName} onChange={(event) => setForm({ ...form, contactName: event.target.value })} /></FormField>
            <FormField label="手机号" required><input value={form.contactPhone} maxLength="11" onChange={(event) => setForm({ ...form, contactPhone: event.target.value })} /></FormField>
            <FormField label="主联系人身份证号" required full><input value={form.contactIdCard} maxLength="18" onChange={(event) => setForm({ ...form, contactIdCard: event.target.value.toUpperCase() })} /></FormField>
            <div className="site-access-companions full">
              <div className="site-access-companion-head"><strong>同行人员</strong><button type="button" disabled={form.companions.length >= 49} onClick={() => setForm({ ...form, companions: [...form.companions, { personName: '', idCard: '' }] })}>添加同行人</button></div>
              {form.companions.map((item, index) => <div className="site-access-companion-row" key={`companion-${index}`}>
                <input placeholder="姓名" value={item.personName} onChange={(event) => updateCompanion(index, 'personName', event.target.value)} />
                <input placeholder="18位身份证号" maxLength="18" value={item.idCard} onChange={(event) => updateCompanion(index, 'idCard', event.target.value.toUpperCase())} />
                <button type="button" className="danger" onClick={() => setForm({ ...form, companions: form.companions.filter((_, itemIndex) => itemIndex !== index) })}>移除</button>
              </div>)}
            </div>
            <FormField label="出行方式" required><select value={form.travelMode} onChange={(event) => setForm({ ...form, travelMode: event.target.value, vehiclePlate: event.target.value === 'OTHER' ? '' : form.vehiclePlate })}><option value="OTHER">非驾车</option><option value="DRIVING">驾车</option></select></FormField>
            <FormField label="车牌号" required={form.travelMode === 'DRIVING'}><input disabled={form.travelMode !== 'DRIVING'} value={form.vehiclePlate} onChange={(event) => setForm({ ...form, vehiclePlate: event.target.value.toUpperCase() })} /></FormField>
            <FormField label="外访备注" full><textarea value={form.visitorRemark} maxLength="500" onChange={(event) => setForm({ ...form, visitorRemark: event.target.value })} /></FormField>
          </>}
        </div>
        <div className="site-access-modal-actions"><button type="button" onClick={() => setEditing(null)}>取消</button><button className="primary" type="button" disabled={saving} onClick={save}>{saving ? '保存中...' : '保存'}</button></div>
      </Modal>}

      {detail && <div className="site-access-drawer-mask" onMouseDown={() => setDetail(null)}><aside className="site-access-drawer" onMouseDown={(event) => event.stopPropagation()}>
        <div className="site-access-modal-head"><div><strong>{detail.inviteNo}</strong><span className={`site-access-status ${String(detail.status || '').toLowerCase()}`}>{STATUS_LABELS[detail.status] || detail.status}</span></div><button type="button" onClick={() => setDetail(null)}>×</button></div>
        <div className="site-access-detail-grid">
          {[['项目', detail.projectName], ['计划时间', `${formatDateTime(detail.visitStartTime)} 至 ${formatDateTime(detail.visitEndTime)}`], ['来访事由', detail.purpose], ['到访地点', detail.visitLocation], ['接待人', `${detail.hostName || '-'} ${detail.hostPhone || ''}`], ['外访单位', detail.visitorCompany || '-'], ['联系人', `${detail.contactName || '-'} ${detail.contactPhone || ''}`], ['出行方式', detail.travelMode === 'DRIVING' ? `驾车 · ${detail.vehiclePlate || '-'}` : detail.travelMode ? '非驾车' : '-'], ['内部备注', detail.internalRemark || '-'], ['外访备注', detail.visitorRemark || '-']].map(([label, value]) => <div key={label}><span>{label}</span><b>{value}</b></div>)}
        </div>
        <h3>入场人员（{detail.visitors?.length || 0}）</h3>
        <div className="site-access-person-list">{(detail.visitors || []).map((person) => <div key={person.id}><span>{person.personType === 'CONTACT' ? '主联系人' : '同行人员'}</span><b>{person.personName}</b><code>{person.idCard}</code></div>)}{!detail.visitors?.length && <p>等待访客填写</p>}</div>
        <h3>操作记录</h3>
        <div className="site-access-audit-list">{(detail.auditLogs || []).map((log) => <div key={log.id}><b>{AUDIT_LABELS[log.actionType] || log.actionType}</b><span>{log.operatorName} · {formatDateTime(log.createTime)}</span><p>{log.comment || '-'}</p></div>)}</div>
        {detail.voidReason && <div className="site-access-void-reason">作废原因：{detail.voidReason}</div>}
      </aside></div>}

      {qrCode && <Modal title={`专属小程序码 · ${qrCode.inviteNo}`} onClose={() => setQrCode(null)} width={430}>
        <div className="site-access-qr">
          {qrCode.imageContent ? <img src={qrCode.imageContent} alt="外访邀请小程序码" /> : <div className="site-access-scene"><span>开发调试 scene</span><code>{qrCode.sceneCode}</code></div>}
          <p>{qrCode.hint}</p>
          <small>页面：{qrCode.pagePath}</small>
        </div>
        <div className="site-access-modal-actions"><button type="button" onClick={() => setQrCode(null)}>关闭</button>{qrCode.imageContent && <button className="primary" type="button" onClick={downloadQr}>下载小程序码</button>}</div>
      </Modal>}
    </div>
  );
}
