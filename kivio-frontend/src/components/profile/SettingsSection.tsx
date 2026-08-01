import { cn } from '@/lib/utils'

interface SettingsSectionProps {
  title: string
  description?: string
  children: React.ReactNode
  /** 退会セクション等、区画自体を強調する場合 */
  tone?: 'default' | 'danger'
}

/**
 * Card ではなく「上罫線 + 見出し列」で区画を切る。見出し列の幅はサイドナビと揃えて、
 * ページ左端から一貫した縦のリズムを作る。
 */
export function SettingsSection({
  title,
  description,
  children,
  tone = 'default',
}: SettingsSectionProps) {
  return (
    <section
      className={cn(
        'grid gap-4 py-8 lg:grid-cols-[minmax(0,14rem)_minmax(0,1fr)] lg:gap-10',
        tone === 'default' && 'border-border border-t first:border-t-0 first:pt-0',
        tone === 'danger' &&
          'border-destructive/30 bg-destructive/3 mt-8 rounded-xl border px-5 py-6 lg:px-6',
      )}
    >
      <div className="space-y-1.5">
        <h2
          className={cn(
            'font-serif text-lg font-bold',
            tone === 'danger' ? 'text-destructive' : 'text-foreground',
          )}
        >
          {title}
        </h2>
        {description && (
          <p className="text-muted-foreground text-sm leading-relaxed">{description}</p>
        )}
      </div>

      <div className="min-w-0">{children}</div>
    </section>
  )
}
