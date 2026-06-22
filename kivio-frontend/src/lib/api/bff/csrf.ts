import 'server-only'
import type { NextRequest, NextResponse } from 'next/server'
import { problemResponse } from './backend'

/**
 * 状態変更系リクエストの CSRF 対策（Origin チェック）。
 *
 * Cookie 認証へ移行するため、SameSite=Lax に加えて Origin ヘッダーがリクエスト先ホストと
 * 一致することを検証する。ブラウザは same-origin の POST/PATCH/PUT/DELETE にも必ず Origin を
 * 付与するため、欠落または不一致＝クロスサイト/非ブラウザとみなして拒否する。
 *
 * @returns 検証 NG なら 403 レスポンス、OK なら null
 */
export function rejectIfCrossOrigin(request: NextRequest): NextResponse | null {
  const origin = request.headers.get('origin')
  const host = request.headers.get('host')

  if (origin && host) {
    try {
      if (new URL(origin).host === host) return null
    } catch {
      // 不正な Origin はそのまま拒否へ
    }
  }

  return problemResponse(403, 'CSRF_VALIDATION_FAILED', 'リクエスト元の検証に失敗しました。')
}
