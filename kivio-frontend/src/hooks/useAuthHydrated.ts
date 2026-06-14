'use client'
import { useSyncExternalStore } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'

/**
 * Zustand persist の復元完了をハイドレーション安全に購読する。
 *
 * SSR / 初回ハイドレーションは false（＝未認証として描画）、復元完了後に true へ切り替わる。
 * これにより hydration mismatch を起こさずに、永続化された認証状態へ移行できる。
 * 認証状態で表示が分岐するヘッダー系コンポーネント（HeaderActions / MobileMenuSheet）で共用する。
 */
export function useAuthHydrated(): boolean {
  return useSyncExternalStore(
    (onStoreChange) => useAuthStore.persist.onFinishHydration(onStoreChange),
    () => useAuthStore.persist.hasHydrated(),
    () => false,
  )
}
