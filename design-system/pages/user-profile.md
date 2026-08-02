# アカウント設定画面レイアウト設計仕様

# プロフィール設定 / 配送先住所管理

**対象画面:** `/profile/settings`（Phase 2）, `/profile/addresses`（Phase 3・OQ-2）
**対象コンポーネント:** `(authenticated)/layout.tsx`, `profile/layout.tsx`, `AccountNav`, `AccountIdentityHeader`, `SettingsSection`, `ProfileSettingsForm`, `ChangePasswordForm`, `WithdrawDialog`, `AddressList`, `AddressCard`, `AddressFormSheet`, `DeleteAddressDialog`
**スコープ:** `/profile/*` セグメント共通レイアウト（グローバル chrome は**使う**）
**MASTER.md との関係:** このファイルのルールが MASTER.md を上書きする（§16 準拠）
**layout.md との関係:** `(auth)` と異なり、`/profile/*` は `layout.md` の `GlobalHeader` / `GlobalFooter` / `MobileBottomNav` を**そのまま使用する**。本ファイルはその内側の「アカウント領域 chrome」（サイドナビ・アイデンティティヘッダー・セクション）のみを定義する。
**auth.md との関係:** 入力コンポーネントの方針が**意図的に異なる**（§7.1）。`FloatingLabelInput` は認証画面専用とし、設定画面では使わない。
**実装タスク:** `docs/implementation-plans/user-profile.md` U-13〜U-18（U-00〜U-12 はバックエンド完了済み）
**作成日:** 2026-08-01

---

## 1. 設計原則 — 設定画面は「認証フォーム」ではなく「台帳」である

`/auth/*` は**初対面のユーザーに 1 つのタスクを完遂させる**画面だった。`/profile/*` は正反対で、**既にログインしている人が、既に存在する自分のデータを、点検し、必要な箇所だけ書き換える**画面である。この違いが chrome・入力方式・保存モデルすべての判断根拠になる。

| 原則 | `/auth/*`（既存） | `/profile/*`（本仕様） |
|---|---|---|
| **出口の扱い** | 出口を消す（離脱＝カゴ落ち） | 出口を残す。グローバル chrome をフルで表示し、設定を終えたらすぐ買い物へ戻れるようにする |
| **タスク数** | 1 画面 1 タスク（順送り） | 1 画面に**独立した複数タスク**（表示名・パスワード・退会）が並列に存在する |
| **入力の初期状態** | 常に空 | **既存値が入っている**（＝フローティングラベルの利点が消える。§7.1） |
| **保存の単位** | フォーム全体を 1 回送信 | **セクション単位で個別に送信**（`PATCH /users/me` と `PATCH /users/me/password` は別 API・別トランザクション） |
| **失敗のコスト** | 入り直せばよい | 誤操作が不可逆（**退会**）。破壊的操作は隔離し二段階にする |
| **視覚的重心** | フォームへ集約 | **走査（スキャン）性**へ。何がどこにあるか一目で分かる区画割りを優先する |

**結論:** アカウント設定の chrome は「フォームの装飾」ではなく **"台帳（ledger）" の罫線** として設計する。カードを敷き詰めて箱を増やすのではなく、**罫線で区画を切り、セクション見出しを左段に置いた 2 カラムのエディトリアル構成**にする（§6）。塗り面は**アイデンティティヘッダー 1 箇所だけ**に許し、それ以外は白地＋罫線で通す。これにより「情報の階層」が UI chrome ではなく**組版**で表現され、`MASTER.md §1`「Simple & Modern／UI chrome を極限まで抑える」に忠実なまま、設定画面としての可読性を得る。

---

## 2. ダークモード方針

`layout.md §1` に準拠し**ライトモード固定**。`/profile/*` はバイヤー画面であり `dark` クラスを付けない。Phase 2 はダークモード無効。

---

## 3. 全体レイアウト構造

### 3.1 レイヤー構造

```
GlobalHeader（layout.md §3・スティッキー z-20）
  └─ AnnouncementBar / HeaderMain / CategoryNav
─────────────────────────────────────────────
main#main-content
  └─ AccountIdentityHeader（§5・全幅の淡色帯）
  └─ アカウント 2 カラム（lg 以上）
       ├─ AccountNav（左・sticky）
       └─ ページコンテンツ（右・max-w-3xl）
─────────────────────────────────────────────
GlobalFooter（layout.md §9）
MobileBottomNav（< md・layout.md §8）
```

### 3.2 デスクトップ / ノート（lg 〜）

```
┌──────────────────────────────────────────────────────────────┐
│  GlobalHeader（検索・カート・アバターメニュー）                  │
├──────────────────────────────────────────────────────────────┤
│  ┌─ AccountIdentityHeader ─────────────────────────────────┐ │
│  │  (avatar)  山田 太郎                      [ 購入者 ]      │ │  bg-secondary/60
│  │   64px     yamada@example.com · 2026年5月から利用          │ │  border-y
│  └──────────────────────────────────────────────────────────┘ │
│                                                                │
│  ┌── AccountNav ───┐   ┌── コンテンツ（max-w-3xl）──────────┐  │
│  │ アカウント        │   │                                  │  │
│  │ ▎プロフィール設定 │   │  プロフィール                     │  │
│  │   配送先住所      │   │  ─────────────────────────────── │  │
│  │   注文履歴        │   │  表示名と…    │ (avatar) [URL___] │  │
│  │                  │   │  アバターを…  │ 表示名  [_______] │  │
│  │ （sticky top-24）│   │              │        [ 保存 ]   │  │
│  └──────────────────┘   │                                  │  │
│        w-56 (224px)     │  アカウント情報                    │  │
│                         │  ─────────────────────────────── │  │
│                         │  ログイン情報 │ メール yamada@…    │  │
│                         │              │ ロール 購入者      │  │
│                         │                                  │  │
│                         │  パスワード                       │  │
│                         │  ─────────────────────────────── │  │
│                         │  定期的な変更 │ 現在の… [_______] │  │
│                         │  を推奨します │ 新しい… [_______] │  │
│                         │              │      [ 変更する ] │  │
│                         │                                  │  │
│                         │  ┌ 退会 ────────────────────────┐│  │
│                         │  │ 危険な操作（border-destructive）││  │
│                         │  └──────────────────────────────┘│  │
│                         └──────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────┤
│  GlobalFooter                                                 │
└──────────────────────────────────────────────────────────────┘
```

| 要素 | 幅 |
|---|---|
| 外側コンテナ | `max-w-7xl mx-auto px-6` |
| `AccountNav` | `w-56`（224px）固定・`shrink-0` |
| コンテンツ列 | `flex-1 max-w-3xl`（768px）※ 設定フォームの 1 行が長くなりすぎないため |
| 列間ギャップ | `gap-10`（40px） |

### 3.3 タブレット（md 〜 lg）

サイドナビは**横並びのセグメントナビ**に切り替え、コンテンツ列の上に置く（縦 2 段）。左 224px を削ってフォームの実効幅を確保する。

### 3.4 モバイル（< md）

```
┌──────────────────────────┐
│  GlobalHeader（簡易）      │
├──────────────────────────┤
│  (avatar) 山田 太郎        │  ← AccountIdentityHeader（縦積み・アバター 48px）
│  yamada@example.com       │
├──────────────────────────┤
│ [プロフィール][住所][注文]  │  ← AccountNav 横スクロールセグメント（sticky top-14）
├──────────────────────────┤
│  プロフィール              │
│  ──────────────────────  │  ← セクションは 1 カラム縦積み（見出し → 説明 → フィールド）
│  表示名と写真を変更します    │
│  (avatar)                 │
│  アバター画像URL           │
│  [____________________]   │
│  表示名                    │
│  [____________________]   │
│  [        保存        ]   │  ← モバイルは w-full
│                          │
│  …                        │
├──────────────────────────┤
│  GlobalFooter             │
│  MobileBottomNav（固定）   │  ← pb-16 でコンテンツが隠れないようにする
└──────────────────────────┘
```

### 3.5 ルートグループとレイアウト骨格

`FRONTEND_IA.md §3` のディレクトリ設計に従い **`(authenticated)`** ルートグループを新規作成する。
> `user-profile.md §6.3` は仮称 `(protected)` と記載しているが、IA が URL/ディレクトリ設計の正であるため **`(authenticated)` を採用**する。URL には影響しない（`/profile/settings`）。認証ガードは `src/proxy.ts` の `PROTECTED_PREFIXES`（`/profile` 登録済み）が担い、ルートグループは**レイアウト共有のためだけ**に存在する。

```tsx
// src/app/(authenticated)/layout.tsx — SC
// (public)/layout.tsx と同じグローバル chrome を持つ。差分は「認証前提」であることのみ。
import { GlobalHeader } from '@/components/layout/GlobalHeader'
import { GlobalFooter } from '@/components/layout/GlobalFooter'
import { MobileBottomNav } from '@/components/layout/MobileBottomNav'

export default function AuthenticatedLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <a
        href="#main-content"
        className="focus:bg-background focus:ring-ring sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-1000 focus:rounded-md focus:px-4 focus:py-2 focus:ring-2"
      >
        メインコンテンツへスキップ
      </a>
      <GlobalHeader />
      <main id="main-content" className="flex-1 pb-16 md:pb-0">
        {children}
      </main>
      <GlobalFooter />
      <MobileBottomNav />
    </>
  )
}
```

```tsx
// src/app/(authenticated)/profile/layout.tsx — SC
// アカウント領域 chrome（アイデンティティヘッダー + サイドナビ）。/profile/* 全ページで共有する。
import { AccountIdentityHeader } from '@/components/profile/AccountIdentityHeader'
import { AccountNav } from '@/components/profile/AccountNav'

export default function ProfileLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="pb-10 md:pb-24">
      <AccountIdentityHeader />

      <div className="mx-auto flex max-w-7xl flex-col gap-8 px-6 pt-8 lg:flex-row lg:gap-10 lg:pt-10">
        <AccountNav />
        <div className="min-w-0 flex-1 lg:max-w-3xl">{children}</div>
      </div>
    </div>
  )
}
```

- `min-w-0` は必須（`flex` 子要素の既定 `min-width:auto` により、長いメールアドレス等で横スクロールが出るのを防ぐ）。
- `pb-16 md:pb-0` はグローバルレイアウト側で `MobileBottomNav` 分を確保済み（`layout.md §8.4`）。`profile/layout.tsx` 側では**重複して足さない**（上記 `pb-10 md:pb-24` はフッターとの間の余白であり、ボトムナビ回避分ではない点に注意）。

---

## 4. アカウントサイドナビ（`AccountNav`）

### 4.1 項目

| 項目 | リンク | 表示条件 | フェーズ |
|---|---|---|---|
| プロフィール設定 | `/profile/settings` | 常時 | Phase 2 |
| 配送先住所 | `/profile/addresses` | `role !== ROLE_ADMIN`（IA §1.2b: 本人のみ BUYER / SELLER） | Phase 3（OQ-2） |
| 注文履歴 | `/orders` | `role !== ROLE_ADMIN` | Phase 3・**リンクのみ**（実装は別スライス） |

- セラー系（ダッシュボード等）は**このナビに入れない**。ヘッダーの「セラーダッシュボード」ボタン（`layout.md §4.3`）が正規導線であり、購入者としての設定と販売者としての管理を混ぜない。
- Phase 2 時点で表示される実リンクは「プロフィール設定」のみになりうる。**項目が 1 件でもナビは表示する**（後続フェーズでの位置の一貫性・現在地の明示のため）。

### 4.2 アクティブ表現 — ティールのレール

アクティブ項目は「塗りつぶしたピル」ではなく **左端 2px のティールレール ＋ 淡いセカンダリ面**で示す。塗りピルは面積が大きく、白基調のページで視線を奪いすぎるため使わない。

```tsx
// src/components/profile/AccountNav.tsx — CC（usePathname を使うため）
'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { User, MapPin, Package } from 'lucide-react'
import { cn } from '@/lib/utils'

const ITEMS = [
  { href: '/profile/settings', label: 'プロフィール設定', icon: User },
  { href: '/profile/addresses', label: '配送先住所', icon: MapPin },
  { href: '/orders', label: '注文履歴', icon: Package },
] as const

export function AccountNav() {
  const pathname = usePathname()

  return (
    <nav aria-label="アカウントメニュー" className="lg:w-56 lg:shrink-0">
      {/* lg: 縦サイドナビ（sticky） / < lg: 横スクロールセグメント */}
      <ul
        className={cn(
          'flex gap-1 overflow-x-auto lg:sticky lg:top-24 lg:flex-col lg:overflow-visible',
          // モバイルは端まで引き伸ばしてスクロール可能に見せる（-mx-6 px-6）
          '-mx-6 px-6 lg:mx-0 lg:px-0',
        )}
      >
        {ITEMS.map(({ href, label, icon: Icon }) => {
          const active = pathname === href || pathname.startsWith(`${href}/`)
          return (
            <li key={href} className="shrink-0">
              <Link
                href={href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'focus-visible:ring-ring flex h-11 items-center gap-2 rounded-md px-3 text-sm whitespace-nowrap transition-colors duration-150 focus-visible:ring-2 focus-visible:outline-none',
                  // lg 以上: 左レール表現 / lg 未満: 下線タブ表現
                  'lg:border-l-2 lg:rounded-l-none',
                  active
                    ? 'text-primary bg-secondary/70 font-medium lg:border-l-accent'
                    : 'text-muted-foreground hover:text-foreground hover:bg-muted lg:border-l-transparent',
                )}
              >
                <Icon className="h-4 w-4 shrink-0" aria-hidden />
                {label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
```

| 状態 | ライン | 面 | 文字 |
|---|---|---|---|
| 非アクティブ | `border-l-transparent` | なし | `text-muted-foreground` |
| ホバー | 同上 | `bg-muted` | `text-foreground` |
| アクティブ | `border-l-accent`（2px ティール） | `bg-secondary/70` | `text-primary font-medium` |
| フォーカス | `ring-2 ring-ring` | — | — |

- **カラー単独に依存しない**（`MASTER.md §14`）: アクティブは色＋`font-medium`＋`aria-current="page"` の 3 重で示す。
- 高さ `h-11`（44px）でタッチターゲット要件を満たす。
- `lg` 未満では横スクロールセグメント。`sticky top-14`（ヘッダー直下）にするかは**しない**——モバイルはページが短くスクロール追従の価値が薄い一方、固定要素が増えるとフォーム領域が痩せるため。

---

## 5. アイデンティティヘッダー（`AccountIdentityHeader`）

ページ内で**唯一の塗り面**。「今どのアカウントを編集しているのか」を最上部で確定させ、以降のセクションを白地の罫線構成にするための基準面になる。

```tsx
// src/components/profile/AccountIdentityHeader.tsx — CC（useAuthStore を読むため）
'use client'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { useAuthStore } from '@/stores/useAuthStore'

export function AccountIdentityHeader() {
  const user = useAuthStore((s) => s.user)
  if (!user) return <AccountIdentityHeaderSkeleton />   // §10

  return (
    <header className="bg-secondary/60 border-border border-y">
      <div className="mx-auto flex max-w-7xl items-center gap-4 px-6 py-6 sm:gap-5 sm:py-8">
        <Avatar className="ring-background size-12 shrink-0 ring-2 sm:size-16">
          <AvatarImage src={user.avatarUrl ?? undefined} alt="" />
          <AvatarFallback className="bg-primary text-primary-foreground font-serif text-lg">
            {user.displayName.slice(0, 1)}
          </AvatarFallback>
        </Avatar>

        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <h1 className="text-foreground truncate font-serif text-xl font-bold sm:text-2xl">
              {user.displayName}
            </h1>
            <Badge variant="secondary" className="bg-background text-primary border-border border">
              {ROLE_LABEL[user.role]}
            </Badge>
          </div>
          <p className="text-muted-foreground truncate text-sm">
            {user.email}
            <span className="hidden sm:inline"> · {formatJoinedMonth(user.createdAt)}から利用</span>
          </p>
        </div>
      </div>
    </header>
  )
}
```

**仕様:**

| 項目 | 指定 |
|---|---|
| 背景 | `bg-secondary/60`（#EEF3F9 の 60%）。純白との差を最小限にし、**帯であって主役ではない**ことを示す |
| 区切り | `border-y border-border`（上下ヘアライン）。影は使わない |
| アバター | `size-12`（モバイル） / `size-16`（sm 以上）。`ring-2 ring-background` で帯から浮かせる |
| フォールバック | 表示名の**先頭 1 文字**を `bg-primary` に白抜き・`font-serif`（`MASTER.md §12` イニシャル表示） |
| 表示名 | `font-serif text-xl sm:text-2xl font-bold`。ページの `<h1>` を兼ねる |
| ロールバッジ | `ROLE_BUYER` → 「購入者」/ `ROLE_SELLER` → 「販売者」/ `ROLE_ADMIN` → 「管理者」。白地＋枠線（帯の上で塗りバッジは重い） |
| 参加日 | `2026年5月から利用`。`sm` 未満は非表示（横幅優先） |

- `<h1>` はこのヘッダーに 1 つだけ置く。各ページ・各セクションの見出しは `<h2>` 以下（§13）。
- ユーザー情報は `useAuthStore` から読む。`AuthHydrator` が未完了の間はスケルトン（§10）。**`GET /users/me` をここで再フェッチしない**（ストアが正・`ProfileSettingsForm` の保存成功時に `setUser` で同期される）。

---

## 6. セクションパターン（`SettingsSection`）

設定画面の全区画はこのコンポーネントで組む。**Card を使わない**（`MASTER.md §10` の Card は商品カード＝クリック可能な情報単位のための chrome であり、設定の区画に流用すると「箱の中に箱」が増える）。区画は**上罫線 ＋ 見出し列**で切る。

```
lg 以上（2 カラム）                        lg 未満（縦積み）
──────────────────────────────────       ──────────────────────
プロフィール         │ [フィールド群]       プロフィール
表示名とアバターを    │                     表示名とアバターを変更します
変更します           │ [       保存 ]      ────────────────────
                    │                     [フィールド群]
                                          [       保存       ]
```

```tsx
// src/components/profile/SettingsSection.tsx — SC
import { cn } from '@/lib/utils'

interface SettingsSectionProps {
  title: string
  description?: string
  children: React.ReactNode
  /** 退会セクション等、区画自体を強調する場合 */
  tone?: 'default' | 'danger'
}

export function SettingsSection({ title, description, children, tone = 'default' }: SettingsSectionProps) {
  return (
    <section
      className={cn(
        'grid gap-4 py-8 lg:grid-cols-[minmax(0,14rem)_minmax(0,1fr)] lg:gap-10',
        tone === 'default' && 'border-border border-t first:border-t-0 first:pt-0',
        tone === 'danger' &&
          'border-destructive/30 bg-destructive/3 mt-8 rounded-xl border px-5 py-6 lg:px-6',
      )}
    >
      <div className="space-y-1.5">
        <h2
          className={cn(
            'font-serif text-lg font-bold',
            tone === 'danger' ? 'text-destructive' : 'text-foreground',
          )}
        >
          {title}
        </h2>
        {description && <p className="text-muted-foreground text-sm leading-relaxed">{description}</p>}
      </div>

      <div className="min-w-0">{children}</div>
    </section>
  )
}
```

**ルール:**

- 見出しは `font-serif text-lg font-bold`（`<h2>`）。本文・ラベル・入力はすべて Sans（`MASTER.md §3.3`）。**Serif を見出しだけに閉じる**ことで、罫線しかない画面に階層とブランドの気配を与える。
- 見出し列は `minmax(0, 14rem)`（224px）＝サイドナビと同じ幅。**ページ左端からのリズムが揃う**（サイドナビ 224 / 見出し列 224）。
- セクション間の区切りは `border-t border-border` のみ。影・角丸・背景は使わない（`tone="danger"` を除く）。
- `first:border-t-0 first:pt-0` で先頭セクションの上罫線を消す（アイデンティティヘッダーの下罫線と二重線になるのを防ぐ）。

---

## 7. フォーム入力の方針

### 7.1 フローティングラベルは使わない（auth.md からの意図的な逸脱）

`auth.md §6.3` は認証系の全入力を `FloatingLabelInput` に統一した。**設定画面ではこれを使わない。**

| 理由 | 説明 |
|---|---|
| **初期値が入っている** | フローティングラベルの価値は「未入力時にラベルがプレースホルダー位置に出て縦幅を節約する」点にある。設定画面の `displayName` / `avatarUrl` / 住所は**最初から値が入っている**ため、ラベルは常に浮上位置に固定され、`h-14` の縦幅だけが残る |
| **走査性** | 設定画面は「読む → 一部だけ直す」画面。ラベルが常に同じ位置・同じサイズで見出しとして並ぶ通常ラベルの方が、値との対応をスキャンしやすい |
| **住所フォームの密度** | 住所は 6 フィールド。`h-14` × 6 ＝ 336px はシートに収まりにくく、スクロールが増える |
| **一貫性の単位** | 「認証フローはフローティング／アカウント設定は通常ラベル」という**画面クラス単位の一貫性**を採る。同一画面内で混在させない限り、利用者は違和感を持たない |

→ 設定・住所の全入力は **`<Label>` ＋ `<Input>`（shadcn）** を使う。`FloatingLabelInput` は `(auth)` 専用のまま据え置く。

### 7.2 フィールドの寸法・状態

shadcn の `Input` は既定 `h-8`（32px）で、**タッチターゲット 44px 要件（`MASTER.md §15`）を満たさない**。設定・住所フォームでは必ず `h-11` に上書きする。

```tsx
<Input className="h-11" ... />
```

| 状態 | 表現 |
|---|---|
| 通常 | `border-input`・`bg-transparent`・`text-base`（`md:text-sm`） |
| フォーカス | `focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50`（shadcn 既定＝ティールリング） |
| エラー | `aria-invalid` → `border-destructive` ＋ フィールド直下に `text-sm text-destructive`（`FieldError` を再利用） |
| 読み取り専用（メール等） | `<Input>` を使わず**プレーンテキストで表示**（§8.2）。`disabled` な入力枠は「編集できるのでは」という誤解と低コントラストを招く |

### 7.3 フィールド行のレイアウト（`FormRow`）

```tsx
// ラベル → 入力 → エラー/ヘルプ の縦積み。設定・住所フォーム共通の最小単位。
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
  <FieldError id="displayName-error" message={errors.displayName?.message} help="100文字以内" />
</div>
```

- `FieldError`（`src/components/auth/FieldError.tsx`）は auth 実装で既に汎用。**`src/components/form/FieldError.tsx` へ移設**して auth / profile の両方から使う（§14）。
- ラベルは**常に表示**。placeholder で代替しない（`MASTER.md §10` フォーム規約）。
- placeholder は**書式の例示に限る**（例: 郵便番号 `150-0002`）。ラベルの言い換えを placeholder に入れない。

### 7.4 パスワード入力（`PasswordInput`）

auth の `PasswordField`（フローティング＋トグル）は流用できないため、**通常ラベル版のトグル付き入力**を `components/form/` に新設する。トグルの `aria-label` / `aria-pressed` の仕様は `auth.md §6.3.3` と完全に揃える。

```tsx
// src/components/form/PasswordInput.tsx — CC
'use client'
import { forwardRef, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

export const PasswordInput = forwardRef<HTMLInputElement, Omit<React.ComponentProps<'input'>, 'type'>>(
  function PasswordInput({ className, ...props }, ref) {
    const [visible, setVisible] = useState(false)
    return (
      <div className="relative">
        <Input ref={ref} type={visible ? 'text' : 'password'} className={cn('h-11 pr-11', className)} {...props} />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'パスワードを非表示' : 'パスワードを表示'}
          aria-pressed={visible}
          className="text-muted-foreground hover:text-foreground focus-visible:ring-ring absolute top-1/2 right-2 -translate-y-1/2 rounded-md p-1.5 focus-visible:ring-2 focus-visible:outline-none"
        >
          {visible ? <EyeOff className="h-5 w-5" aria-hidden /> : <Eye className="h-5 w-5" aria-hidden />}
        </button>
      </div>
    )
  },
)
```

### 7.5 保存モデル — セクション独立保存・ダーティ時のみ活性

**画面全体の「保存」ボタンは置かない。** `PATCH /users/me` と `PATCH /users/me/password` は別 API・別トランザクションであり、1 つのボタンに束ねると「表示名は保存されたがパスワードは失敗した」という部分成功をユーザーに説明できない。

| 規則 | 内容 |
|---|---|
| ボタン位置 | 各セクションのフィールド群**直下・右寄せ**（`lg` 未満は `w-full`） |
| 活性条件 | `formState.isDirty` が `true` かつ `!isPending`。未変更時は `disabled`（`opacity-50 pointer-events-none`。`MASTER.md §14` の「非活性に opacity 0.3 を使わない」に準拠） |
| 送信中 | `Loader2` スピナー ＋ `disabled`（二重送信防止）。ラベルは変えない |
| ペイロード | **ダーティなフィールドのみ送信**（`PATCH` の部分更新セマンティクス。RHF の `formState.dirtyFields` で絞る）。未変更フィールドを送ると `avatarUrl` の意図しない上書きを招く |
| 成功後 | `reset(newValues)` でフォームを「クリーン」に戻し、ボタンを再び非活性にする |

```tsx
<div className="flex justify-end pt-2">
  <Button
    type="submit"
    disabled={!isDirty || isPending}
    className="bg-accent text-accent-foreground hover:bg-accent/90 h-11 w-full sm:w-auto sm:min-w-32"
  >
    {isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
    保存する
  </Button>
</div>
```

### 7.6 成功フィードバック — インラインで、操作した場所に返す

**トーストは使わない。** `MASTER.md §10` は Sonner を挙げているが、当プロジェクトには `sonner` が未導入であり（`package.json` 依存に無し）、かつ設定画面では**どのセクションの保存が成功したのか**を操作地点で返す方が正確である。新規依存を足さずに要件を満たせるため、インライン方式を採用する。

```tsx
// src/components/form/SaveStatus.tsx — CC
// 表示状態は state ではなく「表示済みの signal」との比較で導出する。
// エフェクト内で同期的に setState するとカスケードレンダリングになるため、
// setState はタイマーのコールバック（非同期）だけで行う。
export function SaveStatus({ signal, message = '保存しました' }: { signal: number; message?: string }) {
  const [dismissed, setDismissed] = useState(0)
  const visible = signal !== 0 && signal !== dismissed

  useEffect(() => {
    if (signal === 0) return
    const timer = setTimeout(() => setDismissed(signal), 4000)
    return () => clearTimeout(timer)
  }, [signal])

  return (
    <p aria-live="polite" className="text-success flex min-h-5 items-center gap-1.5 text-sm">
      {visible && (
        <span key={signal} className="motion-safe:animate-[auth-fade_150ms_ease-out] flex items-center gap-1.5">
          <Check className="h-4 w-4" aria-hidden />
          {message}
        </span>
      )}
    </p>
  )
}
```

呼び出し側は保存成功のたびに `setSavedAt(Date.now())` で `signal` を更新する（真偽値ではなく単調増加値にすることで「連続保存」でも再表示される）。

- 保存ボタンの**左隣**（`lg`）／ボタンの**上**（モバイル）に置く。
- `aria-live="polite"` で 1 度だけ読み上げる。表示は **4000ms 後に消す**（`MASTER.md §10` のトースト自動消去時間に揃える）か、次の編集（`isDirty` 復帰）で消す。
- `min-h-5` を確保して、出現時にレイアウトがずれない（CLS 防止）。
- 成功はアイコン＋テキスト＋色の 3 重表現（色単独禁止・`MASTER.md §14`）。

### 7.7 サーバーエラー

`FormAlert`（`src/components/auth/FormAlert.tsx`・`role="alert"`）を**セクション単位**で再利用し、そのセクションのフィールド群の**上**に出す。ページ最上部にまとめない（どの操作が失敗したのか分からなくなる）。`FieldError` 同様、`components/form/` へ移設して共用する（§14）。

---

## 8. `/profile/settings` — プロフィール設定（Phase 2）

`FRONTEND_API_CONTRACT §6.2` の 4 API に 1:1 対応する 4 セクション。並び順は**更新頻度が高い順 → 破壊的な順**。

| 順 | セクション | API | コンポーネント |
|---|---|---|---|
| 1 | プロフィール | `PATCH /users/me` | `ProfileSettingsForm` |
| 2 | アカウント情報（読み取り専用） | `GET /users/me` | `AccountInfoSection` |
| 3 | パスワード | `PATCH /users/me/password` | `ChangePasswordForm` |
| 4 | 退会（Danger Zone） | `DELETE /users/me` | `WithdrawSection` + `WithdrawDialog` |

```tsx
// src/app/(authenticated)/profile/settings/page.tsx — SC
export const metadata = { title: 'プロフィール設定 | Kivio' }

export default function ProfileSettingsPage() {
  return (
    <div className="divide-border">
      <SettingsSection title="プロフィール" description="表示名とアバター画像を変更します。表示名は商品ページやレビューに表示されます。">
        <ProfileSettingsForm />
      </SettingsSection>

      <SettingsSection title="アカウント情報" description="ログインに使う情報と現在の権限です。">
        <AccountInfoSection />
      </SettingsSection>

      <SettingsSection title="パスワード" description="定期的な変更をおすすめします。変更後も他の端末のログインは維持されます。">
        <ChangePasswordForm />
      </SettingsSection>

      <SettingsSection tone="danger" title="退会" description="アカウントを閉じます。この操作は取り消せません。">
        <WithdrawSection />
      </SettingsSection>
    </div>
  )
}
```

### 8.1 プロフィール（`ProfileSettingsForm`）

```
(avatar 64px)   アバター画像 URL
  プレビュー     [https://res.cloudinary.com/...        ]
                画像アップロードは近日対応予定です

              表示名
              [山田 太郎                              ]
              100文字以内

                       保存しました ✓   [   保存する   ]
```

**仕様:**

- **アバターは「URL 入力 ＋ 左にライブプレビュー」**。ドロップゾーン風・カメラアイコン付きの円形ボタンなど、**アップロードできるように見える UI にしない**（Cloudinary 連携は本スライス範囲外・`user-profile.md §1 スコープ外`）。ヘルプ文で「画像アップロードは近日対応予定です」と明示する。
  - プレビューは `watch('avatarUrl')` を `<Avatar>` に流し込む。無効 URL / 読み込み失敗時は `AvatarFallback`（イニシャル）に落ちる。
  - Phase 3 でアップローダに差し替える際、**この行だけを置換**すれば済むよう、`AvatarUrlField` として独立させる。
- `displayName`: `autoComplete="nickname"`、1〜100 文字（`VALIDATION_RULES §2.1`）。
- 成功時の副作用（`FRONTEND_API_CONTRACT §6.2`）:
  1. `useAuthStore.setUser(updated)` → ヘッダーのアバターメニューとアイデンティティヘッダーに即時反映
  2. `queryClient.invalidateQueries({ queryKey: queryKeys.user.me })`
  3. `reset(updated)` → ダーティ解除
  4. `SaveStatus` を表示
- `dirtyFields` に含まれるキーのみ送信（§7.5）。`avatarUrl` を空文字にした場合の扱いは **OQ-P1**（§17）。

### 8.2 アカウント情報（`AccountInfoSection`・読み取り専用）

変更 API が存在しない値（メール・ロール・登録日）を**定義リストで表示**する。入力枠を置かない（§7.2）。

```tsx
<dl className="divide-border divide-y text-sm">
  {[
    { term: 'メールアドレス', desc: user.email },
    { term: 'アカウント種別', desc: ROLE_LABEL[user.role] },
    { term: '登録日', desc: formatDate(user.createdAt) },
  ].map(({ term, desc }) => (
    <div key={term} className="grid grid-cols-[8rem_minmax(0,1fr)] gap-4 py-3 first:pt-0 last:pb-0">
      <dt className="text-muted-foreground">{term}</dt>
      <dd className="text-foreground truncate">{desc}</dd>
    </div>
  ))}
</dl>
```

- メールアドレスの変更導線は Phase 2 に無い。**「変更できません」等の否定文を置かない**（できないことをわざわざ言わない）。将来 API が生えたら行末に「変更」リンクを足せる構造にしておく。
- `status`（`ACTIVE` / `INACTIVE`）は**表示しない**。`ACTIVE` しか到達しない画面で状態バッジを出すのはノイズ。`INACTIVE` の扱いはログイン時点で `USER_DEACTIVATED` として処理済み（`auth.md §7.3`）。

### 8.3 パスワード（`ChangePasswordForm`）

```
現在のパスワード
[••••••••••••                        ] 👁

新しいパスワード
[                                    ] 👁
8文字以上72文字以内

                     [  パスワードを変更  ]
```

**仕様:**

- 3 フィールド目（確認用）は**置かない**。`newPassword` に表示トグルがあり、失敗しても再変更できるため、確認欄は摩擦のみが増える。（登録フロー `auth.md §8.4` に確認欄があるのは、失敗した場合にログインできなくなる＝復旧コストが桁違いだから。ここでは現在のパスワードを知っている前提が既にある。）
- `autoComplete`: 現在＝`current-password` / 新規＝`new-password`。
- バリデーション（`VALIDATION_RULES §2.3`）: `newPassword` 8〜72 文字。`currentPassword` は `@NotBlank` 相当。
- 成功時: **204 No Content** → フィールドを `reset()` で空にし、`SaveStatus` に「パスワードを変更しました」を表示。トークンの再発行やログアウトは**行わない**（バックエンドはセッション無効化を実装していない）。説明文で「変更後も他の端末のログインは維持されます」と事実を明示する。
- 失敗時: `PASSWORD_CHANGE_FAILED`（400）を**セクション上部の `FormAlert`** に表示。フィールド単位のエラーにはしない（現在のパスワード不一致か Google 専用アカウントかを API が区別しないため）。

> **Google ログイン専用ユーザーの扱い（要判断・OQ-P2）**
> バックエンドは `passwordHash == null` のユーザーにも同じ `PASSWORD_CHANGE_FAILED` を返すが、**`GET /users/me`（`AuthUser`）に `hasPassword` 相当のフィールドが無いため、フロントはパスワード未設定を事前に判別できない**。
> **暫定方針（Phase 2）:** セクションは常時表示し、`PASSWORD_CHANGE_FAILED` の文言でどちらの原因も吸収する —
> 「現在のパスワードが正しくありません。Google でログインしているアカウントには、パスワードが設定されていない場合があります。」
> **恒久策の提案:** `UserResponse` に `hasPassword: boolean` を追加し、`false` のときはセクションを「Google アカウントでログインしています」の説明表示に差し替える。§17 OQ-P2 参照。

### 8.4 退会（`WithdrawSection` / `WithdrawDialog`）

ページ最下部・`tone="danger"`（唯一の枠付きブロック）。**セクション内には説明とボタンのみ**を置き、実行は `AlertDialog` に隔離する。

```
┌ 退会 ─────────────────────────────────────────────┐
│ 退会                    アカウントを閉じると:        │
│ アカウントを閉じます。   ・ログインできなくなります    │
│ この操作は取り消せません。・注文履歴を参照できなくなります│
│                        ・登録した配送先住所が使えなくなります│
│                                                    │
│                                  [ 退会する ]       │
└────────────────────────────────────────────────────┘
```

```tsx
// 確認ダイアログ — AlertDialog（@base-ui ベースの shadcn 実装）
<AlertDialog>
  <AlertDialogTrigger render={<Button variant="outline" className="border-destructive/50 text-destructive hover:bg-destructive/10 h-11" />}>
    退会する
  </AlertDialogTrigger>
  <AlertDialogContent>
    <AlertDialogHeader>
      <AlertDialogTitle className="font-serif">本当に退会しますか？</AlertDialogTitle>
      <AlertDialogDescription>
        {user.displayName}（{user.email}）のアカウントを閉じます。この操作は取り消せません。
      </AlertDialogDescription>
    </AlertDialogHeader>

    {/* 二段階目: 同意チェック。チェックするまで実行ボタンは非活性 */}
    <label className="flex items-start gap-2.5 text-sm">
      <input type="checkbox" checked={agreed} onChange={...} className="accent-destructive mt-0.5 size-4" />
      <span>上記の内容を理解し、退会します</span>
    </label>

    {error && <FormAlert>{error}</FormAlert>}

    <AlertDialogFooter>
      <AlertDialogCancel>キャンセル</AlertDialogCancel>
      <AlertDialogAction
        disabled={!agreed || isPending}
        onClick={handleWithdraw}
        className="bg-destructive text-destructive-foreground hover:bg-destructive/90 h-11"
      >
        {isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
        退会する
      </AlertDialogAction>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```

**仕様:**

- **二段階**: ①セクションの「退会する」→ ダイアログ、②ダイアログ内の同意チェック → 実行ボタン活性。
  - 「`退会` と入力してください」型のタイプ確認は**採らない**。B2B の組織削除では妥当だが、消費者アカウントには過剰で、日本語入力（IME）を挟むため失敗率も上がる。同意チェック＋影響の明示で十分な摩擦になる。
- ダイアログ本文に**メールアドレスをエコー**する（どのアカウントを消すのかの最終確認）。
- 破壊ボタンは `bg-destructive`。**ダイアログ内でこれが唯一の塗りボタン**（キャンセルは `outline` / `ghost`）。
- 成功時（204）: `clearAuth()` → `queryClient.clear()` → `router.replace('/')`（`FRONTEND_API_CONTRACT §6.2`）。
  - `queryClient.clear()` を忘れると、退会後にトップへ戻った際に前ユーザーのキャッシュ（カート件数等）が残る。
  - トップ遷移後の告知は Phase 2 では**行わない**（トースト基盤が無いため。`/?withdrawn=1` 等のクエリ告知は Phase 3 の検討事項）。
- 失敗時: ダイアログを閉じず、内部の `FormAlert` にエラーを出す。

### 8.5 エラー UI 対応表（`/profile/settings`）

| API | エラーコード | 表示位置 | 文言 |
|---|---|---|---|
| `PATCH /users/me` | `VALIDATION_FAILED`(422) | 各フィールド直下 | `VALIDATION_RULES §2.1` の文言（サーバー文言をそのまま出さず zod で先に弾く） |
| `PATCH /users/me/password` | `PASSWORD_CHANGE_FAILED`(400) | セクション上部 `FormAlert` | 「現在のパスワードが正しくありません。Google でログインしているアカウントには、パスワードが設定されていない場合があります。」 |
| `PATCH /users/me/password` | `VALIDATION_FAILED`(422) | フィールド直下 | 「パスワードは8文字以上で入力してください」 |
| `DELETE /users/me` | 任意の 4xx/5xx | ダイアログ内 `FormAlert` | 「退会処理に失敗しました。時間をおいて再度お試しください。」 |
| 全 API | `401`（トークン失効） | — | BFF の single-flight refresh に委ねる。復帰不能なら `/auth/login?redirect=/profile/settings` |
| 全 API | ネットワーク断 | セクション上部 `FormAlert` | 「通信に失敗しました。接続を確認して再度お試しください。」 |

---

## 9. `/profile/addresses` — 配送先住所管理（Phase 3 / OQ-2）

> **フェーズの扱い:** `FRONTEND_IA.md §1.2b` では Phase 3。ただしバックエンド API は本スライス（U-06〜U-12）で完成済みで、`user-profile.md` OQ-2 は「最小 FE まで本スライスで作り E2E を通す」を提案している。**本仕様は設計を先行確定させる**ので、OQ-2 がどちらに転んでも実装時に設計待ちが発生しない。装飾的 UX（郵便番号からの住所自動補完等）は Phase 3 に残す（§17 OQ-P4）。

### 9.1 一覧（`AddressList`）

```
配送先住所                                  [ + 住所を追加 ]
──────────────────────────────────────────────────────────

┌─────────────────────────────┐ ┌─────────────────────────────┐
│▎山田 太郎        [デフォルト] │ │ 山田 花子                    │
│ 〒150-0002                   │ │ 〒530-0001                   │
│ 東京都渋谷区渋谷1-2-3         │ │ 大阪府大阪市北区梅田3-1-1     │
│ Kivio ビル 4F                │ │ グランフロント 12F            │
│ 090-1234-5678                │ │ 06-1234-5678                 │
│ ───────────────────────────  │ │ ───────────────────────────  │
│ [編集]              [削除]    │ │ [編集] [デフォルトに] [削除]  │
└─────────────────────────────┘ └─────────────────────────────┘
   ▎= border-l-2 border-accent
```

| 項目 | 指定 |
|---|---|
| グリッド | `grid gap-4 sm:grid-cols-2`（コンテンツ列 `max-w-3xl` に 2 列がちょうど収まる） |
| カード | `rounded-lg border border-border p-5`。**影なし**（`shadow-card` は商品カード＝クリック可能要素の合図。住所カードは面ごとのクリック対象ではない） |
| デフォルト | `border-l-2 border-l-accent` ＋ ティールの `Badge`「デフォルト」。**色＋バッジ文言の二重表現** |
| 並び順 | **デフォルト住所を先頭 → `created_at` 昇順**（`VALIDATION_RULES §5` 確定事項 3）。サーバー順をそのまま描画する |
| 住所の組版 | 宛名（`font-medium`）→ 〒郵便番号 → 都道府県＋市区町村 → 番地・建物 → 電話。`text-sm`、行間 `leading-relaxed`。**`line-clamp` を使わない**（日本語住所の切り詰めは事故のもと・`MASTER.md §14`） |
| アクション | カード下端に罫線区切りで `ghost` サイズ小のボタン列。デフォルト住所には「デフォルトにする」を出さない |
| 追加ボタン | ページ見出し行の右。`bg-accent`（このページの主 CTA）。モバイルは見出し下に `w-full` |
| 上限 | 住所件数の上限は API 側に無い。UI でも制限しないが、**11 件目以降も同じグリッドで積む**（ページングしない） |

### 9.2 追加・編集フォーム（`AddressFormSheet`）

追加・編集は**同一の Sheet** を `mode`（`create` / `edit`）で切り替える（フィールド構成が同一のため、別コンポーネントに割らない）。

| ブレークポイント | `side` | 幅・高さ |
|---|---|---|
| `sm` 以上 | `right` | `sm:max-w-sm`（shadcn 既定）→ **`sm:max-w-md`(448px) に上書き**。6 フィールドが窮屈にならない幅 |
| `< sm` | `bottom` | `h-auto max-h-[85dvh]` ＋ 内部スクロール。`100vh` は使わない（`MASTER.md §14`） |

```
┌ 配送先を追加 ────────────────── ✕ ┐
│ 宛名                              │
│ [                              ] │
│ 郵便番号                          │
│ [150-0002        ]  ← w-40        │
│ 都道府県                          │
│ [東京都              ▾]  ← Select │
│ 市区町村                          │
│ [                              ] │
│ 番地・建物名                       │
│ [                              ] │
│ 電話番号                          │
│ [090-1234-5678                 ] │
│                                  │
│ ☑ この住所をデフォルトに設定する    │
│   チェックすると、現在のデフォルト   │
│   住所は解除されます               │
│ ─────────────────────────────── │
│ [ キャンセル ]      [   保存する ] │  ← SheetFooter（sticky bottom）
└──────────────────────────────────┘
```

**仕様:**

- フィールド順は**記入の物理的順序**（宛名 → 〒 → 都道府県 → 市区町村 → 番地 → 電話）。`VALIDATION_RULES §5` の並びと一致。
- `postalCode` は `inputMode="numeric"`・`w-40`（値が短いのに全幅にすると入力量を過大に見せる）。他は全幅。
- `prefecture` は自由入力ではなく **`Select`（47 都道府県）**。`src/lib/constants/prefectures.ts` に定数を新設。20 文字制約は Select により自動的に満たされ、表記ゆれ（「東京」/「東京都」）も防げる。
- `phoneNumber` は `inputMode="tel"`・`autoComplete="tel"`。
- `autoComplete`: 宛名 `name` / 郵便番号 `postal-code` / 都道府県 `address-level1` / 市区町村 `address-level2` / 番地 `address-line1`。ブラウザの住所オートフィルを効かせる。
- **`isDefault` チェックボックスには副作用の説明を添える**（「現在のデフォルト住所は解除されます」）。サーバーが他住所を `false` に落とす挙動（`DB_DESIGN §3.10`）がユーザーから見えないため。
  - 編集時、対象が既にデフォルトの場合はチェックボックスを**チェック済み・`disabled`** にし、「デフォルト住所です。他の住所をデフォルトに設定すると解除されます」と表示（自分自身のデフォルト解除は API に手段が無い）。
- `SheetFooter` はシート下端に固定（`sticky bottom-0 bg-popover border-t`）。長いフォームでも保存ボタンが常に見える。
- 送信中はシートを閉じない。成功したら閉じる。
- `PATCH` は**ダーティなフィールドのみ**送信（§7.5）。

### 9.3 削除確認（`DeleteAddressDialog`）

`AlertDialog`。退会と違い**同意チェックは付けない**（住所は再登録可能＝復旧コストが低い。摩擦は影響度に比例させる）。

- タイトル: 「この配送先を削除しますか？」
- 本文: 宛名 ＋ 1 行に畳んだ住所をエコー。デフォルト住所の場合は追記 —「デフォルトに設定されている住所です。削除後、デフォルトの配送先はなくなります。」
- 実行: `bg-destructive`。成功で `queryKeys.user.addresses` を invalidate。

### 9.4 空状態（`AddressEmptyState`）

`MASTER.md §10` エンプティステート規約（メッセージ＋行動促進）に準拠。

```tsx
<div className="border-border flex flex-col items-center gap-4 rounded-lg border border-dashed py-16 text-center">
  <MapPin className="text-muted-foreground h-12 w-12" aria-hidden />
  <div className="space-y-1">
    <p className="text-foreground font-medium">配送先住所がまだ登録されていません</p>
    <p className="text-muted-foreground text-sm">登録しておくと、購入時の入力を省けます。</p>
  </div>
  <Button onClick={openCreateSheet} className="bg-accent text-accent-foreground hover:bg-accent/90 h-11">
    住所を追加する
  </Button>
</div>
```

破線ボーダー（`border-dashed`）は**空状態だけの記号**。実データのカードでは使わないため、一目で「まだ何もない」と分かる。

### 9.5 エラー UI 対応表（`/profile/addresses`）

| API | エラーコード | 表示位置 | 文言・挙動 |
|---|---|---|---|
| `GET /users/me/addresses` | 5xx / ネットワーク | 一覧領域 | 再試行ボタン付きのエラーブロック（`FormAlert` ＋「再読み込み」） |
| `POST` / `PATCH` | `VALIDATION_FAILED`(422) | 各フィールド直下 | `VALIDATION_RULES §5` の文言 |
| `PATCH` / `DELETE` | `RESOURCE_NOT_FOUND`(404) | シート／ダイアログ上部 | 「この住所は既に削除されています」→ 閉じて一覧を invalidate |
| `PATCH` / `DELETE` | `ACCESS_DENIED`(403) | 同上 | 「この住所を操作する権限がありません」→ 閉じて一覧を invalidate（本来到達しないが IDOR 防御の応答を握り潰さない） |

---

## 10. ローディング・スケルトン

`MASTER.md §10`（300ms 以上の非同期にはスケルトン・実物と同寸で CLS 防止）に従う。

| 対象 | 扱い |
|---|---|
| `AccountIdentityHeader` | `AuthHydrator` 完了前は同寸スケルトン（`Skeleton` 円 `size-12 sm:size-16` ＋ 2 行のバー）。**帯の高さは維持**する |
| `AccountNav` | スケルトン不要（静的リンク） |
| `/profile/settings` のフォーム | `useAuthStore.user` を初期値に使うため、**基本的にスケルトン不要**。ストア未ハイドレート時のみフィールド行のスケルトン |
| `/profile/addresses` の一覧 | `AddressListSkeleton` — カード 2 枚分（`sm:grid-cols-2`・各 `h-48 rounded-lg`）。空状態と誤認させないよう**カード形状**で出す |

`Suspense` 境界はページ単位ではなく**データ取得コンポーネント単位**（`AddressList`）に置く。設定ページ全体をサスペンドさせると、静的なセクション見出しまで消えて画面が空白になる。

---

## 11. モーション方針

`auth.md §6.4` の初回スタガー登場は **`/profile/*` では採用しない。**

理由: スタガーは「初めて訪れる画面を印象づける」演出であり、**設定画面は再訪率が高く、目的のセクションへ直行したい**画面である。訪れるたびに 300ms の立ち上がりを見せられるのは摩擦にしかならない。同じ frontend-design の指針（「高インパクトな瞬間に集約する」）に従った結果、ここでは**集約すべき瞬間がロード時ではない**と判断する。

代わりにモーションは**状態変化のフィードバック**にだけ使う:

| 対象 | 指定 |
|---|---|
| Sheet の開閉 | shadcn 既定（`duration-200 ease-in-out`・`MASTER.md §7` のモーダル 200ms に一致） |
| AlertDialog | 同上 |
| ナビ／ボタンのホバー | `transition-colors duration-150` |
| `SaveStatus` の出現 | `animate-[auth-fade_150ms_ease-out]`（`globals.css` に定義済みの `auth-fade` を再利用。新規 keyframes を足さない） |
| 保存ボタンの活性/非活性 | 色遷移 150ms。**0ms 切替は禁止**（`MASTER.md §14`） |
| アバタープレビューの差し替え | トランジションなし（画像の読み込み完了で即時。フェードは「読み込めたのか」を曖昧にする） |

`prefers-reduced-motion: reduce` では全て `motion-reduce:transition-none` / `motion-reduce:animate-none`。

---

## 12. レスポンシブまとめ

| 要素 | `< md`（モバイル） | `md 〜 lg`（タブレット） | `lg 〜`（デスクトップ） |
|---|---|---|---|
| グローバル chrome | ヘッダー簡易 ＋ `MobileBottomNav` | ヘッダー ＋ フッター | フル |
| アイデンティティヘッダー | アバター 48px・参加日非表示 | アバター 64px | アバター 64px・参加日表示 |
| `AccountNav` | 横スクロールセグメント（本文上） | 横並びセグメント（本文上） | 縦サイドナビ `w-56` `sticky top-24` |
| `SettingsSection` | 1 カラム縦積み | 1 カラム縦積み | 2 カラム（見出し 224px ／ 本体） |
| 保存ボタン | `w-full` | `w-auto` 右寄せ | `w-auto` 右寄せ |
| 住所グリッド | 1 列 | 2 列（`sm:` から） | 2 列 |
| `AddressFormSheet` | `side="bottom"`・`max-h-[85dvh]` | `side="right"` `max-w-md` | `side="right"` `max-w-md` |
| コンテンツ幅 | `px-6` 全幅 | `max-w-7xl px-6` | `max-w-7xl` ＋ 本文 `max-w-3xl` |

---

## 13. アクセシビリティ要件

- **見出し階層:** `<h1>` は `AccountIdentityHeader`（表示名）に 1 つだけ。各セクション見出しは `<h2>`、住所カードの宛名は `<h3>`。ページごとに `<h1>` を重複させない。
- **ランドマーク:** `<main id="main-content">`（`(authenticated)/layout.tsx`）／`AccountNav` は `<nav aria-label="アカウントメニュー">`／各セクションは `<section>`。スキップリンクはグローバルレイアウト側で提供。
- **現在地:** アクティブなナビ項目に `aria-current="page"`。色だけに依存しない（§4.2）。
- **フォーム:** 全入力に `<Label htmlFor>`。エラーは `aria-invalid` ＋ `aria-describedby` でメッセージに紐付け。サーバーエラーは `FormAlert`（`role="alert"` / `aria-live="polite"`）。
- **保存結果:** `SaveStatus` は `aria-live="polite"`。ボタンの `disabled` 切替だけで成功を伝えない（スクリーンリーダーに何も届かない）。
- **オートコンプリート:** `nickname` / `current-password` / `new-password` / `name` / `postal-code` / `address-level1` / `address-level2` / `address-line1` / `tel`。
- **パスワード表示トグル:** `aria-label`（「パスワードを表示」/「パスワードを非表示」）＋ `aria-pressed`。
- **ダイアログ:** `AlertDialog` は開いた時点で**最も安全な要素**（キャンセル／同意チェック）にフォーカスを置く。破壊ボタンに初期フォーカスを当てない。`Esc` とオーバーレイクリックで閉じられる（退会ダイアログも例外にしない——閉じても何も起きないため危険がない）。
- **Sheet:** 開いている間は背後をフォーカストラップ。閉じたら**トリガー要素へフォーカスを戻す**（`AddressCard` の「編集」ボタン）。編集元カードが削除で消えた場合は「住所を追加」ボタンへ戻す。
- **タッチターゲット:** 入力 `h-11`、ボタン `h-11`、ナビ項目 `h-11`、カード内アクションは `h-9` を許容するが**上下 `py` を含めて 44px** を確保する。
- **コントラスト:** `bg-secondary/60` の帯の上でも `text-muted-foreground`(#64748B) は 4.5:1 を満たす。デフォルト住所カードの `border-l-accent` は装飾であり、意味はバッジ文言が担う。
- **横スクロール:** `AccountNav` のモバイル横スクロール以外に水平スクロールを発生させない。長いメール・URL は `truncate` または `break-all`。

---

## 14. コンポーネント構成とファイルマッピング

```
src/
├── app/
│   └── (authenticated)/                      # ★ 新規ルートグループ（URL に影響しない）
│       ├── layout.tsx                        # ★ SC: グローバル chrome（(public) と同構成）
│       └── profile/
│           ├── layout.tsx                    # ★ SC: AccountIdentityHeader + AccountNav + 2 カラム
│           ├── settings/page.tsx             # ★ SC: 4 セクションを並べるだけ（Phase 2）
│           └── addresses/page.tsx            # ★ SC: <AddressList />（Phase 3 / OQ-2）
│
├── components/
│   ├── form/                                 # ★ 新規: auth / profile 共用のフォーム部品
│   │   ├── FieldError.tsx                    # 🔄 auth/ から移設（既存実装をそのまま）
│   │   ├── FormAlert.tsx                     # 🔄 auth/ から移設（既存実装をそのまま）
│   │   ├── PasswordInput.tsx                 # ★ CC: 通常ラベル版トグル付き（§7.4）
│   │   └── SaveStatus.tsx                    # ★ CC: インライン保存完了表示（§7.6）
│   │
│   ├── ui/
│   │   └── checkbox.tsx                      # ★ @base-ui/react/checkbox ベース（退会同意 / isDefault）
│   │
│   ├── profile/
│   │   ├── AccountIdentityHeader.tsx         # ★ CC: useAuthStore を読む（§5）
│   │   ├── AccountNav.tsx                    # ★ CC: usePathname（§4）
│   │   ├── SettingsSection.tsx               # ★ SC: 2 カラム罫線セクション（§6）
│   │   ├── ProfileSettingsForm.tsx           # ★ CC: displayName / avatarUrl（§8.1）
│   │   ├── AvatarUrlField.tsx                # ★ CC: プレビュー + URL 入力（Phase 3 で差し替える単位）
│   │   ├── AccountInfoSection.tsx            # ★ CC: 読み取り専用 dl（§8.2）
│   │   ├── PasswordSection.tsx               # ★ CC: hasPassword でフォーム / 説明を出し分け（§8.3）
│   │   ├── ChangePasswordForm.tsx            # ★ CC: パスワード変更フォーム（§8.3）
│   │   ├── WithdrawSection.tsx               # ★ CC: danger ブロック + トリガー（§8.4）
│   │   └── WithdrawDialog.tsx                # ★ CC: AlertDialog + 同意チェック（§8.4）
│   │
│   └── address/
│       ├── AddressList.tsx                   # ★ CC: 一覧 + グリッド + シート開閉状態（§9.1）
│       ├── AddressCard.tsx                   # ★ CC: 1 件表示 + アクション（§9.1）
│       ├── AddressFormSheet.tsx              # ★ CC: create / edit 兼用シート（§9.2）
│       ├── DeleteAddressDialog.tsx           # ★ CC: 削除確認（§9.3）
│       ├── AddressEmptyState.tsx             # ★ SC: 空状態（§9.4）
│       └── AddressListSkeleton.tsx           # ★ SC: スケルトン（§10）
│
├── hooks/
│   ├── useMediaQuery.ts                      # ★ Sheet の side を画面幅で出し分ける（§9.2）
│   ├── queries/
│   │   └── useAddressesQuery.ts              # ★ 住所一覧
│   └── mutations/                            # ★ invalidateQueries はすべてここに閉じる
│       ├── useUpdateProfileMutation.ts
│       ├── useChangePasswordMutation.ts
│       ├── useWithdrawAccountMutation.ts
│       ├── useUpsertAddressMutation.ts
│       ├── useSetDefaultAddressMutation.ts
│       └── useDeleteAddressMutation.ts
│
├── lib/
│   ├── api/client/
│   │   ├── users.ts                          # 🔄 updateProfile / changePassword / withdrawAccount（U-15）
│   │   └── addresses.ts                      # ★ listAddresses / createAddress / updateAddress / deleteAddress（U-15）
│   ├── apiErrors.ts                          # 🔄 authErrors.ts から改称。resolveApiError に一本化（通信断を区別）
│   ├── format.ts                             # ★ ROLE_LABEL / formatDate / formatYearMonth / formatPostalCode
│   ├── validations/
│   │   ├── profile.ts                        # ★ updateProfileSchema / changePasswordSchema（U-14）
│   │   └── address.ts                        # ★ addressSchema（U-14）
│   └── constants/
│       └── prefectures.ts                    # ★ 47 都道府県（Select 用・§9.2）
│
└── types/api/
    └── address.ts                            # ★ Address / CreateAddressRequest / UpdateAddressRequest（U-13）
```

**Client / Server Component 境界**（`FRONTEND_CODING_STANDARDS` の「`'use client'` は葉に限定」に準拠）:

| コンポーネント | SC / CC | 理由 |
|---|---|---|
| `(authenticated)/layout.tsx` / `profile/layout.tsx` | SC | 静的レイアウト |
| `settings/page.tsx` / `addresses/page.tsx` | SC | セクションを並べるだけ。状態は子の CC が持つ |
| `SettingsSection` / `AddressEmptyState` / `AddressListSkeleton` | SC | 純粋な表示 |
| `AccountIdentityHeader` / `AccountNav` | CC | Zustand / `usePathname` |
| 各フォーム・ダイアログ・シート | CC | フォーム状態 ＋ mutation |
| `PasswordInput` / `SaveStatus` | CC | ローカル状態 |

**クエリキー**（`src/lib/queryKeys.ts`・既存定義をそのまま使う）:

| 用途 | キー |
|---|---|
| プロフィール | `queryKeys.user.me` → `['user','me']` |
| 住所一覧 | `queryKeys.user.addresses` → `['user','me','addresses']` |

> ⚠️ `FRONTEND_API_CONTRACT §クエリキー` は `['users','me',…]`（複数形）と記載しているが、**実装済みの `queryKeys.ts` は `user`（単数）**。実装は `queryKeys` 定数を正とし、ドキュメント側を後で合わせる（§17 OQ-P5）。

**mutation 後の invalidate:**

| 操作 | invalidate / 副作用 |
|---|---|
| `PATCH /users/me` | `setUser()` ＋ `invalidate(queryKeys.user.me)` |
| `PATCH /users/me/password` | なし（204・キャッシュに影響しない） |
| `DELETE /users/me` | `clearAuth()` ＋ `queryClient.clear()` ＋ `router.replace('/')` |
| `POST` / `PATCH` / `DELETE` 住所 | `invalidate(queryKeys.user.addresses)` |

---

## 15. デザイントークン参照（MASTER.md §2）

| 用途 | トークン | 値 |
|---|---|---|
| アイデンティティ帯 | `bg-secondary/60` ＋ `border-y border-border` | #EEF3F9 60% |
| アバターフォールバック | `bg-primary` / `text-primary-foreground` | #1E3A5F / #FFF |
| ナビのアクティブレール | `border-l-accent`（2px） | #1A9E87 |
| ナビのアクティブ面 | `bg-secondary/70` ＋ `text-primary` | — |
| セクション罫線 | `border-t border-border` | #E2E8F0 |
| セクション見出し | `font-serif text-lg font-bold text-foreground` | — |
| セクション説明 | `text-sm text-muted-foreground` | #64748B |
| 主 CTA（保存・住所追加） | `bg-accent` / `text-accent-foreground` | #1A9E87 / #FFF |
| 保存完了 | `text-success` ＋ `Check` アイコン | #1A9E87 |
| Danger ブロック | `border-destructive/30` / `bg-destructive/3` | #DC2626 系 |
| 破壊ボタン | `bg-destructive` / `text-destructive-foreground` | #DC2626 / #FFF |
| デフォルト住所カード | `border-l-2 border-l-accent` ＋ `Badge`（accent） | #1A9E87 |
| 空状態の枠 | `border-dashed border-border` | #E2E8F0 |
| 入力高さ | `h-11` | 44px（タッチターゲット） |
| カード角丸 | `rounded-lg` | 12px |

**新規 HEX の持ち込みは無し**（`MASTER.md §14`）。不透明度サフィックス（`/60` `/30` `/3`）のみで濃淡を作る。

---

## 16. 実装優先度（`user-profile.md` タスクとの対応）

| 優先度 | 項目 | 対応タスク | 状態 |
|---|---|---|---|
| P0 | `types/api/address.ts` | U-13 | ✅ |
| P0 | zod スキーマ（`profile.ts` / `address.ts`）＋ `prefectures.ts` | U-14 | ✅ |
| P0 | API クライアント（`users.ts` 拡張 / `addresses.ts`） | U-15 | ✅ |
| P0 | `(authenticated)/layout.tsx` ＋ `profile/layout.tsx`（§3.5） | U-16 | ✅ |
| P0 | `AccountIdentityHeader` / `AccountNav` / `SettingsSection`（§4〜6） | U-16 | ✅ |
| P0 | `form/` への `FieldError` / `FormAlert` 移設 ＋ `PasswordInput` / `SaveStatus`（§7） | U-16 | ✅ |
| P0 | `ProfileSettingsForm` / `AccountInfoSection` / `PasswordSection`（§8.1〜8.3） | U-16 | ✅ |
| P0 | `WithdrawSection` / `WithdrawDialog`（§8.4） | U-16 | ✅ |
| P1 | `AddressList` / `AddressCard` / `AddressFormSheet` / `DeleteAddressDialog`（§9） | U-17 | ✅（OQ-P6 で本スライスに含めると決定） |
| P1 | 空状態・スケルトン（§9.4 / §10） | U-16, U-17 | ✅ |
| P1 | コンポーネントテスト（RTL + MSW） | U-18 | ✅ 27 件追加（profile 15 / address 12） |
| P2 | アバター画像アップロード（Cloudinary） | Phase 3（範囲外） | ⬜ |
| P2 | 郵便番号 → 住所自動補完 | Phase 3（OQ-P4） | ⬜ |
| P2 | トースト基盤（Sonner）導入と `SaveStatus` の置換検討 | Phase 3（OQ-P3） | ⬜ 見送り |

---

## 17. Open Questions（2026-08-01 全件クローズ・実装済み）

| # | 論点 | 決定 | 反映先 |
|---|---|---|---|
| **OQ-P1** | **`avatarUrl` のクリア手段** — 入力を空にしたとき「未送信（不変）」か「クリア」か | **空文字＝クリア**を採用。`null`（未送信）とクリアを JSON で区別できないため、クリアの意思表示を空文字に割り当てる。`@URL` は空文字を有効と扱うのでバリデーションは通過し、`User#updateProfile` が `null` 化する | BE: `User#updateProfile` / `UpdateProfileRequest` javadoc / `UserServiceTest` / `UserControllerTest`<br>FE: `updateProfileSchema`（`z.union([z.literal(''), z.url()])`）/ `AvatarUrlField` の削除ボタン<br>DOC: `API_DESIGN.md PATCH /users/me` / `VALIDATION_RULES.md §2.1` |
| **OQ-P2** | **Google 専用ユーザーのパスワードセクション** | 恒久策を採用。**`UserResponse.hasPassword: boolean` を追加**し、`false` のときは変更フォームを出さず「Google でログインしています」の説明表示に差し替える（暫定のエラー文言吸収は不採用） | BE: `UserResponse` / `UserServiceTest` / `UserControllerTest`<br>FE: `AuthUser.hasPassword` / `PasswordSection`<br>DOC: `API_DESIGN.md GET /users/me` |
| **OQ-P3** | **トースト基盤** | 導入しない。インライン `SaveStatus`（§7.6）で完結させる（新規依存なし・操作地点で結果を返せる） | FE: `components/form/SaveStatus.tsx` |
| **OQ-P4** | **郵便番号 → 住所自動補完** | Phase 2/3 では**やらない**。外部 API 依存・オフライン時の挙動・レート制限の検討が別途必要 | — |
| **OQ-P5** | **クエリキーの表記ゆれ** | 実装（`queryKeys.user`・単数）を正とし、ドキュメント側を修正した | DOC: `FRONTEND_API_CONTRACT.md §5` |
| **OQ-P6** | **`/profile/addresses` のフェーズ**（既存 OQ-2） | **本スライスで実装する**。バックエンド API が完成済みで、設計も本書で確定しているため後続に残す理由がない。Phase 3 では郵便番号補完などの装飾的 UX のみ扱う | FE: `components/address/*` / `app/(authenticated)/profile/addresses/page.tsx` |

### 実装時に生じた設計上の補足（本書に反映済み）

| 項目 | 内容 |
|---|---|
| Sheet の `side` 切り替え | `data-[side=bottom]:*` の詳細度が `sm:*` 修飾子より高く、CSS だけでは上書きできない。`useMediaQuery('(min-width: 640px)')` で **prop 自体を出し分ける**（§9.2）。SSR 時は `false`（= bottom）を返すが、シートは初期状態で閉じているため初期描画に影響しない |
| Checkbox | `@base-ui/react/checkbox` を `components/ui/checkbox.tsx` として追加。`span[role=checkbox]` でレンダリングされるため、無効状態は `disabled:` ではなく **`data-disabled:`** で拾う（テストの検証も `aria-disabled`） |
| `watch()` の不使用 | React Compiler が RHF の `watch()` を含むコンポーネントのメモ化をスキップするため、アバタープレビューは **`useWatch`** で購読する |
| `FieldError` / `FormAlert` の移設 | `components/auth/` → `components/form/` へ移動し auth / profile で共用（§14）。併せて `lib/authErrors.ts` → `lib/apiErrors.ts` に改称し、通信断を区別する `resolveApiError` を追加（§7.7・§8.5） |

---

## 18. frontend-design レビュー反映ログ（2026-08-01）

`auth.md §17` と同じ基準でレビューした。**確立済みアイデンティティ（Navy × Teal / Noto Serif・Sans JP / ライト固定 / HEX 直書き禁止 / 商品を主役）を侵さない範囲**でのみ、スキルの指針を採用している。

### 採用した点

| frontend-design の指針 | 本仕様への反映 | 合致理由 |
|---|---|---|
| **Spatial composition — 予期しないレイアウト・非対称** | セクションを「カードの積み重ね」ではなく**見出し列 224px ＋ 本体の非対称 2 カラム罫線構成**にした（§6）。サイドナビ幅と見出し列幅を揃え、ページ全体を貫く 224px のリズムを作った | 箱を減らして組版で階層を作る＝`MASTER.md §1`「UI chrome を極限まで抑える」の設定画面版。装飾を足さずに個性が出る |
| **Typography — 見出しに性格を持たせる** | セクション見出しを `font-serif`（Noto Serif JP Bold）で通した。設定画面で Serif 見出しを使う EC は少なく、**罫線だけの画面に唯一の"声"**を与える | フォントは既定のまま、使い分けの範囲でブランドを効かせる |
| **Backgrounds — 単色ベタを避ける** | 塗り面をアイデンティティ帯 1 箇所に限定し、`bg-secondary/60` ＋ 上下ヘアラインで**面ではなく"帯"**として扱った | 商品面ではないので許容。ただし `auth.md §5.2` のようなグロー／グレインは持ち込まない（設定画面に雰囲気は不要） |
| **Motion — 高インパクトな瞬間に集約** | **初回スタガーを採用しない**判断（§11）。集約すべき瞬間はロードではなく「保存できた」瞬間だと定義し、`SaveStatus` にモーションを割り当てた | 指針の字面ではなく趣旨に従った。再訪頻度の高い画面での入場演出は摩擦 |
| **意図的なコントラスト（危険の可視化）** | 退会ブロックだけを枠付き・淡い destructive 面にし、**ページ内で唯一"箱"にした**（§8.4） | 形状の差そのものが警告になる。色だけに頼らない |

### あえて見送った点

| 指針 | 不採用の理由 |
|---|---|
| 独自ディスプレイフォント／マキシマリズム | `MASTER.md §1` でフォント確定。設定画面は「読み替えの速さ」が価値で、装飾は誤操作リスクを上げる |
| アバターを主役にした大型ヒーロー（カバー画像・グラデーション） | アップロード未実装（URL 入力のみ）で、実データはほぼ空。**実装できない機能を期待させる UI にしない**（§8.1） |
| 各フィールドのマイクロインタラクション多用 | 入力の妨げ。フィードバックは保存結果に集約（§11） |
| フローティングラベルの全画面統一 | 初期値ありの編集フォームでは利点が消え、縦幅と走査コストだけが残る（§7.1）。**一貫性は画面クラス単位で担保する** |
| タイプ確認式の退会（「退会」と入力） | 消費者アカウントには過剰。IME を挟むため失敗率も上がる。同意チェック＋影響明示で十分（§8.4） |

**結論:** `auth.md` が選んだ *refined minimalism* を踏襲しつつ、その表現手段を「フォーム 1 本への集中」から**「罫線と組版による走査性」**へ置き換えた。両画面は同じ語彙（Navy × Teal・Serif 見出し・ティールの CTA・44px タッチターゲット）を共有しながら、目的の違いが構造の違いとして現れる。
