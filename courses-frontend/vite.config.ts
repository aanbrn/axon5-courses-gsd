import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // config.js is a development default only. A deployed image renders it at container start, and
  // the launcher cannot overwrite a file baked into the image under another owner.
  publicDir: command === 'serve' ? 'public' : false,
  build: {
    outDir: 'build/dist',
  },
  server: {
    port: 5173,
    proxy: {
      '/courses': 'http://localhost:8080',
      '/me': 'http://localhost:8080',
      '/session': 'http://localhost:8080',
    },
  },
}));
