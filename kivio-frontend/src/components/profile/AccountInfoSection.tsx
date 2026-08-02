'use client'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuthStore } from '@/stores/useAuthStore'
import { ROLE_LABEL, formatDate } from '@/lib/format'

/**
 * 変更 API を持たない値の読み取り専用表示。`disabled` な入力枠は「編集できるのでは」という
 * 誤解とコントラスト不足を招くため、定義リストのプレーンテキストにする。
 */
export function AccountInfoSection() {
  const user = useAuthStore((state) => state.user)

  if (!user) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-5 w-full" />
        <Skeleton className="h-5 w-2/3" />
        <Skeleton className="h-5 w-1/2" />
      </div>
    )
  }

  const rows = [
    { term: 'メールアドレス', desc: user.email },
    { term: 'アカウント種別', desc: ROLE_LABEL[user.role] },
    { term: '登録日', desc: formatDate(user.createdAt) },
  ]

  return (
    <dl className="divide-border divide-y text-sm">
      {rows.map(({ term, desc }) => (
        <div
          key={term}
          className="grid grid-cols-[8rem_minmax(0,1fr)] gap-4 py-3 first:pt-0 last:pb-0"
        >
          <dt className="text-muted-foreground">{term}</dt>
          <dd className="text-foreground truncate">{desc}</dd>
        </div>
      ))}
    </dl>
  )
}
