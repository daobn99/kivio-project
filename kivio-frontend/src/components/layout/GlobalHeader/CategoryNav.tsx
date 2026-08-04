import Link from 'next/link'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { LayoutGrid, ChevronDown } from 'lucide-react'

const categories = [
  { label: 'ファッション', href: '/search?category=fashion' },
  { label: '家電・スマホ', href: '/search?category=electronics' },
  { label: '本・CD・DVD', href: '/search?category=books' },
  { label: 'おもちゃ・趣味', href: '/search?category=hobbies' },
  { label: 'スポーツ・アウトドア', href: '/search?category=sports' },
  { label: 'インテリア・家具', href: '/search?category=interior' },
]

const quickLinks = [
  { label: 'タイムセール', href: '/#sale' },
  { label: '新着', href: '/#new' },
  { label: 'ランキング', href: '/#ranking' },
  { label: '特集', href: '/#feature' },
]

export function CategoryNav() {
  return (
    <nav aria-label="カテゴリナビゲーション" className="border-border hidden border-b lg:block">
      <div className="mx-auto flex h-10 max-w-7xl items-center gap-1 px-6">
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <Button variant="ghost" size="sm" className="text-foreground gap-1.5 font-medium" />
            }
          >
            <LayoutGrid className="h-4 w-4" />
            カテゴリ
            <ChevronDown className="h-3 w-3" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" className="min-w-48">
            {categories.map(({ label, href }) => (
              <DropdownMenuItem key={label} render={<Link href={href} />}>
                {label}
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
            <DropdownMenuItem render={<Link href="/search" />}>すべてのカテゴリ</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        {/* 縦区切り線 */}
        <div className="bg-border mx-2 h-4 w-px" aria-hidden />

        {quickLinks.map(({ label, href }) => (
          <Link
            key={label}
            href={href}
            className="text-muted-foreground hover:text-foreground hover:bg-muted rounded-md px-3 py-1 text-sm transition-colors duration-150"
          >
            {label}
          </Link>
        ))}
      </div>
    </nav>
  )
}
