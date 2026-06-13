import { http, HttpResponse } from 'msw'

/**
 * 認証 API の MSW ハンドラ。クライアントは相対パス（`/api/v1/auth/...`）へ fetch し、
 * 本番では next.config.ts の rewrites がバックエンドへ転送する（FRONTEND_TEST_STRATEGY.md §4）。
 * エラーケースは各テストで server.use() により上書きする。
 */
export const authHandlers = [
  http.post('/api/v1/auth/login', () =>
    HttpResponse.json({
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    }),
  ),

  http.post('/api/v1/auth/register/request-otp', () =>
    HttpResponse.json(
      { message: '認証コードを送信しました', expiresInSeconds: 600 },
      { status: 202 },
    ),
  ),

  http.post('/api/v1/auth/register/verify-otp', () =>
    HttpResponse.json({ registrationToken: 'test-registration-token', expiresInSeconds: 1800 }),
  ),

  http.post('/api/v1/auth/register/complete', () =>
    HttpResponse.json(
      {
        accessToken: 'test-access-token',
        refreshToken: 'test-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 900,
      },
      { status: 201 },
    ),
  ),
]
