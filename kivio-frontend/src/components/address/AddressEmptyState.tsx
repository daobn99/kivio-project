'use client'
import { MapPin } from 'lucide-react'
import { Button } from '@/components/ui/button'

/** 破線ボーダーは空状態だけの記号にして、実データのカードと一目で区別できるようにする。 */
export function AddressEmptyState({ onAdd }: { onAdd: () => void }) {
  return (
    <div className="border-border flex flex-col items-center gap-4 rounded-lg border border-dashed py-16 text-center">
      <MapPin className="text-muted-foreground h-12 w-12" aria-hidden />
      <div className="space-y-1">
        <p className="text-foreground font-medium">配送先住所がまだ登録されていません</p>
        <p className="text-muted-foreground text-sm">登録しておくと、購入時の入力を省けます。</p>
      </div>
      <Button onClick={onAdd} className="bg-accent text-accent-foreground hover:bg-accent/90 h-11">
        住所を追加する
      </Button>
    </div>
  )
}
