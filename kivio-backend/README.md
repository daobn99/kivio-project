# kivio-backend

Kivio マルチベンダーマーケットプレイスのバックエンド API サーバー。

## 前提条件

| ツール | バージョン |
|---|---|
| Java | 25+ |
| Docker / Docker Compose | 最新安定版 |
| Gradle Wrapper | プロジェクト同梱（`./gradlew`） |

## セットアップ

```bash
# 1. 環境変数ファイルを作成
cp .env.example .env
# .env を編集して各値を設定（特に JWT_SECRET / GOOGLE_CLIENT_* / Stripe / Cloudinary）

# 2. DB のみ起動（ローカル開発）。devcontainer を使用している場合は不要
docker compose up -d db
```

## 起動

```bash
# アプリ起動（dev プロファイル）
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# フル起動（DB + バックエンド + フロントエンド。devcontainer を使用している場合は不要）
docker compose up
```

## テスト

```bash
./gradlew test
./gradlew clean build
```

## 環境変数

`.env.example` を `.env` にコピーして値を設定する。最低限以下が必要：

| 変数名 | 説明 | デフォルト（dev） |
|---|---|---|
| `DB_URL` | PostgreSQL 接続 URL | `jdbc:postgresql://localhost:5432/kivio` |
| `DB_USERNAME` | DB ユーザー名 | `kivio` |
| `DB_PASSWORD` | DB パスワード | `password` |
| `JWT_SECRET` | JWT 署名シークレット（32 バイト以上） | dev 用固定値 |
| `JWT_ACCESS_TOKEN_EXPIRATION` | Access Token 有効期限（秒） | `900`（15 分） |
| `JWT_REFRESH_TOKEN_EXPIRATION` | Refresh Token 有効期限（秒） | `604800`（7 日） |
| `GOOGLE_CLIENT_ID` | Google OAuth クライアント ID | `dummy`（OAuth 不使用時） |
| `GOOGLE_CLIENT_SECRET` | Google OAuth クライアントシークレット | `dummy`（OAuth 不使用時） |
| `REDIS_URL` | Redis 接続 URL（レート制限） | `redis://localhost:6379` |
| `MAIL_HOST` | メール送信ホスト（ローカル: Mailpit） | `localhost` |
| `MAIL_PORT` | メール送信ポート | `1025` |
| `STRIPE_SECRET_KEY` | Stripe シークレットキー（`sk_test_...`） | - |
| `STRIPE_WEBHOOK_SECRET` | Stripe Webhook 署名シークレット（`whsec_...`） | - |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary クラウド名 | - |
| `CLOUDINARY_API_KEY` | Cloudinary API キー | - |
| `CLOUDINARY_API_SECRET` | Cloudinary API シークレット | - |
| `RESEND_API_KEY` | Resend API キー（`re_...`） | - |
| `RESEND_FROM_EMAIL` | 送信元メールアドレス | - |
| `ALLOWED_ORIGINS` | CORS 許可オリジン（カンマ区切り） | `http://localhost:3000` |
| `PROBLEM_BASE_URL` | RFC 9457 エラー type URI のベース URL | `https://kivio.example.com` |
| `SPRING_PROFILES_ACTIVE` | Spring プロファイル | `dev` |

レート制限のデフォルト値（変更不要な場合は省略可）：

| 変数名 | 説明 | デフォルト |
|---|---|---|
| `RATE_LIMIT_AUTH_CAPACITY` | 認証系 req/min/IP | `10` |
| `RATE_LIMIT_API_CAPACITY` | API 全般 req/min/user | `100` |
| `RATE_LIMIT_PUBLIC_CAPACITY` | 公開エンドポイント req/min | `30` |

```bash
# JWT_SECRET の生成例
openssl rand -base64 32
```

### `.env.example` テンプレート

```env
# Database
# devcontainer/docker-compose 使用時は db:5432 のまま。ホスト直接起動時は localhost:5432 に変更する。
DB_URL=jdbc:postgresql://db:5432/kivio
DB_USERNAME=kivio
DB_PASSWORD=password

# Redis (レート制限に使用)
# devcontainer 使用時は redis://redis:6379 のまま
REDIS_URL=redis://localhost:6379

# Mail (ローカル: Mailpit、本番: Resend)
# devcontainer 使用時は MAIL_HOST=mailpit に変更する
MAIL_HOST=localhost
MAIL_PORT=1025

# JWT
# JWT_SECRET は必須。未設定だとアプリが起動しません。
JWT_SECRET=your-32-byte-or-longer-random-secret-here
JWT_ACCESS_TOKEN_EXPIRATION=900
JWT_REFRESH_TOKEN_EXPIRATION=604800

# Google OAuth
# OAuth を使わない場合もダミー値が必要（空白だと起動失敗）
GOOGLE_CLIENT_ID=dummy
GOOGLE_CLIENT_SECRET=dummy

# Stripe (test mode only)
STRIPE_SECRET_KEY=sk_test_
STRIPE_WEBHOOK_SECRET=whsec_

# Cloudinary
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=

# Resend
RESEND_API_KEY=re_
RESEND_FROM_EMAIL=noreply@example.com

# App
ALLOWED_ORIGINS=http://localhost:3000
PROBLEM_BASE_URL=https://kivio.example.com
SPRING_PROFILES_ACTIVE=dev
```

**本番環境の DB URL（Neon 例）:**

```
DB_URL=jdbc:postgresql://ep-xxx.us-east-1.aws.neon.tech/neondb?sslmode=require
```

**Stripe Webhook のローカルテスト:**

```bash
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
# → 表示された whsec_xxx を STRIPE_WEBHOOK_SECRET に設定
```

**Google Cloud Console の設定:**  
承認済みリダイレクト URI に `http://localhost:3000/api/auth/callback/google` を追加する。

## Flyway マイグレーション規則

**ファイルパス:** `src/main/resources/db/migration/`

### 命名規則

```
V{version}__{description}.sql
例:
  V1__create_users_table.sql
  V2__create_refresh_tokens_table.sql
  V10__add_index_to_products_name.sql
```

- バージョンは整数の連番（小数点不可）
- 一度 Flyway に適用したファイルは**絶対に編集しない**（`FlywayException: Validate failed` が発生する）
- 修正が必要な場合は新しいバージョンのファイルを追加する

### テーブル作成テンプレート

```sql
CREATE TABLE users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ            DEFAULT NULL  -- soft delete
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_deleted_at ON users (deleted_at) WHERE deleted_at IS NULL;
```

### マイグレーションのリセット（ローカル開発時のみ）

```bash
# DB を削除して再作成（本番環境では絶対に実行しない）
docker compose down -v && docker compose up -d db
# 起動後に Flyway が全マイグレーションを自動再適用
./gradlew bootRun
```

---

## 開発ワークフロー

```bash
# バックエンドを起動
./gradlew bootRun

# テスト実行
./gradlew test
./gradlew test --tests "*UserServiceTest*"  # 特定クラス

# ビルド確認
./gradlew build
```

| 変更内容 | 確認方法 |
|---|---|
| エンドポイント追加 | Swagger UI で動作確認 |
| DB スキーマ変更 | 新しい Flyway マイグレーションファイルを追加 |
| 環境変数追加 | `.env.example` も同時に更新 |
| ドメイン間 import | `./gradlew build` でコンパイルエラーを確認 |

---

## よくあるトラブル

### `FlywayException: Validate failed`

適用済みのマイグレーションファイルを編集した場合に発生する。

```bash
# ローカルのみ: DB を再作成
docker compose down -v && docker compose up -d db
```

### `Port 8080 already in use`

```bash
lsof -ti:8080 | xargs kill -9
```

### `JWT signature does not match`

`.env` の `JWT_SECRET` が未設定または変更された場合に発生する。`.env` を確認し、再ログインして新しいトークンを取得する。

### DB 接続エラー（`Connection refused`）

```bash
docker ps -a --filter name=kivio-db   # コンテナ状態確認
docker start kivio-db                  # 停止していた場合
docker logs kivio-db                   # ログ確認
```

### Stripe Webhook が受信されない

```bash
# Stripe CLI でローカル転送が起動しているか確認
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
# .env の STRIPE_WEBHOOK_SECRET が CLI 表示の whsec_xxx と一致しているか確認
```

---

## API ドキュメント

起動後に以下 URL で Swagger UI を確認できます（dev プロファイルのみ）：

```
http://localhost:8080/swagger-ui.html    # Swagger UI
http://localhost:8080/v3/api-docs        # OpenAPI JSON
```

> **Note:** prod プロファイルでは Swagger UI・OpenAPI エンドポイントは無効化されます。

## ドキュメント参照

- [アーキテクチャ概要](../docs/architecture/OVERVIEW.md)
- [セキュリティ設計](../docs/architecture/SECURITY.md)
- [DB 設計](../docs/design/DB_DESIGN.md)
- [API 設計](../docs/design/API_DESIGN.md)
- [バックエンドコーディング規約](../docs/development/BACKEND_CODING_STANDARDS.md)
