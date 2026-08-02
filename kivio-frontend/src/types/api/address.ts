/** 配送先住所。件数が少ないため `PageResponse` ではなく素の配列で返る。 */
export interface Address {
  id: string
  /** 宛名 */
  recipientName: string
  /** 郵便番号（ハイフン有無いずれもあり得る） */
  postalCode: string
  /** 都道府県 */
  prefecture: string
  /** 市区町村 */
  city: string
  /** 番地・建物名 */
  addressLine: string
  /** 電話番号（ハイフン有無いずれもあり得る） */
  phoneNumber: string
  /** デフォルト配送先かどうか。ユーザーにつき 1 件のみ true */
  isDefault: boolean
  /** ISO 8601 UTC の作成日時 */
  createdAt: string
}

/** `POST /users/me/addresses` のリクエストボディ */
export type CreateAddressRequest = Omit<Address, 'id' | 'createdAt'>

/**
 * `PATCH /users/me/addresses/{id}` のリクエストボディ。
 * 部分更新のため送信したフィールドのみ更新される（未送信は不変）。
 */
export type UpdateAddressRequest = Partial<CreateAddressRequest>
