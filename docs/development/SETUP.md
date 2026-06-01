# 開発環境セットアップガイド

**対象:** バックエンド（Spring Boot）中心。フロントエンドが実装されたら随時追記。

---

## 目次

1. [前提条件](#1-前提条件)
2. [クイックスタート（推奨: Dev Container）](#2-クイックスタート推奨-dev-container)
3. [環境変数一覧](#4-環境変数一覧)
4. [Flyway マイグレーション規則](#5-flyway-マイグレーション規則)
5. [開発ワークフロー](#6-開発ワークフロー)
6. [よくあるトラブル](#7-よくあるトラブル)

---

## 1. 前提条件

| ツール | バージョン | 備考 |
|---|---|---|
| Docker Desktop | 4.x+ | Dev Container 利用時に必須 |
| VS Code | 最新 | Dev Containers 拡張を推奨 |
| Java | 25 | Dev Container 利用時は不要 |
| Git | 任意 | - |

> **推奨:** VS Code + Dev Container を使うことで Java・Node.js・PostgreSQL の環境構築を省略できます。

---

## 2. クイックスタート（推奨: Dev Container）

> **注意:** `.devcontainer/` の設定ファイルは Phase 2 で整備予定です。現時点は §3 の手動セットアップを使用してください。

整備後は以下の手順で起動できます。

```bash
# 1. リポジトリをクローン
git clone <repository-url>
cd kivio

# 2. VS Code でプロジェクトを開く
code .
# → 右下に「Reopen in Container」が表示されたらクリック
# → コンテナビルドが完了するまで待機（初回: 3〜5分）

# 3. 環境変数ファイルを作成
cp kivio-backend/.env.example kivio-backend/.env
# .env を編集して各サービスのキーを設定（§4 参照）

# 4. バックエンド起動
cd kivio-backend
./gradlew bootRun
# → http://localhost:8080 で起動
# → Swagger UI: http://localhost:8080/swagger-ui.html
```

## 3. 環境変数一覧

`kivio-backend/.env`（または OS の環境変数）に設定する。
Spring Boot は `application.yaml` の `${VAR_NAME}` 形式でこれらを参照する。

### 3.1 データベース

| 変数名 | 例 | 説明 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/kivio` | JDBC 接続 URL |
| `DB_USERNAME` | `kivio` | DB ユーザー名 |
| `DB_PASSWORD` | `password` | DB パスワード |

本番環境では Neon または Supabase の接続文字列を使用する。

```
# Neon の例
DB_URL=jdbc:postgresql://ep-xxx.us-east-1.aws.neon.tech/neondb?sslmode=require
```

### 3.2 JWT

| 変数名 | 例 | 説明 |
|---|---|---|
| `JWT_SECRET` | *(32 バイト以上のランダム文字列)* | HS256 署名鍵（Phase 2） |
| `JWT_ACCESS_TOKEN_EXPIRY_SECONDS` | `900` | Access Token 有効期限（15分） |
| `JWT_REFRESH_TOKEN_EXPIRY_SECONDS` | `604800` | Refresh Token 有効期限（7日） |

```bash
# JWT_SECRET の生成例
openssl rand -base64 32
```

> Phase 5 以降で RS256 に移行する際は `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` を追加する（ADR-004 参照）。

### 3.3 Google OAuth

Google Cloud Console → OAuth 2.0 クライアント ID で取得する。

| 変数名 | 説明 |
|---|---|
| `GOOGLE_CLIENT_ID` | OAuth クライアント ID（`xxx.apps.googleusercontent.com`） |
| `GOOGLE_CLIENT_SECRET` | OAuth クライアントシークレット |

**Google Cloud Console の設定:**
- 承認済みリダイレクト URI に `http://localhost:3000/api/auth/callback/google` を追加

### 3.4 Stripe

[Stripe Dashboard](https://dashboard.stripe.com/) → Developers → API keys で取得する。**テストモードのキーを使用すること。**

| 変数名 | 形式 | 説明 |
|---|---|---|
| `STRIPE_SECRET_KEY` | `sk_test_...` | シークレットキー（サーバーサイドのみ） |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` | Webhook 署名検証用シークレット |

**Webhook のローカルテスト:**

```bash
# Stripe CLI でローカル転送
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
# → whsec_xxx が表示されるので STRIPE_WEBHOOK_SECRET に設定
```

### 3.5 Cloudinary

[Cloudinary Console](https://console.cloudinary.com/) → Dashboard で取得する。

| 変数名 | 説明 |
|---|---|
| `CLOUDINARY_CLOUD_NAME` | クラウド名 |
| `CLOUDINARY_API_KEY` | API キー |
| `CLOUDINARY_API_SECRET` | API シークレット |

### 3.6 Resend（メール）

[Resend Dashboard](https://resend.com/) → API Keys で取得する。

| 変数名 | 形式 | 説明 |
|---|---|---|
| `RESEND_API_KEY` | `re_...` | API キー |
| `RESEND_FROM_EMAIL` | `noreply@kivio.example.com` | 送信元メールアドレス |

### 3.7 CORS・アプリ設定

| 変数名 | ローカル値 | 説明 |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 許可するフロントエンドのオリジン |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring プロファイル（`dev` / `prod`） |

### 3.8 `.env.example` テンプレート

```env
# Database
DB_URL=jdbc:postgresql://localhost:5432/kivio
DB_USERNAME=kivio
DB_PASSWORD=password

# JWT
JWT_SECRET=your-32-byte-or-longer-random-secret-here
JWT_ACCESS_TOKEN_EXPIRY_SECONDS=900
JWT_REFRESH_TOKEN_EXPIRY_SECONDS=604800

# Google OAuth
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

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
CORS_ALLOWED_ORIGINS=http://localhost:3000
SPRING_PROFILES_ACTIVE=dev
```

---

## 4. Flyway マイグレーション規則

**ファイルパス:** `kivio-backend/src/main/resources/db/migration/`

### 4.1 命名規則

```
V{version}__{description}.sql
  │           │
  │           └── スネークケース（英語）
  └── 連番整数（1, 2, 3, ...）

例:
  V1__create_users_table.sql
  V2__create_refresh_tokens_table.sql
  V3__create_seller_applications_table.sql
  V10__add_index_to_products_name.sql
```

**ルール:**
- バージョンは整数の連番（小数点は使わない）
- ファイルは一度 Flyway に適用したら**絶対に編集しない**
- 修正が必要な場合は新しいバージョンのファイルを追加する
- ローカルで適用済みのマイグレーションを変更すると `FlywayException: Validate failed` が発生する

### 4.2 テーブル作成のテンプレート

```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    -- ...
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ            DEFAULT NULL  -- soft delete
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_deleted_at ON users (deleted_at) WHERE deleted_at IS NULL;
```

### 4.3 マイグレーションのリセット（ローカル開発時のみ）

```bash
# DB を削除して再作成（本番環境では絶対に実行しない）

# Docker 単体で起動している場合
docker rm -f kivio-db
docker run -d --name kivio-db -e POSTGRES_DB=kivio -e POSTGRES_USER=kivio \
  -e POSTGRES_PASSWORD=password -p 5432:5432 postgres:16

# docker-compose.yml 整備後（Phase 2 以降）
docker compose down -v && docker compose up -d db

# いずれの場合も、起動後に Flyway が全マイグレーションを自動再適用
./gradlew bootRun
```

---

## 5. 開発ワークフロー

### 日常の作業フロー

```bash
# 1. バックエンドを起動
cd kivio-backend
./gradlew bootRun

# 2. コード変更後は Ctrl+C で停止 → 再起動（DevTools は未設定）

# 3. テスト実行
./gradlew test                    # 全テスト
./gradlew test --tests "*UserServiceTest*"  # 特定クラス

# 4. ビルド確認
./gradlew build
```

### コード変更後の確認ポイント

| 変更内容 | 確認方法 |
|---|---|
| エンドポイント追加 | Swagger UI で動作確認 |
| DB スキーマ変更 | 新しい Flyway マイグレーションファイルを追加 |
| 環境変数追加 | `.env.example` も同時に更新 |
| ドメイン間 import | `./gradlew build` でコンパイルエラーを確認 |

---

## 6. よくあるトラブル

### `FlywayException: Validate failed`

**原因:** 適用済みのマイグレーションファイルを編集した。

**解決策:**
```bash
# ローカルのみ: DB を再作成
docker compose down -v && docker compose up -d db
```

### `Port 8080 already in use`

```bash
# 使用中のプロセスを確認・終了
lsof -ti:8080 | xargs kill -9
```

### `JWT signature does not match`

**原因:** `.env` の `JWT_SECRET` が変更された、または設定されていない。

**解決策:** `.env` の `JWT_SECRET` が設定されていることを確認する。JWT シークレットはバックエンドのみで保持する値であり、フロントエンドとは共有しない。再ログインして新しいシークレットで署名されたトークンを取得すれば解消される。

### DB 接続エラー（`Connection refused`）

```bash
# Docker 単体起動の場合: コンテナ状態確認
docker ps -a --filter name=kivio-db

# 停止している場合
docker start kivio-db

# ログ確認
docker logs kivio-db
```

### Stripe Webhook が受信されない

```bash
# Stripe CLI でローカル転送が起動しているか確認
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
# `.env` の STRIPE_WEBHOOK_SECRET が CLI 表示の `whsec_xxx` と一致しているか確認
```
