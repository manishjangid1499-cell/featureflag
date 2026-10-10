import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],

  server: {
    port: 5174,
    strictPort: true,

    proxy: {
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/flags': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/analytics': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/notifications': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/audit': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/members': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/sdk-keys': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        bypass(req) {
          if (req.headers.accept?.includes('text/html')) {
            return '/index.html'
          }
        },
      },
    },
  },
})