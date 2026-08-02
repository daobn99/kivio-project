import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { PasswordSection } from '@/components/profile/PasswordSection'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'
import { mockCurrentUser } from '@/test/mocks/handlers/users'
import { useAuthStore } from '@/stores/useAuthStore'
import type { AuthUser } from '@/types/api'

const currentUser = mockCurrentUser as AuthUser

function renderSection() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <PasswordSection />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  useAuthStore.setState({ accessToken: 'token', user: currentUser, isAuthenticated: true })
})

describe('PasswordSection', () => {
  it('パスワード変更に成功すると完了表示が出て入力が消える', async () => {
    const user = userEvent.setup()
    renderSection()

    await user.type(screen.getByLabelText('現在のパスワード'), 'CurrentPass1')
    await user.type(screen.getByLabelText('新しいパスワード'), 'NewPassword1')
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }))

    expect(await screen.findByText('パスワードを変更しました')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('現在のパスワード')).toHaveValue(''))
    expect(screen.getByLabelText('新しいパスワード')).toHaveValue('')
  })

  it('PASSWORD_CHANGE_FAILED（400）でエラーメッセージを表示する', async () => {
    server.use(
      http.patch('/api/v1/users/me/password', () =>
        HttpResponse.json(
          { title: '変更失敗', status: 400, code: 'PASSWORD_CHANGE_FAILED', detail: '' },
          { status: 400 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderSection()

    await user.type(screen.getByLabelText('現在のパスワード'), 'WrongPass1')
    await user.type(screen.getByLabelText('新しいパスワード'), 'NewPassword1')
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }))

    expect(await screen.findByText('現在のパスワードが正しくありません。')).toBeInTheDocument()
  })

  it('8文字未満の新しいパスワードはバリデーションで弾く', async () => {
    const user = userEvent.setup()
    renderSection()

    await user.type(screen.getByLabelText('現在のパスワード'), 'CurrentPass1')
    await user.type(screen.getByLabelText('新しいパスワード'), 'short')
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }))

    expect(await screen.findByText('パスワードは8文字以上で入力してください')).toBeInTheDocument()
  })

  it('Google ログイン専用ユーザー（hasPassword: false）にはフォームを出さない', () => {
    useAuthStore.setState({
      accessToken: 'token',
      user: { ...currentUser, hasPassword: false },
      isAuthenticated: true,
    })
    renderSection()

    expect(screen.queryByLabelText('現在のパスワード')).not.toBeInTheDocument()
    expect(screen.getByText(/Google\s*でログインしています/)).toBeInTheDocument()
  })

  it('hasPassword が未取得（undefined）のときはフォームを出す', () => {
    // 古い形式の永続化データ・hasPassword を返さないバックエンドを想定する
    useAuthStore.setState({
      accessToken: 'token',
      user: { ...currentUser, hasPassword: undefined as unknown as boolean },
      isAuthenticated: true,
    })
    renderSection()

    expect(screen.getByLabelText('現在のパスワード')).toBeInTheDocument()
    expect(screen.queryByText(/Google\s*でログインしています/)).not.toBeInTheDocument()
  })

  it('パスワードの表示トグルで input type が切り替わる', async () => {
    const user = userEvent.setup()
    renderSection()

    const toggles = screen.getAllByRole('button', { name: 'パスワードを表示' })
    expect(screen.getByLabelText('現在のパスワード')).toHaveAttribute('type', 'password')

    await user.click(toggles[0])

    expect(screen.getByLabelText('現在のパスワード')).toHaveAttribute('type', 'text')
  })
})
