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
12. [実装チェックリスト](#12-実装チェックリスト)

> **Next.js 16 移行ポイント:** `middleware.ts` → `proxy.ts`、`params` / `searchParams` / `cookies()` / `headers()` が非同期 (`Promise`) に変更。

---

## 1. 全体アーキテクチャ

### 1.1 ディレクトリ構成

```
kivio-frontend/
├── src/
│   ├── app/                        # Next.js App Router（ルーティング専用）
│   │   ├── (auth)/                 # 認証系レイアウトグループ
│   │   │   ├── layout.tsx
│   │   │   ├── login/page.tsx
│   │   │   └── register/page.tsx
│   │   ├── (buyer)/                # バイヤー系レイアウトグループ
│   │   │   ├── layout.tsx
│   │   │   ├── cart/page.tsx
│   │   │   ├── checkout/page.tsx
│   │   │   ├── orders/[id]/page.tsx
│   │   │   └── wishlist/page.tsx
│   │   ├── (seller)/               # セラー系レイアウトグループ
│   │   │   ├── layout.tsx          # SellerSidebar を含む
│   │   │   ├── seller/dashboard/page.tsx
│   │   │   ├── seller/products/
│   │   │   │   ├── page.tsx
│   │   │   │   ├── new/page.tsx
│   │   │   │   └── [id]/edit/page.tsx
│   │   │   └── seller/orders/page.tsx
│   │   ├── (admin)/                # 管理者系レイアウトグループ
│   │   │   ├── layout.tsx
│   │   │   ├── admin/users/page.tsx
│   │   │   └── admin/seller-applications/page.tsx
│   │   ├── products/[id]/page.tsx
│   │   ├── shops/[id]/page.tsx
│   │   ├── search/page.tsx
│   │   ├── layout.tsx              # ルートレイアウト（ThemeProvider・Navbar・Footer）
│   │   ├── page.tsx                # ホーム
│   │   ├── loading.tsx
│   │   ├── error.tsx
│   │   ├── not-found.tsx
│   │   └── global-error.tsx
│   ├── components/
│   │   ├── layout/                 # Navbar, Footer, SellerSidebar
│   │   ├── ui/                     # shadcn/ui ラッパー + 共通 UI（EmptyState, ErrorFallback）
│   │   ├── product/                # ProductCard, ProductGrid, ProductForm
│   │   ├── order/                  # OrderCard, OrderSummary
│   │   ├── cart/                   # CartItem, CartSummary
│   │   ├── auth/                   # LoginForm, RegisterForm
│   │   └── seller/                 # SellerStatsCard, ApplicationForm
│   ├── hooks/                      # Custom Hooks（use プレフィックス必須）
│   ├── lib/
│   │   ├── api/                    # API クライアント関数（Server/Client 別）
│   │   ├── validations/            # Zod スキーマ
│   │   ├── utils.ts                # cn() など汎用ユーティリティ
│   │   └── constants.ts            # 定数（ROUTES, API_BASE_URL 等）
│   ├── stores/                     # Zustand ストア
│   ├── types/                      # TypeScript 型定義（API レスポンス・ドメイン型）
│   └── proxy.ts                    # 認証ガード（Next.js 16: middleware.ts → proxy.ts）
├── public/
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── components.json                 # shadcn/ui 設定
```

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
export default function AddToCartButton({ productId }: { productId: string }) {
  const [isPending, startTransition] = useTransition()
  // ...
}

// ❌ 悪い例: ページ全体を 'use client' にする
'use client'
export default function ProductPage() { /* 全体がクライアントバンドルに入る */ }
```

### 2.3 API クライアント（Client 用）

Client Component からの API 呼び出しは `src/lib/api/client/` に集約する。  
401 はグローバルインターセプトで `/auth/login` にリダイレクトする。

```ts
// src/lib/api/client/base.ts
'use client'
import { redirect } from 'next/navigation'

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    credentials: 'include',
  })
  if (res.status === 401) redirect('/auth/login')
  if (!res.ok) {
    const error = await res.json()
    throw new ApiError(error.title, error.status, error.detail, error.errorCode)
  }
  return res.json()
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
| コンポーネント関数 | `PascalCase` | `export default function ProductCard` |
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

export default function ProductGrid({ products, isLoading, error }: ProductGridProps) {
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
export default function ProductDetailClient({ product }: { product: ProductDto }) {
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

export default function WishlistButton({ productId, initialLiked }: WishlistButtonProps) {
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
export default function SearchBar() {
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
  seller: {
    stats: ['seller', 'stats'] as const,
    products: (params?: SellerProductListParams) => ['seller', 'products', params] as const,
    orders: (params?: OrderListParams) => ['seller', 'orders', params] as const,
  },
} as const
```

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

```ts
// src/hooks/mutations/useAddToCartMutation.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/lib/queryKeys'

export function useAddToCartMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (item: AddToCartRequest) => addToCartApi(item),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.cart.detail })
    },
    onError: (error: ApiError) => {
      toast.error(getErrorMessage(error.errorCode))
    },
  })
}
```

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
export default function ProductDetailClient({ initialProduct, productId }) {
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
│   └── common.ts     # PageResponse<T>, ProblemDetail, ApiErrorCode
├── domain/           # フロントエンドのドメインモデル（API 型を加工したもの）
│   └── cart.ts
└── index.ts          # re-export
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

```ts
// src/types/api/common.ts
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
}

export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  instance: string
  errorCode: ApiErrorCode
}

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

### 9.5 `any` / `unknown` 使用禁止

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

```ts
// src/lib/validations/auth.ts
import { z } from 'zod'

export const loginSchema = z.object({
  email: z.string().email('有効なメールアドレスを入力してください'),
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

export default function LoginForm() {
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

- コンポーネントは `function` 宣言 + `export default` を使う（アロー関数の default export 禁止）
- ファイル 1 つにつき 1 コンポーネントを export する（名前付き export の複数公開は可）
- JSX 内のコメントは `{/* */}` のみ使用する

```tsx
// ✅
export default function ProductCard({ product }: ProductCardProps) {
  return <div>{product.name}</div>
}

// ❌
const ProductCard = ({ product }) => <div>{product.name}</div>
export default ProductCard
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

```tsx
// src/app/layout.tsx
import { Noto_Sans_JP, Inter } from 'next/font/google'

const notoSansJP = Noto_Sans_JP({
  subsets: ['latin'],
  weight: ['400', '500', '700'],
  variable: '--font-noto-sans-jp',
  display: 'swap',
})

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
})

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ja" className={`${notoSansJP.variable} ${inter.variable}`}>
      <body className="font-sans">{children}</body>
    </html>
  )
}
```

```ts
// tailwind.config.ts
theme: {
  extend: {
    fontFamily: {
      sans: ['var(--font-noto-sans-jp)', 'var(--font-inter)', 'sans-serif'],
    },
  },
}
```

### 11.7 ダークモード

```tsx
// ✅ CSS 変数を使った条件分岐不要のスタイリング
<div className="bg-background text-foreground border-border" />

// ✅ ダークモード専用スタイルが必要な場合
<div className="bg-white dark:bg-zinc-900" />

// tailwind.config.ts: darkMode: ['class']
// ThemeProvider の設定で next-themes を使用
```

---

## 12. 実装チェックリスト

### 12.1 コンポーネント作成時

- [ ] Server / Client Component のどちらにすべきか判断した
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

### 12.2 ページ作成時

- [ ] `generateMetadata` を実装している（SEO 対象ページ）
- [ ] `params` を `await` している（`const { id } = await params`）
- [ ] `loading.tsx` または `<Suspense>` で Skeleton を表示している
- [ ] `error.tsx` でエラーバウンダリを設定している
- [ ] 認証が必要なページは `proxy.ts` のガードパスに追加している
- [ ] `useSearchParams` を使うコンポーネントを `<Suspense>` でラップしている
- [ ] URL にフィルター・ページ番号等の状態を反映している（`useSearchParams`）
- [ ] Server Actions 内で `redirect()` / `notFound()` を try-catch で囲んでいない

### 12.3 データ取得時

- [ ] Server Component では `lib/api/server/` の関数を使っている
- [ ] Client Component からの API 呼び出しは TanStack Query を経由している
- [ ] Query Key は `queryKeys.ts` から参照している
- [ ] 並列取得可能なデータは `Promise.all` / 並列 `useQuery` にしている
- [ ] Mutation 後に関連 Query を `invalidateQueries` している
- [ ] API エラーは `ApiError` として型安全にハンドリングしている

### 12.4 フォーム実装時

- [ ] Zod スキーマを `lib/validations/` に定義している
- [ ] `autoComplete` 属性を設定している
- [ ] メール・コード入力に `spellCheck={false}` を設定している
- [ ] バリデーションエラーをインライン表示している
- [ ] 送信中はボタンを `disabled` にしている
- [ ] 送信中はボタンにローディングスピナーを表示している

### 12.5 型定義時

- [ ] `any` を使っていない
- [ ] API レスポンス型は `src/types/api/` に定義している
- [ ] Union 型のエラーコードは `docs/design/ERROR_CODES.md` と一致している
- [ ] フォームの値型は `z.infer<typeof schema>` から生成している

### 12.6 品質確認（各画面完成時）

- [ ] モバイル（375px）でレイアウト崩れがない
- [ ] デスクトップ（1280px）でレイアウト崩れがない
- [ ] ダークモードで色・コントラストの問題がない
- [ ] キーボードのみで全操作が可能
- [ ] `pnpm build` がエラー・型エラーなしで成功する
- [ ] `pnpm lint` がエラーなしで成功する
