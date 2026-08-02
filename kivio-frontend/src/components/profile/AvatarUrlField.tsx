'use client'
import type { UseFormRegisterReturn } from 'react-hook-form'
import { X } from 'lucide-react'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FieldError } from '@/components/form/FieldError'

interface AvatarUrlFieldProps {
  /** 現在の入力値（プレビュー用） */
  value: string
  /** イニシャルフォールバック用 */
  displayName: string
  registration: UseFormRegisterReturn
  error?: string
  /** クリア（空文字化）。空文字はサーバー側で null 化される */
  onClear: () => void
}

/**
 * アップロード基盤が未実装なので、ドロップゾーン等の「アップロードできるように見える UI」に
 * はしない。将来アップローダへ差し替えるとき、この 1 コンポーネントの置換で済むよう独立させている。
 */
export function AvatarUrlField({
  value,
  displayName,
  registration,
  error,
  onClear,
}: AvatarUrlFieldProps) {
  return (
    <div className="flex items-start gap-4">
      <Avatar className="border-border mt-6 size-16 shrink-0 border">
        {/* 無効な URL・読み込み失敗時はフォールバック（イニシャル）に落ちる */}
        <AvatarImage src={value || undefined} alt="" />
        <AvatarFallback className="bg-muted text-muted-foreground font-serif text-lg">
          {displayName.slice(0, 1)}
        </AvatarFallback>
      </Avatar>

      <div className="min-w-0 flex-1 space-y-2">
        <Label htmlFor="avatarUrl">アバター画像 URL</Label>
        <div className="flex items-center gap-2">
          <Input
            id="avatarUrl"
            type="url"
            inputMode="url"
            spellCheck={false}
            placeholder="https://example.com/avatar.png"
            className="h-11"
            aria-invalid={!!error || undefined}
            aria-describedby="avatarUrl-error"
            {...registration}
          />
          {value && (
            <button
              type="button"
              onClick={onClear}
              aria-label="アバター画像を削除"
              className="text-muted-foreground hover:text-foreground focus-visible:ring-ring border-input flex size-11 shrink-0 items-center justify-center rounded-lg border transition-colors duration-150 focus-visible:ring-2 focus-visible:outline-none motion-reduce:transition-none"
            >
              <X className="h-4 w-4" aria-hidden />
            </button>
          )}
        </div>
        <FieldError
          id="avatarUrl-error"
          message={error}
          help="画像アップロードは近日対応予定です。現在は画像の URL を指定してください。"
        />
      </div>
    </div>
  )
}
