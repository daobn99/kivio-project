import { AuthBrandPanel } from '@/components/auth/AuthBrandPanel'
import { AuthMobileLogo } from '@/components/auth/AuthMobileLogo'
import { AuthMinimalFooter } from '@/components/auth/AuthMinimalFooter'
import { AuthSessionProvider } from '@/components/providers/AuthSessionProvider'
import { GoogleAuthBridge } from '@/components/auth/GoogleAuthBridge'

/**
 * 認証系レイアウト
 * Google サインイン（signIn/useSession）を使うため SessionProvider をこの配下に限定して適用し、
 * Google OAuth 完了後の id_token 交換を GoogleAuthBridge で行う。
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthSessionProvider>
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
              <div className="w-full max-w-100">
                {/* Google OAuth 完了後の id_token 交換・進行表示・エラー表示 */}
                <GoogleAuthBridge />
                {children}
              </div>
            </main>
          </div>
        </div>

        {/* 最小フッター: 画面全幅（Split 行の外・最下段） */}
        <AuthMinimalFooter />
      </div>
    </AuthSessionProvider>
  )
}
