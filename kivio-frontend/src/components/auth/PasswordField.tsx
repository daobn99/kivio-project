'use client'
import { forwardRef, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { FloatingLabelInput } from '@/components/ui/FloatingLabelInput'

interface PasswordFieldProps extends Omit<React.ComponentProps<'input'>, 'type'> {
  label: string
  error?: boolean
}

/**
 * パスワード入力欄（フローティングラベル + 表示/非表示トグル）。
 * auth.md §6.3.3。`autoComplete` は呼び出し側で current-password / new-password を指定する。
 */
export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(
  function PasswordField({ label, error, ...props }, ref) {
    const [visible, setVisible] = useState(false)
    return (
      <div className="relative">
        <FloatingLabelInput
          ref={ref}
          type={visible ? 'text' : 'password'}
          label={label}
          error={error}
          className="pr-11"
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'パスワードを非表示' : 'パスワードを表示'}
          aria-pressed={visible}
          className="text-muted-foreground hover:text-foreground focus-visible:ring-ring absolute top-1/2 right-3 -translate-y-1/2 rounded-md focus-visible:ring-2 focus-visible:outline-none"
        >
          {visible ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
        </button>
      </div>
    )
  },
)
