import type { NextRequest } from 'next/server'
import { handleLogout } from '@/lib/api/bff/handlers'

/** ログアウト。refresh_token を失効させ、認証 Cookie を破棄する（204 No Content）。 */
export const POST = (req: NextRequest) => handleLogout(req)
