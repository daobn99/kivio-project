import { test, expect, type Page } from '@playwright/test'

/**
 * 認証フローの E2E。
 *
 * バックエンドは起動せず、`/api/v1/auth/**` を Playwright の route インターセプトで
 * モックする（OTP もモック）。next.config.ts の rewrites より手前のブラウザ層で捕捉するため
 * 実バックエンド不要で 3 ステップ登録・ログイン・認証ガードを検証できる。
 */

const TOKENS = {
  accessToken: 'e2e-access-token',
  refreshToken: 'e2e-refresh-token',
  tokenType: 'Bearer',
  expiresIn: 900,
}

/** ProblemDetail（RFC 9457）形式のエラーを返す */
function problem(status: number, code: string, title: string) {
  return {
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({ type: 'about:blank', title, status, code, detail: '' }),
  }
}

/** 6桁 OTP を各セルに入力する（OtpInput は 1 セル 1 桁） */
async function fillOtp(page: Page, code: string) {
  for (let i = 0; i < code.length; i++) {
    await page.getByLabel(`認証コード ${i + 1}桁目`).fill(code[i])
  }
}

test.describe('認証ガード（proxy）', () => {
  test('未認証ユーザーが protected route に直接アクセスすると /auth/login へリダイレクトされる', async ({
    page,
  }) => {
    await page.goto('/cart')
    await expect(page).toHaveURL(/\/auth\/login/)
  })

  test('認証済みユーザーが /auth/login にアクセスすると / へリダイレクトされる', async ({
    page,
    context,
  }) => {
    await context.addCookies([
      { name: 'access_token', value: 'dummy', url: 'http://127.0.0.1:3000' },
    ])
    await page.goto('/auth/login')
    await expect(page).toHaveURL(/^https?:\/\/[^/]+\/$/)
  })
})

test.describe('ログイン', () => {
  test('正しい資格情報でログインするとホームへ遷移する', async ({ page }) => {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(TOKENS) }),
    )

    await page.goto('/auth/login')
    await page.getByLabel('メールアドレス').fill('user@example.com')
    await page.getByLabel('パスワード', { exact: true }).fill('password123')
    await page.getByRole('button', { name: 'ログイン' }).click()

    await expect(page).toHaveURL(/^https?:\/\/[^/]+\/$/)
  })

  test('誤った資格情報ではエラーメッセージが表示される', async ({ page }) => {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill(problem(401, 'INVALID_CREDENTIALS', '認証失敗')),
    )

    await page.goto('/auth/login')
    await page.getByLabel('メールアドレス').fill('user@example.com')
    await page.getByLabel('パスワード', { exact: true }).fill('wrongpassword')
    await page.getByRole('button', { name: 'ログイン' }).click()

    await expect(page.getByText('メールアドレスまたはパスワードが正しくありません。')).toBeVisible()
    await expect(page).toHaveURL(/\/auth\/login/)
  })
})

test.describe('会員登録（3ステップ）', () => {
  test('メール → OTP → パスワード設定で登録が完了しホームへ遷移する', async ({ page }) => {
    await page.route('**/api/v1/auth/register/request-otp', (route) =>
      route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ message: '認証コードを送信しました', expiresInSeconds: 600 }),
      }),
    )
    await page.route('**/api/v1/auth/register/verify-otp', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ registrationToken: 'e2e-reg-token', expiresInSeconds: 1800 }),
      }),
    )
    await page.route('**/api/v1/auth/register/complete', (route) =>
      route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(TOKENS) }),
    )

    await page.goto('/auth/register')

    // Step1: メール入力
    await expect(page.getByRole('heading', { name: 'Kivioをはじめよう' })).toBeVisible()
    await page.getByLabel('メールアドレス').fill('new@example.com')
    await page.getByRole('button', { name: '認証コードを送信' }).click()

    // Step2: OTP 入力
    await expect(page.getByRole('heading', { name: '認証コードを入力' })).toBeVisible()
    await fillOtp(page, '123456')

    // Step3: パスワード設定
    await expect(page.getByRole('heading', { name: 'パスワードを設定' })).toBeVisible()
    await page.getByLabel('表示名').fill('テスト太郎')
    await page.getByLabel('パスワード', { exact: true }).fill('password123')
    await page.getByLabel('パスワード（確認用）').fill('password123')
    await page.getByRole('button', { name: '登録して始める' }).click()

    // 自動ログインでホームへ
    await expect(page).toHaveURL(/^https?:\/\/[^/]+\/$/)
  })

  test('OTP を誤入力するとエラーが表示され再入力できる', async ({ page }) => {
    await page.route('**/api/v1/auth/register/request-otp', (route) =>
      route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ message: '認証コードを送信しました', expiresInSeconds: 600 }),
      }),
    )
    await page.route('**/api/v1/auth/register/verify-otp', (route) =>
      route.fulfill(problem(400, 'OTP_INVALID', 'コード不正')),
    )

    await page.goto('/auth/register')
    await page.getByLabel('メールアドレス').fill('new@example.com')
    await page.getByRole('button', { name: '認証コードを送信' }).click()

    await expect(page.getByRole('heading', { name: '認証コードを入力' })).toBeVisible()
    await fillOtp(page, '000000')

    await expect(page.getByText('認証コードが正しくありません（残り4回）')).toBeVisible()
    // 失効していないため再入力できる
    await expect(page.getByLabel('認証コード 1桁目')).toBeEnabled()
  })
})
