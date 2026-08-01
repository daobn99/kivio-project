import { ApiError } from '@/lib/api/ApiError'

/** 未知のエラーコードに対する汎用文言 */
export const DEFAULT_API_ERROR = 'エラーが発生しました。しばらくしてからお試しください。'

/** レスポンス自体を受け取れなかった（通信断・CORS 等）場合の文言 */
export const NETWORK_ERROR = '通信に失敗しました。接続を確認して再度お試しください。'

/**
 * エラーを画面に表示する日本語文言へ変換する。
 *
 * 同じコードでも画面によって適切な文言が異なる（`RATE_LIMIT_EXCEEDED` はログインと OTP 送信で
 * 表現を変える）ため、コード→文言の対応表は呼び出し側が `overrides` で渡す。
 *
 * @returns 既知コードは `overrides` の文言 / 未知コードは `DEFAULT_API_ERROR` / 通信断は `NETWORK_ERROR`
 */
export function resolveApiError(error: unknown, overrides: Record<string, string> = {}): string {
  if (!(error instanceof ApiError)) return NETWORK_ERROR
  return overrides[error.code] ?? DEFAULT_API_ERROR
}

/** エラーコードを取り出す（文言ではなく分岐に使う用）。ApiError 以外は null */
export function apiErrorCode(error: unknown): string | null {
  return error instanceof ApiError ? error.code : null
}
