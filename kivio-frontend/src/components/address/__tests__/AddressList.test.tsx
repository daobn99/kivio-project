import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { AddressList } from '@/components/address/AddressList'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'
import { mockAddresses } from '@/test/mocks/handlers/addresses'

function renderList() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <AddressList />
    </QueryClientProvider>,
  )
}

describe('AddressList', () => {
  it('住所一覧を表示し、デフォルト住所にバッジを出す', async () => {
    renderList()

    expect(await screen.findByText('山田 太郎')).toBeInTheDocument()
    expect(screen.getByText('山田 花子')).toBeInTheDocument()
    expect(screen.getByText('〒150-0002')).toBeInTheDocument()
    expect(screen.getByText('デフォルト')).toBeInTheDocument()
  })

  it('デフォルト住所には「デフォルトにする」を出さない', async () => {
    renderList()

    const defaultCard = (await screen.findByText('山田 太郎')).closest('article')!
    const otherCard = screen.getByText('山田 花子').closest('article')!

    expect(within(defaultCard).queryByRole('button', { name: /デフォルトにする/ })).toBeNull()
    expect(within(otherCard).getByRole('button', { name: /デフォルトにする/ })).toBeInTheDocument()
  })

  it('「デフォルトにする」で isDefault: true を PATCH する', async () => {
    let body: unknown
    let patchedId: string | undefined
    server.use(
      http.patch('/api/v1/users/me/addresses/:id', async ({ request, params }) => {
        body = await request.json()
        patchedId = params.id as string
        return HttpResponse.json({ ...mockAddresses[1], isDefault: true })
      }),
    )
    const user = userEvent.setup()
    renderList()

    const otherCard = (await screen.findByText('山田 花子')).closest('article')!
    await user.click(within(otherCard).getByRole('button', { name: /デフォルトにする/ }))

    await waitFor(() => expect(body).toEqual({ isDefault: true }))
    expect(patchedId).toBe(mockAddresses[1].id)
  })

  it('住所が 0 件のとき空状態を表示する', async () => {
    server.use(http.get('/api/v1/users/me/addresses', () => HttpResponse.json([])))
    renderList()

    expect(await screen.findByText('配送先住所がまだ登録されていません')).toBeInTheDocument()
  })

  it('取得に失敗したときはエラーと再読み込みボタンを表示する', async () => {
    server.use(
      http.get('/api/v1/users/me/addresses', () =>
        HttpResponse.json(
          { title: 'エラー', status: 500, code: 'INTERNAL_ERROR', detail: '' },
          { status: 500 },
        ),
      ),
    )
    renderList()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '再読み込み' })).toBeInTheDocument()
  })

  it('削除ボタンで確認ダイアログを開き、確定すると DELETE する', async () => {
    let deletedId: string | undefined
    server.use(
      http.delete('/api/v1/users/me/addresses/:id', ({ params }) => {
        deletedId = params.id as string
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    renderList()

    const card = (await screen.findByText('山田 花子')).closest('article')!
    await user.click(within(card).getByRole('button', { name: /配送先を削除/ }))

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('この配送先を削除しますか？')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: '削除する' }))

    await waitFor(() => expect(deletedId).toBe(mockAddresses[1].id))
  })
})
