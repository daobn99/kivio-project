'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { useWithdrawAccountMutation } from '@/hooks/mutations/useWithdrawAccountMutation'
import { useAuthStore } from '@/stores/useAuthStore'
import { resolveApiError } from '@/lib/apiErrors'
import { ROUTES } from '@/lib/constants'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { FormAlert } from '@/components/form/FormAlert'

const WITHDRAW_ERRORS: Record<string, string> = {
  RESOURCE_NOT_FOUND: 'このアカウントは既に退会済みです。',
}

/**
 * 二段階（セクションのボタン → ダイアログ内の同意チェック）で不可逆操作を守る。
 * タイプ確認（「退会」と入力させる）は消費者アカウントには過剰なので採らない。
 */
export function WithdrawDialog() {
  const router = useRouter()
  const user = useAuthStore((state) => state.user)
  const mutation = useWithdrawAccountMutation()

  const [open, setOpen] = useState(false)
  const [agreed, setAgreed] = useState(false)

  const handleOpenChange = (next: boolean) => {
    setOpen(next)
    if (!next) {
      setAgreed(false)
      mutation.reset()
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger
        render={
          <Button
            variant="outline"
            className="border-destructive/50 text-destructive hover:bg-destructive/10 hover:text-destructive h-11 w-full sm:w-auto"
          />
        }
      >
        退会する
      </AlertDialogTrigger>

      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="font-serif">本当に退会しますか？</AlertDialogTitle>
          <AlertDialogDescription>
            {user ? `${user.displayName}（${user.email}）の` : 'この'}
            アカウントを閉じます。この操作は取り消せません。
          </AlertDialogDescription>
        </AlertDialogHeader>

        <label className="text-foreground flex items-start gap-2.5 text-sm">
          <Checkbox
            checked={agreed}
            onCheckedChange={(checked) => setAgreed(checked === true)}
            className="mt-0.5"
          />
          <span>上記の内容を理解し、退会します</span>
        </label>

        {mutation.isError && (
          <FormAlert>{resolveApiError(mutation.error, WITHDRAW_ERRORS)}</FormAlert>
        )}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={mutation.isPending}>キャンセル</AlertDialogCancel>
          <AlertDialogAction
            disabled={!agreed || mutation.isPending}
            // このボタンは押しても自動では閉じない。失敗時はダイアログを開いたままエラーを見せ、
            // 成功時は router.replace でページごと離脱する
            onClick={() =>
              mutation.mutate(undefined, { onSuccess: () => router.replace(ROUTES.home) })
            }
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90 h-11"
          >
            {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            退会する
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
