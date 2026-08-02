import { http, HttpResponse } from 'msw'

/** `GET /users/me` の既定レスポンス。個別テストは server.use() で上書きする */
export const mockCurrentUser = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'user@example.com',
  displayName: 'テストユーザー',
  avatarUrl: null as string | null,
  role: 'ROLE_BUYER',
  status: 'ACTIVE',
  hasPassword: true,
  createdAt: '2026-05-24T10:00:00Z',
}

/**
 * ユーザー API の MSW ハンドラ。プロフィール取得・更新・パスワード変更・退会を扱う。
 * ロール別・エラー系の挙動を検証するテストでは server.use() で上書きする。
 */
export const userHandlers = [
  http.get('/api/v1/users/me', () => HttpResponse.json(mockCurrentUser)),

  http.patch('/api/v1/users/me', async ({ request }) => {
    const body = (await request.json()) as { displayName?: string; avatarUrl?: string }
    return HttpResponse.json({
      ...mockCurrentUser,
      ...(body.displayName !== undefined ? { displayName: body.displayName } : {}),
      // 空文字はクリア（null 化）— バックエンドの挙動に合わせる
      ...(body.avatarUrl !== undefined ? { avatarUrl: body.avatarUrl || null } : {}),
    })
  }),

  http.patch('/api/v1/users/me/password', () => new HttpResponse(null, { status: 204 })),

  http.delete('/api/v1/users/me', () => new HttpResponse(null, { status: 204 })),
]
