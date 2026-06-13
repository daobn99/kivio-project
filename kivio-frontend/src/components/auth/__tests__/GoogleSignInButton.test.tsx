import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { signIn } from 'next-auth/react'
import { GoogleSignInButton } from '@/components/auth/GoogleSignInButton'

vi.mock('next-auth/react', () => ({ signIn: vi.fn() }))

beforeEach(() => {
  vi.mocked(signIn).mockClear()
})

describe('GoogleSignInButton', () => {
  it('クリックで signIn("google") が呼ばれる', async () => {
    const user = userEvent.setup()
    render(<GoogleSignInButton mode="login" />)

    await user.click(screen.getByRole('button', { name: 'Google で続行' }))

    expect(signIn).toHaveBeenCalledWith('google')
  })

  it('mode により登録ラベルを出し分ける', () => {
    render(<GoogleSignInButton mode="register" />)
    expect(screen.getByRole('button', { name: 'Google で登録' })).toBeInTheDocument()
  })
})
