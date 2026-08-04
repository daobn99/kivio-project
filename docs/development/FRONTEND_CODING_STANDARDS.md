# フロントエンドコーディング規約

**作成日：** 2026年5月31日  
**対象プロジェクト：** Kivio  
**対象スタック：** Next.js 16 (App Router) + TypeScript + Tailwind CSS + shadcn/ui  
**参照元：** Next.js 公式ドキュメント v16.2.6、Web Interface Guidelines、Vercel React Best Practices

---

## 目次

1. [全体アーキテクチャ](#1-全体アーキテクチャ)
2. [レイヤー別責務と規約](#2-レイヤー別責務と規約)
3. [コンポーネント設計規約](#3-コンポーネント設計規約)
4. [React Hooks 規約](#4-react-hooks-規約)
5. [Zustand ストア規約](#5-zustand-ストア規約)
6. [App Router 規約](#6-app-router-規約)
7. [Custom Hooks 規約](#7-custom-hooks-規約)
8. [TanStack Query 規約](#8-tanstack-query-規約)
9. [型定義規約](#9-型定義規約)
10. [フォームバリデーション規約](#10-フォームバリデーション規約)
11. [JSX 規約](#11-jsx-規約)
12. [コメント規約](#12-コメント規約)
13. [実装チェックリスト](#13-実装チェックリスト)

> **Next.js 16 移行ポイント:** `middleware.ts` → `proxy.ts`、`params` / `searchParams` / `cookies()` / `headers()` が非同期 (`Promise`) に変更。
>
> **React 19 移行ポイント:** `forwardRef` は非推奨。`ref` は通常の props として受け取る（`function Foo({ ref, ...props })`）。

---

## 1. 全体アーキテクチャ

### 1.1 ディレクトリ構成

```
kivio-frontend/
├── src/
│   ├── app/                        # Next.js App Router（ルーティング専用）
│   │   ├── (public)/               # 公開画面レイアウトグループ
│   │   │   ├── layout.tsx
│   │   │   └── page.tsx            # ホームページ /
│   │   ├── (authenticated)/        # 認証必須ページのレイアウトグループ
│   │   │   ├── layout.tsx          # グローバル chrome をフル表示
│   │   │   └── profile/
│   │   │       ├── layout.tsx      # アイデンティティ帯 + AccountNav
│   │   │       ├── settings/page.tsx
│   │   │       └── addresses/page.tsx
│   │   ├── (seller)/               # セラーダッシュボードレイアウトグループ（Phase 3+）
│   │   ├── (admin)/                # 管理者ダッシュボードレイアウトグループ（Phase 4+）
│   │   ├── auth/(auth-group)/      # 認証系（ヘッダー・フッター非表示）
│   │   │   ├── layout.tsx          #   URL は /auth/login。グループ名は URL に出ない
│   │   │   ├── login/page.tsx
│   │   │   └── register/page.tsx
│   │   ├── api/                    # Route Handler（BFF・OAuth コールバック）
│   │   │   ├── v1/[...path]/route.ts   # バックエンドへの汎用プロキシ
│   │   │   ├── v1/auth/*/route.ts      # Cookie を発行・破棄する認証系のみ個別実装
│   │   │   └── auth/[...nextauth]/route.ts
│   │   ├── layout.tsx              # ルートレイアウト（Provider 群のみ）
│   │   ├── loading.tsx / error.tsx / not-found.tsx / global-error.tsx
│   │   ├── unauthorized.tsx        # 401 エラー画面（Next.js 16）
│   │   └── forbidden.tsx           # 403 エラー画面（Next.js 16）
│   ├── components/
│   │   ├── layout/                 # GlobalHeader, GlobalFooter, MobileBottomNav
│   │   ├── ui/                     # shadcn/ui（@base-ui/react ベース）+ 共通 UI
│   │   ├── form/                   # 画面横断のフォーム部品（FieldError, FormAlert 等）
│   │   ├── providers/              # QueryProvider, AuthHydrator 等
│   │   ├── auth/                   # LoginForm, RegisterFlow
│   │   ├── profile/                # ProfileSettingsForm, AccountNav
│   │   ├── address/                # AddressList, AddressFormSheet
│   │   ├── product/ order/ cart/ seller/
│   ├── hooks/                      # Custom Hooks（use プレフィックス必須）
│   │   ├── queries/                # TanStack Query の useQuery ラッパー
│   │   └── mutations/              # TanStack Query の useMutation ラッパー
│   ├── lib/
│   │   ├── api/
│   │   │   ├── ApiError.ts         # ProblemDetail を包む例外型
│   │   │   ├── bff/                # Route Handler 側の共通処理（Cookie・CSRF・転送）
│   │   │   ├── client/             # Client Component 用 API 関数（apiFetch 経由）
│   │   │   └── server/             # Server Component 用 API 関数（server-only）
│   │   ├── validations/            # Zod スキーマ
│   │   ├── constants/              # 定数（index.ts に ROUTES 等、以降は用途別に分割）
│   │   ├── apiErrors.ts            # エラーコード → 表示文言の変換
│   │   ├── queryKeys.ts            # Query Key の一元定義
│   │   ├── format.ts               # Intl ベースの表示整形
│   │   └── utils.ts                # cn() など汎用ユーティリティ
│   ├── stores/                     # Zustand ストア
│   ├── types/                      # TypeScript 型定義（api/ ドメイン型・enums.ts）
│   ├── test/                       # Vitest セットアップ・MSW ハンドラ
│   └── proxy.ts                    # 認証ガード（Next.js 16: middleware.ts → proxy.ts）
├── public/
├── next.config.ts
├── tsconfig.json
└── components.json                 # shadcn/ui 設定
```

> Tailwind CSS 4 は設定を CSS 側（`app/globals.css` の `@theme inline`）に置くため、`tailwind.config.ts` は使わない。

### 1.2 レンダリング戦略の原則

| 状況 | 採用戦略 |
|---|---|
| 商品一覧・詳細（SEO重要） | Server Component + `use cache` |
| ダッシュボード（認証後・リアルタイム） | Server Component + Client Component のハイブリッド |
| フォーム・インタラクション | Client Component |
| 認証状態に依存する UI | Client Component（`'use client'`）|
| API キー・秘密情報を使う処理 | Server Component のみ（`server-only`） |

---

## 2. レイヤー別責務と規約

### 2.1 Server Component（デフォルト）

**責務:** データ取得・SEO・初期レンダリング。  
**制約:** イベントハンドラ・`useState`・ブラウザ API 使用禁止。

```tsx
// ✅ 良い例: 非同期 Server Component でデータ取得
export default async function ProductPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const product = await getProduct(id)  // lib/api/products.ts から直接呼ぶ
  return <ProductDetail product={product} />
}

// ❌ 悪い例: Server Component で useEffect
export default function ProductPage() {
  useEffect(() => { /* エラー */ }, [])
}
```

**データ取得は `lib/api/` の関数を介して行い、`fetch` を直接 page/layout に書かない。**

```ts
// src/lib/api/products.ts
import 'server-only'

export async function getProduct(id: string): Promise<Product> {
  const res = await fetch(`${process.env.API_BASE_URL}/api/v1/products/${id}`)
  if (!res.ok) throw new Error('Product not found')
  return res.json()
}
```

### 2.2 Client Component

**責務:** インタラクション・フォーム・状態管理・ブラウザ API。  
**原則:** `'use client'` はできる限り**葉コンポーネントに限定**し、バンドルサイズを最小化する。

```tsx
// ✅ 良い例: インタラクティブ部分だけを切り出す
// src/components/product/AddToCartButton.tsx
'use client'
export function AddToCartButton({ productId }: { productId: string }) {
  const [isPending, startTransition] = useTransition()
  // ...
}

// ❌ 悪い例: ページ全体を 'use client' にする
'use client'
export default function ProductPage() { /* 全体がクライアントバンドルに入る */ }
```

### 2.3 API クライアント（Client 用）

Client Component からの API 呼び出しは `src/lib/api/client/` に集約し、`apiFetch` を必ず経由する。  
Access Token は `Authorization` ヘッダーで直接送らず、Route Handler（BFF）が httpOnly Cookie から詰め替える。

`apiFetch` が一元的に担う責務は次の 4 つ。個別の API 関数側で再実装しない。

| 責務 | 内容 |
|---|---|
| 401 リカバリ | Refresh Token で 1 度だけ再試行し、失敗したら `/auth/login` へリダイレクトする |
| リフレッシュの重複排除 | Token Rotation は単一使用のため single-flight にする（並行リクエストが Reuse Detection を誘発するのを防ぐ）|
| エラー変換 | `ProblemDetail` を `ApiError` に変換して throw する（エラーコードのフィールド名は `code`。`errorCode` ではない）|
| 204 の扱い | `204 No Content` は `res.json()` が `SyntaxError` になるため早期リターンする |

```ts
// src/lib/api/client/base.ts
'use client'
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await doFetch(path, init)

  if (res.status === 401) {
    // refreshSession() は single-flight（同時実行を 1 本に畳む）
    if (!(await refreshSession())) redirect('/auth/login')
    res = await doFetch(path, init)
    if (res.status === 401) redirect('/auth/login')
  }

  if (!res.ok) {
    const problem: ProblemDetail = await res.json()
    throw new ApiError(problem.title, problem.status, problem.detail, problem.code)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
```

### 2.4 Route Handler（API Routes）

`src/app/api/` 配下に配置。外部 API のプロキシ・Webhook 受信・認証コールバックにのみ使用する。  
バックエンドに直接 fetch できる場合は Route Handler を挟まない。

```ts
// src/app/api/auth/callback/google/route.ts
import { type NextRequest } from 'next/server'

export async function GET(request: NextRequest) {
  // Google OAuth コールバック処理
}
```

### 2.5 Proxy（旧 Middleware）

**Next.js 16 からファイル名が `middleware.ts` → `proxy.ts`、関数名が `middleware()` → `proxy()` に変更された。**

`src/proxy.ts` で認証が必要なパスを保護する。

```ts
// src/proxy.ts  ← Next.js 16+（v14-15 は middleware.ts）
import { NextResponse, type NextRequest } from 'next/server'

const PROTECTED_PATHS = ['/cart', '/checkout', '/orders', '/seller', '/admin', '/wishlist', '/messages']

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl
  const token = request.cookies.get('access_token')

  if (PROTECTED_PATHS.some(p => pathname.startsWith(p)) && !token) {
    return NextResponse.redirect(new URL(`/auth/login?from=${pathname}`, request.url))
  }
  return NextResponse.next()
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|public).*)'],
}
```

**移行コマンド:** `npx @next/codemod@latest upgrade`

### 2.6 Server Actions

バックエンドが Spring Boot のため、**ミューテーションは基本 TanStack Query Mutation 経由**（`8. TanStack Query 規約` を参照）。  
ただし、フォーム送信のプログレッシブエンハンスメントや `revalidateTag` によるキャッシュ無効化が必要な場合は Server Actions を用いる。

```ts
// src/app/actions/product.ts
'use server'
import { revalidateTag } from 'next/cache'

export async function revalidateProducts() {
  revalidateTag('products')
}
```

**⚠️ `redirect()` / `notFound()` / `forbidden()` / `unauthorized()` は内部で例外を投げる。**  
try-catch で囲むと Next.js がハンドリングできずナビゲーションが失敗する。

```ts
// ❌ redirect() が catch に吸い込まれてナビゲーション失敗
async function action() {
  try {
    await doSomething()
    redirect('/success')
  } catch (error) {
    return { error: 'failed' }  // redirect の throw もここに来る
  }
}

// ✅ try-catch の外で呼ぶ、または unstable_rethrow で再スロー
import { unstable_rethrow } from 'next/navigation'

async function action() {
  try {
    await doSomething()
  } catch (error) {
    unstable_rethrow(error)  // Next.js 内部エラーを再スロー
    return { error: 'failed' }
  }
  redirect('/success')  // try-catch の外
}
```

**Next.js 16 の認証エラー専用ページ:**

```ts
// app/unauthorized.tsx → 401 画面
// app/forbidden.tsx   → 403 画面
import { unauthorized, forbidden } from 'next/navigation'

if (!session) unauthorized()
if (!session.hasAccess) forbidden()
```

---

## 3. コンポーネント設計規約

### 3.1 命名規則

| 種別 | 命名 | 例 |
|---|---|---|
| コンポーネントファイル | `PascalCase.tsx` | `ProductCard.tsx` |
| ページファイル | `page.tsx`（固定） | `app/products/[id]/page.tsx` |
| レイアウト | `layout.tsx`（固定） | `app/(seller)/layout.tsx` |
| Skeleton | `{ComponentName}Skeleton.tsx` | `ProductCardSkeleton.tsx` |
| コンポーネント関数 | `PascalCase` | `export function ProductCard`（ルートファイルのみ `export default`） |
| Props 型 | `{ComponentName}Props` | `type ProductCardProps` |

### 3.2 Atomic Design 分類

```
Atoms    → shadcn/ui コンポーネントをそのまま、または最小限ラップしたもの
           Button, Input, Badge, Avatar, Skeleton, Separator
Molecules → 複数 Atom の組み合わせ。独自ロジックを持つ
           ProductCard, PriceTag, RatingStars, SearchBar, EmptyState, ErrorFallback
Organisms → ページセクションを構成する大きな単位
           Navbar, Footer, SellerSidebar, ProductGrid, CheckoutForm
Templates → Layout ファイル（app Router の layout.tsx）
Pages     → app Router の page.tsx
```

### 3.3 コンポーネントの 3 状態必須化

すべてのデータ依存コンポーネントに **Loading / Error / Empty** の 3 状態を実装する。

```tsx
// src/components/product/ProductGrid.tsx
interface ProductGridProps {
  products: Product[]
  isLoading?: boolean
  error?: Error | null
}

export function ProductGrid({ products, isLoading, error }: ProductGridProps) {
  if (isLoading) return <ProductGridSkeleton />
  if (error) return <ErrorFallback message="商品の読み込みに失敗しました" />
  if (products.length === 0) return <EmptyState title="商品が見つかりません" />
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
      {products.map(product => <ProductCard key={product.id} product={product} />)}
    </div>
  )
}
```

### 3.4 Server Component → Client Component へのデータ受け渡し

Server Component でデータを取得し、Client Component へ props として渡す。  
props は **シリアライズ可能な値のみ**（関数・クラスインスタンス禁止）。

```tsx
// Server Component (page.tsx)
export default async function ProductPage({ params }) {
  const { id } = await params
  const product = await getProduct(id)
  return <ProductDetailClient product={product} />  // シリアライズ可能な DTO を渡す
}

// Client Component
'use client'
export function ProductDetailClient({ product }: { product: ProductDto }) {
  const [quantity, setQuantity] = useState(1)
  // ...
}
```

### 3.5 shadcn/ui 利用規約

- shadcn/ui コンポーネントは `src/components/ui/` に配置し、**直接編集してよい**（ライブラリではなくコードコピー）
- デザイントークン（カラー）は必ず CSS 変数 (`--primary` 等) を経由する。直接カラー値を Tailwind クラスに書かない
- 新しいバリアントが必要な場合は `cva()` を使って既存コンポーネントに追加する

```tsx
// ✅ CSS 変数経由
<div className="bg-primary text-primary-foreground" />

// ❌ 直接カラー指定
<div className="bg-blue-600 text-white" />
```

**Base UI ベースであることに起因する注意点:**

本プロジェクトの shadcn/ui は Radix ではなく `@base-ui/react` の上に構築されている。ネイティブ要素を前提にしたスタイル・アサーションが通用しないケースがあるため、以下に注意する。

| 事象 | 対処 |
|---|---|
| `Checkbox` は `<span role="checkbox">` として描画される | 無効状態は `disabled:` ではなく `data-disabled:` で装飾する。テストも `toBeDisabled()` ではなく `aria-disabled` を検証する |
| 状態バリアントは `data-*` 属性 | `data-checked:` / `data-open:` を使う（`data-[checked]:` ではなく短縮形が正） |
| `AlertDialogAction` は素の `Button` で、押しても自動で閉じない | 閉じるタイミングは呼び出し側が制御する（失敗時はダイアログを開いたままエラーを表示できる）|
| `Sheet` の `side` は `data-[side=…]` クラスで効く | `data-[side=bottom]:*` は `sm:*` より詳細度が高く CSS では上書きできない。画面幅で出し分けるときは `useMediaQuery` で `side` prop 自体を切り替える |

---

## 4. React Hooks 規約

### 4.1 基本ルール

- Hooks はコンポーネントのトップレベルまたは Custom Hooks 内でのみ呼び出す
- 条件分岐・ループ内での呼び出し禁止
- `useEffect` 内での非同期処理は即時実行関数（IIFE）または別関数に切り出す

```tsx
// ✅
useEffect(() => {
  let cancelled = false
  async function load() {
    const data = await fetchData()
    if (!cancelled) setData(data)
  }
  load()
  return () => { cancelled = true }
}, [])

// ❌ useEffect に async を直接つける
useEffect(async () => { /* クリーンアップできない */ }, [])
```

### 4.2 状態管理の選択指針

| 状態の種類 | 手段 |
|---|---|
| UI ローカル状態（モーダル開閉・入力値） | `useState` |
| 複雑なローカル状態遷移 | `useReducer` |
| 非同期・楽観的更新 | `useOptimistic` + Server Actions |
| サーバー状態（API データのキャッシュ） | TanStack Query |
| 認証・カートのようなグローバル UI 状態 | Zustand |
| フォーム状態 | react-hook-form |

### 4.3 `useOptimistic` パターン

カート追加・お気に入りのようなユーザー操作への即時反映に使う。

```tsx
'use client'
import { useOptimistic, useTransition } from 'react'

export function WishlistButton({ productId, initialLiked }: WishlistButtonProps) {
  const [optimisticLiked, toggleOptimistic] = useOptimistic(
    initialLiked,
    (state) => !state
  )
  const [, startTransition] = useTransition()

  const handleToggle = () => {
    startTransition(async () => {
      toggleOptimistic(null)
      await toggleWishlist(productId)
    })
  }

  return (
    <button onClick={handleToggle} aria-label={optimisticLiked ? 'お気に入りから削除' : 'お気に入りに追加'}>
      <HeartIcon filled={optimisticLiked} />
    </button>
  )
}
```

### 4.4 `useCallback` / `useMemo` の使用基準

パフォーマンス最適化のためだけに使用する。可読性を犠牲にして安易に追加しない。

```tsx
// ✅ 子コンポーネントへのコールバック Props
const handleDelete = useCallback((id: string) => {
  // ...
}, [])

// ✅ 高コストな計算
const sortedProducts = useMemo(() => [...products].sort(byPrice), [products])

// ❌ 不要な useMemo
const title = useMemo(() => product.name, [product.name])  // ただの値参照
```

---

## 5. Zustand ストア規約

### 5.1 ストア分割方針

ドメインごとにストアを分割する。1 ファイル 1 ストアを原則とする。

```
src/stores/
├── useAuthStore.ts       # 認証状態（currentUser, role, logout）
├── useCartStore.ts       # カート（ローカル状態・楽観的UI用）
└── useUiStore.ts         # グローバル UI 状態（toast queue, modal stack）
```

**ルール:**
- サーバー状態（APIデータ）は Zustand に入れない → TanStack Query が管理する
- Zustand は UI 状態とクライアントセッション状態のみ保持する

### 5.2 ストア定義パターン

```ts
// src/stores/useAuthStore.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  user: AuthUser | null
  isAuthenticated: boolean
  setUser: (user: AuthUser) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      setUser: (user) => set({ user, isAuthenticated: true }),
      logout: () => set({ user: null, isAuthenticated: false }),
    }),
    { name: 'kivio-auth' }
  )
)
```

### 5.3 セレクタパターン（不要な再レンダリング防止）

```tsx
// ✅ 必要な値だけを購読
const user = useAuthStore(state => state.user)
const logout = useAuthStore(state => state.logout)

// ❌ ストア全体を購読（どの値が変わっても再レンダリング）
const { user, logout } = useAuthStore()
```

---

## 6. App Router 規約

### 6.1 ルートファイル命名

| ファイル | 用途 | 備考 |
|---|---|---|
| `page.tsx` | ルートの UI | 必須。ないと URL として公開されない |
| `layout.tsx` | 共有レイアウト | 子ルートをラップ。アンマウントされない |
| `loading.tsx` | Suspense Boundary の fallback | Skeleton UI を配置 |
| `error.tsx` | Error Boundary | `'use client'` 必須 |
| `not-found.tsx` | 404 UI | `notFound()` をトリガーに表示 |
| `global-error.tsx` | ルートレイアウトエラー | `'use client'` + `<html><body>` 必須 |
| `route.ts` | API Route Handler | GET/POST/PATCH/DELETE をエクスポート |
| `unauthorized.tsx` | 401 エラー画面 | `unauthorized()` をトリガーに表示（Next.js 16） |
| `forbidden.tsx` | 403 エラー画面 | `forbidden()` をトリガーに表示（Next.js 16） |

### 6.2 動的ルートの `params` 取得

Next.js 16 以降、`params` は `Promise` になった。**必ず `await` すること。**

```tsx
// ✅ Next.js 16
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  // ...
}

// ❌ 旧パターン（型エラー）
export default async function Page({ params: { id } }) { }
```

### 6.3 メタデータ

SEO が必要な全 page.tsx に `generateMetadata` を実装する。

```tsx
export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>
}): Promise<Metadata> {
  const { id } = await params
  const product = await getProduct(id)
  return {
    title: `${product.name} | Kivio`,
    description: product.description,
    openGraph: {
      images: [product.imageUrl],
    },
  }
}
```

### 6.4 ナビゲーション

```tsx
// クライアントサイドナビゲーション
import { useRouter } from 'next/navigation'
const router = useRouter()
router.push('/products')

// Server Component からのリダイレクト
import { redirect } from 'next/navigation'
redirect('/auth/login')

// Link コンポーネント（プリフェッチ自動）
import Link from 'next/link'
<Link href={`/products/${product.id}`}>{product.name}</Link>

// 動的ルートの型安全ヘルパー（src/lib/constants.ts）
export const ROUTES = {
  product: (id: string) => `/products/${id}` as const,
  sellerProduct: (id: string) => `/seller/products/${id}/edit` as const,
} as const
```

**⚠️ `useSearchParams` は必ず `<Suspense>` でラップする:**  
ラップしないとページ全体が CSR にフォールバックするサイレントバグが発生する。

```tsx
// ❌ ページ全体が CSR になる
'use client'
export function SearchBar() {
  const searchParams = useSearchParams()
  return <input defaultValue={searchParams.get('q') ?? ''} />
}

// ✅ Suspense でラップして SSR を維持
import { Suspense } from 'react'

export default function SearchPage() {
  return (
    <Suspense fallback={<SearchBarSkeleton />}>
      <SearchBar />
    </Suspense>
  )
}
```

| Hook | Suspense 必須 |
|---|---|
| `useSearchParams()` | **必須** |
| `usePathname()` | 動的ルートで必須 |
| `useParams()` | 不要 |
| `useRouter()` | 不要 |

### 6.5 キャッシュ戦略

```tsx
// ページキャッシュ（use cache ディレクティブ）
import { unstable_cacheTag as cacheTag } from 'next/cache'

export async function getProducts(): Promise<Product[]> {
  'use cache'
  cacheTag('products')
  const res = await fetch(`${process.env.API_BASE_URL}/api/v1/products`)
  return res.json()
}

// On-Demand Revalidation（商品更新時）
import { revalidateTag } from 'next/cache'
revalidateTag('products')
```

**`React.cache()` ― 同一リクエスト内の重複排除（Vercel ベストプラクティス）:**

```ts
// src/lib/api/server/products.ts
import { cache } from 'react'

// 同じリクエスト内で複数箇所から呼ばれても 1 回しか fetch しない
export const getProduct = cache(async (id: string): Promise<Product> => {
  const res = await fetch(`${process.env.API_BASE_URL}/api/v1/products/${id}`)
  if (!res.ok) throw new Error('Product not found')
  return res.json()
})
```

**Suspense ストリーミング ― 並列データ取得でページ全体のブロックを回避:**

```tsx
// ✅ 各セクションが独立して fetch → 準備できた順に表示
export default function DashboardPage() {
  return (
    <div>
      <Suspense fallback={<StatsSkeleton />}>
        <SellerStatsSection />
      </Suspense>
      <Suspense fallback={<ProductListSkeleton />}>
        <RecentProductsSection />
      </Suspense>
    </div>
  )
}

async function SellerStatsSection() {
  const stats = await getSellerStats()  // 他の fetch をブロックしない
  return <SellerStatsCard stats={stats} />
}
```

**`after()` ― レスポンス送信後にノンブロッキング処理を実行:**

```ts
import { after } from 'next/server'

export async function POST(req: Request) {
  const data = await req.json()
  const result = await saveData(data)

  after(async () => {
    await sendNotification(result.id)  // レスポンスをブロックしない
  })

  return Response.json(result)
}
```

---

## 7. Custom Hooks 規約

### 7.1 命名・配置

- ファイル名・関数名ともに `use` プレフィックス必須: `useCartItems.ts`
- `src/hooks/` 配下に配置
- 1 ファイル 1 Hook を原則とする

### 7.2 設計指針

```ts
// ✅ 良い例: 単一責任・戻り値を明示
export function useProductSearch(initialQuery?: string) {
  const [query, setQuery] = useState(initialQuery ?? '')
  const [debouncedQuery] = useDebounce(query, 300)
  const { data, isLoading } = useProductsQuery({ q: debouncedQuery })

  return { query, setQuery, products: data?.items ?? [], isLoading } as const
}

// ❌ 悪い例: 複数の責任を持つ
export function useProductPageLogic() {
  // データ取得 + フォーム + カート + ... を一つに詰め込む
}
```

### 7.3 よく使う汎用 Hooks

```
useDebounce(value, delay)       — 検索入力のデバウンス
useIntersectionObserver()       — 無限スクロール検知
useMediaQuery(query)            — レスポンシブ判定
useLocalStorage(key, initial)   — LocalStorage の型安全ラッパー
useClipboard()                  — クリップボードコピー
```

---

## 8. TanStack Query 規約

### 8.1 Query Key 設計

Query Key はネスト配列で階層的に定義し、`src/lib/queryKeys.ts` に集約する。

```ts
// src/lib/queryKeys.ts
export const queryKeys = {
  products: {
    all: ['products'] as const,
    list: (params: ProductListParams) => ['products', 'list', params] as const,
    detail: (id: string) => ['products', 'detail', id] as const,
    reviews: (id: string) => ['products', 'detail', id, 'reviews'] as const,
  },
  cart: {
    detail: ['cart'] as const,
  },
  user: {
    me: ['user', 'me'] as const,
    addresses: ['user', 'me', 'addresses'] as const,
  },
  seller: {
    stats: ['seller', 'stats'] as const,
    products: (params?: SellerProductListParams) => ['seller', 'products', params] as const,
    orders: (params?: OrderListParams) => ['seller', 'orders', params] as const,
  },
} as const
```

**ルール:**
- Query Key の文字列リテラルをコンポーネントに直書きしない。必ず `queryKeys` から参照する
- 親子関係は配列の前方一致で表現する（`['user','me']` を無効化すると `['user','me','addresses']` も無効化される）

### 8.2 Query Hooks

各エンドポイントに対応する Custom Hook を `src/hooks/queries/` 配下に作成する。

```ts
// src/hooks/queries/useProductQuery.ts
import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '@/lib/queryKeys'
import { getProductClient } from '@/lib/api/client/products'

export function useProductQuery(id: string) {
  return useQuery({
    queryKey: queryKeys.products.detail(id),
    queryFn: () => getProductClient(id),
    staleTime: 1000 * 60 * 5,  // 5分
  })
}
```

### 8.3 Mutation Hooks

`useMutation` をコンポーネントに直接書かず、`src/hooks/mutations/` の Custom Hook に切り出す。

**キャッシュ無効化はミューテーション側の責務とする。** 同じ `invalidateQueries` を複数のコンポーネントに書くと、呼び出し漏れで一覧が古いまま残る。無効化を Hook に閉じ込めれば、呼び出し側は `mutate()` するだけでよい。

```ts
// src/hooks/mutations/useAddToCartMutation.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/lib/queryKeys'

export function useAddToCartMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (item: AddToCartRequest) => addToCartApi(item),
    // 成否によらず無効化する。失敗理由がサーバー側の状態変化（他タブでの削除等）の場合も
    // 最新化が必要なため onSuccess ではなく onSettled を使う
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.cart.detail }),
  })
}
```

**画面固有の副作用（ダイアログを閉じる・遷移する・トーストを出す）は Hook に入れない。** `mutate()` の第 2 引数、または呼び出し側の `onSuccess` で渡す。

```tsx
// ✅ 汎用ロジックは Hook、画面固有の後処理は呼び出し側
const mutation = useDeleteAddressMutation()
mutation.mutate(id, { onSuccess: () => setDialogOpen(false) })
```

**エラー文言は Hook で決めない。** 同じエラーコードでも画面によって適切な文言が異なるため、`resolveApiError(error, overrides)`（`src/lib/apiErrors.ts`）で表示側が変換する。

### 8.4 Server Component との共存

Server Component でデータ取得済みの場合、Client Component では初期データとして渡す（二重取得を防ぐ）。

```tsx
// page.tsx (Server Component)
export default async function ProductPage({ params }) {
  const { id } = await params
  const product = await getProduct(id)

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <ProductDetailClient initialProduct={product} productId={id} />
    </HydrationBoundary>
  )
}

// ProductDetailClient.tsx (Client Component)
'use client'
export function ProductDetailClient({ initialProduct, productId }) {
  const { data: product } = useProductQuery(productId, { initialData: initialProduct })
  // ...
}
```

---

## 9. 型定義規約

### 9.1 型ファイルの配置

```
src/types/
├── api/              # API レスポンス DTO（バックエンドの JSON 形式に対応）
│   ├── product.ts
│   ├── order.ts
│   ├── auth.ts
│   ├── problem-detail.ts # ProblemDetail, ValidationError
│   ├── page-response.ts  # PageResponse<T>
│   ├── error-codes.ts    # ApiErrorCode
│   └── index.ts          # api/ の re-export（バレル）
├── domain/           # フロントエンドのドメインモデル（API 型を加工したもの）
│   └── cart.ts
└── enums.ts          # バックエンド Enum と対応する const + type 定義
```

### 9.2 命名規則

| 種別 | 規約 | 例 |
|---|---|---|
| 型エイリアス | `PascalCase` | `type ProductDto = {...}` |
| インターフェース | `PascalCase`（`I` プレフィックス不要） | `interface ProductFormValues` |
| Union 型 | `PascalCase` | `type UserRole = 'BUYER' \| 'SELLER' \| 'ADMIN'` |
| 汎用型引数 | 1 文字大文字または説明的な名前 | `T`, `TData`, `TError` |

### 9.3 API レスポンス型

バックエンドの API 設計書（`docs/design/API_DESIGN.md`）の JSON フィールドに厳密に対応させる。

**バックエンドは Jackson の `default-property-inclusion: non_null` を有効にしており、値が null のフィールドは JSON から丸ごと省略される。** 「null になり得る」フィールドは `?:` で任意にし、`null` との union で受ける。`string | null` だけで宣言すると、実際には `undefined` が来て型と実体がずれる。

```ts
// ✅ 省略され得るフィールド
avatarUrl?: string | null

// ❌ 実際には undefined が来るのに null しか許容していない
avatarUrl: string | null
```

```ts
// src/types/api/page-response.ts
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

// src/types/api/problem-detail.ts
export interface ValidationError {
  field: string
  message: string
  rejectedValue?: unknown
}

export interface ProblemDetail {
  type: string
  title: string
  status: number
  /** UPPER_SNAKE_CASE エラーコード（フィールド名は `code`。`errorCode` ではない） */
  code: string
  detail: string
  instance: string
  /** バリデーションエラー時のフィールド別詳細 */
  errors?: ValidationError[]
}

// src/types/api/error-codes.ts
export type ApiErrorCode =
  | 'PRODUCT_NOT_FOUND'
  | 'PRODUCT_OUT_OF_STOCK'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'CART_ITEM_NOT_FOUND'
  | 'SELLER_APPLICATION_ALREADY_EXISTS'
  // ... docs/design/ERROR_CODES.md を参照
```

### 9.4 型ガード

```ts
// ✅ 型ガード関数を使う
function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

// ✅ as const でリテラル型を固定
const USER_ROLES = ['BUYER', 'SELLER', 'ADMIN'] as const
type UserRole = typeof USER_ROLES[number]
```

### 9.5 Enum / Union Type

バックエンドの Enum 値と対応させる。**`const` + `type` パターン**を使い、ランタイムでも参照できるようにする。  
`src/types/enums.ts` にすべての Enum 定義を集約する。

```ts
// src/types/enums.ts — const でランタイム値、type で型アノテーション

export const OrderStatus = {
  PENDING: 'PENDING',
  PAID: 'PAID',
  SHIPPED: 'SHIPPED',
  DELIVERED: 'DELIVERED',
  CANCELLED: 'CANCELLED',
} as const
export type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus]

```

```ts
// 利用例
// ランタイム: UserRole.BUYER → 'BUYER'
// 型注釈:    role: UserRole
// イテレーション: Object.values(UserRole)
// 判定:      if (role === UserRole.ADMIN) { ... }
```

**ルール:**
- バックエンドの Enum 名・値と**完全一致**させる（大文字・スネークケースをそのまま使う）
- `enum` キーワードは使わない（TypeScript の `enum` はツリーシェイキングに不利なため）
- UI 表示用のラベルマップは別途 `constants.ts` に定義し、`enums.ts` に混在させない

```ts
// src/lib/constants.ts — 表示ラベルは定数として分離
export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  [OrderStatus.PENDING]: '注文確認中',
  [OrderStatus.PAID]: '支払済',
  [OrderStatus.SHIPPED]: '発送済',
  [OrderStatus.DELIVERED]: '配達完了',
  [OrderStatus.CANCELLED]: 'キャンセル',
}
```

### 9.6 `any` 使用禁止

- `any` の使用は禁止。どうしても必要な場合は `eslint-disable` コメントと理由を記載する
- 外部データ（API レスポンス・localStorage）には `unknown` を使い、型ガードで絞り込む

---

## 10. フォームバリデーション規約

### 10.1 スタック

- **react-hook-form** — フォーム状態管理
- **zod** — スキーマバリデーション
- `zodResolver` で両者を接続

### 10.2 スキーマ定義

Zod スキーマは `src/lib/validations/` 配下に集約し、フォームコンポーネントと分離する。

> **Zod v4:** メール検証はトップレベルの `z.email()` を使う。`z.string().email()` は v4 で **deprecated**。

```ts
// src/lib/validations/auth.ts
import { z } from 'zod'

export const loginSchema = z.object({
  email: z.email('有効なメールアドレスを入力してください'),
  password: z.string().min(8, 'パスワードは8文字以上です'),
})

export type LoginFormValues = z.infer<typeof loginSchema>
```

### 10.3 フォームコンポーネント実装パターン

```tsx
// src/components/auth/LoginForm.tsx
'use client'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { loginSchema, type LoginFormValues } from '@/lib/validations/auth'

export function LoginForm() {
  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = async (data: LoginFormValues) => {
    // mutate / Server Action 呼び出し
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <FormField
          control={form.control}
          name="email"
          render={({ field }) => (
            <FormItem>
              <FormLabel>メールアドレス</FormLabel>
              <FormControl>
                <Input
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  {...field}
                />
              </FormControl>
              <FormMessage />  {/* インラインエラー表示 */}
            </FormItem>
          )}
        />
        {/* パスワードフィールドも同様 */}
        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? 'ログイン中...' : 'ログイン'}
        </Button>
      </form>
    </Form>
  )
}
```

### 10.4 フォームバリデーション Web Interface Guidelines 準拠

- `<input>` には適切な `type` 属性を付与（`email`, `password`, `tel` 等）
- `autoComplete` 属性を設定する（`email`, `current-password`, `new-password` 等）
- メール・コード入力フィールドには `spellCheck={false}` を設定する
- バリデーションエラーは `FormMessage` でインライン表示する（Toast に流さない）
- エラー発生時は最初のエラーフィールドにフォーカスを移動する
- 送信中はボタンを `disabled` にする（二重送信防止）

---

## 11. JSX 規約

### 11.1 基本ルール

- コンポーネントは `function` 宣言で定義する（アロー関数に代入した default export は禁止）
- **export の方針:**
  - **ルートファイル（`page.tsx` / `layout.tsx` / `error.tsx` / `not-found.tsx` / `global-error.tsx` / `loading.tsx` 等）は `export default`**（Next.js が default export を要求するため、選択の余地はない）
  - **それ以外のコンポーネント・Provider・ユーティリティは名前付き export**（`export function ProductCard`）。リファクタ時の一括リネーム・エディタの auto-import・grep 追跡に有利で、匿名 default export による命名ゆらぎを防ぐ
- ファイル 1 つにつき 1 コンポーネントを export する（補助的な型・定数の名前付き export 併用は可）
- JSX 内のコメントは `{/* */}` のみ使用する

```tsx
// ✅ 非ルートコンポーネント: 名前付き export + function 宣言
export function ProductCard({ product }: ProductCardProps) {
  return <div>{product.name}</div>
}

// ✅ ルートファイル（page.tsx / layout.tsx 等）のみ default export
export default function ProductPage() {
  return <ProductCard product={product} />
}

// ❌ アロー関数に代入した default export
const ProductCard = ({ product }) => <div>{product.name}</div>
export default ProductCard
```

**`ref` は通常の props として受け取る。** React 19 で `forwardRef` は非推奨になり、関数コンポーネントが直接 `ref` を props として受け取れるようになった。ラッパーが 1 段減り、`displayName` の指定も不要になる。

```tsx
// ✅ React 19
export function PasswordInput({ ref, className, ...props }: React.ComponentProps<'input'>) {
  return <Input ref={ref} className={className} {...props} />
}

// ❌ forwardRef（React 19 で非推奨）
export const PasswordInput = forwardRef<HTMLInputElement, Props>(function PasswordInput(props, ref) { ... })
```

### 11.2 アクセシビリティ必須項目（Web Interface Guidelines 準拠）

| 要件 | 実装 |
|---|---|
| アイコンボタン | `aria-label` 必須 |
| フォームコントロール | `<label>` または `aria-label` 必須 |
| インタラクティブ要素 | キーボードハンドラ（`onKeyDown` 等）必須 |
| セマンティック HTML | `<button>`, `<a>`, `<nav>` 等を優先し ARIA は補足 |
| 見出し階層 | `h1` → `h2` → `h3` の順序を崩さない |
| フォーカス | `focus-visible:ring-*` で視覚的フォーカスを保証。`outline-none` 単体使用禁止 |

```tsx
// ✅
<button
  onClick={handleDelete}
  onKeyDown={e => e.key === 'Enter' && handleDelete()}
  aria-label="商品を削除"
  className="focus-visible:ring-2 focus-visible:ring-ring"
>
  <Trash2Icon className="h-4 w-4" aria-hidden />
</button>

// ❌
<div onClick={handleDelete}>削除</div>
```

### 11.3 テキストと数値の表示

```tsx
// ✅ 金額: Intl.NumberFormat を使う（日本円）
const price = new Intl.NumberFormat('ja-JP', { style: 'currency', currency: 'JPY' }).format(1500)

// ✅ 日時: Intl.DateTimeFormat を使う
const date = new Intl.DateTimeFormat('ja-JP', { dateStyle: 'long' }).format(new Date(createdAt))

// ✅ 数値カラム: tabular-nums クラスで桁揃え
<span className="font-variant-numeric tabular-nums">{count}</span>

// ✅ 長いテキストのオーバーフロー処理
<p className="truncate">{product.name}</p>
<p className="line-clamp-2">{product.description}</p>
```

### 11.4 アニメーション規約

```tsx
// ✅ prefers-reduced-motion を考慮
<div className="transition-transform duration-200 ease-out motion-reduce:transition-none">

// ✅ animate は transform / opacity のみ（layout を引き起こさない）
<div className="animate-fade-in" />  // opacity の変化のみ

// ❌ transition: all
<div className="transition-all" />

// アニメーション統一値（tailwind.config.ts に定義）
// ボタン Hover: 100ms / ease-in
// モーダル・Sheet: 200ms / ease-out
// ページ遷移: 150ms / ease-in-out
// Skeleton パルス: 1500ms ループ / ease-in-out
```

### 11.5 画像

```tsx
// ✅ Next.js Image コンポーネントを使う
import Image from 'next/image'

// 固定サイズ画像
<Image
  src={product.imageUrl}
  alt={product.name}
  width={400}
  height={400}
  priority={isAboveFold}    // LCP 画像（ファーストビュー）は priority
  className="object-cover"
/>

// ✅ レスポンシブグリッド → sizes 属性で適切なサイズを配信
<Image
  src={product.imageUrl}
  alt={product.name}
  fill
  sizes="(max-width: 768px) 50vw, (max-width: 1280px) 33vw, 25vw"
  className="object-cover"
/>

// ✅ blur placeholder でレイアウトシフトを防ぐ（リモート画像）
<Image
  src={product.imageUrl}
  alt={product.name}
  width={400}
  height={400}
  placeholder="blur"
  blurDataURL="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
/>

// ❌ <img> を直接使う（width/height 省略は layout shift の原因）
<img src={product.imageUrl} />

// ❌ fill 使用時に sizes を省略（最大サイズの画像をダウンロード）
<Image src={product.imageUrl} alt={product.name} fill />
```

**`next.config.ts` ― リモート画像ドメインを許可リストに追加する（必須）:**

```ts
// next.config.ts
const nextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'res.cloudinary.com',  // Cloudinary
        pathname: '/**',
      },
    ],
  },
}
```

### 11.6 フォント最適化

フォントは必ず `next/font` を使う。Google Fonts や `<link>` タグによる外部読み込みは **レイアウトシフトの原因になるため禁止**。  
見出しは `Noto Serif JP`、本文は `Noto Sans JP` を使い、CSS 変数 `--font-heading` / `--font-body` で制御する（MASTER.md §3 準拠）。

```tsx
// src/app/layout.tsx
import { Noto_Serif_JP, Noto_Sans_JP } from 'next/font/google'

const notoSerif = Noto_Serif_JP({
  subsets: ['latin'],
  weight: ['400', '500', '600', '700'],
  variable: '--font-heading',
  display: 'swap',
  preload: false,
})

const notoSans = Noto_Sans_JP({
  subsets: ['latin'],
  weight: ['300', '400', '500', '700'],
  variable: '--font-body',
  display: 'swap',
  preload: false,
})

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ja" className={`${notoSerif.variable} ${notoSans.variable}`}>
      <body className="font-sans">{children}</body>
    </html>
  )
}
```

`app/globals.css` の `@theme inline` で Tailwind トークンにマッピングする：

```css
/* app/globals.css */
@theme inline {
  --font-sans: var(--font-body);     /* 本文 */
  --font-serif: var(--font-heading); /* 見出し */
}
```

使い分け：見出し（h1–h2、ページタイトル）は `font-serif`、本文・ラベル・ボタンは `font-sans` を明示する。

### 11.7 ダークモード

公開画面（`/` `(public)/*`）はライトモード固定。Seller・Admin ダッシュボードはセグメントスコープダークモード対応の拡張性を保つ（Phase 3+）が、**Phase 2 では実装不要**。

**ライトモード固定（現在）:**

```tsx
// ✅ CSS 変数を使った条件分岐不要のスタイリング
<div className="bg-background text-foreground border-border" />
```

**セグメントスコープダークモード（Phase 3+ での拡張用）:**

`globals.css` の `@custom-variant dark (&:is(.dark *))` により、`.dark` クラスを持つ祖先要素配下にダークテーマを適用できる。

```tsx
// src/app/(seller)/layout.tsx — 例（Phase 3+）
export default function SellerLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="dark min-h-screen bg-background text-foreground">
      <SellerSidebar />
      <main>{children}</main>
    </div>
  )
}
```

詳細は `design-system/pages/layout.md §1` を参照。

---

## 12. コメント規約

### 12.1 基本原則

コードが「何をするか」は命名で伝える。コメントは**「なぜその実装にしたか」**を伝えるためにのみ使う。
バックエンドの §14.7 と同じ方針を TypeScript / React に適用する。

**ドキュメントの章番号を引用しない。** `// user-profile.md §3.5 に従う` のような参照は、ドキュメント側の改訂で容易に無効になるうえ、後からコードだけを読む人には何の情報も与えない。参照したくなったら、その章に書かれている**判断の理由そのもの**を 1 行で書く。

```tsx
// ❌ 参照だけ（ドキュメントが変わると嘘になる／読んでも意味がわからない）
// 設定画面の区画（user-profile.md §6）。見出し列幅は MASTER.md §14 に準拠。

// ✅ 理由を書く（コードだけで完結する）
// 見出し列の幅はサイドナビと揃えて、ページ左端から一貫した縦のリズムを作る
```

### 12.2 TSDoc（`/** */`）— 書く対象と書かない対象

| 対象 | 方針 |
|---|---|
| `export` する型・インターフェースのフィールド | **非自明な場合のみ**各フィールドに1行付ける |
| `export` する Custom Hook | 引数・戻り値の意味が関数名だけでは伝わらない場合 |
| `export` するユーティリティ関数 | 副作用・戻り値・引数の制約が非自明な場合 |
| `const` Enum 相当（`src/types/enums.ts`）の各値 | `DATA_DICTIONARY` の論理名を必ず1行付ける |
| コンポーネント関数そのもの | 原則不要（ファイル名・関数名で自明） |
| `'use client'` / `'use server'` ディレクティブ | 不要（Next.js の仕様で自明） |

**型フィールドの例:**

```tsx
interface ProductCardProps {
  product: ProductDto
  /** カードをクリック不可にする（スケルトン表示中など） */
  disabled?: boolean
  /** true にすると LCP 最適化のため priority 画像として配信される */
  priority?: boolean
}
```

**Custom Hook の例:**

```ts
/**
 * 検索クエリをデバウンスし、商品一覧を返す。
 *
 * @param initialQuery 初期検索文字列。未指定の場合は空文字。
 * @returns `products` はデバウンス後のクエリ結果。`isLoading` は fetch 中フラグ。
 */
export function useProductSearch(initialQuery?: string) { ... }
```

**`const` Enum の例（`DATA_DICTIONARY` の論理名を付ける）:**

```ts
// src/types/enums.ts
export const OrderStatus = {
  /** 注文中 */
  PENDING: 'PENDING',
  /** 支払い済み */
  PAID: 'PAID',
  /** 発送済み */
  SHIPPED: 'SHIPPED',
  /** 受取済み */
  DELIVERED: 'DELIVERED',
  /** キャンセル済み */
  CANCELLED: 'CANCELLED',
} as const
```

### 12.3 インラインコメント（`//`）

複雑なビジネスロジック・非自明な技術的制約のみ。「何をするか」ではなく「なぜそうするか」を書く。

```ts
// ✅ ビジネスルールの理由
if (order.status === OrderStatus.SHIPPED) {
  // 出荷後はキャンセル不可（配送業者への取り消し連絡が必要になるため）
  throw new Error('ORDER_NOT_CANCELLABLE')
}

// ✅ フレームワーク固有の非自明な制約
// useSearchParams は Suspense 外だとページ全体が CSR にフォールバックする
const searchParams = useSearchParams()

// ✅ 非自明な技術的判断
// 204 No Content は res.json() を呼ぶと SyntaxError になるため早期リターン
if (res.status === 204) return undefined as T

// ❌ コードを読めばわかる
// カートアイテムの合計を計算する
const total = items.reduce((sum, item) => sum + item.price * item.quantity, 0)
```

### 12.4 JSX コメント（`{/* */}`）

JSX 内では `{/* */}` のみ使用する（§11.1 参照）。  
セクション区切りや技術的な注記に限定して使い、多用しない。

```tsx
return (
  <div>
    {/* ファーストビューの画像は LCP 対象のため priority を設定している */}
    <Image src={imageUrl} alt={name} priority />
    <ProductInfo product={product} />
  </div>
)
```

### 12.5 禁止パターン

| 禁止 | 代替・理由 |
|---|---|
| コードをコメントアウトして残す | 削除する（`git` で復元可能） |
| 変更履歴コメント（`// 2026-xx-xx 修正: ...`） | `git log` / `git blame` が代替 |
| 自明なコメント（`// ローディング中は Skeleton を返す`） | 削除する |
| ドキュメントの章番号引用（`// auth.md §6.3.3 と揃えている`） | 理由そのものを書く。参照は陳腐化する（§12.1）|
| コンポーネントの設計意図を段落で書いた TSDoc | 設計ドキュメント側に置く。コードには非自明な判断のみ 1〜2 行 |
| `/* ... */` ブロックコメント（JSX 外） | `//` を使う |
| `TODO` / `FIXME` をコードに残す | GitHub Issue で管理する |

---

## 13. 実装チェックリスト

### 13.1 コンポーネント作成時

- [ ] Server / Client Component のどちらにすべきか判断した
- [ ] export 方針に従っている（ルートファイルのみ `export default`、他コンポーネントは名前付き export）
- [ ] `'use client'` は葉コンポーネントに限定している
- [ ] Loading / Error / Empty の 3 状態を実装している
- [ ] Skeleton コンポーネントを用意している
- [ ] アイコンボタンに `aria-label` を設定している
- [ ] フォームコントロールに `<label>` または `aria-label` を設定している
- [ ] フォーカスリング（`focus-visible:ring-*`）を設定している
- [ ] `outline-none` を単体で使っていない
- [ ] 画像に `width` / `height` または `fill` を設定している
- [ ] `fill` 使用時に `sizes` 属性を設定している
- [ ] リモート画像のドメインを `next.config.ts` の `remotePatterns` に追加している
- [ ] フォントは `next/font` を使っている（外部 `<link>` 禁止）
- [ ] `transition-all` を使っていない（`transition-transform` 等に限定）
- [ ] `prefers-reduced-motion` に対応している（`motion-reduce:transition-none`）

### 13.2 ページ作成時

- [ ] `generateMetadata` を実装している（SEO 対象ページ）
- [ ] `params` を `await` している（`const { id } = await params`）
- [ ] `loading.tsx` または `<Suspense>` で Skeleton を表示している
- [ ] `error.tsx` でエラーバウンダリを設定している
- [ ] 認証が必要なページは `proxy.ts` のガードパスに追加している
- [ ] `useSearchParams` を使うコンポーネントを `<Suspense>` でラップしている
- [ ] URL にフィルター・ページ番号等の状態を反映している（`useSearchParams`）
- [ ] Server Actions 内で `redirect()` / `notFound()` を try-catch で囲んでいない

### 13.3 データ取得時

- [ ] Server Component では `lib/api/server/` の関数を使っている
- [ ] Client Component からの API 呼び出しは TanStack Query を経由している
- [ ] `useQuery` / `useMutation` を `hooks/queries/` `hooks/mutations/` の Custom Hook に切り出している
- [ ] Query Key は `queryKeys.ts` から参照している
- [ ] 並列取得可能なデータは `Promise.all` / 並列 `useQuery` にしている
- [ ] `invalidateQueries` をミューテーション Hook 側に持たせている（呼び出し側に散らばっていない）
- [ ] API エラーは `ApiError` として型安全にハンドリングしている

### 13.4 フォーム実装時

- [ ] Zod スキーマを `lib/validations/` に定義している
- [ ] `autoComplete` 属性を設定している
- [ ] メール・コード入力に `spellCheck={false}` を設定している
- [ ] バリデーションエラーをインライン表示している
- [ ] 送信中はボタンを `disabled` にしている
- [ ] 送信中はボタンにローディングスピナーを表示している

### 13.5 型定義時

- [ ] `any` を使っていない
- [ ] API レスポンス型は `src/types/api/` に定義している
- [ ] Union 型のエラーコードは `docs/design/ERROR_CODES.md` と一致している
- [ ] フォームの値型は `z.infer<typeof schema>` から生成している
- [ ] バックエンドの Enum に対応する値は `const + type` パターンで `src/types/enums.ts` に定義している
- [ ] TypeScript の `enum` キーワードを使っていない（`const + type` パターンに統一）
- [ ] UI 表示用ラベルは `enums.ts` ではなく `lib/constants.ts` に分離している

### 13.6 品質確認（各画面完成時）

- [ ] モバイル（375px）でレイアウト崩れがない
- [ ] デスクトップ（1280px）でレイアウト崩れがない
- [ ] 色に依存しない状態表現になっている（アクティブ・エラー・デフォルト等を色だけで示していない）
- [ ] キーボードのみで全操作が可能
- [ ] `pnpm lint` / `pnpm typecheck` / `pnpm test` / `pnpm build` がすべて成功する

### 13.7 コメント確認

- [ ] コメントが「なぜ」を説明している（「何をするか」は書いていない）
- [ ] ドキュメントの章番号を引用していない（`user-profile.md §3.5` のような参照を書いていない）
- [ ] コンポーネント関数に説明的な TSDoc を付けていない（非自明な設計判断のみ 1〜2 行）
- [ ] コメントアウトしたコードが残っていない
- [ ] 変更履歴コメント（`// 2026-xx-xx 修正: ...`）が含まれていない
- [ ] `export` する型の非自明フィールドに TSDoc を付けている
- [ ] `const` Enum の各値に `DATA_DICTIONARY` の論理名（`/** */`）を付けている
