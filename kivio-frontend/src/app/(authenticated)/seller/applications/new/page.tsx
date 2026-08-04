import type { Metadata } from 'next'
import { SellerApplicationView } from '@/components/seller/SellerApplicationView'

export const metadata: Metadata = {
  title: 'セラー申請 | Kivio',
}

/**
 * 見出しとリード文は状態で差し替えない。状態を知っているのは `SellerApplicationView`（CC）
 * だけであり、見出しを状態依存にするとページ全体を CC にする必要がある。加えて同じ URL で
 * `<h1>` が入れ替わると、スクリーンリーダー利用者がページの同一性を見失う。
 *
 * そのためリード文は勧誘ではなく「このページで何ができるか」に徹する。4 状態のうち 2 つ
 * （審査中・却下）は申請済みの人が見る画面であり、勧誘文はそこで浮く。説得はイントロが担う。
 */
export default function SellerApplicationNewPage() {
  return (
    <div className="mx-auto max-w-2xl px-6 py-10 lg:py-14">
      <header className="space-y-2">
        <h1 className="text-foreground font-serif text-2xl font-bold sm:text-3xl">セラー申請</h1>
        <p className="text-muted-foreground text-base leading-relaxed">
          Kivio への出店申請と、審査状況の確認ができます。
        </p>
      </header>

      <SellerApplicationView />
    </div>
  )
}
