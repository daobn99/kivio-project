export interface ValidationError {
  field: string
  message: string
  rejectedValue?: unknown
}

/** RFC 9457 ProblemDetail — バックエンドのエラーレスポンス形式 */
export interface ProblemDetail {
  type: string
  title: string
  status: number
  /** UPPER_SNAKE_CASE エラーコード（フロントエンドでの分岐に使用） */
  code: string
  detail: string
  instance: string
  /** バリデーションエラー時のフィールド別詳細 */
  errors?: ValidationError[]
}
