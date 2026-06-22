import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // tsconfig の paths（@/*）は Vite が native に解決する（vite-tsconfig-paths は不要）
  resolve: { tsconfigPaths: true },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // E2E（Playwright）は別ランナー。Vitest からは除外する
    exclude: ['e2e/**', 'node_modules/**'],
  },
})
