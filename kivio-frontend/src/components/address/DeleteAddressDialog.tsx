'use client'
import { Loader2 } from 'lucide-react'
import { useDeleteAddressMutation } from '@/hooks/mutations/useDeleteAddressMutation'
import { resolveApiError } from '@/lib/apiErrors'
import { formatPostalCode } from '@/lib/format'
import type { Address } from '@/types/api'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { FormAlert } from '@/components/form/FormAlert'

const DELETE_ERRORS: Record<string, string> = {
  RESOURCE_NOT_FOUND: 'この住所は既に削除されています。',
  ACCESS_DENIED: 'この住所を操作する権限がありません。',
}

interface DeleteAddressDialogProps {
  /** 削除対象。null ならダイアログを閉じた状態 */
  address: Address | null
  onOpenChange: (open: boolean) => void
}

/** 退会と違い同意チェックは付けない。住所は再登録できるため、摩擦は影響度に比例させる。 */
export function DeleteAddressDialog({ address, onOpenChange }: DeleteAddressDialogProps) {
  const mutation = useDeleteAddressMutation()

  return (
    <AlertDialog
      open={!!address}
      onOpenChange={(open) => {
        if (!open) mutation.reset()
        onOpenChange(open)
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="font-serif">この配送先を削除しますか？</AlertDialogTitle>
          <AlertDialogDescription>
            {address && (
              <>
                {address.recipientName} / {formatPostalCode(address.postalCode)}{' '}
                {address.prefecture}
                {address.city}
                {address.addressLine}
                {address.isDefault &&
                  '（デフォルトに設定されている住所です。削除後、デフォルトの配送先はなくなります）'}
              </>
            )}
          </AlertDialogDescription>
        </AlertDialogHeader>

        {mutation.isError && (
          <FormAlert>{resolveApiError(mutation.error, DELETE_ERRORS)}</FormAlert>
        )}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={mutation.isPending}>キャンセル</AlertDialogCancel>
          <AlertDialogAction
            disabled={mutation.isPending}
            onClick={() =>
              address && mutation.mutate(address.id, { onSuccess: () => onOpenChange(false) })
            }
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90 h-11"
          >
            {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            削除する
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
