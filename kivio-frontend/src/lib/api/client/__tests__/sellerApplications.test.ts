import { http, HttpResponse } from 'msw'
import { getMySellerApplication } from '@/lib/api/client/sellerApplications'
import { ApiError } from '@/lib/api/ApiError'
import { server } from '@/test/mocks/server'
import { mockPendingApplication, problemResponse } from '@/test/mocks/handlers/sellerApplications'

describe('getMySellerApplication', () => {
  it('未申請（404）はエラーではなく null を返す', async () => {
    await expect(getMySellerApplication()).resolves.toBeNull()
  })

  it('申請があればそのまま返す', async () => {
    server.use(
      http.get('/api/v1/seller-applications/me', () => HttpResponse.json(mockPendingApplication)),
    )

    await expect(getMySellerApplication()).resolves.toEqual(mockPendingApplication)
  })

  it('404 以外は握りつぶさず ApiError を投げる', async () => {
    server.use(
      http.get('/api/v1/seller-applications/me', () =>
        problemResponse(500, 'INTERNAL_SERVER_ERROR'),
      ),
    )

    await expect(getMySellerApplication()).rejects.toBeInstanceOf(ApiError)
  })
})
