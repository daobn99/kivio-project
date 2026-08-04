'use client'
import { useState } from 'react'
import { Plus, RotateCcw } from 'lucide-react'
import { useAddressesQuery } from '@/hooks/queries/useAddressesQuery'
import { useSetDefaultAddressMutation } from '@/hooks/mutations/useSetDefaultAddressMutation'
import { resolveApiError } from '@/lib/apiErrors'
import type { Address } from '@/types/api'
import { Button } from '@/components/ui/button'
import { FormAlert } from '@/components/form/FormAlert'
import { AddressCard } from '@/components/address/AddressCard'
import { AddressEmptyState } from '@/components/address/AddressEmptyState'
import { AddressFormSheet } from '@/components/address/AddressFormSheet'
import { AddressListSkeleton } from '@/components/address/AddressListSkeleton'
import { DeleteAddressDialog } from '@/components/address/DeleteAddressDialog'

export function AddressList() {
  const [sheetOpen, setSheetOpen] = useState(false)
  const [editing, setEditing] = useState<Address | undefined>(undefined)
  const [deleting, setDeleting] = useState<Address | null>(null)

  const { data: addresses, isPending, isError, error, refetch } = useAddressesQuery()
  const setDefault = useSetDefaultAddressMutation()

  const openCreate = () => {
    setEditing(undefined)
    setSheetOpen(true)
  }

  const openEdit = (address: Address) => {
    setEditing(address)
    setSheetOpen(true)
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-1">
          <h2 className="text-foreground font-serif text-lg font-bold">配送先住所</h2>
          <p className="text-muted-foreground text-sm">
            購入時に選べる配送先です。デフォルトの住所は 1 件だけ設定できます。
          </p>
        </div>
        <Button
          onClick={openCreate}
          className="bg-accent text-accent-foreground hover:bg-accent/90 h-11 w-full shrink-0 sm:w-auto"
        >
          <Plus className="h-4 w-4" aria-hidden />
          住所を追加
        </Button>
      </div>

      {setDefault.isError && <FormAlert>{resolveApiError(setDefault.error)}</FormAlert>}

      {isPending && <AddressListSkeleton />}

      {isError && (
        <div className="space-y-3">
          <FormAlert>{resolveApiError(error)}</FormAlert>
          <Button variant="outline" className="h-11" onClick={() => refetch()}>
            <RotateCcw className="h-4 w-4" aria-hidden />
            再読み込み
          </Button>
        </div>
      )}

      {addresses && addresses.length === 0 && <AddressEmptyState onAdd={openCreate} />}

      {addresses && addresses.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2">
          {addresses.map((address) => (
            <AddressCard
              key={address.id}
              address={address}
              onEdit={() => openEdit(address)}
              onDelete={() => setDeleting(address)}
              onMakeDefault={() => setDefault.mutate(address.id)}
              pending={setDefault.isPending}
            />
          ))}
        </div>
      )}

      <AddressFormSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        address={editing}
        onStaleError={() => setSheetOpen(false)}
      />
      <DeleteAddressDialog address={deleting} onOpenChange={(open) => !open && setDeleting(null)} />
    </div>
  )
}
