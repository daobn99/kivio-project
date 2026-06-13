'use client'
import { useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { requestOtp, verifyOtp } from '@/lib/api/client/auth'
import { verifyOtpSchema } from '@/lib/validations/auth'
import { authErrorCode, DEFAULT_AUTH_ERROR } from '@/lib/authErrors'
import { OtpInput } from '@/components/auth/OtpInput'
import { AuthSubmitButton } from '@/components/auth/AuthSubmitButton'

const RESEND_COOLDOWN_SECONDS = 60
const MAX_ATTEMPTS = 5
/** OTP の有効期限（EMAIL_DESIGN AUTH-01 / Redis TTL と一致させる） */
const OTP_EXPIRY_LABEL = '10分'

interface RegisterOtpStepProps {
  email: string
  /** OTP 検証成功（200）。registrationToken を持って Step3 へ */
  onSuccess: (registrationToken: string) => void
  /** 「メールアドレスを変更」で Step1 へ戻る（OTP 状態は破棄） */
  onChangeEmail: () => void
}

export function RegisterOtpStep({ email, onSuccess, onChangeEmail }: RegisterOtpStepProps) {
  const [otp, setOtp] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [attemptsLeft, setAttemptsLeft] = useState(MAX_ATTEMPTS)
  // OTP 失効（期限切れ / 試行上限）。入力を無効化し再送信を促す
  const [locked, setLocked] = useState(false)
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN_SECONDS)

  // 直前の Step1（または再送信）で OTP を送信済みのため、到着時からクールダウンを開始する
  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setInterval(() => setCooldown((c) => (c > 0 ? c - 1 : 0)), 1000)
    return () => clearInterval(timer)
  }, [cooldown])

  const verifyMutation = useMutation({
    mutationFn: (code: string) => verifyOtp(email, code),
    onSuccess: (res) => onSuccess(res.registrationToken),
    onError: (err) => {
      const code = authErrorCode(err)
      if (code === 'OTP_INVALID') {
        const left = Math.max(0, attemptsLeft - 1)
        setAttemptsLeft(left)
        setError(`認証コードが正しくありません（残り${left}回）`)
      } else if (code === 'OTP_EXPIRED') {
        setLocked(true)
        setError('認証コードの有効期限が切れました。コードを再送信してください。')
      } else if (code === 'OTP_MAX_ATTEMPTS_EXCEEDED') {
        setLocked(true)
        setAttemptsLeft(0)
        setError('試行回数の上限に達しました。コードを再送信してください。')
      } else {
        setError(DEFAULT_AUTH_ERROR)
      }
    },
  })

  const resendMutation = useMutation({
    mutationFn: () => requestOtp(email),
    onSuccess: () => {
      setOtp('')
      setError(null)
      setAttemptsLeft(MAX_ATTEMPTS)
      setLocked(false)
      setCooldown(RESEND_COOLDOWN_SECONDS)
    },
    onError: () => setError('コードの再送信に失敗しました。しばらくしてからお試しください。'),
  })

  const handleVerify = (code: string) => {
    if (locked || verifyMutation.isPending) return
    const parsed = verifyOtpSchema.safeParse({ otp: code })
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? '認証コードを入力してください')
      return
    }
    setError(null)
    verifyMutation.mutate(code)
  }

  const canResend = cooldown <= 0 && !resendMutation.isPending

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        handleVerify(otp)
      }}
      noValidate
      className="motion-safe:*:animate-[auth-rise_200ms_ease-out_both] motion-safe:[&>*:nth-child(2)]:[animation-delay:60ms] motion-safe:[&>*:nth-child(3)]:[animation-delay:120ms] motion-safe:[&>*:nth-child(4)]:[animation-delay:180ms]"
    >
      <header className="mb-8">
        <h1 className="text-foreground font-serif text-2xl font-bold">認証コードを入力</h1>
        <p className="text-muted-foreground mt-2 text-sm">
          <span className="text-foreground font-medium break-all">{email}</span>{' '}
          に送信した6桁のコードを入力してください
        </p>
        <p className="text-muted-foreground mt-1 text-sm">
          コードの有効期限は{OTP_EXPIRY_LABEL}です。
        </p>
      </header>

      <div className="space-y-3">
        <OtpInput
          value={otp}
          onChange={setOtp}
          onComplete={handleVerify}
          error={!!error}
          disabled={locked}
        />
        {error && (
          <p role="alert" aria-live="polite" className="text-destructive text-center text-sm">
            {error}
          </p>
        )}
      </div>

      <div className="mt-6">
        <AuthSubmitButton pending={verifyMutation.isPending} disabled={locked}>
          確認
        </AuthSubmitButton>
      </div>

      <div className="text-muted-foreground mt-6 space-y-3 text-center text-sm">
        <p>
          コードが届きませんか？{' '}
          <button
            type="button"
            onClick={() => resendMutation.mutate()}
            disabled={!canResend}
            className="text-accent disabled:text-muted-foreground font-medium hover:underline disabled:no-underline"
          >
            再送信
          </button>
        </p>
        {cooldown > 0 && <p>あと{cooldown}秒で再送信できます</p>}
        <button
          type="button"
          onClick={onChangeEmail}
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          メールアドレスを変更
        </button>
      </div>
    </form>
  )
}
