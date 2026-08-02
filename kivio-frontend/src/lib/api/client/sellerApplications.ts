'use client'
import { apiFetch } from '@/lib/api/client/base'
import { ApiError } from '@/lib/api/ApiError'
import type { CreateSellerApplicationRequest, SellerApplication } from '@/types/api'

/**
 * 自分の最新の申請 1 件。未申請なら `null`。
 *
 * 未申請はサーバーでは 404 だが、これは「まだ申請していない」という正常状態である。
 * ここで `null` に吸収しないと未申請ユーザー全員がエラー扱いになり、リトライまで走る。
 */
export async function getMySellerApplication(): Promise<SellerApplication | null> {
  try {
    return await apiFetch<SellerApplication>('/seller-applications/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null
    throw error
  }
}

/** 却下後の再申請も新規申請と同じ経路。常に新しいレコードが作られる。 */
export function createSellerApplication(
  payload: CreateSellerApplicationRequest,
): Promise<SellerApplication> {
  return apiFetch<SellerApplication>('/seller-applications', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
