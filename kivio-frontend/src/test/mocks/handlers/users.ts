import { http, HttpResponse } from 'msw'

/**
 * ユーザー API の MSW ハンドラ。`GET /users/me` は認証済みユーザーのプロフィールを返す。
 * ロール別の挙動を検証するテストでは server.use() で上書きする。
 */
export const userHandlers = [
  http.get('/api/v1/users/me', () =>
    HttpResponse.json({
      id: '00000000-0000-0000-0000-000000000001',
      email: 'user@example.com',
      displayName: 'テストユーザー',
      avatarUrl: null,
      role: 'ROLE_BUYER',
      status: 'ACTIVE',
      createdAt: '2026-05-24T10:00:00Z',
    }),
  ),
]
