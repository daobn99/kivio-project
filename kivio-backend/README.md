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

`.env.example` を参照。最低限以下が必要：

| 変数名 | 説明 | デフォルト（dev） |
|---|---|---|
| `DB_URL` | PostgreSQL 接続 URL | `jdbc:postgresql://localhost:5432/kivio` |
| `DB_USERNAME` | DB ユーザー名 | `kivio` |
| `DB_PASSWORD` | DB パスワード | `password` |
| `JWT_SECRET` | JWT 署名シークレット（32 バイト以上） | dev 用固定値 |
| `JWT_ACCESS_TOKEN_EXPIRATION` | Access Token 有効期限（秒） | `900`（15 分） |
| `JWT_REFRESH_TOKEN_EXPIRATION` | Refresh Token 有効期限（秒） | `604800`（7 日） |
| `GOOGLE_CLIENT_ID` | Google OAuth クライアント ID | - |
| `GOOGLE_CLIENT_SECRET` | Google OAuth クライアントシークレット | - |
| `ALLOWED_ORIGINS` | CORS 許可オリジン（カンマ区切り） | `http://localhost:3000` |
| `PROBLEM_BASE_URL` | RFC 9457 エラー type URI のベース URL | `https://kivio.example.com` |

レート制限のデフォルト値（変更不要な場合は省略可）：

| 変数名 | 説明 | デフォルト |
|---|---|---|
| `RATE_LIMIT_AUTH_CAPACITY` | 認証系 req/min/IP | `10` |
| `RATE_LIMIT_API_CAPACITY` | API 全般 req/min/user | `100` |
| `RATE_LIMIT_PUBLIC_CAPACITY` | 公開エンドポイント req/min | `30` |

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
