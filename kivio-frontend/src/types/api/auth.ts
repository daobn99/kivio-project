import type { UserRole, UserStatus } from '@/types/enums'

/**
 * 認証トークンレスポンス。
 * `POST /auth/login` / `POST /auth/google` / `POST /auth/register/complete` の成功レスポンス。
 */
export interface AuthTokens {
  /** JWT Access Token（有効期限15分） */
  accessToken: string
  /** Refresh Token。Token Rotation で再発行された場合のみ含まれる */
  refreshToken?: string
  /** トークン種別（常に `Bearer`） */
  tokenType: string
  /** Access Token 有効期限（秒） */
  expiresIn: number
}

/** `POST /auth/register/request-otp` の成功レスポンス（202 Accepted） */
export interface RequestOtpResponse {
  message: string
  /** OTP の有効期限（秒） */
  expiresInSeconds: number
}

/** `POST /auth/register/verify-otp` の成功レスポンス（200 OK） */
export interface VerifyOtpResponse {
  /** 登録完了 API に渡す不透明な登録セッショントークン（UUID v4） */
  registrationToken: string
  /** 登録セッションの有効期限（秒） */
  expiresInSeconds: number
}

/** `POST /auth/check-email` の成功レスポンス（200 OK） */
export interface CheckEmailResponse {
  available: boolean
}

/**
 * ログイン中ユーザーのプロフィール。
 * `GET /users/me` のレスポンスに対応する。
 */
export interface AuthUser {
  id: string
  email: string
  displayName: string
  /** アバター画像 URL。未設定の場合は null */
  avatarUrl: string | null
  role: UserRole
  status: UserStatus
  /** ISO 8601 UTC のアカウント作成日時 */
  createdAt: string
}
