import Link from 'next/link'
import { cn } from '@/lib/utils'
import { buttonVariants } from '@/components/ui/button'
import type { LucideIcon } from 'lucide-react'

interface IconButtonProps {
  href: string
  icon: LucideIcon
  /** 未読・数量バッジ。0 または undefined で非表示 */
  badge?: number
  label: string
}

// リンクとして振る舞うアイコンボタン。Base UI Button の nativeButton 警告を避けるため、
// Button プリミティブではなく buttonVariants + Link を直接利用する（GuestActions のリンクと同方針）。
export function IconButton({ href, icon: Icon, badge, label }: IconButtonProps) {
  return (
    <Link
      href={href}
      aria-label={label}
      className={cn(buttonVariants({ variant: 'ghost', size: 'icon-lg' }), 'relative')}
    >
      <Icon className="size-5" />
      {badge != null && badge > 0 && (
        <span className="bg-destructive text-destructive-foreground absolute -top-1 -right-1 flex h-4.5 min-w-4.5 items-center justify-center rounded-full px-1 text-[10px] font-bold">
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </Link>
  )
}
