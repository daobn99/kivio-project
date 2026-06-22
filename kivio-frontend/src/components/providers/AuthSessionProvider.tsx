'use client'
import { SessionProvider } from 'next-auth/react'

/**
 * NextAuth の SessionProvider クライアントラッパー。
 * useSession / signIn / signOut を使う認証画面でのみ必要なため、ルートではなく
 * 認証レイアウト（src/app/auth/(auth-group)/layout.tsx）にスコープして適用する。
 */
export function AuthSessionProvider({ children }: { children: React.ReactNode }) {
  return <SessionProvider>{children}</SessionProvider>
}
