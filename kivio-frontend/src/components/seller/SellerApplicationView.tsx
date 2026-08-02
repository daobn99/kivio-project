'use client'
import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2, RotateCcw } from 'lucide-react'
import { useAuthHydrated } from '@/hooks/useAuthHydrated'
import { useAuthStore } from '@/stores/useAuthStore'
import { useSellerApplicationQuery } from '@/hooks/queries/useSellerApplicationQuery'
import { ROUTES } from '@/lib/constants'
import type { AuthUser, SellerApplication } from '@/types/api'
import { SellerApplicationStatus, UserRole } from '@/types/enums'
import { Button } from '@/components/ui/button'
import { FormAlert } from '@/components/form/FormAlert'
import { SaveStatus } from '@/components/form/SaveStatus'
import { SellerApplicationForm } from '@/components/seller/SellerApplicationForm'
import { SellerApplicationIntro } from '@/components/seller/SellerApplicationIntro'
import { SellerApplicationSkeleton } from '@/components/seller/SellerApplicationSkeleton'
import { SellerApplicationStatusCard } from '@/components/seller/SellerApplicationStatusCard'

/**
 * 申請の必要が無い（もしくは行えない）利用者への説明。null なら申請画面を出す。
 * 到達経路は違っても結論は同じ「この画面に用は無い」なので、文言だけを出し分ける。
 */
function resolveRedirectMessage(
  user: AuthUser,
  application: SellerApplication | null | undefined,
): string | null {
  if (user.role === UserRole.ROLE_SELLER) {
    return 'すでに出品者として登録されています。トップページへ移動します。'
  }
  if (user.role === UserRole.ROLE_ADMIN) {
    return '管理者アカウントではセラー申請を行えません。トップページへ移動します。'
  }
  if (application?.status === SellerApplicationStatus.APPROVED) {
    return '申請は承認済みです。トップページへ移動します。'
  }
  return null
}

/**
 * 通知帯・エラーの出現。`globals.css` の既存 keyframes を使い回し、新規 keyframes を足さない。
 * 「いつの間にか出ていた」を避けるための 150ms で、状態の入れ替え自体には何も付けない。
 */
const ALERT_ENTER = 'motion-safe:animate-[auth-fade_150ms_ease-out]'

/** 遷移までの数フレームを空白にしない。無言でページが変わると事故か意図か判別できない */
function RedirectNotice({ message }: { message: string }) {
  return (
    <p role="status" className="text-muted-foreground flex items-center gap-2 py-10 text-sm">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      {message}
    </p>
  )
}

/**
 * 1 つの URL に同居する 4 状態（未申請 / 審査中 / 却下 / 承認済み）の出し分け。
 * 分岐の順序には意味がある — 入れ替えると誤リダイレクトやフォームの一瞬の表示が起きる。
 */
export function SellerApplicationView() {
  const router = useRouter()
  const hydrated = useAuthHydrated()
  const user = useAuthStore((state) => state.user)
  // BUYER 以外には画面を見せないため、そもそも取得しない
  const query = useSellerApplicationQuery(hydrated && user?.role === UserRole.ROLE_BUYER)

  const [savedAt, setSavedAt] = useState(0)
  const [conflict, setConflict] = useState('')
  const statusRef = useRef<HTMLElement>(null)
  const focusStatus = useRef(false)

  // ストア復元前に判定すると user が null のまま「BUYER ではない」と誤判定してしまう
  const redirectMessage = hydrated && user ? resolveRedirectMessage(user, query.data) : null

  useEffect(() => {
    // レンダー中に呼ぶと Strict Mode で二重実行される。push だと戻るたびに再リダイレクトになる
    if (redirectMessage) router.replace(ROUTES.seller.applicationRedirect)
  }, [redirectMessage, router])

  // 送信成功でフォームが消えるとフォーカスが body に落ちる。カードが現れた時点で拾い直す
  useEffect(() => {
    if (focusStatus.current && statusRef.current) {
      focusStatus.current = false
      statusRef.current.focus()
    }
  })

  if (!hydrated || !user) return <SellerApplicationSkeleton />
  if (redirectMessage) return <RedirectNotice message={redirectMessage} />
  if (query.isPending) return <SellerApplicationSkeleton />

  if (query.isError) {
    return (
      <div className="mt-8 space-y-4">
        <FormAlert className={ALERT_ENTER}>
          申請状況を取得できませんでした。時間をおいて再度お試しください。
        </FormAlert>
        {/* 再試行はページリロードではなく refetch。reload では認証ストアの復元まで巻き戻る */}
        <Button variant="outline" className="h-11" onClick={() => query.refetch()}>
          <RotateCcw className="h-4 w-4" aria-hidden />
          再読み込み
        </Button>
      </div>
    )
  }

  const application = query.data
  const canApply = application === null || application.status === SellerApplicationStatus.REJECTED

  const onSubmitted = () => {
    setConflict('')
    focusStatus.current = true
    setSavedAt(Date.now())
  }

  return (
    <div className="mt-8 space-y-8">
      {(conflict || savedAt !== 0) && (
        <div className="space-y-3">
          {conflict && <FormAlert className={ALERT_ENTER}>{conflict}</FormAlert>}
          {savedAt !== 0 && <SaveStatus signal={savedAt} message="申請を送信しました" />}
        </div>
      )}

      {application === null && <SellerApplicationIntro />}
      {application !== null && (
        <SellerApplicationStatusCard application={application} ref={statusRef} />
      )}

      {canApply && (
        <SellerApplicationForm
          mode={application === null ? 'create' : 'reapply'}
          onSubmitted={onSubmitted}
          onConflict={setConflict}
        />
      )}
    </div>
  )
}
