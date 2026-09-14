import axios from 'axios';

// API基础配置
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

let currentProjectId = null;
let currentBusinessModule = null;
const moduleRequests = new Set();
export function setApiProjectContext(projectId, moduleCode = null) { currentProjectId = projectId; currentBusinessModule = moduleCode; }
function requestData(config, key) {
  return typeof FormData !== 'undefined' && config.data instanceof FormData ? config.data.get(key) : config.data?.[key];
}
function requestModule(config) {
  const path = String(config.url || '');
  if (/^\/site-access(?:\/|$)/.test(path)) return 'SITE_ACCESS';
  if (/^\/(?:project-documents|document-[^/]+|seal)(?:\/|$)/.test(path)) return 'DOCUMENT';
  if (/^\/(?:electric-boxes|inspection|edge-inspections)(?:\/|$)/.test(path)) return 'INSPECTION';
  if (/^\/quality(?:\/|$)/.test(path)) return 'QUALITY';
  if (/^\/safety-committee(?:\/|$)/.test(path)) return 'SAFETY_COMMITTEE';
  if (/^\/files(?:\/|$)/.test(path)) {
    const type = String(requestData(config, 'businessType') || config.params?.businessType || '');
    if (/^(DOCUMENT_|SEAL_|PROJECT_DOCUMENT)/.test(type)) return 'DOCUMENT';
    if (/^QUALITY_/.test(type)) return 'QUALITY';
    if (/^(INSPECTION_|EDGE_INSPECTION_|ELECTRIC_BOX)/.test(type)) return 'INSPECTION';
    if (/^(COMMITTEE_|SAFETY_COMMITTEE)/.test(type)) return 'SAFETY_COMMITTEE';
    if (/^(SITE_|MEETING_|GUARD_)/.test(type)) return 'SITE_ACCESS';
    return currentBusinessModule;
  }
  return null;
}
function finishModuleRequest(config) {
  if (!config?._moduleRequest) return;
  config._moduleRequest.detach?.();
  moduleRequests.delete(config._moduleRequest);
}
if (typeof window !== 'undefined') window.addEventListener('project-module-availability', ({ detail }) => {
  for (const item of moduleRequests) {
    if (Number(item.projectId) === Number(detail.projectId) && !detail.enabledBusinessModules.includes(item.module)) item.controller.abort();
  }
});

function handleUnauthorized() {
  localStorage.removeItem('site_platform_token');
  localStorage.removeItem('site_platform_user');
  window.dispatchEvent(new CustomEvent('site-platform-auth-expired'));
}

export function getApiErrorMessage(error, fallback = '请求失败') {
  return error?.response?.data?.message
    || error?.response?.data?.error
    || error?.message
    || fallback;
}

export async function ensureFileBlob(blob, fallbackMessage = '文件请求失败') {
  if (!(blob instanceof Blob)) {
    throw new Error(fallbackMessage);
  }
  if (!String(blob.type || '').toLowerCase().includes('json')) {
    return blob;
  }
  try {
    const result = JSON.parse(await blob.text());
    const isResultPayload = Number.isFinite(Number(result?.code))
      && typeof result?.message === 'string'
      && Object.prototype.hasOwnProperty.call(result, 'data');
    if (!isResultPayload) return blob;
    if (Number(result.code) === 401) handleUnauthorized();
    throw new Error(result.message || fallbackMessage);
  } catch (error) {
    if (error instanceof SyntaxError) return blob;
    throw error;
  }
}

// 请求拦截器
apiClient.interceptors.request.use(
  (config) => {
    const module = requestModule(config);
    if (module) {
      const controller = new AbortController();
      const original = config.signal;
      const abort = () => controller.abort();
      if (original?.aborted) abort();
      original?.addEventListener('abort', abort, { once: true });
      const formProject = requestData(config, 'projectId');
      config._moduleRequest = { module, projectId: config.params?.projectId || formProject || currentProjectId, controller, detach: () => original?.removeEventListener('abort', abort) };
      moduleRequests.add(config._moduleRequest);
      config.signal = controller.signal;
    }
    // 添加token
    const token = localStorage.getItem('site_platform_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
apiClient.interceptors.response.use(
  (response) => {
    finishModuleRequest(response.config);
    const result = response.data;
    if (result?.code === 401) {
      handleUnauthorized();
      return Promise.reject(new Error(result.message || '登录状态已失效，请重新登录'));
    }
    return result;
  },
  (error) => {
    finishModuleRequest(error.config);
    const { response } = error;
    if (response?.data?.data?.reason === 'PROJECT_MODULE_DISABLED') window.dispatchEvent(new CustomEvent('project-modules-changed', { detail: response.data.data }));
    if (response?.data?.message) {
      error.message = response.data.message;
    }
    if (response) {
      switch (response.status) {
        case 401:
          // token过期，清除token并跳转登录
          handleUnauthorized();
          break;
        case 403:
          console.error('没有权限');
          break;
        case 500:
          console.error('服务器错误');
          break;
        default:
          break;
      }
    }
    return Promise.reject(error);
  }
);

// 通用GET请求
export const get = (url, params) => {
  return apiClient.get(url, { params });
};

// 通用POST请求
export const post = (url, data) => {
  return apiClient.post(url, data);
};

// 通用PUT请求
export const put = (url, data) => {
  return apiClient.put(url, data);
};

// 通用DELETE请求
export const del = (url, params) => {
  return apiClient.delete(url, { params });
};

export default apiClient;
