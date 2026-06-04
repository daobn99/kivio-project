'use client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'

export default function QueryProvider({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // 1分以内の再フェッチを抑制してバックエンドへの無駄なリクエストを減らす
            staleTime: 1000 * 60,
            // ネットワーク瞬断を考慮して1回だけリトライ。それ以上はユーザーへエラーを見せる
            retry: 1,
          },
        },
      }),
  )

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
