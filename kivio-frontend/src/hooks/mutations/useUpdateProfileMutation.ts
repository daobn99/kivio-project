'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateProfile, type UpdateProfilePayload } from '@/lib/api/client/users'
import { queryKeys } from '@/lib/queryKeys'
import { useAuthStore } from '@/stores/useAuthStore'

/** 表示名・アバターの更新。成功時は Zustand も更新してヘッダー等へ即時反映する。 */
export function useUpdateProfileMutation() {
  const queryClient = useQueryClient()
  const setUser = useAuthStore((state) => state.setUser)

  return useMutation({
    mutationFn: (payload: UpdateProfilePayload) => updateProfile(payload),
    onSuccess: (updated) => {
      setUser(updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.user.me })
    },
  })
}
