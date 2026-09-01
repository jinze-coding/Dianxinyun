import React, { useEffect, useRef, useState } from "react";
import {
  assignQualityIssue,
  createQualityIssueExportJob,
  downloadQualityIssueExport,
  getQualityAssignees,
  getQualityIssueExportJobs,
  getQualityIssue,
  getQualityIssuePage,
  getQualitySummary,
  reviewQualityIssue,
  submitQualityRectification,
  voidQualityIssue,
} from "../../services/quality";
import {
  deleteFile,
  downloadFile,
  getFileList,
  updateFileStatus,
  uploadFile,
} from "../../services/file";
import { confirmAdministrativeDeletion } from "../../services/administrativeDeletion";
import {
  collectProjectMenuCodes,
  hasProjectPermission,
  isPlatformAdmin,
} from "../../utils/permissions";
import { pageMenuAllowed } from "../../utils/roleAuthorization";
import WeeklyInspectionPanel from "./WeeklyInspectionPanel";
import "./quality-management.css";

const formatTime = (value) =>
  value ? String(value).replace("T", " ").slice(0, 16) : "-";
const statusLabel = (issue) =>
  issue.status === "VOIDED"
    ? "已作废"
    : issue.overdue
    ? "已逾期"
    : { PENDING: "待整改", RECHECK: "待复查", CLOSED: "已关闭", VOIDED: "已作废" }[
        issue.status
      ] || issue.status;
const severityLabel = (value) =>
  ({ NORMAL: "一般", WARNING: "重要", DANGER: "严重" })[value] || value;
const actionLabel = (value) =>
  ({
    CREATE: "发起检查",
    RECTIFY: "提交整改",
    REVIEW_PASS: "复查通过",
    REVIEW_REJECT: "复查退回",
    ASSIGN: "改派/调整期限",
    VOID: "问题作废",
  })[value] || value;
const projectKey = (value) =>
  value === null || value === undefined ? "" : String(value);
const QUALITY_PAGE_SIZE = 20;
const QUALITY_TABS = [
  { key: "weekly", label: "周检记录", description: "按周组织检查" },
  { key: "issues", label: "整改闭环", description: "跟踪问题进度" },
  { key: "documents", label: "质量资料", description: "沉淀过程文件" },
];
const issueQueryKey = (
  projectId,
  status,
  source,
  keyword,
  startDate,
  endDate,
  pageNo,
) =>
  `${projectKey(projectId)}|${status}|${source}|${keyword.trim()}|${startDate}|${endDate}|${pageNo}`;
const localDateText = (date = new Date()) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
};
const currentMonthDateRange = () => {
  const today = new Date();
  return {
    startDate: localDateText(new Date(today.getFullYear(), today.getMonth(), 1)),
    endDate: localDateText(today),
  };
};
const validateDateRange = ({ startDate, endDate }, required = false) => {
  if (!startDate && !endDate) {
    return required ? "请选择完整的开始日期和结束日期" : "";
  }
  if (!startDate || !endDate) return "开始日期和结束日期必须同时选择";
  if (endDate < startDate) return "结束日期不能早于开始日期";
  if (endDate > localDateText()) return "结束日期不能晚于今天";
  const days = Math.round(
    (new Date(`${endDate}T00:00:00`).getTime()
      - new Date(`${startDate}T00:00:00`).getTime()) / 86_400_000,
  ) + 1;
  return days > 366 ? "日期范围最长为 366 天" : "";
};
const exportJobStatusLabel = (status) => ({
  PENDING: "等待生成",
  RUNNING: "生成中",
  SUCCEEDED: "已完成",
  FAILED: "生成失败",
  EXPIRED: "已过期",
})[status] || status;
const isArchivedDocument = (file) =>
  ["ARCHIVED", "已归档"].includes(String(file?.status || "").toUpperCase())
  || file?.status === "已归档";
const documentStatusLabel = (file) =>
  isArchivedDocument(file) ? "已归档" : "有效";
const parsePhotoFileIds = (value) => {
  const values = Array.isArray(value)
    ? value
    : String(value || "").split(",");
  return [...new Set(
    values
      .map((id) => Number(id))
      .filter((id) => Number.isFinite(id) && id > 0),
  )];
};
const chronologicalLogs = (logs = []) =>
  [...logs].sort((left, right) => {
    const timeDiff =
      new Date(left.createTime || 0).getTime()
      - new Date(right.createTime || 0).getTime();
    if (timeDiff) return timeDiff;
    return Number(left.id || 0) - Number(right.id || 0);
  });
const buildEvidenceStages = (issue) => {
  const logs = chronologicalLogs(issue?.logs || []);
  let rectifyRound = 0;
  let reviewRound = 0;
  const stages = logs.flatMap((log) => {
    if (log.actionType === "CREATE") {
      return [{
        key: `log-${log.id || "create"}`,
        type: "CREATE",
        title: "问题发起",
        required: true,
        operatorName: log.operatorName,
        createTime: log.createTime,
        comment: log.comment,
        photoIds: parsePhotoFileIds(log.photoFileIds),
      }];
    }
    if (log.actionType === "RECTIFY") {
      rectifyRound += 1;
      return [{
        key: `log-${log.id || `rectify-${rectifyRound}`}`,
        type: "RECTIFY",
        title: `第 ${rectifyRound} 轮整改`,
        required: true,
        operatorName: log.operatorName,
        createTime: log.createTime,
        comment: log.comment,
        photoIds: parsePhotoFileIds(log.photoFileIds),
      }];
    }
    if (["REVIEW_PASS", "REVIEW_REJECT"].includes(log.actionType)) {
      reviewRound += 1;
      return [{
        key: `log-${log.id || `review-${reviewRound}`}`,
        type: "REVIEW",
        title: `第 ${reviewRound} 轮复查 · ${
          log.actionType === "REVIEW_PASS" ? "通过" : "退回"
        }`,
        required: false,
        operatorName: log.operatorName,
        createTime: log.createTime,
        comment: log.comment,
        photoIds: parsePhotoFileIds(log.photoFileIds),
      }];
    }
    return [];
  });

  const attachFallback = (type, ids, fallbackStage) => {
    const fallbackIds = parsePhotoFileIds(ids);
    const sameTypeStages = stages.filter((stage) => stage.type === type);
    if (!fallbackIds.length || sameTypeStages.some((stage) => stage.photoIds.length)) {
      return;
    }
    if (sameTypeStages.length) {
      sameTypeStages[sameTypeStages.length - 1].photoIds = fallbackIds;
      return;
    }
    stages.push({ ...fallbackStage, photoIds: fallbackIds });
  };
  attachFallback(
    "CREATE",
    parsePhotoFileIds(issue?.originalProblemPhotoFileIds).length
      ? issue.originalProblemPhotoFileIds
      : issue?.issuePhotoFileIds,
    {
    key: "fallback-create",
    type: "CREATE",
    title: "问题发起",
    required: true,
    operatorName: issue?.createdByName,
    createTime: issue?.createTime,
    comment: issue?.description,
    },
  );
  attachFallback(
    "RECTIFY",
    parsePhotoFileIds(issue?.latestRectificationPhotoFileIds).length
      ? issue.latestRectificationPhotoFileIds
      : issue?.rectificationPhotoFileIds,
    {
    key: "fallback-rectify",
    type: "RECTIFY",
    title: "最近一轮整改",
    required: true,
    operatorName: issue?.assigneeName,
    createTime: issue?.rectifiedTime,
    comment: issue?.rectificationDescription,
    },
  );
  attachFallback("REVIEW", issue?.reviewPhotoFileIds, {
    key: "fallback-review",
    type: "REVIEW",
    title: "最近一轮复查",
    required: false,
    operatorName: issue?.reviewerName,
    createTime: issue?.reviewTime,
    comment: issue?.reviewComment,
  });
  return stages.sort((left, right) => {
    const leftTime = new Date(left.createTime || 0).getTime();
    const rightTime = new Date(right.createTime || 0).getTime();
    return leftTime - rightTime;
  });
};
const collectEvidenceIds = (issue) => [
  ...new Set(buildEvidenceStages(issue).flatMap((stage) => stage.photoIds)),
];
const reviewEvidenceCheck = (issue, evidenceState) => {
  if (!issue || issue.status !== "RECHECK") {
    return { ready: false, message: "当前问题不在待复查状态" };
  }
  if (evidenceState.loading) {
    return { ready: false, message: "过程证据仍在读取，请稍候" };
  }
  const stages = buildEvidenceStages(issue);
  const createStage = stages.find((stage) => stage.type === "CREATE");
  const rectifyStages = stages.filter((stage) => stage.type === "RECTIFY");
  const latestRectification = rectifyStages[rectifyStages.length - 1];
  if (
    !createStage?.photoIds.length
    || !latestRectification?.photoIds.length
  ) {
    return {
      ready: false,
      message: "问题照片或本轮整改照片缺失，不能复查通过",
    };
  }
  const failedIds = collectEvidenceIds(issue).filter(
    (id) => evidenceState.files[id]?.status !== "ready",
  );
  if (failedIds.length) {
    return {
      ready: false,
      message: `有 ${failedIds.length} 个过程附件读取失败，不能复查通过`,
    };
  }
  return { ready: true, message: "" };
};

export default function QualityManagementPage({ projectId, theme: T, currentUser, businessTarget }) {
  const [summary, setSummary] = useState(null);
  const [summaryProjectKey, setSummaryProjectKey] = useState("");
  const [issues, setIssues] = useState([]);
  const [issueTotal, setIssueTotal] = useState(0);
  const [loadedIssueQueryKey, setLoadedIssueQueryKey] = useState("");
  const [documents, setDocuments] = useState([]);
  const [documentsProjectKey, setDocumentsProjectKey] = useState("");
  const [documentScope, setDocumentScope] = useState("ACTIVE");
  const [members, setMembers] = useState([]);
  const [activeTab, setActiveTab] = useState("weekly");
  const [menuNotice, setMenuNotice] = useState("");
  const [status, setStatus] = useState("ALL");
  const [issueSource, setIssueSource] = useState("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [dateRange, setDateRange] = useState({ startDate: "", endDate: "" });
  const [appliedDateRange, setAppliedDateRange] = useState({ startDate: "", endDate: "" });
  const [exportOpen, setExportOpen] = useState(false);
  const [exportForm, setExportForm] = useState({
    ...currentMonthDateRange(),
    source: "ALL",
    status: "ALL",
    keyword: "",
  });
  const [exportJobs, setExportJobs] = useState([]);
  const [exportJobsLoading, setExportJobsLoading] = useState(false);
  const [exportSubmitting, setExportSubmitting] = useState(false);
  const [exportErrorText, setExportErrorText] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorText, setErrorText] = useState("");
  const [errorQueryKey, setErrorQueryKey] = useState("");
  const [documentsLoading, setDocumentsLoading] = useState(false);
  const [documentsErrorText, setDocumentsErrorText] = useState("");
  const [documentsErrorProjectKey, setDocumentsErrorProjectKey] = useState("");
  const [modal, setModal] = useState(null);
  const [selectedIssue, setSelectedIssue] = useState(null);
  const [actionForm, setActionForm] = useState({
    description: "",
    files: [],
    passed: true,
    comment: "",
    assigneeId: "",
    deadline: "",
  });
  const [documentFile, setDocumentFile] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [documentBusyAction, setDocumentBusyAction] = useState("");
  const [openingFileId, setOpeningFileId] = useState(null);
  const [evidenceState, setEvidenceState] = useState({
    loading: false,
    files: {},
  });
  const currentProjectIdRef = useRef(projectId);
  const issueRequestIdRef = useRef(0);
  const documentRequestIdRef = useRef(0);
  const memberRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);
  const openedBusinessTargetRef = useRef("");
  const evidenceUrlsRef = useRef([]);
  const exportRequestIdRef = useRef(0);
  const submittingRef = useRef(false);
  const documentBusyRef = useRef(false);
  const openingFileRef = useRef(false);

  currentProjectIdRef.current = projectId;

  const projectMenuCodes = collectProjectMenuCodes(currentUser, projectId);
  const canViewIssues = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ["QUALITY_ISSUES"], ["WEB_QUALITY", "QUALITY_MANAGEMENT"]);
  const canViewDocuments = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ["QUALITY_DOCUMENTS"], ["WEB_QUALITY", "QUALITY_MANAGEMENT"]);

  const releaseEvidenceUrls = () => {
    evidenceUrlsRef.current.forEach((url) => URL.revokeObjectURL(url));
    evidenceUrlsRef.current = [];
  };

  const beginSubmitting = () => {
    if (submittingRef.current) return false;
    submittingRef.current = true;
    setSubmitting(true);
    return true;
  };

  const finishSubmitting = () => {
    submittingRef.current = false;
    setSubmitting(false);
  };

  useEffect(
    () => () => {
      releaseEvidenceUrls();
    },
    [],
  );

  const fieldStyle = {
    width: "100%",
    boxSizing: "border-box",
    padding: "8px 10px",
    borderRadius: 6,
    border: `1px solid ${T.borderColor}`,
    background: T.surface2,
    color: T.textPrimary,
    outline: "none",
    fontSize: 12,
  };
  const buttonStyle = (kind = "primary") => ({
    padding: "7px 13px",
    borderRadius: 6,
    border: kind === "secondary" ? `1px solid ${T.borderColor}` : "none",
    background:
      kind === "danger"
        ? T.danger
        : kind === "secondary"
          ? T.surface2
          : T.accent,
    color: kind === "secondary" ? T.textSecondary : "#fff",
    cursor: "pointer",
    fontSize: 12,
    whiteSpace: "nowrap",
  });

  const loadQualityData = async (options = {}) => {
    const targetProjectId = options.projectId ?? projectId;
    const targetStatus = options.status ?? status;
    const targetSource = options.source ?? issueSource;
    const targetKeyword = (options.keyword ?? appliedKeyword).trim();
    const targetStartDate = options.startDate ?? appliedDateRange.startDate;
    const targetEndDate = options.endDate ?? appliedDateRange.endDate;
    const targetPageNo = options.pageNo ?? pageNo;
    const targetProjectKey = projectKey(targetProjectId);
    if (
      !targetProjectKey
      || projectKey(currentProjectIdRef.current) !== targetProjectKey
    ) {
      return;
    }
    const targetQueryKey = issueQueryKey(
      targetProjectId,
      targetStatus,
      targetSource,
      targetKeyword,
      targetStartDate,
      targetEndDate,
      targetPageNo,
    );
    const requestId = ++issueRequestIdRef.current;
    setLoading(true);
    setErrorText("");
    setErrorQueryKey("");
    try {
      const [summaryRes, issueRes] = await Promise.all([
        getQualitySummary(targetProjectId),
        getQualityIssuePage(targetProjectId, {
          status: targetStatus,
          source: targetSource,
          keyword: targetKeyword || undefined,
          startDate: targetStartDate || undefined,
          endDate: targetEndDate || undefined,
          pageNo: targetPageNo,
          pageSize: QUALITY_PAGE_SIZE,
        }),
      ]);
      if (
        requestId !== issueRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return;
      }
      if (summaryRes.code !== 200)
        throw new Error(summaryRes.message || "质量统计加载失败");
      if (issueRes.code !== 200)
        throw new Error(issueRes.message || "质量问题加载失败");
      const pageData = issueRes.data || {};
      const total = Number(pageData.total || 0);
      const totalPages = Math.max(1, Math.ceil(total / QUALITY_PAGE_SIZE));
      if (total > 0 && targetPageNo > totalPages) {
        setPageNo(totalPages);
        return;
      }
      setSummary(summaryRes.data);
      setSummaryProjectKey(targetProjectKey);
      setIssues(pageData.records || pageData.items || []);
      setIssueTotal(total);
      setLoadedIssueQueryKey(targetQueryKey);
    } catch (error) {
      if (
        requestId !== issueRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return;
      }
      setSummary(null);
      setSummaryProjectKey("");
      setIssues([]);
      setIssueTotal(0);
      setLoadedIssueQueryKey("");
      setErrorText(error.message || "质量数据加载失败");
      setErrorQueryKey(targetQueryKey);
    } finally {
      if (
        requestId === issueRequestIdRef.current
        && projectKey(currentProjectIdRef.current) === targetProjectKey
      ) {
        setLoading(false);
      }
    }
  };

  const loadDocuments = async (options = {}) => {
    const targetProjectId = options.projectId ?? projectId;
    const targetProjectKey = projectKey(targetProjectId);
    if (
      !targetProjectKey
      || projectKey(currentProjectIdRef.current) !== targetProjectKey
    ) {
      return;
    }
    const requestId = ++documentRequestIdRef.current;
    setDocumentsLoading(true);
    setDocumentsErrorText("");
    setDocumentsErrorProjectKey("");
    try {
      const res = await getFileList(targetProjectId, {
        businessType: "QUALITY_DOCUMENT",
      });
      if (
        requestId !== documentRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return;
      }
      if (res.code !== 200)
        throw new Error(res.message || "质量资料加载失败");
      setDocuments(res.data || []);
      setDocumentsProjectKey(targetProjectKey);
    } catch (error) {
      if (
        requestId !== documentRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return;
      }
      setDocuments([]);
      setDocumentsProjectKey("");
      setDocumentsErrorText(error.message || "质量资料加载失败");
      setDocumentsErrorProjectKey(targetProjectKey);
    } finally {
      if (
        requestId === documentRequestIdRef.current
        && projectKey(currentProjectIdRef.current) === targetProjectKey
      ) {
        setDocumentsLoading(false);
      }
    }
  };

  const loadExportJobs = async (options = {}) => {
    const targetProjectId = options.projectId ?? projectId;
    const targetProjectKey = projectKey(targetProjectId);
    if (
      !targetProjectKey
      || projectKey(currentProjectIdRef.current) !== targetProjectKey
    ) {
      return;
    }
    const requestId = ++exportRequestIdRef.current;
    if (!options.silent) setExportJobsLoading(true);
    setExportErrorText("");
    try {
      const res = await getQualityIssueExportJobs(targetProjectId);
      if (
        requestId !== exportRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return;
      }
      if (res.code !== 200) throw new Error(res.message || "导出任务加载失败");
      setExportJobs(res.data || []);
    } catch (error) {
      if (
        requestId !== exportRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return;
      }
      setExportErrorText(error.message || "导出任务加载失败");
    } finally {
      if (requestId === exportRequestIdRef.current && !options.silent) {
        setExportJobsLoading(false);
      }
    }
  };

  useEffect(() => {
    issueRequestIdRef.current += 1;
    documentRequestIdRef.current += 1;
    memberRequestIdRef.current += 1;
    detailRequestIdRef.current += 1;
    exportRequestIdRef.current += 1;
    setSummary(null);
    setSummaryProjectKey("");
    setIssues([]);
    setIssueTotal(0);
    setLoadedIssueQueryKey("");
    setErrorText("");
    setErrorQueryKey("");
    setLoading(false);
    setDocuments([]);
    setDocumentsProjectKey("");
    setDocumentScope("ACTIVE");
    setDocumentsErrorText("");
    setDocumentsErrorProjectKey("");
    setDocumentsLoading(false);
    setMembers([]);
    submittingRef.current = false;
    documentBusyRef.current = false;
    openingFileRef.current = false;
    setSubmitting(false);
    setDocumentBusyAction("");
    setOpeningFileId(null);
    releaseEvidenceUrls();
    setEvidenceState({ loading: false, files: {} });
    setStatus("ALL");
    setIssueSource("ALL");
    setPageNo(1);
    setKeyword("");
    setAppliedKeyword("");
    setDateRange({ startDate: "", endDate: "" });
    setAppliedDateRange({ startDate: "", endDate: "" });
    setExportOpen(false);
    setExportForm({
      ...currentMonthDateRange(),
      source: "ALL",
      status: "ALL",
      keyword: "",
    });
    setExportJobs([]);
    setExportJobsLoading(false);
    setExportSubmitting(false);
    setExportErrorText("");
    setModal(null);
    setSelectedIssue(null);
    setDocumentFile(null);
  }, [projectId]);
  useEffect(() => {
    if (activeTab === "issues" && canViewIssues) loadQualityData();
  }, [
    activeTab,
    canViewIssues,
    projectId,
    status,
    issueSource,
    appliedKeyword,
    appliedDateRange.startDate,
    appliedDateRange.endDate,
    pageNo,
  ]);
  useEffect(() => {
    if (activeTab === "documents" && canViewDocuments) {
      loadDocuments();
    }
  }, [activeTab, canViewDocuments, projectId]);
  useEffect(() => {
    if (["weekly", "issues"].includes(activeTab) && !canViewIssues && canViewDocuments) {
      setActiveTab("documents");
      setMenuNotice("当前角色无质量周检菜单权限，已切换到质量资料");
    } else if (activeTab === "documents" && !canViewDocuments && canViewIssues) {
      setActiveTab("weekly");
      setMenuNotice("当前角色无质量资料菜单权限，已切换到质量周检");
    }
  }, [activeTab, canViewDocuments, canViewIssues]);
  useEffect(() => {
    if (!exportOpen) return undefined;
    const hasActiveJob = exportJobs.some((job) =>
      ["PENDING", "RUNNING"].includes(job.status),
    );
    if (!hasActiveJob) return undefined;
    const timer = window.setInterval(
      () => loadExportJobs({ silent: true }),
      3000,
    );
    return () => window.clearInterval(timer);
  }, [exportOpen, exportJobs, projectId]);

  const currentProjectKey = projectKey(projectId);
  const currentQueryKey = issueQueryKey(
    projectId,
    status,
    issueSource,
    appliedKeyword,
    appliedDateRange.startDate,
    appliedDateRange.endDate,
    pageNo,
  );
  const currentSummary =
    summaryProjectKey === currentProjectKey ? summary : null;
  const currentIssues =
    loadedIssueQueryKey === currentQueryKey ? issues : [];
  const currentIssueTotal =
    loadedIssueQueryKey === currentQueryKey ? issueTotal : 0;
  const totalIssuePages = Math.max(
    1,
    Math.ceil(currentIssueTotal / QUALITY_PAGE_SIZE),
  );
  const currentDocuments =
    documentsProjectKey === currentProjectKey ? documents : [];
  const activeDocuments = currentDocuments.filter(
    (file) => !isArchivedDocument(file),
  );
  const archivedDocuments = currentDocuments.filter(isArchivedDocument);
  const visibleDocuments =
    documentScope === "ARCHIVED" ? archivedDocuments : activeDocuments;
  const currentIssueError =
    errorQueryKey === currentQueryKey ? errorText : "";
  const currentDocumentsError =
    documentsErrorProjectKey === currentProjectKey ? documentsErrorText : "";
  const issuesAreLoading = Boolean(projectId)
    && (loading
      || (
        loadedIssueQueryKey !== currentQueryKey
        && errorQueryKey !== currentQueryKey
      ));
  const documentsAreLoading = Boolean(projectId)
    && (documentsLoading
      || (
        documentsProjectKey !== currentProjectKey
        && documentsErrorProjectKey !== currentProjectKey
      ));

  const canManage = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, "quality.manage");
  const canRectify = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, "quality.rectify");
  const canReview = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, "quality.review");
  const canDelete = isPlatformAdmin(currentUser);
  const loadMembers = async () => {
    const targetProjectId = projectId;
    const targetProjectKey = projectKey(targetProjectId);
    const requestId = ++memberRequestIdRef.current;
    try {
      const res = await getQualityAssignees(targetProjectId);
      if (
        requestId !== memberRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return false;
      }
      if (res.code !== 200) {
        throw new Error(res.message || "整改负责人加载失败");
      }
      const candidates = res.data || [];
      if (!candidates.length) {
        throw new Error("当前项目没有具备质量整改权限的负责人");
      }
      setMembers(candidates);
      return candidates;
    } catch (error) {
      if (
        requestId !== memberRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return false;
      }
      setMembers([]);
      alert(error.message || "整改负责人加载失败，请稍后重试");
      return false;
    }
  };

  const loadIssueEvidence = async (issue, requestId, targetProjectKey) => {
    releaseEvidenceUrls();
    const evidenceIds = collectEvidenceIds(issue);
    if (!evidenceIds.length) {
      setEvidenceState({ loading: false, files: {} });
      return;
    }
    setEvidenceState({ loading: true, files: {} });
    const results = await Promise.all(
      evidenceIds.map(async (id) => {
        try {
          const blob = await downloadFile(id);
          return {
            id,
            status: "ready",
            url: URL.createObjectURL(blob),
            contentType: blob.type || "",
          };
        } catch (error) {
          return {
            id,
            status: "error",
            message: error.message || "附件读取失败",
          };
        }
      }),
    );
    if (
      requestId !== detailRequestIdRef.current
      || projectKey(currentProjectIdRef.current) !== targetProjectKey
    ) {
      results.forEach((result) => {
        if (result.url) URL.revokeObjectURL(result.url);
      });
      return;
    }
    evidenceUrlsRef.current = results
      .map((result) => result.url)
      .filter(Boolean);
    setEvidenceState({
      loading: false,
      files: Object.fromEntries(results.map((result) => [result.id, result])),
    });
  };

  const openIssue = async (issue) => {
    const targetProjectKey = currentProjectKey;
    const requestId = ++detailRequestIdRef.current;
    try {
      const res = await getQualityIssue(issue.id);
      if (
        requestId !== detailRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return;
      }
      if (res.code !== 200) throw new Error(res.message);
      setSelectedIssue(res.data);
      setActionForm({
        description: "",
        files: [],
        passed: true,
        comment: "",
        assigneeId: res.data.assigneeId || "",
        deadline: res.data.deadline || "",
      });
      setModal("detail");
      await loadIssueEvidence(res.data, requestId, targetProjectKey);
    } catch (error) {
      if (
        requestId !== detailRequestIdRef.current
        || projectKey(currentProjectIdRef.current) !== targetProjectKey
      ) {
        return;
      }
      alert(error.message || "详情加载失败");
    }
  };

  useEffect(() => {
    const issueId = Number(businessTarget?.id || 0);
    const targetKey = businessTarget?.routeCode === "QUALITY_ISSUE_DETAIL" && issueId
      ? `${projectId || ""}:${issueId}:${businessTarget?.openedAt || ""}`
      : "";
    if (!targetKey || openedBusinessTargetRef.current === targetKey || !canViewIssues) return;
    openedBusinessTargetRef.current = targetKey;
    setActiveTab("issues");
    setMenuNotice("");
    openIssue({ id: issueId });
  }, [businessTarget?.id, businessTarget?.openedAt, businessTarget?.routeCode, canViewIssues, projectId]);

  useEffect(() => {
    if (businessTarget?.routeCode !== "QUALITY_WEEKLY_INSPECTION_WEEK" || !canViewIssues) return;
    setActiveTab("weekly");
    setMenuNotice("");
  }, [businessTarget?.openedAt, businessTarget?.routeCode, canViewIssues]);

  const uploadFiles = async (files, businessType) => {
    const ids = [];
    try {
      for (const file of files || []) {
        const res = await uploadFile({
          file,
          projectId,
          fileType: file.type?.startsWith("image/") ? "质量照片" : "质量资料",
          businessType,
        });
        if (res.code !== 200)
          throw new Error(res.message || `附件 ${file.name} 上传失败`);
        ids.push(res.data.id);
      }
      return ids;
    } catch (error) {
      await Promise.allSettled(ids.map((id) => deleteFile(id)));
      throw error;
    }
  };

  const submitRectification = async () => {
    if (submittingRef.current) return;
    if (!actionForm.description.trim() || !actionForm.files.length)
      return alert("请填写整改说明并上传至少一张整改照片");
    if (!beginSubmitting()) return;
    let photoFileIds = [];
    try {
      photoFileIds = await uploadFiles(
        actionForm.files,
        "QUALITY_RECTIFICATION_PENDING",
      );
      const res = await submitQualityRectification(selectedIssue.id, {
        description: actionForm.description.trim(),
        photoFileIds,
      });
      if (res.code !== 200) throw new Error(res.message || "整改提交失败");
      closeModal();
      await loadQualityData();
    } catch (error) {
      await Promise.allSettled(photoFileIds.map((id) => deleteFile(id)));
      alert(error.message || "整改提交失败");
    } finally {
      finishSubmitting();
    }
  };

  const submitReview = async (passed) => {
    if (submittingRef.current) return;
    if (!passed && !actionForm.comment.trim())
      return alert("退回整改时必须填写复查意见");
    if (passed) {
      const evidenceCheck = reviewEvidenceCheck(selectedIssue, evidenceState);
      if (!evidenceCheck.ready) return alert(evidenceCheck.message);
      if (
        !window.confirm(
          "确认已核对完整的问题及整改证据，并通过复查关闭该问题？关闭后不可继续修改。",
        )
      ) {
        return;
      }
    }
    if (!beginSubmitting()) return;
    let photoFileIds = [];
    try {
      photoFileIds = actionForm.files.length
        ? await uploadFiles(actionForm.files, "QUALITY_REVIEW_PENDING")
        : [];
      const res = await reviewQualityIssue(selectedIssue.id, {
        passed,
        comment: actionForm.comment.trim(),
        photoFileIds,
      });
      if (res.code !== 200) throw new Error(res.message || "复查失败");
      closeModal();
      await loadQualityData();
    } catch (error) {
      await Promise.allSettled(photoFileIds.map((id) => deleteFile(id)));
      alert(error.message || "复查失败");
    } finally {
      finishSubmitting();
    }
  };

  const submitAssign = async () => {
    if (submittingRef.current) return;
    if (!actionForm.assigneeId)
      return alert("请选择整改负责人");
    if (!beginSubmitting()) return;
    try {
      const res = await assignQualityIssue(selectedIssue.id, {
        assigneeId: actionForm.assigneeId
          ? Number(actionForm.assigneeId)
          : null,
        deadline: actionForm.deadline || null,
        comment: actionForm.comment.trim(),
      });
      if (res.code !== 200) throw new Error(res.message || "改派失败");
      setSelectedIssue(res.data);
      setModal("detail");
      await loadQualityData();
    } catch (error) {
      alert(error.message || "改派失败");
    } finally {
      finishSubmitting();
    }
  };

  const submitVoid = async () => {
    if (submittingRef.current || !selectedIssue) return;
    if (!actionForm.comment.trim())
      return alert("请填写作废原因");
    if (
      !window.confirm(
        "确认作废这条质量问题？作废后不能继续整改、复查或改派，原因会写入操作留痕。",
      )
    ) {
      return;
    }
    if (!beginSubmitting()) return;
    try {
      const res = await voidQualityIssue(selectedIssue.id, {
        comment: actionForm.comment.trim(),
      });
      if (res.code !== 200) throw new Error(res.message || "作废失败");
      closeModal();
      await loadQualityData();
    } catch (error) {
      alert(error.message || "作废失败");
    } finally {
      finishSubmitting();
    }
  };

  const removeIssue = async (issue) => {
    if (submittingRef.current || !issue) return;
    if (!beginSubmitting()) return;
    try {
      const deleted = await confirmAdministrativeDeletion("QUALITY_ISSUE", issue.id);
      if (!deleted) return;
      closeModal();
      setSelectedIssue(null);
      await loadQualityData();
    } catch (error) {
      alert(error.message || "质量问题永久删除失败，请稍后重试");
    } finally {
      finishSubmitting();
    }
  };

  const uploadDocument = async () => {
    if (submittingRef.current) return;
    if (!documentFile) return alert("请选择质量资料");
    if (!beginSubmitting()) return;
    try {
      const res = await uploadFile({
        file: documentFile,
        projectId,
        fileType: "质量资料",
        businessType: "QUALITY_DOCUMENT",
      });
      if (res.code !== 200) throw new Error(res.message || "上传失败");
      setDocumentFile(null);
      setDocumentScope("ACTIVE");
      await loadDocuments();
    } catch (error) {
      alert(error.message || "上传失败");
    } finally {
      finishSubmitting();
    }
  };

  const openFile = async (fileId) => {
    if (openingFileRef.current) return;
    const previewWindow = window.open("", "_blank");
    if (!previewWindow) {
      alert("浏览器阻止了新窗口，请允许弹出窗口后重试");
      return;
    }
    previewWindow.opener = null;
    previewWindow.document.title = "文件读取中";
    previewWindow.document.body.textContent = "文件读取中，请稍候...";
    openingFileRef.current = true;
    setOpeningFileId(fileId);
    try {
      const blob = await downloadFile(fileId);
      const url = URL.createObjectURL(blob);
      previewWindow.location.replace(url);
      setTimeout(() => URL.revokeObjectURL(url), 300000);
    } catch (error) {
      previewWindow.close();
      alert(error.message || "文件打开失败");
    } finally {
      openingFileRef.current = false;
      setOpeningFileId(null);
    }
  };

  const closeModal = () => {
    detailRequestIdRef.current += 1;
    releaseEvidenceUrls();
    setEvidenceState({ loading: false, files: {} });
    setModal(null);
  };

  const changeDocumentStatus = async (file, nextStatus) => {
    if (documentBusyRef.current) return;
    const restoring = nextStatus !== "ARCHIVED";
    const action = nextStatus === "ARCHIVED" ? "archive" : "restore";
    documentBusyRef.current = true;
    setDocumentBusyAction(`${action}:${file.id}`);
    try {
      const res = await updateFileStatus(file.id, nextStatus);
      if (res.code !== 200) {
        throw new Error(
          res.message || (nextStatus === "ARCHIVED" ? "归档失败" : "恢复失败"),
        );
      }
      await loadDocuments();
    } catch (error) {
      alert(
        error.message
          || (restoring ? "归档失败，请稍后重试" : "恢复失败，请稍后重试"),
      );
    } finally {
      documentBusyRef.current = false;
      setDocumentBusyAction("");
    }
  };

  const removeDocument = async (file) => {
    if (documentBusyRef.current) return;
    documentBusyRef.current = true;
    setDocumentBusyAction(`delete:${file.id}`);
    try {
      const deleted = await confirmAdministrativeDeletion("FILE", file.id);
      if (!deleted) return;
      await loadDocuments();
    } catch (error) {
      alert(error.message || "删除失败，请稍后重试");
    } finally {
      documentBusyRef.current = false;
      setDocumentBusyAction("");
    }
  };

  const runSearch = () => {
    const nextKeyword = keyword.trim();
    const dateError = validateDateRange(dateRange);
    if (dateError) {
      alert(dateError);
      return;
    }
    const datesUnchanged = dateRange.startDate === appliedDateRange.startDate
      && dateRange.endDate === appliedDateRange.endDate;
    if (nextKeyword === appliedKeyword && datesUnchanged) {
      if (pageNo !== 1) {
        setPageNo(1);
      } else {
        loadQualityData({
          keyword: nextKeyword,
          startDate: dateRange.startDate,
          endDate: dateRange.endDate,
          pageNo: 1,
        });
      }
      return;
    }
    setPageNo(1);
    setAppliedKeyword(nextKeyword);
    setAppliedDateRange(dateRange);
  };

  const openExport = () => {
    setExportForm({
      ...currentMonthDateRange(),
      source: issueSource,
      status,
      keyword: appliedKeyword,
    });
    setExportOpen(true);
    loadExportJobs();
  };

  const submitExport = async () => {
    const dateError = validateDateRange(exportForm, true);
    if (dateError) {
      alert(dateError);
      return;
    }
    setExportSubmitting(true);
    try {
      const res = await createQualityIssueExportJob({
        projectId,
        startDate: exportForm.startDate,
        endDate: exportForm.endDate,
        source: exportForm.source,
        status: exportForm.status,
        keyword: exportForm.keyword.trim() || undefined,
      });
      if (res.code !== 200) throw new Error(res.message || "导出任务创建失败");
      await loadExportJobs();
    } catch (error) {
      alert(error.message || "导出任务创建失败");
    } finally {
      setExportSubmitting(false);
    }
  };

  const downloadExport = async (job) => {
    try {
      const blob = await downloadQualityIssueExport(job.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `质量问题汇总_${job.startDate}至${job.endDate}.xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      alert(error.message || "报表下载失败");
    }
  };

  const pill = (label, tone = "normal") => (
    <span
      style={{
        display: "inline-flex",
        padding: "3px 8px",
        borderRadius: 999,
        fontSize: 11,
        fontWeight: 700,
        color:
          tone === "danger"
            ? T.danger
            : tone === "warning"
              ? T.warning
              : tone === "success"
                ? T.success
                : T.accent,
        background:
          tone === "danger"
            ? `${T.danger}16`
            : tone === "warning"
              ? `${T.warning}18`
              : tone === "success"
                ? `${T.success}16`
                : T.activeItemBg,
      }}
    >
      {label}
    </span>
  );

  return (
    <div
      className="quality-workspace"
      style={{
        height: "100%",
        padding: "18px 20px",
        display: "flex",
        flexDirection: "column",
        gap: 14,
        overflow: "hidden",
        background: T.pageBg,
      }}
    >
      <QualityWorkspaceHeader
        T={T}
        activeTab={activeTab}
        canViewIssues={canViewIssues}
        canViewDocuments={canViewDocuments}
        menuNotice={menuNotice}
        onChange={(tab) => {
          setActiveTab(tab);
          setMenuNotice("");
        }}
      />
      <div
        className="quality-content-card"
        role="tabpanel"
        id={`quality-panel-${activeTab}`}
        aria-labelledby={`quality-tab-${activeTab}`}
        style={{
          flex: 1,
          minHeight: 0,
          overflow: "hidden",
          display: "flex",
          flexDirection: "column",
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 12,
        }}
      >
        {activeTab === "weekly" ? (
          <WeeklyInspectionPanel
            projectId={projectId}
            T={T}
            canManage={canManage}
            buttonStyle={buttonStyle}
            fieldStyle={fieldStyle}
            pill={pill}
            onOpenIssue={(issue) => openIssue(issue)}
            businessTarget={businessTarget}
          />
        ) : activeTab === "issues" ? (
          <>
          <QualityPanelHeader
            T={T}
            eyebrow="问题闭环台账"
            title="整改闭环"
            description="集中跟踪周检问题和历史独立问题，按负责人、期限与状态推进闭环。"
          >
            <select
              aria-label="问题来源"
              value={issueSource}
              onChange={(e) => {
                setPageNo(1);
                setIssueSource(e.target.value);
              }}
              style={{ ...fieldStyle, width: 132 }}
              title="按问题来源筛选"
            >
              <option value="ALL">全部问题</option>
              <option value="WEEKLY">周检问题</option>
              <option value="HISTORICAL">历史独立问题</option>
            </select>
            <select
              aria-label="问题状态"
              value={status}
              onChange={(e) => {
                setPageNo(1);
                setStatus(e.target.value);
              }}
              style={{ ...fieldStyle, width: 120 }}
            >
              <option value="ALL">全部状态</option>
              <option value="PENDING">待整改</option>
              <option value="OVERDUE">已逾期</option>
              <option value="RECHECK">待复查</option>
              <option value="CLOSED">已关闭</option>
              <option value="VOIDED">已作废</option>
            </select>
            <label className="quality-date-filter" style={{ color: T.textMuted }}>
              <span>开始</span>
              <input
                aria-label="问题开始日期"
                type="date"
                max={localDateText()}
                value={dateRange.startDate}
                onChange={(e) => setDateRange((current) => ({
                  ...current,
                  startDate: e.target.value,
                }))}
                style={{ ...fieldStyle, width: 136 }}
              />
            </label>
            <label className="quality-date-filter" style={{ color: T.textMuted }}>
              <span>结束</span>
              <input
                aria-label="问题结束日期"
                type="date"
                max={localDateText()}
                value={dateRange.endDate}
                onChange={(e) => setDateRange((current) => ({
                  ...current,
                  endDate: e.target.value,
                }))}
                style={{ ...fieldStyle, width: 136 }}
              />
            </label>
            <input
              aria-label="搜索质量问题"
              value={keyword}
              onChange={(e) => {
                const nextKeyword = e.target.value;
                setKeyword(nextKeyword);
                if (!nextKeyword.trim() && appliedKeyword) {
                  setPageNo(1);
                  setAppliedKeyword("");
                }
              }}
              onKeyDown={(e) => e.key === "Enter" && runSearch()}
              placeholder="搜索问题、位置、负责人"
              style={{ ...fieldStyle, width: 240 }}
            />
            <button
              disabled={issuesAreLoading}
              onClick={runSearch}
              style={buttonStyle("secondary")}
            >
              查询
            </button>
            <button
              disabled={issuesAreLoading || !projectId}
              onClick={openExport}
              style={buttonStyle()}
            >
              导出汇总
            </button>
          </QualityPanelHeader>
          <QualityMetricGrid T={T} summary={currentSummary} />
          <div className="quality-table-region">
          {issuesAreLoading ? (
            <LoadingState T={T} text="质量问题加载中..." />
          ) : currentIssueError ? (
            <LoadError
              T={T}
              text={currentIssueError}
              onRetry={() => loadQualityData()}
              buttonStyle={buttonStyle}
            />
          ) : (
            <>
            <TableHead
              T={T}
              columns="105px 120px 1.5fr 1fr .8fr .8fr .8fr 90px"
              labels={[
                "问题日期",
                "编号",
                "问题",
                "位置",
                "负责人",
                "期限",
                "状态",
                "操作",
              ]}
            />
            {currentIssues.map((issue) => (
              <TableRow
                key={issue.id}
                T={T}
                columns="105px 120px 1.5fr 1fr .8fr .8fr .8fr 90px"
              >
                <span>{issue.recordDate || "-"}</span>
                <span
                  title={issue.issueNo || "-"}
                  style={{
                    display: "block",
                    minWidth: 0,
                    maxWidth: "100%",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                    color: T.textMuted,
                  }}
                >
                  {issue.issueNo || "-"}
                </span>
                <span style={{ minWidth: 0 }}>
                  <strong style={{ display: "block", color: T.textPrimary }}>
                    {issue.title}
                  </strong>
                  <small
                    style={{
                      color:
                        issue.severity === "DANGER"
                          ? T.danger
                          : issue.severity === "WARNING"
                            ? T.warning
                            : T.textMuted,
                    }}
                  >
                    严重等级：{severityLabel(issue.severity)}
                  </small>
                </span>
                <span>{issue.location || "-"}</span>
                <span>{issue.assigneeName || "待重新分配"}</span>
                <span
                  style={{ color: issue.overdue ? T.danger : T.textSecondary }}
                >
                  {issue.dueText || issue.deadline || "-"}
                </span>
                {pill(
                  statusLabel(issue),
                  issue.overdue
                    ? "danger"
                    : issue.status === "PENDING"
                      ? "warning"
                      : issue.status === "CLOSED"
                        ? "success"
                        : "normal",
                )}
                <button
                  onClick={() => openIssue(issue)}
                  style={buttonStyle("secondary")}
                >
                  详情
                </button>
              </TableRow>
            ))}
            {!currentIssues.length && (
              <Empty
                T={T}
                text={
                  appliedKeyword
                    ? `当前筛选下未找到与“${appliedKeyword}”匹配的质量问题`
                    : status !== "ALL"
                      ? `“${
                        {
                          PENDING: "待整改",
                          OVERDUE: "已逾期",
                          RECHECK: "待复查",
                          CLOSED: "已关闭",
                          VOIDED: "已作废",
                        }[status] || status
                      }”分类暂无质量问题`
                      : "当前项目暂无质量问题"
                }
              />
            )}
            {currentIssueTotal > 0 && (
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "flex-end",
                  gap: 8,
                  padding: "11px 14px",
                  borderTop: `1px solid ${T.borderColor}`,
                  color: T.textMuted,
                  fontSize: 11,
                }}
              >
                <span>
                  共 {currentIssueTotal} 条，第 {pageNo}/{totalIssuePages} 页
                </span>
                <button
                  type="button"
                  disabled={pageNo <= 1 || issuesAreLoading}
                  onClick={() => setPageNo((current) => Math.max(1, current - 1))}
                  style={buttonStyle("secondary")}
                >
                  上一页
                </button>
                <button
                  type="button"
                  disabled={pageNo >= totalIssuePages || issuesAreLoading}
                  onClick={() =>
                    setPageNo((current) =>
                      Math.min(totalIssuePages, current + 1),
                    )
                  }
                  style={buttonStyle("secondary")}
                >
                  下一页
                </button>
              </div>
            )}
            </>
          )}
          </div>
          </>
        ) : (
          <>
          <QualityPanelHeader
            T={T}
            eyebrow="质量过程文件"
            title="质量资料"
            description="集中保存质量照片、报告和过程文件，归档后仍可追溯与恢复。"
          >
            <div className="quality-scope-switch" role="group" aria-label="质量资料范围">
              {[
                ["ACTIVE", "有效资料", activeDocuments.length],
                ["ARCHIVED", "历史归档", archivedDocuments.length],
              ].map(([scope, label, count]) => {
                const selected = documentScope === scope;
                return (
                  <button
                    key={scope}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => setDocumentScope(scope)}
                    style={{
                      ...buttonStyle("secondary"),
                      borderColor: selected ? `${T.accent}66` : T.borderColor,
                      background: selected ? T.activeItemBg : T.surface2,
                      color: selected ? T.accent : T.textSecondary,
                      fontWeight: selected ? 800 : 600,
                    }}
                  >
                    {label} <span className="quality-scope-count">{count}</span>
                  </button>
                );
              })}
            </div>
            <button
              disabled={documentsAreLoading || Boolean(documentBusyAction)}
              onClick={() => loadDocuments()}
              style={buttonStyle("secondary")}
            >
              刷新
            </button>
            {documentScope === "ACTIVE" && (
              <>
                <label
                  className="quality-file-picker"
                  style={{
                    borderColor: documentFile ? `${T.accent}66` : T.borderColor,
                    background: documentFile ? T.activeItemBg : T.surface2,
                    color: documentFile ? T.accent : T.textSecondary,
                  }}
                  title={documentFile?.name || "选择质量资料文件"}
                >
                  <span className="quality-file-picker__name">
                    {documentFile?.name || "选择资料文件"}
                  </span>
                  <span className="quality-file-picker__action">浏览</span>
                  <input
                    type="file"
                    onChange={(e) => setDocumentFile(e.target.files?.[0] || null)}
                  />
                </label>
                <button
                  disabled={
                    !canManage
                    || !documentFile
                    || submitting
                    || Boolean(documentBusyAction)
                  }
                  onClick={uploadDocument}
                  style={buttonStyle()}
                >
                  {submitting ? "上传中..." : "上传资料"}
                </button>
              </>
            )}
          </QualityPanelHeader>
          <div
            className="quality-context-note"
            style={{
              background: T.surface2,
              borderColor: T.borderColor,
              color: T.textMuted,
            }}
          >
            <span className="quality-context-note__dot" style={{ background: T.accent }} />
            {documentScope === "ACTIVE"
              ? "当前展示有效质量资料；归档后可在“历史归档”中查看并恢复。"
              : "历史归档资料保持只读，可恢复为有效资料；平台管理员可按影响确认后永久删除。"}
          </div>
          <div className="quality-table-region">
            {documentsAreLoading ? (
              <LoadingState T={T} text="质量资料加载中..." />
            ) : currentDocumentsError ? (
              <LoadError
                T={T}
                text={currentDocumentsError}
                onRetry={() => loadDocuments()}
                buttonStyle={buttonStyle}
              />
            ) : (
              <>
                <TableHead
                  T={T}
                  columns="1.5fr .8fr .8fr .9fr 160px"
                  labels={["文件名", "类型", "状态", "上传时间", "操作"]}
                />
                {visibleDocuments.map((file) => (
                  <TableRow
                    key={file.id}
                    T={T}
                    columns="1.5fr .8fr .8fr .9fr 160px"
                  >
                    <strong>{file.fileName}</strong>
                    <span>{file.fileType || "-"}</span>
                    {pill(
                      documentStatusLabel(file),
                      isArchivedDocument(file) ? "success" : "normal",
                    )}
                    <span>{formatTime(file.createTime)}</span>
                    <span style={{ display: "flex", gap: 6 }}>
                      <button
                        disabled={openingFileId !== null}
                        onClick={() => openFile(file.id)}
                        style={buttonStyle("secondary")}
                      >
                        {openingFileId === file.id ? "读取中..." : "查看"}
                      </button>
                      {canManage && (
                        <>
                          <button
                            disabled={Boolean(documentBusyAction)}
                            onClick={() =>
                              changeDocumentStatus(
                                file,
                                isArchivedDocument(file) ? "UPLOADED" : "ARCHIVED",
                              )
                            }
                            style={buttonStyle("secondary")}
                          >
                            {documentBusyAction ===
                            `${isArchivedDocument(file) ? "restore" : "archive"}:${file.id}`
                              ? isArchivedDocument(file)
                                ? "恢复中..."
                                : "归档中..."
                              : isArchivedDocument(file)
                                ? "恢复"
                                : "归档"}
                          </button>
                          {canDelete && (
                            <button
                              disabled={Boolean(documentBusyAction)}
                              onClick={() => removeDocument(file)}
                              style={buttonStyle("danger")}
                            >
                              {documentBusyAction === `delete:${file.id}`
                                ? "删除中..."
                                : "删除"}
                            </button>
                          )}
                        </>
                      )}
                    </span>
                  </TableRow>
                ))}
                {!visibleDocuments.length && (
                  <Empty
                    T={T}
                    title={
                      documentScope === "ARCHIVED"
                        ? "暂无已归档质量资料"
                        : "暂无有效质量资料"
                    }
                    text={
                      documentScope === "ARCHIVED"
                        ? "已归档的质量文件会集中显示在这里。"
                        : "选择文件并上传后，质量资料会按时间留存在当前项目中。"
                    }
                  />
                )}
              </>
            )}
          </div>
          </>
        )}
      </div>
      {exportOpen && (
        <QualityExportModal
          T={T}
          form={exportForm}
          setForm={setExportForm}
          jobs={exportJobs}
          loading={exportJobsLoading}
          submitting={exportSubmitting}
          errorText={exportErrorText}
          fieldStyle={fieldStyle}
          buttonStyle={buttonStyle}
          onClose={() => setExportOpen(false)}
          onSubmit={submitExport}
          onRefresh={() => loadExportJobs()}
          onDownload={downloadExport}
        />
      )}
      {modal === "detail" && selectedIssue && (
        <Modal T={T} title="质量问题详情" onClose={closeModal}>
          <IssueDetail
            T={T}
            issue={selectedIssue}
            actionForm={actionForm}
            setActionForm={setActionForm}
            canManage={canManage}
            canRectify={canRectify}
            canReview={canReview}
            canDelete={canDelete}
            submitting={submitting}
            buttonStyle={buttonStyle}
            fieldStyle={fieldStyle}
            pill={pill}
            evidenceState={evidenceState}
            onRectify={submitRectification}
            onReview={submitReview}
            onAssign={async () => {
              const candidates = await loadMembers();
              if (!candidates) return;
              setActionForm((current) => ({ ...current, assigneeId: "" }));
              setModal("assign");
            }}
            onVoid={() => {
              setActionForm((current) => ({
                ...current,
                comment: "",
                files: [],
              }));
              setModal("void");
            }}
            onDelete={() => removeIssue(selectedIssue)}
          />
        </Modal>
      )}
      {modal === "assign" && selectedIssue && (
        <Modal
          T={T}
          title="改派整改人/调整期限"
          onClose={() => setModal("detail")}
        >
          <label style={labelStyle(T)}>
            整改负责人
            <select
              style={fieldStyle}
              value={actionForm.assigneeId}
              onChange={(e) =>
                setActionForm({ ...actionForm, assigneeId: e.target.value })
              }
            >
              <option value="">请选择整改负责人</option>
              {members.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.displayName}
                </option>
              ))}
            </select>
          </label>
          <label style={labelStyle(T)}>
            闭环期限
            <input
              type="date"
              style={fieldStyle}
              value={actionForm.deadline}
              onChange={(e) =>
                setActionForm({ ...actionForm, deadline: e.target.value })
              }
            />
          </label>
          <label style={labelStyle(T)}>
            调整说明
            <textarea
              style={{ ...fieldStyle, minHeight: 70 }}
              maxLength={1000}
              value={actionForm.comment}
              onChange={(e) =>
                setActionForm({ ...actionForm, comment: e.target.value })
              }
            />
          </label>
          <ModalActions
            buttonStyle={buttonStyle}
            submitting={submitting}
            onCancel={() => setModal("detail")}
            onSubmit={submitAssign}
          />
        </Modal>
      )}
      {modal === "void" && selectedIssue && (
        <Modal
          T={T}
          title="作废质量问题"
          onClose={() => setModal("detail")}
        >
          <div
            style={{
              marginBottom: 12,
              padding: 10,
              borderRadius: 7,
              background: T.surface2,
              color: T.textSecondary,
              fontSize: 12,
            }}
          >
            <strong style={{ display: "block", color: T.textPrimary }}>
              {selectedIssue.title}
            </strong>
            <span>{selectedIssue.issueNo} · {statusLabel(selectedIssue)}</span>
          </div>
          <label style={labelStyle(T)}>
            作废原因 *
            <textarea
              style={{ ...fieldStyle, minHeight: 90 }}
              maxLength={1000}
              value={actionForm.comment}
              onChange={(e) =>
                setActionForm({ ...actionForm, comment: e.target.value })
              }
              placeholder="说明误建、重复或不属于质量问题等原因"
            />
          </label>
          <ModalActions
            buttonStyle={buttonStyle}
            submitting={submitting}
            onCancel={() => setModal("detail")}
            onSubmit={submitVoid}
            submitTone="danger"
            submitLabel="确认作废"
          />
        </Modal>
      )}
    </div>
  );
}

function QualityWorkspaceHeader({
  T,
  activeTab,
  canViewIssues,
  canViewDocuments,
  menuNotice,
  onChange,
}) {
  const visibleTabs = QUALITY_TABS.filter((tab) =>
    tab.key === "documents" ? canViewDocuments : canViewIssues,
  );
  return (
    <section
      className="quality-workspace-header"
      style={{
        background: `linear-gradient(135deg, ${T.cardBg} 0%, ${T.cardBg} 58%, ${T.activeItemBg} 100%)`,
        borderColor: T.borderColor,
      }}
    >
      <div className="quality-workspace-intro">
        <span className="quality-workspace-kicker" style={{ color: T.accent }}>
          质量管理工作台
        </span>
        <div className="quality-workspace-title-row">
          <h2 style={{ color: T.textPrimary }}>质量周检</h2>
          <span
            className="quality-workspace-badge"
            style={{ color: T.accent, background: T.activeItemBg }}
          >
            Web / 小程序协同
          </span>
        </div>
        <p style={{ color: T.textMuted }}>
          按周组织检查，逐项推进整改复查，并将质量过程资料统一留痕。
        </p>
        {menuNotice && (
          <span role="status" className="quality-workspace-notice" style={{ color: T.warning }}>
            {menuNotice}
          </span>
        )}
      </div>
      <div className="quality-workspace-tabs" role="tablist" aria-label="质量周检页面">
        {visibleTabs.map((tab) => {
          const active = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              role="tab"
              id={`quality-tab-${tab.key}`}
              aria-selected={active}
              aria-controls={`quality-panel-${tab.key}`}
              tabIndex={active ? 0 : -1}
              onClick={() => onChange(tab.key)}
              style={{
                borderColor: active ? `${T.accent}66` : T.borderColor,
                background: active ? T.activeItemBg : T.cardBg,
                color: active ? T.accent : T.textPrimary,
                boxShadow: active ? `inset 0 -3px 0 ${T.accent}` : "none",
              }}
            >
              <span>{tab.label}</span>
              <small style={{ color: active ? T.accent : T.textMuted }}>
                {tab.description}
              </small>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function QualityPanelHeader({ T, eyebrow, title, description, children }) {
  return (
    <header
      className="quality-panel-header"
      style={{ borderColor: T.borderColor, background: T.cardBg }}
    >
      <div className="quality-panel-copy">
        <span className="quality-panel-eyebrow" style={{ color: T.accent }}>
          {eyebrow}
        </span>
        <h3 style={{ color: T.textPrimary }}>{title}</h3>
        <p style={{ color: T.textMuted }}>{description}</p>
      </div>
      <div className="quality-panel-actions">{children}</div>
    </header>
  );
}

function QualityMetricGrid({ T, summary }) {
  const metrics = [
    ["今日新增", summary?.todayCheckCount || 0, T.accent],
    ["待整改", summary?.pendingCount || 0, T.warning],
    ["已逾期", summary?.overdueCount || 0, T.danger],
    ["待复查", summary?.recheckCount || 0, T.accent2],
    ["闭环率", `${summary?.closureRate || 0}%`, T.success],
  ];
  return (
    <section
      className="quality-metric-grid"
      aria-label="整改闭环统计"
      style={{ borderColor: T.borderColor, background: T.surface2 }}
    >
      {metrics.map(([label, value, color]) => (
        <div
          key={label}
          className="quality-metric-card"
          style={{ borderColor: T.borderColor, background: T.cardBg }}
        >
          <span style={{ color: T.textMuted }}>{label}</span>
          <strong style={{ color }}>{value}</strong>
        </div>
      ))}
    </section>
  );
}

function IssueDetail({
  T,
  issue,
  actionForm,
  setActionForm,
  canManage,
  canRectify,
  canReview,
  canDelete,
  submitting,
  buttonStyle,
  fieldStyle,
  pill,
  evidenceState,
  onRectify,
  onReview,
  onAssign,
  onVoid,
  onDelete,
}) {
  const evidenceStages = buildEvidenceStages(issue);
  const originalEvidence = evidenceStages.find((stage) => stage.type === "CREATE");
  const rectificationStages = evidenceStages.filter(
    (stage) => stage.type === "RECTIFY",
  );
  const latestRectification = rectificationStages[rectificationStages.length - 1];
  const evidenceCheck = reviewEvidenceCheck(issue, evidenceState);
  const openEvidence = (fileId) => {
    const evidence = evidenceState.files[fileId];
    if (!evidence || evidence.status !== "ready") {
      alert(evidence?.message || "附件尚未读取完成，请稍后重试");
      return;
    }
    const viewer = window.open(evidence.url, "_blank");
    if (!viewer) {
      alert("浏览器阻止了新窗口，请允许弹出窗口后重试");
      return;
    }
    viewer.opener = null;
  };
  return (
    <>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <div>
          <span style={{ color: T.textMuted, fontSize: 11 }}>
            {issue.issueNo}
          </span>
          <h3 style={{ margin: "5px 0 0", color: T.textPrimary, fontSize: 18 }}>
            {issue.title}
          </h3>
        </div>
        {pill(
          statusLabel(issue),
          issue.overdue
            ? "danger"
            : issue.status === "CLOSED"
              ? "success"
              : "warning",
        )}
      </div>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(2,1fr)",
          gap: 8,
          marginTop: 14,
        }}
      >
        {[
          ["位置", issue.location],
          ["等级", severityLabel(issue.severity)],
          ["负责人", issue.assigneeName || "待重新分配"],
          ["期限", issue.deadline],
          ["发起人", issue.createdByName],
          ["发起时间", formatTime(issue.createTime)],
          [
            "问题来源",
            issue.weeklyInspectionNo
              || (issue.weeklyInspectionId
                ? `质量周检 #${issue.weeklyInspectionId}`
                : "历史独立问题"),
          ],
        ].map(([label, value]) => (
          <div
            key={label}
            style={{
              padding: 9,
              background: T.surface2,
              borderRadius: 6,
              color: T.textSecondary,
              fontSize: 12,
            }}
          >
            <span style={{ color: T.textMuted }}>{label}</span>
            <strong
              style={{ display: "block", marginTop: 3, color: T.textPrimary }}
            >
              {value || "-"}
            </strong>
          </div>
        ))}
      </div>
      <p
        style={{
          padding: "10px 0",
          color: T.textSecondary,
          fontSize: 12,
          lineHeight: 1.6,
        }}
      >
        {issue.description || "无问题描述"}
      </p>
      <div style={{ marginBottom: 14 }}>
        <strong style={{ color: T.textPrimary, fontSize: 13 }}>
          整改前后照片对比
        </strong>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(2,minmax(0,1fr))",
            gap: 10,
            marginTop: 8,
          }}
        >
          <div>
            <div style={{ marginBottom: 5, color: T.textMuted, fontSize: 11 }}>
              原始问题照片
            </div>
            <EvidenceStage
              T={T}
              stage={originalEvidence || {
                key: "missing-original",
                title: "问题发起",
                required: true,
                photoIds: [],
              }}
              evidenceState={evidenceState}
              onOpen={openEvidence}
            />
          </div>
          <div>
            <div style={{ marginBottom: 5, color: T.textMuted, fontSize: 11 }}>
              最新一轮整改照片
            </div>
            <EvidenceStage
              T={T}
              stage={latestRectification || {
                key: "missing-rectification",
                title: "尚未提交整改",
                required: true,
                photoIds: [],
              }}
              evidenceState={evidenceState}
              onOpen={openEvidence}
            />
          </div>
        </div>
      </div>
      <div
        style={{
          marginBottom: 12,
          paddingTop: 4,
        }}
      >
        <strong style={{ color: T.textPrimary, fontSize: 13 }}>完整证据时间线</strong>
        <div
          style={{
            marginTop: 8,
            display: "grid",
            gap: 8,
          }}
        >
          {evidenceStages.map((stage) => (
            <EvidenceStage
              key={stage.key}
              T={T}
              stage={stage}
              evidenceState={evidenceState}
              onOpen={openEvidence}
            />
          ))}
          {!evidenceStages.length && (
            <div
              style={{
                padding: 12,
                borderRadius: 7,
                border: `1px solid ${T.danger}55`,
                background: `${T.danger}0d`,
                color: T.danger,
                fontSize: 11,
              }}
            >
              未找到问题过程留痕，证据无法核对。
            </div>
          )}
        </div>
      </div>
      {evidenceState.loading && (
        <div
          style={{
            padding: "9px 11px",
            borderRadius: 6,
            background: T.activeItemBg,
            color: T.accent,
            fontSize: 11,
          }}
        >
          正在校验过程附件，校验完成前不能复查通过。
        </div>
      )}
      {issue.canRectify && canRectify && (
        <div
          style={{
            padding: 12,
            background: T.surface2,
            borderRadius: 7,
            marginTop: 10,
          }}
        >
          <strong style={{ color: T.textPrimary, fontSize: 13 }}>
            提交整改
          </strong>
          <textarea
            style={{ ...fieldStyle, minHeight: 70, marginTop: 8 }}
            maxLength={1000}
            value={actionForm.description}
            onChange={(e) =>
              setActionForm({ ...actionForm, description: e.target.value })
            }
            placeholder="填写整改措施和结果"
          />
          <input
            type="file"
            accept="image/*"
            multiple
            style={{ ...fieldStyle, marginTop: 8 }}
            onChange={(e) =>
              setActionForm({
                ...actionForm,
                files: Array.from(e.target.files || []),
              })
            }
          />
          <button
            disabled={submitting}
            onClick={onRectify}
            style={{ ...buttonStyle(), marginTop: 8 }}
          >
            提交复查
          </button>
        </div>
      )}
      {issue.canReview && canReview && (
        <div
          style={{
            padding: 12,
            background: T.surface2,
            borderRadius: 7,
            marginTop: 10,
          }}
        >
          <strong style={{ color: T.textPrimary, fontSize: 13 }}>
            复查处理
          </strong>
          {!evidenceCheck.ready && (
            <div
              role="alert"
              style={{
                marginTop: 8,
                padding: "8px 10px",
                borderRadius: 6,
                background: `${T.danger}12`,
                color: T.danger,
                fontSize: 11,
              }}
            >
              {evidenceCheck.message}
            </div>
          )}
          <textarea
            style={{ ...fieldStyle, minHeight: 70, marginTop: 8 }}
            maxLength={1000}
            value={actionForm.comment}
            onChange={(e) =>
              setActionForm({ ...actionForm, comment: e.target.value })
            }
            placeholder="退回时必须填写意见"
          />
          <input
            type="file"
            accept="image/*"
            multiple
            style={{ ...fieldStyle, marginTop: 8 }}
            onChange={(e) =>
              setActionForm({
                ...actionForm,
                files: Array.from(e.target.files || []),
              })
            }
          />
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button
              disabled={submitting}
              onClick={() => onReview(false)}
              style={buttonStyle("danger")}
            >
              退回整改
            </button>
            <button
              disabled={submitting || !evidenceCheck.ready}
              onClick={() => onReview(true)}
              style={buttonStyle()}
              title={evidenceCheck.ready ? "" : evidenceCheck.message}
            >
              复查通过
            </button>
          </div>
        </div>
      )}
      {canManage && !["CLOSED", "VOIDED"].includes(issue.status) && (
        <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
          <button
            disabled={submitting}
            onClick={onAssign}
            style={buttonStyle("secondary")}
          >
            改派/调整期限
          </button>
          <button
            disabled={submitting}
            onClick={onVoid}
            style={buttonStyle("danger")}
          >
            作废问题
          </button>
        </div>
      )}
      {canDelete && (
        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 12 }}>
          <button
            disabled={submitting}
            onClick={onDelete}
            style={buttonStyle("danger")}
          >
            永久删除问题
          </button>
        </div>
      )}
      <div
        style={{
          marginTop: 16,
          borderTop: `1px solid ${T.borderColor}`,
          paddingTop: 12,
        }}
      >
        <strong style={{ color: T.textPrimary, fontSize: 13 }}>操作留痕</strong>
        {(issue.logs || []).map((log) => (
          <div
            key={log.id}
            style={{
              display: "grid",
              gridTemplateColumns: "120px 1fr 140px",
              gap: 10,
              padding: "9px 0",
              borderBottom: `1px solid ${T.borderColor}`,
              fontSize: 11,
              color: T.textSecondary,
            }}
          >
            <span>{actionLabel(log.actionType)}</span>
            <span>
              {log.operatorName || "-"} · {log.comment || "-"}
            </span>
            <span>{formatTime(log.createTime)}</span>
          </div>
        ))}
        {!(issue.logs || []).length && (
          <div style={{ padding: 14, color: T.textMuted, fontSize: 11 }}>
            暂无留痕
          </div>
        )}
      </div>
    </>
  );
}

function EvidenceStage({ T, stage, evidenceState, onOpen }) {
  return (
    <section
      style={{
        padding: 11,
        borderRadius: 7,
        border: `1px solid ${T.borderColor}`,
        background: T.surface2,
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "flex-start",
          justifyContent: "space-between",
          gap: 12,
        }}
      >
        <strong style={{ color: T.textPrimary, fontSize: 12 }}>
          {stage.title}
        </strong>
        <span style={{ color: T.textMuted, fontSize: 10 }}>
          {stage.operatorName || "-"} · {formatTime(stage.createTime)}
        </span>
      </div>
      {stage.comment && (
        <div
          style={{
            marginTop: 6,
            color: T.textSecondary,
            fontSize: 11,
            lineHeight: 1.55,
            whiteSpace: "pre-wrap",
          }}
        >
          {stage.comment}
        </div>
      )}
      {stage.photoIds.length ? (
        <div
          style={{
            display: "flex",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 9,
          }}
        >
          {stage.photoIds.map((fileId, index) => {
            const evidence = evidenceState.files[fileId];
            const ready = evidence?.status === "ready";
            const failed = evidence?.status === "error";
            return (
              <button
                key={fileId}
                type="button"
                disabled={!ready}
                onClick={() => onOpen(fileId)}
                title={failed ? evidence.message : `查看第 ${index + 1} 张证据`}
                style={{
                  width: 92,
                  minHeight: 70,
                  padding: 0,
                  overflow: "hidden",
                  borderRadius: 6,
                  border: `1px solid ${
                    failed ? `${T.danger}88` : T.borderColor
                  }`,
                  background: T.cardBg,
                  color: failed ? T.danger : T.textSecondary,
                  cursor: ready ? "pointer" : "not-allowed",
                  fontSize: 10,
                }}
              >
                {ready && evidence.contentType.startsWith("image/") ? (
                  <img
                    src={evidence.url}
                    alt={`${stage.title}证据 ${index + 1}`}
                    style={{
                      display: "block",
                      width: "100%",
                      height: 70,
                      objectFit: "cover",
                    }}
                  />
                ) : failed ? (
                  <span style={{ display: "block", padding: 8 }}>
                    附件 {index + 1}
                    <br />
                    读取失败
                  </span>
                ) : (
                  <span style={{ display: "block", padding: 8 }}>
                    附件 {index + 1}
                    <br />
                    {ready ? "点击查看" : "读取中..."}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      ) : (
        <div
          style={{
            marginTop: 7,
            color: stage.required ? T.danger : T.textMuted,
            fontSize: 11,
          }}
        >
          {stage.required
            ? "本阶段未找到必需的照片附件"
            : "本次复查未上传附件"}
        </div>
      )}
      {stage.photoIds.some(
        (fileId) => evidenceState.files[fileId]?.status === "error",
      ) && (
        <div
          role="alert"
          style={{ marginTop: 7, color: T.danger, fontSize: 10 }}
        >
          {stage.photoIds
            .filter(
              (fileId) => evidenceState.files[fileId]?.status === "error",
            )
            .map(
              (fileId) =>
                `附件 ${fileId}：${
                  evidenceState.files[fileId]?.message || "读取失败"
                }`,
            )
            .join("；")}
        </div>
      )}
    </section>
  );
}

function TableHead({ T, columns, labels }) {
  return (
    <div
      className="quality-table-head"
      style={{
        display: "grid",
        gridTemplateColumns: columns,
        minWidth: 920,
        gap: 10,
        padding: "10px 14px",
        position: "sticky",
        top: 0,
        zIndex: 1,
        background: T.surface2,
        borderBottom: `1px solid ${T.borderColor}`,
        color: T.textMuted,
        fontSize: 11,
      }}
    >
      {labels.map((label) => (
        <span key={label}>{label}</span>
      ))}
    </div>
  );
}
function TableRow({ T, columns, children }) {
  return (
    <div
      className="quality-table-row"
      style={{
        display: "grid",
        gridTemplateColumns: columns,
        minWidth: 920,
        gap: 10,
        alignItems: "center",
        minHeight: 52,
        padding: "8px 14px",
        borderBottom: `1px solid ${T.borderColor}`,
        color: T.textSecondary,
        fontSize: 12,
      }}
    >
      {children}
    </div>
  );
}
function LoadingState({ T, text }) {
  return (
    <div
      className="quality-state quality-state--loading"
      role="status"
      aria-live="polite"
      style={{
        color: T.textMuted,
      }}
    >
      <span className="quality-state-spinner" style={{ borderColor: `${T.accent}33`, borderTopColor: T.accent }} />
      {text}
    </div>
  );
}
function LoadError({ T, text, onRetry, buttonStyle }) {
  return (
    <div className="quality-state" role="alert" style={{ color: T.danger }}>
      <span className="quality-state-symbol" style={{ color: T.danger, background: `${T.danger}12` }}>!</span>
      <strong>内容加载失败</strong>
      <span>{text}</span>
      <button
        onClick={onRetry}
        style={{ ...buttonStyle("secondary"), marginTop: 4 }}
      >
        重新加载
      </button>
    </div>
  );
}
function Empty({ T, title, text }) {
  const heading = title || text;
  const description = title ? text : "调整筛选条件或稍后再查看。";
  return (
    <div
      className="quality-state quality-state--empty"
      style={{ color: T.textMuted }}
    >
      <span
        className="quality-state-symbol"
        aria-hidden="true"
        style={{ color: T.accent, background: T.activeItemBg }}
      >
        ✓
      </span>
      <strong style={{ color: T.textPrimary }}>{heading}</strong>
      <span>{description}</span>
    </div>
  );
}
function QualityExportModal({
  T,
  form,
  setForm,
  jobs,
  loading,
  submitting,
  errorText,
  fieldStyle,
  buttonStyle,
  onClose,
  onSubmit,
  onRefresh,
  onDownload,
}) {
  const updateForm = (field, value) => setForm((current) => ({
    ...current,
    [field]: value,
  }));
  return (
    <Modal T={T} title="质量问题月度汇总导出" onClose={onClose}>
      <div
        className="quality-export-form"
        style={{ background: T.surface2, borderColor: T.borderColor }}
      >
        <label style={labelStyle(T)}>
          开始日期 *
          <input
            aria-label="导出开始日期"
            type="date"
            max={localDateText()}
            value={form.startDate}
            onChange={(e) => updateForm("startDate", e.target.value)}
            style={fieldStyle}
          />
        </label>
        <label style={labelStyle(T)}>
          结束日期 *
          <input
            aria-label="导出结束日期"
            type="date"
            max={localDateText()}
            value={form.endDate}
            onChange={(e) => updateForm("endDate", e.target.value)}
            style={fieldStyle}
          />
        </label>
        <label style={labelStyle(T)}>
          问题来源
          <select
            value={form.source}
            onChange={(e) => updateForm("source", e.target.value)}
            style={fieldStyle}
          >
            <option value="ALL">全部问题</option>
            <option value="WEEKLY">周检问题</option>
            <option value="HISTORICAL">历史独立问题</option>
          </select>
        </label>
        <label style={labelStyle(T)}>
          当前状态
          <select
            value={form.status}
            onChange={(e) => updateForm("status", e.target.value)}
            style={fieldStyle}
          >
            <option value="ALL">全部状态</option>
            <option value="PENDING">待整改</option>
            <option value="OVERDUE">已逾期</option>
            <option value="RECHECK">待复查</option>
            <option value="CLOSED">已关闭</option>
            <option value="VOIDED">已作废</option>
          </select>
        </label>
        <label className="quality-export-keyword" style={labelStyle(T)}>
          关键词
          <input
            maxLength={100}
            value={form.keyword}
            onChange={(e) => updateForm("keyword", e.target.value)}
            placeholder="问题编号、标题、位置或负责人"
            style={fieldStyle}
          />
        </label>
        <div className="quality-export-submit">
          <span style={{ color: T.textMuted, fontSize: 11 }}>
            最多 300 个问题、800 张原图；完成后的文件保留 7 天。
          </span>
          <button disabled={submitting} onClick={onSubmit} style={buttonStyle()}>
            {submitting ? "正在创建..." : "生成 Excel"}
          </button>
        </div>
      </div>

      <div className="quality-export-history-head">
        <div>
          <strong style={{ color: T.textPrimary }}>近期导出任务</strong>
          <div style={{ color: T.textMuted, fontSize: 11, marginTop: 3 }}>
            后台生成期间可以关闭窗口，稍后重新进入查看进度。
          </div>
        </div>
        <button disabled={loading} onClick={onRefresh} style={buttonStyle("secondary")}>
          {loading ? "刷新中..." : "刷新"}
        </button>
      </div>
      {errorText && (
        <div role="alert" className="quality-export-error" style={{ color: T.danger }}>
          {errorText}
        </div>
      )}
      <div className="quality-export-jobs">
        {jobs.map((job) => (
          <div
            key={job.id}
            className="quality-export-job"
            style={{ borderColor: T.borderColor, background: T.cardBg }}
          >
            <div className="quality-export-job__main">
              <strong style={{ color: T.textPrimary }}>
                {job.startDate} 至 {job.endDate}
              </strong>
              <span style={{ color: T.textMuted }}>
                {job.issueCount || 0} 个问题 · {job.photoCount || 0} 张照片 · 创建于 {formatTime(job.createTime)}
              </span>
              {job.errorMessage && (
                <span role="alert" style={{ color: T.danger }}>
                  {job.errorMessage}
                </span>
              )}
              {job.status === "SUCCEEDED" && (
                <span style={{ color: T.textMuted }}>
                  文件有效期至 {formatTime(job.expiresTime)}
                </span>
              )}
            </div>
            <div className="quality-export-job__actions">
              <span
                className="quality-export-progress"
                style={{ color: job.status === "FAILED" ? T.danger : T.accent }}
              >
                {exportJobStatusLabel(job.status)}
                {["PENDING", "RUNNING"].includes(job.status)
                  ? ` ${job.progress || 0}%`
                  : ""}
              </span>
              {job.downloadable && (
                <button onClick={() => onDownload(job)} style={buttonStyle()}>
                  下载 Excel
                </button>
              )}
            </div>
          </div>
        ))}
        {!loading && !jobs.length && (
          <div className="quality-export-empty" style={{ color: T.textMuted }}>
            暂无导出任务
          </div>
        )}
      </div>
    </Modal>
  );
}
function Modal({ T, title, onClose, children }) {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1200,
        background: "rgba(10,20,35,.58)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: 760,
          maxWidth: "96vw",
          maxHeight: "88vh",
          overflow: "auto",
          background: T.modalBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 8,
          padding: 18,
          boxShadow: "0 18px 50px rgba(0,0,0,.28)",
        }}
        onClick={(event) => event.stopPropagation()}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 16,
          }}
        >
          <strong style={{ color: T.textPrimary, fontSize: 16 }}>
            {title}
          </strong>
          <button
            onClick={onClose}
            style={{
              border: 0,
              background: "transparent",
              color: T.textMuted,
              fontSize: 20,
              cursor: "pointer",
            }}
          >
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
function ModalActions({
  buttonStyle,
  submitting,
  onCancel,
  onSubmit,
  submitTone,
  submitLabel = "确认提交",
}) {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "flex-end",
        gap: 8,
        marginTop: 18,
      }}
    >
      <button
        disabled={submitting}
        onClick={onCancel}
        style={buttonStyle("secondary")}
      >
        取消
      </button>
      <button
        disabled={submitting}
        onClick={onSubmit}
        style={buttonStyle(submitTone)}
      >
        {submitting ? "提交中..." : submitLabel}
      </button>
    </div>
  );
}
function labelStyle(T) {
  return {
    display: "flex",
    flexDirection: "column",
    gap: 5,
    marginBottom: 10,
    color: T.textSecondary,
    fontSize: 12,
  };
}
