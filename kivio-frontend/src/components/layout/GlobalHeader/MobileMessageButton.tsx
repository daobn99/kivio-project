'use client'
import Link from 'next/link'
import { MessageCircleMore } from 'lucide-react'
import { cn } from '@/lib/utils'
import { buttonVariants } from '@/components/ui/button'
import { useAuthStore } from '@/stores/useAuthStore'
import { useAuthHydrated } from '@/hooks/useAuthHydrated'

/**
 * モバイルヘッダーのメッセージ導線。
 * デスクトップの BuyerActions/SellerActions と同様、ログイン時のみ表示する（ゲストには出さない）。
 * 復元完了前は描画しないことで hydration mismatch を避ける。
 */
export function MobileMessageButton() {
  const hydrated = useAuthHydrated()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  if (!hydrated || !isAuthenticated) return null

  return (
    <Link
      href="/messages"
      aria-label="メッセージ"
      className={cn(buttonVariants({ variant: 'ghost', size: 'icon' }))}
    >
      <MessageCircleMore className="size-5" />
    </Link>
  )
}
