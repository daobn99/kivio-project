import type { SellerApplicationStatus } from '@/types/enums'

/**
 * セラー申請。`GET /seller-applications/me` は常に最新 1 件を返す（履歴の一覧 API は無い）。
 * 却下後の再申請は新しいレコードになるため、返るのは常に「今の状態」である。
 */
export interface SellerApplication {
  id: string
  applicantId: string
  reason: string
  status: SellerApplicationStatus
  /** 審査コメント。未審査・コメント未記入ならレスポンスから省略される（`non_null` 設定） */
  reviewComment?: string | null
  /** ISO 8601 UTC の審査日時。未審査ならレスポンスから省略される */
  reviewedAt?: string | null
  /** ISO 8601 UTC の申請日時 */
  createdAt: string
}

/**
 * `POST /seller-applications` のリクエストボディ。
 * `status` / `reviewerId` 等のサーバー決定フィールドは受け付けられない。
 */
export interface CreateSellerApplicationRequest {
  reason: string
}
