import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { WithdrawDialog } from '@/components/profile/WithdrawDialog'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'
import { mockCurrentUser } from '@/test/mocks/handlers/users'
import { useAuthStore } from '@/stores/useAuthStore'
import type { AuthUser } from '@/types/api'

const replace = vi.fn()
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }))

const currentUser = mockCurrentUser as AuthUser

function renderDialog() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <WithdrawDialog />
    </QueryClientProvider>,
  )
}

/** ダイアログを開き、実行ボタンを返す */
async function openDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: '退会する' }))
  const dialog = await screen.findByRole('alertdialog')
  return dialog
}

beforeEach(() => {
  replace.mockClear()
  useAuthStore.setState({ accessToken: 'token', user: currentUser, isAuthenticated: true })
})

describe('WithdrawDialog', () => {
  it('同意チェックを入れるまで実行ボタンは非活性', async () => {
    const user = userEvent.setup()
    renderDialog()

    const dialog = await openDialog(user)
    const execute = within(dialog).getByRole('button', { name: '退会する' })
    expect(execute).toBeDisabled()

    await user.click(within(dialog).getByRole('checkbox'))

    expect(execute).toBeEnabled()
  })

  it('退会に成功すると認証状態を破棄してトップへ遷移する', async () => {
    const user = userEvent.setup()
    renderDialog()

    const dialog = await openDialog(user)
    await user.click(within(dialog).getByRole('checkbox'))
    await user.click(within(dialog).getByRole('button', { name: '退会する' }))

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('失敗時はダイアログを閉じずエラーを表示する', async () => {
    server.use(
      http.delete('/api/v1/users/me', () =>
        HttpResponse.json(
          { title: 'エラー', status: 500, code: 'INTERNAL_ERROR', detail: '' },
          { status: 500 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderDialog()

    const dialog = await openDialog(user)
    await user.click(within(dialog).getByRole('checkbox'))
    await user.click(within(dialog).getByRole('button', { name: '退会する' }))

    expect(await within(dialog).findByRole('alert')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
  })

  it('確認ダイアログに対象アカウントのメールアドレスを表示する', async () => {
    const user = userEvent.setup()
    renderDialog()

    const dialog = await openDialog(user)

    expect(within(dialog).getByText(/user@example\.com/)).toBeInTheDocument()
  })
})
