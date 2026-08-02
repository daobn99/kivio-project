import { Skeleton } from '@/components/ui/skeleton'

/**
 * 形は「未申請（制度説明 ＋ 申請フォーム）」に寄せる。4 状態のうち初回訪問で最も出やすいのが
 * 未申請であり、確率の高い形に寄せた方が確定時のガタつきが小さい。
 *
 * 一枚板ではなく実物と同じ行構成で組む。イントロは説明文の折り返しで高さが変わり、
 * 固定高では確定時に 200px 近い押し下げが出るため（`AddressListSkeleton` と同じ流儀）。
 */
export function SellerApplicationSkeleton() {
  return (
    <div className="mt-8 space-y-8">
      {/* イントロ。実物の塗り面（bg-secondary/50）は淡すぎて bg-muted のバーが沈むため、
          塗りは置かず余白と行数だけを実物に合わせる */}
      <div className="p-5 sm:p-6">
        <Skeleton className="h-7 w-48" />
        <div className="mt-4 space-y-4">
          {[0, 1, 2].map((index) => (
            <div key={index} className="flex gap-3">
              <Skeleton className="size-5 shrink-0" />
              <div className="w-full space-y-1.5">
                <Skeleton className="h-5 w-52" />
                <Skeleton className="h-4 w-full" />
                {/* 説明文はモバイルでのみ 2 行に折り返す */}
                <Skeleton className="h-4 w-2/3 sm:hidden" />
              </div>
            </div>
          ))}
        </div>
        <div className="border-border mt-5 space-y-2 border-t pt-4">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      </div>

      <div className="space-y-4">
        <div className="space-y-2">
          <Skeleton className="h-7 w-24" />
          <Skeleton className="h-4 w-3/4" />
        </div>
        <Skeleton className="h-40 w-full rounded-lg" />
        <div className="flex justify-between">
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-16" />
        </div>
        <Skeleton className="h-11 w-full sm:ml-auto sm:w-40" />
      </div>
    </div>
  )
}
