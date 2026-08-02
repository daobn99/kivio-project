/**
 * バックエンド Enum に対応する const + type 定義。
 * 値はバックエンドの Enum 名と完全一致させる。
 */

export const UserRole = {
  /** バイヤー */
  ROLE_BUYER: 'ROLE_BUYER',
  /** セラー */
  ROLE_SELLER: 'ROLE_SELLER',
  /** 管理者 */
  ROLE_ADMIN: 'ROLE_ADMIN',
} as const
export type UserRole = (typeof UserRole)[keyof typeof UserRole]

export const UserStatus = {
  /** 有効 */
  ACTIVE: 'ACTIVE',
  /** 無効・停止中 */
  INACTIVE: 'INACTIVE',
} as const
export type UserStatus = (typeof UserStatus)[keyof typeof UserStatus]
