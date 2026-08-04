'use client'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { useCreateSellerApplicationMutation } from '@/hooks/mutations/useCreateSellerApplicationMutation'
import {
  MAX_REASON_LENGTH,
  sellerApplicationSchema,
  type SellerApplicationFormValues,
} from '@/lib/validations/sellerApplication'
import { apiErrorCode, resolveApiError } from '@/lib/apiErrors'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { FieldError } from '@/components/form/FieldError'
import { FormAlert } from '@/components/form/FormAlert'
import { cn } from '@/lib/utils'

/**
 * 409 は「申請できる状態ではなくなった」＝この画面自体が別の状態へ切り替わることを意味する。
 * 文言はフォームではなく親（`SellerApplicationView`）が出す — invalidate によって
 * フォームがアンマウントされ、ここに出すと一瞬も表示されないため。
 */
const CONFLICT_MESSAGES: Record<string, string> = {
  SELLER_APPLICATION_PENDING: '審査中の申請があります。結果が出るまでお待ちください。',
  SELLER_APPLICATION_ALREADY_APPROVED: 'すでに出品者として承認されています。',
}

/** 画面構造が変わらないエラーはフォーム内に出す */
const INLINE_MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: '入力内容を確認してください。',
}

const COPY = {
  create: {
    heading: '申請理由',
    description: 'どのような商品を販売したいか、これまでの経験などをご記入ください。',
    submit: '申請する',
  },
  reapply: {
    heading: '再申請',
    description: '内容を見直して、あらためて申請できます。',
    submit: '再申請する',
  },
} as const

interface SellerApplicationFormProps {
  /** `create` = 未申請からの新規申請 / `reapply` = 却下後の再申請。文言のみが変わる */
  mode: keyof typeof COPY
  onSubmitted: () => void
  onConflict: (message: string) => void
}

export function SellerApplicationForm({
  mode,
  onSubmitted,
  onConflict,
}: SellerApplicationFormProps) {
  const mutation = useCreateSellerApplicationMutation()
  const { heading, description, submit } = COPY[mode]

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<SellerApplicationFormValues>({
    resolver: zodResolver(sellerApplicationSchema),
    defaultValues: { reason: '' },
  })

  // watch() を使うと React Compiler がこのコンポーネントのメモ化を諦める
  const reason = useWatch({ control, name: 'reason' })
  // zod は trim 後に評価するため末尾空白の分だけずれるが、カウンターは先に赤くなる側にずれる
  const length = reason?.length ?? 0

  const conflictMessage = CONFLICT_MESSAGES[apiErrorCode(mutation.error) ?? '']

  const onSubmit = (values: SellerApplicationFormValues) => {
    mutation.mutate(
      { reason: values.reason },
      {
        onSuccess: onSubmitted,
        onError: (error) => {
          const message = CONFLICT_MESSAGES[apiErrorCode(error) ?? '']
          if (message) onConflict(message)
        },
      },
    )
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
      <div className="space-y-2">
        <h2 className="text-foreground border-border border-b pb-2 font-serif text-lg font-bold">
          {heading}
        </h2>
        <p className="text-muted-foreground text-sm leading-relaxed">{description}</p>
      </div>

      {mutation.isError && !conflictMessage && (
        // 出現だけ 150ms でフェードさせる（設計書 §9）。既存 keyframes の使い回し
        <FormAlert className="motion-safe:animate-[auth-fade_150ms_ease-out]">
          {resolveApiError(mutation.error, INLINE_MESSAGES)}
        </FormAlert>
      )}

      <div className="space-y-2">
        {/* 見出しはラベルを兼ねない。文字列が重複するためラベルは sr-only にする */}
        <Label htmlFor="reason" className="sr-only">
          申請理由
        </Label>
        {/* maxLength は付けない。貼り付けた長文が無言で切り捨てられ、気づけないため */}
        <Textarea
          id="reason"
          className="min-h-40"
          autoComplete="off"
          aria-invalid={!!errors.reason || undefined}
          aria-describedby="reason-error"
          {...register('reason')}
        />
        <div className="flex items-start justify-between gap-4">
          <FieldError id="reason-error" message={errors.reason?.message} help="1000文字以内" />
          {/* 1 文字ごとに読み上げると入力が成立しないため支援技術には渡さない */}
          <span
            aria-hidden
            className={cn(
              'shrink-0 text-xs tabular-nums transition-colors duration-150',
              length > MAX_REASON_LENGTH ? 'text-destructive' : 'text-muted-foreground',
            )}
          >
            {length} / {MAX_REASON_LENGTH}
          </span>
        </div>
      </div>

      <div className="flex justify-end">
        <Button
          type="submit"
          disabled={mutation.isPending}
          className="bg-accent text-accent-foreground hover:bg-accent/90 h-11 w-full sm:w-auto sm:min-w-40"
        >
          {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
          {submit}
        </Button>
      </div>
    </form>
  )
}
