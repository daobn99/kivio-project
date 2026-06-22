'use client'
import { useRef } from 'react'
import { cn } from '@/lib/utils'

const OTP_LENGTH = 6

interface OtpInputProps {
  value: string
  onChange: (value: string) => void
  /** 6 桁揃った瞬間に呼ばれる（自動送信などに使う） */
  onComplete?: (value: string) => void
  error?: boolean
  disabled?: boolean
}

/**
 * 6 桁分割入力（auth.md §8.3）。フローティングラベルの例外（§6.3）。
 * 数字のみ・ペースト対応・Backspace で前セルへ移動。各セルに aria-label を付与する。
 * inputMode="numeric" / autoComplete="one-time-code" で SMS・メールの自動入力に対応する。
 */
export function OtpInput({ value, onChange, onComplete, error, disabled }: OtpInputProps) {
  const inputsRef = useRef<(HTMLInputElement | null)[]>([])

  const digits = value.split('').slice(0, OTP_LENGTH)

  const focusCell = (index: number) => {
    const target = inputsRef.current[Math.max(0, Math.min(index, OTP_LENGTH - 1))]
    target?.focus()
    target?.select()
  }

  const commit = (next: string) => {
    onChange(next)
    if (next.length === OTP_LENGTH) onComplete?.(next)
  }

  const handleChange = (index: number, raw: string) => {
    const numeric = raw.replace(/\D/g, '')
    if (!numeric) return

    // 1 セルに複数文字（オートフィル・IME 確定）が来た場合は index 以降へ流し込む
    const chars = numeric.split('')
    const arr = value.split('')
    let cursor = index
    for (const ch of chars) {
      if (cursor >= OTP_LENGTH) break
      arr[cursor] = ch
      cursor++
    }
    commit(arr.join('').slice(0, OTP_LENGTH))
    focusCell(cursor)
  }

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      e.preventDefault()
      const arr = value.split('')
      if (arr[index]) {
        arr[index] = ''
        onChange(arr.join(''))
      } else if (index > 0) {
        arr[index - 1] = ''
        onChange(arr.join(''))
        focusCell(index - 1)
      }
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault()
      focusCell(index - 1)
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      focusCell(index + 1)
    }
  }

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_LENGTH)
    if (!pasted) return
    commit(pasted)
    focusCell(pasted.length)
  }

  return (
    <div className="flex justify-center gap-2" role="group" aria-label="認証コード（6桁）">
      {Array.from({ length: OTP_LENGTH }).map((_, index) => (
        <input
          // 固定長の静的セル。並び替えは発生しないため index を key にしてよい
          key={index}
          ref={(el) => {
            inputsRef.current[index] = el
          }}
          type="text"
          inputMode="numeric"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          spellCheck={false}
          maxLength={1}
          disabled={disabled}
          aria-label={`認証コード ${index + 1}桁目`}
          aria-invalid={error || undefined}
          value={digits[index] ?? ''}
          onChange={(e) => handleChange(index, e.target.value)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onPaste={handlePaste}
          onFocus={(e) => e.target.select()}
          className={cn(
            'bg-background text-foreground h-14 w-12 rounded-lg border text-center text-xl font-medium tabular-nums',
            'transition-[color,border-color,box-shadow] duration-150 outline-none motion-reduce:transition-none',
            'border-input focus:border-accent focus:ring-ring focus:ring-2',
            'disabled:bg-muted/50 disabled:opacity-60',
            'aria-invalid:border-destructive aria-invalid:focus:ring-destructive/40',
          )}
        />
      ))}
    </div>
  )
}
