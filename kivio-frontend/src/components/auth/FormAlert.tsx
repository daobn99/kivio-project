import { CircleAlert } from 'lucide-react'
import { cn } from '@/lib/utils'

interface FormAlertProps {
  children: React.ReactNode
  className?: string
}

/**
 * フォーム上部のサーバーエラー表示。`role="alert"` / `aria-live="polite"` で支援技術に通知する
 * （auth.md §7.2・§13）。ログイン失敗等はメール／パスワードのどちらが誤りかを示さない。
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
