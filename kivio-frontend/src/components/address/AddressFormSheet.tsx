'use client'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { useUpsertAddressMutation } from '@/hooks/mutations/useUpsertAddressMutation'
import { addressSchema, type AddressFormValues } from '@/lib/validations/address'
import { PREFECTURES } from '@/lib/constants/prefectures'
import { apiErrorCode, resolveApiError } from '@/lib/apiErrors'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import type { Address, UpdateAddressRequest } from '@/types/api'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { FormAlert } from '@/components/form/FormAlert'
import { FieldError } from '@/components/form/FieldError'

const ADDRESS_ERRORS: Record<string, string> = {
  RESOURCE_NOT_FOUND: 'この住所は既に削除されています。',
  ACCESS_DENIED: 'この住所を操作する権限がありません。',
}

const EMPTY_VALUES: AddressFormValues = {
  recipientName: '',
  postalCode: '',
  prefecture: '',
  city: '',
  addressLine: '',
  phoneNumber: '',
  isDefault: false,
}

interface AddressFormSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 編集対象。未指定なら新規追加モード */
  address?: Address
  /** 404 / 403 のように一覧の再取得が必要な失敗のあとに呼ばれる */
  onStaleError?: () => void
}

/** 追加と編集はフィールド構成が同じなので 1 つのコンポーネントで兼ねる。 */
export function AddressFormSheet({
  open,
  onOpenChange,
  address,
  onStaleError,
}: AddressFormSheetProps) {
  // data-[side=…] は sm: 修飾子より詳細度が高く CSS では上書きできないため、
  // 画面幅による出し分けは prop 自体を切り替えて行う
  const isDesktop = useMediaQuery('(min-width: 640px)')
  const isEdit = !!address
  // 既にデフォルトの住所は、自分自身のデフォルト解除に相当する API 手段がないため変更させない
  const defaultLocked = isEdit && address.isDefault

  const mutation = useUpsertAddressMutation(address?.id)

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors, dirtyFields },
  } = useForm<AddressFormValues>({
    resolver: zodResolver(addressSchema),
    defaultValues: EMPTY_VALUES,
  })

  const { reset: resetMutation } = mutation

  // 開くたびに対象の値で初期化する（前回の入力・エラーを持ち越さない）
  useEffect(() => {
    if (!open) return
    resetMutation()
    reset(
      address
        ? {
            recipientName: address.recipientName,
            postalCode: address.postalCode,
            prefecture: address.prefecture,
            city: address.city,
            addressLine: address.addressLine,
            phoneNumber: address.phoneNumber,
            isDefault: address.isDefault,
          }
        : EMPTY_VALUES,
    )
  }, [open, address, reset, resetMutation])

  const onSubmit = (values: AddressFormValues) => {
    let payload: AddressFormValues | UpdateAddressRequest = values
    if (isEdit) {
      // 部分更新: 変更されたフィールドのみ送る
      const patch: UpdateAddressRequest = {}
      for (const key of Object.keys(dirtyFields) as (keyof AddressFormValues)[]) {
        Object.assign(patch, { [key]: values[key] })
      }
      payload = patch
    }

    mutation.mutate(payload, {
      onSuccess: () => onOpenChange(false),
      onError: (error) => {
        // 一覧と実データがずれている（他タブで削除された等）なら開いたままにせず閉じる
        const code = apiErrorCode(error)
        if (code === 'RESOURCE_NOT_FOUND' || code === 'ACCESS_DENIED') onStaleError?.()
      },
    })
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side={isDesktop ? 'right' : 'bottom'}
        className="max-h-[85dvh] gap-0 overflow-y-auto sm:max-h-none sm:max-w-md"
      >
        <SheetHeader>
          <SheetTitle className="font-serif text-lg font-bold">
            {isEdit ? '配送先を編集' : '配送先を追加'}
          </SheetTitle>
          <SheetDescription>登録した住所は購入時の配送先として選べます。</SheetDescription>
        </SheetHeader>

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex min-h-0 flex-1 flex-col">
          <div className="space-y-4 px-4 pb-4">
            {mutation.isError && (
              <FormAlert>{resolveApiError(mutation.error, ADDRESS_ERRORS)}</FormAlert>
            )}

            <div className="space-y-2">
              <Label htmlFor="recipientName">宛名</Label>
              <Input
                id="recipientName"
                className="h-11"
                autoComplete="name"
                aria-invalid={!!errors.recipientName || undefined}
                aria-describedby="recipientName-error"
                {...register('recipientName')}
              />
              <FieldError id="recipientName-error" message={errors.recipientName?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="postalCode">郵便番号</Label>
              <Input
                id="postalCode"
                inputMode="numeric"
                autoComplete="postal-code"
                placeholder="150-0002"
                className="h-11 w-40"
                aria-invalid={!!errors.postalCode || undefined}
                aria-describedby="postalCode-error"
                {...register('postalCode')}
              />
              <FieldError id="postalCode-error" message={errors.postalCode?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="prefecture">都道府県</Label>
              <Controller
                control={control}
                name="prefecture"
                render={({ field }) => (
                  <Select
                    value={field.value || null}
                    onValueChange={(value) => field.onChange(value ?? '')}
                  >
                    <SelectTrigger
                      id="prefecture"
                      className="h-11 w-full"
                      aria-invalid={!!errors.prefecture || undefined}
                      aria-describedby="prefecture-error"
                    >
                      <SelectValue placeholder="選択してください" />
                    </SelectTrigger>
                    <SelectContent>
                      {PREFECTURES.map((name) => (
                        <SelectItem key={name} value={name}>
                          {name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              <FieldError id="prefecture-error" message={errors.prefecture?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="city">市区町村</Label>
              <Input
                id="city"
                className="h-11"
                autoComplete="address-level2"
                aria-invalid={!!errors.city || undefined}
                aria-describedby="city-error"
                {...register('city')}
              />
              <FieldError id="city-error" message={errors.city?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="addressLine">番地・建物名</Label>
              <Input
                id="addressLine"
                className="h-11"
                autoComplete="address-line1"
                aria-invalid={!!errors.addressLine || undefined}
                aria-describedby="addressLine-error"
                {...register('addressLine')}
              />
              <FieldError id="addressLine-error" message={errors.addressLine?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="phoneNumber">電話番号</Label>
              <Input
                id="phoneNumber"
                inputMode="tel"
                autoComplete="tel"
                placeholder="090-1234-5678"
                className="h-11"
                aria-invalid={!!errors.phoneNumber || undefined}
                aria-describedby="phoneNumber-error"
                {...register('phoneNumber')}
              />
              <FieldError id="phoneNumber-error" message={errors.phoneNumber?.message} />
            </div>

            <div className="border-border space-y-1.5 border-t pt-4">
              <Controller
                control={control}
                name="isDefault"
                render={({ field }) => (
                  <label className="text-foreground flex items-start gap-2.5 text-sm">
                    <Checkbox
                      checked={field.value}
                      onCheckedChange={(checked) => field.onChange(checked === true)}
                      disabled={defaultLocked}
                      className="mt-0.5"
                    />
                    <span>この住所をデフォルトに設定する</span>
                  </label>
                )}
              />
              <p className="text-muted-foreground pl-7 text-sm">
                {defaultLocked
                  ? 'デフォルト住所です。他の住所をデフォルトに設定すると解除されます。'
                  : 'チェックすると、現在のデフォルト住所は解除されます。'}
              </p>
            </div>
          </div>

          <SheetFooter className="bg-popover border-border sticky bottom-0 flex-row justify-end gap-2 border-t">
            <Button
              type="button"
              variant="outline"
              className="h-11"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              キャンセル
            </Button>
            <Button
              type="submit"
              disabled={mutation.isPending}
              className="bg-accent text-accent-foreground hover:bg-accent/90 h-11 min-w-28"
            >
              {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
              保存する
            </Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
