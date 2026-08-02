'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateAddress } from '@/lib/api/client/addresses'
import { queryKeys } from '@/lib/queryKeys'

/** 指定した住所をデフォルトにする。既存のデフォルトはサーバー側で解除される。 */
export function useSetDefaultAddressMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => updateAddress(id, { isDefault: true }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.user.addresses }),
  })
}
