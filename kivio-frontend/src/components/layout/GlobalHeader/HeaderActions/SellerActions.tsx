'use client'
import Link from 'next/link'
import { Heart, ShoppingCart, MessageCircle, Store } from 'lucide-react'
import { cn } from '@/lib/utils'
import { buttonVariants } from '@/components/ui/button'
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

interface SellerActionsProps {
  user: AuthUser
}

export function SellerActions({ user }: SellerActionsProps) {
  return (
    <div className="flex items-center gap-1">
      <IconButton href="/wishlist" icon={Heart} label="お気に入り" />
      <IconButton href="/cart" icon={ShoppingCart} label="カート" />
      <IconButton href="/messages" icon={MessageCircle} label="メッセージ" />
      <NotificationBell />
      <Link
        href="/seller/dashboard"
        className={cn(
          buttonVariants({ variant: 'outline', size: 'sm' }),
          'border-primary text-primary hover:bg-secondary hidden font-medium xl:flex',
        )}
      >
        <Store className="mr-1.5 h-4 w-4" />
        ダッシュボード
      </Link>
      <UserMenu role="SELLER" user={user} />
    </div>
  )
}
