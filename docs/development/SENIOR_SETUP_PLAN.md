# シニア主導セットアッププロセス評価・実行計画

## コンテキスト

Kivio は Phase 1（要件定義・ドキュメント・devcontainer）が完了し、Phase 2 の実装開始直前にある。バックエンド・フロントエンドともに実装はゼロの状態。本ドキュメントでは、ジュニアが feature ブランチを切れる状態になるまでのシニア主導セットアップロードマップを示す。

---

## プロセス評価

### 垂直スライシング — ✅ 有効・採用

Kivio は既に Phase 2〜5 に機能群が整理されており相性が良い。「Backend 担当 / Frontend 担当」の水平分業ではなく「Auth 一式 / 商品カタログ一式」のように End-to-End で切ることで、担当者が一機能を完結させる体験を得られる。

> **注意:** Spring Security 基盤（JWT フィルター・Filter Chain）はクロスカッティングなため、垂直スライスの前にシニアが先行実装する。これを垂直スライスに含めると全タスクがブロックされる。

### スキャフォールディング — ✅ 有効・実施必須

Backend は依存関係のみ追加済みで実装はゼロ。Frontend は Next.js 16.2 + Tailwind 4 + ESLint のみで shadcn/ui・TanStack Query・Zustand・NextAuth v5 は未インストール。全てシニアが実施すべき範囲。

**Kivio 固有の追加事項:**
- `application.yaml` の環境別プロファイル設定（dev / test）
- Flyway ベースマイグレーション（`docs/design/DB_DESIGN.md` を参照）
- Next.js 16 の破壊的変更への対応（`params` / `searchParams` 非同期化、`middleware.ts` → `proxy.ts`）
- 現在 `app/` がルート直下にあるため `src/app/` へ移動し `tsconfig.json` を更新する作業が必要

### CI/CD — ✅ 有効・必須

`.github/workflows/` が存在しない。ジュニアのコード品質を担保するため、実装開始前に整備する。Husky による pre-commit フックも合わせて構築する。

---

## Kivio 固有の調整点

| 提示されたプロセス | Kivio での調整 |
|---|---|
| 垂直スライスで Junior に委任 | セキュリティ基盤・DB マイグレーション基底はシニア先行実装 |
| API Contract First (OpenAPI) | `docs/design/API_DESIGN.md` が既にある → OpenAPI アノテーションで同期する形 |
| Axios/Fetch Instance の設定 | `src/lib/api/` に Server/Client 別で配置。認証ガードは `src/proxy.ts`（`FRONTEND_CODING_STANDARDS.md` 参照） |
| `middleware.ts` でのガード | Next.js 16 では `src/proxy.ts`（単一ファイル）に変更済み |
| Husky + Git hooks | Frontend ルートに追加。Backend は Checkstyle で代替 |

---

## 実行計画

各フェーズはシニアが実施する。最後に "Hello World" 疎通確認を経てからジュニアに開放する。

---

### フェーズ 0: ブランチ戦略・運用ルール確立（0.5日）

- ブランチ戦略: `main`（本番相当） / `develop`（結合） / `feature/<name>`（機能開発）
- PR ルール: `feature/*` → `develop` のみ。セルフマージ禁止
- コミットメッセージ規約: Conventional Commits（`feat:`, `fix:`, `chore:`）
- 成果物: `docs/development/GIT_WORKFLOW.md` を追加

---

### フェーズ 1: バックエンド スキャフォールディング（2〜3日）

#### 1-1. アプリケーション設定
- `application.yaml` に dev / test プロファイル分離（既存ファイルは `.yaml` 拡張子）
- DB・JWT・Rate Limit・外部サービスの設定値を環境変数参照に外部化
- JWT パラメーター（Access 15 min / Refresh 7 days）を反映（`SECURITY.md` 参照）

#### 1-2. DB マイグレーション基底（Flyway）
- `V1__init_schema.sql`：`docs/design/DB_DESIGN.md` の全テーブル定義を一括反映
- Soft Delete カラム（`deleted_at`）・UUID 主キー（ADR-005）・監査カラムを含む

#### 1-3. 共通レイヤー（`io.kivio.common`）
- `PageResponse<T>`：Spring `Page<T>` をラップする汎用レスポンス型
- `SoftDeletableEntity`（`@MappedSuperclass`）：`@SQLRestriction("deleted_at IS NULL")` 付き
- カスタム例外クラス群（`KivioException`, `ResourceNotFoundException`, `ConflictException` 等）

#### 1-4. Spring Security + JWT フィルター（`io.kivio.config`）
- Security Filter Chain の定義（公開 / BUYER 専用 / SELLER 専用 / ADMIN 専用パスの振り分け）
- `JwtAuthenticationFilter`（リクエストごとにトークン検証）
- OAuth2（Google）ログインの初期設定

#### 1-5. 横断的関心事（Cross-cutting Concerns）
- `@ControllerAdvice`：RFC 9457 `ProblemDetail` 形式で全例外を一元ハンドリング
- `CorrelationIdFilter`：MDC に `correlationId` を設定しリクエスト全体に伝搬
- `@Auditable` AOP + `AuditLogAspect`（`AUDIT.md` 参照）
- Bucket4j による Rate Limit（認証 API: 10 req/min/IP、一般 API: 100 req/min/user）

#### 1-6. OpenAPI 設定
- `SpringDocConfig`：Swagger UI + Bearer Token 入力フィールドの有効化
- `http://localhost:8080/swagger-ui.html` で API 仕様を確認可能にする

#### 1-7. `kivio-backend/README.md` の作成
scaffolding が動いた後、実際のコマンドが確定してから書く。詳細は既存 docs に委ねるため薄くてよい。

記載内容:
- 起動コマンド（`./gradlew bootRun`、`docker compose up -d db`）
- テスト実行（`./gradlew test`）
- 必要な環境変数一覧（`.env.example` へのリンク）
- Swagger UI URL（`http://localhost:8080/swagger-ui.html`）
- 詳細ドキュメントへのリンク（`docs/` 配下の主要ファイル）

---

### フェーズ 2: フロントエンド スキャフォールディング（1〜2日）

#### 2-1. `src/` ディレクトリへの移行
現在 `app/` はルート直下にあるが、`FRONTEND_CODING_STANDARDS.md` の目標構成は `src/app/`。

1. `app/` → `src/app/` に移動
2. `tsconfig.json` の `paths` と `baseUrl` を `src/` 基準に更新
3. `next.config.ts` の alias 設定を確認

#### 2-2. 依存関係インストール
```
shadcn/ui（+ components.json 生成: npx shadcn@latest init）
@tanstack/react-query v5
zustand
react-hook-form + zod + @hookform/resolvers
next-auth v5（Auth.js）
prettier + prettier-plugin-tailwindcss
husky + lint-staged
commitlint + @commitlint/config-conventional
```

#### 2-3. ディレクトリ構成の確立（`FRONTEND_CODING_STANDARDS.md` に準拠）
```
kivio-frontend/
├── src/
│   ├── app/                    # App Router（2-1 で移行）
│   ├── components/
│   │   ├── ui/                 # shadcn/ui 自動生成コンポーネント
│   │   ├── layout/             # Navbar, Footer, SellerSidebar
│   │   ├── product/            # ProductCard, ProductGrid, ProductForm
│   │   ├── order/              # OrderCard, OrderSummary
│   │   ├── cart/               # CartItem, CartSummary
│   │   ├── auth/               # LoginForm, RegisterForm
│   │   └── seller/             # SellerStatsCard, ApplicationForm
│   ├── hooks/                  # カスタムフック（use* プレフィックス）
│   ├── lib/
│   │   ├── api/                # API クライアント関数（Server/Client 別）
│   │   ├── validations/        # Zod スキーマ
│   │   ├── utils.ts            # cn() など汎用ユーティリティ
│   │   └── constants.ts        # ROUTES, API_BASE_URL 等
│   ├── stores/                 # Zustand ストア
│   ├── types/                  # 型定義（api/, domain/, enums.ts）
│   └── proxy.ts                # 認証ガード（Next.js 16: middleware.ts → proxy.ts）
├── public/
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── components.json             # shadcn/ui 設定（npx shadcn init で生成）
```

#### 2-4. 型定義の基底
- `ProblemDetail`（RFC 9457 準拠）
- `PageResponse<T>`（バックエンドと同形）
- `ApiErrorCode`（`docs/design/ERROR_CODES.md` から列挙）

#### 2-5. TanStack Query プロバイダー
- `QueryClient` + `QueryClientProvider` を `src/app/layout.tsx` に設定
- `queryKeys.ts`：階層的クエリキーの雛形を作成

#### 2-6. HTTP クライアント（フェッチラッパー）
- `src/lib/api/client/`：Bearer Token 自動付与、401 時のリフレッシュ処理、ProblemDetail エラーのパース
- `src/lib/api/server/`：`cookies()` からセッション取得するサーバー側フェッチ

#### 2-7. Husky + lint-staged の設定
- pre-commit: ESLint + Prettier チェック（`prettier` は 2-2 でインストール済み）
- commit-msg: Conventional Commits 形式の検証（`commitlint`、2-2 でインストール済み）

#### 2-8. `kivio-frontend/README.md` の作成
scaffolding が動いた後に書く。詳細は既存 docs に委ねるため薄くてよい。

記載内容:
- 起動コマンド（`pnpm dev`、`pnpm build`、`pnpm lint`）
- 必要な環境変数一覧（`.env.local.example` へのリンク）
- shadcn/ui コンポーネント追加方法（`npx shadcn@latest add <component>`）
- 詳細ドキュメントへのリンク（`FRONTEND_CODING_STANDARDS.md`、`FRONTEND_IA.md`、`design-system/MASTER.md`）

---

### フェーズ 3: 疎通確認（0.5日）

1. バックエンドに `GET /api/v1/health` エンドポイントを追加（認証不要）
2. `docker compose up` で全サービスを起動
3. Next.js の Server Component から `/api/v1/health` を呼び出してレスポンス表示
4. Swagger UI でエンドポイントを確認
5. Flyway マイグレーションが正常に適用されていることを確認

---

### フェーズ 4: CI/CD パイプライン構築（1日）

#### CI（`ci.yml`）— PR 作成・`develop` へのプッシュ時にトリガー
- **Backend:** Checkstyle → `./gradlew test`（Testcontainers で PostgreSQL 起動） → ビルド確認
- **Frontend:** `pnpm lint` → `pnpm build` → 型チェック

#### CD（`cd-dev.yml`）— `develop` へのマージ時にトリガー
- Backend / Frontend の Dockerfile をビルド
- Docker Image をレジストリへプッシュ（認証情報は GitHub Secrets でプレースホルダー化）
- デプロイコマンドはコメントアウトで記述（環境依存のため）

---

## バーティカルスライス — ジュニア向けタスク割り当て

フェーズ 0〜4 完了後、以下の単位で feature ブランチを切る。各タスクは 1 名が E2E（API + UI）で担当する。

| # | タスク名 | ブランチ名 | Phase | 主要 API |
|---|---|---|---|---|
| 1 | 認証（登録・ログイン・OAuth） | `feature/auth` | 2 | `POST /auth/register`, `/auth/login`, `/auth/google` |
| 2 | ユーザープロフィール・住所 | `feature/user-profile` | 2 | `GET/PATCH /users/me`, `/users/me/addresses` |
| 3 | 出品者申請 | `feature/seller-application` | 2 | `POST /seller-applications`, `GET /seller-applications/me` |
| 4 | ショップ・商品 CRUD | `feature/catalog` | 2 | `POST /products`, `GET /products`, `/shops/:id` |
| 5 | 商品検索・一覧表示 | `feature/product-search` | 3 | `GET /products?q=&category=` |
| 6 | カート操作 | `feature/cart` | 3 | `GET/POST/PATCH/DELETE /cart` |
| 7 | チェックアウト・注文 | `feature/checkout` | 3 | `POST /orders/checkout`, Stripe Webhook |
| 8 | 注文履歴・ステータス | `feature/orders` | 3 | `GET /orders`, `GET /orders/:id` |
| 9 | チャット（WebSocket） | `feature/chat` | 4 | STOMP `/app/chat.*`, `/topic/chat.*` |
| 10 | 通知 | `feature/notification` | 4 | `GET /notifications`, WebSocket |
| 11 | 管理者機能 | `feature/admin` | 4 | `/admin/*` |
| 12 | レビュー・ウィッシュリスト | `feature/review-wishlist` | 5 | `GET/POST /reviews`, `/wishlist` |

> **優先順位:** タスク 1〜4 を Phase 2 の MVP として先行する。

---

## ジュニア開放の判定基準

| 確認項目 | 手段 |
|---|---|
| 全 Docker サービスが起動 | `docker compose up` → 全コンテナ healthy |
| Flyway マイグレーション適用 | ログに `Successfully applied N migrations` |
| 認証ガードが機能している | 保護エンドポイントに無認証でアクセス → `401` |
| ヘルスエンドポイント疎通 | `GET /api/v1/health` → `200 OK` |
| Swagger UI が表示される | `http://localhost:8080/swagger-ui.html` |
| フロントエンドビルドが通る | `pnpm build` が 0 エラー |
| pre-commit フックが機能する | フォーマットを崩してコミット試行 → 弾かれる |
| CI がグリーン | PR を開いて GitHub Actions が全ステップ通過 |
