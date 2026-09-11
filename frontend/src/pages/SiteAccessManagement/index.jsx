import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  createSiteVisitInvitation,
  exportSiteVisitVisitors,
  exportMeetingVisitRegistrations,
  getSiteVisitHostOptions,
  getSiteVisitInvitation,
  getSiteVisitInvitations,
  getSiteVisitMiniCode,
  getMeetingCheckinMiniCode,
  getSiteVisitorProfile,
  getSiteVisitorProfiles,
  disableSiteVisitorProfile,
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
import {
  SITE_ACCESS_MEETING_CREATION_ENABLED,
  WEB_RELEASE_MARKER,
  resolveCreatedInvitationType,
} from '../../utils/releaseFeatures';
import {
  createSiteAccessProfileRequestGuard,
  siteAccessProfileBelongsToProject,
} from '../../utils/siteAccessProfileRequests';
import GuardVisitPanel from './GuardVisitPanel';
import { meetingQrImage, meetingScreenUrl } from './meetingScreenModel';
import { saveMeetingScreenReturn } from './meetingScreenNavigation';
import MeetingRegistrationPanel from './MeetingRegistrationPanel';
import MeetingDetailPage from './MeetingDetailPage';
import {
  createSiteAccessRequestGuard,
  normalizeInvitationStatusForType,
} from './meetingManagement';
import './index.css';
import './siteAccessExport.css';
import './siteAccessHostPicker.css';
import './siteAccessProfiles.css';
import './guardVisits.css';
import './meetingVisits.css';
import './meetingMaterials.css';

const PAGE_SIZE = 20;
const STATUS_LABELS = {
  PENDING: '待填写',
  SUBMITTED: '已登记',
  OPEN: '开放登记',
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
const hasCompanionContent = (person) => Boolean(
  person.personCompany?.trim() || person.personName?.trim() || person.personPhone?.trim()
);
const isInvitationActive = (invitation, now = Date.now()) => {
  if (!['PENDING', 'SUBMITTED', 'OPEN'].includes(invitation?.status)) return false;
  const visitEndTime = new Date(invitation.visitEndTime).getTime();
  return Number.isFinite(visitEndTime) && visitEndTime > now;
};
const effectiveInvitationStatus = (invitation, now = Date.now()) => {
  if (['PENDING', 'SUBMITTED', 'OPEN'].includes(invitation?.status)) {
    const visitEndTime = new Date(invitation.visitEndTime).getTime();
    if (Number.isFinite(visitEndTime) && visitEndTime <= now) return 'EXPIRED';
  }
  return invitation?.status;
};
const PersonContactFields = ({ person }) => (
  <div className="site-access-person-contact-fields">
    <b>单位：{person.personCompany || '-'}</b>
    <b>姓名：{person.personName || '-'}</b>
    <b>手机号码：{person.personPhone || '-'}</b>
  </div>
);
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
  inviteType: 'SINGLE',
  ...defaultTimes(),
  purpose: '',
  visitLocation: '',
  hostUserId: '',
  internalRemark: '',
  visitorCompany: '',
  contactName: '',
  contactPhone: '',
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
        placeholder="输入姓名或手机号码搜索"
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
          <small>{host.phone || '未填写手机号码'}</small>
          {String(host.userId) === String(value) && <b>已选择</b>}
        </button>)}
        {!matchedHosts.length && <div className="site-access-host-empty">没有匹配的项目成员</div>}
      </div>}
    </div>
  );
}

export default function SiteAccessManagementPage({ projectId, theme: T, currentUser, initialState }) {
  const [restoredState] = useState(() => initialState?.projectId === Number(projectId) ? initialState.filters : null);
  const restoreProjectRef = useRef(projectId);
  const restorePageRef = useRef(restoredState?.pageNo || 1);
  const restoreQueryRef = useRef(null);
  const restoreScrollRef = useRef(restoredState?.scrollTop || 0);
  const pageRef = useRef(null);
  const [activeSection, setActiveSection] = useState('INVITATION');
  const today = useMemo(() => formatLocalDate(new Date()), []);
  const [periodMode, setPeriodMode] = useState(restoredState?.periodMode || 'DAY');
  const [anchorDate, setAnchorDate] = useState(restoredState?.anchorDate || today);
  const [customStart, setCustomStart] = useState(restoredState?.customStart || today);
  const [customEnd, setCustomEnd] = useState(restoredState?.customEnd || today);
  const [status, setStatus] = useState(restoredState?.status || '');
  const [inviteType, setInviteType] = useState(restoredState?.inviteType || '');
  const [clockNow, setClockNow] = useState(Date.now);
  const [keywordInput, setKeywordInput] = useState(restoredState?.keywordInput || '');
  const [keyword, setKeyword] = useState(restoredState?.keyword || '');
  const [pageNo, setPageNo] = useState(restoredState?.pageNo || 1);
  const [pageData, setPageData] = useState({ records: [], total: 0, pageNo: 1, pageSize: PAGE_SIZE });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [hosts, setHosts] = useState([]);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [detail, setDetail] = useState(null);
  const [meetingTab, setMeetingTab] = useState(restoredState?.detailTab || 'checkin');
  const listScrollRef = useRef(restoredState?.scrollTop || 0);
  const [qrCode, setQrCode] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [exportFilters, setExportFilters] = useState(null);
  const [profilePanel, setProfilePanel] = useState(null);
  const [profileDetail, setProfileDetail] = useState(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const activeProjectIdRef = useRef(projectId);
  const invitationListRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const invitationDetailRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const invitationFormRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const invitationQrRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const invitationMutationRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const profileListRequestGuardRef = useRef(createSiteAccessProfileRequestGuard());
  const profileDetailRequestGuardRef = useRef(createSiteAccessProfileRequestGuard());
  const profileMutationRequestGuardRef = useRef(createSiteAccessProfileRequestGuard());
  activeProjectIdRef.current = projectId;

  useEffect(() => {
    const timer = window.setInterval(() => setClockNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

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
    const requestProjectId = projectId;
    const requestTicket = invitationListRequestGuardRef.current.begin(requestProjectId);
    const rangeError = validateSiteVisitDateRange(range.startDate, range.endDate);
    if (rangeError) {
      if (invitationListRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(rangeError);
        setLoading(false);
      }
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await getSiteVisitInvitations({
        projectId: requestProjectId,
        inviteType: inviteType || undefined,
        status: status || undefined,
        keyword: keyword || undefined,
        startDate: range.startDate,
        endDate: range.endDate,
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
      });
      if (!invitationListRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      setPageData(responseData(response, '外访列表加载失败') || { records: [], total: 0 });
      setPageNo(targetPage);
    } catch (loadError) {
      if (invitationListRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(loadError.message || '外访列表加载失败');
      }
    } finally {
      if (invitationListRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setLoading(false);
      }
    }
  }, [inviteType, keyword, pageNo, projectId, range.endDate, range.startDate, status]);

  useEffect(() => {
    invitationListRequestGuardRef.current.invalidate();
    invitationDetailRequestGuardRef.current.invalidate();
    invitationFormRequestGuardRef.current.invalidate();
    invitationQrRequestGuardRef.current.invalidate();
    invitationMutationRequestGuardRef.current.invalidate();
    profileListRequestGuardRef.current.invalidate();
    profileDetailRequestGuardRef.current.invalidate();
    profileMutationRequestGuardRef.current.invalidate();
    if (restoreProjectRef.current !== projectId) {
      restoreProjectRef.current = projectId;
      restorePageRef.current = 1;
      restoreScrollRef.current = 0;
      setPageNo(1);
      setKeyword('');
      setKeywordInput('');
    }
    setDetail(null);
    setEditing(null);
    setQrCode(null);
    setExportFilters(null);
    setProfilePanel(null);
    setProfileDetail(null);
    setProfileLoading(false);
    setLoading(false);
    setSaving(false);
  }, [projectId]);

  useEffect(() => {
    const query = JSON.stringify([projectId, periodMode, anchorDate, customStart, customEnd, inviteType, status, keyword]);
    if (restoreQueryRef.current !== null && restoreQueryRef.current !== query) restorePageRef.current = 1;
    restoreQueryRef.current = query;
    load(restorePageRef.current);
  }, [projectId, periodMode, anchorDate, customStart, customEnd, inviteType, status, keyword]);

  useEffect(() => {
    if (loading || !pageData.records?.length || !restoreScrollRef.current) return;
    if (!initialState?.filters?.detailId) pageRef.current?.scrollTo({ top: restoreScrollRef.current });
    restoreScrollRef.current = 0;
  }, [loading, pageData]);

  const openMeetingScreen = (invitationId) => {
    let returnContext = '';
    try {
      returnContext = saveMeetingScreenReturn(window.localStorage, window.crypto.randomUUID(), {
        invitationId, projectId, userId: currentUser?.id,
        filters: { periodMode, anchorDate, customStart, customEnd, inviteType, status, keyword, keywordInput, pageNo,
          scrollTop: detail?.inviteType === 'MEETING' ? listScrollRef.current : pageRef.current?.scrollTop || 0,
          detailId: detail?.inviteType === 'MEETING' ? detail.id : null, detailTab: meetingTab },
      });
    } catch { /* A restricted browser can still open the screen and return to its meeting's project. */ }
    window.open(meetingScreenUrl(invitationId, returnContext), '_blank', 'noopener,noreferrer');
  };

  const loadHosts = async (requestProjectId) => {
    const response = await getSiteVisitHostOptions(requestProjectId);
    return responseData(response, '接待人加载失败') || [];
  };

  const openCreate = async () => {
    const requestProjectId = projectId;
    const requestTicket = invitationFormRequestGuardRef.current.begin(requestProjectId);
    setError('');
    try {
      const values = await loadHosts(requestProjectId);
      if (!invitationFormRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      setHosts(values);
      const next = emptyForm();
      next.hostUserId = String(values.find((item) => Number(item.userId) === Number(currentUser?.id))?.userId
        || values[0]?.userId || '');
      setForm(next);
      setEditing({ mode: 'CREATE' });
    } catch (openError) {
      if (invitationFormRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(openError.message || '无法创建邀请');
      }
    }
  };

  const openDetail = async (id, nextTab = 'checkin', restoring = false) => {
    const requestProjectId = projectId;
    const requestTicket = invitationDetailRequestGuardRef.current.begin(requestProjectId);
    setError('');
    try {
      const response = await getSiteVisitInvitation(id);
      const value = responseData(response, '外访详情加载失败');
      if (!invitationDetailRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      if (String(value?.projectId ?? '') !== String(requestProjectId ?? '')) return;
      if (detail?.id !== id && value.inviteType === 'MEETING') {
        if (!restoring) listScrollRef.current = pageRef.current?.scrollTop || 0;
        setMeetingTab(nextTab);
        pageRef.current?.scrollTo({ top: 0 });
      }
      setDetail(value);
    } catch (detailError) {
      if (invitationDetailRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(detailError.message || '外访详情加载失败');
      }
    }
  };

  useEffect(() => {
    if (initialState?.projectId === Number(projectId) && initialState?.filters?.detailId) {
      void openDetail(initialState.filters.detailId, initialState.filters.detailTab || 'checkin', true);
    }
  }, [initialState, projectId]);

  const openEdit = async (id) => {
    const requestProjectId = projectId;
    const requestTicket = invitationFormRequestGuardRef.current.begin(requestProjectId);
    setError('');
    try {
      const [detailResponse, values] = await Promise.all([
        getSiteVisitInvitation(id),
        loadHosts(requestProjectId),
      ]);
      const value = responseData(detailResponse, '外访详情加载失败');
      if (!invitationFormRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      if (String(value?.projectId ?? '') !== String(requestProjectId ?? '')) return;
      setHosts(values);
      const people = value.visitors || [];
      const contact = people.find((person) => person.personType === 'CONTACT') || {};
      setForm({
        inviteType: value.inviteType || 'SINGLE',
        visitStartTime: inputDateTime(value.visitStartTime),
        visitEndTime: inputDateTime(value.visitEndTime),
        purpose: value.purpose || '',
        visitLocation: value.visitLocation || '',
        hostUserId: String(value.hostUserId || ''),
        internalRemark: value.internalRemark || '',
        visitorCompany: value.visitorCompany || '',
        contactName: value.contactName || contact.personName || '',
        contactPhone: value.contactPhone || '',
        companions: people.filter((person) => person.personType === 'COMPANION')
          .map((person) => ({
            personCompany: person.personCompany || '',
            personName: person.personName || '',
            personPhone: person.personPhone || '',
          })),
        travelMode: value.travelMode || 'OTHER',
        vehiclePlate: value.vehiclePlate || '',
        visitorRemark: value.visitorRemark || '',
      });
      setEditing({ mode: 'EDIT', value });
    } catch (editError) {
      if (invitationFormRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(editError.message || '无法修改邀请');
      }
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
    const requestProjectId = projectId;
    const requestTicket = invitationMutationRequestGuardRef.current.begin(requestProjectId);
    setSaving(true);
    setError('');
    try {
      const payload = {
        projectId: requestProjectId,
        inviteType: editing.mode === 'CREATE'
          ? resolveCreatedInvitationType(form.inviteType, SITE_ACCESS_MEETING_CREATION_ENABLED)
          : form.inviteType,
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
        const companions = form.companions.filter(hasCompanionContent).map((item) => ({
          personCompany: item.personCompany.trim(),
          personName: item.personName.trim(),
          personPhone: item.personPhone.trim(),
        }));
        const invalidPhoneIndex = companions.findIndex((item) => item.personPhone
          && !/^1[3-9]\d{9}$/.test(item.personPhone));
        if (submitted && invalidPhoneIndex >= 0) {
          throw new Error(`请填写第${invalidPhoneIndex + 1}位同行人员的正确手机号码`);
        }
        saved = responseData(await updateSiteVisitInvitation(editing.value.id, {
          ...payload,
          visitorCompany: submitted ? form.visitorCompany.trim() : null,
          contactName: submitted ? form.contactName.trim() : null,
          contactPhone: submitted ? form.contactPhone.trim() : null,
          companions: submitted ? companions : [],
          travelMode: submitted ? form.travelMode : null,
          vehiclePlate: submitted && form.travelMode === 'DRIVING' ? form.vehiclePlate.trim() : null,
          visitorRemark: submitted ? form.visitorRemark.trim() || null : null,
        }), '邀请修改失败');
      }
      if (!invitationMutationRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      const created = editing.mode === 'CREATE';
      setEditing(null);
      setNotice(created
        ? (saved.inviteType === 'MEETING' ? '会议邀请已创建，请转发共享会议小程序码' : '邀请已创建，请转发专属小程序码')
        : '外访信息已保存并记录审计');
      await load(created ? 1 : pageNo);
      if (saved.inviteType === 'MEETING' && (created || detail?.id === saved.id)) await openDetail(saved.id, created ? 'materials' : meetingTab);
      else if (created) await showQr(saved.id);
    } catch (saveError) {
      if (invitationMutationRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(saveError.message || '保存失败');
      }
    } finally {
      if (invitationMutationRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setSaving(false);
      }
    }
  };

  const showQr = async (id, checkin = false) => {
    const requestProjectId = projectId;
    const requestTicket = invitationQrRequestGuardRef.current.begin(requestProjectId);
    setError('');
    try {
      const response = await (checkin ? getMeetingCheckinMiniCode(id) : getSiteVisitMiniCode(id));
      if (!invitationQrRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      const data = responseData(response, '小程序码生成失败');
      setQrCode(checkin ? { ...data, imageContent: meetingQrImage(data), displayTitle: '会场签到码' } : data);
    } catch (qrError) {
      if (invitationQrRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(qrError.message || '小程序码生成失败');
      }
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
    link.download = `${qrCode.displayTitle || '外访邀请'}_${qrCode.inviteNo}.png`;
    link.click();
  };

  const openExport = () => {
    setError('');
    const exportInviteType = inviteType || 'SINGLE';
    setExportFilters({
      periodMode,
      anchorDate,
      customStart,
      customEnd,
      inviteType: exportInviteType,
      status: exportInviteType === 'MEETING'
        ? 'REGISTERED'
        : (status || 'SUBMITTED'),
      meetingStatus: exportInviteType === 'MEETING' && ['OPEN', 'EXPIRED', 'VOIDED'].includes(status)
        ? status : '',
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
      const meeting = exportFilters.inviteType === 'MEETING';
      const blob = meeting
        ? await exportMeetingVisitRegistrations({
          projectId,
          status: exportFilters.status,
          meetingStatus: exportFilters.meetingStatus || undefined,
          keyword: exportFilters.keyword.trim() || undefined,
          startDate: exportRange.startDate,
          endDate: exportRange.endDate,
        })
        : await exportSiteVisitVisitors({
          projectId,
          status: exportFilters.status,
          keyword: exportFilters.keyword.trim() || undefined,
          startDate: exportRange.startDate,
          endDate: exportRange.endDate,
        });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `场内管理_${meeting ? '会议登记' : '外访人员'}_${exportRange.startDate}-${exportRange.endDate}.xlsx`;
      link.click();
      URL.revokeObjectURL(url);
      setExportFilters(null);
      setNotice(`已按${PERIOD_LABELS[exportFilters.periodMode]}导出 ${exportRange.startDate} 至 ${exportRange.endDate} 的${meeting ? '会议登记' : '外访人员'}`);
    } catch (exportError) {
      setError(exportError.message || '导出失败');
    } finally {
      setExporting(false);
    }
  };

  const loadProfiles = async (targetPage = 1, filters = profilePanel || { status: '', keyword: '' }) => {
    const requestProjectId = projectId;
    const requestTicket = profileListRequestGuardRef.current.begin(requestProjectId);
    setProfileLoading(true);
    setError('');
    try {
      const response = await getSiteVisitorProfiles({
        projectId: requestProjectId,
        status: filters.status || undefined,
        keyword: filters.keyword?.trim() || undefined,
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
      });
      const data = responseData(response, '常用资料加载失败') || { records: [], total: 0 };
      if (!profileListRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      setProfilePanel({ ...filters, ...data, pageNo: targetPage, projectId: requestProjectId });
    } catch (profileError) {
      if (profileListRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(profileError.message || '常用资料加载失败');
      }
    } finally {
      if (profileListRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setProfileLoading(false);
      }
    }
  };

  const openProfiles = () => loadProfiles(1, { status: '', keyword: '' });

  const closeProfiles = () => {
    profileListRequestGuardRef.current.invalidate();
    profileDetailRequestGuardRef.current.invalidate();
    profileMutationRequestGuardRef.current.invalidate();
    setProfilePanel(null);
    setProfileDetail(null);
    setProfileLoading(false);
  };

  const closeProfileDetail = () => {
    profileDetailRequestGuardRef.current.invalidate();
    setProfileDetail(null);
  };

  const openProfileDetail = async (profile) => {
    const requestProjectId = projectId;
    if (!siteAccessProfileBelongsToProject(profile, requestProjectId)) {
      setError('常用资料所属项目已变化，请重新打开常用资料');
      return;
    }
    const requestTicket = profileDetailRequestGuardRef.current.begin(requestProjectId);
    setError('');
    try {
      const response = await getSiteVisitorProfile(profile.id);
      if (!profileDetailRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      setProfileDetail(responseData(response, '常用资料详情加载失败'));
    } catch (profileError) {
      if (profileDetailRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(profileError.message || '常用资料详情加载失败');
      }
    }
  };

  const disableProfile = async (profile) => {
    const requestProjectId = projectId;
    if (!siteAccessProfileBelongsToProject(profile, requestProjectId)) {
      setError('常用资料所属项目已变化，未执行停用操作');
      return;
    }
    if (!window.confirm(`确认停用常用资料“${profile.profileName}”吗？历史邀请不会受影响。`)) return;
    const requestTicket = profileMutationRequestGuardRef.current.begin(requestProjectId);
    setError('');
    try {
      responseData(await disableSiteVisitorProfile(profile.id), '常用资料停用失败');
      if (!profileMutationRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) return;
      setNotice('常用资料已停用，历史来访记录未改变');
      profileDetailRequestGuardRef.current.invalidate();
      setProfileDetail((current) => current?.id === profile.id ? null : current);
      await loadProfiles(profilePanel?.pageNo || 1);
    } catch (profileError) {
      if (profileMutationRequestGuardRef.current.isCurrent(requestTicket, activeProjectIdRef.current)) {
        setError(profileError.message || '常用资料停用失败');
      }
    }
  };

  const records = pageData.records || pageData.items || [];
  const totalPages = Math.max(1, Math.ceil(Number(pageData.total || 0) / PAGE_SIZE));

  return (
    <div className="site-access-page" ref={pageRef} data-release-marker={WEB_RELEASE_MARKER} style={{ '--sa-accent': T.accent, '--sa-border': T.borderColor, '--sa-card': T.cardBg, '--sa-page': T.pageBg, '--sa-text': T.textPrimary, '--sa-secondary': T.textSecondary, '--sa-muted': T.textMuted }}>
      <div hidden={detail?.inviteType === 'MEETING'}>
      <section className="site-access-title-card">
        <div>
          <h1>场内管理</h1>
          <p>{activeSection === 'INVITATION'
            ? (SITE_ACCESS_MEETING_CREATION_ENABLED
              ? '创建单次预约或共享会议邀请，通过小程序提前收集人员和车辆信息。'
              : '创建单次预约，通过小程序提前收集人员和车辆信息。')
            : '门卫室二维码长期有效；访客无需审批，每次登记生成24小时放行页。'}</p>
        </div>
        {activeSection === 'INVITATION' && <div className="site-access-head-actions">
          <button className="secondary" type="button" onClick={openProfiles}>常用资料</button>
          {canExport && <button className="secondary" type="button" disabled={exporting} onClick={openExport}>{exporting ? '导出中...' : '导出登记人员'}</button>}
          {canManage && <button className="primary" type="button" onClick={openCreate}>新建邀请</button>}
        </div>}
      </section>

      <nav className="site-access-section-tabs" aria-label="场内管理分类">
        <button type="button" className={activeSection === 'INVITATION' ? 'active' : ''} onClick={() => {
          setActiveSection('INVITATION'); setDetail(null); setEditing(null); setQrCode(null); setExportFilters(null);
        }}>预约邀请</button>
        <button type="button" className={activeSection === 'GUARD' ? 'active' : ''} onClick={() => {
          setActiveSection('GUARD'); setDetail(null); setEditing(null); setQrCode(null); setExportFilters(null);
        }}>门卫登记</button>
      </nav>

      {activeSection === 'INVITATION' ? <>
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
        <select value={inviteType} onChange={(event) => {
          const nextType = event.target.value;
          setInviteType(nextType);
          setStatus((current) => normalizeInvitationStatusForType(nextType, current));
        }}>
          <option value="">全部类型</option><option value="SINGLE">单次预约</option><option value="MEETING">会议邀请</option>
        </select>
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="">全部状态</option>
          {inviteType !== 'MEETING' && <option value="PENDING">待填写</option>}
          {inviteType !== 'MEETING' && <option value="SUBMITTED">已登记</option>}
          {inviteType !== 'SINGLE' && <option value="OPEN">开放登记</option>}
          <option value="EXPIRED">已过期</option>
          <option value="VOIDED">已作废</option>
        </select>
        <div className="site-access-keyword">
          <input value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} onKeyDown={(event) => {
            if (event.key === 'Enter') setKeyword(keywordInput.trim());
          }} placeholder="邀请主题/来访事由、邀请编号、单位、姓名、车牌、接待人" />
          <button type="button" onClick={() => setKeyword(keywordInput.trim())}>查询</button>
        </div>
        <div className="site-access-range-label">{range.startDate} 至 {range.endDate}</div>
      </section>

      {notice && <div className="site-access-notice" onClick={() => setNotice('')}>{notice}</div>}
      {error && <div className="site-access-error" onClick={() => setError('')}>{error}</div>}

      <section className="site-access-table-card">
        <div className="site-access-table-wrap">
          <table>
            <thead><tr><th className="site-access-invitation-topic-column">邀请主题 / 类型</th><th>计划来访时间</th><th>登记概况</th><th>人数</th><th>出行</th><th>接待人</th><th>状态</th><th>创建 / 提交</th><th className="site-access-invitation-actions">操作</th></tr></thead>
            <tbody>
              {!loading && records.map((item) => {
                const invitationTopic = String(item.purpose || '').trim() || '未填写主题';
                return (
                  <tr key={item.id}>
                    <td className="site-access-invitation-topic-column"><button className="link site-access-invitation-topic" type="button" title={invitationTopic} onClick={() => openDetail(item.id)}>{invitationTopic}</button><small><span className={`site-access-invite-type ${String(item.inviteType || 'SINGLE').toLowerCase()}`}>{item.inviteType === 'MEETING' ? '会议邀请' : '单次预约'}</span></small></td>
                    <td>{formatDateTime(item.visitStartTime)}<small>至 {formatDateTime(item.visitEndTime)}</small></td>
                    <td>{item.inviteType === 'MEETING' ? `${item.registrationGroupCount || 0} 组已登记` : (item.visitorCompany || '等待访客填写')}<small>{item.inviteType === 'MEETING' ? `${item.registeredPersonCount || 0} 人` : (item.contactName || '-')}</small></td>
                    <td>{item.inviteType === 'MEETING' ? (item.registeredPersonCount || 0) : (item.visitorCount || 0)}</td>
                    <td>{item.inviteType === 'MEETING' ? '各登记组独立填写' : (item.travelMode === 'DRIVING' ? `驾车 · ${item.vehiclePlate || '-'}` : item.travelMode ? '非驾车' : '-')}</td>
                    <td>{item.hostName || '-'}</td>
                    <td><span className={`site-access-status ${String(effectiveInvitationStatus(item, clockNow) || '').toLowerCase()}`}>{STATUS_LABELS[effectiveInvitationStatus(item, clockNow)] || effectiveInvitationStatus(item, clockNow)}</span></td>
                    <td>{formatDateTime(item.createTime)}<small>{item.inviteType === 'MEETING' ? '持续开放至截止' : (item.submittedTime ? `提交 ${formatDateTime(item.submittedTime)}` : '尚未提交')}</small></td>
                    <td className="site-access-invitation-actions"><div className="site-access-row-actions">
                      <button type="button" onClick={() => openDetail(item.id)}>详情</button>
                      {canManage && isInvitationActive(item, clockNow) && <button type="button" onClick={() => showQr(item.id)}>预约码</button>}
                      {item.inviteType === 'MEETING' && canManage && isInvitationActive(item, clockNow) && <button type="button" onClick={() => showQr(item.id, true)}>签到码</button>}
                      {item.inviteType === 'MEETING' && <button className="secondary" type="button" onClick={() => openMeetingScreen(item.id)}>签到大屏</button>}
                      {canManage && isInvitationActive(item, clockNow) && <button type="button" onClick={() => openEdit(item.id)}>修改</button>}
                      {canManage && isInvitationActive(item, clockNow) && <button className="danger" type="button" onClick={() => voidInvitation(item)}>作废</button>}
                      {isPlatformAdmin(currentUser) && <button className="danger" type="button" onClick={() => deleteInvitation(item)}>删除</button>}
                    </div></td>
                  </tr>
                );
              })}
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
      </> : <GuardVisitPanel
        projectId={projectId}
        canManage={canManage}
        canExport={canExport}
        onOpenProfiles={openProfiles}
      />}
      </div>
      {detail?.inviteType === 'MEETING' && <MeetingDetailPage invitation={detail} projectId={projectId} currentUser={currentUser}
        canManage={canManage} canExport={canExport} canDelete={isPlatformAdmin(currentUser)} active={isInvitationActive(detail, clockNow)}
        statusLabel={STATUS_LABELS[effectiveInvitationStatus(detail, clockNow)]} tab={meetingTab} onTabChange={setMeetingTab}
        onBack={() => { setDetail(null); window.requestAnimationFrame(() => pageRef.current?.scrollTo({ top: listScrollRef.current })); }}
        onQr={(checkin) => showQr(detail.id, checkin)} onEdit={() => openEdit(detail.id)} onScreen={() => openMeetingScreen(detail.id)}
        onChanged={async () => { await load(pageNo); await openDetail(detail.id, meetingTab); }} />}
      {detail?.inviteType === 'MEETING' && error && <div className="site-access-error" role="alert">{error}</div>}

      {exportFilters && <Modal title="筛选并导出登记人员" onClose={() => !exporting && setExportFilters(null)} width={620}>
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
          <FormField label="邀请类型" required>
            <select value={exportFilters.inviteType} onChange={(event) => setExportFilters({ ...exportFilters, inviteType: event.target.value, status: event.target.value === 'MEETING' ? 'REGISTERED' : 'SUBMITTED', meetingStatus: '' })}>
              <option value="SINGLE">单次预约</option><option value="MEETING">会议邀请</option>
            </select>
          </FormField>
          <FormField label="导出状态" required>
            <select value={exportFilters.status} onChange={(event) => setExportFilters({ ...exportFilters, status: event.target.value })}>
              {exportFilters.inviteType === 'MEETING' ? <><option value="REGISTERED">已登记</option><option value="VOIDED">已作废</option></> : <><option value="SUBMITTED">已登记</option><option value="VOIDED">已作废</option><option value="PENDING">待填写</option><option value="EXPIRED">已过期</option></>}
            </select>
          </FormField>
          {exportFilters.inviteType === 'MEETING' && <FormField label="会议状态">
            <select value={exportFilters.meetingStatus || ''} onChange={(event) => setExportFilters({ ...exportFilters, meetingStatus: event.target.value })}>
              <option value="">全部会议</option><option value="OPEN">开放登记</option><option value="EXPIRED">已过期</option><option value="VOIDED">整场已作废</option>
            </select>
          </FormField>}
          <FormField label="关键词">
            <input value={exportFilters.keyword} onChange={(event) => setExportFilters({ ...exportFilters, keyword: event.target.value })} placeholder={exportFilters.inviteType === 'MEETING' ? '登记编号、单位、姓名、车牌' : '单位、姓名、车牌、接待人'} />
          </FormField>
          <div className="site-access-export-summary full">
            <strong>{PERIOD_LABELS[exportFilters.periodMode]}</strong>
            <span>{exportRange?.startDate || '-'} 至 {exportRange?.endDate || '-'}</span>
            <small className={exportRangeError ? 'error' : ''}>{exportRangeError || `${exportFilters.inviteType === 'MEETING' ? '会议导出按登记时间计算' : '单次预约按计划到场时间计算'}；一名来访人员一行，最长可自定义 366 天。`}</small>
          </div>
        </div>
        <div className="site-access-modal-actions"><button type="button" disabled={exporting} onClick={() => setExportFilters(null)}>取消</button><button className="primary" type="button" disabled={exporting || Boolean(exportRangeError)} onClick={exportVisitors}>{exporting ? '正在导出...' : '导出 Excel'}</button></div>
      </Modal>}

      {siteAccessProfileBelongsToProject(profilePanel, projectId) && <Modal title="外访人员常用资料" onClose={closeProfiles} width={980}>
        <div className="site-access-profile-toolbar">
          <select value={profilePanel.status || ''} onChange={(event) => loadProfiles(1, { ...profilePanel, status: event.target.value })}>
            <option value="">全部状态</option><option value="ACTIVE">使用中</option><option value="DISABLED">已停用</option>
          </select>
          <input value={profilePanel.keyword || ''} onChange={(event) => setProfilePanel({ ...profilePanel, keyword: event.target.value })} onKeyDown={(event) => event.key === 'Enter' && loadProfiles(1)} placeholder="资料名称、单位、姓名、车牌" />
          <button type="button" onClick={() => loadProfiles(1)}>查询</button>
          <span>资料仅在同一项目、同一微信身份下复用；历史邀请保持独立快照。</span>
        </div>
        <div className="site-access-profile-table">
          <table><thead><tr><th>资料名称</th><th>单位 / 姓名</th><th>人数</th><th>出行</th><th>最近使用</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              {!profileLoading && (profilePanel.records || []).map((profile) => <tr key={profile.id}>
                <td><button className="link" type="button" onClick={() => openProfileDetail(profile)}>{profile.profileName}</button><small>{profile.profileCode}</small></td>
                <td>{profile.visitorCompany}<small>{profile.contactName} · {profile.maskedContactPhone}</small></td>
                <td>{profile.visitorCount}</td>
                <td>{profile.travelMode === 'DRIVING' ? `驾车 · ${profile.vehiclePlate || '-'}` : '非驾车'}</td>
                <td>{formatDateTime(profile.lastUsedTime)}</td>
                <td><span className={`site-access-profile-status ${String(profile.status).toLowerCase()}`}>{profile.status === 'ACTIVE' ? '使用中' : '已停用'}</span></td>
                <td><div className="site-access-row-actions"><button type="button" onClick={() => openProfileDetail(profile)}>详情</button>{canManage && profile.status === 'ACTIVE' && <button className="danger" type="button" onClick={() => disableProfile(profile)}>停用</button>}</div></td>
              </tr>)}
              {!profileLoading && !(profilePanel.records || []).length && <tr><td colSpan="7" className="site-access-empty">当前项目暂无匹配的常用资料</td></tr>}
              {profileLoading && <tr><td colSpan="7" className="site-access-empty">正在加载...</td></tr>}
            </tbody></table>
        </div>
        <div className="site-access-pagination">
          <span>共 {profilePanel.total || 0} 条 · 第 {profilePanel.pageNo || 1}/{Math.max(1, Math.ceil(Number(profilePanel.total || 0) / PAGE_SIZE))} 页</span>
          <button type="button" disabled={(profilePanel.pageNo || 1) <= 1 || profileLoading} onClick={() => loadProfiles((profilePanel.pageNo || 1) - 1)}>上一页</button>
          <button type="button" disabled={(profilePanel.pageNo || 1) >= Math.max(1, Math.ceil(Number(profilePanel.total || 0) / PAGE_SIZE)) || profileLoading} onClick={() => loadProfiles((profilePanel.pageNo || 1) + 1)}>下一页</button>
        </div>
      </Modal>}

      {siteAccessProfileBelongsToProject(profileDetail, projectId) && <Modal title={`常用资料 · ${profileDetail.profileName}`} onClose={closeProfileDetail} width={720}>
        <div className="site-access-detail-grid">
          {[['状态', profileDetail.status === 'ACTIVE' ? '使用中' : '已停用'], ['单位', profileDetail.visitorCompany], ['姓名', `${profileDetail.contactName} ${profileDetail.contactPhone || ''}`], ['出行方式', profileDetail.travelMode === 'DRIVING' ? `驾车 · ${profileDetail.vehiclePlate || '-'}` : '非驾车'], ['最近使用', formatDateTime(profileDetail.lastUsedTime)], ['更新时间', formatDateTime(profileDetail.updateTime)]].map(([label, value]) => <div key={label}><span>{label}</span><b>{value}</b></div>)}
        </div>
        <h3 className="site-access-profile-people-title">保存的入场人员（{profileDetail.people?.length || 0}）</h3>
        <div className="site-access-person-list">{(profileDetail.people || []).map((person, index) => <div key={`${person.personType}-${index}`}><span>{person.personType === 'CONTACT' ? '本人' : '同行人员'}</span><PersonContactFields person={person} /></div>)}</div>
        <div className="site-access-modal-actions"><button type="button" onClick={closeProfileDetail}>关闭</button>{canManage && profileDetail.status === 'ACTIVE' && <button className="danger" type="button" onClick={() => disableProfile(profileDetail)}>停用资料</button>}</div>
      </Modal>}

      {editing && <Modal title={editing.mode === 'CREATE' ? '新建预约邀请' : `修改 ${editing.value.inviteNo}`} onClose={() => setEditing(null)}>
        <div className="site-access-form-grid">
          {editing.mode === 'CREATE' && SITE_ACCESS_MEETING_CREATION_ENABLED && <FormField label="邀请类型" required full><div className="site-access-invite-type-options"><button type="button" className={form.inviteType === 'SINGLE' ? 'active' : ''} onClick={() => setForm({ ...form, inviteType: 'SINGLE' })}><b>单次预约</b><span>一条邀请只登记一组访客</span></button><button type="button" className={form.inviteType === 'MEETING' ? 'active' : ''} onClick={() => setForm({ ...form, inviteType: 'MEETING' })}><b>会议邀请</b><span>共享二维码，每个微信身份独立登记</span></button></div></FormField>}
          <FormField label="计划到场" required><input type="datetime-local" value={form.visitStartTime} onChange={(event) => setForm({ ...form, visitStartTime: event.target.value })} /></FormField>
          <FormField label="计划离场" required><input type="datetime-local" value={form.visitEndTime} onChange={(event) => setForm({ ...form, visitEndTime: event.target.value })} /></FormField>
          <FormField label={form.inviteType === 'MEETING' ? '会议主题' : '来访事由'} required full><input value={form.purpose} maxLength="300" onChange={(event) => setForm({ ...form, purpose: event.target.value })} /></FormField>
          <FormField label="到访地点" required><input value={form.visitLocation} maxLength="200" onChange={(event) => setForm({ ...form, visitLocation: event.target.value })} /></FormField>
          <FormField label="接待人" required><HostCombobox hosts={hosts} value={form.hostUserId} onChange={(hostUserId) => setForm({ ...form, hostUserId })} /></FormField>
          <FormField label="内部备注" full><textarea value={form.internalRemark} maxLength="500" onChange={(event) => setForm({ ...form, internalRemark: event.target.value })} /></FormField>

          {editing.value?.status === 'SUBMITTED' && <>
            <div className="site-access-form-section full">访客已登记信息（修改会写入加密审计）</div>
            <FormField label="单位" required full><input value={form.visitorCompany} onChange={(event) => setForm({ ...form, visitorCompany: event.target.value })} /></FormField>
            <FormField label="姓名" required><input value={form.contactName} onChange={(event) => setForm({ ...form, contactName: event.target.value })} /></FormField>
            <FormField label="手机号码" required><input value={form.contactPhone} maxLength="11" onChange={(event) => setForm({ ...form, contactPhone: event.target.value })} /></FormField>
            <div className="site-access-companions full">
              <div className="site-access-companion-head"><strong>同行人员（单位、姓名、手机号码均选填）</strong><button type="button" disabled={form.companions.length >= 49} onClick={() => setForm({ ...form, companions: [...form.companions, { personCompany: '', personName: '', personPhone: '' }] })}>添加同行人</button></div>
              {form.companions.map((item, index) => <div className="site-access-companion-row" key={`companion-${index}`}>
                <input placeholder="单位（选填）" maxLength="200" value={item.personCompany} onChange={(event) => updateCompanion(index, 'personCompany', event.target.value)} />
                <input placeholder="姓名（选填）" maxLength="50" value={item.personName} onChange={(event) => updateCompanion(index, 'personName', event.target.value)} />
                <input placeholder="手机号码（选填）" maxLength="11" value={item.personPhone} onChange={(event) => updateCompanion(index, 'personPhone', event.target.value)} />
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

      {detail && detail.inviteType !== 'MEETING' && <div className="site-access-drawer-mask" onMouseDown={() => setDetail(null)}><aside className="site-access-drawer site-access-visitor-drawer" onMouseDown={(event) => event.stopPropagation()}>
        <div className="site-access-modal-head"><div><strong>{detail.inviteNo}</strong><span className={`site-access-invite-type ${String(detail.inviteType || 'SINGLE').toLowerCase()}`}>{detail.inviteType === 'MEETING' ? '会议邀请' : '单次预约'}</span><span className={`site-access-status ${String(effectiveInvitationStatus(detail, clockNow) || '').toLowerCase()}`}>{STATUS_LABELS[effectiveInvitationStatus(detail, clockNow)] || effectiveInvitationStatus(detail, clockNow)}</span></div><button type="button" onClick={() => setDetail(null)}>×</button></div>
        <div className="site-access-detail-grid">
          {(detail.inviteType === 'MEETING' ? [
            ['项目', detail.projectName], ['会议时间', `${formatDateTime(detail.visitStartTime)} 至 ${formatDateTime(detail.visitEndTime)}`],
            ['会议主题', detail.purpose], ['会议地点', detail.visitLocation], ['接待人', `${detail.hostName || '-'} ${detail.hostPhone || ''}`],
            ['登记组数', detail.registrationGroupCount || 0], ['登记人数', detail.registeredPersonCount || 0], ['内部备注', detail.internalRemark || '-'],
          ] : [['项目', detail.projectName], ['计划时间', `${formatDateTime(detail.visitStartTime)} 至 ${formatDateTime(detail.visitEndTime)}`], ['来访事由', detail.purpose], ['到访地点', detail.visitLocation], ['接待人', `${detail.hostName || '-'} ${detail.hostPhone || ''}`], ['单位', detail.visitorCompany || '-'], ['姓名', `${detail.contactName || '-'} ${detail.contactPhone || ''}`], ['出行方式', detail.travelMode === 'DRIVING' ? `驾车 · ${detail.vehiclePlate || '-'}` : detail.travelMode ? '非驾车' : '-'], ['资料来源', detail.sourceProfileId ? `常用资料 · ${detail.sourceProfileName || detail.sourceProfileId}` : '本次手工填写'], ['内部备注', detail.internalRemark || '-'], ['外访备注', detail.visitorRemark || '-']]).map(([label, value]) => <div key={label}><span>{label}</span><b>{value}</b></div>)}
        </div>
        {detail.inviteType === 'MEETING' ? <MeetingRegistrationPanel invitation={detail} projectId={projectId} canManage={canManage} canExport={canExport} currentTime={clockNow} onChanged={async () => { await load(pageNo); await openDetail(detail.id); }} /> : <><h3>入场人员（{detail.visitors?.length || 0}）</h3><div className="site-access-person-list">{(detail.visitors || []).map((person) => <div key={person.id}><span>{person.personType === 'CONTACT' ? '本人' : '同行人员'}</span><PersonContactFields person={person} /></div>)}{!detail.visitors?.length && <p>等待访客填写</p>}</div></>}
        <h3>操作记录</h3>
        <div className="site-access-audit-list">{(detail.auditLogs || []).map((log) => <div key={log.id}><b>{AUDIT_LABELS[log.actionType] || log.actionType}</b><span>{log.operatorName} · {formatDateTime(log.createTime)}</span><p>{log.comment || '-'}</p></div>)}</div>
        {detail.voidReason && <div className="site-access-void-reason">作废原因：{detail.voidReason}</div>}
      </aside></div>}

      {qrCode && <Modal title={`${qrCode.displayTitle || (qrCode.inviteType === 'MEETING' ? '会议预约码' : '专属小程序码')} · ${qrCode.inviteNo}`} onClose={() => setQrCode(null)} width={430}>
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
