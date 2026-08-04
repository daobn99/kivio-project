import type { NextRequest } from 'next/server'
import { handleTokenMint } from '@/lib/api/bff/handlers'

/** 登録完了・自動ログイン。トークンを発行し httpOnly Cookie へ格納する（201 Created）。 */
export const POST = (req: NextRequest) => handleTokenMint(req, '/auth/register/complete')
