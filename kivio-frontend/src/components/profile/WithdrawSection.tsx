'use client'
import { WithdrawDialog } from '@/components/profile/WithdrawDialog'

const CONSEQUENCES = [
  'ログインできなくなります',
  '注文履歴を参照できなくなります',
  '登録した配送先住所が使えなくなります',
]

/** 実行そのものは WithdrawDialog に隔離し、ここでは影響の明示とトリガーだけを持つ。 */
export function WithdrawSection() {
  return (
    <div className="space-y-5">
      <div className="space-y-2 text-sm">
        <p className="text-foreground">アカウントを閉じると:</p>
        <ul className="text-muted-foreground list-disc space-y-1 pl-5">
          {CONSEQUENCES.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </div>

      <div className="flex sm:justify-end">
        <WithdrawDialog />
      </div>
    </div>
  )
}
