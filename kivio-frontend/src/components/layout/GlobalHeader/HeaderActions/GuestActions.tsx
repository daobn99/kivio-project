import Link from 'next/link'
import { ShoppingCart } from 'lucide-react'
import { cn } from '@/lib/utils'
import { buttonVariants } from '@/components/ui/button'
import { LanguageButton } from './LanguageButton'
import { IconButton } from './IconButton'

export function GuestActions() {
  return (
    <div className="flex items-center gap-1.5">
      <LanguageButton />
      <IconButton href="/cart" icon={ShoppingCart} label="カート" />

      {/* ユーティリティ群と認証 CTA を視覚的に分ける縦罫線 */}
      <span aria-hidden className="bg-border mx-1 h-6 w-px" />

      <Link
        href="/auth/login"
        className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), 'text-sm font-medium')}
      >
        ログイン
      </Link>

      <Link
        href="/auth/register"
        className={cn(
          buttonVariants({ size: 'sm' }),
          'bg-accent hover:bg-accent/90 text-accent-foreground h-9 w-30 rounded-full text-sm font-medium',
        )}
      >
        会員登録
      </Link>
    </div>
  )
}
