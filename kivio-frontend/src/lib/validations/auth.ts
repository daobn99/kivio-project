import { z } from 'zod'

// バリデーション規約・文言の単一の正は docs/design/VALIDATION_RULES.md を参照。
// 制約や文言を変更する場合は同ドキュメント・バックエンドの Bean Validation と合わせて更新すること。

const emailField = z
  .email('メールアドレスの形式が正しくありません')
  .max(255, 'メールアドレスは255文字以内で入力してください')

const passwordField = z
  .string()
  .min(8, 'パスワードは8文字以上で入力してください')
  .max(72, 'パスワードは72文字以内で入力してください')

export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, 'パスワードを入力してください'),
})
export type LoginFormValues = z.infer<typeof loginSchema>

export const requestOtpSchema = z.object({
  email: emailField,
})
export type RequestOtpFormValues = z.infer<typeof requestOtpSchema>

export const verifyOtpSchema = z.object({
  otp: z.string().regex(/^\d{6}$/, '認証コードは6桁の数字で入力してください'),
})
export type VerifyOtpFormValues = z.infer<typeof verifyOtpSchema>

export const completeRegistrationSchema = z
  .object({
    password: passwordField,
    passwordConfirm: z.string(),
    displayName: z
      .string()
      .trim()
      .min(1, '表示名を入力してください')
      .max(100, '表示名は100文字以内で入力してください'),
  })
  .refine((data) => data.password === data.passwordConfirm, {
    message: 'パスワードと確認用パスワードが一致しません',
    path: ['passwordConfirm'],
  })
export type CompleteRegistrationFormValues = z.infer<typeof completeRegistrationSchema>
