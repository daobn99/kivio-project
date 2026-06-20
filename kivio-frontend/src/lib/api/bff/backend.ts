import 'server-only'
import { NextResponse } from 'next/server'

const BACKEND_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

interface BackendCallOptions {
  method: string
  body?: string
  /** 付与すると `Authorization: Bearer` を注入する（access_token Cookie 由来） */
  accessToken?: string
  contentType?: string | null
}

/**
 * バックエンド（Spring Boot）の `/api/v1` を呼ぶ。
 * ブラウザの Cookie は転送せず、必要なときだけ access_token を Bearer に詰め替える
 * （バックエンドは Bearer 認証のみ・Cookie は解釈しない）。
 */
export function callBackend(path: string, opts: BackendCallOptions): Promise<Response> {
  const headers: Record<string, string> = {}
  if (opts.contentType) headers['content-type'] = opts.contentType
  if (opts.accessToken) headers['authorization'] = `Bearer ${opts.accessToken}`

  return fetch(`${BACKEND_BASE_URL}/api/v1${path}`, {
    method: opts.method,
    headers,
    body: opts.body,
    cache: 'no-store',
    redirect: 'manual',
  })
}

/** バックエンドのレスポンス本文・ステータス・Content-Type をそのままブラウザへ中継する。 */
export async function relayResponse(backendRes: Response): Promise<NextResponse> {
  const text = await backendRes.text()
  return new NextResponse(text.length > 0 ? text : null, {
    status: backendRes.status,
    headers: { 'content-type': backendRes.headers.get('content-type') ?? 'application/json' },
  })
}

/** RFC 9457 ProblemDetail 形式のエラー（クライアントの ApiError 解釈に合わせる）。 */
export function problemResponse(status: number, code: string, detail: string): NextResponse {
  return NextResponse.json(
    { type: 'about:blank', title: detail, status, detail, code },
    { status, headers: { 'content-type': 'application/problem+json' } },
  )
}
