import 'server-only'
import type { NextResponse } from 'next/server'

export const ACCESS_TOKEN_COOKIE = 'access_token'
export const REFRESH_TOKEN_COOKIE = 'refresh_token'

// Refresh Token の寿命（7日）。バックエンドの JWT_REFRESH_TOKEN_EXPIRATION と一致させる。
const REFRESH_MAX_AGE_SECONDS = 60 * 60 * 24 * 7

const baseCookieOptions = {
  httpOnly: true,
  // 同一オリジン BFF。Lax によりクロスサイトの POST ではそもそも Cookie が送られず CSRF を大きく抑止する。
  sameSite: 'lax' as const,
  // 本番(HTTPS)のみ Secure。dev(http://localhost) では false にしないと Cookie がセットされない。
  secure: process.env.NODE_ENV === 'production',
  path: '/',
}

interface TokenSet {
  accessToken: string
  /** Token Rotation で再発行された場合のみ存在する */
  refreshToken?: string
  /** access_token の有効期間（秒）。Cookie の maxAge に用いる */
  expiresIn: number
}

/**
 * 認証 Cookie を発行する。
 * access_token は短命（expiresIn 秒）、refresh_token は長命（7日）。
 * いずれも httpOnly のため JS からは読めない（XSS でのトークン持ち出しを防ぐ）。
 */
export function setAuthCookies(response: NextResponse, tokens: TokenSet): void {
  response.cookies.set(ACCESS_TOKEN_COOKIE, tokens.accessToken, {
    ...baseCookieOptions,
    maxAge: tokens.expiresIn,
  })
  if (tokens.refreshToken) {
    response.cookies.set(REFRESH_TOKEN_COOKIE, tokens.refreshToken, {
      ...baseCookieOptions,
      maxAge: REFRESH_MAX_AGE_SECONDS,
    })
  }
}

/** ログアウト・リフレッシュ失敗時に認証 Cookie を破棄する。 */
export function clearAuthCookies(response: NextResponse): void {
  response.cookies.set(ACCESS_TOKEN_COOKIE, '', { ...baseCookieOptions, maxAge: 0 })
  response.cookies.set(REFRESH_TOKEN_COOKIE, '', { ...baseCookieOptions, maxAge: 0 })
}
