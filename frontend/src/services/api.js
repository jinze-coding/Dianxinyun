import axios from 'axios';

// API基础配置
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
apiClient.interceptors.request.use(
  (config) => {
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
    return response.data;
  },
  (error) => {
    const { response } = error;
    if (response) {
      switch (response.status) {
        case 401:
          // token过期，清除token并跳转登录
          localStorage.removeItem('site_platform_token');
          localStorage.removeItem('site_platform_user');
          window.location.href = '/login';
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
