'use client'
import { redirect } from 'next/navigation'
import { ApiError } from '@/lib/api/ApiError'
import type { ProblemDetail } from '@/types/api'

let refreshInFlight: Promise<boolean> | null = null

/**
 * Refresh Token（httpOnly Cookie）でセッションを更新する。成功なら true（新しい
 * access/refresh Cookie が BFF により発行済み）、失敗（ネットワークエラー含む）なら false。
 *
 * Token Rotation は単一使用のため、並行リクエストが同時にリフレッシュすると 2 本目が
 * 失効済みトークンの再利用と判定され、バックエンドの Reuse Detection（全セッション無効化）を
 * 誘発する。これを防ぐため single-flight で重複排除する。
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

async function doFetch(path: string, init?: RequestInit): Promise<Response> {
  return fetch(`/api/v1${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    credentials: 'include',
  })
}

/**
 * Client Component 用 API フェッチ関数。
 * 401 を受け取った場合はリフレッシュを1回試み、失敗すればログイン画面へリダイレクトする。
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await doFetch(path, init)

  if (res.status === 401) {
    const refreshed = await refreshSession()
    if (!refreshed) redirect('/auth/login')
    res = await doFetch(path, init)
    // リフレッシュ後も 401 はトークン不正と判断してログインへ
    if (res.status === 401) redirect('/auth/login')
  }

  if (!res.ok) {
    const problem: ProblemDetail = await res.json()
    throw new ApiError(problem.title, problem.status, problem.detail, problem.code)
  }

  // 204 No Content は res.json() を呼ぶと SyntaxError になるため早期リターン
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
