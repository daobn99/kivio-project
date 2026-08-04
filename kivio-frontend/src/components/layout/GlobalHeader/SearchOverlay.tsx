'use client'
import { Search, ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useEffect, useRef } from 'react'

interface SearchOverlayProps {
  isOpen: boolean
  onClose: () => void
}

export function SearchOverlay({ isOpen, onClose }: SearchOverlayProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (isOpen) inputRef.current?.focus()
  }, [isOpen])

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    if (isOpen) document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <div className="bg-background fixed inset-0 z-50 flex flex-col">
      <div className="border-border flex h-14 items-center gap-2 border-b px-4">
        <Button variant="ghost" size="icon" aria-label="検索を閉じる" onClick={onClose}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <form
          className="border-border bg-muted focus-within:ring-ring flex h-10 flex-1 items-center gap-2 rounded-full border px-4 focus-within:ring-2"
          onSubmit={(e) => e.preventDefault()}
        >
          <Search className="text-muted-foreground h-4 w-4 shrink-0" />
          <input
            ref={inputRef}
            type="search"
            placeholder="キーワード、ブランド、ショップ名で探す"
            className="placeholder:text-muted-foreground min-w-0 flex-1 bg-transparent text-sm outline-none"
            aria-label="商品を検索"
          />
        </form>
        <button
          type="submit"
          form="search-form"
          className="bg-accent text-accent-foreground hover:bg-accent/90 h-9 shrink-0 rounded-full px-4 text-sm font-medium transition-colors duration-150"
          aria-label="検索"
        >
          検索
        </button>
      </div>
      {/* Phase 3: 最近の検索・人気キーワード */}
    </div>
  )
}
