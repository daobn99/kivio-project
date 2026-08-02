'use client'
import { Pencil, Star, Trash2 } from 'lucide-react'
import type { Address } from '@/types/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatPostalCode } from '@/lib/format'
import { cn } from '@/lib/utils'

interface AddressCardProps {
  address: Address
  onEdit: () => void
  onDelete: () => void
  /** デフォルトに設定する（`PATCH { isDefault: true }`）。デフォルト住所には渡らない */
  onMakeDefault: () => void
  /** デフォルト切替の通信中。連打防止 */
  pending?: boolean
}

/**
 * 影は付けない。`shadow-card` は「面ごとクリックできる」合図として商品カードに使っており、
 * 住所カードはクリック対象ではないため。デフォルトは左レールとバッジ文言の二重表現にする。
 */
export function AddressCard({
  address,
  onEdit,
  onDelete,
  onMakeDefault,
  pending,
}: AddressCardProps) {
  return (
    <article
      className={cn(
        'border-border flex flex-col rounded-lg border p-5',
        address.isDefault && 'border-l-accent border-l-2',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-foreground font-medium">{address.recipientName}</h3>
        {address.isDefault && (
          <Badge className="bg-accent text-accent-foreground shrink-0">デフォルト</Badge>
        )}
      </div>

      <address className="text-muted-foreground mt-2 space-y-0.5 text-sm leading-relaxed not-italic">
        <p>{formatPostalCode(address.postalCode)}</p>
        <p>
          {address.prefecture}
          {address.city}
        </p>
        <p>{address.addressLine}</p>
        <p className="tabular-nums">{address.phoneNumber}</p>
      </address>

      {/* 同じラベルのボタンがカードの数だけ並ぶため、aria-label で対象を補う */}
      <div className="border-border mt-4 flex flex-wrap items-center gap-1 border-t pt-3">
        <Button
          variant="ghost"
          size="sm"
          className="h-9"
          onClick={onEdit}
          aria-label={`${address.recipientName} の配送先を編集`}
        >
          <Pencil className="h-4 w-4" aria-hidden />
          編集
        </Button>
        {!address.isDefault && (
          <Button
            variant="ghost"
            size="sm"
            className="h-9"
            onClick={onMakeDefault}
            disabled={pending}
            aria-label={`${address.recipientName} の配送先をデフォルトにする`}
          >
            <Star className="h-4 w-4" aria-hidden />
            デフォルトにする
          </Button>
        )}
        <Button
          variant="ghost"
          size="sm"
          className="text-destructive hover:text-destructive hover:bg-destructive/10 ml-auto h-9"
          onClick={onDelete}
          aria-label={`${address.recipientName} の配送先を削除`}
        >
          <Trash2 className="h-4 w-4" aria-hidden />
          削除
        </Button>
      </div>
    </article>
  )
}
