'use client'
import { useEffect, useRef, useState } from 'react'
import { RegisterEmailStep } from '@/components/auth/RegisterEmailStep'
import { RegisterOtpStep } from '@/components/auth/RegisterOtpStep'
import { RegisterPasswordStep } from '@/components/auth/RegisterPasswordStep'

type Step = 'email' | 'otp' | 'password'

interface FlowState {
  step: Step
  email: string
  registrationToken: string
  /** Step3 等から差し戻された際に Step1 上部へ出す通知 */
  notice?: string
}

/**
 * 会員登録フロー。可視ステッパーを持たない順序つき状態機械。
 * 現在画面・email・registrationToken を内部状態で保持し、子画面（*Step）を切り替える。
 */
export function RegisterFlow() {
  const [state, setState] = useState<FlowState>({ step: 'email', email: '', registrationToken: '' })
  const containerRef = useRef<HTMLDivElement>(null)
  const isInitialMount = useRef(true)

  // 進捗 chrome が無いため、画面切替時に新コンテナへフォーカスを移し（先頭は h1）、
  // 支援技術利用者に「画面が変わった」ことを伝える。初回マウントは除く
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false
      return
    }
    containerRef.current?.focus()
  }, [state.step])

  return (
    <div
      // key を step にして切替ごとに remount → auth-fade を再生
      key={state.step}
      ref={containerRef}
      tabIndex={-1}
      className="rounded-lg outline-none motion-safe:animate-[auth-fade_150ms_ease-out]"
    >
      {state.step === 'email' && (
        <RegisterEmailStep
          defaultEmail={state.email}
          notice={state.notice}
          onSuccess={(email) => setState({ step: 'otp', email, registrationToken: '' })}
        />
      )}

      {state.step === 'otp' && (
        <RegisterOtpStep
          email={state.email}
          onSuccess={(registrationToken) =>
            setState((prev) => ({ ...prev, step: 'password', registrationToken }))
          }
          onChangeEmail={() =>
            setState({ step: 'email', email: state.email, registrationToken: '' })
          }
        />
      )}

      {state.step === 'password' && (
        <RegisterPasswordStep
          registrationToken={state.registrationToken}
          onSessionInvalid={(notice) =>
            setState({ step: 'email', email: state.email, registrationToken: '', notice })
          }
        />
      )}
    </div>
  )
}
