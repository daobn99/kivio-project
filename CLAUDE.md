# Kivio — CLAUDE.md

## Commands

### Backend (kivio-backend/)
```bash
./gradlew build          # compile + test
./gradlew test           # unit + integration tests
./gradlew bootRun        # dev server → localhost:8080
./gradlew clean build    # clean build
```

### Frontend (kivio-frontend/) — ディレクトリ初期化済み・実装未着手
```bash
pnpm dev                 # dev server → localhost:3000
pnpm build
pnpm lint
```

### Docker (project root)
```bash
docker compose up        # PostgreSQL + backend + frontend
docker compose up -d db  # DB only
```

## Architecture: Modular Monolith + DDD

設計方針: モジュラーモノリスに DDD（Strategic + Tactical）を採用。
詳細: `docs/architecture/OVERVIEW.md`、`docs/architecture/DOMAIN_MODEL.md`

Package root: `io.kivio`

```
io.kivio/
├── common/         # PageResponse<T>, exceptions, base entities, SoftDeletableEntity
├── config/         # Spring config (Security, WebSocket, OpenAPI, CorrelationIdFilter)
├── domain/
│   ├── identity/   # User, RefreshToken, SellerApplication
│   ├── catalog/    # Shop, Product, Category
│   ├── order/      # Cart, Order, Payment, Address
│   ├── messaging/  # ChatRoom, ChatMessage
│   ├── notification/ # Notification
│   ├── review/     # Review, Wishlist
│   ├── platform/   # PlatformConfig
│   └── audit/      # AuditLog, @Auditable, AuditLogAspect
└── infra/          # external: Stripe, Cloudinary, Resend, Google OAuth
```

各ドメインパッケージ内部構成（統一）:

```
domain/{context}/
├── controller/   # @RestController
├── service/      # @Service + @Transactional（Application Service）
├── domain/       # 集約ルート・Entity・Value Object・Domain Event
├── repository/   # @Repository インターフェース
└── dto/          # Request/Response DTO, Mapper
```

**制約:**
- `domain` パッケージ間の直接 import 禁止
- クロスドメイン呼び出しは Application Service（同期）または Spring Events（非同期・副作用）経由
- 集約内のエンティティは集約ルート経由でのみ変更する
- Repository は集約ルートごとに 1 つ

## Conventions

### API
- ベースパス: `/api/v1`
- エラー: RFC 9457 `ProblemDetail`（`Content-Type: application/problem+json`）
- ページネーション: `PageResponse<T>` でラップ — Spring の `Page<T>` を直接返さない
- JSON フィールド: `camelCase`
- 日時: ISO 8601 UTC（例: `2026-05-24T10:00:00Z`）
- 金額: 整数・円単位（¥1,500 → `1500`）
- 部分更新は常に `PATCH` — `PUT` は使わない
- エラーコード: `UPPER_SNAKE_CASE`（例: `PRODUCT_OUT_OF_STOCK`）

### Implementation Tools
- ボイラープレート: Lombok（`@RequiredArgsConstructor`, `@Builder`, `@Getter`, `@Slf4j`）
- 設定値: `application.yml`（cron 式・JWT 有効期限・Rate Limit 値等は外部化）

### Soft Delete
- `users` / `shops` / `categories`: `deleted_at` タイムスタンプ（物理削除禁止）
- `products`: `status = 'DELETED'`（`deleted_at` カラムなし）
- `orders` / `payments`: 削除操作を提供しない（会計記録）
- 実装: `SoftDeletableEntity`（`@MappedSuperclass`）+ `@SQLRestriction("deleted_at IS NULL")`

### Audit Log
- `audit_logs` は追記専用 — `UPDATE` / `DELETE` 禁止
- `@Auditable` AOP でサービス層に横断実装。ビジネスロジックに監査コードを混在させない
- アクション名: `{ENTITY}_{VERB}` `UPPER_SNAKE_CASE`（例: `ORDER_CANCELLED`）
- `correlation_id` は MDC 経由でリクエスト開始時に生成・伝搬
- 詳細: `docs/architecture/AUDIT.md`

### Security
- パスワード: BCrypt cost factor 12
- JWT: HS256（Phase 2）/ RS256（Phase 5+）、Access Token 15分 / Refresh Token 7日
- Refresh Token: SHA-256 ハッシュを DB 保存・ログアウト時に削除・リフレッシュごとにローテーション
- レート制限: 認証系 10 req/min/IP、API全般 100 req/min/user
- 詳細: `docs/architecture/SECURITY.md`

## Current Phase

**Phase 1**（要件定義・ドキュメント・devcontainer）— 完了  
**Phase 2** 進行中: Auth / User / SellerApplication / Shop / Product CRUD / Audit 基盤

## References

| ドキュメント | 内容 |
|---|---|
| `docs/requirements/REQUIREMENTS.md` | 全要件・機能仕様 |
| `docs/architecture/OVERVIEW.md` | システム構成・レイヤー・ドメイン間通信 |
| `docs/architecture/DOMAIN_MODEL.md` | DDD 集約・Value Object・Domain Events |
| `docs/architecture/SECURITY.md` | JWT 認証・Security Filter Chain |
| `docs/architecture/AUDIT.md` | `@Auditable` AOP・監査ログ設計 |
| `docs/development/BACKEND_CODING_STANDARDS.md` | バックエンドコーディング規約（Lombok・レイヤー・例外・テスト等）|
| `docs/development/FRONTEND_DESIGN_PROCESS.md` | フロントエンド設計手順書（User Flow〜API Contract・各ステップの成果物）|
| `docs/development/FRONTEND_CODING_STANDARDS.md` | フロントエンドコーディング規約（App Router・SC/CC境界・Zustand・TanStack Query・フォーム・型定義等）|
| `docs/development/FRONTEND_TEST_STRATEGY.md` | フロントエンドテスト戦略（Vitest・RTL・Playwright・MSW・TanStack Query テスト方針）|
| `docs/design/frontend/FRONTEND_IA.md` | 画面URL一覧・ナビゲーション構造・レイアウト設計・デザイン方針 |
| `docs/design/frontend/USER_FLOW.md` | ユーザーフロー図（Mermaid）全フェーズ分 |
| `docs/design/frontend/FRONTEND_API_CONTRACT.md` | フロントエンド視点のAPIコントラクト（画面×API対応表・並列フェッチ・エラーUI・WebSocket） |
| `docs/design/API_DESIGN.md` | エンドポイント一覧・レスポンス形式 |
| `docs/design/DB_DESIGN.md` | テーブル定義・インデックス設計 |
| `docs/design/DATA_DICTIONARY.md` | データ定義書（各テーブルのカラム意味・制約・用語統一） |
| `docs/design/SEQUENCE_FLOW.md` | シーケンス図（認証・注文・決済フロー） |
| `docs/design/EMAIL_DESIGN.md` | メールテンプレート設計 |
| `docs/design/ERROR_CODES.md` | エラーコード一覧（UPPER_SNAKE_CASE） |
| `design-system/MASTER.md` | フロントエンドデザインシステム（カラー・タイポグラフィ・コンポーネント仕様） |
| `adr/` | 設計判断の記録（ADR-001〜005） |
