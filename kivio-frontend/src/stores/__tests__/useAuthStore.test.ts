import { act } from '@testing-library/react'
import { useAuthStore } from '@/stores/useAuthStore'
import type { AuthUser } from '@/types/api'

const mockUser: AuthUser = {
  id: '1',
  email: 'user@example.com',
  displayName: 'テスト太郎',
  avatarUrl: null,
  role: 'ROLE_BUYER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
}

beforeEach(() => {
  // persist 永続化分も含めて完全初期化する
  useAuthStore.setState({ accessToken: null, user: null, isAuthenticated: false })
})

describe('useAuthStore', () => {
  it('setAuth を呼ぶと isAuthenticated が true になり accessToken と user が入る', () => {
    act(() => useAuthStore.getState().setAuth({ accessToken: 'token-1', user: mockUser }))
    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(true)
    expect(state.accessToken).toBe('token-1')
    expect(state.user).toEqual(mockUser)
  })

  it('setAccessToken を呼ぶと accessToken のみ更新される', () => {
    act(() => useAuthStore.getState().setAccessToken('token-2'))
    const state = useAuthStore.getState()
    expect(state.accessToken).toBe('token-2')
    expect(state.user).toBeNull()
  })

  it('clearAuth を呼ぶと認証状態が全消去される', () => {
    useAuthStore.setState({ accessToken: 'token-1', user: mockUser, isAuthenticated: true })
    act(() => useAuthStore.getState().clearAuth())
    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(false)
    expect(state.accessToken).toBeNull()
    expect(state.user).toBeNull()
  })
})
