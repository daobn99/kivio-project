import NextAuth from 'next-auth'
import Google from 'next-auth/providers/google'
import { ROUTES } from '@/lib/constants'

/**
 * NextAuth v5 設定。
 * Google OAuth は「Google から `id_token` を受け取る」目的にのみ利用する。
 * Kivio の認証状態（Access Token + ユーザー情報）の正は Zustand（メモリ）であり、
 * NextAuth セッションは `id_token` をバックエンド `POST /api/v1/auth/google` へ
 * 受け渡すための一時的な橋渡しに過ぎない（交換完了後に `signOut` で破棄する）。
 */
export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [Google],
  // localhost / docker いずれのホストでも Host ヘッダーを信頼する（dev 用）。
  trustHost: true,
  // サインイン／エラー時の遷移先を NextAuth 既定（/api/auth/error）ではなく、自前のログイン画面に向ける。Google 同意画面でキャンセルした場合
  // （error=access_denied）もエラーページではなくログイン画面へ戻す。
  pages: {
    signIn: ROUTES.auth.login,
    error: ROUTES.auth.login,
  },
  callbacks: {
    // 初回サインイン時のみ account から Google `id_token` を JWT に載せる。
    jwt({ token, account }) {
      if (account?.id_token) token.googleIdToken = account.id_token
      return token
    },
    // クライアントの GoogleAuthBridge が読めるよう session に `id_token` を露出する。
    session({ session, token }) {
      session.googleIdToken = token.googleIdToken
      return session
    },
  },
})
