'use client'
import { Button } from '@/components/ui/button'
import { Search } from 'lucide-react'

interface MobileSearchButtonProps {
  onClick: () => void
}

export function MobileSearchButton({ onClick }: MobileSearchButtonProps) {
  return (
    <Button variant="ghost" size="icon" aria-label="検索" onClick={onClick}>
      <Search className="h-5 w-5" />
    </Button>
  )
}
