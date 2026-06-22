'use client'
import { useEffect, useRef } from 'react'
import { refreshSession } from '@/lib/api/client/base'
import { getCurrentUser } from '@/lib/api/client/users'
import { useAuthStore } from '@/stores/useAuthStore'

/**
 * リロード後の認証状態の再ハイドレーション。
 *
 * Access Token はメモリ（Zustand）保持のためリロードで失われるが、認証 Cookie
 * （access_token / refresh_token）は httpOnly Cookie として残る。永続化された
 * `isAuthenticated` が true なのにメモリのトークンが無い＝リロード直後のみ、Cookie 越しに
 * `GET /users/me` でセッションを確認する。access_token 失効時は refresh を 1 度試み、
 * それでも失敗すれば（＝Cookie セッションが無効）永続化された認証状態を破棄する。
 *
 * 本コンポーネントは描画を持たず、ルートレイアウトに一度だけマウントする。
 */
export function AuthHydrator() {
  const ranRef = useRef(false)

  useEffect(() => {
    if (ranRef.current) return
    ranRef.current = true

    const { isAuthenticated, accessToken, setUser, clearAuth } = useAuthStore.getState()
    // 未ログイン、またはログイン直後（メモリにトークンあり）は確認不要
    if (!isAuthenticated || accessToken) return

    void (async () => {
      try {
        setUser(await getCurrentUser())
      } catch {
        if (!(await refreshSession())) {
          clearAuth()
          return
        }
        try {
          setUser(await getCurrentUser())
        } catch {
          clearAuth()
        }
      }
    })()
  }, [])

  return null
}
