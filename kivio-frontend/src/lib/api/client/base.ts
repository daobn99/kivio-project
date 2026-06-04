'use client'
import { redirect } from 'next/navigation'
import { ApiError } from '@/lib/api/ApiError'
import type { ProblemDetail } from '@/types/api'

// リフレッシュ成功なら true、失敗（ネットワークエラー含む）なら false を返す
async function tryRefresh(): Promise<boolean> {
  try {
    const res = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    })
    return res.ok
  } catch {
    return false
  }
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
    const refreshed = await tryRefresh()
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
