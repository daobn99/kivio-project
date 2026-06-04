import type { ApiErrorCode } from '@/types/api'

/** RFC 9457 ProblemDetail をクライアント例外として扱うクラス */
export class ApiError extends Error {
  constructor(
    message: string,
    /** HTTP ステータスコード */
    public readonly status: number,
    /** エラーの詳細メッセージ */
    public readonly detail: string,
    /** UPPER_SNAKE_CASE エラーコード。未知コードは string にフォールバック */
    public readonly code: ApiErrorCode | string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}
