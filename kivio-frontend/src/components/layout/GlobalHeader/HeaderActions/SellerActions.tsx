'use client'
import Link from 'next/link'
import { ShoppingCart, Store, MessageCircleMore } from 'lucide-react'
import { cn } from '@/lib/utils'
import { buttonVariants } from '@/components/ui/button'
import type { AuthUser } from '@/types/api'
import { LanguageButton } from './LanguageButton'
import { IconButton } from './IconButton'
import { NotificationBell } from './NotificationBell'
import { UserMenu } from './UserMenu'

interface SellerActionsProps {
  user: AuthUser
}

export function SellerActions({ user }: SellerActionsProps) {
  return (
    <div className="flex items-center gap-1.5">
      {/* ユーティリティ群（言語・カート・メッセージ・通知） */}
      <LanguageButton />
      <IconButton href="/cart" icon={ShoppingCart} label="カート" />
      <IconButton href="/messages" icon={MessageCircleMore} label="メッセージ" />
      <NotificationBell />

      {/* ユーティリティ群とアカウント群を視覚的に分ける縦罫線 */}
      <span aria-hidden className="bg-border mx-1 h-6 w-px" />

      {/* アカウント群（ダッシュボードショートカット + アバターメニュー） */}
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
      <UserMenu user={user} />
    </div>
  )
}
