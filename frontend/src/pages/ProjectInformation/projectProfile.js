export const PROJECT_PROFILE_REQUIRED_FIELDS = [
  'projectName', 'shortName', 'directCompany', 'manager', 'managerPhone', 'address',
  'engineeringType', 'startDate', 'endDate', 'phase', 'ownerUnit', 'supervisionUnit',
  'designUnit', 'contractor', 'buildingArea', 'landArea', 'buildingHeight',
];

function isValidIpv6(value) {
  if (!value.includes(':') || value.includes(':::') || value.includes('%') || !/^[0-9a-f:]+$/i.test(value)) return false;
  const compressionCount = value.split('::').length - 1;
  if (compressionCount > 1) return false;
  const parts = value.split(':').filter(Boolean);
  if (!parts.every((part) => part.length <= 4)) return false;
  return compressionCount === 1 ? parts.length < 8 : parts.length === 8;
}

export function validateProjectProfile(draft) {
  const errors = {};
  PROJECT_PROFILE_REQUIRED_FIELDS.forEach((field) => {
    if (draft?.[field] === null || draft?.[field] === undefined || String(draft[field]).trim() === '') {
      errors[field] = '此项为必填项';
    }
  });
  if ((draft?.shortName || '').trim().length > 12) errors.shortName = '项目简称不能超过12个字';
  if (draft?.startDate && draft?.endDate && draft.endDate < draft.startDate) errors.endDate = '计划竣工日期不能早于计划开工日期';
  if (draft?.actualStartDate && draft?.actualEndDate && draft.actualEndDate < draft.actualStartDate) errors.actualEndDate = '实际竣工日期不能早于实际开工日期';
  [
    'contractAmount', 'buildingArea', 'landArea', 'buildingHeight', 'excavationDepth',
    'undergroundFloorCount', 'abovegroundFloorCount', 'managementStaffCount',
    'attendanceCount', 'partyMemberCount',
  ].forEach((field) => {
    if (draft?.[field] !== '' && draft?.[field] !== null && draft?.[field] !== undefined
      && (!Number.isFinite(Number(draft[field])) || Number(draft[field]) < 0)) {
      errors[field] = '请输入非负数';
    }
  });
  ['undergroundFloorCount', 'abovegroundFloorCount', 'managementStaffCount', 'attendanceCount', 'partyMemberCount'].forEach((field) => {
    if (!errors[field] && draft?.[field] !== '' && draft?.[field] !== null && draft?.[field] !== undefined
      && !Number.isInteger(Number(draft[field]))) errors[field] = '请输入整数';
  });
  const ip = (draft?.fixedIpAddress || '').trim();
  if (ip) {
    const ipv4 = ip.split('.');
    const validV4 = ipv4.length === 4 && ipv4.every((part) => /^(0|[1-9]\d{0,2})$/.test(part) && Number(part) <= 255);
    const validV6 = isValidIpv6(ip);
    if (!validV4 && !validV6) errors.fixedIpAddress = '请输入有效的 IPv4 或 IPv6 地址';
  }
  if (!Array.isArray(draft?.images) || draft.images.length === 0) errors.images = '请至少上传一张项目效果图';
  if ((draft?.images?.length || 0) > 20) errors.images = '项目效果图最多20张';
  return errors;
}

export function profilePayload(draft) {
  const payload = { ...draft, imageFileIds: (draft.images || []).map((image) => image.fileId), expectedVersion: draft.profileVersion };
  delete payload.images;
  delete payload.canEdit;
  delete payload.updateTime;
  delete payload.projectId;
  return payload;
}
