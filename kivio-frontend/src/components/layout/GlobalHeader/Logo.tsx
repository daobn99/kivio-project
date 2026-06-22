import Image from 'next/image'
import Link from 'next/link'
import { cn } from '@/lib/utils'

interface LogoProps {
  className?: string
}

export function Logo({ className }: LogoProps) {
  return (
    <Link
      href="/"
      className={cn(
        'focus-visible:ring-ring flex shrink-0 items-center gap-2 rounded-md focus-visible:ring-2 focus-visible:outline-none',
        className,
      )}
    >
      <Image
        src="/images/kivio-logo.svg"
        alt=""
        width={36}
        height={36}
        priority
        className="h-7 w-7 md:h-8 md:w-8 lg:h-9 lg:w-9"
      />
      <span className="text-primary font-serif text-xl leading-none font-bold">Kivio</span>
    </Link>
  )
}
