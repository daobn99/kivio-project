import type { Metadata } from 'next'
import { SettingsSection } from '@/components/profile/SettingsSection'
import { ProfileSettingsForm } from '@/components/profile/ProfileSettingsForm'
import { AccountInfoSection } from '@/components/profile/AccountInfoSection'
import { PasswordSection } from '@/components/profile/PasswordSection'
import { WithdrawSection } from '@/components/profile/WithdrawSection'

export const metadata: Metadata = {
  title: 'プロフィール設定 | Kivio',
}

/** セクションは更新頻度が高い順 → 破壊的な順に並べる。保存はセクション単位で完結する。 */
export default function ProfileSettingsPage() {
  return (
    <div>
      <SettingsSection
        title="プロフィール"
        description="表示名とアバター画像を変更します。表示名は商品ページやレビューに表示されます。"
      >
        <ProfileSettingsForm />
      </SettingsSection>

      <SettingsSection title="アカウント情報" description="ログインに使う情報と現在の権限です。">
        <AccountInfoSection />
      </SettingsSection>

      <SettingsSection
        title="パスワード"
        description="定期的な変更をおすすめします。変更後も他の端末のログインは維持されます。"
      >
        <PasswordSection />
      </SettingsSection>

      <SettingsSection
        tone="danger"
        title="退会"
        description="アカウントを閉じます。この操作は取り消せません。"
      >
        <WithdrawSection />
      </SettingsSection>
    </div>
  )
}
