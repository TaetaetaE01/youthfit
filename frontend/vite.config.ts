import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  envDir: path.resolve(__dirname, '..'),
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // 머신 TZ 에 의존하는 날짜 회귀를 결정적으로 잡기 위해 테스트 TZ 를 고정한다.
    // (datetime 유틸은 명시 오프셋/Asia/Seoul 을 쓰므로 원래 TZ 무관하지만, 안전망)
    env: { TZ: 'UTC' },
    alias: {
      '@': path.resolve(__dirname, './src'),
    },

  },
})
