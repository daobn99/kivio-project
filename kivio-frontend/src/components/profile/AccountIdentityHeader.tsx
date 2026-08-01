'use client'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuthStore } from '@/stores/useAuthStore'
import { ROLE_LABEL, formatYearMonth } from '@/lib/format'

/**
 * ページ内で唯一の塗り面。「今どのアカウントを編集しているか」をここで確定させ、
 * 以降のセクションは白地 + 罫線で構成する。
 *
 * ユーザー情報はストアを正とし、ここで再フェッチしない（更新は各フォームが反映する）。
 */
export function AccountIdentityHeader() {
  const user = useAuthStore((state) => state.user)

  if (!user) return <AccountIdentityHeaderSkeleton />

  return (
    <header className="bg-secondary/60 border-border border-y">
      <div className="mx-auto flex max-w-7xl items-center gap-4 px-6 py-6 sm:gap-5 sm:py-8">
        <Avatar className="ring-background size-12 shrink-0 ring-2 sm:size-16">
          <AvatarImage src={user.avatarUrl ?? undefined} alt="" />
          <AvatarFallback className="bg-primary text-primary-foreground font-serif text-lg">
            {user.displayName.slice(0, 1)}
          </AvatarFallback>
        </Avatar>

        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <h1 className="text-foreground truncate font-serif text-xl font-bold sm:text-2xl">
              {user.displayName}
            </h1>
            <Badge variant="secondary" className="bg-background text-primary border-border border">
              {ROLE_LABEL[user.role]}
            </Badge>
          </div>
          <p className="text-muted-foreground truncate text-sm">
            {user.email}
            <span className="hidden sm:inline"> · {formatYearMonth(user.createdAt)}から利用</span>
          </p>
        </div>
      </div>
    </header>
  )
}

/** ハイドレート前のプレースホルダー。帯の高さを維持して CLS を防ぐ */
function AccountIdentityHeaderSkeleton() {
  return (
    <header className="bg-secondary/60 border-border border-y">
      <div className="mx-auto flex max-w-7xl items-center gap-4 px-6 py-6 sm:gap-5 sm:py-8">
        <Skeleton className="size-12 shrink-0 rounded-full sm:size-16" />
        <div className="space-y-2">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-4 w-56" />
        </div>
      </div>
    </header>
  )
}
