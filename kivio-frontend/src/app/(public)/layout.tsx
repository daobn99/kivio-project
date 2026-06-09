import { GlobalHeader } from '@/components/layout/GlobalHeader'
import { GlobalFooter } from '@/components/layout/GlobalFooter'
import { MobileBottomNav } from '@/components/layout/MobileBottomNav'

export default function PublicLayout({ children }: { children: React.ReactNode }) {
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
