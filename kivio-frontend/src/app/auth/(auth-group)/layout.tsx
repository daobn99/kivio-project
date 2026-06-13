import { AuthBrandPanel } from '@/components/auth/AuthBrandPanel'
import { AuthMobileLogo } from '@/components/auth/AuthMobileLogo'
import { AuthMinimalFooter } from '@/components/auth/AuthMinimalFooter'

/**
 * 認証系レイアウト（design-system/pages/auth.md §3）。
 * グローバルヘッダー / フッター / モバイルナビは使わず、専用の最小 chrome を持つ。
 * 縦 2 段構成: 上 = Split 行（flex-1）／下 = 全幅フッター。
 * `auth/` は実パスセグメント、`(auth-group)` は URL に影響しないレイアウトグループ
 * （FRONTEND_IA.md・URL は /auth/login・/auth/register）。
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-background flex min-h-dvh flex-col">
      <a
        href="#main-content"
        className="focus:bg-background focus:ring-ring sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-1000 focus:rounded-md focus:px-4 focus:py-2 focus:ring-2"
      >
        メインコンテンツへスキップ
      </a>

      {/* Split 行: 画面の上部いっぱいを占める */}
      <div className="grid flex-1 md:grid-cols-2 lg:grid-cols-12">
        {/* 左: ブランドパネル（md 未満は非表示） */}
        <AuthBrandPanel className="hidden md:flex lg:col-span-5" />

        {/* 右: フォームカラム */}
        <div className="flex flex-col lg:col-span-7">
          <AuthMobileLogo className="md:hidden" />
          <main id="main-content" className="flex flex-1 items-center justify-center px-6 py-10">
            <div className="w-full max-w-100">{children}</div>
          </main>
        </div>
      </div>

      {/* 最小フッター: 画面全幅（Split 行の外・最下段） */}
      <AuthMinimalFooter />
    </div>
  )
}
