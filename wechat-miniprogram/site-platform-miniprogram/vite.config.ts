import { defineConfig, loadEnv } from 'vite';
import uniModule from '@dcloudio/vite-plugin-uni';

const uni = ((uniModule as unknown as { default?: typeof uniModule }).default || uniModule) as typeof uniModule;
const proxyTarget = 'http://127.0.0.1:8080';
const trustedDevelopmentOrigin = /^https?:\/\/(?:(?:localhost|127\.0\.0\.1|\[::1\])|(?:10\.(?:\d{1,3}\.){2}\d{1,3})|(?:169\.254\.(?:\d{1,3}\.)\d{1,3})|(?:172\.(?:1[6-9]|2\d|3[01])\.(?:\d{1,3}\.)\d{1,3})|(?:192\.168\.(?:\d{1,3}\.)\d{1,3}))(?::\d+)?$/;

function isLocalOrPrivateHost(hostname: string) {
  const normalized = hostname.toLowerCase();
  if (normalized === 'localhost' || normalized === '::1' || normalized === '[::1]'
    || normalized === '0.0.0.0' || normalized.endsWith('.local')) {
    return true;
  }
  const octets = normalized.split('.').map(Number);
  if (octets.length !== 4 || octets.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
    return false;
  }
  return octets[0] === 10
    || octets[0] === 127
    || (octets[0] === 169 && octets[1] === 254)
    || (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31)
    || (octets[0] === 192 && octets[1] === 168);
}

function validateMpRealEnvironment(mode: string, env: Record<string, string>) {
  if (mode !== 'mp-real') return;

  const configured = env.VITE_API_BASE_URL?.trim();
  if (!configured) {
    throw new Error('mp-real 构建缺少 VITE_API_BASE_URL，请先配置合法 HTTPS 接口地址');
  }

  let apiUrl: URL;
  try {
    apiUrl = new URL(configured);
  } catch {
    throw new Error('mp-real 的 VITE_API_BASE_URL 必须是合法的 HTTPS 绝对地址');
  }

  if (apiUrl.protocol !== 'https:') {
    throw new Error('mp-real 的 VITE_API_BASE_URL 必须使用 HTTPS');
  }
  if (isLocalOrPrivateHost(apiUrl.hostname)) {
    throw new Error('mp-real 禁止使用 localhost、127.0.0.1 或局域网接口地址');
  }
  if (apiUrl.pathname.replace(/\/+$/, '') !== '/api/v1' || apiUrl.search || apiUrl.hash) {
    throw new Error('mp-real 的 VITE_API_BASE_URL 必须以 /api/v1 作为完整接口前缀');
  }
  if (env.VITE_USE_MOCK !== 'false') {
    throw new Error('mp-real 必须显式设置 VITE_USE_MOCK=false');
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  validateMpRealEnvironment(mode, env);

  return {
    plugins: [uni()],
    server: {
      port: 3003,
      host: '0.0.0.0',
      cors: {
        origin: trustedDevelopmentOrigin,
      },
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyRequest) => {
              proxyRequest.setHeader('Origin', proxyTarget);
            });
          }
        }
      }
    }
  };
});
