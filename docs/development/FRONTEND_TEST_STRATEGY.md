# フロントエンドテスト戦略

> バックエンドのテスト戦略は [TEST_STRATEGY.md](./TEST_STRATEGY.md) を参照。
> コーディング規約は [FRONTEND_CODING_STANDARDS.md](./FRONTEND_CODING_STANDARDS.md) を参照。

**基本方針: ポートフォリオ用のため、壊れると困るコアロジックに絞って最小限のテストを書く。**

---

## 目次

1. [テスト対象の選定方針](#1-テスト対象の選定方針)
2. [テストピラミッド](#2-テストピラミッド)
3. [ツールと役割分担](#3-ツールと役割分担)
4. [MSW セットアップ](#4-msw-セットアップ)
5. [テスト種別ガイド](#5-テスト種別ガイド)
6. [TanStack Query を含む非同期テスト](#6-tanstack-query-を含む非同期テスト)
7. [命名規則](#7-命名規則)
8. [テスト実行](#8-テスト実行)

---

## 1. テスト対象の選定方針

### テストする（コアロジック）

| 対象 | 理由 |
|---|---|
| Zod バリデーションスキーマ | バリデーションルールのミスはユーザー体験に直結する |
| Custom Hooks（非同期・副作用あり） | コンポーネントから切り離せる再利用ロジック |
| フォームコンポーネント（LoginForm・RegisterForm 等） | バリデーション UI の動作確認 |
| Zustand ストアのアクション | 認証・カートの状態遷移は副作用が大きい |
| E2E: 認証フロー・購入フロー | 最重要ユーザーフローはブラウザで確認する |

### テストしない（コストに見合わない）

| 対象 | 理由 |
|---|---|
| shadcn/ui ラッパーコンポーネント（Button・Input 等） | ライブラリ側でテスト済み。純粋なパススルー |
| Skeleton・Empty・Error の表示コンポーネント | スナップショットテストは変化に脆い |
| Server Component（page.tsx・layout.tsx） | Next.js の RSC テストは複雑でコストが高い |
| 型定義ファイル（`src/types/`） | TypeScript コンパイルで保証される |
| `cn()` 等の 1 行ユーティリティ | 実装が自明すぎる |

---

## 2. テストピラミッド

```
          ┌───────────────┐
          │     E2E       │  少数・遅い・クリティカルフローのみ
          │  (Playwright) │  認証 / 商品閲覧 / カート追加
          ├───────────────┤
          │  Component    │  フォームの入力・バリデーション UI
          │  (RTL + MSW)  │  TanStack Query を含む非同期コンポーネント
          ├───────────────┤
          │    Unit       │  Zod スキーマ / Custom Hooks / Zustand Store
          │   (Vitest)    │  速い・多数
          └───────────────┘
```

| 種別 | ツール | 目安件数 |
|---|---|---|
| Unit | Vitest | 20〜30 件 |
| Component | Vitest + RTL + MSW | 10〜15 件 |
| E2E | Playwright | 5〜8 シナリオ |

---

## 3. ツールと役割分担

| ツール | 役割 |
|---|---|
| **Vitest** | Unit / Component テストのランナー。Jest 互換 API で Next.js との相性が良い |
| **React Testing Library (RTL)** | コンポーネントのレンダリング・ユーザーインタラクションのテスト |
| **MSW (Mock Service Worker)** | API レスポンスのモック。テスト・開発時に共通のハンドラーを使う |
| **@testing-library/user-event** | 実際のユーザー操作（入力・クリック）を再現する |
| **Playwright** | ブラウザ E2E テスト。Chromium のみを対象にする（ポートフォリオ目的） |

### インストール

```bash
# Vitest + RTL
# @testing-library/dom は RTL の peer dependency として Next.js 公式が明示要求
pnpm add -D vitest @vitejs/plugin-react jsdom vite-tsconfig-paths \
  @testing-library/react @testing-library/dom \
  @testing-library/user-event @testing-library/jest-dom

# MSW
pnpm add -D msw

# Playwright (Chromium のみ)
pnpm add -D @playwright/test
npx playwright install chromium
```

### `vitest.config.mts`

> Next.js 公式は `.mts` 拡張子推奨。パスエイリアスは `vite-tsconfig-paths` で `tsconfig.json` から自動解決する（手動 `alias` は不要）。

```ts
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tsconfigPaths from 'vite-tsconfig-paths'

export default defineConfig({
  plugins: [tsconfigPaths(), react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
})
```

> `globals: true` を使う場合は `tsconfig.json` の `compilerOptions.types` に `"vitest/globals"` を追加してエディタ補完を有効にする。
>
> ```json
> { "compilerOptions": { "types": ["vitest/globals"] } }
> ```

### `src/test/setup.ts`

```ts
import '@testing-library/jest-dom'
import { server } from './mocks/server'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
```

---

## 4. MSW セットアップ

API モックは MSW に集約する。開発時と Vitest テスト時で同じハンドラーを使い回す。

### ディレクトリ構成

```
src/test/
├── mocks/
│   ├── handlers/
│   │   ├── auth.ts       # POST /api/v1/auth/login|register|refresh
│   │   ├── products.ts   # GET /api/v1/products, /api/v1/products/:id
│   │   └── cart.ts       # GET/POST/PATCH/DELETE /api/v1/cart
│   ├── server.ts         # Node 環境（Vitest）
│   └── browser.ts        # ブラウザ環境（開発時 dev server）
├── fixtures/
│   ├── product.ts        # テスト用データファクトリ
│   └── user.ts
└── setup.ts
```

### `src/test/mocks/handlers/auth.ts`

```ts
import { http, HttpResponse } from 'msw'

export const authHandlers = [
  http.post('/api/v1/auth/login', () =>
    HttpResponse.json({
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
    })
  ),

  http.post('/api/v1/auth/register', () =>
    HttpResponse.json({ id: 'user-1', email: 'test@example.com' }, { status: 201 })
  ),
]
```

### `src/test/mocks/server.ts`

```ts
import { setupServer } from 'msw/node'
import { authHandlers } from './handlers/auth'
import { productHandlers } from './handlers/products'
import { cartHandlers } from './handlers/cart'

export const server = setupServer(...authHandlers, ...productHandlers, ...cartHandlers)
```

### テストでのハンドラー上書き（エラーケース）

```ts
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'

it('ログイン失敗時に 401 エラーを表示する', async () => {
  server.use(
    http.post('/api/v1/auth/login', () =>
      HttpResponse.json({ title: '認証失敗', status: 401 }, { status: 401 })
    )
  )
  // ... テスト本体
})
```

---

## 5. テスト種別ガイド

### 5.1 Zod スキーマ（Unit）

バリデーションルールを直接検証する。最も書きやすく費用対効果が高い。

```ts
// src/lib/validations/__tests__/auth.test.ts
import { loginSchema } from '@/lib/validations/auth'

describe('loginSchema', () => {
  it('有効な入力はパスする', () => {
    const result = loginSchema.safeParse({ email: 'user@example.com', password: 'password123' })
    expect(result.success).toBe(true)
  })

  it('メールアドレス形式が不正な場合はエラーになる', () => {
    const result = loginSchema.safeParse({ email: 'not-email', password: 'password123' })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].path).toContain('email')
  })

  it('パスワードが 8 文字未満の場合はエラーになる', () => {
    const result = loginSchema.safeParse({ email: 'user@example.com', password: '1234567' })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].path).toContain('password')
  })
})
```

### 5.2 Zustand ストア（Unit）

アクションの状態遷移を検証する。テスト間でストアをリセットすることを忘れない。

```ts
// src/stores/__tests__/useAuthStore.test.ts
import { renderHook, act } from '@testing-library/react'
import { useAuthStore } from '@/stores/useAuthStore'

const mockUser = { id: '1', email: 'user@example.com', role: 'BUYER' as const }

beforeEach(() => {
  useAuthStore.setState({ user: null, isAuthenticated: false })
})

describe('useAuthStore', () => {
  it('setUser を呼ぶと isAuthenticated が true になる', () => {
    const { result } = renderHook(() => useAuthStore())
    act(() => result.current.setUser(mockUser))
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual(mockUser)
  })

  it('logout を呼ぶとユーザー情報がクリアされる', () => {
    useAuthStore.setState({ user: mockUser, isAuthenticated: true })
    const { result } = renderHook(() => useAuthStore())
    act(() => result.current.logout())
    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
  })
})
```

### 5.3 フォームコンポーネント（Component）

バリデーションエラーの表示とフォーム送信を検証する。

```tsx
// src/components/auth/__tests__/LoginForm.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test/utils'
import LoginForm from '@/components/auth/LoginForm'

function renderLoginForm() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <LoginForm />
    </QueryClientProvider>
  )
}

describe('LoginForm', () => {
  it('空フォームを送信するとバリデーションエラーが表示される', async () => {
    const user = userEvent.setup()
    renderLoginForm()

    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByText(/有効なメールアドレス/)).toBeInTheDocument()
    expect(await screen.findByText(/8文字以上/)).toBeInTheDocument()
  })

  it('有効な入力で送信するとボタンがローディング状態になる', async () => {
    const user = userEvent.setup()
    renderLoginForm()

    await user.type(screen.getByLabelText('メールアドレス'), 'user@example.com')
    await user.type(screen.getByLabelText('パスワード'), 'password123')
    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'ログイン中...' })).toBeDisabled()
    })
  })
})
```

---

## 6. TanStack Query を含む非同期テスト

### テスト用ユーティリティ

```ts
// src/test/utils.ts
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement } from 'react'

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },    // テストでリトライしない
      mutations: { retry: false },
    },
  })
}

export function renderWithQuery(ui: ReactElement, options?: RenderOptions) {
  const client = createTestQueryClient()
  return render(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
    options
  )
}
```

### Query Hook のテスト（MSW でレスポンスをモック）

> **⚠️ wrapper の書き方に注意:** `createTestQueryClient()` を wrapper コンポーネント関数内で呼ぶと、再レンダリングのたびに新しい QueryClient が生成されキャッシュがリセットされる。`waitFor` が永久ループするバグの原因になる。
> `createWrapper()` で QueryClient を一度だけ生成してから wrapper を返すファクトリパターンを使う。

```ts
// src/hooks/queries/__tests__/useProductQuery.test.ts
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { QueryClientProvider } from '@tanstack/react-query'
import { useProductQuery } from '@/hooks/queries/useProductQuery'
import { createTestQueryClient } from '@/test/utils'
import { server } from '@/test/mocks/server'

// ✅ QueryClient をテストごとに 1 度だけ生成するファクトリ
function createWrapper() {
  const queryClient = createTestQueryClient()
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

it('商品データを取得して返す', async () => {
  const { result } = renderHook(() => useProductQuery('product-1'), {
    wrapper: createWrapper(),
  })

  await waitFor(() => expect(result.current.isSuccess).toBe(true))

  expect(result.current.data?.id).toBe('product-1')
})

it('API エラー時に isError が true になる', async () => {
  server.use(
    http.get('/api/v1/products/not-found', () =>
      HttpResponse.json({ title: '商品が見つかりません', status: 404 }, { status: 404 })
    )
  )

  const { result } = renderHook(() => useProductQuery('not-found'), {
    wrapper: createWrapper(),
  })

  await waitFor(() => expect(result.current.isError).toBe(true))
})
```

### Mutation Hook のテスト

```ts
// src/hooks/mutations/__tests__/useAddToCartMutation.test.ts
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { useAddToCartMutation } from '@/hooks/mutations/useAddToCartMutation'
import { createTestQueryClient } from '@/test/utils'

it('カート追加成功後に cart クエリが再フェッチされる', async () => {
  const queryClient = createTestQueryClient()
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

  const { result } = renderHook(() => useAddToCartMutation(), {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  })

  act(() => {
    result.current.mutate({ productId: 'p-1', quantity: 1 })
  })

  await waitFor(() => expect(result.current.isSuccess).toBe(true))

  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['cart'] })
})
```

---

## 7. 命名規則

### テストファイルの配置

テスト対象ファイルと同じディレクトリの `__tests__/` フォルダに置く。

```
src/lib/validations/
├── auth.ts
└── __tests__/
    └── auth.test.ts

src/hooks/queries/
├── useProductQuery.ts
└── __tests__/
    └── useProductQuery.test.ts
```

E2E テストはプロジェクトルートの `e2e/` ディレクトリに配置する。

```
e2e/
├── auth.spec.ts
├── product.spec.ts
└── checkout.spec.ts
```

### テストメソッド命名

```ts
// 日本語で「〜する」形式（何を検証するかが明確になる）
it('有効な入力はパスする', () => { ... })
it('パスワードが 8 文字未満の場合はエラーになる', () => { ... })
it('ログアウト後にユーザー情報がクリアされる', () => { ... })
```

---

## 8. テスト実行

```bash
# Unit + Component テスト（全件）
pnpm test

# ウォッチモード（開発中）
pnpm test --watch

# カバレッジレポート（確認用・CI 目標値なし）
pnpm test --coverage

# E2E テスト（バックエンド起動が必要）
pnpm exec playwright test

# E2E テスト（ヘッドあり・デバッグ用）
pnpm exec playwright test --headed

# 特定ファイルのみ
pnpm test src/lib/validations/__tests__/auth.test.ts
```

### Playwright 設定（`playwright.config.ts`）

```ts
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [
    // ポートフォリオ目的のため Chromium のみ
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
  },
})
```

### E2E テスト対象シナリオ（最小限）

| シナリオ | ファイル | 優先度 |
|---|---|---|
| ユーザー登録→ログイン→ログアウト | `auth.spec.ts` | 必須 |
| 商品一覧→詳細→カート追加 | `product.spec.ts` | 必須 |
| セラー申請フォーム送信 | `seller.spec.ts` | あれば良い |
