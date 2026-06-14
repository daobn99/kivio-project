'use client'
import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Heart, LayoutDashboard, LogOut, Menu, Package, Store, Tag, User } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import {
  Sheet,
  SheetContent,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/useAuthStore'
import { useAuthHydrated } from '@/hooks/useAuthHydrated'
import { logout } from '@/lib/api/client/auth'
import { ROUTES } from '@/lib/constants'
import { UserRole } from '@/types/enums'
import type { AuthUser } from '@/types/api'

const categories = [
  { label: 'ファッション', href: '/search?category=fashion' },
  { label: '家電・スマホ', href: '/search?category=electronics' },
  { label: '本・CD・DVD', href: '/search?category=books' },
  { label: 'おもちゃ・趣味', href: '/search?category=hobbies' },
  { label: 'スポーツ・アウトドア', href: '/search?category=sports' },
  { label: 'インテリア・家具', href: '/search?category=interior' },
]

interface NavItemProps {
  href: string
  icon: LucideIcon
  label: string
  onNavigate: () => void
}

// メニュー内ナビゲーションリンク。アイコン付き・44px 前後のタップ領域・遷移時に Sheet を閉じる。
function NavItem({ href, icon: Icon, label, onNavigate }: NavItemProps) {
  return (
    <Link
      href={href}
      onClick={onNavigate}
      className="text-foreground hover:bg-muted flex items-center gap-3 rounded-md px-3 py-2.5 text-sm transition-colors duration-150"
    >
      <Icon className="text-muted-foreground size-4.5 shrink-0" />
      {label}
    </Link>
  )
}

interface AccountSectionProps {
  user: AuthUser
  onNavigate: () => void
}

// ログイン済みのアカウント領域（プロフィールヘッダー + 各種導線）。役割で出し分ける。
function AccountSection({ user, onNavigate }: AccountSectionProps) {
  const isSeller = user.role === UserRole.ROLE_SELLER

  return (
    <>
      {/* プロフィールヘッダー */}
      <div className="flex items-center gap-3 px-4 py-4">
        <Avatar size="lg">
          <AvatarImage src={user.avatarUrl ?? ''} alt="" />
          <AvatarFallback>{user.displayName.charAt(0)}</AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <p className="text-foreground truncate text-sm font-medium">{user.displayName}</p>
          <p className="text-muted-foreground truncate text-xs">{user.email}</p>
        </div>
      </div>

      {/* セラー専用の業務導線。視認性のため独立セクションで先頭に置く */}
      {isSeller && (
        <nav aria-label="出品者メニュー" className="border-border border-t px-2 py-2">
          <NavItem
            href={ROUTES.seller.dashboard}
            icon={LayoutDashboard}
            label="ダッシュボード"
            onNavigate={onNavigate}
          />
          <NavItem
            href={ROUTES.seller.products}
            icon={Tag}
            label="出品管理"
            onNavigate={onNavigate}
          />
        </nav>
      )}

      {/* アカウント共通導線 */}
      <nav aria-label="アカウントメニュー" className="border-border border-t px-2 py-2">
        <NavItem
          href="/profile/settings"
          icon={User}
          label="プロフィール設定"
          onNavigate={onNavigate}
        />
        <NavItem
          href={ROUTES.buyer.orders}
          icon={Package}
          label="注文履歴"
          onNavigate={onNavigate}
        />
        <NavItem
          href={ROUTES.buyer.wishlist}
          icon={Heart}
          label="お気に入り"
          onNavigate={onNavigate}
        />
        {!isSeller && (
          <NavItem
            href="/seller/applications/new"
            icon={Store}
            label="セラー申請"
            onNavigate={onNavigate}
          />
        )}
      </nav>
    </>
  )
}

function GuestSection({ onNavigate }: { onNavigate: () => void }) {
  return (
    <div className="flex flex-col gap-2 p-4">
      <Link
        href={ROUTES.auth.login}
        onClick={onNavigate}
        className="border-border text-foreground hover:bg-muted w-full rounded-lg border px-4 py-2.5 text-center text-sm font-medium transition-colors duration-150"
      >
        ログイン
      </Link>
      <Link
        href={ROUTES.auth.register}
        onClick={onNavigate}
        className="bg-accent text-accent-foreground hover:bg-accent/90 w-full rounded-lg px-4 py-2.5 text-center text-sm font-medium transition-colors duration-150"
      >
        会員登録
      </Link>
    </div>
  )
}

export function MobileMenuSheet() {
  const router = useRouter()
  const [open, setOpen] = useState(false)

  const hydrated = useAuthHydrated()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const user = useAuthStore((state) => state.user)
  const clearAuth = useAuthStore((state) => state.clearAuth)

  // 復元完了前は未認証として描画し、hydration mismatch を避ける（HeaderActions と同方針）
  const loggedInUser = hydrated && isAuthenticated && user ? user : null

  const close = () => setOpen(false)

  const handleLogout = async () => {
    // Refresh Token の無効化はベストエフォート。失敗してもローカルの認証状態は必ず破棄する。
    try {
      await logout()
    } catch {
      // ネットワークエラー等は無視してローカルログアウトを優先する
    }
    clearAuth()
    setOpen(false)
    router.replace(ROUTES.home)
  }

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger render={<Button variant="ghost" size="icon" aria-label="メニューを開く" />}>
        <Menu className="h-5 w-5" />
      </SheetTrigger>
      <SheetContent side="left" className="w-80 gap-0 p-0">
        <SheetHeader className="border-border border-b px-4 pt-4 pb-3">
          <SheetTitle className="text-primary font-serif">Kivio</SheetTitle>
        </SheetHeader>

        {/* 本文。項目が増えても収まるようスクロール領域にする */}
        <div className="min-h-0 flex-1 overflow-y-auto">
          {loggedInUser ? (
            <AccountSection user={loggedInUser} onNavigate={close} />
          ) : (
            <GuestSection onNavigate={close} />
          )}

          {/* カテゴリ */}
          <nav aria-label="カテゴリ" className="border-border border-t px-2 py-3">
            <p className="text-muted-foreground px-3 pb-1 text-xs font-semibold tracking-wider uppercase">
              カテゴリ
            </p>
            {categories.map(({ label, href }) => (
              <Link
                key={label}
                href={href}
                onClick={close}
                className="text-foreground hover:bg-muted block rounded-md px-3 py-2.5 text-sm transition-colors duration-150"
              >
                {label}
              </Link>
            ))}
            <Link
              href={ROUTES.search}
              onClick={close}
              className="text-accent hover:bg-muted block rounded-md px-3 py-2.5 text-sm font-medium transition-colors duration-150"
            >
              すべてのカテゴリ →
            </Link>
          </nav>
        </div>

        {/* ログアウトは誤操作を避けるため最下部に固定配置する */}
        {loggedInUser && (
          <SheetFooter className="border-border border-t p-2">
            <button
              type="button"
              onClick={handleLogout}
              className="text-destructive hover:bg-destructive/10 flex w-full cursor-pointer items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium transition-colors duration-150"
            >
              <LogOut className="size-4.5 shrink-0" />
              ログアウト
            </button>
          </SheetFooter>
        )}
      </SheetContent>
    </Sheet>
  )
}
