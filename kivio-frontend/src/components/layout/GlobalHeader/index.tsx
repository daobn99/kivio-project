'use client'
import { useState } from 'react'
import { useScrolled } from '@/hooks/useScrolled'
import { AnnouncementBar } from './AnnouncementBar'
import { Logo } from './Logo'
import { SearchBar } from './SearchBar'
import { SearchOverlay } from './SearchOverlay'
import { CategoryNav } from './CategoryNav'
import { MobileMenuSheet } from './MobileMenuSheet'
import { MobileSearchButton } from './MobileSearchButton'
import { HeaderActions } from './HeaderActions'

export function GlobalHeader() {
  const scrolled = useScrolled()
  const [searchOpen, setSearchOpen] = useState(false)

  return (
    <>
      <AnnouncementBar />

      <header
        data-scrolled={scrolled}
        className="bg-background sticky top-0 z-20 transition-shadow duration-150 data-[scrolled=true]:shadow-[0_2px_12px_rgba(30,58,95,0.08)]"
      >
        {/* HeaderMain 行 */}
        <div className="border-border border-b lg:border-b-0">
          {/* デスクトップ */}
          <div className="mx-auto hidden h-16 max-w-7xl grid-cols-[auto_1fr_auto] items-center gap-6 px-6 lg:grid">
            <Logo />
            <SearchBar className="w-full" />
            <HeaderActions />
          </div>

          {/* タブレット 上段 */}
          <div className="mx-auto hidden h-14 max-w-7xl items-center justify-between gap-4 px-6 md:flex lg:hidden">
            <Logo />
            <HeaderActions />
          </div>
          {/* タブレット 検索バー第2行 */}
          <div className="hidden px-4 pb-2 md:block lg:hidden">
            <SearchBar className="w-full" />
          </div>

          {/* モバイル */}
          <div className="relative flex h-14 items-center justify-between px-4 md:hidden">
            <MobileMenuSheet />
            <Logo className="absolute left-1/2 -translate-x-1/2" />
            <MobileSearchButton onClick={() => setSearchOpen(true)} />
          </div>
        </div>

        <CategoryNav />
      </header>

      <SearchOverlay isOpen={searchOpen} onClose={() => setSearchOpen(false)} />
    </>
  )
}
