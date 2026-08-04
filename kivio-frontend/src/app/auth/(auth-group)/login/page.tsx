import type { Metadata } from 'next'
import { LoginForm } from '@/components/auth/LoginForm'

export const metadata: Metadata = {
  title: 'ログイン | Kivio',
  description: 'Kivio にログインして、日本中の個人から最高の商品を見つけましょう。',
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ email?: string }>
}) {
  // 会員登録で「登録済み」と判定された際の ?email= を引き継ぎ、メール欄を初期化する
  const { email } = await searchParams
  return <LoginForm defaultEmail={email} />
}
