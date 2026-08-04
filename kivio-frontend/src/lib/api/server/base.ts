import 'server-only'
import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import { ApiError } from '@/lib/api/ApiError'
import type { ProblemDetail } from '@/types/api'

/** Next.js の fetch 拡張オプション（`revalidate` / `tags`）を含む RequestInit */
type NextFetchInit = RequestInit & {
  next?: {
    revalidate?: number | false
    tags?: string[]
  }
}

/**
 * Server Component 用 API フェッチ関数。
 * Cookie から `access_token` を読み取り Authorization ヘッダーに付与する。
 * 401 はそのままログイン画面へリダイレクトする（Server 側でリフレッシュは行わない）。
 *
 * @param init `next.tags` を指定すると On-Demand Revalidation の対象になる。
 */
export async function apiFetchServer<T>(path: string, init?: NextFetchInit): Promise<T> {
  const cookieStore = await cookies()
  const token = cookieStore.get('access_token')?.value

  const { next, headers: initHeaders, ...rest } = init ?? {}

  const res = await fetch(`${process.env.API_BASE_URL}/api/v1${path}`, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(initHeaders as Record<string, string> | undefined),
    },
    // デフォルトはキャッシュなし。呼び出し側が `next.revalidate` や `'use cache'` で上書きする
    next: { revalidate: 0, ...next },
  })

  if (res.status === 401) redirect('/auth/login')

  if (!res.ok) {
    const problem: ProblemDetail = await res.json()
    throw new ApiError(problem.title, problem.status, problem.detail, problem.code)
  }

  // 204 No Content は res.json() を呼ぶと SyntaxError になるため早期リターン
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
