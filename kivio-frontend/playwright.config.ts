import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  use: {
    // localhost が IPv6(::1) に解決し Next の IPv4 バインドへ届かない環境があるため 127.0.0.1 で固定する
    baseURL: 'http://127.0.0.1:3000',
    trace: 'on-first-retry',
  },
  projects: [
    // ポートフォリオ目的のため Chromium のみ
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  // PW_NO_SERVER=1 のときは Playwright に dev サーバを起動させない（外部で起動済みのサーバを使う）
  webServer: process.env.PW_NO_SERVER
    ? undefined
    : {
        command: 'pnpm dev',
        url: 'http://127.0.0.1:3000',
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
