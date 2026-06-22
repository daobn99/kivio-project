'use client'
import { Search } from 'lucide-react'
import { cn } from '@/lib/utils'

interface SearchBarProps {
  className?: string
}

export function SearchBar({ className }: SearchBarProps) {
  return (
    <form
      className={cn(
        'border-border bg-background focus-within:ring-ring flex h-10 w-full max-w-140 items-center gap-2 rounded-full border px-4 transition-shadow duration-150 focus-within:ring-2',
        className,
      )}
      onSubmit={(e) => e.preventDefault()}
    >
      <Search className="text-muted-foreground h-4 w-4 shrink-0" />
      <input
        type="search"
        placeholder="キーワード、ブランド、ショップ名で探す"
        className="placeholder:text-muted-foreground min-w-0 flex-1 bg-transparent text-sm outline-none"
        aria-label="商品を検索"
      />
      <button
        type="submit"
        className="bg-accent text-accent-foreground hover:bg-accent/90 h-7 shrink-0 rounded-full px-4 text-sm font-medium transition-colors duration-150"
        aria-label="検索"
      >
        検索
      </button>
    </form>
  )
}
