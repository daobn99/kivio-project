import Image from 'next/image'
import Link from 'next/link'

const serviceLinks = [
  { label: 'セラー登録', href: '/seller/applications/new' },
  { label: '手数料について', href: '#' },
  { label: '安全なお取引', href: '#' },
]

const supportLinks = [
  { label: 'よくある質問', href: '#' },
  { label: 'お問い合わせ', href: '#' },
  { label: '利用ガイド', href: '#' },
]

const companyLinks = [
  { label: '会社概要', href: '#' },
  { label: '採用情報', href: '#' },
  { label: 'プレスリリース', href: '#' },
]

const legalLinks = [
  { label: 'プライバシーポリシー', href: '#' },
  { label: '利用規約', href: '#' },
  { label: '特定商取引法に基づく表記', href: '#' },
]

function FooterLinkGroup({
  title,
  links,
}: {
  title: string
  links: { label: string; href: string }[]
}) {
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold">{title}</h3>
      <ul className="space-y-2">
        {links.map(({ label, href }) => (
          <li key={label}>
            <Link
              href={href}
              className="text-primary-foreground/70 hover:text-primary-foreground text-sm transition-colors duration-150"
            >
              {label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

export function GlobalFooter() {
  return (
    <footer className="bg-primary text-primary-foreground">
      <nav aria-label="フッターナビゲーション">
        <div className="mx-auto max-w-7xl px-6 py-12">
          <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-4">
            {/* Brand */}
            <div className="space-y-4">
              <Link href="/" className="flex items-center gap-2">
                <Image
                  src="/images/kivio-logo.svg"
                  alt=""
                  width={32}
                  height={32}
                  className="brightness-0 invert"
                />
                <span className="font-serif text-xl font-bold">Kivio</span>
              </Link>
              <p className="text-primary-foreground/70 text-sm leading-relaxed">
                日本中の個人から
                <br />
                最高の商品を。
              </p>
            </div>

            <FooterLinkGroup title="サービス" links={serviceLinks} />
            <FooterLinkGroup title="サポート" links={supportLinks} />
            <FooterLinkGroup title="会社情報" links={companyLinks} />
          </div>
        </div>

        {/* Bottom bar */}
        <div className="border-primary-foreground/20 border-t">
          <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 px-6 py-4 sm:flex-row">
            <p className="text-primary-foreground/60 text-xs">
              © 2026 Kivio, Inc. All rights reserved.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-4">
              {legalLinks.map(({ label, href }) => (
                <Link
                  key={label}
                  href={href}
                  className="text-primary-foreground/60 hover:text-primary-foreground text-xs transition-colors duration-150"
                >
                  {label}
                </Link>
              ))}
            </div>
          </div>
        </div>
      </nav>
    </footer>
  )
}
