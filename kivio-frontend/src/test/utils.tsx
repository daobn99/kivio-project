import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'

/** テスト用 QueryClient。リトライを無効化して失敗を即座に表面化させる。 */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

/** 単発レンダリング用。QueryClient を 1 度だけ生成して provider で包む。 */
export function renderWithQuery(ui: ReactElement, options?: RenderOptions) {
  const client = createTestQueryClient()
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>, options)
}

/**
 * renderHook 用 wrapper ファクトリ。QueryClient をテストごとに 1 度だけ生成する
 * （wrapper 関数内で生成すると再レンダリングのたびにキャッシュが消え waitFor が
 * 永久ループするため）。
 */
export function createQueryWrapper() {
  const client = createTestQueryClient()
  return function QueryWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
}
