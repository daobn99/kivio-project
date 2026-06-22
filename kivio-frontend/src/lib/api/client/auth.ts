'use client'
import { ApiError } from '@/lib/api/ApiError'
import type {
  AuthTokens,
  CheckEmailResponse,
  ProblemDetail,
  RequestOtpResponse,
  VerifyOtpResponse,
} from '@/types/api'

/**
 * 認証エンドポイント専用の POST フェッチ。
 *
 * 共通の `apiFetch` を使わないのは、認証 API の 401 が「セッション切れ」ではなく
 * `INVALID_CREDENTIALS` 等の業務エラーであり、ログイン画面へのリダイレクトではなく
 * フォーム上でのエラー表示が必要なため。Refresh Token は HTTP-only Cookie 経由で
 * 送られるため `credentials: 'include'` を指定する。
 */
async function authFetch<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`/api/v1/auth${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })

  if (!res.ok) {
    const problem: ProblemDetail = await res.json()
    throw new ApiError(problem.title, problem.status, problem.detail, problem.code)
  }

  // 204 No Content（logout）は res.json() を呼ぶと SyntaxError になるため早期リターン
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export function checkEmail(email: string): Promise<CheckEmailResponse> {
  return authFetch('/check-email', { email })
}

export function requestOtp(email: string): Promise<RequestOtpResponse> {
  return authFetch('/register/request-otp', { email })
}

export function verifyOtp(email: string, otp: string): Promise<VerifyOtpResponse> {
  return authFetch('/register/verify-otp', { email, otp })
}

export interface CompleteRegistrationInput {
  registrationToken: string
  password: string
  passwordConfirm: string
  displayName?: string
}

export function completeRegistration(input: CompleteRegistrationInput): Promise<AuthTokens> {
  return authFetch('/register/complete', input)
}

export function login(email: string, password: string): Promise<AuthTokens> {
  return authFetch('/login', { email, password })
}

export function googleLogin(idToken: string): Promise<AuthTokens> {
  return authFetch('/google', { idToken })
}

// Refresh Token / Refresh の対象トークンは HTTP-only Cookie に保持されるため、
// ボディは BFF（Next Route Handler）側で Cookie から注入する。
export function refresh(): Promise<AuthTokens> {
  return authFetch('/refresh')
}

export function logout(): Promise<void> {
  return authFetch('/logout')
}
