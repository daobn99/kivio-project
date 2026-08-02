'use client'
import { useCallback, useSyncExternalStore } from 'react'

/**
 * メディアクエリの一致状態を購読する。
 *
 * CSS だけでは切り替えられない「コンポーネントの prop」を画面幅で変える用途に使う
 * （例: Sheet の `side` をモバイルは bottom・デスクトップは right にする）。
 * SSR 時は常に false を返すため、初期描画に影響する箇所では使わないこと。
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (onStoreChange: () => void) => {
      const mql = window.matchMedia(query)
      mql.addEventListener('change', onStoreChange)
      return () => mql.removeEventListener('change', onStoreChange)
    },
    [query],
  )

  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(query).matches,
    () => false,
  )
}
