// 校验工具函数

// 校验手机号
export function validatePhone(phone) {
  if (!phone) return { valid: false, message: '手机号不能为空' };
  const reg = /^1[3-9]\d{9}$/;
  if (!reg.test(phone)) {
    return { valid: false, message: '手机号格式不正确' };
  }
  return { valid: true };
}

// 校验身份证号
export function validateIdCard(idCard) {
  if (!idCard) return { valid: false, message: '身份证号不能为空' };
  const reg = /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/;
  if (!reg.test(idCard)) {
    return { valid: false, message: '身份证号格式不正确' };
  }
  return { valid: true };
}

// 校验邮箱
export function validateEmail(email) {
  if (!email) return { valid: false, message: '邮箱不能为空' };
  const reg = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$/;
  if (!reg.test(email)) {
    return { valid: false, message: '邮箱格式不正确' };
  }
  return { valid: true };
}

// 校验必填项
export function validateRequired(value, fieldName = '该字段') {
  if (!value || (typeof value === 'string' && value.trim() === '')) {
    return { valid: false, message: `${fieldName}不能为空` };
  }
  return { valid: true };
}

// 校验长度
export function validateLength(value, min, max, fieldName = '该字段') {
  if (!value) {
    return { valid: false, message: `${fieldName}不能为空` };
  }
  const len = value.length;
  if (min && len < min) {
    return { valid: false, message: `${fieldName}长度不能少于${min}个字符` };
  }
  if (max && len > max) {
    return { valid: false, message: `${fieldName}长度不能超过${max}个字符` };
  }
  return { valid: true };
}

// 校验数字范围
export function validateRange(value, min, max, fieldName = '该字段') {
  if (value === '' || value === null || value === undefined) {
    return { valid: false, message: `${fieldName}不能为空` };
  }
  const num = Number(value);
  if (isNaN(num)) {
    return { valid: false, message: `${fieldName}必须是数字` };
  }
  if (min !== undefined && num < min) {
    return { valid: false, message: `${fieldName}不能小于${min}` };
  }
  if (max !== undefined && num > max) {
    return { valid: false, message: `${fieldName}不能大于${max}` };
  }
  return { valid: true };
}

// 校验文件类型
export function validateFileType(file, acceptTypes) {
  if (!file) return { valid: false, message: '请选择文件' };
  if (!acceptTypes) return { valid: true };

  const acceptArr = acceptTypes.split(',').map(t => t.trim().toLowerCase());
  const fileExt = '.' + file.name.split('.').pop().toLowerCase();
  const fileType = file.type.toLowerCase();

  const extMatch = acceptArr.some(type => type.startsWith('.') && type === fileExt);
  const typeMatch = acceptArr.some(type => !type.startsWith('.') && fileType.includes(type));

  if (!extMatch && !typeMatch) {
    return { valid: false, message: `文件类型不支持，请上传${acceptTypes}格式` };
  }
  return { valid: true };
}

// 校验文件大小
export function validateFileSize(file, maxSize) {
  if (!file) return { valid: false, message: '请选择文件' };
  if (file.size > maxSize) {
    const maxMB = (maxSize / 1024 / 1024).toFixed(1);
    return { valid: false, message: `文件大小不能超过${maxMB}MB` };
  }
  return { valid: true };
}

// 综合校验表单
export function validateForm(formData, rules) {
  const errors = {};
  let isValid = true;

  for (const field in rules) {
    const rule = rules[field];
    const value = formData[field];

    if (rule.required) {
      const requiredResult = validateRequired(value, rule.label || field);
      if (!requiredResult.valid) {
        errors[field] = requiredResult.message;
        isValid = false;
        continue;
      }
    }

    if (value && rule.validator) {
      const result = rule.validator(value);
      if (!result.valid) {
        errors[field] = result.message;
        isValid = false;
      }
    }
  }

  return { isValid, errors };
}
