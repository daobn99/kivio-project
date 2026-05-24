# Kivio — CLAUDE.md

## Commands

### Backend (kivio-backend/)
```bash
./gradlew build          # compile + test
./gradlew test           # unit + integration tests
./gradlew bootRun        # dev server → localhost:8080
./gradlew clean build    # clean build
```

### Frontend (kivio-frontend/) — 未作成
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

## Architecture: Modular Monolith

Package root: `com.kivio`

```
com.kivio/
├── common/         # PageResponse<T>, exceptions, base entities
├── config/         # Spring config (Security, WebSocket, OpenAPI)
├── domain/
│   ├── identity/   # User, Auth, SellerApplication
│   ├── catalog/    # Shop, Product, Category
│   ├── order/      # Cart, Order, Payment
│   ├── messaging/  # ChatRoom, ChatMessage
│   ├── notification/
│   ├── review/
│   ├── platform/   # PlatformConfig
│   └── audit/      # AuditLog, @Auditable AOP
└── infra/          # external: Stripe, Cloudinary, Resend
```

**制約:** `domain` パッケージ間の直接 import 禁止。
クロスドメイン呼び出しは Application Service または Spring Events 経由。

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

### Security
- パスワード: BCrypt cost factor 12
- JWT: RS256 または HS256、Access Token 15分 / Refresh Token 7日
- レート制限: 認証系 10 req/min/IP、API全般 100 req/min/user

## Current Phase

**Phase 1**（要件定義・ドキュメント・devcontainer）— 完了  
**Phase 2** 次: Auth / User / SellerApplication / Shop / Product CRUD / Audit 基盤

参照: `docs/REQUIREMENTS.md`（全要件）、`adr/`（設計判断）
