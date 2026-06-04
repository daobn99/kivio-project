# Kivio — フロントエンド

Kivio マーケットプレイスの Next.js 16 フロントエンド。App Router・TanStack Query・Zustand・shadcn/ui で構築。

## 前提条件

- Node.js 20+
- pnpm 9+
- バックエンドが `http://localhost:8080` で起動していること（[kivio-backend](../kivio-backend/README.md) 参照）

## 起動手順

```bash
cp .env.local.example .env.local
# .env.local の各変数を設定する（下記「環境変数」参照）

pnpm install
pnpm dev        # → http://localhost:3000
```

## コマンド

| コマンド | 説明 |
|---|---|
| `pnpm dev` | 開発サーバー起動（ホットリロード） |
| `pnpm build` | 本番ビルド |
| `pnpm start` | 本番ビルドの配信 |
| `pnpm lint` | ESLint 実行 |

## 環境変数

`.env.local.example` を `.env.local` にコピーして値を設定する。

| 変数名 | 説明 |
|---|---|
| `API_BASE_URL` | Server Component が使うバックエンド URL（デフォルト: `http://localhost:8080`） |
| `NEXT_PUBLIC_API_BASE_URL` | Client Component / ブラウザが使うバックエンド URL |
| `AUTH_SECRET` | NextAuth シークレット — `openssl rand -base64 32` で生成 |
| `AUTH_GOOGLE_ID` | Google OAuth クライアント ID |
| `AUTH_GOOGLE_SECRET` | Google OAuth クライアントシークレット |

## shadcn/ui コンポーネントの追加

```bash
npx shadcn@latest add <component>

# 例
npx shadcn@latest add button
npx shadcn@latest add dialog
npx shadcn@latest add form
```

生成先: `src/components/ui/`

## ディレクトリ構成

```
src/
├── app/                   # App Router（ページ・レイアウト）
├── components/
│   ├── ui/                # shadcn/ui 自動生成コンポーネント
│   ├── layout/            # Navbar, Footer, SellerSidebar
│   ├── auth/              # LoginForm, RegisterForm
│   ├── product/           # ProductCard, ProductGrid
│   ├── order/             # OrderCard, OrderSummary
│   ├── cart/              # CartItem, CartSummary
│   └── seller/            # SellerStatsCard, ApplicationForm
├── hooks/                 # カスタムフック（use* プレフィックス）
├── lib/
│   ├── api/client/        # Client Component 用フェッチラッパー（401 時自動リフレッシュ）
│   ├── api/server/        # Server Component 用フェッチラッパー（Cookie からトークン取得）
│   ├── validations/       # Zod スキーマ
│   ├── utils.ts           # cn() などの汎用ユーティリティ
│   └── constants.ts       # ROUTES, API_BASE_URL 等
├── stores/                # Zustand ストア
├── types/                 # 共通型定義（api/, domain/, enums）
└── proxy.ts               # 認証ガード（Next.js 16: middleware.ts の代替）
```

## 関連ドキュメント

- [フロントエンドコーディング規約](../docs/development/FRONTEND_CODING_STANDARDS.md)
- [画面・ナビゲーション構成](../docs/design/frontend/FRONTEND_IA.md)
- [デザインシステム](../design-system/MASTER.md)
- [テスト戦略](../docs/development/FRONTEND_TEST_STRATEGY.md)
- [API コントラクト](../docs/design/frontend/FRONTEND_API_CONTRACT.md)
