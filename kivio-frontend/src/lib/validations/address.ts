import { z } from 'zod'

// 正規表現・文字数はバックエンドの Bean Validation と一致させること。

/** 追加・編集の両方で使う。編集時も全体を検証し、送信時にダーティなフィールドだけ抽出する。 */
export const addressSchema = z.object({
  recipientName: z
    .string()
    .trim()
    .min(1, '宛名を入力してください')
    .max(100, '宛名は100文字以内で入力してください'),
  postalCode: z
    .string()
    .trim()
    .min(1, '郵便番号を入力してください')
    .regex(/^\d{3}-?\d{4}$/, '郵便番号は7桁の数字で入力してください'),
  prefecture: z
    .string()
    .trim()
    .min(1, '都道府県を選択してください')
    .max(20, '都道府県は20文字以内で入力してください'),
  city: z
    .string()
    .trim()
    .min(1, '市区町村を入力してください')
    .max(100, '市区町村は100文字以内で入力してください'),
  addressLine: z
    .string()
    .trim()
    .min(1, '番地・建物名を入力してください')
    .max(255, '番地・建物名は255文字以内で入力してください'),
  phoneNumber: z
    .string()
    .trim()
    .min(1, '電話番号を入力してください')
    .regex(/^0\d{1,4}-?\d{1,4}-?\d{4}$/, '電話番号の形式が正しくありません'),
  isDefault: z.boolean(),
})
export type AddressFormValues = z.infer<typeof addressSchema>
