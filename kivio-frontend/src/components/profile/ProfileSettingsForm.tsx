'use client'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import type { UpdateProfilePayload } from '@/lib/api/client/users'
import { useUpdateProfileMutation } from '@/hooks/mutations/useUpdateProfileMutation'
import { updateProfileSchema, type UpdateProfileFormValues } from '@/lib/validations/profile'
import { useAuthStore } from '@/stores/useAuthStore'
import { resolveApiError } from '@/lib/apiErrors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { FormAlert } from '@/components/form/FormAlert'
import { FieldError } from '@/components/form/FieldError'
import { SaveStatus } from '@/components/form/SaveStatus'
import { AvatarUrlField } from '@/components/profile/AvatarUrlField'

export function ProfileSettingsForm() {
  const user = useAuthStore((state) => state.user)
  const [savedAt, setSavedAt] = useState(0)
  const mutation = useUpdateProfileMutation()

  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors, isDirty, dirtyFields },
  } = useForm<UpdateProfileFormValues>({
    resolver: zodResolver(updateProfileSchema),
    // ハイドレート後に user が入るため defaultValues ではなく values で追従させる
    // （保存成功時のストア更新でもフォームがクリーン状態に戻る）
    values: {
      displayName: user?.displayName ?? '',
      avatarUrl: user?.avatarUrl ?? '',
    },
  })

  // watch() を使うと React Compiler がこのコンポーネントのメモ化を諦める
  const avatarUrl = useWatch({ control, name: 'avatarUrl' })
  const displayName = useWatch({ control, name: 'displayName' })

  if (!user) return <ProfileSettingsFormSkeleton />

  const onSubmit = (formValues: UpdateProfileFormValues) => {
    const payload: UpdateProfilePayload = {}
    if (dirtyFields.displayName) payload.displayName = formValues.displayName
    // 空文字はクリア要求としてそのまま送る
    if (dirtyFields.avatarUrl) payload.avatarUrl = formValues.avatarUrl
    mutation.mutate(payload, { onSuccess: () => setSavedAt(Date.now()) })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
      {mutation.isError && <FormAlert>{resolveApiError(mutation.error)}</FormAlert>}

      <AvatarUrlField
        value={avatarUrl}
        displayName={displayName || user.displayName}
        registration={register('avatarUrl')}
        error={errors.avatarUrl?.message}
        onClear={() => setValue('avatarUrl', '', { shouldDirty: true, shouldValidate: true })}
      />

      <div className="space-y-2">
        <Label htmlFor="displayName">表示名</Label>
        <Input
          id="displayName"
          className="h-11"
          autoComplete="nickname"
          aria-invalid={!!errors.displayName || undefined}
          aria-describedby="displayName-error"
          {...register('displayName')}
        />
        <FieldError
          id="displayName-error"
          message={errors.displayName?.message}
          help="100文字以内。商品ページやレビューに表示されます。"
        />
      </div>

      <div className="flex flex-col-reverse gap-3 pt-1 sm:flex-row sm:items-center sm:justify-end">
        <SaveStatus signal={savedAt} />
        <Button
          type="submit"
          disabled={!isDirty || mutation.isPending}
          className="bg-accent text-accent-foreground hover:bg-accent/90 h-11 w-full sm:w-auto sm:min-w-32"
        >
          {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
          保存する
        </Button>
      </div>
    </form>
  )
}

function ProfileSettingsFormSkeleton() {
  return (
    <div className="space-y-5">
      <div className="flex items-start gap-4">
        <Skeleton className="mt-6 size-16 shrink-0 rounded-full" />
        <div className="flex-1 space-y-2">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-11 w-full" />
        </div>
      </div>
      <div className="space-y-2">
        <Skeleton className="h-4 w-20" />
        <Skeleton className="h-11 w-full" />
      </div>
    </div>
  )
}
