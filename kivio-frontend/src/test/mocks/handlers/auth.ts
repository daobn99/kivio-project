import { http, HttpResponse } from 'msw'

/**
 * 認証 API の MSW ハンドラ。クライアントは相対パス（`/api/v1/auth/...`）へ fetch し、
 * 実環境では BFF Route Handler（src/app/api/v1）がトークンの Cookie 化を行いつつバックエンドへ中継する。MSW はその相対パスを直接モックする。
 * エラーケースは各テストで server.use() により上書きする。
 */
export const authHandlers = [
  // BFF（Route Handler）は Refresh Token を httpOnly Cookie へ退避し body から除去する。
  // クライアントが実際に受け取る body は accessToken のみ（refreshToken は含まれない）。
  http.post('/api/v1/auth/login', () =>
    HttpResponse.json({
      accessToken: 'test-access-token',
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
        tokenType: 'Bearer',
        expiresIn: 900,
      },
      { status: 201 },
    ),
  ),
]
