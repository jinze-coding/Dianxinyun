const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

function parseLocalDate(value) {
  const match = DATE_PATTERN.exec(String(value || ""));
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatLocalDate(value) {
  const date = value instanceof Date ? value : parseLocalDate(value);
  if (!date || Number.isNaN(date.getTime())) return "";
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function addLocalDays(value, days) {
  const date = value instanceof Date ? new Date(value) : parseLocalDate(value);
  if (!date) return "";
  date.setDate(date.getDate() + Number(days || 0));
  return formatLocalDate(date);
}

export function mondayOfWeek(value = new Date()) {
  const date = value instanceof Date ? new Date(value) : parseLocalDate(value);
  if (!date || Number.isNaN(date.getTime())) return "";
  date.setHours(0, 0, 0, 0);
  const day = date.getDay();
  date.setDate(date.getDate() - (day === 0 ? 6 : day - 1));
  return formatLocalDate(date);
}

export function isFutureWeek(weekStart, today = new Date()) {
  const start = parseLocalDate(mondayOfWeek(weekStart));
  const current = parseLocalDate(mondayOfWeek(today));
  return Boolean(start && current && start.getTime() > current.getTime());
}

export function defaultInspectionDate(weekStart, today = new Date()) {
  const currentWeek = mondayOfWeek(today);
  return mondayOfWeek(weekStart) === currentWeek
    ? formatLocalDate(today)
    : addLocalDays(mondayOfWeek(weekStart), 6);
}

export function newWeeklyItem(order = 1) {
  const randomPart =
    typeof crypto !== "undefined" && crypto.randomUUID
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  return {
    itemKey: `web-${randomPart}`,
    itemOrder: order,
    title: "",
    location: "",
    description: "",
    severity: "NORMAL",
    assigneeId: "",
    assigneeName: "",
    deadline: "",
    beforePhotoFileIds: [],
    newBeforeFiles: [],
  };
}

export function inspectionToEditor(detail) {
  const weekStart = mondayOfWeek(detail?.weekStart || new Date());
  return {
    id: detail?.id,
    inspectionNo: detail?.inspectionNo || "",
    projectId: detail?.projectId,
    weekStart,
    weekEnd: detail?.weekEnd || addLocalDays(weekStart, 6),
    inspectionDate:
      detail?.inspectionDate || defaultInspectionDate(weekStart),
    conclusion: detail?.conclusion || "",
    overviewPhotoFileIds: [...(detail?.overviewPhotoFileIds || [])],
    newOverviewFiles: [],
    items: (detail?.draftItems || []).map((item, index) => ({
      ...newWeeklyItem(index + 1),
      ...item,
      itemKey: item.itemKey || `draft-item-${item.id}`,
      itemOrder: index + 1,
      assigneeId: item.assigneeId ? String(item.assigneeId) : "",
      beforePhotoFileIds: [...(item.beforePhotoFileIds || [])],
      newBeforeFiles: [],
    })),
    status: detail?.status || "DRAFT",
    version: Number(detail?.version || 0),
    lastEditedByName: detail?.lastEditedByName || "",
    updateTime: detail?.updateTime || "",
  };
}

export function buildWeeklyDraftPayload(editor, uploadedOverviewIds = [], uploadedItemIds = {}) {
  return {
    expectedVersion: Number(editor.version || 0),
    inspectionDate: editor.inspectionDate || null,
    conclusion: String(editor.conclusion || "").trim(),
    overviewPhotoFileIds: [
      ...(editor.overviewPhotoFileIds || []),
      ...uploadedOverviewIds,
    ].map(Number),
    items: (editor.items || []).map((item, index) => ({
      itemKey: item.itemKey,
      itemOrder: index + 1,
      title: String(item.title || "").trim(),
      location: String(item.location || "").trim(),
      description: String(item.description || "").trim(),
      severity: item.severity || "NORMAL",
      assigneeId: item.assigneeId ? Number(item.assigneeId) : null,
      deadline: item.deadline || null,
      beforePhotoFileIds: [
        ...(item.beforePhotoFileIds || []),
        ...(uploadedItemIds[item.itemKey] || []),
      ].map(Number),
    })),
  };
}

export function validateWeeklySubmission(editor, today = new Date()) {
  const problems = [];
  const inspectionDate = parseLocalDate(editor?.inspectionDate);
  const weekStart = parseLocalDate(editor?.weekStart);
  const weekEnd = parseLocalDate(editor?.weekEnd || addLocalDays(editor?.weekStart, 6));
  if (!inspectionDate) {
    problems.push("请选择检查日期");
  } else if (
    (weekStart && inspectionDate < weekStart)
    || (weekEnd && inspectionDate > weekEnd)
  ) {
    problems.push("检查日期必须位于所选周次内");
  } else if (inspectionDate > parseLocalDate(formatLocalDate(today))) {
    problems.push("检查日期不能晚于今天");
  }

  const items = editor?.items || [];
  if (items.length > 50) problems.push("每份周检最多添加 50 个问题");
  if (!items.length) {
    if (!String(editor?.conclusion || "").trim()) {
      problems.push("无问题周检必须填写检查结论");
    }
    const overviewCount =
      (editor?.overviewPhotoFileIds?.length || 0)
      + (editor?.newOverviewFiles?.length || 0);
    if (overviewCount < 1 || overviewCount > 20) {
      problems.push("无问题周检必须上传 1–20 张现场照片");
    }
  }

  const todayText = formatLocalDate(today);
  items.forEach((item, index) => {
    const prefix = `第 ${index + 1} 个问题`;
    if (!String(item.title || "").trim()) problems.push(`${prefix}未填写标题`);
    if (!item.assigneeId) problems.push(`${prefix}未选择整改负责人`);
    if (!["NORMAL", "WARNING", "DANGER"].includes(item.severity)) {
      problems.push(`${prefix}严重等级无效`);
    }
    if (!item.deadline) {
      problems.push(`${prefix}未填写闭环期限`);
    } else if (item.deadline < todayText) {
      problems.push(`${prefix}闭环期限不能早于提交日`);
    }
    const photoCount =
      (item.beforePhotoFileIds?.length || 0)
      + (item.newBeforeFiles?.length || 0);
    if (photoCount < 1 || photoCount > 20) {
      problems.push(`${prefix}必须上传 1–20 张整改前照片`);
    }
  });
  return problems;
}
