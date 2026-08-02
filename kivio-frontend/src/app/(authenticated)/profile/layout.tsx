import { AccountIdentityHeader } from '@/components/profile/AccountIdentityHeader'
import { AccountNav } from '@/components/profile/AccountNav'

/** アイデンティティ帯 + サイドナビ + 本文列。`/profile/*` の全ページで共有する。 */
export default function ProfileLayout({ children }: { children: React.ReactNode }) {
  // ボトムナビ分の余白は親レイアウトの main が持つ。ここはフッターとの間の余白のみ
  return (
    <div className="pb-10 md:pb-24">
      <AccountIdentityHeader />

      <div className="mx-auto flex max-w-7xl flex-col gap-8 px-6 pt-8 lg:flex-row lg:gap-10 lg:pt-10">
        <AccountNav />
        {/* min-w-0: 長いメールアドレス等で flex 子要素がはみ出して横スクロールが出るのを防ぐ */}
        <div className="min-w-0 flex-1 lg:max-w-3xl">{children}</div>
      </div>
    </div>
  )
}
