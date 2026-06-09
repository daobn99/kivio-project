'use client'
import { Heart, ShoppingCart, MessageCircle } from 'lucide-react'
import { IconButton } from './IconButton'
import { NotificationBell } from './NotificationBell'
import { UserMenu } from './UserMenu'

interface AuthUser {
  id: string
  name: string
  email: string
  role: 'BUYER' | 'SELLER' | 'ADMIN'
  avatarUrl?: string
}

interface BuyerActionsProps {
  user: AuthUser
}

// Phase 2: バッジは 0 固定。Phase 3 以降で API 連携。
export function BuyerActions({ user }: BuyerActionsProps) {
  return (
    <div className="flex items-center gap-1">
      <IconButton href="/wishlist" icon={Heart} label="お気に入り" />
      <IconButton href="/cart" icon={ShoppingCart} label="カート" />
      <IconButton href="/messages" icon={MessageCircle} label="メッセージ" />
      <NotificationBell />
      <UserMenu role="BUYER" user={user} />
    </div>
  )
}
