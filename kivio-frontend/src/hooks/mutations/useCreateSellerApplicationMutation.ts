'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createSellerApplication } from '@/lib/api/client/sellerApplications'
import { ApiError } from '@/lib/api/ApiError'
import { queryKeys } from '@/lib/queryKeys'
import type { CreateSellerApplicationRequest, SellerApplication } from '@/types/api'

/**
 * 申請の送信。新規・再申請とも同じ `POST` で、常に新しいレコードが作られる。
 *
 * 409 は「他タブ・別デバイスで状態が進んだ」サインなので、成功時と同じく再取得して
 * 画面を実際の状態へ追随させる。一方 5xx・通信断では再取得しない — 失敗した取得結果で
 * 画面をエラー表示に落とすと、入力中のフォームごと失われるため。
 */
export function useCreateSellerApplicationMutation() {
  const queryClient = useQueryClient()
  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: queryKeys.sellerApplication.me })

  return useMutation<SellerApplication, unknown, CreateSellerApplicationRequest>({
    mutationFn: createSellerApplication,
    onSuccess: invalidate,
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) invalidate()
    },
  })
}
