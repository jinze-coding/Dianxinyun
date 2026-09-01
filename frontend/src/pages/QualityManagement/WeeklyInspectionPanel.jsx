import React, { useEffect, useRef, useState } from "react";

import { deleteFile, downloadFile, uploadFile } from "../../services/file";
import {
  createOrResumeWeeklyInspectionDraft,
  discardWeeklyInspectionDraft,
  getQualityAssignees,
  getWeeklyInspection,
  getWeeklyInspectionPage,
  getWeeklyInspectionReminderAssignees,
  getWeeklyInspectionReminderSetting,
  getWeeklyInspectionSummary,
  saveWeeklyInspectionDraft,
  submitWeeklyInspection,
  updateWeeklyInspectionReminderSetting,
} from "../../services/quality";
import {
  addLocalDays,
  buildWeeklyDraftPayload,
  formatLocalDate,
  inspectionToEditor,
  isFutureWeek,
  mondayOfWeek,
  newWeeklyItem,
  validateWeeklySubmission,
} from "../../utils/qualityWeeklyInspection";

const PAGE_SIZE = 20;
const statusText = (status) =>
  ({ DRAFT: "共享草稿", SUBMITTED: "已提交" })[status] || status || "-";
const issueStatusText = (status) =>
  ({ PENDING: "待整改", RECHECK: "待复查", CLOSED: "已关闭", VOIDED: "已作废" })[status]
  || status
  || "-";
const issueStatusTone = (status) =>
  ({ PENDING: "warning", RECHECK: "warning", CLOSED: "success", VOIDED: "danger" })[status]
  || "warning";
const formatTime = (value) =>
  value ? String(value).replace("T", " ").slice(0, 16) : "-";
const resultRecords = (data) => data?.records || data?.items || [];

export default function WeeklyInspectionPanel({
  projectId,
  T,
  canManage,
  buttonStyle,
  fieldStyle,
  pill,
  onOpenIssue,
  businessTarget,
}) {
  const [inspections, setInspections] = useState([]);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState(null);
  const [pageNo, setPageNo] = useState(1);
  const [status, setStatus] = useState("ALL");
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorText, setErrorText] = useState("");
  const [weekPickerOpen, setWeekPickerOpen] = useState(false);
  const [selectedWeek, setSelectedWeek] = useState(mondayOfWeek());
  const [detail, setDetail] = useState(null);
  const [editor, setEditor] = useState(null);
  const [members, setMembers] = useState([]);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState("");
  const [reminderOpen, setReminderOpen] = useState(false);
  const [reminderLoading, setReminderLoading] = useState(false);
  const [reminderSaving, setReminderSaving] = useState(false);
  const [reminderError, setReminderError] = useState("");
  const [reminderSetting, setReminderSetting] = useState(null);
  const [reminderAssignees, setReminderAssignees] = useState([]);
  const requestRef = useRef(0);
  const openRequestRef = useRef(0);
  const openedBusinessTargetRef = useRef("");
  const busyRef = useRef(false);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const loadPage = async (options = {}) => {
    const targetProjectId = options.projectId ?? projectId;
    if (!targetProjectId) return;
    const targetPage = options.pageNo ?? pageNo;
    const targetStatus = options.status ?? status;
    const targetKeyword = options.keyword ?? appliedKeyword;
    const requestId = ++requestRef.current;
    setLoading(true);
    setErrorText("");
    try {
      const [pageRes, summaryRes] = await Promise.all([
        getWeeklyInspectionPage(targetProjectId, {
          pageNo: targetPage,
          pageSize: PAGE_SIZE,
          status: targetStatus === "ALL" ? undefined : targetStatus,
          keyword: targetKeyword || undefined,
        }),
        getWeeklyInspectionSummary(targetProjectId),
      ]);
      if (requestId !== requestRef.current) return;
      if (pageRes.code !== 200) {
        throw new Error(pageRes.message || "周检记录加载失败");
      }
      if (summaryRes.code !== 200) {
        throw new Error(summaryRes.message || "周检摘要加载失败");
      }
      const pageData = pageRes.data || {};
      const nextTotal = Number(pageData.total || 0);
      const nextTotalPages = Math.max(1, Math.ceil(nextTotal / PAGE_SIZE));
      if (nextTotal > 0 && targetPage > nextTotalPages) {
        setPageNo(nextTotalPages);
        return;
      }
      setInspections(resultRecords(pageData));
      setTotal(nextTotal);
      setSummary(summaryRes.data || null);
    } catch (error) {
      if (requestId !== requestRef.current) return;
      setInspections([]);
      setTotal(0);
      setSummary(null);
      setErrorText(error.message || "周检记录加载失败");
    } finally {
      if (requestId === requestRef.current) setLoading(false);
    }
  };

  useEffect(() => {
    requestRef.current += 1;
    openRequestRef.current += 1;
    setPageNo(1);
    setStatus("ALL");
    setKeyword("");
    setAppliedKeyword("");
    setSelectedWeek(mondayOfWeek());
    setWeekPickerOpen(false);
    setDetail(null);
    setEditor(null);
    setDirty(false);
    setMembers([]);
    setReminderOpen(false);
    setReminderSetting(null);
    setReminderAssignees([]);
    setReminderError("");
    openedBusinessTargetRef.current = "";
    busyRef.current = false;
    setBusy("");
  }, [projectId]);

  useEffect(() => {
    loadPage();
  }, [projectId, pageNo, status, appliedKeyword]);

  useEffect(() => {
    if (!canManage && status === "DRAFT") {
      setPageNo(1);
      setStatus("ALL");
    }
  }, [canManage, status]);

  useEffect(() => {
    if (!dirty || !editor) return undefined;
    const warn = (event) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty, editor]);

  const beginBusy = (action) => {
    if (busyRef.current) return false;
    busyRef.current = true;
    setBusy(action);
    return true;
  };
  const endBusy = () => {
    busyRef.current = false;
    setBusy("");
  };

  const loadMembers = async () => {
    const res = await getQualityAssignees(projectId);
    if (res.code !== 200) throw new Error(res.message || "整改负责人加载失败");
    const candidates = res.data || [];
    setMembers(candidates);
    return candidates;
  };

  const fetchInspection = async (id) => {
    const res = await getWeeklyInspection(id);
    if (res.code !== 200) throw new Error(res.message || "周检详情加载失败");
    return res.data;
  };

  const openWeekInspection = async (weekStart, closePicker = false) => {
    if (!projectId) throw new Error("请先选择项目");
    if (!weekStart) throw new Error("请选择周检周次");
    if (isFutureWeek(weekStart)) throw new Error("不能创建未来周次的质量周检");
    if (!beginBusy("draft")) return;
    const requestId = ++openRequestRef.current;
    try {
      await loadMembers();
      const res = await createOrResumeWeeklyInspectionDraft({ projectId, weekStart });
      if (res.code !== 200) throw new Error(res.message || "草稿创建失败");
      const data = res.data?.draftItems
        ? res.data
        : await fetchInspection(res.data?.id || res.data);
      if (requestId !== openRequestRef.current) return;
      if (closePicker) setWeekPickerOpen(false);
      if (data.status === "SUBMITTED") {
        setDetail(data);
        setEditor(null);
        setDirty(false);
        return;
      }
      setEditor(inspectionToEditor(data));
      setDirty(false);
    } finally {
      if (requestId === openRequestRef.current) endBusy();
    }
  };

  const openInspection = async (inspection) => {
    if (!beginBusy("detail")) return;
    try {
      const data = await fetchInspection(inspection.id);
      if (data.status === "DRAFT" && canManage) {
        await loadMembers();
        setEditor(inspectionToEditor(data));
        setDirty(false);
      } else {
        setDetail(data);
      }
    } catch (error) {
      alert(error.message || "周检详情加载失败");
    } finally {
      endBusy();
    }
  };

  const startDraft = async () => {
    const weekStart = mondayOfWeek(selectedWeek);
    try {
      await openWeekInspection(weekStart, true);
    } catch (error) {
      alert(error.message || "草稿创建失败");
    }
  };

  useEffect(() => {
    const routeCode = String(businessTarget?.routeCode || "").toUpperCase();
    const weekStart = String(businessTarget?.weekStart || "");
    const targetProjectMatches = Number(businessTarget?.projectId || 0) === Number(projectId || 0);
    const targetKey = routeCode === "QUALITY_WEEKLY_INSPECTION_WEEK" && weekStart && targetProjectMatches
      ? `${projectId || ""}:${weekStart}:${businessTarget?.openedAt || ""}`
      : "";
    if (!targetKey || !canManage || openedBusinessTargetRef.current === targetKey) return;
    openedBusinessTargetRef.current = targetKey;
    setSelectedWeek(weekStart);
    openWeekInspection(weekStart).catch((error) => alert(error.message || "周检记录打开失败"));
  }, [businessTarget?.openedAt, businessTarget?.projectId, businessTarget?.routeCode, businessTarget?.weekStart, canManage, projectId]);

  const openReminderSetting = async () => {
    if (!projectId || reminderLoading) return;
    setReminderOpen(true);
    setReminderLoading(true);
    setReminderError("");
    try {
      const [settingRes, assigneeRes] = await Promise.all([
        getWeeklyInspectionReminderSetting(projectId),
        getWeeklyInspectionReminderAssignees(projectId),
      ]);
      if (settingRes.code !== 200) throw new Error(settingRes.message || "提醒设置加载失败");
      if (assigneeRes.code !== 200) throw new Error(assigneeRes.message || "责任人加载失败");
      const value = settingRes.data || {};
      setReminderSetting({
        ...value,
        enabled: Boolean(value.enabled),
        dayOfWeek: Number(value.dayOfWeek || 7),
        triggerTime: String(value.triggerTime || "18:00").slice(0, 5),
        responsibleUserId: value.responsibleUserId ? String(value.responsibleUserId) : "",
        version: Number(value.version || 0),
      });
      setReminderAssignees(assigneeRes.data || []);
    } catch (error) {
      setReminderError(error.message || "提醒设置加载失败");
    } finally {
      setReminderLoading(false);
    }
  };

  const saveReminderSetting = async () => {
    if (!reminderSetting || reminderSaving) return;
    if (reminderSetting.enabled && !reminderSetting.responsibleUserId) {
      setReminderError("启用提醒前请选择一名质量周检主责任人");
      return;
    }
    setReminderSaving(true);
    setReminderError("");
    try {
      const res = await updateWeeklyInspectionReminderSetting(projectId, {
        enabled: Boolean(reminderSetting.enabled),
        dayOfWeek: Number(reminderSetting.dayOfWeek),
        triggerTime: reminderSetting.triggerTime,
        responsibleUserId: reminderSetting.responsibleUserId
          ? Number(reminderSetting.responsibleUserId)
          : null,
        expectedVersion: Number(reminderSetting.version || 0),
      });
      if (res.code !== 200) throw new Error(res.message || "提醒设置保存失败");
      const saved = res.data || {};
      setReminderSetting((current) => ({
        ...current,
        ...saved,
        enabled: Boolean(saved.enabled),
        triggerTime: String(saved.triggerTime || current.triggerTime).slice(0, 5),
        responsibleUserId: saved.responsibleUserId ? String(saved.responsibleUserId) : "",
        version: Number(saved.version || 0),
      }));
      alert("质量周检提醒设置已保存");
    } catch (error) {
      if (error?.response?.status === 409) {
        setReminderError("提醒设置已被其他管理员更新，请关闭后重新打开再保存");
      } else {
        setReminderError(error.message || "提醒设置保存失败");
      }
    } finally {
      setReminderSaving(false);
    }
  };

  const closeOverlay = () => {
    if (editor && dirty && !window.confirm("当前有未保存修改，确认关闭编辑器？")) return;
    setEditor(null);
    setDetail(null);
    setDirty(false);
  };

  const updateEditor = (patch) => {
    setEditor((current) => ({ ...current, ...patch }));
    setDirty(true);
  };

  const updateItem = (index, patch) => {
    setEditor((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...patch } : item,
      ),
    }));
    setDirty(true);
  };

  const uploadPendingFiles = async (files, businessType) => {
    const ids = [];
    try {
      for (const file of files || []) {
        const res = await uploadFile({
          file,
          projectId,
          fileType: "质量照片",
          businessType,
        });
        if (res.code !== 200) throw new Error(res.message || `${file.name} 上传失败`);
        ids.push(Number(res.data.id));
      }
      return ids;
    } catch (error) {
      await Promise.allSettled(ids.map((id) => deleteFile(id)));
      throw error;
    }
  };

  const persistDraft = async () => {
    const uploadedIds = [];
    const uploadedItemIds = {};
    try {
      const overviewIds = await uploadPendingFiles(
        editor.newOverviewFiles,
        "QUALITY_WEEKLY_PENDING",
      );
      uploadedIds.push(...overviewIds);
      for (const item of editor.items) {
        const ids = await uploadPendingFiles(
          item.newBeforeFiles,
          "QUALITY_WEEKLY_ITEM_PENDING",
        );
        uploadedItemIds[item.itemKey] = ids;
        uploadedIds.push(...ids);
      }
      const res = await saveWeeklyInspectionDraft(
        editor.id,
        buildWeeklyDraftPayload(editor, overviewIds, uploadedItemIds),
      );
      if (res.code !== 200) throw new Error(res.message || "草稿保存失败");
      const saved = res.data?.draftItems ? res.data : await fetchInspection(editor.id);
      const nextEditor = inspectionToEditor(saved);
      setEditor(nextEditor);
      setDirty(false);
      return nextEditor;
    } catch (error) {
      await Promise.allSettled(uploadedIds.map((id) => deleteFile(id)));
      if (error?.response?.status === 409) {
        alert("共享草稿已被其他成员更新。本地未保存内容仍保留，系统没有覆盖服务端版本；请核对后重新加载最新草稿。");
        error.weeklyConflictNotified = true;
      }
      throw error;
    }
  };

  const saveDraft = async () => {
    if (!beginBusy("save")) return;
    try {
      await persistDraft();
      await loadPage();
      alert("共享草稿已保存");
    } catch (error) {
      if (error?.response?.status !== 409) alert(error.message || "草稿保存失败");
    } finally {
      endBusy();
    }
  };

  const submitDraft = async () => {
    const validation = validateWeeklySubmission(editor);
    if (validation.length) return alert(validation.slice(0, 8).join("\n"));
    if (!window.confirm(`确认整批提交本周检${editor.items.length ? `及 ${editor.items.length} 个质量问题` : "（无问题）"}？提交后周检内容不可修改。`)) return;
    if (!beginBusy("submit")) return;
    try {
      const saved = await persistDraft();
      const res = await submitWeeklyInspection(saved.id, saved.version);
      if (res.code !== 200) throw new Error(res.message || "周检提交失败");
      setEditor(null);
      setDirty(false);
      await loadPage({ pageNo: 1 });
      setPageNo(1);
      alert("质量周检已整批提交");
    } catch (error) {
      if (error?.response?.status === 409) {
        if (!error.weeklyConflictNotified) {
          alert("提交前共享草稿已发生变化，系统未重复创建问题；请重新加载后核对最新内容。");
        }
      } else {
        alert(error.message || "周检提交失败");
      }
    } finally {
      endBusy();
    }
  };

  const reloadDraft = async () => {
    if (dirty && !window.confirm("重新加载会丢弃当前未保存内容，确认继续？")) return;
    if (!beginBusy("reload")) return;
    try {
      const data = await fetchInspection(editor.id);
      if (data.status === "SUBMITTED") {
        setEditor(null);
        setDetail(data);
        setDirty(false);
        alert("该周检已由其他成员提交，现已切换为只读详情。");
        return;
      }
      setEditor(inspectionToEditor(data));
      setDirty(false);
    } catch (error) {
      alert(error.message || "草稿重新加载失败");
    } finally {
      endBusy();
    }
  };

  const discardDraft = async () => {
    if (!window.confirm("确认放弃这份共享草稿？草稿内容和草稿照片将被清理，之后可重新创建该周周检。")) return;
    if (!beginBusy("discard")) return;
    try {
      const res = await discardWeeklyInspectionDraft(editor.id, editor.version);
      if (res.code !== 200) throw new Error(res.message || "草稿放弃失败");
      setEditor(null);
      setDirty(false);
      await loadPage({ pageNo: 1 });
      setPageNo(1);
    } catch (error) {
      if (error?.response?.status === 409) {
        alert("共享草稿已被其他成员更新，当前版本不能放弃；请重新加载后再操作。");
      } else {
        alert(error.message || "草稿放弃失败");
      }
    } finally {
      endBusy();
    }
  };

  const addItem = () => {
    if (editor.items.length >= 50) return alert("每份周检最多添加 50 个问题");
    updateEditor({ items: [...editor.items, newWeeklyItem(editor.items.length + 1)] });
  };

  const removeItem = (index) => {
    if (!window.confirm(`确认移除第 ${index + 1} 个问题？`)) return;
    updateEditor({
      items: editor.items
        .filter((_, itemIndex) => itemIndex !== index)
        .map((item, itemIndex) => ({ ...item, itemOrder: itemIndex + 1 })),
    });
  };

  const copyPreviousSettings = (index) => {
    if (index < 1) return;
    const previous = editor.items[index - 1];
    updateItem(index, {
      severity: previous.severity,
      assigneeId: previous.assigneeId,
      deadline: previous.deadline,
    });
  };

  const selectOverviewFiles = (files) => {
    const selected = Array.from(files || []);
    if (editor.overviewPhotoFileIds.length + selected.length > 20) {
      return alert("周检现场照片最多 20 张");
    }
    updateEditor({ newOverviewFiles: selected });
  };

  const selectItemFiles = (index, files) => {
    const selected = Array.from(files || []);
    if (editor.items[index].beforePhotoFileIds.length + selected.length > 20) {
      return alert("每个问题最多上传 20 张整改前照片");
    }
    updateItem(index, { newBeforeFiles: selected });
  };

  return (
    <div className="quality-weekly-panel" style={{ height: "100%", display: "flex", flexDirection: "column", minHeight: 0 }}>
      <header className="quality-panel-header" style={{ borderColor: T.borderColor, background: T.cardBg }}>
        <div className="quality-panel-copy">
          <span className="quality-panel-eyebrow" style={{ color: T.accent }}>项目周检台账</span>
          <div className="quality-panel-title-line">
            <h3 style={{ color: T.textPrimary }}>周检记录</h3>
            {pill(
              summary?.hasInspection ? `本周 · ${statusText(summary.status)}` : "本周 · 尚未创建",
              summary?.status === "DRAFT" ? "warning" : summary?.status === "SUBMITTED" ? "success" : "normal",
            )}
            {summary?.status === "DRAFT" && pill(`${summary.draftItemCount || 0} 个草稿问题`, "warning")}
            {summary?.lateSubmission && pill("往期补录", "warning")}
          </div>
          <p style={{ color: T.textMuted }}>每个项目每周一份记录，共享草稿可跨 Web 与小程序继续编辑。</p>
        </div>
        <div className="quality-panel-actions">
            {canManage && <button type="button" disabled={!projectId || reminderLoading} onClick={openReminderSetting} style={buttonStyle("secondary")}>提醒设置</button>}
            <select aria-label="周检状态" value={status} onChange={(event) => { setPageNo(1); setStatus(event.target.value); }} style={{ ...fieldStyle, width: 110 }}>
              <option value="ALL">全部状态</option>
              {canManage && <option value="DRAFT">共享草稿</option>}
              <option value="SUBMITTED">已提交</option>
            </select>
            <input aria-label="搜索周检记录" value={keyword} onChange={(event) => setKeyword(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") { setPageNo(1); setAppliedKeyword(keyword.trim()); } }} placeholder="搜索周检编号或结论" style={{ ...fieldStyle, width: 210 }} />
            <button type="button" disabled={loading} onClick={() => { setPageNo(1); setAppliedKeyword(keyword.trim()); }} style={buttonStyle("secondary")}>查询</button>
            <button type="button" disabled={!projectId || !canManage || Boolean(busy)} onClick={() => setWeekPickerOpen(true)} style={buttonStyle()}>创建/继续周检</button>
        </div>
      </header>

      <div className="quality-table-region" style={{ flex: 1, minHeight: 0, overflow: "auto" }}>
        {loading ? (
          <StateText T={T} loading>周检记录加载中...</StateText>
        ) : errorText ? (
          <StateText T={T} danger>{errorText}<br /><button type="button" onClick={() => loadPage()} style={{ ...buttonStyle("secondary"), marginTop: 10 }}>重新加载</button></StateText>
        ) : (
          <>
            <HeaderRow T={T} />
            {inspections.map((inspection) => (
              <div className="quality-table-row" key={inspection.id} style={{ display: "grid", gridTemplateColumns: "150px 170px 105px 100px 1fr 100px", minWidth: 920, alignItems: "center", gap: 10, padding: "10px 14px", borderBottom: `1px solid ${T.borderColor}`, color: T.textSecondary, fontSize: 12 }}>
                <span title={inspection.inspectionNo}>{inspection.inspectionNo || `草稿 #${inspection.id}`}</span>
                <span>{inspection.weekStart} 至 {inspection.weekEnd || addLocalDays(inspection.weekStart, 6)}</span>
                <span style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                  {pill(statusText(inspection.status), inspection.status === "DRAFT" ? "warning" : "success")}
                  {inspection.lateSubmission && pill("补录", "warning")}
                </span>
                <span>{inspection.status === "DRAFT" ? "编辑中" : `${inspection.submittedIssueCount || 0} 个问题`}</span>
                <span>{inspection.lastEditedByName || inspection.submittedByName || inspection.createdByName || "-"} · {formatTime(inspection.updateTime || inspection.submittedTime)}</span>
                <button type="button" disabled={Boolean(busy)} onClick={() => openInspection(inspection)} style={buttonStyle("secondary")}>{inspection.status === "DRAFT" && canManage ? "继续编辑" : "查看"}</button>
              </div>
            ))}
            {!inspections.length && <StateText T={T}>当前筛选下暂无质量周检</StateText>}
          </>
        )}
      </div>

      {total > 0 && (
        <div className="quality-pagination" style={{ display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 8, padding: "9px 12px", borderTop: `1px solid ${T.borderColor}`, color: T.textMuted, fontSize: 11 }}>
          <span>共 {total} 条，第 {pageNo}/{totalPages} 页</span>
          <button type="button" disabled={pageNo <= 1 || loading} onClick={() => setPageNo((value) => Math.max(1, value - 1))} style={buttonStyle("secondary")}>上一页</button>
          <button type="button" disabled={pageNo >= totalPages || loading} onClick={() => setPageNo((value) => Math.min(totalPages, value + 1))} style={buttonStyle("secondary")}>下一页</button>
        </div>
      )}

      {weekPickerOpen && (
        <Overlay T={T} title="选择质量周检周次" onClose={() => { if (!busy) setWeekPickerOpen(false); }} width={500}>
          <label style={labelStyle(T)}>选择当前周或往期任意日期
            <input type="date" max={formatLocalDate(new Date())} value={selectedWeek} onChange={(event) => setSelectedWeek(mondayOfWeek(event.target.value))} style={fieldStyle} />
          </label>
          <div style={{ padding: 10, borderRadius: 7, background: T.surface2, color: T.textSecondary, fontSize: 12 }}>
            周次：{mondayOfWeek(selectedWeek)} 至 {addLocalDays(mondayOfWeek(selectedWeek), 6)}。同一项目同一周只保留一份共享草稿或正式周检。
          </div>
          <Actions buttonStyle={buttonStyle} busy={Boolean(busy)} onCancel={() => setWeekPickerOpen(false)} onConfirm={startDraft} confirmText="创建/恢复草稿" />
        </Overlay>
      )}

      {reminderOpen && (
        <Overlay T={T} title="质量周检未提交提醒" onClose={() => { if (!reminderSaving) setReminderOpen(false); }} width={620}>
          {reminderLoading ? (
            <StateText T={T} loading>提醒设置加载中...</StateText>
          ) : !reminderSetting ? (
            <StateText T={T} danger>{reminderError || "提醒设置加载失败"}<br /><button type="button" onClick={openReminderSetting} style={{ ...buttonStyle("secondary"), marginTop: 10 }}>重新加载</button></StateText>
          ) : (
            <>
              {reminderError && <div style={{ marginBottom: 12, padding: 10, borderRadius: 7, border: `1px solid ${T.danger}`, color: T.danger, background: `${T.danger}12`, fontSize: 12 }}>{reminderError}</div>}
              <div style={{ padding: 12, borderRadius: 8, background: T.surface2, color: T.textSecondary, fontSize: 12, lineHeight: 1.8 }}>
                仅保存共享草稿仍视为未提交；每个自然周只提醒一次。首次启用或重新启用后，从下一个符合规则的周次开始生效，不追补历史周次。
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "150px 150px 1fr", gap: 12, marginTop: 14 }}>
                <label style={labelStyle(T)}>提醒星期
                  <select value={reminderSetting.dayOfWeek} onChange={(event) => setReminderSetting({ ...reminderSetting, dayOfWeek: Number(event.target.value) })} style={fieldStyle}>
                    {[1, 2, 3, 4, 5, 6, 7].map((day) => <option key={day} value={day}>星期{["一", "二", "三", "四", "五", "六", "日"][day - 1]}</option>)}
                  </select>
                </label>
                <label style={labelStyle(T)}>提醒时间
                  <input type="time" value={reminderSetting.triggerTime} onChange={(event) => setReminderSetting({ ...reminderSetting, triggerTime: event.target.value })} style={fieldStyle} />
                </label>
                <label style={labelStyle(T)}>主责任人
                  <select value={reminderSetting.responsibleUserId} onChange={(event) => setReminderSetting({ ...reminderSetting, responsibleUserId: event.target.value })} style={fieldStyle}>
                    <option value="">请选择具备 quality.manage 的成员</option>
                    {reminderAssignees.map((member) => <option key={member.userId || member.id} value={member.userId || member.id}>{member.displayName || member.realName || member.username || member.userName}</option>)}
                  </select>
                </label>
              </div>
              <label style={{ ...labelStyle(T), display: "flex", flexDirection: "row", alignItems: "center", gap: 8, marginTop: 14 }}>
                <input type="checkbox" checked={reminderSetting.enabled} onChange={(event) => setReminderSetting({ ...reminderSetting, enabled: event.target.checked })} />
                启用质量周检未提交站内提醒
              </label>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 12 }}>
                <div style={{ padding: 10, borderRadius: 7, background: T.surface2, color: T.textMuted, fontSize: 11 }}>规则生效时间<br /><strong style={{ color: T.textPrimary }}>{formatTime(reminderSetting.reminderEffectiveTime)}</strong></div>
                <div style={{ padding: 10, borderRadius: 7, background: T.surface2, color: T.textMuted, fontSize: 11 }}>下一次提醒<br /><strong style={{ color: T.textPrimary }}>{formatTime(reminderSetting.nextReminderTime)}</strong></div>
              </div>
              <Actions buttonStyle={buttonStyle} busy={reminderSaving} onCancel={() => setReminderOpen(false)} onConfirm={saveReminderSetting} confirmText={reminderSaving ? "保存中..." : "保存提醒设置"} />
            </>
          )}
        </Overlay>
      )}

      {editor && (
        <Overlay T={T} title={`编辑质量周检 · ${editor.weekStart} 至 ${editor.weekEnd}`} onClose={() => { if (!busy) closeOverlay(); }} width={1120}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 12, padding: 10, marginBottom: 12, borderRadius: 7, background: T.surface2, color: T.textSecondary, fontSize: 11 }}>
            <span>共享草稿 · 版本 {editor.version}{dirty ? " · 有未保存修改" : " · 已同步"}</span>
            <span>最近编辑：{editor.lastEditedByName || "-"} · {formatTime(editor.updateTime)}</span>
          </div>
          <fieldset disabled={Boolean(busy)} style={{ minWidth: 0, margin: 0, padding: 0, border: 0 }}>
          <div style={{ display: "grid", gridTemplateColumns: "220px 1fr", gap: 12 }}>
            <label style={labelStyle(T)}>检查日期 *
              <input type="date" min={editor.weekStart} max={[editor.weekEnd, formatLocalDate(new Date())].sort()[0]} value={editor.inspectionDate} onChange={(event) => updateEditor({ inspectionDate: event.target.value })} style={fieldStyle} />
            </label>
            <label style={labelStyle(T)}>检查结论（无问题时必填）
              <textarea maxLength={1000} value={editor.conclusion} onChange={(event) => updateEditor({ conclusion: event.target.value })} style={{ ...fieldStyle, minHeight: 70 }} placeholder="记录本周检查范围、总体情况和结论" />
            </label>
          </div>
          <PhotoEditor T={T} title="周检现场照片" existingIds={editor.overviewPhotoFileIds} newFiles={editor.newOverviewFiles} fieldStyle={fieldStyle} onRemoveExisting={(fileId) => updateEditor({ overviewPhotoFileIds: editor.overviewPhotoFileIds.filter((id) => id !== fileId) })} onSelect={selectOverviewFiles} />

          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", margin: "18px 0 9px" }}>
            <strong style={{ color: T.textPrimary }}>本次检查问题（{editor.items.length}/50）</strong>
            <button type="button" disabled={editor.items.length >= 50 || Boolean(busy)} onClick={addItem} style={buttonStyle()}>添加问题</button>
          </div>
          <div style={{ display: "grid", gap: 12 }}>
            {editor.items.map((item, index) => (
              <IssueEditor key={item.itemKey} T={T} index={index} item={item} members={members} fieldStyle={fieldStyle} buttonStyle={buttonStyle} onChange={(patch) => updateItem(index, patch)} onCopy={() => copyPreviousSettings(index)} onRemove={() => removeItem(index)} onSelectFiles={(files) => selectItemFiles(index, files)} />
            ))}
            {!editor.items.length && <div style={{ padding: 18, textAlign: "center", color: T.textMuted, border: `1px dashed ${T.borderColor}`, borderRadius: 8, fontSize: 12 }}>本周无问题时可直接填写检查结论并上传现场照片；发现问题请逐项添加。</div>}
          </div>
          </fieldset>

          <div style={{ position: "sticky", bottom: -18, zIndex: 2, display: "flex", justifyContent: "space-between", gap: 8, margin: "20px -18px -18px", padding: 14, borderTop: `1px solid ${T.borderColor}`, background: T.modalBg }}>
            <button type="button" disabled={Boolean(busy)} onClick={discardDraft} style={buttonStyle("danger")}>{busy === "discard" ? "放弃中..." : "放弃草稿"}</button>
            <div style={{ display: "flex", gap: 8 }}>
              <button type="button" disabled={Boolean(busy)} onClick={reloadDraft} style={buttonStyle("secondary")}>重新加载</button>
              <button type="button" disabled={Boolean(busy)} onClick={saveDraft} style={buttonStyle("secondary")}>{busy === "save" ? "保存中..." : "保存草稿"}</button>
              <button type="button" disabled={Boolean(busy)} onClick={submitDraft} style={buttonStyle()}>{busy === "submit" ? "提交中..." : `整批提交${editor.items.length ? `（${editor.items.length} 个问题）` : "（无问题）"}`}</button>
            </div>
          </div>
        </Overlay>
      )}

      {detail && (
        <Overlay T={T} title={`质量周检详情 · ${detail.inspectionNo || detail.id}`} onClose={closeOverlay} width={980}>
          <InspectionDetail
            T={T}
            detail={detail}
            pill={pill}
            buttonStyle={buttonStyle}
            onOpenIssue={(issue) => {
              setDetail(null);
              onOpenIssue(issue);
            }}
          />
        </Overlay>
      )}
    </div>
  );
}

function HeaderRow({ T }) {
  return <div className="quality-table-head" style={{ display: "grid", gridTemplateColumns: "150px 170px 105px 100px 1fr 100px", minWidth: 920, gap: 10, padding: "10px 14px", position: "sticky", top: 0, zIndex: 1, background: T.surface2, borderBottom: `1px solid ${T.borderColor}`, color: T.textMuted, fontSize: 11 }}><span>周检编号</span><span>周次</span><span>状态</span><span>问题数</span><span>最近操作</span><span>操作</span></div>;
}

function IssueEditor({ T, index, item, members, fieldStyle, buttonStyle, onChange, onCopy, onRemove, onSelectFiles }) {
  return (
    <section style={{ padding: 14, border: `1px solid ${T.borderColor}`, borderRadius: 8, background: T.cardBg }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <strong style={{ color: T.textPrimary }}>问题 {index + 1}</strong>
        <div style={{ display: "flex", gap: 7 }}>
          {index > 0 && <button type="button" onClick={onCopy} style={buttonStyle("secondary")}>复制上一题设置</button>}
          <button type="button" onClick={onRemove} style={buttonStyle("danger")}>移除</button>
        </div>
      </div>
      <label style={labelStyle(T)}>问题标题 *
        <input maxLength={200} value={item.title} onChange={(event) => onChange({ title: event.target.value })} style={fieldStyle} />
      </label>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 150px 1fr 180px", gap: 10 }}>
        <label style={labelStyle(T)}>问题位置
          <input maxLength={200} value={item.location || ""} onChange={(event) => onChange({ location: event.target.value })} style={fieldStyle} />
        </label>
        <label style={labelStyle(T)}>严重等级 *
          <select value={item.severity} onChange={(event) => onChange({ severity: event.target.value })} style={fieldStyle}><option value="NORMAL">一般</option><option value="WARNING">重要</option><option value="DANGER">严重</option></select>
        </label>
        <label style={labelStyle(T)}>整改负责人 *
          <select value={item.assigneeId} onChange={(event) => onChange({ assigneeId: event.target.value })} style={fieldStyle}><option value="">请选择</option>{members.map((member) => <option key={member.userId} value={member.userId}>{member.displayName}</option>)}</select>
        </label>
        <label style={labelStyle(T)}>闭环期限 *
          <input type="date" min={formatLocalDate(new Date())} value={item.deadline || ""} onChange={(event) => onChange({ deadline: event.target.value })} style={fieldStyle} />
        </label>
      </div>
      <label style={labelStyle(T)}>问题描述
        <textarea maxLength={1000} value={item.description || ""} onChange={(event) => onChange({ description: event.target.value })} style={{ ...fieldStyle, minHeight: 65 }} />
      </label>
      <PhotoEditor T={T} title="整改前照片 *（1–20 张）" existingIds={item.beforePhotoFileIds} newFiles={item.newBeforeFiles} fieldStyle={fieldStyle} onRemoveExisting={(fileId) => onChange({ beforePhotoFileIds: item.beforePhotoFileIds.filter((id) => id !== fileId) })} onSelect={onSelectFiles} />
    </section>
  );
}

function PhotoEditor({ T, title, existingIds, newFiles, fieldStyle, onRemoveExisting, onSelect }) {
  const [showSavedPhotos, setShowSavedPhotos] = useState(false);
  return (
    <div style={{ padding: 10, marginBottom: 10, borderRadius: 7, background: T.surface2 }}>
      <strong style={{ color: T.textPrimary, fontSize: 12 }}>{title}</strong>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 7 }}>
        {existingIds.map((fileId) => <span key={fileId} style={{ display: "inline-flex", alignItems: "center", gap: 5, padding: "4px 7px", borderRadius: 5, border: `1px solid ${T.borderColor}`, color: T.textSecondary, fontSize: 10 }}>已保存照片 #{fileId}<button type="button" onClick={() => onRemoveExisting(fileId)} style={{ border: 0, background: "transparent", color: T.danger, cursor: "pointer" }}>×</button></span>)}
        {newFiles.map((file) => <span key={`${file.name}-${file.lastModified}`} style={{ padding: "4px 7px", borderRadius: 5, background: T.activeItemBg, color: T.accent, fontSize: 10 }}>待上传：{file.name}</span>)}
        {!existingIds.length && !newFiles.length && <span style={{ color: T.textMuted, fontSize: 10 }}>暂未选择照片</span>}
      </div>
      {existingIds.length > 0 && (
        <>
          <button
            type="button"
            onClick={() => setShowSavedPhotos((value) => !value)}
            style={{
              marginTop: 7,
              padding: 0,
              border: 0,
              background: "transparent",
              color: T.accent,
              cursor: "pointer",
              fontSize: 10,
            }}
          >
            {showSavedPhotos ? "收起已保存照片" : `预览已保存照片（${existingIds.length}）`}
          </button>
          {showSavedPhotos && <RemotePhotoGrid T={T} fileIds={existingIds} />}
        </>
      )}
      <input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={(event) => onSelect(event.target.files)} style={{ ...fieldStyle, marginTop: 8 }} />
    </div>
  );
}

function InspectionDetail({ T, detail, pill, buttonStyle, onOpenIssue }) {
  const issues = detail.issues || [];
  return (
    <>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(5,minmax(0,1fr))", gap: 8 }}>
        {[["周次", `${detail.weekStart} 至 ${detail.weekEnd}`], ["检查日期", detail.inspectionDate], ["提交人", detail.submittedByName], ["提交时间", formatTime(detail.submittedTime)], ["问题总数", detail.submittedIssueCount ?? issues.length], ["待整改", detail.pendingCount || 0], ["待复查", detail.recheckCount || 0], ["已关闭", detail.closedCount || 0], ["已作废", detail.voidedCount || 0]].map(([label, value]) => <div key={label} style={{ padding: 10, borderRadius: 7, background: T.surface2, color: T.textSecondary, fontSize: 11 }}><span style={{ color: T.textMuted }}>{label}</span><strong style={{ display: "block", marginTop: 4, color: T.textPrimary }}>{value ?? "-"}</strong></div>)}
      </div>
      {detail.lateSubmission && <div style={{ marginTop: 10 }}>{pill("往期补录", "warning")}</div>}
      <section style={{ marginTop: 14 }}><strong style={{ color: T.textPrimary }}>检查结论</strong><div style={{ marginTop: 7, padding: 10, borderRadius: 7, background: T.surface2, color: T.textSecondary, whiteSpace: "pre-wrap", fontSize: 12 }}>{detail.conclusion || "未填写"}</div></section>
      <section style={{ marginTop: 14 }}><strong style={{ color: T.textPrimary }}>周检现场照片</strong><RemotePhotoGrid T={T} fileIds={detail.overviewPhotoFileIds || []} /></section>
      <section style={{ marginTop: 16 }}><strong style={{ color: T.textPrimary }}>本次问题（{issues.length}）</strong><div style={{ marginTop: 8, display: "grid", gap: 7 }}>{issues.map((issue, index) => <div key={issue.id} style={{ display: "grid", gridTemplateColumns: "42px 1fr 110px 90px 80px 80px", gap: 8, alignItems: "center", padding: 10, borderRadius: 7, border: `1px solid ${T.borderColor}`, color: T.textSecondary, fontSize: 11 }}><span>#{index + 1}</span><span><strong style={{ display: "block", color: T.textPrimary }}>{issue.title}</strong>{issue.location || "-"}</span><span>{issue.assigneeName || "待重新分配"}</span><span>{issue.deadline || "-"}</span><span>{pill(issueStatusText(issue.status), issueStatusTone(issue.status))}</span><button type="button" onClick={() => onOpenIssue(issue)} style={buttonStyle("secondary")}>问题详情</button></div>)}{!issues.length && <div style={{ padding: 14, color: T.textMuted, fontSize: 11 }}>本周检查无质量问题</div>}</div></section>
    </>
  );
}

function RemotePhotoGrid({ T, fileIds }) {
  const [files, setFiles] = useState({});
  useEffect(() => {
    let active = true;
    const urls = [];
    setFiles({});
    Promise.all((fileIds || []).map(async (id) => {
      try {
        const blob = await downloadFile(id);
        const url = URL.createObjectURL(blob);
        urls.push(url);
        return [id, { url, ready: true }];
      } catch (error) {
        return [id, { ready: false, error: error.message || "读取失败" }];
      }
    })).then((entries) => { if (active) setFiles(Object.fromEntries(entries)); });
    return () => { active = false; urls.forEach((url) => URL.revokeObjectURL(url)); };
  }, [fileIds.join(",")]);
  if (!fileIds.length) return <div style={{ marginTop: 7, color: T.textMuted, fontSize: 11 }}>未上传现场照片</div>;
  return <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 8 }}>{fileIds.map((id, index) => { const file = files[id]; return <button key={id} type="button" disabled={!file?.ready} onClick={() => file?.ready && window.open(file.url, "_blank", "noopener,noreferrer")} title={file?.error || `查看现场照片 ${index + 1}`} style={{ width: 112, height: 82, padding: 0, borderRadius: 7, overflow: "hidden", border: `1px solid ${T.borderColor}`, background: T.surface2, color: T.textMuted, fontSize: 10 }}>{file?.ready ? <img src={file.url} alt={`现场照片 ${index + 1}`} style={{ width: "100%", height: "100%", objectFit: "cover" }} /> : file?.error ? "读取失败" : "读取中..."}</button>; })}</div>;
}

function Overlay({ T, title, onClose, width, children }) {
  return <div style={{ position: "fixed", inset: 0, zIndex: 1300, display: "flex", alignItems: "center", justifyContent: "center", padding: 20, background: "rgba(10,20,35,.62)" }} onClick={onClose}><div style={{ width, maxWidth: "96vw", maxHeight: "92vh", overflow: "auto", padding: 18, borderRadius: 9, border: `1px solid ${T.borderColor}`, background: T.modalBg, boxShadow: "0 18px 55px rgba(0,0,0,.32)" }} onClick={(event) => event.stopPropagation()}><div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16 }}><strong style={{ color: T.textPrimary, fontSize: 16 }}>{title}</strong><button type="button" onClick={onClose} style={{ border: 0, background: "transparent", color: T.textMuted, fontSize: 22, cursor: "pointer" }}>×</button></div>{children}</div></div>;
}

function Actions({ buttonStyle, busy, onCancel, onConfirm, confirmText }) {
  return <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 18 }}><button type="button" disabled={busy} onClick={onCancel} style={buttonStyle("secondary")}>取消</button><button type="button" disabled={busy} onClick={onConfirm} style={buttonStyle()}>{busy ? "处理中..." : confirmText}</button></div>;
}

function StateText({ T, danger = false, loading = false, children }) {
  if (loading) {
    return (
      <div className="quality-state quality-state--loading" role="status" style={{ color: T.textMuted }}>
        <span className="quality-state-spinner" style={{ borderColor: `${T.accent}33`, borderTopColor: T.accent }} />
        {children}
      </div>
    );
  }
  return (
    <div className="quality-state quality-state--empty" role={danger ? "alert" : "status"} style={{ color: danger ? T.danger : T.textMuted }}>
      <span className="quality-state-symbol" aria-hidden="true" style={{ color: danger ? T.danger : T.accent, background: danger ? `${T.danger}12` : T.activeItemBg }}>
        {danger ? "!" : "✓"}
      </span>
      <strong style={{ color: danger ? T.danger : T.textPrimary }}>{danger ? "内容加载失败" : children}</strong>
      {danger ? <span>{children}</span> : <span>创建周检或调整筛选条件后，记录会显示在这里。</span>}
    </div>
  );
}

function labelStyle(T) {
  return { display: "flex", flexDirection: "column", gap: 5, marginBottom: 10, color: T.textSecondary, fontSize: 12 };
}
