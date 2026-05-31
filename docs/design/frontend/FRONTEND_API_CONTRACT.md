# FRONTEND API コントラクト
# マルチベンダー型マーケットプレイス「Kivio」

**作成日：** 2026年5月31日  
**対象スタック：** Next.js App Router + TanStack Query v5 + Axios + Zustand  
**参照：** `docs/design/API_DESIGN.md`、`docs/design/ERROR_CODES.md`、`docs/design/frontend/FRONTEND_IA.md`

---

## 目次

1. [このドキュメントについて](#1-このドキュメントについて)
2. [型定義](#2-型定義)
3. [API クライアント設計](#3-api-クライアント設計)
4. [認証 & セッション管理](#4-認証--セッション管理)
5. [TanStack Query キャッシュキー設計](#5-tanstack-query-キャッシュキー設計)
6. [画面 × API 対応表](#6-画面--api-対応表)
7. [並列フェッチ・データ依存設計](#7-並列フェッチデータ依存設計)
8. [WebSocket / STOMP 統合](#8-websocket--stomp-統合)
9. [エラーコード → UI 表示方針](#9-エラーコード--ui-表示方針)
10. [フォームバリデーションエラーのマッピング](#10-フォームバリデーションエラーのマッピング)
11. [環境変数](#11-環境変数)

---

## 1. このドキュメントについて

### 1.1 目的

`docs/design/API_DESIGN.md`（バックエンド視点の仕様）に対して、**フロントエンドが何を・どのように使うか**を定義するコントラクト文書。バックエンドとフロントエンドが並行開発を進める際の共通仕様書として機能する。

`API_DESIGN.md` とのスコープ分担:

| ドキュメント | 記述内容 |
|---|---|
| `API_DESIGN.md` | バックエンドの全エンドポイント仕様（リクエスト・レスポンス・エラー） |
| 本ドキュメント | フロントエンドの実装視点（画面 × API 対応表・並列フェッチ・エラーUI・キャッシュ設計） |

### 1.2 API 基本情報

| 項目 | 値 |
|---|---|
| ベース URL | `NEXT_PUBLIC_API_URL + /api/v1` |
| 認証方式 | `Authorization: Bearer <accessToken>` |
| 日時フォーマット | ISO 8601 UTC: `2026-05-24T10:00:00Z` |
| 金額フォーマット | 整数（円単位）: `8500` = ¥8,500 |
| エラー形式 | RFC 9457 `ProblemDetail`（`Content-Type: application/problem+json`） |
| ページネーション | `page`（0始まり）/ `size`（デフォルト20）→ `PageResponse<T>` |
| WebSocket | `NEXT_PUBLIC_WS_URL + /ws?token=<accessToken>` |

---

## 2. 型定義

`src/types/api/` 配下に分割して配置する（共通型・エラー型は `src/types/api/common.ts`、認証型は `src/types/api/auth.ts`）。バックエンドの Enum に対応する型は `src/types/enums.ts` で `const + type` パターンで定義する（CODING_STANDARDS §9.1, §9.6）。

### 2.1 共通型

```typescript
// src/types/api/common.ts
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number               // Spring Data の命名に準拠（0始まり）
  first: boolean
  last: boolean
}

export interface ApiError {
  type: string
  title: string
  status: number
  errorCode: string             // UPPER_SNAKE_CASE — UI 分岐に使用（CODING_STANDARDS §9.3）
  detail: string
  instance: string
  errors?: ValidationFieldError[]
}

export interface ValidationFieldError {
  field: string
  message: string
  rejectedValue?: unknown       // password 等の機密フィールドは省略
}
```

バックエンドの Enum 値と対応する型は `src/types/enums.ts` で `const + type` パターンで定義する。`enum` キーワードは使用しない（CODING_STANDARDS §9.6）。

```typescript
// src/types/enums.ts

export const UserRole = {
  BUYER:  'ROLE_BUYER',
  SELLER: 'ROLE_SELLER',
  ADMIN:  'ROLE_ADMIN',
} as const
export type UserRole = (typeof UserRole)[keyof typeof UserRole]

export const OrderStatus = {
  PENDING_PAYMENT:   'PENDING_PAYMENT',
  PAYMENT_CONFIRMED: 'PAYMENT_CONFIRMED',
  PROCESSING:        'PROCESSING',
  SHIPPED:           'SHIPPED',
  DELIVERED:         'DELIVERED',
  COMPLETED:         'COMPLETED',
  CANCELLED:         'CANCELLED',
} as const
export type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus]

export const ProductStatus = {
  DRAFT:    'DRAFT',
  ACTIVE:   'ACTIVE',
  INACTIVE: 'INACTIVE',
  DELETED:  'DELETED',
} as const
export type ProductStatus = (typeof ProductStatus)[keyof typeof ProductStatus]

export const ApplicationStatus = {
  PENDING:  'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
} as const
export type ApplicationStatus = (typeof ApplicationStatus)[keyof typeof ApplicationStatus]

export const ShippingType = {
  FREE:             'FREE',
  FIXED:            'FIXED',
  CONDITIONAL_FREE: 'CONDITIONAL_FREE',
} as const
export type ShippingType = (typeof ShippingType)[keyof typeof ShippingType]

export const NotificationType = {
  ORDER_CONFIRMED:             'ORDER_CONFIRMED',
  ORDER_STATUS_CHANGED:        'ORDER_STATUS_CHANGED',
  ORDER_CANCELLED:             'ORDER_CANCELLED',
  NEW_MESSAGE:                 'NEW_MESSAGE',
  SELLER_APPLICATION_APPROVED: 'SELLER_APPLICATION_APPROVED',
  SELLER_APPLICATION_REJECTED: 'SELLER_APPLICATION_REJECTED',
} as const
export type NotificationType = (typeof NotificationType)[keyof typeof NotificationType]
```

### 2.2 認証型

```typescript
// POST /auth/login, /auth/google, /auth/verify-email のレスポンス
export interface AuthTokens {
  accessToken: string
  refreshToken: string          // 上記3エンドポイントのみ返す
  tokenType: 'Bearer'
  expiresIn: number             // 秒 (900 = 15分)
}

// POST /auth/refresh のレスポンス（refreshToken は返らない）
export interface RefreshResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export interface AuthUser {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
  role: UserRole
  status: 'ACTIVE' | 'INACTIVE'
  emailVerified: boolean
  createdAt: string
}
```

---

## 3. API クライアント設計

`src/lib/api/client/base.ts` に実装する（CODING_STANDARDS §1.1, §2.3）。

### 3.1 Axios インスタンス

```typescript
import axios, { AxiosInstance } from 'axios'

export const apiClient: AxiosInstance = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL + '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10_000,
})
```

### 3.2 認証インターセプター（リクエスト）

```typescript
apiClient.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().accessToken
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})
```

### 3.3 トークンリフレッシュインターセプター（レスポンス）

```typescript
let isRefreshing = false
let refreshQueue: Array<(token: string) => void> = []

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    const errorCode: string | undefined = error.response?.data?.errorCode

    // TOKEN_INVALID は即ログアウト。TOKEN_EXPIRED のみリフレッシュを試みる
    if (errorCode === 'TOKEN_EXPIRED' && !originalRequest._retried) {
      originalRequest._retried = true

      if (isRefreshing) {
        return new Promise((resolve) => {
          refreshQueue.push((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(apiClient(originalRequest))
          })
        })
      }

      isRefreshing = true
      try {
        const refreshToken = useAuthStore.getState().refreshToken
        const { data } = await axios.post(
          `${process.env.NEXT_PUBLIC_API_URL}/api/v1/auth/refresh`,
          { refreshToken }
        )
        useAuthStore.getState().setAccessToken(data.accessToken)
        refreshQueue.forEach((cb) => cb(data.accessToken))
        refreshQueue = []
        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
        return apiClient(originalRequest)
      } catch {
        useAuthStore.getState().clearAuth()
        // lib 層から router は呼べないため、カスタムイベントで layout 層へ通知
        window.dispatchEvent(new CustomEvent('auth:logout'))
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)
```

### 3.4 エラーユーティリティ

```typescript
export function isApiError(
  error: unknown
): error is { response: { data: ApiError } } {
  return (
    axios.isAxiosError(error) &&
    typeof error.response?.data?.errorCode === 'string'
  )
}

export function getApiErrorCode(error: unknown): string | null {
  return isApiError(error) ? error.response.data.errorCode : null
}

export function getValidationErrors(error: unknown): ValidationFieldError[] {
  if (isApiError(error) && error.response.data.errors) {
    return error.response.data.errors
  }
  return []
}
```

---

## 4. 認証 & セッション管理

### 4.1 トークン保存方式

| トークン | 保存場所 | 理由 |
|---|---|---|
| `accessToken` | Zustand（in-memory） | XSS への露出を最小化。ページリフレッシュ時は Refresh Token で自動再取得 |
| `refreshToken` | `localStorage` | ページリフレッシュ時の復元に使用。Phase 2 では localStorage を採用 |

> **Phase 5+ の改善案:** Next.js API Route を Proxy として挟み、Refresh Token を `httpOnly` Cookie で管理することで XSS リスクを排除できる。

### 4.2 Zustand authStore インターフェース

`src/stores/useAuthStore.ts` に実装する（CODING_STANDARDS §5.1）。

```typescript
interface AuthState {
  accessToken: string | null
  refreshToken: string | null   // localStorage と常に同期
  user: AuthUser | null
  isLoading: boolean            // 起動時のトークン復元処理中フラグ。初期値 true

  // isAuthenticated は保存しない — user !== null で判定する
  // （保存すると clearAuth 忘れ等でフラグが stale になるリスクがある）
  get isAuthenticated(): boolean  // = user !== null && accessToken !== null

  setTokens: (tokens: AuthTokens) => void
  setAccessToken: (token: string) => void
  setUser: (user: AuthUser) => void
  setLoading: (loading: boolean) => void
  clearAuth: () => void
}
```

`setTokens` 実装時: `localStorage.setItem('refreshToken', tokens.refreshToken)` を忘れずに実行する。  
`clearAuth` 実装時: `localStorage.removeItem('refreshToken')` でストレージも同時に削除する。  
`isLoading` は初期値 `true` にする。`AuthProvider` の復元処理が完了した時点で `false` にセットする。

### 4.3 アプリ起動時の自動トークン復元

`src/components/providers/auth-provider.tsx` に実装する。`RootLayout` の Provider として配置。

```typescript
useEffect(() => {
  const storedRefreshToken = localStorage.getItem('refreshToken')
  if (!storedRefreshToken) {
    useAuthStore.getState().setLoading(false)
    return
  }

  apiClient
    .post('/auth/refresh', { refreshToken: storedRefreshToken })
    .then(({ data }) => {
      useAuthStore.getState().setAccessToken(data.accessToken)
      return apiClient.get<AuthUser>('/users/me')
    })
    .then(({ data }) => {
      useAuthStore.getState().setUser(data)
    })
    .catch(() => {
      localStorage.removeItem('refreshToken')
      useAuthStore.getState().clearAuth()
    })
    .finally(() => {
      useAuthStore.getState().setLoading(false)
    })
}, [])
```

### 4.4 ログイン後のロール別リダイレクト

```typescript
function redirectByRole(role: UserRole, router: AppRouterInstance) {
  if (role === UserRole.SELLER) router.replace('/seller/dashboard')
  else if (role === UserRole.ADMIN) router.replace('/admin/dashboard')
  else router.replace('/')
}
```

### 4.5 ログアウト

```typescript
async function logout(router: AppRouterInstance) {
  const refreshToken = localStorage.getItem('refreshToken')
  if (refreshToken) {
    // 失敗してもクライアント側はログアウトするため catch で無視
    await apiClient.post('/auth/logout', { refreshToken }).catch(() => {})
  }
  useAuthStore.getState().clearAuth()  // localStorage も同時クリア
  router.push('/auth/login')
}
```

---

## 5. TanStack Query キャッシュキー設計

`src/lib/queryKeys.ts` にファクトリーパターンで定義する（CODING_STANDARDS §8.1）。

```typescript
export const queryKeys = {
  // 認証・ユーザー
  currentUser:         ['users', 'me'] as const,
  addresses:           ['users', 'me', 'addresses'] as const,
  sellerApplication:   ['seller-applications', 'me'] as const,

  // カテゴリー（静的・Infinity staleTime）
  categories:          ['categories'] as const,

  // 商品
  products: {
    list:    (params: Record<string, unknown>) => ['products', 'list', params] as const,
    detail:  (id: string)                       => ['products', 'detail', id] as const,
    reviews: (id: string, page?: number)        => ['products', 'detail', id, 'reviews', page] as const,
  },

  // ショップ
  shops: {
    list:   (params?: Record<string, unknown>) => ['shops', 'list', params] as const,
    detail: (id: string)                        => ['shops', 'detail', id] as const,
    me:     ['shops', 'me'] as const,
  },

  // カート（常に最新）
  cart: {
    detail: ['cart'] as const,
  },

  // 注文
  orders: {
    list:       (params?: Record<string, unknown>) => ['orders', 'list', params] as const,
    detail:     (id: string)                        => ['orders', 'detail', id] as const,
    sellerList: (params?: Record<string, unknown>) => ['orders', 'seller', 'list', params] as const,
  },

  // セラーダッシュボード
  sellerDashboard: (params?: { year?: number; month?: number }) =>
    ['seller', 'dashboard', params] as const,

  // チャット
  chatRooms: {
    list:   ['chat-rooms'] as const,
    detail: (id: string) => ['chat-rooms', 'detail', id] as const,
  },

  // 通知
  notifications: (params?: Record<string, unknown>) => ['notifications', params] as const,

  // お気に入り
  wishlist: (params?: Record<string, unknown>) => ['wishlist', params] as const,

  // 管理者
  admin: {
    sellerApplications: (p?: Record<string, unknown>) => ['admin', 'seller-applications', p] as const,
    users:              (p?: Record<string, unknown>) => ['admin', 'users', p] as const,
    products:           (p?: Record<string, unknown>) => ['admin', 'products', p] as const,
    categories:         ['admin', 'categories'] as const,
    platformConfigs:    ['admin', 'platform-configs'] as const,
    dashboard:          ['admin', 'dashboard'] as const,
  },
}
```

### 5.1 staleTime 指針

| データ種別 | staleTime | 理由 |
|---|---|---|
| カテゴリー | `Infinity` | 変更頻度がきわめて低い。アプリ起動時に1回取得で十分 |
| 商品一覧 | `60_000`（1分） | 検索結果はやや古くても許容 |
| 商品詳細 | `30_000`（30秒） | 在庫数の新鮮さが購入判断に影響する |
| カート | `0` | チェックアウト前は常に最新が必要 |
| 注文詳細 | `0` | WebSocket で更新が来るため |
| 通知 | `30_000` | WebSocket と組み合わせて使用 |
| セラーダッシュボード | `60_000` | 分単位の更新で十分 |
| ショップ詳細 | `120_000`（2分） | 変更頻度が低い |
| ユーザー情報 | `300_000`（5分） | ログイン直後以外は変更が少ない |

### 5.2 Mutation 後の Invalidation 指針

TanStack Query では部分キー一致による一括 invalidation が可能。リスト系は `['orders', 'list']` 等のプレフィックスで全パラメーターバリアントを無効化できる。

| Mutation | Invalidate するキー |
|---|---|
| `PATCH /users/me` | `['users', 'me']` |
| `POST /users/me/addresses` | `['users', 'me', 'addresses']` |
| `PATCH /users/me/addresses/{id}` | `['users', 'me', 'addresses']` |
| `DELETE /users/me/addresses/{id}` | `['users', 'me', 'addresses']` |
| `POST /seller-applications` | `['seller-applications', 'me']` |
| `POST /products` | `['products', 'list']` |
| `PATCH /products/{id}` | `['products', 'detail', id]`（detail）、`['products', 'list']` |
| `DELETE /products/{id}` | `['products', 'list']` |
| `POST /products/{id}/images` | `['products', 'detail', id]` |
| `DELETE /products/{id}/images/{imageId}` | `['products', 'detail', id]` |
| `PATCH /shops/me` | `['shops', 'me']` |
| `PATCH /shops/me/shipping-policy` | `['shops', 'me']` |
| `POST /cart/items` | `['cart']` |
| `PATCH /cart/items/{id}` | `['cart']` |
| `DELETE /cart/items/{id}` | `['cart']` |
| `DELETE /cart` | `['cart']` |
| `PATCH /orders/{id}/status` | `['orders', 'detail', id]`（detail）、`['orders', 'seller', 'list']` |
| `POST /orders/{id}/cancel` | `['orders', 'detail', id]`（detail）、`['orders', 'list']` |
| `PATCH /notifications/{id}/read` | `['notifications']` |
| `PATCH /notifications/read-all` | `['notifications']` |
| `POST /wishlist` | `['wishlist']` |
| `DELETE /wishlist/{productId}` | `['wishlist']` |
| `POST /chat-rooms` | `['chat-rooms']` |
| `POST /admin/seller-applications/{id}/approve` | `['admin', 'seller-applications']` |
| `POST /admin/seller-applications/{id}/reject` | `['admin', 'seller-applications']` |
| `POST /admin/categories` | `['admin', 'categories']`、`['categories']`（公開用も） |
| `PATCH /admin/categories/{id}` | `['admin', 'categories']`、`['categories']` |
| `DELETE /admin/categories/{id}` | `['admin', 'categories']`、`['categories']` |
| `PATCH /admin/platform-configs/{key}` | `['admin', 'platform-configs']` |
| `PATCH /admin/users/{id}/status` | `['admin', 'users']` |
| `PATCH /admin/products/{id}/status` | `['admin', 'products']` |

---

## 6. 画面 × API 対応表

---

### Phase 2（認証・プロフィール・セラー・商品 CRUD）

#### 6.1 認証画面

| 画面 | パス | API コール | 必要フィールド | 備考 |
|---|---|---|---|---|
| ログイン | `/auth/login` | `POST /auth/login` | `accessToken`, `refreshToken`, `expiresIn` | ロール別リダイレクト（§4.4） |
| | | `POST /auth/google` | 同上 | Google ID Token を `idToken` に詰めて送信 |
| 新規登録 Step1 | `/auth/register` | `POST /auth/check-email` | `available: boolean` | onBlur で呼び出し。`EMAIL_ALREADY_REGISTERED` でインラインエラー |
| 新規登録 Step2 | `/auth/register` | `POST /auth/register` | `id`, `email`, `role` | 201 → 確認メール送信済み画面へ遷移 |
| メール認証 | `/auth/verify-email` | `POST /auth/verify-email` | `accessToken`, `refreshToken` | URL `?token=` を Body に詰め替えて送信（サーバーログ露出防止）。成功後 `router.replace('/')` |

#### 6.2 プロフィール設定（`/profile/settings`）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /users/me` | 初期値でフォームを埋める | `displayName`, `avatarUrl`, `email`, `role`, `emailVerified` |
| `PATCH /users/me` | 表示名・アバター URL 更新 | `displayName?`, `avatarUrl?` |
| `PATCH /users/me/password` | パスワード変更 | `currentPassword`, `newPassword` |
| `DELETE /users/me` | 退会 | 204 → `clearAuth()` → `/` |

並列フェッチ: `GET /users/me` のみ。

#### 6.3 セラー申請（`/seller/applications/new`）

| API コール | 用途 | レスポンスと UI |
|---|---|---|
| `GET /seller-applications/me` | 申請履歴確認（マウント時） | 404 → 申請フォーム表示 |
| | | `PENDING` → 審査中 UI（再申請ボタン非表示） |
| | | `REJECTED` → 却下理由 + 再申請フォーム |
| | | `APPROVED` → `/seller/dashboard` へリダイレクト |
| `POST /seller-applications` | 申請送信 | `reason`（1000文字以内）。201 → PENDING UI へ |

#### 6.4 セラーダッシュボード（`/seller/dashboard`）

| API コール | 用途 | 必要フィールド | 並列可否 |
|---|---|---|---|
| `GET /seller/dashboard` | KPI・日別売上・人気商品 | `summary.*`, `recentOrders`, `dailyRevenue`, `topProducts`, `unreadMessageCount` | ✅ |
| `GET /orders?size=10` | 直近注文（Phase 2 はシンプル表示） | `id`, `status`, `totalAmount`, `createdAt` | ✅ |

#### 6.5 商品管理（`/seller/products`）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /shops/me` | 自ショップ ID の取得 | `id`（以降の products クエリに使用） |
| `GET /products?shopId={shop.id}` | 商品一覧（自店舗） | `id`, `name`, `price`, `status`, `stockQuantity`, `thumbnailUrl` |
| `PATCH /products/{id}` | ステータス変更（ACTIVE/INACTIVE） | `status` |
| `DELETE /products/{id}` | 論理削除 | 204 → 一覧を invalidate |

**shopId の取得戦略:** `GET /shops/me` を dependent query として先行実行し、返却された `shop.id` を `GET /products` の `shopId` パラメータに渡す。  
TanStack Query の `enabled: !!shop?.id` を使って、shop 取得完了後に products を自動フェッチする。ページ内での逐次実行なので UI のウォーターフォールは発生しない（両クエリともサーバー起動後の最初のアクセス以外はキャッシュヒットする）。

#### 6.6 商品登録（`/seller/products/new`）

| API コール | 用途 | 必要フィールド | 順序 |
|---|---|---|---|
| `GET /categories` | カテゴリーセレクト | `id`, `name`, `children[]` | 並列（先行取得） |
| `POST /products` | 商品メタデータ登録（DRAFT） | `name`, `description`, `price`, `stockQuantity`, `categoryId?`, `status` | ① |
| `POST /products/{id}/images` | 画像アップロード（最大5枚） | `file`（multipart） | ②（product.id が必要） |

画像アップロードは商品 ID 取得後に実行する（逐次）。複数枚の画像は `Promise.all` で並列アップロード可。

#### 6.7 商品編集（`/seller/products/[id]/edit`）

| API コール | 用途 | 並列可否 |
|---|---|---|
| `GET /products/{id}` | 現在値でフォームを初期化 | ✅ |
| `GET /categories` | カテゴリーセレクト | ✅ |
| `PATCH /products/{id}` | 変更フィールドのみ送信 | — |
| `POST /products/{id}/images` | 画像追加（最大5枚まで） | — |
| `DELETE /products/{id}/images/{imageId}` | 画像削除 | — |

#### 6.8 ショップ設定（`/seller/shop/settings`）

| API コール | 用途 |
|---|---|
| `GET /shops/me` | 初期値取得 |
| `PATCH /shops/me` | 基本情報更新（`name?`, `description?`, `logoUrl?`） |
| `PATCH /shops/me/shipping-policy` | 配送ポリシー更新 |

`shippingType` と `fixedFee`/`freeThreshold` の組み合わせバリデーションはフロントでも実施する:
- `FREE` → `fixedFee` / `freeThreshold` は送信しない
- `FIXED` → `fixedFee` 必須
- `CONDITIONAL_FREE` → `freeThreshold` 必須

#### 6.9 公開画面（Phase 2）

| 画面 | パス | API コール | 並列可否 |
|---|---|---|---|
| ホーム | `/` | `GET /products?sort=createdAt,desc&size=20` | ✅ |
| | | `GET /categories` | ✅ |
| 商品詳細 | `/products/[id]` | `GET /products/{id}` | ✅ |
| | | `GET /products/{id}/reviews?page=0&size=20` | ✅ |
| ショップ詳細 | `/shops/[id]` | `GET /shops/{id}` | ✅ |
| | | `GET /products?shopId={id}&sort=createdAt,desc` | ✅ |

#### 6.10 管理者：セラー申請管理（`/admin/seller-applications`）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /admin/seller-applications?status=PENDING` | 申請一覧（初期ロード） | `applicant.*`, `reason`, `status`, `createdAt` |
| `GET /admin/seller-applications?status=APPROVED` | フィルター切り替え | 同上 |
| `GET /admin/seller-applications?status=REJECTED` | フィルター切り替え | 同上 |
| `POST /admin/seller-applications/{id}/approve` | 承認 | `comment?` |
| `POST /admin/seller-applications/{id}/reject` | 却下 | `comment`（必須） |

---

### Phase 3（検索・カート・決済・注文管理）

#### 6.11 商品検索（`/search`）

| API コール | クエリパラメータ | 並列可否 |
|---|---|---|
| `GET /products` | `q`, `categoryId`, `minPrice`, `maxPrice`, `inStock`, `sort`, `page`, `size` | ✅ |
| `GET /categories` | — | ✅（フィルターパネル用。Infinity staleTime） |

URL クエリ文字列を `useSearchParams()` で読み取り、TanStack Query のキーに渡す。フィルター変更時は `router.push` でURLを更新 → クエリキーが変わり自動再フェッチ。

#### 6.12 カート（`/cart`）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /cart` | カート取得 | `items[].product.*`, `items[].quantity`, `items[].subtotal`, `items[].isAvailable`, `items[].unavailableReason`, `totalAmount` |
| `POST /cart/items` | 商品追加 | `productId`, `quantity` |
| `PATCH /cart/items/{itemId}` | 数量変更 | `quantity` |
| `DELETE /cart/items/{itemId}` | 商品削除 | — |
| `DELETE /cart` | 全クリア | — |

`isAvailable = false` の `unavailableReason` → UI の警告バナーに使用:
- `OUT_OF_STOCK` → 「在庫切れです」
- `PRODUCT_DELETED` / `PRODUCT_INACTIVE` → 「この商品は現在購入できません」

#### 6.13 チェックアウト（`/checkout`）

順序依存あり。全て逐次実行:

```
① GET /users/me/addresses        → 住所一覧を表示
   ↓ ユーザーが住所を選択
② POST /orders/checkout          → { orders[], clientSecret, totalAmount }
   ↓ clientSecret 受け取り
③ stripe.confirmPayment()        → Stripe 決済実行
   ↓ 成功
④ /checkout/success?orderId={id} へリダイレクト
```

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /users/me/addresses` | 住所選択（既存住所一覧） | `id`, `recipientName`, `postalCode`, `prefecture`, `city`, `addressLine`, `isDefault` |
| `POST /users/me/addresses` | 住所新規登録（住所なし or 新規追加時） | 全住所フィールド → 返却 `id` を次のチェックアウトで使用 |
| `POST /orders/checkout` | 注文作成 + PaymentIntent 生成 | `addressId` → `{ orders[], clientSecret, totalAmount }` |
| `stripe.confirmPayment()` | Stripe 決済 | `clientSecret`（Stripe.js API） |

**住所新規登録のフロー:** 住所が0件またはユーザーが新規追加を選択した場合、住所フォームを表示 → `POST /users/me/addresses` → 返却 `id` をそのまま `POST /orders/checkout` の `addressId` に使用する（逐次）。

複数ショップの商品がある場合: `orders[]` が複数返却される。ショップ別に金額・送料を分けて表示する（PAY-07）。  
`CART_EMPTY` / `PRODUCT_OUT_OF_STOCK` / `PRODUCT_NOT_ACTIVE` エラー時はカートへ戻るリンクを表示する。

#### 6.14 注文履歴（`/orders`）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /orders?status=&page=` | 注文一覧 | `id`, `status`, `totalAmount`, `shop.name`, `itemCount`, `createdAt` |

`status` フィルターは URL クエリ `?status=PROCESSING` で管理する。タブ切り替えで `router.push` を使用。

#### 6.15 注文詳細（`/orders/[id]`）

| API コール | 用途 |
|---|---|
| `GET /orders/{id}` | 注文詳細取得 |
| WS: `/topic/orders/{orderId}` | ステータスリアルタイム更新 |
| `POST /orders/{id}/cancel` | キャンセル（`PAYMENT_CONFIRMED` / `PROCESSING` のみ） |

WS メッセージ受信時 → `queryClient.setQueryData(queryKeys.orders.detail(id), newData)` でキャッシュを直接更新。

注文明細の `isReviewed` フィールドを確認し、`COMPLETED` かつ未レビューの明細にのみ「レビューを書く」ボタンを表示（Phase 5 実装）。

#### 6.16 配送先住所管理（`/profile/addresses`）

| API コール | 用途 |
|---|---|
| `GET /users/me/addresses` | 住所一覧取得 |
| `POST /users/me/addresses` | 住所追加 |
| `PATCH /users/me/addresses/{id}` | 住所更新 |
| `DELETE /users/me/addresses/{id}` | 住所削除 |

#### 6.17 セラー注文管理（`/seller/orders`）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /orders?status=PAYMENT_CONFIRMED&page=` | 新規注文一覧 | `id`, `status`, `totalAmount`, `buyer.displayName`, `createdAt`, `itemCount` |
| `PATCH /orders/{id}/status` | ステータス進行 | `status`（`PROCESSING` / `SHIPPED` / `COMPLETED`） |
| `POST /orders/{id}/cancel` | キャンセル | `reason?` |

ステータス遷移図: `PAYMENT_CONFIRMED → PROCESSING → SHIPPED → COMPLETED`

---

### Phase 4（チャット・通知・管理者機能）

#### 6.18 チャット一覧（`/messages`）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /chat-rooms` | ルーム一覧（最終メッセージ降順） | `id`, `shop.*`, `buyer.*`, `lastMessage.*`, `unreadCount` |

#### 6.19 チャットルーム（`/messages/[roomId]`）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `POST /chat-rooms` | チャット開始（商品詳細・ショップページから） | `shopId` → 201（新規）または 200（既存）→ `id` |
| `GET /chat-rooms/{id}?page=0&size=50` | 初期メッセージ履歴 | `messages.content[]`, `shop.*`, `buyer.*` |
| STOMP: `/app/chat.send` | メッセージ送信 | `chatRoomId`, `body` |
| WS: `/topic/chat/{chatRoomId}` | リアルタイム受信 | `id`, `senderId`, `senderDisplayName`, `body`, `createdAt` |
| `GET /chat-rooms/{id}?page=N` | 過去メッセージ（無限スクロール） | 同上 |

`POST /chat-rooms` は同一バイヤー・ショップ間で既存ルームがある場合 `200 OK` を返す。レスポンスの `id` を使って `/messages/{id}` へ遷移する。

#### 6.20 通知（グローバル）

ナビバーに組み込む。全認証画面で動作。

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /notifications?size=10` | ドロップダウン初期ロード | レスポンス全体: `unreadCount`（未読バッジ用、トップレベル）、`content[].id`, `content[].type`, `content[].title`, `content[].body`, `content[].isRead`, `content[].relatedEntityType`, `content[].relatedEntityId` |
| WS: `/topic/notifications/{userId}` | リアルタイム通知 | 新規通知オブジェクト（`content[]` の1要素と同形式） |
| `PATCH /notifications/{id}/read` | 個別既読 | — |
| `PATCH /notifications/read-all` | 全既読 | — |

> `unreadCount` は `content[]` 内の各通知オブジェクトのフィールドではなく、`PageResponse` の**トップレベル**に追加されたフィールド。ナビバーのバッジ表示にはこのフィールドを使う。

WS 受信時 → `queryClient.invalidateQueries({ queryKey: queryKeys.notifications() })` で再フェッチ。

`relatedEntityType` + `relatedEntityId` による遷移先:
- `ORDER` → `/orders/{relatedEntityId}`
- `SELLER_APPLICATION` → `/seller/applications/new`（申請状況確認）
- `CHAT_ROOM` → `/messages/{relatedEntityId}`

#### 6.21 管理者画面（Phase 4）

| 画面 | API コール | 並列可否 |
|---|---|---|
| 管理者ダッシュボード | `GET /admin/dashboard` | — |
| ユーザー管理 | `GET /admin/users?role=&status=&q=&page=` | — |
| | `PATCH /admin/users/{id}/status` | — |
| 商品モデレーション | `GET /admin/products?status=&shopId=&page=` | — |
| | `PATCH /admin/products/{id}/status` | — |
| カテゴリー管理 | `GET /admin/categories` | — |
| | `POST /admin/categories` | — |
| | `PATCH /admin/categories/{id}` | — |
| | `DELETE /admin/categories/{id}` | — |
| プラットフォーム設定 | `GET /admin/platform-configs` | — |
| | `PATCH /admin/platform-configs/{key}` | — |

---

### Phase 5（レビュー・お気に入り）

#### 6.22 レビュー投稿（商品詳細レビュータブ）

| API コール | 用途 | 必要フィールド |
|---|---|---|
| `GET /products/{id}/reviews?page=&size=20` | レビュー一覧 + サマリー | `content[]`, `summary.averageRating`, `summary.totalCount`, `summary.ratingDistribution` |
| `POST /order-items/{id}/review` | レビュー投稿 | `rating`（1〜5）, `comment?` |

レビュー投稿ボタンの表示条件: 注文詳細 `GET /orders/{id}` の `items[].isReviewed === false` かつ `orderStatus === 'COMPLETED'`。

#### 6.23 お気に入り（`/wishlist`）

| API コール | 用途 |
|---|---|
| `GET /wishlist?page=` | お気に入り商品一覧（`ProductSummaryResponse`） |
| `POST /wishlist` | 追加（`productId`） |
| `DELETE /wishlist/{productId}` | 削除 |

ハートアイコンの楽観的更新を推奨: `useMutation` の `onMutate` でキャッシュを即時更新し、`onError` で元に戻す。

---

## 7. 並列フェッチ・データ依存設計

### 7.1 Server Component での並列フェッチ

```typescript
// 商品詳細: 商品情報とレビューを並列取得
async function ProductPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const [product, reviews] = await Promise.all([
    fetchProduct(id),
    fetchProductReviews(id, { page: 0 }),
  ])
}

// ホーム: 商品一覧とカテゴリーを並列取得
async function HomePage() {
  const [products, categories] = await Promise.all([
    fetchProducts({ sort: 'createdAt,desc', size: 20 }),
    fetchCategories(),
  ])
}

// セラーダッシュボード: 全データ並列取得
async function SellerDashboardPage() {
  const [dashboard, recentOrders] = await Promise.all([
    fetchSellerDashboard(),
    fetchOrders({ size: 10 }),
  ])
}

// ショップ詳細: ショップ情報と商品一覧を並列取得
async function ShopPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const [shop, products] = await Promise.all([
    fetchShop(id),
    fetchProducts({ shopId: id }),
  ])
}
```

### 7.2 順序依存が必要なケース（逐次）

```typescript
// チェックアウト: 住所選択 → 注文作成 → Stripe 決済
const addresses = await fetchAddresses()
// UI でユーザーが住所を選択した後
const checkoutResult = await postCheckout({ addressId: selected.id })
// clientSecret を受け取った後
await stripe.confirmPayment({ clientSecret: checkoutResult.clientSecret, ... })

// 商品登録: メタデータ → 画像アップロード（product.id が必要）
const product = await postProduct({ name, price, stockQuantity, ... })
await Promise.all(imageFiles.map((file) => uploadProductImage(product.id, file)))

// メール認証: URL パラメータ取得 → Body で送信 → トークン保存 → リダイレクト
const token = searchParams.get('token')
const tokens = await postVerifyEmail({ token })        // AuthTokens（refreshToken 含む）
useAuthStore.getState().setTokens(tokens)              // localStorage への refreshToken 保存も含む
const { data: user } = await apiClient.get<AuthUser>('/users/me')
useAuthStore.getState().setUser(user)
router.replace('/')                                    // router は同期 API なので await 不要
```

### 7.3 ウォーターフォール回避

```typescript
// ❌ 避けるべき（直列になっている）
const product = await fetchProduct(id)
const reviews = await fetchProductReviews(id)  // product の完了を待っている

// ✅ 推奨（並列）
const [product, reviews] = await Promise.all([
  fetchProduct(id),
  fetchProductReviews(id),
])
```

---

## 8. WebSocket / STOMP 統合

### 8.1 接続設定

`src/hooks/useStompClient.ts` に実装する（CODING_STANDARDS §7.1: Hooks ファイルは camelCase）。

```typescript
import { Client } from '@stomp/stompjs'

export function createStompClient(accessToken: string): Client {
  return new Client({
    brokerURL: `${process.env.NEXT_PUBLIC_WS_URL}/ws?token=${accessToken}`,
    reconnectDelay: 5_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
  })
}
```

> **JWT の送信:** WebSocket ハンドシェイク時に HTTP ヘッダーは設定できないため、Query パラメータで送信する。バックエンドの Spring `HandshakeInterceptor` が検証する。

### 8.2 トピック一覧と購読タイミング

| トピック | 購読タイミング | 処理 |
|---|---|---|
| `/topic/notifications/{userId}` | ログイン後（RootLayout Provider） | `queryClient.invalidateQueries({ queryKey: queryKeys.notifications() })` |
| `/topic/orders/{orderId}` | `/orders/[id]` マウント時 | `queryClient.setQueryData(queryKeys.orders.detail(orderId), data)` |
| `/topic/chat/{chatRoomId}` | `/messages/[roomId]` マウント時 | `queryClient.setQueryData(queryKeys.chatRooms.detail(chatRoomId), ...)` |

### 8.3 共通 useWebSocket フック インターフェース

```typescript
function useWebSocket(topic: string, onMessage: (body: unknown) => void): {
  isConnected: boolean
}
```

### 8.4 チャットメッセージ送信

```typescript
// REST ではなく STOMP publish を使用
stompClient.publish({
  destination: '/app/chat.send',
  body: JSON.stringify({ chatRoomId, body: messageText }),
})
```

### 8.5 チャットキャッシュへのリアルタイム追記

```typescript
stompClient.subscribe(`/topic/chat/${chatRoomId}`, (frame) => {
  const newMessage = JSON.parse(frame.body)
  queryClient.setQueryData(queryKeys.chatRooms.detail(chatRoomId), (old: ChatRoomDetail) => ({
    ...old,
    messages: {
      ...old.messages,
      content: [...old.messages.content, newMessage],
      totalElements: old.messages.totalElements + 1,
    },
  }))
  // 自分が送信者でない場合: チャット一覧の未読カウントも更新
  if (newMessage.senderId !== currentUser.id) {
    queryClient.invalidateQueries({ queryKey: queryKeys.chatRooms.list })
  }
})
```

---

## 9. エラーコード → UI 表示方針

### 9.1 表示方法の定義

| 方法 | 用途 |
|---|---|
| **Toast（エラー）** | バックグラウンド処理・一時的な操作失敗 |
| **インライン** | フォーム送信・カート操作など画面内の特定箇所 |
| **ページリダイレクト** | 認証切れ・権限なし（ユーザー操作を中断） |
| **サイレント** | UI への影響がない失敗（既読処理など） |

### 9.2 全エラーコード対応表

#### 汎用エラー

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `UNAUTHORIZED` | 401 | ページリダイレクト | — (`/auth/login?redirect=<元URL>` へ) |
| `TOKEN_EXPIRED` | 401 | インターセプターが自動リフレッシュ。失敗時はリダイレクト | — |
| `TOKEN_INVALID` | 401 | ページリダイレクト | — |
| `ACCESS_DENIED` | 403 | Toast（エラー）/ ページリダイレクト | 「このページへのアクセス権限がありません」 |
| `RESOURCE_NOT_FOUND` | 404 | `not-found.tsx` またはインライン | 「お探しのページまたはデータが見つかりませんでした」 |
| `VALIDATION_FAILED` | 422 | インライン（フォームフィールド別） | `errors[].message` をそのまま表示 |
| `RATE_LIMIT_EXCEEDED` | 429 | Toast（エラー） | 「リクエストが多すぎます。しばらくしてからお試しください」 |
| `DUPLICATE_ENTRY` | 409 | インライン / Toast | 「すでに登録されています」 |
| `INTERNAL_SERVER_ERROR` | 500 | `error.tsx` / Toast | 「サーバーエラーが発生しました。時間をおいて再度お試しください」 |

#### 認証・ユーザー

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `INVALID_CREDENTIALS` | 401 | インライン（フォーム下部） | 「メールアドレスまたはパスワードが正しくありません」 |
| `EMAIL_NOT_VERIFIED` | 403 | インライン（フォーム下部） | 「メールアドレスの確認が完了していません。受信ボックスをご確認ください」 |
| `EMAIL_ALREADY_REGISTERED` | 409 | インライン（email フィールド） | 「このメールアドレスはすでに登録されています」 |
| `EMAIL_VERIFICATION_TOKEN_INVALID` | 400 | インライン + 再送信ボタン | 「認証リンクが無効または使用済みです。再送信してください」 |
| `EMAIL_VERIFICATION_TOKEN_EXPIRED` | 400 | インライン + 再送信ボタン | 「認証リンクの有効期限が切れました（24時間）。再送信してください」 |
| `GOOGLE_TOKEN_INVALID` | 401 | Toast（エラー） | 「Google 認証に失敗しました。再度お試しください」 |
| `REFRESH_TOKEN_INVALID` | 401 | ページリダイレクト | — (ログアウト → `/auth/login`) |
| `USER_DEACTIVATED` | 403 | インライン（フォーム下部） | 「このアカウントは無効化されています。サポートにお問い合わせください」 |
| `PASSWORD_CHANGE_FAILED` | 400 | インライン（`currentPassword` フィールド） | 「現在のパスワードが正しくありません」 |

#### セラー申請

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `SELLER_APPLICATION_PENDING` | 409 | インライン | 「すでに審査中の申請があります。結果をお待ちください」 |
| `SELLER_APPLICATION_ALREADY_APPROVED` | 409 | ページリダイレクト | — (`/seller/dashboard` へ) |
| `SELLER_APPLICATION_NOT_REVIEWABLE` | 409 | Toast（エラー） | 「この申請はすでに処理済みです」（管理者画面） |

#### ショップ

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `SHOP_NAME_ALREADY_TAKEN` | 409 | インライン（`name` フィールド） | 「このショップ名はすでに使用されています」 |
| `SHOP_NOT_FOUND` | 404 | not-found / Toast | 「ショップが見つかりません」 |

#### 商品

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `PRODUCT_NOT_ACTIVE` | 409 | インライン（購入ボタン付近） | 「この商品は現在購入できません」 |
| `PRODUCT_OUT_OF_STOCK` | 409 | インライン（購入ボタン付近） | 「この商品は在庫切れです」 |
| `PRODUCT_IMAGE_LIMIT_EXCEEDED` | 409 | Toast（警告） | 「商品画像は最大5枚までです」 |
| `PRODUCT_NOT_OWNED` | 403 | Toast（エラー） | 「この操作を実行する権限がありません」 |

#### カート

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `CART_ITEM_QUANTITY_EXCEEDED` | 409 | インライン（数量入力付近） | 「在庫数を超えた数量は追加できません」 |
| `CANNOT_PURCHASE_OWN_PRODUCT` | 409 | Toast（警告） | 「自分のショップの商品は購入できません」 |

#### 注文・決済

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `CART_EMPTY` | 409 | Toast（エラー） | 「カートに商品がありません」 |
| `ORDER_NOT_CANCELLABLE` | 409 | インライン（キャンセルボタン付近） | 「この注文はキャンセルできません（発送済みまたは完了済み）」 |
| `ORDER_STATUS_TRANSITION_INVALID` | 409 | Toast（エラー） | 「このステータス変更は許可されていません」 |
| `PAYMENT_FAILED` | 422 | インライン（Stripe Element 下部） | 「決済に失敗しました。カード情報をご確認ください」 |
| `REFUND_FAILED` | 500 | Toast（エラー） | 「返金処理に失敗しました。サポートにお問い合わせください」 |
| `ORDER_NOT_FOUND` | 404 | not-found | 「注文が見つかりません」 |

#### レビュー

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `REVIEW_ALREADY_SUBMITTED` | 409 | Toast（警告） | 「この商品へのレビューはすでに投稿済みです」 |
| `REVIEW_NOT_ELIGIBLE` | 403 | Toast（エラー） | 「レビューを投稿できる条件を満たしていません」 |

#### チャット・通知

| エラーコード | HTTP | UI 表示方法 | 日本語メッセージ |
|---|---|---|---|
| `CHAT_ROOM_NOT_FOUND` | 404 | ページリダイレクト | — (`/messages` へ戻る) |
| `NOTIFICATION_NOT_FOUND` | 404 | サイレント（無視） | — |

---

## 10. フォームバリデーションエラーのマッピング

バックエンドの `VALIDATION_FAILED` レスポンスには `errors[]` 配列が含まれる。react-hook-form の `setError` に変換するユーティリティを用意する。

```typescript
// src/lib/form-utils.ts

export function applyServerValidationErrors(
  error: unknown,
  setError: UseFormSetError<FieldValues>
): boolean {
  const validationErrors = getValidationErrors(error)
  if (validationErrors.length === 0) return false

  validationErrors.forEach(({ field, message }) => {
    setError(field as FieldPath<FieldValues>, {
      type: 'server',
      message,
    })
  })
  return true
}

// フォーム送信ハンドラーでの使い方
const onSubmit = async (data: FormValues) => {
  try {
    await postProduct(data)
    toast.success('商品を登録しました')
  } catch (error) {
    const handled = applyServerValidationErrors(error, setError)
    if (!handled) {
      const code = getApiErrorCode(error) ?? 'INTERNAL_SERVER_ERROR'
      toast.error(ERROR_MESSAGES[code] ?? ERROR_MESSAGES.INTERNAL_SERVER_ERROR)
    }
  }
}
```

`ERROR_MESSAGES` は §9.2 の「日本語メッセージ」列をオブジェクトにまとめた定数。`src/lib/constants.ts` に定義する（CODING_STANDARDS §1.1: 定数は `src/lib/constants.ts` に集約）。

### 10.1 フィールド名対応表

バックエンドの `errors[].field`（`camelCase`）はフロントエンドのフォームフィールド名と一致させる。不一致の場合は変換マッピングを追加する。

| バックエンド `field` | フロントエンドフォームフィールド | 画面 |
|---|---|---|
| `email` | `email` | 認証系 |
| `password` | `password` | ログイン・登録 |
| `passwordConfirm` | `passwordConfirm` | 新規登録 |
| `displayName` | `displayName` | プロフィール設定 |
| `currentPassword` | `currentPassword` | パスワード変更 |
| `newPassword` | `newPassword` | パスワード変更 |
| `recipientName` | `recipientName` | 住所管理 |
| `postalCode` | `postalCode` | 住所管理 |
| `prefecture` | `prefecture` | 住所管理 |
| `city` | `city` | 住所管理 |
| `addressLine` | `addressLine` | 住所管理 |
| `phoneNumber` | `phoneNumber` | 住所管理 |
| `name` | `name` | 商品登録・ショップ設定 |
| `price` | `price` | 商品登録 |
| `stockQuantity` | `stockQuantity` | 商品登録 |
| `categoryId` | `categoryId` | 商品登録 |
| `reason` | `reason` | セラー申請 |
| `shippingType` | `shippingType` | 配送ポリシー |
| `fixedFee` | `fixedFee` | 配送ポリシー |
| `freeThreshold` | `freeThreshold` | 配送ポリシー |
| `rating` | `rating` | レビュー投稿 |

---

## 11. 環境変数

`src/env.ts` で zod を使ってバリデーションする。ランタイムエラーより起動時エラーで気づける。

```typescript
import { z } from 'zod'

const clientEnvSchema = z.object({
  NEXT_PUBLIC_API_URL:                z.string().url(),
  NEXT_PUBLIC_WS_URL:                 z.string().url(),
  NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY: z.string().startsWith('pk_'),
})

export const env = clientEnvSchema.parse({
  NEXT_PUBLIC_API_URL:                process.env.NEXT_PUBLIC_API_URL,
  NEXT_PUBLIC_WS_URL:                 process.env.NEXT_PUBLIC_WS_URL,
  NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY: process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY,
})
```

`.env.local`（`.gitignore` 対象）のサンプル:

| 変数名 | ローカル開発 | 本番 |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | `https://api.kivio.example.com` |
| `NEXT_PUBLIC_WS_URL` | `ws://localhost:8080` | `wss://api.kivio.example.com` |
| `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` | `pk_test_...` | `pk_live_...` |

---

**以上**

*このドキュメントはフロントエンド実装の進行に合わせて更新する。エンドポイントの追加・変更時は `API_DESIGN.md` と `ERROR_CODES.md` の両方との整合性を確認すること。*
