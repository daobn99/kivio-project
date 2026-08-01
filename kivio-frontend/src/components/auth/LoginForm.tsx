'use client'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { login } from '@/lib/api/client/auth'
import { getCurrentUser } from '@/lib/api/client/users'
import { loginSchema, type LoginFormValues } from '@/lib/validations/auth'
import { useAuthStore } from '@/stores/useAuthStore'
import { resolveApiError } from '@/lib/apiErrors'
import { ROUTES } from '@/lib/constants'
import { FloatingLabelInput } from '@/components/ui/FloatingLabelInput'
import { PasswordField } from '@/components/auth/PasswordField'
import { AuthSubmitButton } from '@/components/auth/AuthSubmitButton'
import { FormAlert } from '@/components/form/FormAlert'
import { FieldError } from '@/components/form/FieldError'
import { AuthDivider } from '@/components/auth/AuthDivider'
import { GoogleSignInButton } from '@/components/auth/GoogleSignInButton'

// ログイン失敗はメール／パスワードのどちらが誤りかを示さない（アカウント列挙攻撃の対策）
const LOGIN_ERRORS: Record<string, string> = {
  INVALID_CREDENTIALS: 'メールアドレスまたはパスワードが正しくありません。',
  USER_DEACTIVATED: 'このアカウントは利用停止中です。サポートへお問い合わせください。',
  RATE_LIMIT_EXCEEDED: '試行回数が上限に達しました。しばらくしてからお試しください。',
}

interface LoginFormProps {
  /** 登録済みメールからの誘導（?email=）等でメール欄を初期化する */
  defaultEmail?: string
}

export function LoginForm({ defaultEmail = '' }: LoginFormProps) {
  const router = useRouter()
  const setAuth = useAuthStore((state) => state.setAuth)

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: defaultEmail, password: '' },
  })
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = form

  const mutation = useMutation({
    // ログイン後、Access Token で GET /users/me を呼びユーザー情報まで取得して認証状態を確定する
    mutationFn: async (values: LoginFormValues) => {
      const tokens = await login(values.email, values.password)
      const user = await getCurrentUser(tokens.accessToken)
      return { tokens, user }
    },
    onSuccess: ({ tokens, user }) => {
      setAuth({ accessToken: tokens.accessToken, user })
      router.replace(ROUTES.home)
    },
  })

  const serverError = mutation.isError ? resolveApiError(mutation.error, LOGIN_ERRORS) : null

  return (
    <form
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      noValidate
      className="motion-safe:*:animate-[auth-rise_200ms_ease-out_both] motion-safe:[&>*:nth-child(2)]:[animation-delay:60ms] motion-safe:[&>*:nth-child(3)]:[animation-delay:120ms] motion-safe:[&>*:nth-child(4)]:[animation-delay:180ms] motion-safe:[&>*:nth-child(5)]:[animation-delay:240ms] motion-safe:[&>*:nth-child(6)]:[animation-delay:300ms]"
    >
      <header className="mb-8">
        <h1 className="text-foreground font-serif text-2xl font-bold">Kivio へようこそ</h1>
      </header>

      {serverError && (
        <div className="mb-6">
          <FormAlert>{serverError}</FormAlert>
        </div>
      )}

      <div className="space-y-1.5">
        <FloatingLabelInput
          label="メールアドレス"
          type="email"
          autoComplete="email"
          spellCheck={false}
          aria-describedby={errors.email ? 'login-email-error' : undefined}
          error={!!errors.email}
          {...register('email')}
        />
        <FieldError id="login-email-error" message={errors.email?.message} />
      </div>

      <div className="mt-4 space-y-1.5">
        <PasswordField
          label="パスワード"
          autoComplete="current-password"
          aria-describedby={errors.password ? 'login-password-error' : undefined}
          error={!!errors.password}
          {...register('password')}
        />
        <FieldError id="login-password-error" message={errors.password?.message} />
        <div className="flex justify-end">
          {/* リセット機能は未実装のため href="#" */}
          <Link href="#" className="text-accent text-sm hover:underline">
            パスワードを忘れた方
          </Link>
        </div>
      </div>

      <div className="mt-6">
        <AuthSubmitButton pending={mutation.isPending}>ログイン</AuthSubmitButton>
      </div>

      <div>
        <AuthDivider />
        <GoogleSignInButton mode="login" />
      </div>

      <p className="text-muted-foreground mt-8 text-center text-sm">
        アカウントをお持ちでない方は{' '}
        <Link href={ROUTES.auth.register} className="text-accent font-medium hover:underline">
          会員登録
        </Link>
      </p>
    </form>
  )
}
