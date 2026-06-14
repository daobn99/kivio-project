'use client'
import { useSyncExternalStore } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { GuestActions } from './GuestActions'
import { BuyerActions } from './BuyerActions'
import { SellerActions } from './SellerActions'
import { UserRole } from '@/types/enums'

// Zustand persist の復元完了をハイドレーション安全に購読する。
// SSR / 初回ハイドレーションは false（未認証として描画）、復元完了後に true へ切り替わり、
// hydration mismatch を起こさずに永続化された認証状態へ移行する。
function useAuthHydrated() {
  return useSyncExternalStore(
    (onStoreChange) => useAuthStore.persist.onFinishHydration(onStoreChange),
    () => useAuthStore.persist.hasHydrated(),
    () => false,
  )
}

export function HeaderActions() {
  const hydrated = useAuthHydrated()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const user = useAuthStore((state) => state.user)

  if (!hydrated || !isAuthenticated || !user) {
    return <GuestActions />
  }

  if (user.role === UserRole.ROLE_SELLER) {
    return <SellerActions user={user} />
  }

  // ROLE_ADMIN は専用の AdminHeader を使うため、グローバルヘッダーでは BUYER と同じ表示で足りる
  return <BuyerActions user={user} />
}
