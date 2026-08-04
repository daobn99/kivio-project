'use client'
import Link from 'next/link'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { requestOtp } from '@/lib/api/client/auth'
import { requestOtpSchema, type RequestOtpFormValues } from '@/lib/validations/auth'
import { resolveApiError, apiErrorCode } from '@/lib/apiErrors'
import { ROUTES } from '@/lib/constants'
import { FloatingLabelInput } from '@/components/ui/FloatingLabelInput'
import { AuthSubmitButton } from '@/components/auth/AuthSubmitButton'
import { FormAlert } from '@/components/form/FormAlert'
import { FieldError } from '@/components/form/FieldError'
import { AuthDivider } from '@/components/auth/AuthDivider'
import { GoogleSignInButton } from '@/components/auth/GoogleSignInButton'

const EMAIL_STEP_ERRORS: Record<string, string> = {
  RATE_LIMIT_EXCEEDED: '送信回数が上限に達しました。しばらくしてからお試しください。',
}

interface RegisterEmailStepProps {
  defaultEmail: string
  /** 認証コード送信成功（202）。保持した email を持って Step2 へ */
  onSuccess: (email: string) => void
  /** Step3 等から差し戻された際に上部へ出す通知（セッション切れ等） */
  notice?: string
}

export function RegisterEmailStep({ defaultEmail, onSuccess, notice }: RegisterEmailStepProps) {
  const form = useForm<RequestOtpFormValues>({
    resolver: zodResolver(requestOtpSchema),
    defaultValues: { email: defaultEmail },
  })
  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors },
  } = form

  const mutation = useMutation({
    mutationFn: (values: RequestOtpFormValues) => requestOtp(values.email),
    onSuccess: () => onSuccess(getValues('email')),
  })

  // 登録済みメールはログインへ誘導するため専用 UI（リンク付き）を出す
  const isAlreadyRegistered = apiErrorCode(mutation.error) === 'EMAIL_ALREADY_REGISTERED'
  const serverError =
    mutation.isError && !isAlreadyRegistered
      ? resolveApiError(mutation.error, EMAIL_STEP_ERRORS)
      : null

  return (
    <form
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      noValidate
      className="motion-safe:*:animate-[auth-rise_200ms_ease-out_both] motion-safe:[&>*:nth-child(2)]:[animation-delay:60ms] motion-safe:[&>*:nth-child(3)]:[animation-delay:120ms] motion-safe:[&>*:nth-child(4)]:[animation-delay:180ms] motion-safe:[&>*:nth-child(5)]:[animation-delay:240ms]"
    >
      <header className="mb-8">
        <h1 className="text-foreground font-serif text-2xl font-bold">Kivioをはじめよう</h1>
        <p className="text-muted-foreground mt-2 text-sm">
          メールアドレスを入力して認証コードを送信します。
        </p>
      </header>

      {notice && (
        <div className="mb-6">
          <FormAlert>{notice}</FormAlert>
        </div>
      )}
      {isAlreadyRegistered && (
        <div className="mb-6">
          <FormAlert>
            <p>このメールアドレスは既に使用されています。</p>
            <Link
              href={`${ROUTES.auth.login}?email=${encodeURIComponent(getValues('email'))}`}
              className="text-destructive font-medium underline"
            >
              ログインはこちら
            </Link>
          </FormAlert>
        </div>
      )}
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
          autoFocus
          aria-describedby={errors.email ? 'register-email-error' : undefined}
          error={!!errors.email}
          {...register('email')}
        />
        <FieldError id="register-email-error" message={errors.email?.message} />
      </div>

      <div className="mt-6">
        <AuthSubmitButton pending={mutation.isPending}>認証コードを送信</AuthSubmitButton>
      </div>

      <div>
        <AuthDivider />
        <GoogleSignInButton mode="register" />
      </div>

      <p className="text-muted-foreground mt-8 text-center text-sm">
        既にアカウントをお持ちですか？{' '}
        <Link href={ROUTES.auth.login} className="text-accent font-medium hover:underline">
          ログイン
        </Link>
      </p>
    </form>
  )
}
