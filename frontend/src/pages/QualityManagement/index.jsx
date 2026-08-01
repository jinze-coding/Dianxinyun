import React, { useEffect, useRef, useState } from "react";
import {
  assignQualityIssue,
  createQualityIssue,
  getQualityAssignees,
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
import { hasProjectPermission, isPlatformAdmin } from "../../utils/permissions";

const EMPTY_CREATE = {
  requestKey: "",
  title: "",
  location: "",
  description: "",
  severity: "NORMAL",
  assigneeId: "",
  deadline: "",
  files: [],
};
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
const issueQueryKey = (projectId, status, keyword, pageNo) =>
  `${projectKey(projectId)}|${status}|${keyword.trim()}|${pageNo}`;
const createRequestKey = () =>
  `web-${typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`}`;
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
  attachFallback("CREATE", issue?.issuePhotoFileIds, {
    key: "fallback-create",
    type: "CREATE",
    title: "问题发起",
    required: true,
    operatorName: issue?.createdByName,
    createTime: issue?.createTime,
    comment: issue?.description,
  });
  attachFallback("RECTIFY", issue?.rectificationPhotoFileIds, {
    key: "fallback-rectify",
    type: "RECTIFY",
    title: "最近一轮整改",
    required: true,
    operatorName: issue?.assigneeName,
    createTime: issue?.rectifiedTime,
    comment: issue?.rectificationDescription,
  });
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

export default function QualityManagementPage({ projectId, theme: T, currentUser }) {
  const [summary, setSummary] = useState(null);
  const [summaryProjectKey, setSummaryProjectKey] = useState("");
  const [issues, setIssues] = useState([]);
  const [issueTotal, setIssueTotal] = useState(0);
  const [loadedIssueQueryKey, setLoadedIssueQueryKey] = useState("");
  const [documents, setDocuments] = useState([]);
  const [documentsProjectKey, setDocumentsProjectKey] = useState("");
  const [documentScope, setDocumentScope] = useState("ACTIVE");
  const [members, setMembers] = useState([]);
  const [activeTab, setActiveTab] = useState("issues");
  const [status, setStatus] = useState("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorText, setErrorText] = useState("");
  const [errorQueryKey, setErrorQueryKey] = useState("");
  const [documentsLoading, setDocumentsLoading] = useState(false);
  const [documentsErrorText, setDocumentsErrorText] = useState("");
  const [documentsErrorProjectKey, setDocumentsErrorProjectKey] = useState("");
  const [modal, setModal] = useState(null);
  const [selectedIssue, setSelectedIssue] = useState(null);
  const [createForm, setCreateForm] = useState(EMPTY_CREATE);
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
  const evidenceUrlsRef = useRef([]);
  const submittingRef = useRef(false);
  const documentBusyRef = useRef(false);
  const openingFileRef = useRef(false);

  currentProjectIdRef.current = projectId;

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
    const targetKeyword = (options.keyword ?? appliedKeyword).trim();
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
      targetKeyword,
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
          keyword: targetKeyword || undefined,
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

  useEffect(() => {
    issueRequestIdRef.current += 1;
    documentRequestIdRef.current += 1;
    memberRequestIdRef.current += 1;
    detailRequestIdRef.current += 1;
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
    setPageNo(1);
    setKeyword("");
    setAppliedKeyword("");
    setModal(null);
    setSelectedIssue(null);
    setDocumentFile(null);
  }, [projectId]);
  useEffect(() => {
    loadQualityData();
  }, [projectId, status, appliedKeyword, pageNo]);
  useEffect(() => {
    if (activeTab === "documents") {
      loadDocuments();
    }
  }, [activeTab, projectId]);

  const currentProjectKey = projectKey(projectId);
  const currentQueryKey = issueQueryKey(
    projectId,
    status,
    appliedKeyword,
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

  const canManage = Boolean(currentSummary?.canManage)
    && (isPlatformAdmin(currentUser)
      || hasProjectPermission(currentUser, projectId, "quality.manage"));
  const canRectify = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, "quality.rectify");
  const canReview = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, "quality.review");
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

  const openCreate = async () => {
    if (submittingRef.current) return;
    const candidates = await loadMembers();
    if (!candidates) return;
    const date = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10);
    setCreateForm({
      ...EMPTY_CREATE,
      requestKey: createRequestKey(),
      deadline: date,
    });
    setModal("create");
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

  const submitCreate = async () => {
    if (submittingRef.current) return;
    if (!createForm.title.trim() || !createForm.files.length)
      return alert("请填写问题标题并上传至少一张问题照片");
    if (!createForm.assigneeId)
      return alert("请选择整改负责人");
    if (!beginSubmitting()) return;
    let photoFileIds = [];
    try {
      photoFileIds = await uploadFiles(
        createForm.files,
        "QUALITY_PENDING",
      );
      const res = await createQualityIssue({
        ...createForm,
        files: undefined,
        projectId,
        assigneeId: createForm.assigneeId
          ? Number(createForm.assigneeId)
          : undefined,
        photoFileIds,
      });
      if (res.code !== 200) throw new Error(res.message || "发起失败");
      const boundPhotoIds = new Set(
        parsePhotoFileIds(res.data?.issuePhotoFileIds),
      );
      await Promise.allSettled(
        photoFileIds
          .filter((id) => !boundPhotoIds.has(id))
          .map((id) => deleteFile(id)),
      );
      closeModal();
      setPageNo(1);
      await loadQualityData({ pageNo: 1 });
    } catch (error) {
      await Promise.allSettled(photoFileIds.map((id) => deleteFile(id)));
      alert(error.message || "发起失败");
    } finally {
      finishSubmitting();
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
    if (
      !window.confirm(
        `确认永久删除质量资料《${file.fileName}》？删除后无法恢复。`,
      )
    ) {
      return;
    }
    documentBusyRef.current = true;
    setDocumentBusyAction(`delete:${file.id}`);
    try {
      const res = await deleteFile(file.id);
      if (res.code !== 200) throw new Error(res.message || "删除失败");
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
    if (nextKeyword === appliedKeyword) {
      if (pageNo !== 1) {
        setPageNo(1);
      } else {
        loadQualityData({ keyword: nextKeyword, pageNo: 1 });
      }
      return;
    }
    setPageNo(1);
    setAppliedKeyword(nextKeyword);
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
      style={{
        height: "100%",
        padding: 16,
        display: "flex",
        flexDirection: "column",
        gap: 12,
        overflow: "hidden",
        background: T.pageBg,
      }}
    >
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(5,minmax(0,1fr))",
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 8,
          overflow: "hidden",
          flexShrink: 0,
        }}
      >
        {[
          ["今日新增问题", currentSummary?.todayCheckCount || 0, T.accent],
          ["待整改", currentSummary?.pendingCount || 0, T.warning],
          ["已逾期", currentSummary?.overdueCount || 0, T.danger],
          ["待复查", currentSummary?.recheckCount || 0, T.accent2],
          ["闭环率", `${currentSummary?.closureRate || 0}%`, T.success],
        ].map(([label, value, color], index) => (
          <div
            key={label}
            style={{
              padding: "14px 18px",
              borderLeft: index ? `1px solid ${T.borderColor}` : "none",
            }}
          >
            <div style={{ fontSize: 11, color: T.textMuted }}>{label}</div>
            <div style={{ marginTop: 5, fontSize: 25, fontWeight: 800, color }}>
              {value}
            </div>
          </div>
        ))}
      </div>
      <div
        style={{
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 8,
          padding: "9px 12px",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 12,
          flexShrink: 0,
        }}
      >
        <div style={{ display: "flex", gap: 6 }}>
          <button
            onClick={() => setActiveTab("issues")}
            style={{
              ...buttonStyle(activeTab === "issues" ? "primary" : "secondary"),
              background: activeTab === "issues" ? T.accent : T.surface2,
            }}
          >
            质量问题
          </button>
          <button
            onClick={() => setActiveTab("documents")}
            style={{
              ...buttonStyle(
                activeTab === "documents" ? "primary" : "secondary",
              ),
              background: activeTab === "documents" ? T.accent : T.surface2,
            }}
          >
            质量资料
          </button>
        </div>
        {activeTab === "issues" ? (
          <div style={{ display: "flex", gap: 7 }}>
            <select
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
            </select>
            <input
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
              disabled={!canManage}
              onClick={openCreate}
              style={buttonStyle()}
            >
              发起检查
            </button>
          </div>
        ) : (
          <div
            style={{
              display: "flex",
              gap: 7,
              alignItems: "center",
              justifyContent: "flex-end",
              flexWrap: "wrap",
            }}
          >
            <button
              onClick={() => setDocumentScope("ACTIVE")}
              style={buttonStyle(
                documentScope === "ACTIVE" ? "primary" : "secondary",
              )}
            >
              有效资料 {activeDocuments.length}
            </button>
            <button
              onClick={() => setDocumentScope("ARCHIVED")}
              style={buttonStyle(
                documentScope === "ARCHIVED" ? "primary" : "secondary",
              )}
            >
              历史归档 {archivedDocuments.length}
            </button>
            <button
              disabled={documentsAreLoading || Boolean(documentBusyAction)}
              onClick={() => loadDocuments()}
              style={buttonStyle("secondary")}
            >
              刷新
            </button>
            {documentScope === "ACTIVE" && (
              <>
                <input
                  type="file"
                  onChange={(e) => setDocumentFile(e.target.files?.[0] || null)}
                  style={{ ...fieldStyle, width: 260 }}
                />
                <button
                  disabled={
                    !canManage || submitting || Boolean(documentBusyAction)
                  }
                  onClick={uploadDocument}
                  style={buttonStyle()}
                >
                  {submitting ? "上传中..." : "上传资料"}
                </button>
              </>
            )}
          </div>
        )}
      </div>
      <div
        style={{
          flex: 1,
          minHeight: 0,
          overflow: "auto",
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 8,
        }}
      >
        {activeTab === "issues" ? (
          issuesAreLoading ? (
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
              columns="120px 1.5fr 1fr .8fr .8fr .8fr 90px"
              labels={[
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
                columns="120px 1.5fr 1fr .8fr .8fr .8fr 90px"
              >
                <span style={{ color: T.textMuted }}>{issue.issueNo}</span>
                <span>
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
                <span>{issue.assigneeName || "-"}</span>
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
          )
        ) : documentsAreLoading ? (
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
            <div
              style={{
                padding: "10px 14px",
                background: T.surface2,
                borderBottom: `1px solid ${T.borderColor}`,
                color: T.textMuted,
                fontSize: 11,
                lineHeight: 1.6,
              }}
            >
              {documentScope === "ACTIVE"
                ? "当前仅展示有效质量资料；已归档资料可在“历史归档”中查看并恢复。"
                : "历史归档资料保持只读，可恢复为有效资料或永久删除。"}
            </div>
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
                        `${
                          isArchivedDocument(file) ? "restore" : "archive"
                        }:${file.id}`
                          ? isArchivedDocument(file)
                            ? "恢复中..."
                            : "归档中..."
                          : isArchivedDocument(file)
                            ? "恢复"
                            : "归档"}
                      </button>
                      <button
                        disabled={Boolean(documentBusyAction)}
                        onClick={() => removeDocument(file)}
                        style={buttonStyle("danger")}
                      >
                        {documentBusyAction === `delete:${file.id}`
                          ? "删除中..."
                          : "删除"}
                      </button>
                    </>
                  )}
                </span>
              </TableRow>
            ))}
            {!visibleDocuments.length && (
              <Empty
                T={T}
                text={
                  documentScope === "ARCHIVED"
                    ? "暂无已归档质量资料"
                    : "暂无有效质量资料"
                }
              />
            )}
          </>
        )}
      </div>
      {modal && !["assign", "void"].includes(modal) && (
        <Modal
          T={T}
          title={modal === "create" ? "发起质量检查" : "质量问题详情"}
          onClose={closeModal}
        >
          {modal === "create" ? (
            <>
              <label style={labelStyle(T)}>
                问题标题 *
                <input
                  style={fieldStyle}
                  maxLength={200}
                  value={createForm.title}
                  onChange={(e) =>
                    setCreateForm({ ...createForm, title: e.target.value })
                  }
                />
              </label>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 10,
                }}
              >
                <label style={labelStyle(T)}>
                  问题位置
                  <input
                    style={fieldStyle}
                    maxLength={200}
                    value={createForm.location}
                    onChange={(e) =>
                      setCreateForm({ ...createForm, location: e.target.value })
                    }
                  />
                </label>
                <label style={labelStyle(T)}>
                  严重等级
                  <select
                    style={fieldStyle}
                    value={createForm.severity}
                    onChange={(e) =>
                      setCreateForm({ ...createForm, severity: e.target.value })
                    }
                  >
                    <option value="NORMAL">一般</option>
                    <option value="WARNING">重要</option>
                    <option value="DANGER">严重</option>
                  </select>
                </label>
                <label style={labelStyle(T)}>
                  整改负责人
                  <select
                    style={fieldStyle}
                    value={createForm.assigneeId}
                    onChange={(e) =>
                      setCreateForm({
                        ...createForm,
                        assigneeId: e.target.value,
                      })
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
                    value={createForm.deadline}
                    onChange={(e) =>
                      setCreateForm({ ...createForm, deadline: e.target.value })
                    }
                  />
                </label>
              </div>
              <label style={labelStyle(T)}>
                问题描述
                <textarea
                  style={{ ...fieldStyle, minHeight: 80 }}
                  maxLength={1000}
                  value={createForm.description}
                  onChange={(e) =>
                    setCreateForm({
                      ...createForm,
                      description: e.target.value,
                    })
                  }
                />
              </label>
              <label style={labelStyle(T)}>
                问题照片 *
                <input
                  type="file"
                  accept="image/*"
                  multiple
                  style={fieldStyle}
                  onChange={(e) =>
                    setCreateForm({
                      ...createForm,
                      files: Array.from(e.target.files || []),
                    })
                  }
                />
              </label>
              <ModalActions
                buttonStyle={buttonStyle}
                submitting={submitting}
                onCancel={closeModal}
                onSubmit={submitCreate}
              />
            </>
          ) : (
            selectedIssue && (
              <IssueDetail
                T={T}
                issue={selectedIssue}
                members={members}
                actionForm={actionForm}
                setActionForm={setActionForm}
                canManage={canManage}
                canRectify={canRectify}
                canReview={canReview}
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
                  setActionForm((current) => ({
                    ...current,
                    assigneeId: "",
                  }));
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
              />
            )
          )}
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

function IssueDetail({
  T,
  issue,
  actionForm,
  setActionForm,
  canManage,
  canRectify,
  canReview,
  submitting,
  buttonStyle,
  fieldStyle,
  pill,
  evidenceState,
  onRectify,
  onReview,
  onAssign,
  onVoid,
}) {
  const evidenceStages = buildEvidenceStages(issue);
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
          ["负责人", issue.assigneeName],
          ["期限", issue.deadline],
          ["发起人", issue.createdByName],
          ["发起时间", formatTime(issue.createTime)],
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
      <div
        style={{
          marginBottom: 12,
          paddingTop: 4,
        }}
      >
        <strong style={{ color: T.textPrimary, fontSize: 13 }}>过程证据</strong>
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
      style={{
        display: "grid",
        gridTemplateColumns: columns,
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
      style={{
        display: "grid",
        gridTemplateColumns: columns,
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
      role="status"
      aria-live="polite"
      style={{
        padding: 36,
        textAlign: "center",
        color: T.textMuted,
        fontSize: 12,
      }}
    >
      {text}
    </div>
  );
}
function LoadError({ T, text, onRetry, buttonStyle }) {
  return (
    <div style={{ padding: 24, color: T.danger, fontSize: 12 }}>
      <div>{text}</div>
      <button
        onClick={onRetry}
        style={{ ...buttonStyle("secondary"), marginTop: 12 }}
      >
        重新加载
      </button>
    </div>
  );
}
function Empty({ T, text }) {
  return (
    <div
      style={{
        padding: 36,
        textAlign: "center",
        color: T.textMuted,
        fontSize: 12,
      }}
    >
      {text}
    </div>
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
