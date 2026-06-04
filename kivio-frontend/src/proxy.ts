import { NextResponse, type NextRequest } from 'next/server'

const PROTECTED_PATHS = [
  '/cart',
  '/checkout',
  '/orders',
  '/seller',
  '/admin',
  '/wishlist',
  '/messages',
]

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl
  const token = request.cookies.get('access_token')

  if (PROTECTED_PATHS.some((p) => pathname.startsWith(p)) && !token) {
    return NextResponse.redirect(
      new URL(`/auth/login?from=${encodeURIComponent(pathname)}`, request.url),
    )
  }
  return NextResponse.next()
}

export const config = {
  // _next 静的アセット・画像・favicon・public ディレクトリへのリクエストはプロキシをスキップする
  matcher: ['/((?!_next/static|_next/image|favicon.ico|public).*)'],
}
