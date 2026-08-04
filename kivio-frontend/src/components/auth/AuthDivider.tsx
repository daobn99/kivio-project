/**
 * 「または」ディバイダー。email フォームとソーシャルログインの区切り。
 * フォーム側はカードなし（背景色直置き）のため bg-background でラインを切り抜く。
 */
export function AuthDivider() {
  return (
    <div className="relative my-6">
      <div className="absolute inset-0 flex items-center">
        <span className="border-border w-full border-t" />
      </div>
      <div className="relative flex justify-center">
        <span className="bg-background text-muted-foreground px-3 text-xs">または</span>
      </div>
    </div>
  )
}
