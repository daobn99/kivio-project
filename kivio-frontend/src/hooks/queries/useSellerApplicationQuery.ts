'use client'
import { useQuery } from '@tanstack/react-query'
import { getMySellerApplication } from '@/lib/api/client/sellerApplications'
import { queryKeys } from '@/lib/queryKeys'

/**
 * 自分の申請状況。`data === null` が「未申請」を表す。
 *
 * `staleTime` は既定（0）のまま伸ばさない。審査は管理者側で進むため、
 * マウントのたびに最新を取りに行く価値がある。
 *
 * @param enabled BUYER 以外は画面を見せずリダイレクトするため、そもそも取得しない
 */
export function useSellerApplicationQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.sellerApplication.me,
    queryFn: getMySellerApplication,
    enabled,
  })
}
