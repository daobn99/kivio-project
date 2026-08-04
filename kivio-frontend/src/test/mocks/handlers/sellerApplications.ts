import { http, HttpResponse } from 'msw'
import type { SellerApplication } from '@/types/api'

const APPLICANT_ID = '00000000-0000-0000-0000-000000000001'

export const mockPendingApplication: SellerApplication = {
  id: '50000000-0000-0000-0000-000000000001',
  applicantId: APPLICANT_ID,
  reason: 'ハンドメイドのアクセサリーを販売したいです。',
  status: 'PENDING',
  createdAt: '2026-08-02T01:00:00Z',
}

export const mockRejectedApplication: SellerApplication = {
  id: '50000000-0000-0000-0000-000000000002',
  applicantId: APPLICANT_ID,
  reason: '雑貨を販売したいです。',
  status: 'REJECTED',
  reviewComment: '申請内容から取り扱い商品を判断できませんでした。',
  reviewedAt: '2026-08-01T02:00:00Z',
  createdAt: '2026-07-30T01:00:00Z',
}

/** RFC 9457 ProblemDetail。未申請の 404 もこの形で返る */
export function problemResponse(status: number, code: string) {
  return HttpResponse.json({ title: 'エラー', status, code, detail: '' }, { status })
}

/**
 * セラー申請 API の MSW ハンドラ。
 * 既定は「未申請」（404）。他の 3 状態は各テストが server.use() で上書きする。
 */
export const sellerApplicationHandlers = [
  http.get('/api/v1/seller-applications/me', () => problemResponse(404, 'RESOURCE_NOT_FOUND')),

  http.post('/api/v1/seller-applications', async ({ request }) => {
    const { reason } = (await request.json()) as { reason: string }
    return HttpResponse.json({ ...mockPendingApplication, reason }, { status: 201 })
  }),
]
