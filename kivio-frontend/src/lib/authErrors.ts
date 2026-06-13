import { ApiError } from '@/lib/api/ApiError'

/** 想定外のエラー時に表示する汎用文言 */
export const DEFAULT_AUTH_ERROR = 'エラーが発生しました。しばらくしてからお試しください。'

/**
 * ApiError のエラーコードを画面別の日本語文言へ変換する。
 *
 * 同じコードでも画面によって文言が異なる（例: `RATE_LIMIT_EXCEEDED` はログインと OTP 送信で
 * 表現を変える）ため、各フォームが `overrides` でコード→文言の対応表を渡す。
 * 文言の正は docs/design/VALIDATION_RULES.md / design-system/pages/auth.md の各エラー表。
 *
 * @returns マッチする文言。ApiError 以外・未知コードは `DEFAULT_AUTH_ERROR`
 */
export function resolveAuthError(error: unknown, overrides: Record<string, string>): string {
  if (error instanceof ApiError && error.code in overrides) {
    return overrides[error.code]
  }
  return DEFAULT_AUTH_ERROR
}

/** ApiError のコードを取り出す（分岐用）。ApiError 以外は null */
export function authErrorCode(error: unknown): string | null {
  return error instanceof ApiError ? error.code : null
}
