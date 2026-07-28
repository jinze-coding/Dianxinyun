import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3002,
    open: false,
    allowedHosts: [
      'trained-channels-alternate-petersburg.trycloudflare.com',
    ],
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/api/v1'),
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
});
