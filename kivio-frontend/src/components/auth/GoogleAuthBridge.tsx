'use client'
import { useEffect, useRef, useState } from 'react'
import { useSession, signOut } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { googleLogin } from '@/lib/api/client/auth'
import { getCurrentUser } from '@/lib/api/client/users'
import { useAuthStore } from '@/stores/useAuthStore'
import { resolveApiError } from '@/lib/apiErrors'
import { ROUTES } from '@/lib/constants'
import { FormAlert } from '@/components/form/FormAlert'

const GOOGLE_ERRORS: Record<string, string> = {
  GOOGLE_TOKEN_INVALID: 'Google 認証に失敗しました。お手数ですが、もう一度お試しください。',
  USER_DEACTIVATED: 'このアカウントは利用停止中です。サポートへお問い合わせください。',
}

/**
 * Google OAuth 完了後の id_token 交換・進行表示・エラー表示を行うコンポーネント。
 */
export function GoogleAuthBridge() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const setAuth = useAuthStore((s) => s.setAuth)
  // React 18 の Strict Mode 二重実行・再レンダリングでの多重交換を防ぐ
  const startedRef = useRef(false)
  const [error, setError] = useState<string | null>(null)

  // NextAuth の pages.error 遷移（同意画面キャンセル＝access_denied 等）で URL に付く
  // `?error=...` を表示には使わないため除去し、ログイン画面の URL を綺麗に保つ。
  // ?email= 等の他パラメータは残す。
  useEffect(() => {
    if (typeof window === 'undefined') return
    const url = new URL(window.location.href)
    if (url.searchParams.has('error')) {
      url.searchParams.delete('error')
      window.history.replaceState(null, '', url.pathname + url.search)
    }
  }, [])

  useEffect(() => {
    const idToken = session?.googleIdToken
    if (status !== 'authenticated' || !idToken || startedRef.current) return
    startedRef.current = true

    void (async () => {
      try {
        const tokens = await googleLogin(idToken)
        const user = await getCurrentUser(tokens.accessToken)
        // 受け渡し済みの NextAuth セッションは不要。先に破棄してから Kivio 認証を確定する
        await signOut({ redirect: false })
        setAuth({ accessToken: tokens.accessToken, user })
        router.replace(ROUTES.home)
      } catch (e) {
        await signOut({ redirect: false })
        setError(resolveApiError(e, GOOGLE_ERRORS))
        startedRef.current = false
      }
    })()
  }, [status, session, setAuth, router])

  if (error) {
    return (
      <div className="mb-6">
        <FormAlert>{error}</FormAlert>
      </div>
    )
  }

  if (status === 'authenticated' && session?.googleIdToken) {
    return (
      <div className="text-muted-foreground mb-6 text-center text-sm" role="status">
        Google でサインインしています…
      </div>
    )
  }

  return null
}
