'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteAddress } from '@/lib/api/client/addresses'
import { queryKeys } from '@/lib/queryKeys'

export function useDeleteAddressMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => deleteAddress(id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.user.addresses }),
  })
}
