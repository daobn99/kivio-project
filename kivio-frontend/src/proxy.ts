import { NextResponse, type NextRequest } from 'next/server'

// 認証必須ルート（未認証 → /auth/login へ）。トップレベルのパスプレフィックスで判定する。
// 一覧の正は docs/design/frontend/FRONTEND_IA.md §1.2 / §1.2b / §1.3。
const PROTECTED_PATHS = [
  '/cart',
  '/checkout',
  '/orders',
  '/profile',
  '/wishlist',
  '/messages',
  '/seller',
  '/admin',
]

// 未認証専用ルート（認証済み → / へ）。ログイン・会員登録は既ログインなら見せない。
const GUEST_ONLY_PATHS = ['/auth/login', '/auth/register']

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl
  // セッションの正は httpOnly Cookie の refresh_token（7日）。access_token（15分）は短命で
  // ナビゲーション中に失効しうるため、ソフトな認証ガードは長命の refresh_token の有無で判定する。
  // ミドルウェアはメモリ上の Zustand を参照できない。実際のアクセス制御はバックエンドが
  // Bearer 検証で行い、ここは UX 上の振り分け（ログイン誘導）のみを担う。
  const isAuthenticated = Boolean(request.cookies.get('refresh_token'))

  if (!isAuthenticated && PROTECTED_PATHS.some((p) => pathname.startsWith(p))) {
    // 復帰先を from に載せ、ログイン後に元の画面へ戻せるようにする
    return NextResponse.redirect(
      new URL(`/auth/login?from=${encodeURIComponent(pathname)}`, request.url),
    )
  }

  if (isAuthenticated && GUEST_ONLY_PATHS.some((p) => pathname.startsWith(p))) {
    return NextResponse.redirect(new URL('/', request.url))
  }

  return NextResponse.next()
}

export const config = {
  // _next 静的アセット・画像・favicon・public ディレクトリへのリクエストはプロキシをスキップする
  matcher: ['/((?!_next/static|_next/image|favicon.ico|public).*)'],
}
