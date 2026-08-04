import { z } from 'zod'

// 制約と文言は `VALIDATION_RULES.md §6` およびバックエンドの Bean Validation
// （`CreateSellerApplicationRequest`）と一字一句そろえること。

/** 申請理由の上限。文字数カウンターの表示にも使う */
export const MAX_REASON_LENGTH = 1000

/** 新規申請・再申請で共用する（送信されるのは同じ `POST /seller-applications`）。 */
export const sellerApplicationSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, '申請理由を入力してください')
    .max(MAX_REASON_LENGTH, '申請理由は1000文字以内で入力してください'),
})
export type SellerApplicationFormValues = z.infer<typeof sellerApplicationSchema>
