'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { withdrawAccount } from '@/lib/api/client/users'
import { useAuthStore } from '@/stores/useAuthStore'

/** 退会。成功したら認証状態とキャッシュを完全に捨てる（前ユーザーの情報を残さない）。 */
export function useWithdrawAccountMutation() {
  const queryClient = useQueryClient()
  const clearAuth = useAuthStore((state) => state.clearAuth)

  return useMutation({
    mutationFn: withdrawAccount,
    onSuccess: () => {
      clearAuth()
      queryClient.clear()
    },
  })
}
