import assert from 'node:assert/strict';
import {
  extractElectricBoxScene,
  extractElectricBoxSceneFromScanResult
} from '../src/utils/electricBoxScene.ts';
import { isElectricInspectionTodo } from '../src/utils/electricInspectionTodo.ts';

assert.equal(extractElectricBoxScene('B:DEMO-PUBLIC-001'), 'B:DEMO-PUBLIC-001');
assert.equal(extractElectricBoxScene('P:EDGE-POINT-001'), '');
assert.equal(
  extractElectricBoxScene('pages/scan-entry/index?scene=B%3ADEMO-PUBLIC-001'),
  'B:DEMO-PUBLIC-001'
);
assert.equal(
  extractElectricBoxSceneFromScanResult({
    scanType: 'WX_CODE',
    result: '无法解析但非空的微信返回值',
    path: 'pages/scan-entry/index?scene=B%3ADEMO-PUBLIC-001'
  }),
  'B:DEMO-PUBLIC-001'
);
assert.equal(extractElectricBoxScene('pages/scan-entry/index?scene=P%3AEDGE-POINT-001'), '');
assert.equal(
  extractElectricBoxSceneFromScanResult({
    scanType: 'QR_CODE',
    result: 'https://zhihuiyz.xyz/public/electric-boxes/DEMO-PUBLIC-001/monthly-records'
  }),
  'B:DEMO-PUBLIC-001'
);
assert.equal(
  extractElectricBoxSceneFromScanResult({
    scanType: 'WX_CODE',
    result: 'B:SHOULD-NOT-BE-USED',
    path: 'pages/login/index'
  }),
  ''
);
assert.equal(extractElectricBoxSceneFromScanResult({ result: 'not-an-electric-box-code' }), '');

assert.equal(isElectricInspectionTodo({ type: 'RECTIFICATION', businessType: 'INSPECTION_RECORD' }), true);
assert.equal(isElectricInspectionTodo({ type: 'RECHECK', businessType: 'INSPECTION_RECORD' }), true);
assert.equal(isElectricInspectionTodo({ type: 'RECTIFICATION', businessType: 'QUALITY_ISSUE' }), false);
assert.equal(isElectricInspectionTodo({ type: 'RECHECK', routeCode: 'QUALITY_ISSUE_DETAIL' }), false);
assert.equal(isElectricInspectionTodo({ type: 'RECTIFICATION', routeCode: 'INSPECTION_RECTIFICATION_DETAIL' }), true);
assert.equal(isElectricInspectionTodo({ type: 'RECTIFICATION', routeCode: 'RECTIFICATION_DETAIL' }), true);
assert.equal(isElectricInspectionTodo({ type: 'RECHECK' }), true);

console.log('electric-box scan parsing: OK');
