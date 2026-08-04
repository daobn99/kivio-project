'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { User, MapPin, Package } from 'lucide-react'
import { useAuthStore } from '@/stores/useAuthStore'
import { UserRole } from '@/types/enums'
import { ROUTES } from '@/lib/constants'
import { cn } from '@/lib/utils'

const ITEMS = [
  { href: '/profile/settings', label: 'プロフィール設定', icon: User, buyerOnly: false },
  { href: '/profile/addresses', label: '配送先住所', icon: MapPin, buyerOnly: true },
  { href: ROUTES.buyer.orders, label: '注文履歴', icon: Package, buyerOnly: true },
] as const

/**
 * lg 以上は左サイドの縦ナビ（sticky）、lg 未満は本文上の横スクロールセグメント。
 * アクティブ表現は左レール・淡い面・font-medium・aria-current の重ねがけにして、色だけに依存させない。
 */
export function AccountNav() {
  const pathname = usePathname()
  const role = useAuthStore((state) => state.user?.role)

  // 購入導線（住所・注文履歴）は ADMIN には出さない
  const items = ITEMS.filter((item) => !item.buyerOnly || role !== UserRole.ROLE_ADMIN)

  return (
    <nav aria-label="アカウントメニュー" className="lg:w-56 lg:shrink-0">
      <ul className="-mx-6 flex gap-1 overflow-x-auto px-6 lg:sticky lg:top-24 lg:mx-0 lg:flex-col lg:overflow-visible lg:px-0">
        {items.map(({ href, label, icon: Icon }) => {
          const active = pathname === href || pathname.startsWith(`${href}/`)
          return (
            <li key={href} className="shrink-0">
              <Link
                href={href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'focus-visible:ring-ring flex h-11 items-center gap-2 rounded-md px-3 text-sm whitespace-nowrap transition-colors duration-150 focus-visible:ring-2 focus-visible:outline-none motion-reduce:transition-none',
                  'lg:rounded-l-none lg:border-l-2',
                  active
                    ? 'text-primary bg-secondary/70 lg:border-l-accent font-medium'
                    : 'text-muted-foreground hover:text-foreground hover:bg-muted lg:border-l-transparent',
                )}
              >
                <Icon className="h-4 w-4 shrink-0" aria-hidden />
                {label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
