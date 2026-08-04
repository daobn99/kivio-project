import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface AuthSubmitButtonProps {
  children: React.ReactNode
  /** 送信中。spinner 表示 + disabled で二重送信を防ぐ */
  pending?: boolean
  disabled?: boolean
}

/**
 * 認証フォームの主 CTA（accent・full width・h-11）。
 */
export function AuthSubmitButton({ children, pending, disabled }: AuthSubmitButtonProps) {
  return (
    <Button
      type="submit"
      disabled={pending || disabled}
      className="bg-accent text-accent-foreground hover:bg-accent/90 h-11 w-full"
    >
      {pending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
      {children}
    </Button>
  )
}
