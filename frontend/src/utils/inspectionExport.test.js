import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  buildInspectionExportParams,
  filterInspectionExportBoxes,
  getInspectionExportDefaults,
  toggleInspectionExportBoxSelection,
  validateInspectionExportRange,
} from './inspectionExport.js';

describe('inspection export date range', () => {
  it('uses the selected day or the selected month through today', () => {
    assert.deepEqual(getInspectionExportDefaults({
      periodMode: 'DAY', month: '2026-07', checkDate: '2026-07-12', today: '2026-08-03',
    }), { startDate: '2026-07-12', endDate: '2026-07-12' });
    assert.deepEqual(getInspectionExportDefaults({
      periodMode: 'MONTH', month: '2026-08', checkDate: '', today: '2026-08-03',
    }), { startDate: '2026-08-01', endDate: '2026-08-03' });
    assert.deepEqual(getInspectionExportDefaults({
      periodMode: 'MONTH', month: '2026-07', checkDate: '', today: '2026-08-03',
    }), { startDate: '2026-07-01', endDate: '2026-07-31' });
  });

  it('accepts at most 366 inclusive days and rejects invalid ranges', () => {
    assert.equal(validateInspectionExportRange('2024-01-01', '2024-12-31', '2026-08-03'), '');
    assert.match(validateInspectionExportRange('2023-12-31', '2024-12-31', '2026-08-03'), /366天/);
    assert.match(validateInspectionExportRange('2026-08-02', '2026-08-01', '2026-08-03'), /开始日期/);
    assert.match(validateInspectionExportRange('2026-08-01', '2026-08-04', '2026-08-03'), /今天/);
    assert.match(validateInspectionExportRange('2026-02-30', '2026-03-01', '2026-08-03'), /有效/);
  });
});

describe('inspection export electric-box selection', () => {
  it('omits boxIds for all boxes and serializes a distinct partial selection', () => {
    assert.equal(buildInspectionExportParams({
      projectId: 1, startDate: '2026-08-01', endDate: '2026-08-03', allBoxes: true, selectedBoxIds: ['10'],
    }).boxIds, undefined);
    assert.equal(buildInspectionExportParams({
      projectId: 1, startDate: '2026-08-01', endDate: '2026-08-03', allBoxes: false, selectedBoxIds: ['10', 20, '10'],
    }).boxIds, '10,20');
  });

  it('filters boxes without losing selected ids and toggles selection', () => {
    const boxes = [
      { id: 10, boxCode: 'EB-001', boxName: '一级箱', installLocation: '东侧' },
      { id: 20, boxCode: 'EB-002', boxName: '二级箱', responsibleElectricianName: '金泽' },
    ];
    assert.deepEqual(filterInspectionExportBoxes(boxes, '金泽').map(box => box.id), [20]);
    assert.deepEqual(toggleInspectionExportBoxSelection(['10'], 20), ['10', '20']);
    assert.deepEqual(toggleInspectionExportBoxSelection(['10', '20'], 10), ['20']);
  });
});
