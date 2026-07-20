import { defineConfig } from 'vite';
import uniModule from '@dcloudio/vite-plugin-uni';

const uni = ((uniModule as unknown as { default?: typeof uniModule }).default || uniModule) as typeof uniModule;

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 3003,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
});
