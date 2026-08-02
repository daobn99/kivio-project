'use client'
import { useMutation } from '@tanstack/react-query'
import { changePassword, type ChangePasswordPayload } from '@/lib/api/client/users'

/** パスワード変更。204 が返るだけでキャッシュには影響しない。 */
export function useChangePasswordMutation() {
  return useMutation({
    mutationFn: (payload: ChangePasswordPayload) => changePassword(payload),
  })
}
