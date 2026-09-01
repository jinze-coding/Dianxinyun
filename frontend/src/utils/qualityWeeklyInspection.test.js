import assert from "node:assert/strict";
import test from "node:test";

import {
  addLocalDays,
  buildWeeklyDraftPayload,
  defaultInspectionDate,
  inspectionToEditor,
  isFutureWeek,
  mondayOfWeek,
  validateWeeklySubmission,
} from "./qualityWeeklyInspection.js";

test("normalizes a selected date to Monday without UTC drift", () => {
  assert.equal(mondayOfWeek("2026-08-26"), "2026-08-24");
  assert.equal(addLocalDays("2026-08-24", 6), "2026-08-30");
  assert.equal(isFutureWeek("2026-08-31", new Date(2026, 7, 26)), true);
  assert.equal(defaultInspectionDate("2026-08-17", new Date(2026, 7, 26)), "2026-08-23");
});

test("maps server draft and emits the optimistic-lock save payload", () => {
  const editor = inspectionToEditor({
    id: 8,
    weekStart: "2026-08-24",
    version: 3,
    overviewPhotoFileIds: [11],
    draftItems: [{ id: 9, title: "临边防护", assigneeId: 2, beforePhotoFileIds: [12] }],
  });
  const payload = buildWeeklyDraftPayload(editor, [13], { [editor.items[0].itemKey]: [14] });
  assert.equal(payload.expectedVersion, 3);
  assert.deepEqual(payload.overviewPhotoFileIds, [11, 13]);
  assert.deepEqual(payload.items[0].beforePhotoFileIds, [12, 14]);
  assert.equal(payload.items[0].assigneeId, 2);
});

test("requires evidence for zero-problem and per-problem submissions", () => {
  const empty = inspectionToEditor({
    weekStart: "2026-08-24",
    inspectionDate: "2026-08-26",
    draftItems: [],
  });
  assert.deepEqual(
    validateWeeklySubmission(empty, new Date(2026, 7, 26)),
    ["无问题周检必须填写检查结论", "无问题周检必须上传 1–20 张现场照片"],
  );

  const oneProblem = {
    ...empty,
    conclusion: "发现问题",
    items: [{
      itemKey: "one",
      title: "临边防护",
      severity: "NORMAL",
      assigneeId: "2",
      deadline: "2026-08-27",
      beforePhotoFileIds: [12],
      newBeforeFiles: [],
    }],
  };
  assert.deepEqual(validateWeeklySubmission(oneProblem, new Date(2026, 7, 26)), []);

  assert.deepEqual(
    validateWeeklySubmission(
      { ...oneProblem, inspectionDate: "2026-08-27" },
      new Date(2026, 7, 26),
    ),
    ["检查日期不能晚于今天"],
  );

  assert.equal(
    validateWeeklySubmission(
      { ...oneProblem, items: Array.from({ length: 51 }, () => oneProblem.items[0]) },
      new Date(2026, 7, 26),
    )[0],
    "每份周检最多添加 50 个问题",
  );
});
