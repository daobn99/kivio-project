import { z } from 'zod'

// 制約と文言はバックエンドの Bean Validation と一致させること。

/** `avatarUrl` の空文字はクリア要求。バックエンドがこれを受けて null 化する。 */
export const updateProfileSchema = z.object({
  displayName: z
    .string()
    .trim()
    .min(1, '表示名を入力してください')
    .max(100, '表示名は100文字以内で入力してください'),
  avatarUrl: z.union([z.literal(''), z.url('URLの形式が正しくありません')]),
})
export type UpdateProfileFormValues = z.infer<typeof updateProfileSchema>

/**
 * `currentPassword` に強度ルールは課さない。既存値との照合でしかなく、
 * 不一致はサーバーが `PASSWORD_CHANGE_FAILED` で返すため。
 */
export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, '現在のパスワードを入力してください'),
  newPassword: z
    .string()
    .min(8, 'パスワードは8文字以上で入力してください')
    .max(72, 'パスワードは72文字以内で入力してください'),
})
export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>
