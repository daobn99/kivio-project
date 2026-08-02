import { Skeleton } from '@/components/ui/skeleton'

/** 空状態と誤認させないよう、カード形状のまま実物と同寸で出す（CLS 防止）。 */
export function AddressListSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {[0, 1].map((index) => (
        <div key={index} className="border-border space-y-3 rounded-lg border p-5">
          <Skeleton className="h-5 w-28" />
          <div className="space-y-2">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-4 w-32" />
          </div>
          <Skeleton className="h-9 w-full" />
        </div>
      ))}
    </div>
  )
}
