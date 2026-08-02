import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthUser } from '@/types/api'

/** 永続化した AuthUser の形。`AuthUser` にフィールドを追加・変更したら上げる */
const AUTH_STORAGE_VERSION = 1

interface AuthState {
  /** Access Token はメモリのみで保持する（localStorage に置かない＝XSS 対策） */
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
      // AuthUser にフィールドを足したらここを上げる。古い形のユーザーが残っていると、
      // 欠けたフィールドが undefined のまま分岐に使われて誤った UI が出る
      version: AUTH_STORAGE_VERSION,
      migrate: (persisted) => ({
        // ユーザー情報だけ捨てる。isAuthenticated は残すことで AuthHydrator が
        // GET /users/me を叩き直し、最新の形で埋め直してくれる
        user: null,
        isAuthenticated:
          (persisted as { isAuthenticated?: boolean } | null)?.isAuthenticated ?? false,
      }),
    },
  ),
)
