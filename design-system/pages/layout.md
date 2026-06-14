# グローバルレイアウト設計仕様
# GlobalHeader / GlobalFooter / MobileBottomNav

**対象コンポーネント:** `GlobalHeader`, `GlobalFooter`, `MobileBottomNav`  
**スコープ:** 全公開画面（`/` `(public)/*` `(authenticated)/*`）共通レイアウト  
**MASTER.md との関係:** このファイルのルールが MASTER.md を上書きする（§16 準拠）  
**作成日:** 2026-06-09

---

## 1. ダークモード方針

### 1.1 基本方針

| 対象 | モード | 実装方法 |
|---|---|---|
| 全公開画面・バイヤー画面 | **ライトのみ** | `<html>` に `dark` クラスなし |
| Seller Dashboard (`/seller/*`) | **ライトのみ（Phase 2）** | Phase 3+ での拡張時に `className="dark"` を追加可能 |
| Admin Dashboard (`/admin/*`) | **ライトのみ（Phase 2）** | Phase 3+ での拡張時に `className="dark"` を追加可能 |

### 1.2 拡張メカニズム

**Phase 3+ 時の実装構造（今は準備のみ）:**

`globals.css` の `@custom-variant dark (&:is(.dark *))` により、`.dark` クラスを持つ祖先要素の子孫にダークテーマを適用できる構造を備える。現在は使用しないが、将来的な拡張の余地を保つ。

```tsx
// src/app/(seller)/layout.tsx — 例（Phase 3+ で有効化予定）
export default function SellerLayout({ children }: { children: React.ReactNode }) {
  // Phase 2: className="dark" コメントアウト / Phase 3: 有効化
  return (
    <div className="min-h-screen bg-background text-foreground"> {/* Phase 2 */}
      {/* <div className="dark min-h-screen bg-background text-foreground"> */} {/* Phase 3+ */}
      <SellerSidebar />
      <main>{children}</main>
    </div>
  )
}
```

### 1.3 ライトモード固定の根拠

Amazon・Mercari・Alibaba はいずれもライトモード専用。理由は商品画像が「色」であるため、ダーク背景は商品の色温度・明度を歪め、購買判断に悪影響を与えるため。

**Phase 2 実装スコープ：** ダークモード無効。全画面ライトのみ。

---

## 2. ロゴ仕様

### 2.1 アセット

| 用途 | ファイル | 形式 |
|---|---|---|
| 全用途共通 | `public/images/kivio-logo.svg` | SVG（解像度非依存・透過・Retina 対応） |

**注意:** `public/images/` ディレクトリ未作成の場合、`mkdir -p public/images` して SVG を配置すること。  
`next/image` で SVG を使う場合、`next.config.ts` に `images: { dangerouslyAllowSVG: true }` が必要（後述）。

### 2.2 ロゴコンポーネント構成

```tsx
// ロゴ = アイコン画像（36px）+ "Kivio" テキスト（Noto Serif JP Bold）
<Link href="/" className="flex items-center gap-2 shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md">
  <Image
    src="/images/kivio-logo.svg"
    alt=""               // アイコンは装飾 → alt="" で隣のテキストが読み上げられる
    width={36}
    height={36}
    priority             // LCP 要素のため priority 必須
  />
  <span className="font-serif text-xl font-bold text-primary leading-none">
    Kivio
  </span>
</Link>
```

**next.config.ts への追記（SVG 使用に必要）:**

```ts
// next.config.ts
const nextConfig = {
  images: {
    dangerouslyAllowSVG: true,
    contentSecurityPolicy: "default-src 'self'; script-src 'none'; sandbox;",
  },
};
```

### 2.3 サイズ規定

| ブレークポイント | アイコン | テキスト | 理由 |
|---|---|---|---|
| モバイル（< md） | 28×28px | text-lg | コンパクトヘッダー |
| タブレット（md〜） | 32×32px | text-xl | 標準 |
| デスクトップ（lg〜） | 36×36px | text-xl | 標準 |

### 2.4 Admin 向けロゴバリアント

```tsx
// Admin ヘッダーではロゴ横に "Admin" バッジを追加
<Link href="/admin/dashboard" className="flex items-center gap-2">
  <Image src="/images/kivio-logo.svg" alt="" width={32} height={32} />
  <span className="font-serif text-lg font-bold text-primary">Kivio</span>
  <Badge className="bg-primary/10 text-primary text-xs">Admin</Badge>
</Link>
```

---

## 3. ヘッダー設計仕様

### 3.1 レイヤー構造

```
  AnnouncementBar（任意・キャンペーン告知）h-9 = 36px      ← スクロールで消える（sticky 外）
┌─────────────────────────────────────────────────────────┐  sticky top-0 z-20
│  HeaderMain（ロゴ + 検索バー + 認証アクション）h-16 = 64px│
├─────────────────────────────────────────────────────────┤
│  CategoryNav（カテゴリタブ）h-10 = 40px / lg以上のみ表示  │  ← sticky header 内に含める
└─────────────────────────────────────────────────────────┘
```

**sticky 対象:** `<header>` 要素全体（HeaderMain + CategoryNav）。AnnouncementBar は sticky `<header>` の**外側**に置きスクロールで消える。  
**z-index:** `z-20`（MASTER.md §8 準拠）。  
**desktop 時の sticky 高さ:** HeaderMain 64px + CategoryNav 40px = **104px**。

### 3.2 AnnouncementBar

```tsx
// 条件: isVisible prop（Phase 2 では常に非表示で可）
<div className="bg-primary text-primary-foreground text-sm text-center py-2 px-4 relative">
  <span>🎉 Kivio オープン記念：全商品送料無料キャンペーン実施中</span>
  <button
    className="absolute right-4 top-1/2 -translate-y-1/2 text-primary-foreground/70 hover:text-primary-foreground"
    aria-label="お知らせを閉じる"
  >
    <X className="w-4 h-4" />
  </button>
</div>
```

### 3.3 HeaderMain — デスクトップ（lg: 1280px）

```
┌──────────────────────────────────────────────────────────────────┐
│  px-6 max-w-7xl mx-auto  h-16                              │
│  [Logo + "Kivio"]  [─── SearchBar (max-w-140 / 560px) ───]  [Actions]│
└──────────────────────────────────────────────────────────────────┘
```

**GlobalHeader/index.tsx 全体構造（"use client" 必須）:**

`useScrolled` フックを使うため GlobalHeader は Client Component にする。

```tsx
// src/components/layout/GlobalHeader/index.tsx
"use client";
import { useScrolled } from "@/hooks/useScrolled";

export function GlobalHeader() {
  const scrolled = useScrolled();

  return (
    <>
      {/* AnnouncementBar: sticky 外 → スクロールで消える */}
      <AnnouncementBar />

      {/* sticky header: HeaderMain + CategoryNav を一体で固定 */}
      <header
        data-scrolled={scrolled}
        className="sticky top-0 z-20 bg-background transition-shadow duration-150 data-[scrolled=true]:shadow-[0_2px_12px_rgba(30,58,95,0.08)]"
      >
        {/* HeaderMain 行 */}
        <div className="border-b border-border lg:border-b-0">
          {/* デスクトップ / タブレット上段 */}
          <div className="max-w-7xl mx-auto px-6 h-16 hidden md:grid grid-cols-[auto_1fr_auto] items-center gap-6">
            <Logo />
            <SearchBar className="hidden lg:flex" />   {/* lg+ のみ中央に表示 */}
            <HeaderActions />
          </div>
          {/* タブレット: 検索バー第2行（md〜lg 未満） */}
          <div className="hidden md:block lg:hidden px-4 pb-2">
            <SearchBar className="w-full" />
          </div>
          {/* モバイル */}
          <div className="flex md:hidden items-center justify-between px-4 h-14">
            <MobileMenuButton />
            <Logo className="absolute left-1/2 -translate-x-1/2" />
            <MobileSearchButton />
          </div>
        </div>

        {/* CategoryNav: lg 以上のみ表示、border-b で sticky header 下端を区切る */}
        <CategoryNav />
      </header>
    </>
  );
}
```

`data-[scrolled=true]:shadow-[...]` はスクロール量を JS で検知して `data-scrolled` 属性を切り替えることで適用する（§7 参照）。

### 3.4 HeaderMain — タブレット（md: 768px）

```
┌───────────────────────────────────────────────────────┐
│  px-4  h-14                                           │
│  [Logo]               [🌐] [ログイン] [会員登録]       │
├───────────────────────────────────────────────────────┤
│  px-4 pb-2                                            │
│  [────────── SearchBar (full-width) ──────────]       │
└───────────────────────────────────────────────────────┘
```

タブレットでは検索バーをヘッダー第2行に配置してメインナビの視認性を確保する。

### 3.5 HeaderMain — モバイル（< md: 375px）

```
┌──────────────────────────────────────────────────────┐
│  px-4  h-14                                          │
│  [☰]        [Logo + "Kivio"]        [🔍]             │
└──────────────────────────────────────────────────────┘
```

- 左端: ハンバーガーアイコン → `MobileMenuSheet`（Sheet コンポーネント）を開く
- 中央: ロゴ（絶対中央配置 `absolute left-1/2 -translate-x-1/2`）
- 右端: 検索アイコン → 検索オーバーレイを表示（クリックで SearchOverlay を `z-50` に展開）
- 認証ボタン（ログイン/会員登録）はモバイルヘッダーには**表示しない** → BottomNav の「マイページ」タブで代替

---

## 4. 認証状態別ヘッダーアクション仕様

> **認証状態の解決方法（実装メモ）:** `HeaderActions` は Client Component で `useAuthStore`（Zustand）の `isAuthenticated` / `user.role` を読み、Guest / BUYER / SELLER を出し分ける。`user` はログイン・登録完了後に `GET /api/v1/users/me` で取得して store に保持する。Zustand persist の復元はクライアントのみで起きるため、初回 SSR / ハイドレーション時は Guest を描画し、マウント後に実状態へ切り替える（hydration mismatch 回避）。`role` の値はバックエンド enum と同形（`ROLE_BUYER` / `ROLE_SELLER` / `ROLE_ADMIN`）。`ROLE_ADMIN` は専用 `AdminHeader` を使うため、グローバルヘッダーでは BUYER 表示で足りる。地球アイコンは 3 状態共通のため `LanguageButton` に切り出す。
>
> **レイアウト指針（共通）:** Alibaba 等の EC ヘッダーに倣い、アイコンが密集しないようゾーン分けする。①アイコンボタンは `size="icon-lg"`（36px、グリフ `size-5`）でタップ領域に余白を持たせる、②行間は `gap-1.5`（6px）、③**ユーティリティ群（言語・カート・メッセージ・通知）とアカウント群（アバター／セラーはダッシュボード+アバター・Guest は認証 CTA）の境界に縦罫線 `<span aria-hidden className="bg-border mx-1 h-6 w-px" />` を入れる**。これにより「操作系」と「アカウント/CTA 系」が視覚的に分離され、密集感を解消する。

### 4.1 未認証（Guest）

地球アイコンの右にカートアイコンを追加する（未ログインでも閲覧導線を見せる）。ユーティリティ群（言語・カート）と認証 CTA の間を縦罫線で分ける。

```tsx
<div className="flex items-center gap-1.5">
  {/* ユーティリティ群: 言語（準備中）・カート */}
  <LanguageButton />
  <IconButton href="/cart" icon={ShoppingCart} label="カート" />

  {/* ゾーン区切り */}
  <span aria-hidden className="bg-border mx-1 h-6 w-px" />

  {/* ログインボタン: ghost */}
  <Button variant="ghost" size="sm" asChild className="font-medium">
    <Link href="/auth/login">ログイン</Link>
  </Button>

  {/* 会員登録ボタン: ティール CTA */}
  <Button size="sm" asChild className="bg-accent hover:bg-accent/90 text-accent-foreground font-medium">
    <Link href="/auth/register">会員登録</Link>
  </Button>
</div>
```

### 4.2 BUYER（認証済み）

アイコン行は **地球 → カート → メッセージ → 通知 →〔縦罫線〕→ アバター** の順。ウィッシュリスト（お気に入り）はアイコン行から外し、UserMenu ドロップダウン内の項目へ移設する。ユーティリティ群とアバターの間に縦罫線を入れて密集を避ける。

```tsx
<div className="flex items-center gap-1.5">
  {/* ユーティリティ群 */}
  <LanguageButton />
  <IconButton href="/cart" icon={ShoppingCart} badge={cartCount} label="カート" />
  <IconButton href="/messages" icon={MessageCircleMore} badge={unreadMessages} label="メッセージ" />
  <NotificationBell unreadCount={unreadNotifications} />

  {/* ゾーン区切り */}
  <span aria-hidden className="bg-border mx-1 h-6 w-px" />

  {/* アカウント群: アバターメニュー */}
  <UserMenu user={user} />
</div>
```

**UserMenu ドロップダウン（BUYER）:**

`Heart`（お気に入り）はアイコン行から本ドロップダウンへ移設する。

```
┌─────────────────────┐
│  👤 山田 太郎        │  ← ユーザー名（text-sm font-medium）
│  yamada@example.com │  ← メール（text-xs text-muted-foreground）
├─────────────────────┤
│  プロフィール設定     │  → /profile/settings
│  注文履歴            │  → /orders
│  お気に入り          │  → /wishlist  ← アイコン行から移設
│  セラー申請          │  → /seller/applications/new  ← 申請未済かつ PENDING なしの場合のみ
├─────────────────────┤
│  ログアウト          │  ← text-destructive。logout() API → clearAuth() → / へ遷移
└─────────────────────┘
```

**開閉挙動:** クリック / キーボードに加えて**マウスオーバーでも開く**。Base UI の `Menu.Root` は `openOnHover` を持たないため、`open` / `onOpenChange` で制御コンポーネント化し、トリガーとパネルの `mouseenter` / `mouseleave`（閉じる側は 120ms の猶予）で開閉を制御する。`modal={false}` でパネル外の操作も許可する。新規ライブラリは導入せず既存の `dropdown-menu`（Base UI）を流用する。

### 4.3 SELLER（認証済み）

BUYER のアイコン群（地球・カート・メッセージ・通知）に加えて、セラーダッシュボードへのショートカットを追加。アイコン行の構成は 4.2 と揃える（Heart はアイコン行から除外済み）。ユーティリティ群とアカウント群（ダッシュボード + アバター）の間に縦罫線を入れる。

```tsx
<div className="flex items-center gap-1.5">
  {/* ユーティリティ群（BUYER と同じ: 地球・カート・メッセージ・通知） */}
  <LanguageButton />
  <IconButton href="/cart" icon={ShoppingCart} label="カート" />
  <IconButton href="/messages" icon={MessageCircleMore} label="メッセージ" />
  <NotificationBell />

  {/* ゾーン区切り */}
  <span aria-hidden className="bg-border mx-1 h-6 w-px" />

  {/* アカウント群: セラーダッシュボードボタン（アウトライン）+ アバターメニュー */}
  <Button variant="outline" size="sm" asChild className="border-primary text-primary hover:bg-secondary font-medium hidden xl:flex">
    <Link href="/seller/dashboard">
      <Store className="w-4 h-4 mr-1.5" />
      ダッシュボード
    </Link>
  </Button>
  <UserMenu user={user} />
</div>
```

**UserMenu ドロップダウン（SELLER）:**

セラーも購入者として買い物するため「お気に入り」を含める。セラー申請は表示しない。

```
┌─────────────────────┐
│  👤 山田 ショップ    │
│  yamada@example.com │
├─────────────────────┤
│  プロフィール設定     │  → /profile/settings
│  注文履歴（バイヤーとして） → /orders
│  お気に入り          │  → /wishlist
├─────────────────────┤
│  ログアウト          │  ← text-destructive
└─────────────────────┘
```

### 4.4 ADMIN

Admin はグローバルヘッダーを使用せず、専用の `AdminHeader` を使用。

```tsx
// src/components/layout/AdminHeader/index.tsx
// - ロゴ: "Kivio Admin" バッジ付き
// - 中央: 空白（検索なし）
// - 右: 通知ベル + AdminMenu DropdownMenu + UserMenu
// - bg-primary text-primary-foreground（ダーク背景ナビ）
```

---

## 5. 検索バー仕様

### 5.1 デスクトップ検索バー

```tsx
// UI のみ実装（Phase 2）。送信ハンドラは空関数で可。
<form className="flex items-center w-full max-w-140 rounded-full border border-border bg-background px-4 h-10 gap-2 focus-within:ring-2 focus-within:ring-ring transition-shadow duration-150">
  <Search className="w-4 h-4 text-muted-foreground shrink-0" />
  <input
    type="search"
    placeholder="キーワード、ブランド、ショップ名で探す"
    className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground min-w-0"
    aria-label="商品を検索"
  />
  <button
    type="submit"
    className="rounded-full bg-accent text-accent-foreground px-4 h-7 text-sm font-medium hover:bg-accent/90 transition-colors duration-150 shrink-0"
    aria-label="検索"
  >
    検索
  </button>
</form>
```

### 5.2 モバイル検索オーバーレイ

モバイルで検索アイコンをタップすると全画面オーバーレイが展開（Phase 2 は UI のみ）。

```tsx
// SearchOverlay: fixed inset-0 z-50 bg-background flex flex-col
// - 上部: 戻るボタン + 入力フィールド + 検索ボタン
// - 下部: 最近の検索・人気キーワード（Phase 3 実装）
// 今は入力フィールドのみで可
```

---

## 6. カテゴリサブナビ（CategoryNav）

`lg`（1024px）以上でのみ表示。**sticky `<header>` 内の最下段**に配置する（§3.1 参照）。`border-b` が sticky header の下端境界線を担う。

```tsx
<nav
  aria-label="カテゴリナビゲーション"
  className="hidden lg:block border-b border-border"
>
  <div className="max-w-7xl mx-auto px-6 h-10 flex items-center gap-1">

    {/* カテゴリドロップダウン */}
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-1.5 font-medium text-foreground">
          <LayoutGrid className="w-4 h-4" />
          カテゴリ
          <ChevronDown className="w-3 h-3" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-56">
        {/* Phase 2: ハードコードカテゴリリスト。Phase 3 で API 化 */}
        <DropdownMenuItem asChild><Link href="/search?category=fashion">ファッション</Link></DropdownMenuItem>
        <DropdownMenuItem asChild><Link href="/search?category=electronics">家電・スマホ</Link></DropdownMenuItem>
        <DropdownMenuItem asChild><Link href="/search?category=books">本・CD・DVD</Link></DropdownMenuItem>
        <DropdownMenuItem asChild><Link href="/search?category=hobbies">おもちゃ・趣味</Link></DropdownMenuItem>
        <DropdownMenuItem asChild><Link href="/search?category=sports">スポーツ・アウトドア</Link></DropdownMenuItem>
        <DropdownMenuItem asChild><Link href="/search?category=interior">インテリア・家具</Link></DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild><Link href="/search">すべてのカテゴリ</Link></DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>

    <Separator orientation="vertical" className="h-4 mx-2" />

    {/* クイックリンク（Phase 2: /#へのリンクで可） */}
    {[
      { label: "タイムセール", href: "/#sale" },
      { label: "新着", href: "/#new" },
      { label: "ランキング", href: "/#ranking" },
      { label: "特集", href: "/#feature" },
    ].map(({ label, href }) => (
      <Link
        key={label}
        href={href}
        className="text-sm text-muted-foreground hover:text-foreground px-3 py-1 rounded-md hover:bg-muted transition-colors duration-150"
      >
        {label}
      </Link>
    ))}
  </div>
</nav>
```

---

## 7. スティッキー・スクロール挙動

### 7.1 スクロール検知フック

```tsx
// src/hooks/useScrolled.ts
"use client";
import { useState, useEffect } from "react";

export function useScrolled(threshold = 10) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const handler = () => setScrolled(window.scrollY > threshold);
    window.addEventListener("scroll", handler, { passive: true });
    return () => window.removeEventListener("scroll", handler);
  }, [threshold]);
  return scrolled;
}
```

### 7.2 ヘッダーへの適用

GlobalHeader/index.tsx の `data-scrolled={scrolled}` 属性切り替えで shadow を制御する（実装は §3.3 参照）。

```tsx
// shadow 値は MASTER.md §5 の --shadow-card（0 2px 8px rgba(30,58,95,0.08)）をわずかに強調
"data-[scrolled=true]:shadow-[0_2px_12px_rgba(30,58,95,0.08)]"
```

`transition-shadow duration-150` で 150ms の滑らかな shadow 登場アニメーションを付与する（MASTER.md §7 のマイクロインタラクション規則準拠）。

---

## 8. モバイルボトムナビ（MobileBottomNav）

`md`（768px）未満でのみ表示する Fixed ボトムナビ。`z-20`。

### 8.1 ナビ構成（未認証）

| タブ | アイコン | ラベル | リンク |
|---|---|---|---|
| ホーム | `Home` | ホーム | `/` |
| 検索 | `Search` | 検索 | `/search`（UI のみ） |
| 出品 | `PlusCircle`（FAB） | 出品 | `/auth/login`（未認証時はログイン誘導） |
| お知らせ | `Bell` | お知らせ | `/auth/login`（未認証時） |
| マイページ | `User` | マイページ | `/auth/login` または `/profile/settings`（認証時） |

### 8.2 ナビ構成（認証済み BUYER/SELLER）

| タブ | バッジ |
|---|---|
| ホーム | なし |
| 検索 | なし |
| 出品（FAB） | なし（BUYER: `/seller/applications/new` / SELLER: `/seller/products/new`） |
| お知らせ | 未読数 |
| マイページ | なし |

### 8.3 実装スケルトン

```tsx
// src/components/layout/MobileBottomNav/index.tsx
<nav
  aria-label="モバイルナビゲーション"
  className="fixed bottom-0 inset-x-0 z-20 bg-background border-t border-border flex items-center md:hidden"
  style={{ paddingBottom: "env(safe-area-inset-bottom)" }}  // iOS ホームバー対応
>
  <div className="flex w-full">
    {items.map((item) =>
      item.isFab ? (
        // 出品ボタン: FAB スタイル
        <Link
          key={item.href}
          href={item.href}
          className="flex-1 flex flex-col items-center justify-center py-2 gap-0.5 relative"
          aria-label={item.label}
        >
          <div className="w-12 h-12 rounded-full bg-accent flex items-center justify-center shadow-lg -mt-5">
            <item.icon className="w-6 h-6 text-accent-foreground" />
          </div>
          <span className="text-xs text-muted-foreground mt-1">{item.label}</span>
        </Link>
      ) : (
        <Link
          key={item.href}
          href={item.href}
          className="flex-1 flex flex-col items-center justify-center py-3 gap-0.5 text-muted-foreground aria-[current=page]:text-accent transition-colors duration-150"
          aria-label={item.label}
        >
          <div className="relative">
            <item.icon className="w-6 h-6" />
            {item.badge > 0 && (
              <span className="absolute -top-1.5 -right-1.5 min-w-4.5 h-4.5 rounded-full bg-destructive text-destructive-foreground text-[10px] font-bold flex items-center justify-center px-1">
                {item.badge > 99 ? "99+" : item.badge}
              </span>
            )}
          </div>
          <span className="text-xs">{item.label}</span>
        </Link>
      )
    )}
  </div>
</nav>
```

### 8.4 BottomNav 分のコンテンツ余白

`(public)/layout.tsx` と `(authenticated)/layout.tsx` の `<main>` に `pb-16 md:pb-0` を追加して、固定ナビの裏にコンテンツが隠れないようにする。各ページの `page.tsx` 側では設定不要。

```tsx
// src/app/(public)/layout.tsx
<main id="main-content" className="flex-1 pb-16 md:pb-0">
  {children}
</main>
<MobileBottomNav />
```

---

## 9. フッター仕様

### 9.1 構成

シンプルながら信頼感を演出する 2 段構成。Phase 2 は「シンプルフッター」で十分。

```
┌──────────────────────────────────────────────────────────────────┐
│  bg-primary  text-primary-foreground  py-12                      │
│  max-w-7xl mx-auto px-6                                    │
│                                                                  │
│  ┌─────────────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ Brand             │  │ サービス   │  │ サポート   │  │ 会社情報   │ │
│  │ [Logo white]      │  │          │  │          │  │          │ │
│  │ "日本中の個人から  │  │ セラー登録 │  │ FAQ      │  │ 会社概要   │ │
│  │  最高の商品を"     │  │ 手数料    │  │ お問合せ  │  │ 採用情報   │ │
│  │                   │  │ 安全取引  │  │ 利用ガイド │  │ プレス    │ │
│  └─────────────────┘  └──────────┘  └──────────┘  └──────────┘ │
├──────────────────────────────────────────────────────────────────┤
│  border-t border-primary-foreground/20  py-4                     │
│  © 2026 Kivio, Inc.    プライバシーポリシー  利用規約  特定商取引法 │
└──────────────────────────────────────────────────────────────────┘
```

### 9.2 実装スケルトン

```tsx
<footer className="bg-primary text-primary-foreground">
  <div className="max-w-7xl mx-auto px-6 py-12">
    <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-4">

      {/* Brand */}
      <div className="space-y-4">
        <Link href="/" className="flex items-center gap-2">
          {/* ロゴ SVG: ダーク背景のため brightness-0 invert で白反転（§2.2 と同じく alt="" + テキストで読み上げ） */}
          <Image
            src="/images/kivio-logo.svg"
            alt=""
            width={32}
            height={32}
            className="brightness-0 invert"
          />
          <span className="font-serif text-xl font-bold">Kivio</span>
        </Link>
        <p className="text-sm text-primary-foreground/70 leading-relaxed">
          日本中の個人から<br />最高の商品を。
        </p>
      </div>

      {/* サービス */}
      <div className="space-y-3">
        <h3 className="text-sm font-semibold">サービス</h3>
        <ul className="space-y-2">
          {[
            { label: "セラー登録", href: "/seller/applications/new" },
            { label: "手数料について", href: "#" },
            { label: "安全なお取引", href: "#" },
          ].map(({ label, href }) => (
            <li key={label}>
              <Link href={href} className="text-sm text-primary-foreground/70 hover:text-primary-foreground transition-colors duration-150">
                {label}
              </Link>
            </li>
          ))}
        </ul>
      </div>

      {/* サポート */}
      <div className="space-y-3">
        <h3 className="text-sm font-semibold">サポート</h3>
        <ul className="space-y-2">
          {[
            { label: "よくある質問", href: "#" },
            { label: "お問い合わせ", href: "#" },
            { label: "利用ガイド", href: "#" },
          ].map(({ label, href }) => (
            <li key={label}>
              <Link href={href} className="text-sm text-primary-foreground/70 hover:text-primary-foreground transition-colors duration-150">
                {label}
              </Link>
            </li>
          ))}
        </ul>
      </div>

      {/* 会社情報 */}
      <div className="space-y-3">
        <h3 className="text-sm font-semibold">会社情報</h3>
        <ul className="space-y-2">
          {[
            { label: "会社概要", href: "#" },
            { label: "採用情報", href: "#" },
            { label: "プレスリリース", href: "#" },
          ].map(({ label, href }) => (
            <li key={label}>
              <Link href={href} className="text-sm text-primary-foreground/70 hover:text-primary-foreground transition-colors duration-150">
                {label}
              </Link>
            </li>
          ))}
        </ul>
      </div>

    </div>
  </div>

  {/* Bottom bar */}
  <div className="border-t border-primary-foreground/20">
    <div className="max-w-7xl mx-auto px-6 py-4 flex flex-col sm:flex-row items-center justify-between gap-3">
      <p className="text-xs text-primary-foreground/60">
        © 2026 Kivio, Inc. All rights reserved.
      </p>
      <div className="flex items-center gap-4">
        {[
          { label: "プライバシーポリシー", href: "#" },
          { label: "利用規約", href: "#" },
          { label: "特定商取引法に基づく表記", href: "#" },
        ].map(({ label, href }) => (
          <Link key={label} href={href} className="text-xs text-primary-foreground/60 hover:text-primary-foreground transition-colors duration-150">
            {label}
          </Link>
        ))}
      </div>
    </div>
  </div>
</footer>
```

---

## 10. コンポーネント構成とファイルマッピング

```
src/
├── app/
│   ├── layout.tsx                           # RootLayout（フォント + QueryProvider + Toaster のみ）
│   │                                        # ← <html> に dark クラスなし（ライト固定）
│   ├── (public)/
│   │   ├── layout.tsx                       # PublicLayout（スキップリンク + GlobalHeader + main + GlobalFooter + MobileBottomNav）
│   │   │                                    # ← <main id="main-content" className="flex-1 pb-16 md:pb-0">
│   │   └── page.tsx                         # ホームページ /
│   └── (authenticated)/
│       └── layout.tsx                       # AuthenticatedLayout（GlobalHeader + main + GlobalFooter + MobileBottomNav）
│                                            # ← (public)/layout.tsx と同構成。認証ガード middleware で保護。
│
├── hooks/
│   └── useScrolled.ts                       # スクロール量検知フック（"use client" 内で使用）
│
└── components/
    └── layout/
        ├── GlobalHeader/
        │   ├── index.tsx                    # GlobalHeader（"use client"。AnnouncementBar + sticky <header>）
        │   ├── AnnouncementBar.tsx          # キャンペーン告知バー（sticky 外）
        │   ├── CategoryNav.tsx              # カテゴリサブナビ（sticky <header> 内最下段、lg 以上）
        │   ├── Logo.tsx                     # ロゴ画像 + "Kivio" テキスト
        │   ├── SearchBar.tsx                # 検索フォーム UI（"use client"）
        │   ├── SearchOverlay.tsx            # モバイル: 全画面検索オーバーレイ（fixed inset-0 z-50）
        │   ├── MobileMenuButton.tsx         # ☰ ハンバーガーボタン
        │   ├── MobileSearchButton.tsx       # 🔍 モバイル検索アイコンボタン
        │   ├── MobileMenuSheet.tsx          # ☰ タップで開くモバイルメニュー（Sheet）
        │   │                               # 内容: ログイン/会員登録リンク + カテゴリリスト
        │   └── HeaderActions/
        │       ├── index.tsx                # 認証状態（useAuthStore）に応じて分岐するアクション領域（"use client"）
        │       ├── GuestActions.tsx         # 未認証: 地球 + カート + ログイン + 会員登録
        │       ├── BuyerActions.tsx         # BUYER: 地球 + カート + メッセージ + 通知 + UserMenu
        │       ├── SellerActions.tsx        # SELLER: BUYER 群 + Dashboard btn + UserMenu
        │       ├── LanguageButton.tsx       # 地球アイコン（多言語準備中・3 状態共通）
        │       ├── IconButton.tsx           # バッジ付きアイコンボタン（共通）
        │       ├── NotificationBell.tsx     # 通知ドロップダウン
        │       └── UserMenu.tsx             # アバター + DropdownMenu（ホバー開閉・お気に入り・ログアウト）
        │
        ├── GlobalFooter/
        │   └── index.tsx                    # フッター（Server Component 可）
        │
        └── MobileBottomNav/
            └── index.tsx                    # Fixed ボトムナビ（md 以下、"use client"）
```

**Client / Server Component 境界:**

| コンポーネント | SC / CC | 理由 |
|---|---|---|
| `GlobalHeader/index.tsx` | **CC** | `useScrolled` フックを使用 |
| `SearchBar.tsx` | **CC** | 入力状態を管理 |
| `SearchOverlay.tsx` | **CC** | open/close 状態を管理 |
| `MobileMenuSheet.tsx` | **CC** | Sheet open/close 状態 |
| `MobileBottomNav/index.tsx` | **CC** | `usePathname` でアクティブ判定 |
| `CategoryNav.tsx` | SC | 静的リンクのみ（Phase 2） |
| `Logo.tsx` | SC | 静的 |
| `GlobalFooter/index.tsx` | SC | 静的リンクのみ |

---

## 11. ホームページ（Phase 2 スコープ）

ヘッダー・フッターの動作確認用として最小実装のみ。

```
src/app/(public)/page.tsx

┌──────────────────────────────────────────────┐
│  GlobalHeader                                 │
├──────────────────────────────────────────────┤
│  <main className="flex-1 pb-16 md:pb-0">      │
│    <!-- Hero: ヒーローバナー TBD -->           │
│    <section className="bg-muted py-24 text-center">
│      <h1>商品を探す、売る。<br/>あなたのマーケット。</h1>
│      <p>Kivio でお気に入りの一品を見つけよう</p>
│    </section>
│    <!-- 商品グリッド: スケルトンカード8枚プレースホルダー -->
│  </main>                                      │
├──────────────────────────────────────────────┤
│  GlobalFooter                                 │
├──────────────────────────────────────────────┤
│  MobileBottomNav（md 以下）                   │
└──────────────────────────────────────────────┘
```

---

## 12. アクセシビリティ要件（レイアウト固有）

- `<header>` に `role` 不要（HTML5 landmark 自動付与）
- スキップリンク: `<header>` 直前に配置

```tsx
// src/app/(public)/layout.tsx 先頭
<a
  href="#main-content"
  className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-1000 focus:bg-background focus:px-4 focus:py-2 focus:rounded-md focus:ring-2 focus:ring-ring"
>
  メインコンテンツへスキップ
</a>
```

- `<main id="main-content">` と対応させる
- BottomNav の各アイテム: `aria-current="page"` を現在ページに付与
- HeaderActions のアイコンボタン: すべて `aria-label` 必須（MASTER.md §11）
- フッターリンク: `<nav aria-label="フッターナビゲーション">` でラップ
- タッチターゲット: BottomNav タブは `min-h-11`（44px）以上（MASTER.md §15）

---

## 13. Phase 2 実装優先度

| 優先度 | コンポーネント | 状態 |
|---|---|---|
| P0 | `GlobalHeader`（未認証状態のみ） | ヘッダーUI 100% |
| P0 | `GlobalFooter` | シンプルフッター |
| P0 | `MobileBottomNav`（未認証状態のみ） | BottomNav UI |
| P1 | ホームページ スケルトン | TBD プレースホルダー |
| P2 | `HeaderActions/BuyerActions` | 認証実装後 |
| P2 | `HeaderActions/SellerActions` | 認証実装後 |
| P3 | `AnnouncementBar` | コンテンツ確定後 |
| P3 | `CategoryNav`（API 化） | Phase 3 以降 |
