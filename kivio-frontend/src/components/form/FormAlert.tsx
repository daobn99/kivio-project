import { CircleAlert } from 'lucide-react'
import { cn } from '@/lib/utils'

interface FormAlertProps {
  children: React.ReactNode
  className?: string
}

/**
 * サーバーエラーの表示。認証画面ではフォーム上部に 1 つ、設定画面ではセクション単位で置き、
 * どの操作が失敗したのかを操作地点で示す。
 */
export function FormAlert({ children, className }: FormAlertProps) {
  return (
    <div
      role="alert"
      aria-live="polite"
      className={cn(
        'border-destructive/30 bg-destructive/10 text-destructive flex items-start gap-2 rounded-lg border px-3 py-2.5 text-sm',
        className,
      )}
    >
      <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div className="space-y-1">{children}</div>
    </div>
  )
}
