import type { NextRequest } from 'next/server'
import { handleTokenMint } from '@/lib/api/bff/handlers'

/** ログイン。トークンを発行し httpOnly Cookie へ格納する。 */
export const POST = (req: NextRequest) => handleTokenMint(req, '/auth/login')
