import { GlobalHeader } from '@/components/layout/GlobalHeader'
import { GlobalFooter } from '@/components/layout/GlobalFooter'
import { MobileBottomNav } from '@/components/layout/MobileBottomNav'

/**
 * 認証画面と違いグローバル chrome をフルで表示する。用が済んだらすぐ買い物へ戻れるよう、出口を消さない。
 *
 * 認証ガードそのものは proxy.ts が担い、このルートグループはレイアウト共有のためだけに存在する。
 */
export default function AuthenticatedLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <a
        href="#main-content"
        className="focus:bg-background focus:ring-ring sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-1000 focus:rounded-md focus:px-4 focus:py-2 focus:ring-2"
      >
        メインコンテンツへスキップ
      </a>
      <GlobalHeader />
      <main id="main-content" className="flex-1 pb-16 md:pb-0">
        {children}
      </main>
      <GlobalFooter />
      <MobileBottomNav />
    </>
  )
}
