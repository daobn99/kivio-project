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
    <main className="flex min-h-screen flex-col items-center justify-center gap-10 bg-zinc-50 p-8 dark:bg-zinc-950">
      <div className="flex flex-col items-center gap-2 text-center">
        <h1 className="text-4xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Kivio
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          マルチベンダーマーケットプレイス — 開発環境
        </p>
      </div>

      <div className="w-full max-w-sm rounded-xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-zinc-400">
          システムステータス
        </h2>
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
            API バックエンド
          </span>
          <span
            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
              isUp
                ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
            }`}
          >
            <span
              className={`h-1.5 w-1.5 rounded-full ${isUp ? 'bg-green-500' : 'bg-red-500'}`}
            />
            {health ? health.status : '接続不可'}
          </span>
        </div>
        {health?.timestamp && (
          <p className="mt-3 text-xs text-zinc-400">
            確認日時: {new Date(health.timestamp).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' })}
          </p>
        )}
        {!health && (
          <p className="mt-3 text-xs text-zinc-400">
            バックエンドが起動していません。
            <code className="ml-1 rounded bg-zinc-100 px-1 py-0.5 font-mono text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
              ./gradlew bootRun
            </code>
          </p>
        )}
      </div>

      <div className="flex gap-4 text-sm">
        <a
          href="http://localhost:8080/swagger-ui.html"
          target="_blank"
          rel="noopener noreferrer"
          className="rounded-lg border border-zinc-200 bg-white px-4 py-2 font-medium text-zinc-700 transition-colors hover:border-zinc-300 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800"
        >
          Swagger UI
        </a>
        <a
          href="http://localhost:8025"
          target="_blank"
          rel="noopener noreferrer"
          className="rounded-lg border border-zinc-200 bg-white px-4 py-2 font-medium text-zinc-700 transition-colors hover:border-zinc-300 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800"
        >
          Mailpit
        </a>
      </div>
    </main>
  )
}
