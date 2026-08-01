import { http, HttpResponse } from 'msw'
import type { Address } from '@/types/api'

export const mockAddresses: Address[] = [
  {
    id: '10000000-0000-0000-0000-000000000001',
    recipientName: '山田 太郎',
    postalCode: '150-0002',
    prefecture: '東京都',
    city: '渋谷区渋谷1-2-3',
    addressLine: 'Kivio ビル 4F',
    phoneNumber: '090-1234-5678',
    isDefault: true,
    createdAt: '2026-05-24T10:00:00Z',
  },
  {
    id: '10000000-0000-0000-0000-000000000002',
    recipientName: '山田 花子',
    postalCode: '530-0001',
    prefecture: '大阪府',
    city: '大阪市北区梅田3-1-1',
    addressLine: 'グランフロント 12F',
    phoneNumber: '06-1234-5678',
    isDefault: false,
    createdAt: '2026-06-01T10:00:00Z',
  },
]

/**
 * 配送先住所 API の MSW ハンドラ。
 * 一覧はサーバー側で「デフォルト先頭 → createdAt 昇順」に並んでいる前提の配列を返す。
 */
export const addressHandlers = [
  http.get('/api/v1/users/me/addresses', () => HttpResponse.json(mockAddresses)),

  http.post('/api/v1/users/me/addresses', async ({ request }) => {
    const body = (await request.json()) as Omit<Address, 'id' | 'createdAt'>
    return HttpResponse.json(
      { ...body, id: '10000000-0000-0000-0000-000000000009', createdAt: '2026-08-01T00:00:00Z' },
      { status: 201 },
    )
  }),

  http.patch('/api/v1/users/me/addresses/:id', async ({ request, params }) => {
    const body = (await request.json()) as Partial<Address>
    const target = mockAddresses.find((a) => a.id === params.id) ?? mockAddresses[0]
    return HttpResponse.json({ ...target, ...body })
  }),

  http.delete('/api/v1/users/me/addresses/:id', () => new HttpResponse(null, { status: 204 })),
]
