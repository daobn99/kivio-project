import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Globe } from 'lucide-react'
import { cn } from '@/lib/utils'
import { buttonVariants } from '@/components/ui/button'

export function GuestActions() {
  return (
    <div className="flex items-center gap-2">
      {/* 多言語対応準備中 */}
      <Button
        variant="ghost"
        size="icon"
        aria-label="言語設定（準備中）"
        disabled
        className="text-muted-foreground"
      >
        <Globe className="h-5 w-5" />
      </Button>

      <Link
        href="/auth/login"
        className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), 'font-medium')}
      >
        ログイン
      </Link>

      <Link
        href="/auth/register"
        className={cn(
          buttonVariants({ size: 'sm' }),
          'bg-accent hover:bg-accent/90 text-accent-foreground h-9 w-30 rounded-full font-medium',
        )}
      >
        会員登録
      </Link>
    </div>
  )
}
