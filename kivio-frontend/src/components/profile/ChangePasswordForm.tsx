'use client'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { useChangePasswordMutation } from '@/hooks/mutations/useChangePasswordMutation'
import { changePasswordSchema, type ChangePasswordFormValues } from '@/lib/validations/profile'
import { resolveApiError } from '@/lib/apiErrors'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { FormAlert } from '@/components/form/FormAlert'
import { FieldError } from '@/components/form/FieldError'
import { SaveStatus } from '@/components/form/SaveStatus'
import { PasswordInput } from '@/components/form/PasswordInput'

const PASSWORD_ERRORS: Record<string, string> = {
  PASSWORD_CHANGE_FAILED: '現在のパスワードが正しくありません。',
}

/**
 * 確認用パスワード欄は置かない。表示トグルがあり、失敗しても現在のパスワードを知っている
 * 前提で再試行できるため、入力を 2 度求める価値がない。
 */
export function ChangePasswordForm() {
  const [savedAt, setSavedAt] = useState(0)
  const mutation = useChangePasswordMutation()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '' },
  })

  const onSubmit = (values: ChangePasswordFormValues) => {
    mutation.mutate(values, {
      onSuccess: () => {
        reset({ currentPassword: '', newPassword: '' })
        setSavedAt(Date.now())
      },
    })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
      {mutation.isError && (
        <FormAlert>{resolveApiError(mutation.error, PASSWORD_ERRORS)}</FormAlert>
      )}

      <div className="space-y-2">
        <Label htmlFor="currentPassword">現在のパスワード</Label>
        <PasswordInput
          id="currentPassword"
          autoComplete="current-password"
          aria-invalid={!!errors.currentPassword || undefined}
          aria-describedby="currentPassword-error"
          {...register('currentPassword')}
        />
        <FieldError id="currentPassword-error" message={errors.currentPassword?.message} />
      </div>

      <div className="space-y-2">
        <Label htmlFor="newPassword">新しいパスワード</Label>
        <PasswordInput
          id="newPassword"
          autoComplete="new-password"
          aria-invalid={!!errors.newPassword || undefined}
          aria-describedby="newPassword-error"
          {...register('newPassword')}
        />
        <FieldError
          id="newPassword-error"
          message={errors.newPassword?.message}
          help="8文字以上72文字以内"
        />
      </div>

      <div className="flex flex-col-reverse gap-3 pt-1 sm:flex-row sm:items-center sm:justify-end">
        <SaveStatus signal={savedAt} message="パスワードを変更しました" />
        <Button
          type="submit"
          disabled={!isDirty || mutation.isPending}
          className="bg-accent text-accent-foreground hover:bg-accent/90 h-11 w-full sm:w-auto sm:min-w-40"
        >
          {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
          パスワードを変更
        </Button>
      </div>
    </form>
  )
}
