import { Button } from '@/components/ui/button'
import { Globe } from 'lucide-react'

/**
 * 多言語対応準備中の地球アイコン。全認証状態のヘッダー共通で、現状は無効化して表示する。
 */
export function LanguageButton() {
  return (
    <Button
      variant="ghost"
      size="icon-lg"
      aria-label="言語設定（準備中）"
      disabled
      className="text-muted-foreground"
    >
      <Globe className="size-5" />
    </Button>
  )
}
