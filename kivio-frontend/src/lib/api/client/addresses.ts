'use client'
import { apiFetch } from '@/lib/api/client/base'
import type { Address, CreateAddressRequest, UpdateAddressRequest } from '@/types/api'

/** ページングされず素の配列で返る。並び順はサーバーが確定させる。 */
export function listAddresses(): Promise<Address[]> {
  return apiFetch<Address[]>('/users/me/addresses')
}

/** `isDefault: true` を指定すると、既存のデフォルト住所はサーバー側で解除される。 */
export function createAddress(payload: CreateAddressRequest): Promise<Address> {
  return apiFetch<Address>('/users/me/addresses', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** 他人の住所は `ACCESS_DENIED`（403）、存在しない住所は `RESOURCE_NOT_FOUND`（404）。 */
export function updateAddress(id: string, payload: UpdateAddressRequest): Promise<Address> {
  return apiFetch<Address>(`/users/me/addresses/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

/** 住所は物理削除される（`addresses` は `deleted_at` を持たない）。 */
export function deleteAddress(id: string): Promise<void> {
  return apiFetch<void>(`/users/me/addresses/${id}`, { method: 'DELETE' })
}
