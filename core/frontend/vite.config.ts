import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': process.env.UML_API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
      '/ws': {
        target: process.env.UML_API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
        ws: true,
        configure: (proxy) => {
          proxy.on('error', (err: any) => {
            if (err?.code === 'ECONNRESET' || err?.code === 'ECONNABORTED') {
              return;
            }
            console.error('[vite ws proxy error]', err);
          });
        },
      },
    },
  },
  preview: { port: 4173, strictPort: true },
});
