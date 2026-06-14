'use client'
import { useAuthStore } from '@/stores/useAuthStore'
import { useAuthHydrated } from '@/hooks/useAuthHydrated'
import { GuestActions } from './GuestActions'
import { BuyerActions } from './BuyerActions'
import { SellerActions } from './SellerActions'
import { UserRole } from '@/types/enums'

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
