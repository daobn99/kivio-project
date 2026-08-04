'use client'
import { useId } from 'react'
import { cn } from '@/lib/utils'

interface FloatingLabelInputProps extends React.ComponentProps<'input'> {
  label: string
  /** true でエラー配色（border / label を destructive にする） */
  error?: boolean
}

/**
 * フローティングラベル付き input。
 *
 * 未入力時はラベルがフィールド中央にプレースホルダー風に表示され、フォーカスまたは
 * 入力開始で上部へ浮上する。`:placeholder-shown` / `:focus` の peer 連動のみで実現し
 * JS の状態管理を持たない。
 */
export function FloatingLabelInput({
  label,
  error,
  className,
  id,
  ...props
}: FloatingLabelInputProps) {
  const autoId = useId()
  const inputId = id ?? autoId

  return (
    <div className="relative">
      <input
        id={inputId}
        // 空白プレースホルダーで :placeholder-shown を有効化する（空文字だとラベルが浮上したままになる）
        placeholder=" "
        aria-invalid={error || undefined}
        className={cn(
          'peer bg-background text-foreground h-14 w-full rounded-lg border px-3 pt-5 pb-1 text-base',
          'transition-[color,border-color,box-shadow] duration-150 outline-none motion-reduce:transition-none',
          'border-input focus:border-accent focus:ring-ring focus:ring-2',
          'aria-invalid:border-destructive aria-invalid:focus:ring-destructive/40',
          className,
        )}
        {...props}
      />
      <label
        htmlFor={inputId}
        className={cn(
          'text-muted-foreground pointer-events-none absolute top-2 left-3 text-xs transition-all duration-150 motion-reduce:transition-none',
          'peer-placeholder-shown:top-1/2 peer-placeholder-shown:-translate-y-1/2 peer-placeholder-shown:text-base',
          'peer-focus:text-accent peer-focus:top-2 peer-focus:translate-y-0 peer-focus:text-xs',
          'peer-aria-invalid:text-destructive',
        )}
      >
        {label}
      </label>
    </div>
  )
}
