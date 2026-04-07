import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: process.env.VITE_BASE_PATH || '/skypulse/',
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  build: {
    outDir: resolve(__dirname, '../src/main/resources/static'),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'echarts': ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'],
          'vue-vendor': ['vue', 'vue-router'],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/skypulse/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/skypulse/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
