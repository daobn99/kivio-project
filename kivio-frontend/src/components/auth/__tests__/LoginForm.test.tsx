import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { LoginForm } from '@/components/auth/LoginForm'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'
import { useAuthStore } from '@/stores/useAuthStore'

const replace = vi.fn()
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }))
vi.mock('next-auth/react', () => ({ signIn: vi.fn() }))

function renderLoginForm() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <LoginForm />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  replace.mockClear()
  useAuthStore.setState({ accessToken: null, user: null, isAuthenticated: false })
})

describe('LoginForm', () => {
  it('空フォームを送信するとバリデーションエラーが表示される', async () => {
    const user = userEvent.setup()
    renderLoginForm()

    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByText('メールアドレスの形式が正しくありません')).toBeInTheDocument()
    expect(await screen.findByText('パスワードを入力してください')).toBeInTheDocument()
  })

  it('有効な入力で送信すると accessToken が保存されホームへ遷移する', async () => {
    const user = userEvent.setup()
    renderLoginForm()

    await user.type(screen.getByLabelText('メールアドレス'), 'user@example.com')
    await user.type(screen.getByLabelText('パスワード'), 'password123')
    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
    expect(useAuthStore.getState().accessToken).toBe('test-access-token')
  })

  it('API エラー（401 INVALID_CREDENTIALS）時にエラーメッセージが表示される', async () => {
    server.use(
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json(
          { title: '認証失敗', status: 401, code: 'INVALID_CREDENTIALS', detail: '' },
          { status: 401 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderLoginForm()

    await user.type(screen.getByLabelText('メールアドレス'), 'user@example.com')
    await user.type(screen.getByLabelText('パスワード'), 'wrongpassword')
    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(
      await screen.findByText('メールアドレスまたはパスワードが正しくありません。'),
    ).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })
})
