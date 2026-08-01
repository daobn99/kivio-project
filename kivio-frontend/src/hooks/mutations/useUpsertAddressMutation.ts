'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createAddress, updateAddress } from '@/lib/api/client/addresses'
import { queryKeys } from '@/lib/queryKeys'
import type { Address, CreateAddressRequest, UpdateAddressRequest } from '@/types/api'

/**
 * 配送先住所の追加・更新。追加と編集はフィールド構成が同じで UI も共通のため 1 つにまとめる。
 *
 * @param addressId 更新対象の ID。未指定なら追加（`POST`）になる。
 */
export function useUpsertAddressMutation(addressId?: string) {
  const queryClient = useQueryClient()

  return useMutation<Address, unknown, CreateAddressRequest | UpdateAddressRequest>({
    mutationFn: (payload) =>
      addressId
        ? updateAddress(addressId, payload)
        : createAddress(payload as CreateAddressRequest),
    // 失敗理由がサーバー側の状態変化（他タブでの削除等）のこともあるため成否によらず再取得する
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.user.addresses }),
  })
}
