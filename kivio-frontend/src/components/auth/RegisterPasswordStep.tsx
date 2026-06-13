'use client'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { completeRegistration } from '@/lib/api/client/auth'
import {
  completeRegistrationSchema,
  type CompleteRegistrationFormValues,
} from '@/lib/validations/auth'
import { useAuthStore } from '@/stores/useAuthStore'
import { authErrorCode, resolveAuthError } from '@/lib/authErrors'
import { ROUTES } from '@/lib/constants'
import { FloatingLabelInput } from '@/components/ui/FloatingLabelInput'
import { PasswordField } from '@/components/auth/PasswordField'
import { AuthSubmitButton } from '@/components/auth/AuthSubmitButton'
import { FormAlert } from '@/components/auth/FormAlert'
import { FieldError } from '@/components/auth/FieldError'

const COMPLETE_ERRORS: Record<string, string> = {
  RATE_LIMIT_EXCEEDED: '試行回数が上限に達しました。しばらくしてからお試しください。',
}

interface RegisterPasswordStepProps {
  registrationToken: string
  /**
   * registrationToken の失効（30分 TTL 切れ）や complete 時のメール重複で Step1 へ戻す。
   * @param notice Step1 上部に表示する通知文言
   */
  onSessionInvalid: (notice: string) => void
}

export function RegisterPasswordStep({
  registrationToken,
  onSessionInvalid,
}: RegisterPasswordStepProps) {
  const router = useRouter()
  const setAccessToken = useAuthStore((state) => state.setAccessToken)

  const form = useForm<CompleteRegistrationFormValues>({
    resolver: zodResolver(completeRegistrationSchema),
    defaultValues: { displayName: '', password: '', passwordConfirm: '' },
  })
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = form

  const mutation = useMutation({
    mutationFn: (values: CompleteRegistrationFormValues) =>
      completeRegistration({
        registrationToken,
        password: values.password,
        passwordConfirm: values.passwordConfirm,
        displayName: values.displayName?.trim() ? values.displayName.trim() : undefined,
      }),
    onSuccess: (tokens) => {
      setAccessToken(tokens.accessToken)
      router.replace(ROUTES.home)
    },
    onError: (err) => {
      const code = authErrorCode(err)
      if (code === 'REGISTRATION_SESSION_INVALID') {
        onSessionInvalid('セッションの有効期限が切れました。最初からやり直してください。')
      } else if (code === 'EMAIL_ALREADY_REGISTERED') {
        onSessionInvalid('このメールアドレスは登録済みです。ログインしてください。')
      }
    },
  })

  // セッション切れ等は Step1 へ差し戻すため、ここでは残りのコードのみ表示する
  const serverError =
    mutation.isError &&
    authErrorCode(mutation.error) !== 'REGISTRATION_SESSION_INVALID' &&
    authErrorCode(mutation.error) !== 'EMAIL_ALREADY_REGISTERED'
      ? resolveAuthError(mutation.error, COMPLETE_ERRORS)
      : null

  return (
    <form
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      noValidate
      className="motion-safe:*:animate-[auth-rise_200ms_ease-out_both] motion-safe:[&>*:nth-child(2)]:[animation-delay:60ms] motion-safe:[&>*:nth-child(3)]:[animation-delay:120ms] motion-safe:[&>*:nth-child(4)]:[animation-delay:180ms] motion-safe:[&>*:nth-child(5)]:[animation-delay:240ms]"
    >
      <header className="mb-8">
        <h1 className="text-foreground font-serif text-2xl font-bold">パスワードを設定</h1>
        <p className="text-muted-foreground mt-2 text-sm">あと少しで完了です</p>
      </header>

      {serverError && (
        <div className="mb-6">
          <FormAlert>{serverError}</FormAlert>
        </div>
      )}

      <div className="space-y-1.5">
        <FloatingLabelInput
          label="表示名（任意）"
          type="text"
          autoComplete="nickname"
          aria-describedby={errors.displayName ? 'register-displayname-error' : undefined}
          error={!!errors.displayName}
          {...register('displayName')}
        />
        <FieldError id="register-displayname-error" message={errors.displayName?.message} />
      </div>

      <div className="mt-4 space-y-1.5">
        <PasswordField
          label="パスワード"
          autoComplete="new-password"
          aria-describedby={errors.password ? 'register-password-error' : 'register-password-help'}
          error={!!errors.password}
          {...register('password')}
        />
        <FieldError
          id={errors.password ? 'register-password-error' : 'register-password-help'}
          message={errors.password?.message}
          help="8文字以上"
        />
      </div>

      <div className="mt-4 space-y-1.5">
        <PasswordField
          label="パスワード（確認用）"
          autoComplete="new-password"
          aria-describedby={errors.passwordConfirm ? 'register-passwordconfirm-error' : undefined}
          error={!!errors.passwordConfirm}
          {...register('passwordConfirm')}
        />
        <FieldError id="register-passwordconfirm-error" message={errors.passwordConfirm?.message} />
      </div>

      <div className="mt-6">
        <AuthSubmitButton pending={mutation.isPending}>登録して始める</AuthSubmitButton>
      </div>

      <p className="text-muted-foreground mt-6 text-center text-xs leading-relaxed">
        登録すると{' '}
        <Link href="#" className="text-accent hover:underline">
          利用規約
        </Link>{' '}
        と{' '}
        <Link href="#" className="text-accent hover:underline">
          プライバシーポリシー
        </Link>{' '}
        に同意したものとみなされます。
      </p>
    </form>
  )
}
