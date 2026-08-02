import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { SellerApplicationView } from '@/components/seller/SellerApplicationView'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'
import { mockCurrentUser } from '@/test/mocks/handlers/users'
import {
  mockPendingApplication,
  mockRejectedApplication,
  problemResponse,
} from '@/test/mocks/handlers/sellerApplications'
import { useAuthStore } from '@/stores/useAuthStore'
import type { AuthUser, SellerApplication } from '@/types/api'
import type { UserRole } from '@/types/enums'

const replace = vi.fn()
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }))

// 復元前にロールを判定しない回帰を張るため、ハイドレート状態をテストから制御する
let hydrated = true
vi.mock('@/hooks/useAuthHydrated', () => ({ useAuthHydrated: () => hydrated }))

const ME = '/api/v1/seller-applications/me'
const APPLY = '/api/v1/seller-applications'

function renderView() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <SellerApplicationView />
    </QueryClientProvider>,
  )
}

function setUser(role: UserRole = 'ROLE_BUYER') {
  useAuthStore.setState({
    accessToken: 'token',
    user: { ...(mockCurrentUser as AuthUser), role },
    isAuthenticated: true,
  })
}

/** `GET /me` を固定のレスポンスに差し替える */
function respondWith(application: SellerApplication) {
  server.use(http.get(ME, () => HttpResponse.json(application)))
}

beforeEach(() => {
  hydrated = true
  replace.mockClear()
  setUser()
})

describe('SellerApplicationView', () => {
  it('未申請なら制度説明と申請フォームを出す', async () => {
    renderView()

    expect(await screen.findByText('出品者になるとできること')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '申請する' })).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it('審査中なら状況だけを出し、フォームは出さない', async () => {
    respondWith(mockPendingApplication)
    renderView()

    expect(await screen.findByText('審査中')).toBeInTheDocument()
    expect(screen.getByText(mockPendingApplication.reason)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /申請する/ })).toBeNull()
    expect(screen.queryByText('出品者になるとできること')).toBeNull()
  })

  it('却下なら却下理由と再申請フォームを出す', async () => {
    respondWith(mockRejectedApplication)
    renderView()

    expect(await screen.findByText('却下')).toBeInTheDocument()
    expect(screen.getByText('却下理由')).toBeInTheDocument()
    expect(screen.getByText(mockRejectedApplication.reviewComment!)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '再申請する' })).toBeInTheDocument()
    // 説得は済んでいるため制度説明は出さない
    expect(screen.queryByText('出品者になるとできること')).toBeNull()
  })

  it('却下理由が無い却下では理由ブロックを出さず、再申請の導線は残す', async () => {
    respondWith({ ...mockRejectedApplication, reviewComment: undefined })
    renderView()

    expect(await screen.findByText('却下')).toBeInTheDocument()
    expect(screen.queryByText('却下理由')).toBeNull()
    expect(screen.getByRole('button', { name: '再申請する' })).toBeInTheDocument()
  })

  it('承認済みならリダイレクトする', async () => {
    respondWith({ ...mockPendingApplication, status: 'APPROVED' })
    renderView()

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
    expect(screen.queryByRole('button', { name: /申請する/ })).toBeNull()
  })

  it.each(['ROLE_SELLER', 'ROLE_ADMIN'] as const)(
    '%s は取得すらせずリダイレクトする',
    async (role) => {
      const fetched = vi.fn()
      server.use(
        http.get(ME, () => {
          fetched()
          return problemResponse(404, 'RESOURCE_NOT_FOUND')
        }),
      )
      setUser(role)
      renderView()

      await waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
      expect(screen.queryByRole('button', { name: /申請する/ })).toBeNull()
      expect(fetched).not.toHaveBeenCalled()
    },
  )

  it('ストア復元前はロールを判定せず、リダイレクトしない', async () => {
    hydrated = false
    setUser('ROLE_SELLER')
    renderView()

    await waitFor(() => expect(replace).not.toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /申請する/ })).toBeNull()
  })

  it('取得に失敗したときはエラーと再読み込みボタンを出す', async () => {
    server.use(http.get(ME, () => problemResponse(500, 'INTERNAL_SERVER_ERROR')))
    renderView()

    expect(await screen.findByRole('alert')).toHaveTextContent('申請状況を取得できませんでした')
    expect(screen.getByRole('button', { name: '再読み込み' })).toBeInTheDocument()
  })

  it('再読み込みで取り直せる', async () => {
    let failed = false
    server.use(
      http.get(ME, () => {
        if (failed) return HttpResponse.json(mockPendingApplication)
        failed = true
        return problemResponse(500, 'INTERNAL_SERVER_ERROR')
      }),
    )
    const user = userEvent.setup()
    renderView()

    await user.click(await screen.findByRole('button', { name: '再読み込み' }))

    expect(await screen.findByText('審査中')).toBeInTheDocument()
  })

  it('空のまま送信するとエラーを出し、送信しない', async () => {
    const posted = vi.fn()
    server.use(
      http.post(APPLY, () => {
        posted()
        return HttpResponse.json(mockPendingApplication, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderView()

    await user.click(await screen.findByRole('button', { name: '申請する' }))

    expect(await screen.findByText('申請理由を入力してください')).toBeInTheDocument()
    expect(posted).not.toHaveBeenCalled()
  })

  it('1000文字を超えるとカウンターが超過を示し、送信されない', async () => {
    const posted = vi.fn()
    server.use(
      http.post(APPLY, () => {
        posted()
        return HttpResponse.json(mockPendingApplication, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderView()

    const textarea = await screen.findByLabelText('申請理由')
    // 1001 文字の入力を 1 度で流し込む（userEvent.type だと現実的な時間で終わらない）
    fireEvent.change(textarea, { target: { value: 'あ'.repeat(1001) } })

    expect(await screen.findByText('1001 / 1000')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '申請する' }))

    expect(await screen.findByText('申請理由は1000文字以内で入力してください')).toBeInTheDocument()
    expect(posted).not.toHaveBeenCalled()
  })

  it('送信すると POST し、画面が審査中に切り替わる', async () => {
    let body: unknown
    let submitted = false
    server.use(
      http.get(ME, () =>
        submitted
          ? HttpResponse.json(mockPendingApplication)
          : problemResponse(404, 'RESOURCE_NOT_FOUND'),
      ),
      http.post(APPLY, async ({ request }) => {
        body = await request.json()
        submitted = true
        return HttpResponse.json(mockPendingApplication, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderView()

    await user.type(await screen.findByLabelText('申請理由'), '手作りの器を販売したいです')
    await user.click(screen.getByRole('button', { name: '申請する' }))

    await waitFor(() => expect(body).toEqual({ reason: '手作りの器を販売したいです' }))
    expect(await screen.findByText('審査中')).toBeInTheDocument()
    expect(screen.getByText('申請を送信しました')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '申請する' })).toBeNull()
  })

  it('送信中は二重送信できない', async () => {
    server.use(http.post(APPLY, () => new Promise(() => {})))
    const user = userEvent.setup()
    renderView()

    await user.type(await screen.findByLabelText('申請理由'), '手作りの器を販売したいです')
    await user.click(screen.getByRole('button', { name: '申請する' }))

    await waitFor(() => expect(screen.getByRole('button', { name: '申請する' })).toBeDisabled())
  })

  it('409（審査中）は専用の文言を出し、画面を審査中へ追随させる', async () => {
    let submitted = false
    server.use(
      http.get(ME, () =>
        submitted
          ? HttpResponse.json(mockPendingApplication)
          : problemResponse(404, 'RESOURCE_NOT_FOUND'),
      ),
      http.post(APPLY, () => {
        submitted = true
        return problemResponse(409, 'SELLER_APPLICATION_PENDING')
      }),
    )
    const user = userEvent.setup()
    renderView()

    await user.type(await screen.findByLabelText('申請理由'), '手作りの器を販売したいです')
    await user.click(screen.getByRole('button', { name: '申請する' }))

    // フォームはアンマウントされるため、文言は親の通知帯に残っていなければならない
    expect(
      await screen.findByText('審査中の申請があります。結果が出るまでお待ちください。'),
    ).toBeInTheDocument()
    expect(await screen.findByText('審査中')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '申請する' })).toBeNull()
  })

  it('409（承認済み）は専用の文言を出す', async () => {
    server.use(http.post(APPLY, () => problemResponse(409, 'SELLER_APPLICATION_ALREADY_APPROVED')))
    const user = userEvent.setup()
    renderView()

    await user.type(await screen.findByLabelText('申請理由'), '手作りの器を販売したいです')
    await user.click(screen.getByRole('button', { name: '申請する' }))

    expect(await screen.findByText('すでに出品者として承認されています。')).toBeInTheDocument()
  })

  it('409 以外の送信失敗はフォーム内にエラーを出す', async () => {
    server.use(http.post(APPLY, () => problemResponse(500, 'INTERNAL_SERVER_ERROR')))
    const user = userEvent.setup()
    renderView()

    await user.type(await screen.findByLabelText('申請理由'), '手作りの器を販売したいです')
    await user.click(screen.getByRole('button', { name: '申請する' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    // 状態は変わらないのでフォームは残る
    expect(screen.getByRole('button', { name: '申請する' })).toBeInTheDocument()
  })
})
