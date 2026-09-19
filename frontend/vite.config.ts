import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ command }) => ({
  plugins: [react()],
  base: command === 'build' ? '/react/' : '/',
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/css': 'http://localhost:8080',
      '/favicon.png': 'http://localhost:8080'
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
}))
