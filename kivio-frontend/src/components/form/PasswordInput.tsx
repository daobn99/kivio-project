'use client'
import { useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

/**
 * 通常ラベル（`<Label htmlFor>`）と組み合わせるパスワード入力。
 *
 * 認証画面の `PasswordField`（フローティングラベル）とは意図的に別物にしている。設定画面は
 * 初期値ありの編集フォームで、ラベルが最初から浮上した状態になりフローティングの利点が消えるため。
 */
export function PasswordInput({
  className,
  ...props
}: Omit<React.ComponentProps<'input'>, 'type'>) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="relative">
      <Input
        type={visible ? 'text' : 'password'}
        className={cn('h-11 pr-11', className)}
        {...props}
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'パスワードを非表示' : 'パスワードを表示'}
        aria-pressed={visible}
        className="text-muted-foreground hover:text-foreground focus-visible:ring-ring absolute top-1/2 right-2 -translate-y-1/2 rounded-md p-1.5 focus-visible:ring-2 focus-visible:outline-none"
      >
        {visible ? (
          <EyeOff className="h-5 w-5" aria-hidden />
        ) : (
          <Eye className="h-5 w-5" aria-hidden />
        )}
      </button>
    </div>
  )
}
