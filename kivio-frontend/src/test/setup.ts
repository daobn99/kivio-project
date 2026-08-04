import '@testing-library/jest-dom'
import { cleanup } from '@testing-library/react'
import { server } from './mocks/server'

// jsdom は matchMedia を実装していない。常に「不一致」を返すスタブを置き、
// 特定の幅を前提にするテストは各自 vi.stubGlobal で上書きする。
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())
