'use client'
import { useAuthStore } from '@/stores/useAuthStore'
import { ChangePasswordForm } from '@/components/profile/ChangePasswordForm'

/**
 * Google ログイン専用ユーザーはパスワードを持たず、変更フォームを出しても必ず失敗するため
 * 説明表示に差し替える。
 *
 * 判定は `=== false` で行う。取得前・古い形式のキャッシュでは `hasPassword` が
 * `undefined` になり得るが、そのケースで「パスワードなし」と断定すると、パスワードを
 * 持つユーザーから変更手段を奪ってしまう。不明なときはフォームを出す側に倒す。
 */
export function PasswordSection() {
  const hasPassword = useAuthStore((state) => state.user?.hasPassword)

  if (hasPassword === false) {
    return (
      <div className="border-border bg-muted/40 text-muted-foreground rounded-lg border p-4 text-sm leading-relaxed">
        このアカウントは Google
        でログインしています。パスワードは設定されていないため、変更する項目はありません。
      </div>
    )
  }

  return <ChangePasswordForm />
}
