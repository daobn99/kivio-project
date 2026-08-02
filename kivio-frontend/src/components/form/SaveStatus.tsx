'use client'
import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'

interface SaveStatusProps {
  /** 保存成功のたびにインクリメントされる値。変化を検知して表示をリセットする */
  signal: number
  message?: string
}

/**
 * 保存完了をトーストではなくセクション内に出す。複数のフォームが並ぶ画面では、
 * 「どの操作が成功したのか」を操作地点で示さないと対応づけられないため。
 *
 * `min-h-5` は出現時にレイアウトがずれないようにするためのもの。
 */
export function SaveStatus({ signal, message = '保存しました' }: SaveStatusProps) {
  // 表示済み（消去済み）の signal を覚え、表示可否は state ではなく導出で決める
  const [dismissed, setDismissed] = useState(0)
  const visible = signal !== 0 && signal !== dismissed

  useEffect(() => {
    if (signal === 0) return
    const timer = setTimeout(() => setDismissed(signal), 4000)
    return () => clearTimeout(timer)
  }, [signal])

  return (
    <p aria-live="polite" className="text-success flex min-h-5 items-center gap-1.5 text-sm">
      {visible && (
        <span
          key={signal}
          className="flex items-center gap-1.5 motion-safe:animate-[auth-fade_150ms_ease-out]"
        >
          <Check className="h-4 w-4" aria-hidden />
          {message}
        </span>
      )}
    </p>
  )
}
