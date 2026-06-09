'use client'
import Link from 'next/link'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { Menu } from 'lucide-react'

const categories = [
  { label: 'ファッション', href: '/search?category=fashion' },
  { label: '家電・スマホ', href: '/search?category=electronics' },
  { label: '本・CD・DVD', href: '/search?category=books' },
  { label: 'おもちゃ・趣味', href: '/search?category=hobbies' },
  { label: 'スポーツ・アウトドア', href: '/search?category=sports' },
  { label: 'インテリア・家具', href: '/search?category=interior' },
]

export function MobileMenuSheet() {
  return (
    <Sheet>
      <SheetTrigger render={<Button variant="ghost" size="icon" aria-label="メニューを開く" />}>
        <Menu className="h-5 w-5" />
      </SheetTrigger>
      <SheetContent side="left" className="w-72 p-0">
        <SheetHeader className="border-border border-b px-4 pt-4 pb-3">
          <SheetTitle className="text-primary font-serif">Kivio</SheetTitle>
        </SheetHeader>

        <div className="space-y-6 p-4">
          {/* 認証リンク */}
          <div className="flex flex-col gap-2">
            <Link
              href="/auth/login"
              className="border-border text-foreground hover:bg-muted w-full rounded-lg border px-4 py-2 text-center text-sm font-medium transition-colors duration-150"
            >
              ログイン
            </Link>
            <Link
              href="/auth/register"
              className="bg-accent text-accent-foreground hover:bg-accent/90 w-full rounded-lg px-4 py-2 text-center text-sm font-medium transition-colors duration-150"
            >
              会員登録
            </Link>
          </div>

          {/* カテゴリ */}
          <div>
            <p className="text-muted-foreground mb-2 text-xs font-semibold tracking-wider uppercase">
              カテゴリ
            </p>
            <ul className="space-y-1">
              {categories.map(({ label, href }) => (
                <li key={label}>
                  <Link
                    href={href}
                    className="text-foreground hover:bg-muted block rounded-md px-3 py-2 text-sm transition-colors duration-150"
                  >
                    {label}
                  </Link>
                </li>
              ))}
              <li>
                <Link
                  href="/search"
                  className="text-accent hover:bg-muted block rounded-md px-3 py-2 text-sm font-medium transition-colors duration-150"
                >
                  すべてのカテゴリ →
                </Link>
              </li>
            </ul>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  )
}
