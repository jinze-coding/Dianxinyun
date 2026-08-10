import test from 'node:test';
import assert from 'node:assert/strict';
import { profilePayload, validateProjectProfile } from './projectProfile.js';

const complete = () => ({
  projectName: '示例项目', shortName: '示例', directCompany: '直属公司', manager: '负责人',
  managerPhone: '13800000000', address: '项目地址', engineeringType: '房建', startDate: '2026-01-01',
  endDate: '2026-12-31', phase: '施工中', ownerUnit: '建设单位', supervisionUnit: '监理单位',
  designUnit: '设计单位', contractor: '施工单位', buildingArea: '100', landArea: '90',
  buildingHeight: '20', fixedIpAddress: '192.168.1.10', profileVersion: 2,
  images: [{ fileId: 7 }],
});

test('valid project profile has no client errors', () => {
  assert.deepEqual(validateProjectProfile(complete()), {});
});

test('rejects reversed dates, negative values, invalid ip and missing image', () => {
  const value = { ...complete(), endDate: '2025-01-01', buildingArea: '-1', fixedIpAddress: '999.1.1.1', images: [] };
  const errors = validateProjectProfile(value);
  assert.ok(errors.endDate);
  assert.ok(errors.buildingArea);
  assert.ok(errors.fixedIpAddress);
  assert.ok(errors.images);
});

test('payload keeps version and converts image objects to ids', () => {
  const payload = profilePayload(complete());
  assert.deepEqual(payload.imageFileIds, [7]);
  assert.equal(payload.expectedVersion, 2);
  assert.equal('images' in payload, false);
});

test('accepts compressed IPv6 and rejects malformed colon sequences', () => {
  assert.equal(validateProjectProfile({ ...complete(), fixedIpAddress: '2001:db8::1' }).fixedIpAddress, undefined);
  assert.ok(validateProjectProfile({ ...complete(), fixedIpAddress: '2001:::1' }).fixedIpAddress);
  assert.ok(validateProjectProfile({ ...complete(), fixedIpAddress: '1:2:3:4:5:6:7:8::' }).fixedIpAddress);
});
