import Image from 'next/image'
import Link from 'next/link'
import { cn } from '@/lib/utils'

interface AuthMobileLogoProps {
  className?: string
}

/**
 * モバイル（< md）上部中央のロゴ。
 * 認証を中断してトップへ戻る正規の出口を 1 つだけ残すため `/` へリンクする。
 */
export function AuthMobileLogo({ className }: AuthMobileLogoProps) {
  return (
    <div className={cn('flex justify-center pt-8 pb-2', className)}>
      <Link
        href="/"
        className="focus-visible:ring-ring flex items-center gap-2 rounded-md focus-visible:ring-2 focus-visible:outline-none"
      >
        <Image src="/images/kivio-logo.svg" alt="" width={32} height={32} priority />
        <span className="text-primary font-serif text-xl leading-none font-bold">Kivio</span>
      </Link>
    </div>
  )
}
