import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { RegisterFlow } from '@/components/auth/RegisterFlow'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'

vi.mock('next/navigation', () => ({ useRouter: () => ({ replace: vi.fn() }) }))
vi.mock('next-auth/react', () => ({ signIn: vi.fn() }))

function renderFlow() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RegisterFlow />
    </QueryClientProvider>,
  )
}

/** Step1（メール）→ Step2（OTP）へ進める */
async function advanceToOtpStep(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('メールアドレス'), 'new@example.com')
  await user.click(screen.getByRole('button', { name: '認証コードを送信' }))
  expect(await screen.findByRole('heading', { name: '認証コードを入力' })).toBeInTheDocument()
}

/** OTP 6桁を貼り付ける（OtpInput はペースト対応） */
async function pasteOtp(user: ReturnType<typeof userEvent.setup>, code: string) {
  const firstCell = screen.getByLabelText('認証コード 1桁目')
  await user.click(firstCell)
  await user.paste(code)
}

describe('RegisterFlow', () => {
  it('Step1: メール入力 → request-otp 成功で Step2 へ遷移する', async () => {
    const user = userEvent.setup()
    renderFlow()

    expect(screen.getByRole('heading', { name: 'Kivioをはじめよう' })).toBeInTheDocument()
    await advanceToOtpStep(user)
    expect(screen.getByText('new@example.com')).toBeInTheDocument()
  })

  it('Step2: 6桁OTP入力 → verify-otp 成功で Step3（パスワード設定）へ遷移する', async () => {
    const user = userEvent.setup()
    renderFlow()

    await advanceToOtpStep(user)
    await pasteOtp(user, '123456')

    expect(await screen.findByRole('heading', { name: 'パスワードを設定' })).toBeInTheDocument()
  })

  it('Step2: OTP_INVALID で残り回数を表示し再入力できる', async () => {
    server.use(
      http.post('/api/v1/auth/register/verify-otp', () =>
        HttpResponse.json(
          { title: 'コード不正', status: 400, code: 'OTP_INVALID', detail: '' },
          { status: 400 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderFlow()

    await advanceToOtpStep(user)
    await pasteOtp(user, '000000')

    expect(await screen.findByText('認証コードが正しくありません（残り4回）')).toBeInTheDocument()
    // 失効していないため入力欄は有効なまま
    expect(screen.getByLabelText('認証コード 1桁目')).not.toBeDisabled()
  })

  it('Step3: パスワード不一致でバリデーションエラーになる', async () => {
    const user = userEvent.setup()
    renderFlow()

    await advanceToOtpStep(user)
    await pasteOtp(user, '123456')
    await screen.findByRole('heading', { name: 'パスワードを設定' })

    await user.type(screen.getByLabelText('表示名'), 'テスト太郎')
    await user.type(screen.getByLabelText('パスワード'), 'password123')
    await user.type(screen.getByLabelText('パスワード（確認用）'), 'password999')
    await user.click(screen.getByRole('button', { name: '登録して始める' }))

    expect(
      await screen.findByText('パスワードと確認用パスワードが一致しません'),
    ).toBeInTheDocument()
  })
})
