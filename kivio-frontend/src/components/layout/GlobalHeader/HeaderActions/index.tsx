'use client'
// Phase 2: 未認証状態のみ実装。Phase 3 以降で useAuthStore を連携しロール別に分岐する。
import { GuestActions } from './GuestActions'

export function HeaderActions() {
  return <GuestActions />
}
