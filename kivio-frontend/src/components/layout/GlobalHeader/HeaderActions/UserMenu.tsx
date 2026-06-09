'use client'
import Link from 'next/link'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  DropdownMenuLabel,
} from '@/components/ui/dropdown-menu'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'

interface AuthUser {
  id: string
  name: string
  email: string
  role: 'BUYER' | 'SELLER' | 'ADMIN'
  avatarUrl?: string
}

interface UserMenuProps {
  role: 'BUYER' | 'SELLER'
  user: AuthUser
  onLogout?: () => void
}

export function UserMenu({ role, user, onLogout }: UserMenuProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className="focus-visible:ring-ring rounded-full focus-visible:ring-2 focus-visible:outline-none"
        aria-label="アカウントメニュー"
      >
        <Avatar>
          <AvatarImage src={user.avatarUrl ?? ''} alt="" />
          <AvatarFallback>{user.name.charAt(0)}</AvatarFallback>
        </Avatar>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-48">
        <DropdownMenuLabel className="px-3 py-2">
          <p className="text-foreground truncate text-sm font-medium">{user.name}</p>
          <p className="text-muted-foreground truncate text-xs">{user.email}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem render={<Link href="/profile/settings" />}>
          プロフィール設定
        </DropdownMenuItem>
        <DropdownMenuItem render={<Link href="/orders" />}>
          注文履歴{role === 'SELLER' ? '（バイヤーとして）' : ''}
        </DropdownMenuItem>
        {role === 'BUYER' && (
          <DropdownMenuItem render={<Link href="/seller/applications/new" />}>
            セラー申請
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onClick={onLogout}>
          ログアウト
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
