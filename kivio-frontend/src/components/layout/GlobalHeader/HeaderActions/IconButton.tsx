import Link from 'next/link'
import { Button } from '@/components/ui/button'
import type { LucideIcon } from 'lucide-react'

interface IconButtonProps {
  href: string
  icon: LucideIcon
  /** 未読・数量バッジ。0 または undefined で非表示 */
  badge?: number
  label: string
}

export function IconButton({ href, icon: Icon, badge, label }: IconButtonProps) {
  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={label}
      render={<Link href={href} />}
      className="relative"
    >
      <Icon className="h-5 w-5" />
      {badge != null && badge > 0 && (
        <span className="bg-destructive text-destructive-foreground absolute -top-1 -right-1 flex h-4.5 min-w-4.5 items-center justify-center rounded-full px-1 text-[10px] font-bold">
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </Button>
  )
}
