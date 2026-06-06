# Kivio

マルチベンダー型マーケットプレイス — 個人・小規模事業者が低コストで出店・売買できる EC プラットフォーム。  

バイヤー / セラー / アドミンの3ロールを持ち、商品出品・注文・決済・リアルタイムチャット・レビューを一通りカバーします。

## 技術スタック

**バックエンド**

| カテゴリ | 技術 |
|---|---|
| 言語 / フレームワーク | Java 25 / Spring Boot 4.x |
| セキュリティ | Spring Security + JWT (HS256 → RS256) + OAuth2 (Google) |
| ORM / DB | Spring Data JPA + Flyway / PostgreSQL 17 |
| リアルタイム | Spring WebSocket / STOMP |
| API ドキュメント | SpringDoc OpenAPI (Swagger UI) |
| ビルド | Gradle (Kotlin DSL) + Checkstyle + JaCoCo (80% 閾値) |

**フロントエンド**

| カテゴリ | 技術 |
|---|---|
| フレームワーク | Next.js 16 (App Router) + TypeScript 5 |
| UI | shadcn/ui + Tailwind CSS 4 |
| 認証 | Auth.js v5 (NextAuth.js) |
| 状態管理 / データフェッチ | Zustand 5 + TanStack Query 5 |
| フォーム | React Hook Form + Zod |

**外部サービス**

| 用途 | サービス |
|---|---|
| 決済 | Stripe（テストモード） |
| 画像ストレージ | Cloudinary |
| メール送信 | Resend |
| OAuth | Google |
| ホスティング | Vercel (FE) / Render or Railway (BE) |

## アーキテクチャ

モジュラーモノリス + DDD（Strategic & Tactical）。ドメイン間の直接 import を禁止し、クロスドメイン呼び出しは Application Service（同期）または Spring Events（非同期・副作用）経由に限定しています。

```
Next.js (Vercel)
    │ HTTPS / WSS
Spring Boot API (Render / Railway)
    ├── identity      # 認証・ユーザー・セラー申請
    ├── catalog       # ショップ・商品・カテゴリー
    ├── order         # カート・注文・決済
    ├── messaging     # チャット (WebSocket / STOMP)
    ├── notification  # 通知
    ├── review        # レビュー・お気に入り
    ├── platform      # PlatformConfig（手数料率等）
    └── audit         # @Auditable AOP・追記専用ログ
    │ JPA
PostgreSQL (Neon / Supabase)
```

## プロジェクト構成

```
kivio/
├── kivio-backend/            # Spring Boot アプリ
│   └── src/main/java/io/kivio/
│       ├── common/           # PageResponse<T>, 例外, SoftDeletableEntity
│       ├── config/           # Security, WebSocket, OpenAPI, CorrelationIdFilter
│       ├── domain/           # 8 ドメイン（上記参照）
│       └── infra/            # Stripe / Cloudinary / Resend / Google 統合
├── kivio-frontend/           # Next.js アプリ
│   └── src/
│       ├── app/              # App Router (pages & layouts)
│       ├── components/       # UI コンポーネント
│       ├── hooks/            # カスタムフック
│       ├── lib/              # HTTP クライアント・ユーティリティ
│       ├── stores/           # Zustand ストア
│       └── types/            # 型定義
├── design-system/            # フロントエンドデザインシステム (MASTER.md)
├── .devcontainer/            # Dev Container 設定
├── .github/workflows/        # CI (lint + test + build) / CD (dev deploy)
├── adr/                      # Architecture Decision Records (ADR-001〜005)
└── docs/
    ├── architecture/         # OVERVIEW, DOMAIN_MODEL, SECURITY, AUDIT
    ├── design/               # API, DB, シーケンス, エラーコード, フロントエンド設計
    ├── development/          # コーディング規約, テスト戦略, Git ワークフロー
    └── requirements/         # 要件定義書
```

## ローカル開発

Dev Container を使用します。コンテナ内に Java 25 / Node.js 24 / pnpm が事前インストールされており、PostgreSQL・Redis・Mailpit も自動起動します。

```bash
# 1. リポジトリをクローン（ローカルターミナル）
git clone <repository-url>
cd kivio
code .

# 2. VS Code で開き「Reopen in Container」をクリック（初回: 3〜5 分）

# 以降はコンテナ内ターミナル（/workspace$）で実行

# 3. 環境変数を設定（JWT_SECRET のみ必須。DB/Redis/Mail 接続先は自動設定済み）
cp kivio-backend/.env.example kivio-backend/.env
# JWT_SECRET を設定: openssl rand -base64 32

# 4. バックエンド起動
cd kivio-backend && ./gradlew bootRun
# → http://localhost:8080
# → Swagger UI: http://localhost:8080/swagger-ui.html
# → Mailpit:    http://localhost:8025

# 5. フロントエンド起動
cd kivio-frontend && pnpm install && pnpm dev
# → http://localhost:3000
```

> **Note**
> `kivio-frontend/.env.local` は認証機能（ログイン・セッション）を使う際に必要です。
> 基本的な画面確認のみであれば作成しなくても起動できます。
> ```bash
> cp kivio-frontend/.env.local.example kivio-frontend/.env.local
> # AUTH_SECRET を設定: openssl rand -base64 32
> ```

## 環境変数

### バックエンド (`kivio-backend/.env`)

| 変数 | 必須 | 説明 |
|---|---|---|
| `DB_URL` | ✓ | JDBC URL（例: `jdbc:postgresql://localhost:5432/kivio`） |
| `DB_USERNAME` / `DB_PASSWORD` | ✓ | DB 認証情報 |
| `JWT_SECRET` | ✓ | 256bit 以上のランダム文字列（`openssl rand -base64 32`） |
| `JWT_ACCESS_TOKEN_EXPIRATION` | — | Access Token 有効期限（秒、デフォルト: 900） |
| `JWT_REFRESH_TOKEN_EXPIRATION` | — | Refresh Token 有効期限（秒、デフォルト: 604800） |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | — | Google OAuth（使わない場合は `dummy` を設定） |
| `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` | — | Stripe テストキー |
| `CLOUDINARY_*` | — | Cloudinary 認証情報 |
| `RESEND_API_KEY` | — | Resend メール送信キー |
| `ALLOWED_ORIGINS` | — | CORS 許可オリジン（デフォルト: `http://localhost:3000`） |

### フロントエンド (`kivio-frontend/.env.local`)

| 変数 | 説明 |
|---|---|
| `API_BASE_URL` | バックエンド URL（Server Components 用） |
| `NEXT_PUBLIC_API_BASE_URL` | バックエンド URL（ブラウザ用） |
| `AUTH_SECRET` | NextAuth.js シークレット（`openssl rand -base64 32`） |
| `AUTH_GOOGLE_ID` / `AUTH_GOOGLE_SECRET` | Google OAuth（ソーシャルログイン用） |

## 開発コマンド

```bash
# バックエンド（kivio-backend/）
./gradlew build                      # コンパイル + テスト
./gradlew test                       # ユニット + 統合テスト（Testcontainers）
./gradlew bootRun                    # 開発サーバー起動
./gradlew checkstyleMain             # Checkstyle 静的解析
./gradlew jacocoTestCoverageVerification  # カバレッジ検証（80% 閾値）

# フロントエンド（kivio-frontend/）
pnpm dev          # 開発サーバー起動
pnpm build        # プロダクションビルド
pnpm lint         # ESLint
pnpm typecheck    # TypeScript 型チェック
```

## CI / CD

GitHub Actions で PR・push 時に自動実行されます。

| ジョブ | 内容 |
|---|---|
| Backend | Checkstyle → テスト（Testcontainers）→ JaCoCo カバレッジ検証 → bootJar |
| Frontend | ESLint → TypeScript 型チェック → `next build` |
| CD (develop) | `develop` ブランチへの push 時に dev 環境へ自動デプロイ |

## Docker イメージの動作確認

CI と同じ Docker イメージをローカルで起動し、`bootRun` / `pnpm dev` では再現できない問題（Dockerfile のビルドエラー、コンテナ間の接続設定ミスなど）をデプロイ前に検証できます。

devcontainer 内では Docker が使えないため、**ローカルターミナル（`/kivio`）**から実行します。

```bash
export JWT_SECRET=$(openssl rand -base64 32)
export AUTH_SECRET=$(openssl rand -base64 32)
docker compose up
```

## 主要な設計決定

| ADR | 内容 |
|---|---|
| [ADR-001](adr/ADR-001-modular-monolith.md) | モジュラーモノリス採用 |
| [ADR-002](adr/ADR-002-ddd-adoption.md) | DDD 採用 |
| [ADR-003](adr/ADR-003-soft-delete-strategy.md) | Soft Delete 戦略 |
| [ADR-004](adr/ADR-004-jwt-strategy.md) | JWT 戦略（HS256 → RS256） |
| [ADR-005](adr/ADR-005-uuid-primary-key.md) | 主キーに UUID 採用（`audit_logs` のみ BIGINT 例外） |

## ドキュメント

**アーキテクチャ・設計**

| ドキュメント | 内容 |
|---|---|
| [アーキテクチャ概要](docs/architecture/OVERVIEW.md) | レイヤー構成・ドメイン間通信 |
| [ドメインモデル](docs/architecture/DOMAIN_MODEL.md) | 集約・Value Object・Domain Events |
| [セキュリティ設計](docs/architecture/SECURITY.md) | JWT 認証・Security Filter Chain |
| [監査ログ設計](docs/architecture/AUDIT.md) | `@Auditable` AOP |

**API・DB・インフラ**

| ドキュメント | 内容 |
|---|---|
| [API 設計](docs/design/API_DESIGN.md) | エンドポイント一覧・レスポンス形式 |
| [DB 設計](docs/design/DB_DESIGN.md) | テーブル定義・インデックス設計 |
| [シーケンスフロー](docs/design/SEQUENCE_FLOW.md) | 認証・注文・決済のシーケンス図 |
| [エラーコード](docs/design/ERROR_CODES.md) | エラーコード一覧（UPPER_SNAKE_CASE） |
| [CI/CD](docs/infra/CI_CD.md) | GitHub Actions ワークフロー解説 |
| [Docker ガイド](docs/infra/DOCKER_GUIDE.md) | コンテナ構成・Dev Container 利用方法 |

**フロントエンド設計**

| ドキュメント | 内容 |
|---|---|
| [フロントエンド IA](docs/design/frontend/FRONTEND_IA.md) | 画面 URL 一覧・ナビゲーション構造・レイアウト設計 |
| [ユーザーフロー](docs/design/frontend/USER_FLOW.md) | ユーザーフロー図（Mermaid） |
| [API コントラクト](docs/design/frontend/FRONTEND_API_CONTRACT.md) | 画面×API 対応表・並列フェッチ・WebSocket |
| [デザインシステム](design-system/MASTER.md) | カラー・タイポグラフィ・コンポーネント仕様 |

**開発規約**

| ドキュメント | 内容 |
|---|---|
| [Git ワークフロー](docs/development/GIT_WORKFLOW.md) | ブランチ戦略・PR ルール・コミットメッセージ規約 |
| [テスト戦略](docs/development/TEST_STRATEGY.md) | テストピラミッド・Testcontainers・モック方針 |
| [バックエンドコーディング規約](docs/development/BACKEND_CODING_STANDARDS.md) | Lombok・レイヤー・例外処理 |
| [フロントエンドコーディング規約](docs/development/FRONTEND_CODING_STANDARDS.md) | App Router・SC/CC 境界・TanStack Query |
| [要件定義書](docs/requirements/REQUIREMENTS.md) | 機能・非機能要件 |
