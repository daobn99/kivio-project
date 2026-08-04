'use client'
import { useQuery } from '@tanstack/react-query'
import { listAddresses } from '@/lib/api/client/addresses'
import { queryKeys } from '@/lib/queryKeys'

/**
 * 配送先住所の一覧。
 * 並び順（デフォルトを先頭 → 作成日昇順）はサーバーが確定させるため、受け取った順に描画する。
 */
export function useAddressesQuery() {
  return useQuery({
    queryKey: queryKeys.user.addresses,
    queryFn: listAddresses,
  })
}
