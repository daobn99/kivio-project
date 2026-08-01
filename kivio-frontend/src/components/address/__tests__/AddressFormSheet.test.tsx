import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { AddressFormSheet } from '@/components/address/AddressFormSheet'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'
import { mockAddresses } from '@/test/mocks/handlers/addresses'
import type { Address } from '@/types/api'

const onOpenChange = vi.fn()

function renderSheet(address?: Address) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <AddressFormSheet open onOpenChange={onOpenChange} address={address} />
    </QueryClientProvider>,
  )
}

/** 全必須フィールドを埋める（都道府県は Select なので個別に選択する） */
async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('宛名'), '佐藤 次郎')
  await user.type(screen.getByLabelText('郵便番号'), '160-0022')
  await user.click(screen.getByRole('combobox'))
  await user.click(await screen.findByRole('option', { name: '東京都' }))
  await user.type(screen.getByLabelText('市区町村'), '新宿区新宿1-1-1')
  await user.type(screen.getByLabelText('番地・建物名'), 'カイビオ荘 101')
  await user.type(screen.getByLabelText('電話番号'), '090-0000-1111')
}

beforeEach(() => {
  onOpenChange.mockClear()
})

describe('AddressFormSheet', () => {
  it('新規追加モードでは空のフォームを「配送先を追加」として開く', () => {
    renderSheet()

    expect(screen.getByText('配送先を追加')).toBeInTheDocument()
    expect(screen.getByLabelText('宛名')).toHaveValue('')
  })

  it('入力して保存すると POST し、シートを閉じる', async () => {
    let body: unknown
    server.use(
      http.post('/api/v1/users/me/addresses', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...mockAddresses[0], id: 'new' }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderSheet()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() =>
      expect(body).toEqual({
        recipientName: '佐藤 次郎',
        postalCode: '160-0022',
        prefecture: '東京都',
        city: '新宿区新宿1-1-1',
        addressLine: 'カイビオ荘 101',
        phoneNumber: '090-0000-1111',
        isDefault: false,
      }),
    )
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })

  it('郵便番号の形式が不正ならバリデーションエラーを出し送信しない', async () => {
    const post = vi.fn()
    server.use(
      http.post('/api/v1/users/me/addresses', () => {
        post()
        return HttpResponse.json(mockAddresses[0], { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderSheet()

    await user.type(screen.getByLabelText('郵便番号'), '12345')
    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('郵便番号は7桁の数字で入力してください')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('編集モードでは既存値を初期表示し、変更したフィールドのみ PATCH する', async () => {
    let body: unknown
    server.use(
      http.patch('/api/v1/users/me/addresses/:id', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(mockAddresses[1])
      }),
    )
    const user = userEvent.setup()
    renderSheet(mockAddresses[1])

    expect(screen.getByText('配送先を編集')).toBeInTheDocument()
    const recipient = screen.getByLabelText('宛名')
    expect(recipient).toHaveValue('山田 花子')

    await user.clear(recipient)
    await user.type(recipient, '山田 花')
    await user.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() => expect(body).toEqual({ recipientName: '山田 花' }))
  })

  it('既にデフォルトの住所ではデフォルト設定を変更できない', () => {
    renderSheet(mockAddresses[0])

    // base-ui の Checkbox は span でレンダリングされるため aria-disabled で検証する
    expect(screen.getByRole('checkbox')).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByText(/他の住所をデフォルトに設定すると解除されます/)).toBeInTheDocument()
  })

  it('404（他タブで削除済み）のときはシートを閉じて一覧の再取得を促す', async () => {
    const onStaleError = vi.fn()
    server.use(
      http.patch('/api/v1/users/me/addresses/:id', () =>
        HttpResponse.json(
          { title: '見つかりません', status: 404, code: 'RESOURCE_NOT_FOUND', detail: '' },
          { status: 404 },
        ),
      ),
    )
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <AddressFormSheet
          open
          onOpenChange={onOpenChange}
          address={mockAddresses[1]}
          onStaleError={onStaleError}
        />
      </QueryClientProvider>,
    )

    const recipient = screen.getByLabelText('宛名')
    await user.clear(recipient)
    await user.type(recipient, '別の宛名')
    await user.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() => expect(onStaleError).toHaveBeenCalled())
  })
})
