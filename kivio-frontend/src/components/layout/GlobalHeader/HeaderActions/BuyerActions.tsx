'use client'
import { ShoppingCart, MessageCircleMore } from 'lucide-react'
import type { AuthUser } from '@/types/api'
import { LanguageButton } from './LanguageButton'
import { IconButton } from './IconButton'
import { NotificationBell } from './NotificationBell'
import { UserMenu } from './UserMenu'

interface BuyerActionsProps {
  user: AuthUser
}

// Phase 2: バッジは 0 固定。Phase 3 以降で API 連携。お気に入りは UserMenu ドロップダウンへ移設。
export function BuyerActions({ user }: BuyerActionsProps) {
  return (
    <div className="flex items-center gap-1.5">
      <LanguageButton />
      <IconButton href="/cart" icon={ShoppingCart} label="カート" />
      <IconButton href="/messages" icon={MessageCircleMore} label="メッセージ" />
      <NotificationBell />

      {/* ユーティリティ群とアカウント群を視覚的に分ける縦罫線 */}
      <span aria-hidden className="bg-border mx-1 h-6 w-px" />

      <UserMenu user={user} />
    </div>
  )
}
