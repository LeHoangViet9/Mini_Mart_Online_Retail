import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    server: { proxy: { '/api': { target: env.API_URL || 'http://54.79.162.59', changeOrigin: true, timeout: 60000, proxyTimeout: 60000 } } },
    test: { environment: 'jsdom', setupFiles: './src/test-setup.js' },
  };
});
