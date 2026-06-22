'use client'
import { useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { logout } from '@/lib/api/client/auth'
import { useAuthStore } from '@/stores/useAuthStore'
import { ROUTES } from '@/lib/constants'
import type { AuthUser } from '@/types/api'

interface UserMenuProps {
  user: AuthUser
}

export function UserMenu({ user }: UserMenuProps) {
  const router = useRouter()
  const clearAuth = useAuthStore((state) => state.clearAuth)

  // クリック / キーボードに加えてホバーでも開く。Base UI Menu.Root は openOnHover を持たないため
  // 制御コンポーネント化し、トリガー・パネルの mouseenter/leave で open を制御する。
  const [open, setOpen] = useState(false)
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const cancelClose = () => {
    if (closeTimer.current) {
      clearTimeout(closeTimer.current)
      closeTimer.current = null
    }
  }
  // トリガーからパネルへポインタを移す猶予を設けてから閉じる（ホバーのちらつき防止）
  const scheduleClose = () => {
    cancelClose()
    closeTimer.current = setTimeout(() => setOpen(false), 120)
  }

  const handleLogout = async () => {
    // Refresh Token の無効化はベストエフォート。失敗してもクライアントの認証状態は必ず破棄する。
    try {
      await logout()
    } catch {
      // ネットワークエラー等は無視してローカルログアウトを優先する
    }
    clearAuth()
    router.replace(ROUTES.home)
  }

  const isBuyer = user.role === 'ROLE_BUYER'

  return (
    <DropdownMenu open={open} onOpenChange={setOpen} modal={false}>
      <DropdownMenuTrigger
        className="focus-visible:ring-ring cursor-pointer rounded-full focus-visible:ring-2 focus-visible:outline-none"
        aria-label="アカウントメニュー"
        onMouseEnter={() => {
          cancelClose()
          setOpen(true)
        }}
        onMouseLeave={scheduleClose}
      >
        <Avatar>
          <AvatarImage src={user.avatarUrl ?? ''} alt="" />
          <AvatarFallback>{user.displayName.charAt(0)}</AvatarFallback>
        </Avatar>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        className="min-w-48"
        onMouseEnter={cancelClose}
        onMouseLeave={scheduleClose}
      >
        {/* ユーザー情報ヘッダー。Menu.Group のラベルではなく単独の見出しなので GroupLabel は使わない */}
        <div className="px-3 py-2">
          <p className="text-foreground truncate text-sm font-medium">{user.displayName}</p>
          <p className="text-muted-foreground truncate text-xs">{user.email}</p>
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuItem render={<Link href="/profile/settings" />}>
          プロフィール設定
        </DropdownMenuItem>
        <DropdownMenuItem render={<Link href={ROUTES.buyer.orders} />}>
          注文履歴{isBuyer ? '' : '（バイヤーとして）'}
        </DropdownMenuItem>
        <DropdownMenuItem render={<Link href={ROUTES.buyer.wishlist} />}>
          お気に入り
        </DropdownMenuItem>
        {isBuyer && (
          <DropdownMenuItem render={<Link href="/seller/applications/new" />}>
            セラー申請
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onClick={handleLogout}>
          ログアウト
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
