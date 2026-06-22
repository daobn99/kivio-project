'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, ShoppingCart, PlusCircle, Bell, User } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface NavItem {
  label: string
  href: string
  icon: LucideIcon
  badge?: number
  isFab?: boolean
}

// Phase 2: 未認証状態のみ。出品・お知らせ・マイページはログイン誘導。
const navItems: NavItem[] = [
  { label: 'ホーム', href: '/', icon: Home },
  { label: 'カート', href: '/cart', icon: ShoppingCart },
  { label: '出品', href: '/auth/login', icon: PlusCircle, isFab: true },
  { label: 'お知らせ', href: '/auth/login', icon: Bell },
  { label: 'マイページ', href: '/auth/login', icon: User },
]

export function MobileBottomNav() {
  const pathname = usePathname()

  return (
    <nav
      aria-label="モバイルナビゲーション"
      className="bg-background border-border fixed inset-x-0 bottom-0 z-20 flex items-center border-t md:hidden"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      <div className="flex w-full">
        {navItems.map((item) => {
          const isActive = pathname === item.href

          if (item.isFab) {
            return (
              <Link
                key={item.label}
                href={item.href}
                className="relative flex min-h-11 flex-1 flex-col items-center justify-center gap-0.5 py-2"
                aria-label={item.label}
              >
                <div className="bg-accent -mt-5 flex h-12 w-12 items-center justify-center rounded-full shadow-lg">
                  <item.icon className="text-accent-foreground h-6 w-6" />
                </div>
                <span className="text-muted-foreground mt-1 text-xs">{item.label}</span>
              </Link>
            )
          }

          return (
            <Link
              key={item.label}
              href={item.href}
              aria-current={isActive ? 'page' : undefined}
              aria-label={
                item.badge && item.badge > 0 ? `${item.label}（${item.badge}件）` : item.label
              }
              className={cn(
                'flex min-h-11 flex-1 flex-col items-center justify-center gap-0.5 py-3 transition-colors duration-150',
                isActive ? 'text-accent' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              <div className="relative">
                <item.icon className="h-6 w-6" />
                {item.badge != null && item.badge > 0 && (
                  <span className="bg-destructive text-destructive-foreground absolute -top-1.5 -right-1.5 flex h-4.5 min-w-4.5 items-center justify-center rounded-full px-1 text-[10px] font-bold">
                    {item.badge > 99 ? '99+' : item.badge}
                  </span>
                )}
              </div>
              <span className="text-xs">{item.label}</span>
            </Link>
          )
        })}
      </div>
    </nav>
  )
}
