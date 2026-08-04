'use client'
import { signIn } from 'next-auth/react'
import { Button } from '@/components/ui/button'
import { GoogleIcon } from '@/components/auth/GoogleIcon'

interface GoogleSignInButtonProps {
  /** ラベルの出し分け。login: 「Google で続行」 / register: 「Google で登録」 */
  mode: 'login' | 'register'
}

/**
 * Google サインインボタン。NextAuth の signIn("google") を呼ぶ。
 * outline バリアントで accent CTA と差別化し、メール導線を主・ソーシャルを副に見せる。
 */
export function GoogleSignInButton({ mode }: GoogleSignInButtonProps) {
  return (
    <Button
      type="button"
      variant="outline"
      className="border-border h-11 w-full gap-2 font-medium"
      onClick={() => signIn('google')}
    >
      <GoogleIcon className="h-4 w-4" />
      Google で{mode === 'login' ? '続行' : '登録'}
    </Button>
  )
}
