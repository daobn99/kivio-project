import type { Metadata } from 'next'
import { RegisterFlow } from '@/components/auth/RegisterFlow'

export const metadata: Metadata = {
  title: '会員登録 | Kivio',
  description: 'Kivio のアカウントを作成して、日本中の個人から最高の商品を見つけましょう。',
}

export default function RegisterPage() {
  return <RegisterFlow />
}
