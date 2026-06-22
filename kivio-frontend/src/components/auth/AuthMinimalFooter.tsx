import Link from 'next/link'

// 残すのは法的必須（プライバシー・利用規約・特商法）+ サポート（ヘルプ）の 4 本のみ（auth.md §11）。
// マーケ導線は出さない。Phase 2 は href="#"、実 URL 確定後に差し替える。
const FOOTER_LINKS = [
  { label: 'プライバシーポリシー', href: '#' },
  { label: '利用規約', href: '#' },
  { label: '特定商取引法に基づく表記', href: '#' },
  { label: 'ヘルプ', href: '#' },
]

/**
 * 認証画面の最小フッター（auth.md §11）。1 行・画面全幅・低彩度（bg-background）。
 */
export function AuthMinimalFooter() {
  return (
    <footer className="border-border border-t">
      <nav
        aria-label="フッターナビゲーション"
        className="mx-auto flex max-w-7xl flex-col items-center gap-2 px-6 py-4 sm:flex-row sm:justify-center sm:gap-4"
      >
        <p className="text-muted-foreground text-xs">© 2026 Kivio, Inc.</p>
        <div className="flex flex-wrap items-center justify-center gap-x-4 gap-y-1">
          {FOOTER_LINKS.map(({ label, href }) => (
            <Link
              key={label}
              href={href}
              className="text-muted-foreground hover:text-foreground text-xs transition-colors duration-150"
            >
              {label}
            </Link>
          ))}
        </div>
      </nav>
    </footer>
  )
}
