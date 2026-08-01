import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { ProfileSettingsForm } from '@/components/profile/ProfileSettingsForm'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'
import { mockCurrentUser } from '@/test/mocks/handlers/users'
import { useAuthStore } from '@/stores/useAuthStore'
import type { AuthUser } from '@/types/api'

const currentUser = mockCurrentUser as AuthUser

function renderForm() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <ProfileSettingsForm />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  useAuthStore.setState({ accessToken: 'token', user: currentUser, isAuthenticated: true })
})

describe('ProfileSettingsForm', () => {
  it('未変更のあいだ保存ボタンは非活性', () => {
    renderForm()
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled()
  })

  it('表示名を変更して保存すると store が更新され完了表示が出る', async () => {
    const user = userEvent.setup()
    renderForm()

    const input = screen.getByLabelText('表示名')
    await user.clear(input)
    await user.type(input, '新しい名前')

    const submit = screen.getByRole('button', { name: '保存する' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByText('保存しました')).toBeInTheDocument()
    await waitFor(() => expect(useAuthStore.getState().user?.displayName).toBe('新しい名前'))
  })

  it('変更したフィールドのみ送信する（部分更新）', async () => {
    let body: unknown
    server.use(
      http.patch('/api/v1/users/me', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...currentUser, displayName: '新しい名前' })
      }),
    )
    const user = userEvent.setup()
    renderForm()

    const input = screen.getByLabelText('表示名')
    await user.clear(input)
    await user.type(input, '新しい名前')
    await user.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() => expect(body).toEqual({ displayName: '新しい名前' }))
  })

  it('表示名を空にするとバリデーションエラーを表示し送信しない', async () => {
    const patch = vi.fn()
    server.use(
      http.patch('/api/v1/users/me', () => {
        patch()
        return HttpResponse.json(currentUser)
      }),
    )
    const user = userEvent.setup()
    renderForm()

    await user.clear(screen.getByLabelText('表示名'))
    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('表示名を入力してください')).toBeInTheDocument()
    expect(patch).not.toHaveBeenCalled()
  })

  it('アバターの削除ボタンで空文字を送りクリアする', async () => {
    useAuthStore.setState({
      accessToken: 'token',
      user: { ...currentUser, avatarUrl: 'https://example.com/a.png' },
      isAuthenticated: true,
    })
    let body: unknown
    server.use(
      http.patch('/api/v1/users/me', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...currentUser, avatarUrl: null })
      }),
    )
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByRole('button', { name: 'アバター画像を削除' }))
    await user.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() => expect(body).toEqual({ avatarUrl: '' }))
    await waitFor(() => expect(useAuthStore.getState().user?.avatarUrl).toBeNull())
  })

  it('サーバーエラー時にアラートを表示する', async () => {
    server.use(
      http.patch('/api/v1/users/me', () =>
        HttpResponse.json(
          { title: 'エラー', status: 500, code: 'INTERNAL_ERROR', detail: '' },
          { status: 500 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderForm()

    const input = screen.getByLabelText('表示名')
    await user.clear(input)
    await user.type(input, '新しい名前')
    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
