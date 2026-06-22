import type { NextRequest } from 'next/server'
import { handleRefresh } from '@/lib/api/bff/handlers'

/** アクセストークン再発行（Token Rotation）。refresh_token Cookie を用い、新 Cookie を発行する。 */
export const POST = (req: NextRequest) => handleRefresh(req)
