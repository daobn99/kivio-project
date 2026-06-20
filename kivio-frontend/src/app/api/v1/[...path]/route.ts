import type { NextRequest } from 'next/server'
import { proxyToBackend } from '@/lib/api/bff/handlers'

/**
 * `/api/v1/*` の BFF データプロキシ（catch-all）。
 * access_token Cookie を Bearer に詰め替えてバックエンドへ転送する。
 * トークンを発行・破棄する `/api/v1/auth/{login,google,register/complete,refresh,logout}`
 * は、より具体的なルートが本ハンドラより優先される。
 */
type Ctx = { params: Promise<{ path: string[] }> }

export const GET = (req: NextRequest, ctx: Ctx) => proxyToBackend(req, ctx)
export const POST = (req: NextRequest, ctx: Ctx) => proxyToBackend(req, ctx)
export const PATCH = (req: NextRequest, ctx: Ctx) => proxyToBackend(req, ctx)
export const PUT = (req: NextRequest, ctx: Ctx) => proxyToBackend(req, ctx)
export const DELETE = (req: NextRequest, ctx: Ctx) => proxyToBackend(req, ctx)
