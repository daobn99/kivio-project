import { Button } from '@/components/ui/button'
import { Menu } from 'lucide-react'

interface MobileMenuButtonProps {
  onClick?: () => void
}

export function MobileMenuButton({ onClick }: MobileMenuButtonProps) {
  return (
    <Button variant="ghost" size="icon" aria-label="メニューを開く" onClick={onClick}>
      <Menu className="h-5 w-5" />
    </Button>
  )
}
