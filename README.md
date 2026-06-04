# Kivio

マルチベンダー型マーケットプレイス — 個人・小規模事業者向けの低コスト EC プラットフォーム。
ポートフォリオ用途のプロジェクト（実商取引なし）。

## 概要

| 項目 | 内容 |
|---|---|
| コンセプト | 誰でも5分でお店が開ける売買プラットフォーム |
| ロール | バイヤー / セラー / アドミン |
| 現フェーズ | Phase 2（基盤・認証・商品 CRUD 実装中） |

## 技術スタック

**バックエンド**

| カテゴリ | 技術 |
|---|---|
| 言語 / フレームワーク | Java 25 / Spring Boot 4.x |
| セキュリティ | Spring Security + JWT (HS256 → RS256) + OAuth2 |
| ORM / DB | Spring Data JPA + Flyway / PostgreSQL 16 |
| リアルタイム | Spring WebSocket / STOMP |
| ビルド | Gradle (Kotlin DSL) |

**フロントエンド** *(実装未着手)*

| カテゴリ | 技術 |
|---|---|
| フレームワーク | Next.js 16 (App Router) + TypeScript 5 |
| UI | shadcn/ui + Tailwind CSS 4 |
| 認証 | NextAuth.js (Auth.js v5) |
| 状態管理 / データフェッチ | Zustand + TanStack Query 5 |

**外部サービス**

| 用途 | サービス |
|---|---|
| 決済 | Stripe（テストモード） |
| 画像 | Cloudinary |
| メール | Resend |
| OAuth | Google |
| ホスティング | Vercel (FE) / Render or Railway (BE) |

## アーキテクチャ

モジュラーモノリス + DDD（Strategic & Tactical）。将来のマイクロサービス化を見据えた境界設計。

```
Next.js (Vercel)
    │ HTTPS / WSS
Spring Boot API (Render/Railway)
    ├── identity    # 認証・ユーザー・セラー申請
    ├── catalog     # ショップ・商品・カテゴリー
    ├── order       # カート・注文・決済
    ├── messaging   # チャット (WebSocket/STOMP)
    ├── notification
    ├── review
    ├── platform    # PlatformConfig（手数料率等）
    └── audit       # @Auditable AOP・追記専用ログ
    │ JPA
PostgreSQL (Neon / Supabase)
```

ドメイン間の直接 import は禁止。クロスドメイン呼び出しは Application Service（同期）または Spring Events（非同期）経由。

## プロジェクト構成

```
kivio/
├── kivio-backend/        # Spring Boot アプリ
│   └── src/main/java/io/kivio/
│       ├── common/       # PageResponse<T>, 例外, SoftDeletableEntity
│       ├── config/       # Security, WebSocket, OpenAPI, CorrelationIdFilter
│       ├── domain/       # ドメインパッケージ（上記8ドメイン）
│       └── infra/        # Stripe / Cloudinary / Resend / Google 統合
├── kivio-frontend/       # Next.js アプリ（ディレクトリ初期化済み・実装未着手）
├── design-system/        # フロントエンドデザインシステム（MASTER.md）
├── .devcontainer/        # Dev Container 設定
├── adr/                  # Architecture Decision Records (ADR-001〜005)
├── docs/
│   ├── architecture/     # OVERVIEW, DOMAIN_MODEL, SECURITY, AUDIT
│   ├── design/           # API, DB, データ定義, シーケンス, メール, エラーコード, フロントエンド設計
│   ├── development/      # セットアップ, テスト戦略, コーディング規約（BE/FE）, FE設計プロセス
│   └── requirements/     # RFP, ヒアリング, 要件定義書
└── infra/                # IaC（将来）
```

## ローカル開発

### 前提条件

- Docker + Docker Compose
- Java 25 (devcontainer 利用時は不要)
- Node.js 22+ / pnpm（フロントエンド実装後）

### 起動

```bash
# 環境変数ファイルを作成（初回のみ）
cp kivio-backend/.env.example kivio-backend/.env
# .env を編集して各サービスのキーを設定（詳細: docs/development/SETUP.md）

# DB のみ起動（バックエンド単体開発時）
docker compose up -d db

# バックエンド（Spring Boot）
cd kivio-backend
./gradlew bootRun          # → localhost:8080
./gradlew test             # テスト実行
./gradlew clean build      # クリーンビルド

# フロントエンド（実装後）
cd kivio-frontend
pnpm dev                   # → localhost:3000
```

### Dev Container

`.devcontainer/` を VS Code Dev Containers 拡張で開くと、Java・Node.js・PostgreSQL を含む一貫した開発環境を起動できます。

### API ドキュメント

バックエンド起動後、Swagger UI は `http://localhost:8080/swagger-ui.html` で確認できます。

## 主要な設計決定

| ADR | 内容 |
|---|---|
| [ADR-001](adr/ADR-001-modular-monolith.md) | モジュラーモノリス採用 |
| [ADR-002](adr/ADR-002-ddd-adoption.md) | DDD 採用 |
| [ADR-003](adr/ADR-003-soft-delete-strategy.md) | Soft Delete 戦略 |
| [ADR-004](adr/ADR-004-jwt-strategy.md) | JWT 戦略（HS256 → RS256） |
| [ADR-005](adr/ADR-005-uuid-primary-key.md) | 主キーに UUID 採用（`audit_logs` のみ BIGINT 例外） |

## フェーズ別スコープ

| フェーズ | 状態 | 内容 |
|---|---|---|
| Phase 1 | ✅ 完了 | 要件定義・ドキュメント・devcontainer |
| Phase 2 | 🚧 進行中 | 認証・ユーザー・セラー申請・ショップ・商品 CRUD・監査基盤 |
| Phase 3 | 予定 | 商品検索・カート・Stripe 決済・注文管理 |
| Phase 4 | 予定 | リアルタイムチャット・通知・セラーダッシュボード・管理者機能 |
| Phase 5 | 予定 | レビュー・お気に入り・メール通知・デプロイ |

## ドキュメント

**アーキテクチャ・設計**

| ドキュメント | 内容 |
|---|---|
| [アーキテクチャ概要](docs/architecture/OVERVIEW.md) | レイヤー構成・ドメイン間通信 |
| [ドメインモデル](docs/architecture/DOMAIN_MODEL.md) | 集約・Value Object・Domain Events |
| [セキュリティ設計](docs/architecture/SECURITY.md) | JWT 認証・Security Filter Chain |
| [監査ログ設計](docs/architecture/AUDIT.md) | @Auditable AOP |

**API・DB・インフラ設計**

| ドキュメント | 内容 |
|---|---|
| [API 設計](docs/design/API_DESIGN.md) | エンドポイント一覧・レスポンス形式 |
| [DB 設計](docs/design/DB_DESIGN.md) | テーブル定義・インデックス設計 |
| [データ定義書](docs/design/DATA_DICTIONARY.md) | 各テーブルのカラム意味・制約・用語統一 |
| [シーケンスフロー](docs/design/SEQUENCE_FLOW.md) | 認証・注文・決済のシーケンス図 |
| [メール設計](docs/design/EMAIL_DESIGN.md) | メールテンプレート・送信トリガー |
| [エラーコード](docs/design/ERROR_CODES.md) | エラーコード一覧（UPPER_SNAKE_CASE） |

**フロントエンド設計**

| ドキュメント | 内容 |
|---|---|
| [フロントエンド IA](docs/design/frontend/FRONTEND_IA.md) | 画面 URL 一覧・ナビゲーション構造・レイアウト設計 |
| [ユーザーフロー](docs/design/frontend/USER_FLOW.md) | ユーザーフロー図（Mermaid）全フェーズ分 |
| [API コントラクト](docs/design/frontend/FRONTEND_API_CONTRACT.md) | 画面×API 対応表・並列フェッチ・エラー UI・WebSocket |
| [デザインシステム](design-system/MASTER.md) | カラー・タイポグラフィ・コンポーネント仕様（shadcn/ui） |

**開発規約・運用**

| ドキュメント | 内容 |
|---|---|
| [Git ワークフロー](docs/development/GIT_WORKFLOW.md) | ブランチ戦略・PR ルール・コミットメッセージ規約 |
| [セットアップガイド](docs/development/SETUP.md) | 環境変数・Flyway 規則・トラブルシュート |
| [テスト戦略](docs/development/TEST_STRATEGY.md) | テストピラミッド・Testcontainers・モック方針 |
| [バックエンドコーディング規約](docs/development/BACKEND_CODING_STANDARDS.md) | Lombok・レイヤー・例外・テスト等 |
| [フロントエンド設計プロセス](docs/development/FRONTEND_DESIGN_PROCESS.md) | User Flow〜API Contract・各ステップの成果物 |
| [フロントエンドコーディング規約](docs/development/FRONTEND_CODING_STANDARDS.md) | App Router・SC/CC 境界・Zustand・TanStack Query |
| [要件定義書](docs/requirements/REQUIREMENTS.md) | 機能・非機能要件 |
