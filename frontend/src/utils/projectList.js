export function requireProjectList(response) {
  if (response?.code !== 200) {
    throw new Error(response?.message || '项目加载失败');
  }
  if (!Array.isArray(response.data)) {
    throw new Error('项目列表接口返回格式不正确');
  }
  return response.data;
}
