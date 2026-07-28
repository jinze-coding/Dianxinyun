import React, { useEffect, useMemo, useState } from "react";
import {
  assignQualityIssue,
  createQualityIssue,
  getQualityIssue,
  getQualityIssues,
  getQualitySummary,
  reviewQualityIssue,
  submitQualityRectification,
} from "../../services/quality";
import {
  deleteFile,
  downloadFile,
  getFileList,
  updateFileStatus,
  uploadFile,
} from "../../services/file";
import { getProjectMembers } from "../../services/projectMembers";
import { hasProjectPermission, isPlatformAdmin } from "../../utils/permissions";

const EMPTY_CREATE = {
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
  issue.overdue
    ? "已逾期"
    : { PENDING: "待整改", RECHECK: "待复查", CLOSED: "已关闭" }[
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
  })[value] || value;

export default function QualityManagementPage({ projectId, theme: T, currentUser }) {
  const [summary, setSummary] = useState(null);
  const [issues, setIssues] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [members, setMembers] = useState([]);
  const [activeTab, setActiveTab] = useState("issues");
  const [status, setStatus] = useState("ALL");
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorText, setErrorText] = useState("");
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

  const load = async () => {
    if (!projectId) return;
    setLoading(true);
    setErrorText("");
    try {
      const [summaryRes, issueRes, fileRes] = await Promise.all([
        getQualitySummary(projectId),
        getQualityIssues(projectId, {
          status,
          keyword: keyword.trim() || undefined,
        }),
        getFileList(projectId, { businessType: "QUALITY_DOCUMENT" }),
      ]);
      if (summaryRes.code !== 200)
        throw new Error(summaryRes.message || "质量统计加载失败");
      if (issueRes.code !== 200)
        throw new Error(issueRes.message || "质量问题加载失败");
      setSummary(summaryRes.data);
      setIssues(issueRes.data || []);
      setDocuments(fileRes.code === 200 ? fileRes.data || [] : []);
    } catch (error) {
      setSummary(null);
      setIssues([]);
      setErrorText(error.message || "质量数据加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setStatus("ALL");
    setKeyword("");
    setModal(null);
    setSelectedIssue(null);
  }, [projectId]);
  useEffect(() => {
    load();
  }, [projectId, status]);

  const canManage = Boolean(summary?.canManage)
    && (isPlatformAdmin(currentUser)
      || hasProjectPermission(currentUser, projectId, "quality.manage"));
  const canRectify = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, "quality.rectify");
  const canReview = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, "quality.review");
  const filteredIssues = useMemo(
    () =>
      !keyword.trim()
        ? issues
        : issues.filter((issue) =>
            `${issue.title}${issue.location || ""}${issue.assigneeName || ""}`
              .toLowerCase()
              .includes(keyword.trim().toLowerCase()),
          ),
    [issues, keyword],
  );

  const loadMembers = async () => {
    try {
      const res = await getProjectMembers(projectId);
      setMembers(res.code === 200 ? res.data || [] : []);
    } catch (_) {
      setMembers([]);
    }
  };

  const openCreate = async () => {
    await loadMembers();
    const date = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10);
    setCreateForm({ ...EMPTY_CREATE, deadline: date });
    setModal("create");
  };

  const openIssue = async (issue) => {
    try {
      const res = await getQualityIssue(issue.id);
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
    } catch (error) {
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
    if (!createForm.title.trim() || !createForm.files.length)
      return alert("请填写问题标题并上传至少一张问题照片");
    setSubmitting(true);
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
      setModal(null);
      await load();
    } catch (error) {
      await Promise.allSettled(photoFileIds.map((id) => deleteFile(id)));
      alert(error.message || "发起失败");
    } finally {
      setSubmitting(false);
    }
  };

  const submitRectification = async () => {
    if (!actionForm.description.trim() || !actionForm.files.length)
      return alert("请填写整改说明并上传至少一张整改照片");
    setSubmitting(true);
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
      setModal(null);
      await load();
    } catch (error) {
      await Promise.allSettled(photoFileIds.map((id) => deleteFile(id)));
      alert(error.message || "整改提交失败");
    } finally {
      setSubmitting(false);
    }
  };

  const submitReview = async (passed) => {
    if (!passed && !actionForm.comment.trim())
      return alert("退回整改时必须填写复查意见");
    setSubmitting(true);
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
      setModal(null);
      await load();
    } catch (error) {
      await Promise.allSettled(photoFileIds.map((id) => deleteFile(id)));
      alert(error.message || "复查失败");
    } finally {
      setSubmitting(false);
    }
  };

  const submitAssign = async () => {
    setSubmitting(true);
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
      await load();
    } catch (error) {
      alert(error.message || "改派失败");
    } finally {
      setSubmitting(false);
    }
  };

  const uploadDocument = async () => {
    if (!documentFile) return alert("请选择质量资料");
    setSubmitting(true);
    try {
      const res = await uploadFile({
        file: documentFile,
        projectId,
        fileType: "质量资料",
        businessType: "QUALITY_DOCUMENT",
      });
      if (res.code !== 200) throw new Error(res.message || "上传失败");
      setDocumentFile(null);
      await load();
    } catch (error) {
      alert(error.message || "上传失败");
    } finally {
      setSubmitting(false);
    }
  };

  const openFile = async (fileId) => {
    try {
      const blob = await downloadFile(fileId);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (error) {
      alert(error.message || "文件打开失败");
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
          ["今日检查", summary?.todayCheckCount || 0, T.accent],
          ["待整改", summary?.pendingCount || 0, T.warning],
          ["已逾期", summary?.overdueCount || 0, T.danger],
          ["待复查", summary?.recheckCount || 0, T.accent2],
          ["闭环率", `${summary?.closureRate || 0}%`, T.success],
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
              onChange={(e) => setStatus(e.target.value)}
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
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && load()}
              placeholder="搜索问题、位置、负责人"
              style={{ ...fieldStyle, width: 240 }}
            />
            <button onClick={load} style={buttonStyle("secondary")}>
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
          <div style={{ display: "flex", gap: 7, alignItems: "center" }}>
            <input
              type="file"
              onChange={(e) => setDocumentFile(e.target.files?.[0] || null)}
              style={{ ...fieldStyle, width: 260 }}
            />
            <button
              disabled={!canManage || submitting}
              onClick={uploadDocument}
              style={buttonStyle()}
            >
              上传资料
            </button>
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
        {errorText ? (
          <div style={{ padding: 24, color: T.danger }}>{errorText}</div>
        ) : activeTab === "issues" ? (
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
            {filteredIssues.map((issue) => (
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
                    {severityLabel(issue.severity)}
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
            {!filteredIssues.length && (
              <Empty T={T} text="当前项目暂无质量问题" />
            )}
          </>
        ) : (
          <>
            <TableHead
              T={T}
              columns="1.5fr .8fr .8fr .9fr 160px"
              labels={["文件名", "类型", "状态", "上传时间", "操作"]}
            />
            {documents.map((file) => (
              <TableRow
                key={file.id}
                T={T}
                columns="1.5fr .8fr .8fr .9fr 160px"
              >
                <strong>{file.fileName}</strong>
                <span>{file.fileType || "-"}</span>
                {pill(
                  file.status || "已上传",
                  file.status === "ARCHIVED" || file.status === "已归档"
                    ? "success"
                    : "normal",
                )}
                <span>{formatTime(file.createTime)}</span>
                <span style={{ display: "flex", gap: 6 }}>
                  <button
                    onClick={() => openFile(file.id)}
                    style={buttonStyle("secondary")}
                  >
                    查看
                  </button>
                  {canManage && (
                    <>
                      <button
                        onClick={async () => {
                          await updateFileStatus(file.id, "ARCHIVED");
                          await load();
                        }}
                        style={buttonStyle("secondary")}
                      >
                        归档
                      </button>
                      <button
                        onClick={async () => {
                          if (window.confirm("确认删除该资料？")) {
                            await deleteFile(file.id);
                            await load();
                          }
                        }}
                        style={buttonStyle("danger")}
                      >
                        删除
                      </button>
                    </>
                  )}
                </span>
              </TableRow>
            ))}
            {!documents.length && <Empty T={T} text="暂无质量资料" />}
          </>
        )}
      </div>
      {modal && modal !== 'assign' && (
        <Modal
          T={T}
          title={modal === "create" ? "发起质量检查" : "质量问题详情"}
          onClose={() => setModal(null)}
        >
          {modal === "create" ? (
            <>
              <label style={labelStyle(T)}>
                问题标题 *
                <input
                  style={fieldStyle}
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
                    <option value="">默认当前用户</option>
                    {members.map((member) => (
                      <option key={member.userId} value={member.userId}>
                        {member.realName || member.username}
                      </option>
                    ))}
                  </select>
                </label>
                <label style={labelStyle(T)}>
                  整改期限
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
                onCancel={() => setModal(null)}
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
                openFile={openFile}
                onRectify={submitRectification}
                onReview={submitReview}
                onAssign={async () => {
                  await loadMembers();
                  setModal("assign");
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
              {members.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.realName || member.username}
                </option>
              ))}
            </select>
          </label>
          <label style={labelStyle(T)}>
            整改期限
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
  openFile,
  onRectify,
  onReview,
  onAssign,
}) {
  const photos = [
    ...(issue.issuePhotoFileIds || []),
    ...(issue.rectificationPhotoFileIds || []),
    ...(issue.reviewPhotoFileIds || []),
  ];
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
      {photos.length > 0 && (
        <div
          style={{
            display: "flex",
            gap: 7,
            flexWrap: "wrap",
            marginBottom: 12,
          }}
        >
          {photos.map((id, index) => (
            <button
              key={`${id}-${index}`}
              onClick={() => openFile(id)}
              style={buttonStyle("secondary")}
            >
              查看照片 {index + 1}
            </button>
          ))}
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
          <textarea
            style={{ ...fieldStyle, minHeight: 70, marginTop: 8 }}
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
              disabled={submitting}
              onClick={() => onReview(true)}
              style={buttonStyle()}
            >
              复查通过
            </button>
          </div>
        </div>
      )}
      {canManage && issue.status !== "CLOSED" && (
        <button
          onClick={onAssign}
          style={{ ...buttonStyle("secondary"), marginTop: 12 }}
        >
          改派/调整期限
        </button>
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
function ModalActions({ buttonStyle, submitting, onCancel, onSubmit }) {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "flex-end",
        gap: 8,
        marginTop: 18,
      }}
    >
      <button onClick={onCancel} style={buttonStyle("secondary")}>
        取消
      </button>
      <button disabled={submitting} onClick={onSubmit} style={buttonStyle()}>
        {submitting ? "提交中..." : "确认提交"}
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
