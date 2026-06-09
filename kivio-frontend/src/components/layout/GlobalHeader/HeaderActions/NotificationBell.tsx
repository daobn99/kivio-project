'use client'
import { Button } from '@/components/ui/button'
import { Bell } from 'lucide-react'

interface NotificationBellProps {
  unreadCount?: number
}

// Phase 2: 未読バッジ表示のみ。ドロップダウンは Phase 3 以降で実装。
export function NotificationBell({ unreadCount = 0 }: NotificationBellProps) {
  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={`通知${unreadCount > 0 ? `（未読${unreadCount}件）` : ''}`}
      className="relative"
    >
      <Bell className="h-5 w-5" />
      {unreadCount > 0 && (
        <span className="bg-destructive text-destructive-foreground absolute -top-1 -right-1 flex h-4.5 min-w-4.5 items-center justify-center rounded-full px-1 text-[10px] font-bold">
          {unreadCount > 99 ? '99+' : unreadCount}
        </span>
      )}
    </Button>
  )
}
