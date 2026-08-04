// Phase 2 暫定: GlobalHeader/Footer の動作確認を兼ねた開発用ページ。
// 本番ホームページは app/page.tsx を削除後 app/(public)/page.tsx で実装する。
import { GlobalHeader } from '@/components/layout/GlobalHeader'
import { GlobalFooter } from '@/components/layout/GlobalFooter'
import { MobileBottomNav } from '@/components/layout/MobileBottomNav'

type HealthResponse = {
  status: string
  timestamp: string
}

async function fetchHealth(): Promise<HealthResponse | null> {
  const apiBase = process.env.API_BASE_URL ?? 'http://localhost:8080'
  try {
    const res = await fetch(`${apiBase}/api/v1/health`, {
      next: { revalidate: 0 },
    })
    if (!res.ok) return null
    return res.json()
  } catch {
    return null
  }
}

export default async function Home() {
  const health = await fetchHealth()
  const isUp = health?.status === 'UP'

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
        <section className="bg-muted px-4 py-24 text-center">
          <h1 className="text-foreground mb-4 font-serif text-4xl font-bold">
            商品を探す、売る。
            <br />
            あなたのマーケット。
          </h1>
          <p className="text-muted-foreground text-lg">Kivio でお気に入りの一品を見つけよう</p>
        </section>

        {/* 開発用: API 疎通確認 */}
        <div className="border-border bg-card mx-auto mt-8 max-w-sm rounded-xl border p-6 shadow-sm">
          <h2 className="text-muted-foreground mb-4 text-xs font-semibold tracking-widest uppercase">
            システムステータス
          </h2>
          <div className="flex items-center justify-between">
            <span className="text-foreground text-sm font-medium">API バックエンド</span>
            <span
              className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                isUp ? 'bg-success/10 text-success' : 'bg-destructive/10 text-destructive'
              }`}
            >
              <span
                className={`h-1.5 w-1.5 rounded-full ${isUp ? 'bg-success' : 'bg-destructive'}`}
              />
              {health ? health.status : '接続不可'}
            </span>
          </div>
          {health?.timestamp && (
            <p className="text-muted-foreground mt-3 text-xs">
              確認日時:{' '}
              {new Date(health.timestamp).toLocaleString('ja-JP', {
                timeZone: 'Asia/Tokyo',
              })}
            </p>
          )}
          {!health && (
            <p className="text-muted-foreground mt-3 text-xs">
              バックエンドが起動していません。
              <code className="bg-muted text-foreground ml-1 rounded px-1 py-0.5 font-mono">
                ./gradlew bootRun
              </code>
            </p>
          )}
        </div>
      </main>
      <GlobalFooter />
      <MobileBottomNav />
    </>
  )
}
