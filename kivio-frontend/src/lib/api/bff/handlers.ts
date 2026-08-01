import 'server-only'
import { NextResponse, type NextRequest } from 'next/server'
import { callBackend, problemResponse, relayResponse } from './backend'
import {
  ACCESS_TOKEN_COOKIE,
  REFRESH_TOKEN_COOKIE,
  clearAuthCookies,
  setAuthCookies,
} from './cookies'
import { rejectIfCrossOrigin } from './csrf'

interface BackendTokens {
  accessToken: string
  refreshToken?: string
  tokenType: string
  expiresIn: number
}

/**
 * クライアントへ返す body からトークンの保管責務を分離する。
 * Refresh Token は httpOnly Cookie 専用とし JS から不可視にする。Access Token は
 * Access Token はメモリ保持のため body でも返す。
 */
function stripRefreshToken(tokens: BackendTokens) {
  return {
    accessToken: tokens.accessToken,
    tokenType: tokens.tokenType,
    expiresIn: tokens.expiresIn,
  }
}

/**
 * login / google / register-complete 用。
 * バックエンドでトークンを発行 → access/refresh を Cookie 化 → Refresh を除いた body を返す。
 */
export async function handleTokenMint(
  request: NextRequest,
  backendPath: string,
): Promise<NextResponse> {
  const blocked = rejectIfCrossOrigin(request)
  if (blocked) return blocked

  const backendRes = await callBackend(backendPath, {
    method: 'POST',
    body: await request.text(),
    contentType: 'application/json',
  })
  if (!backendRes.ok) return relayResponse(backendRes)

  const tokens = (await backendRes.json()) as BackendTokens
  const response = NextResponse.json(stripRefreshToken(tokens), { status: backendRes.status })
  setAuthCookies(response, tokens)
  return response
}

/**
 * refresh 用。Refresh Token Cookie をバックエンドへ渡してローテーションし、新しい Cookie を発行する。
 * 失効・再利用検知などの失敗時はセッション終了として Cookie を破棄する。
 */
export async function handleRefresh(request: NextRequest): Promise<NextResponse> {
  const blocked = rejectIfCrossOrigin(request)
  if (blocked) return blocked

  const refreshToken = request.cookies.get(REFRESH_TOKEN_COOKIE)?.value
  if (!refreshToken) {
    return problemResponse(
      401,
      'REFRESH_TOKEN_MISSING',
      'セッションの有効期限が切れました。再度ログインしてください。',
    )
  }

  const backendRes = await callBackend('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
    contentType: 'application/json',
  })
  if (!backendRes.ok) {
    const response = await relayResponse(backendRes)
    clearAuthCookies(response)
    return response
  }

  const tokens = (await backendRes.json()) as BackendTokens
  const response = NextResponse.json(stripRefreshToken(tokens), { status: 200 })
  setAuthCookies(response, tokens)
  return response
}

/**
 * logout 用。バックエンドで Refresh Token を失効させ、Cookie を破棄する。
 * バックエンドの失効はベストエフォート（失敗しても Cookie は必ず破棄する）。
 */
export async function handleLogout(request: NextRequest): Promise<NextResponse> {
  const blocked = rejectIfCrossOrigin(request)
  if (blocked) return blocked

  const refreshToken = request.cookies.get(REFRESH_TOKEN_COOKIE)?.value
  if (refreshToken) {
    await callBackend('/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
      contentType: 'application/json',
    }).catch(() => undefined)
  }

  const response = new NextResponse(null, { status: 204 })
  clearAuthCookies(response)
  return response
}

/**
 * 認証付きデータプロキシ。
 * access_token Cookie を `Authorization: Bearer` に詰め替えてバックエンドへ中継する。
 * トークン管理を伴う `/auth/*` は専用ハンドラが優先されるため、ここには到達しない。
 */
export async function proxyToBackend(
  request: NextRequest,
  ctx: { params: Promise<{ path: string[] }> },
): Promise<NextResponse> {
  const mutating = request.method !== 'GET' && request.method !== 'HEAD'
  if (mutating) {
    const blocked = rejectIfCrossOrigin(request)
    if (blocked) return blocked
  }

  const { path } = await ctx.params
  const target = `/${path.join('/')}${request.nextUrl.search}`

  const backendRes = await callBackend(target, {
    method: request.method,
    body: mutating ? await request.text() : undefined,
    accessToken: request.cookies.get(ACCESS_TOKEN_COOKIE)?.value,
    contentType: request.headers.get('content-type'),
  })
  return relayResponse(backendRes)
}
