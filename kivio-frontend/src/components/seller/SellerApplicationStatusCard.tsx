import type { Ref } from 'react'
import { CheckCircle2, Clock, XCircle } from 'lucide-react'
import type { SellerApplication } from '@/types/api'
import { SellerApplicationStatus } from '@/types/enums'
import { Badge } from '@/components/ui/badge'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'

interface StatusPresentation {
  label: string
  icon: typeof Clock
  /** 左レール。`AccountNav` のアクティブ表現と同じ「2px の左罫線＝状態の標識」 */
  rail: string
  badge: string
}

/** 色は装飾。意味はバッジの文言とアイコンが担う（色単独で状態を表さない） */
const PRESENTATION: Record<SellerApplicationStatus, StatusPresentation> = {
  [SellerApplicationStatus.PENDING]: {
    label: '審査中',
    icon: Clock,
    rail: 'border-l-warning',
    badge: 'border-warning/20 bg-warning/10 text-warning border',
  },
  [SellerApplicationStatus.REJECTED]: {
    label: '却下',
    icon: XCircle,
    rail: 'border-l-destructive',
    badge: 'bg-destructive/10 text-destructive',
  },
  [SellerApplicationStatus.APPROVED]: {
    label: '承認済み',
    icon: CheckCircle2,
    rail: 'border-l-success',
    badge: 'border-success/20 bg-success/10 text-success border',
  },
}

interface SellerApplicationStatusCardProps {
  application: SellerApplication
  /** 送信成功でフォームが消えたあと、フォーカスをここへ移すために親が渡す */
  ref?: Ref<HTMLElement>
}

/**
 * 影は付けない。`shadow-card` は「面ごとクリックできる」合図として商品カードに使っており、
 * このカードはクリック対象ではないため（住所カードと同じ判断）。
 */
export function SellerApplicationStatusCard({
  application,
  ref,
}: SellerApplicationStatusCardProps) {
  const { label, icon: Icon, rail, badge } = PRESENTATION[application.status]

  // 審査コメント未記入の却下ではキーごと省略されるため真値で判定する。
  // 理由が無いときは「理由なし」と書かず、ブロックごと出さない
  const rejectReason =
    application.status === SellerApplicationStatus.REJECTED ? application.reviewComment : null

  return (
    <section
      ref={ref}
      tabIndex={-1}
      aria-labelledby="status-heading"
      className={cn(
        'border-border bg-background rounded-xl border border-l-2 p-5 outline-none sm:p-6',
        rail,
      )}
    >
      <h2 id="status-heading" className="sr-only">
        申請状況
      </h2>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <Badge className={badge}>
          <Icon aria-hidden />
          {label}
        </Badge>
        <p className="text-muted-foreground text-sm tabular-nums">
          {formatDate(application.createdAt)} 申請
        </p>
      </div>

      <h3 className="text-muted-foreground mt-5 text-sm font-medium">申請理由</h3>
      {/* 自分が書いた文章を全文読み返せることに価値があるため line-clamp では切り詰めない。
          却下理由が下に出るときは塗りを外す — 同時に出る塗り面は 1 つまでで、
          そのとき読ませたいのは自分の申請文ではなく却下理由だから（設計書 §1） */}
      <p
        className={cn(
          'mt-2 rounded-lg p-4 text-sm leading-relaxed wrap-break-word whitespace-pre-wrap',
          rejectReason ? 'border-border border' : 'bg-muted/60',
        )}
      >
        {application.reason}
      </p>

      {application.status === SellerApplicationStatus.PENDING && (
        <p className="text-muted-foreground mt-5 text-sm leading-relaxed">
          審査には数日かかる場合があります。結果が出るとこのページの表示が変わります。
        </p>
      )}

      {rejectReason && (
        <div className="border-destructive/20 bg-destructive/5 mt-5 rounded-lg border p-4">
          <h3 className="text-destructive text-sm font-medium">却下理由</h3>
          <p className="mt-2 text-sm leading-relaxed wrap-break-word whitespace-pre-wrap">
            {rejectReason}
          </p>
        </div>
      )}
    </section>
  )
}
