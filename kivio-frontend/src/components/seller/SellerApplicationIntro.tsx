import { PackagePlus, Store, TrendingUp } from 'lucide-react'

/**
 * Phase 2 で実際に到達できる機能だけを書く。審査の所要日数や通知手段（メール等）は
 * 該当機能が未実装のため約束しない。確実なのは「このページで確認できる」ことだけ。
 */
const BENEFITS = [
  {
    icon: Store,
    title: 'ショップを開設できます',
    body: '審査に通ると、あなた専用のショップページが作成されます。',
  },
  {
    icon: PackagePlus,
    title: '商品を登録して販売できます',
    body: '写真・価格・在庫を登録して、すぐに販売を開始できます。',
  },
  {
    icon: TrendingUp,
    title: '注文と売上を管理できます',
    body: '注文状況や売上をダッシュボードで確認できます。',
  },
] as const

/** 未申請のときだけ出す。却下後は説得が済んでいるため出さない（読むべきは却下理由）。 */
export function SellerApplicationIntro() {
  return (
    <section aria-labelledby="intro-heading" className="bg-secondary/50 rounded-xl p-5 sm:p-6">
      <h2 id="intro-heading" className="text-foreground font-serif text-lg font-bold">
        出品者になるとできること
      </h2>
      <ul className="mt-4 space-y-4">
        {BENEFITS.map(({ icon: Icon, title, body }) => (
          <li key={title} className="flex gap-3">
            <Icon className="text-accent mt-0.5 h-5 w-5 shrink-0" aria-hidden />
            <div className="space-y-0.5">
              <p className="text-foreground text-base font-medium">{title}</p>
              <p className="text-muted-foreground text-sm leading-relaxed">{body}</p>
            </div>
          </li>
        ))}
      </ul>
      <div className="text-muted-foreground border-border mt-5 space-y-1 border-t pt-4 text-sm leading-relaxed">
        <p>申請内容を確認のうえ、結果をお知らせします。審査状況はこのページで確認できます。</p>
        <p>送信後の取り消しはできません。内容をご確認のうえ送信してください。</p>
      </div>
    </section>
  )
}
