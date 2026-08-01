import { UserRole } from '@/types/enums'

/** ロールの表示名。ADMIN も含め全ロールを網羅する（アカウント設定は全ロールが訪れる） */
export const ROLE_LABEL: Record<UserRole, string> = {
  [UserRole.ROLE_BUYER]: '購入者',
  [UserRole.ROLE_SELLER]: '販売者',
  [UserRole.ROLE_ADMIN]: '管理者',
}

const JST = 'Asia/Tokyo'

/** ISO 8601 UTC → `2026年5月24日`。日時は UTC で届き、表示は JST に落とす */
export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('ja-JP', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: JST,
  }).format(new Date(iso))
}

/** ISO 8601 UTC → `2026年5月`（アカウント利用開始月の表示用） */
export function formatYearMonth(iso: string): string {
  return new Intl.DateTimeFormat('ja-JP', {
    year: 'numeric',
    month: 'long',
    timeZone: JST,
  }).format(new Date(iso))
}

/** 郵便番号を `〒150-0002` 形式に整える。ハイフン無しで保存された値にも対応する */
export function formatPostalCode(postalCode: string): string {
  const digits = postalCode.replace(/-/g, '')
  return digits.length === 7 ? `〒${digits.slice(0, 3)}-${digits.slice(3)}` : `〒${postalCode}`
}
