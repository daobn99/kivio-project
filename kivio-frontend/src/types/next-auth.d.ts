import 'next-auth'
import 'next-auth/jwt'

/**
 * NextAuth の型拡張。
 * Google OAuth で取得した `id_token` を session / JWT に持ち回すための拡張。
 * `POST /api/v1/auth/google` への受け渡し専用フィールド。
 */
declare module 'next-auth' {
  interface Session {
    googleIdToken?: string
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    googleIdToken?: string
  }
}
