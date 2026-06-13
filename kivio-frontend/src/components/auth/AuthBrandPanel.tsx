import Image from 'next/image'
import Link from 'next/link'
import { cn } from '@/lib/utils'

interface AuthBrandPanelProps {
  className?: string
}

/**
 * 認証画面の左ブランドパネル（auth.md §5）。md 以上で表示。
 * 濃紺地に奥行きレイヤー（シェーディング・accent グロー・微細グレイン）を重ね、
 * ロゴ + タグライン + イラストを配置する。装飾はすべて aria-hidden / alt=""。
 */
export function AuthBrandPanel({ className }: AuthBrandPanelProps) {
  return (
    <aside
      className={cn(
        'bg-primary text-primary-foreground relative flex-col justify-between overflow-hidden p-10',
        className,
      )}
    >
      {/* 奥行きレイヤー（すべて装飾） */}
      <div
        aria-hidden
        className="absolute inset-0 bg-linear-to-b from-white/5 via-transparent to-black/25"
      />
      <div
        aria-hidden
        className="bg-accent/15 absolute -top-20 -right-24 h-80 w-80 rounded-full blur-3xl"
      />
      <div
        aria-hidden
        className="bg-accent/10 absolute -bottom-28 -left-16 h-96 w-96 rounded-full blur-3xl"
      />
      <div
        aria-hidden
        className="absolute inset-0 bg-[url('/images/noise.svg')] bg-size-[180px] opacity-[0.12] mix-blend-soft-light"
      />

      {/* 上部: ロゴ + タグライン */}
      <div className="relative z-10 space-y-6">
        <Link
          href="/"
          className="focus-visible:ring-primary-foreground/60 flex w-fit items-center gap-2 rounded-md focus-visible:ring-2 focus-visible:outline-none"
        >
          <Image
            src="/images/kivio-logo.svg"
            alt=""
            width={36}
            height={36}
            className="h-9 w-9 brightness-0 invert"
            priority
          />
          <span className="font-serif text-2xl leading-none font-bold">Kivio</span>
        </Link>
        <div className="space-y-4">
          <p className="font-serif text-2xl leading-snug font-bold tracking-tight text-balance lg:text-3xl">
            日本中の個人から、
            <br />
            最高の商品を。
          </p>
          <span aria-hidden className="bg-accent block h-0.5 w-12 rounded-full" />
        </div>
      </div>

      {/* 下部: イラスト */}
      <div className="relative z-10 mt-8 flex justify-center">
        <Image
          src="/images/login-illustration.png"
          alt=""
          width={520}
          height={780}
          className="h-auto w-full max-w-90 rounded-2xl"
          priority
        />
      </div>
    </aside>
  )
}
