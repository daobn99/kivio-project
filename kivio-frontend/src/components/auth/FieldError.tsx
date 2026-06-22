interface FieldErrorProps {
  id: string
  /** zod / RHF のエラーメッセージ。未定義なら何も描画しない */
  message?: string
  /** 常時表示するヘルプテキスト（例: パスワードの「8文字以上」）。エラー時は message を優先 */
  help?: string
}

/**
 * フィールド直下のエラー / ヘルプテキスト（auth.md §6.3.4）。
 * `id` は対応する入力の `aria-describedby` に渡してメッセージと紐付ける。
 */
export function FieldError({ id, message, help }: FieldErrorProps) {
  if (!message && !help) return null
  return (
    <p id={id} className={message ? 'text-destructive text-sm' : 'text-muted-foreground text-sm'}>
      {message ?? help}
    </p>
  )
}
