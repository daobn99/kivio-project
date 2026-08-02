import type { Metadata } from 'next'
import { AddressList } from '@/components/address/AddressList'

export const metadata: Metadata = {
  title: '配送先住所 | Kivio',
}

export default function ProfileAddressesPage() {
  return <AddressList />
}
