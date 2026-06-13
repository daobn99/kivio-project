import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthUser } from '@/types/api'

interface AuthState {
  /** Access Token はメモリのみで保持する（localStorage に永続化しない＝XSS 対策。SECURITY.md §2.1） */
  accessToken: string | null
  user: AuthUser | null
  isAuthenticated: boolean
  /** ログイン・登録完了時に認証情報をまとめてセットする */
  setAuth: (params: { accessToken: string; user: AuthUser }) => void
  /** リフレッシュで Access Token のみ更新する */
  setAccessToken: (accessToken: string) => void
  /** プロフィール更新時などにユーザー情報のみ差し替える */
  setUser: (user: AuthUser) => void
  /** ログアウト時に認証状態を全消去する */
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      isAuthenticated: false,
      setAuth: ({ accessToken, user }) => set({ accessToken, user, isAuthenticated: true }),
      setAccessToken: (accessToken) => set({ accessToken }),
      setUser: (user) => set({ user, isAuthenticated: true }),
      clearAuth: () => set({ accessToken: null, user: null, isAuthenticated: false }),
    }),
    {
      name: 'kivio-auth',
      // accessToken は永続化対象から除外する。リロード後は Refresh Token（Cookie）から再取得する
      partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
    },
  ),
)
