'use client'
import { ApiError } from '@/lib/api/ApiError'
import { apiFetch } from '@/lib/api/client/base'
import { useAuthStore } from '@/stores/useAuthStore'
import type { AuthUser, ProblemDetail } from '@/types/api'

/**
 * 認証中ユーザー自身のプロフィールを取得する。
 *
 * ログイン直後はまだストアにトークンが入っていないため、受け取ったトークンを
 * `accessToken` で渡せるようにしている。省略時はストアの値を使う。
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

/** 未送信フィールドは更新されない（部分更新） */
export interface UpdateProfilePayload {
  displayName?: string
  /** 空文字を送るとアバターがクリアされる */
  avatarUrl?: string
}

/** 変更したフィールドのみを渡すこと。未送信フィールドはサーバー側で不変となる。 */
export function updateProfile(payload: UpdateProfilePayload): Promise<AuthUser> {
  return apiFetch<AuthUser>('/users/me', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

/** 現在のパスワード不一致・パスワード未設定のどちらも `PASSWORD_CHANGE_FAILED`（400）。 */
export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return apiFetch<void>('/users/me/password', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

/** 論理削除。以降このアカウントではログインできない。 */
export function withdrawAccount(): Promise<void> {
  return apiFetch<void>('/users/me', { method: 'DELETE' })
}
