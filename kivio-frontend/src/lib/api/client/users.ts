'use client'
import { ApiError } from '@/lib/api/ApiError'
import { useAuthStore } from '@/stores/useAuthStore'
import type { AuthUser, ProblemDetail } from '@/types/api'

/**
 * 認証中ユーザー自身のプロフィールを取得する（`GET /api/v1/users/me`）。
 *
 * Access Token はメモリ（Zustand）に保持される設計のため、ここで明示的に
 * `Authorization: Bearer` を付与する。ログイン直後など store への反映前に呼ぶ場合は
 * `accessToken` を引数で渡す（呼び出し側が受け取ったトークンをそのまま使える）。
 */
export async function getCurrentUser(accessToken?: string): Promise<AuthUser> {
  const token = accessToken ?? useAuthStore.getState().accessToken

  const res = await fetch('/api/v1/users/me', {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    credentials: 'include',
  })

  if (!res.ok) {
    const problem: ProblemDetail = await res.json()
    throw new ApiError(problem.title, problem.status, problem.detail, problem.code)
  }

  return res.json() as Promise<AuthUser>
}
