# 認証機能 実装計画 / 進捗管理
**ブランチ:** `feature/auth`  
**担当 Phase:** Phase 2  
**最終更新:** 2026-06-11  
**ステータス:** 🟡 実装中（**バックエンド完了**：OTP + Redis 方式（[ADR-006](../../adr/ADR-006-email-otp-redis.md)）への改修を実施し `./gradlew build` グリーン。残りはフロントエンド T-12〜T-20）

> **⚠️ 設計変更の経緯（2026-06-11）:**  
> メール確認方式を「登録時に `users` 作成 → マジックリンク（`/auth/verify-email`）」から、**「メール認証コード（OTP）+ Redis 一時ストレージ」**へ変更した（[ADR-006](../../adr/ADR-006-email-otp-redis.md)）。`users` レコードはメール認証完了後にのみ作成する。  
> これに伴い `email_verification_tokens` テーブル・`users.email_verified` 列・`POST /auth/verify-email`・`EMAIL_NOT_VERIFIED` を**廃止**し、`POST /auth/register/{request-otp,verify-otp,complete}` の 3 ステップへ置き換える。旧方式で実装済みの backend（T-01〜T-11）は本ドキュメントの「要改修」項目に従って改修する。

---

## 目次

1. [Overview](#1-overview)
2. [Required References](#2-required-references)
3. [API Contract](#3-api-contract)
4. [DB Migration / Redis / Seed Plan](#4-db-migration--redis--seed-plan)
5. [Backend Implementation Plan](#5-backend-implementation-plan)
6. [Frontend Implementation Plan](#6-frontend-implementation-plan)
7. [Task Breakdown](#7-task-breakdown)
8. [Security Checklist](#8-security-checklist)
9. [Test Checklist](#9-test-checklist)
10. [Definition of Done](#10-definition-of-done)
11. [Risks / Open Questions](#11-risks--open-questions)

---

## 1. Overview

### スコープ

`feature/auth` ブランチが担当するのは以下の認証フロー一式。

| フロー | エンドポイント |
|---|---|
| メールアドレス重複チェック（インラインUX・任意） | `POST /api/v1/auth/check-email` |
| 認証コード（OTP）送信（登録ステップ1） | `POST /api/v1/auth/register/request-otp` |
| 認証コード（OTP）検証（登録ステップ2） | `POST /api/v1/auth/register/verify-otp` |
| パスワード設定・登録完了・自動ログイン（登録ステップ3） | `POST /api/v1/auth/register/complete` |
| メール＋パスワードログイン | `POST /api/v1/auth/login` |
| Google OAuth ログイン | `POST /api/v1/auth/google` |
| トークンリフレッシュ | `POST /api/v1/auth/refresh` |
| ログアウト | `POST /api/v1/auth/logout` |

> **設計方針:** `users` レコードはメール認証（OTP）完了後にのみ作成する。OTP・登録セッション（`registrationToken`）は **Redis に TTL 付きで一時保存**し、DB テーブルを持たない。これにより「未確認アカウントの滞留」「未確認状態での即ログイン」「期限切れリンクの再送信」を構造的に排除する（[ADR-006](../../adr/ADR-006-email-otp-redis.md)）。

### スコープ外（シニア先行実装済みを前提とする）

以下はスキャフォールディング（フェーズ 1-3 / 1-4 / 1-5）として先行実装されていること。

- `SecurityConfig`（Filter Chain 定義・公開エンドポイント設定）
- `JwtAuthenticationFilter`（既存リクエストへのトークン検証）
- `CorrelationIdFilter`・`GlobalExceptionHandler`・`RateLimitingFilter`
- Flyway 基底マイグレーション（`V1__init_schema.sql`）

### 実装順序サマリー

```
Redis インフラ導入（build.gradle / docker-compose / application.yml / RedisConfig）
  └─► DB Migration（email_verification_tokens・users.email_verified の廃止）
        └─► Backend: Entity / Repository / Redis Store / Service / Controller
              └─► Frontend: API クライアント / Zod スキーマ / UI（3 ステップ）/ ページ
```

---

## 2. Required References

実装前にタスクに関連するドキュメントのみを必ず通読すること。

| ドキュメント | 参照箇所 | 理由 |
|---|---|---|
| `adr/ADR-006-email-otp-redis.md` | 全体 | **OTP + Redis 方式の設計判断・Redis キー設計・廃止項目** |
| `docs/architecture/SECURITY.md` | §1〜§6 全体 + §4 Rate Limiting | JWT 設計・BCrypt 設定・Refresh Token Rotation・Google ID Token 検証・OTP のメール単位スロットリング |
| `docs/architecture/OVERVIEW.md` | アーキ図・技術スタック | Redis（Spring Data Redis / Lettuce）の位置づけ |
| `docs/design/API_DESIGN.md` | §2 認証 (Auth) | 全エンドポイントのリクエスト/レスポンス仕様・エラーコード |
| `docs/design/DB_DESIGN.md` | §3.1 users, §3.2 refresh_tokens, §3.3 OTP（Redis） | テーブル定義・制約・業務ルール・Redis 一時ストレージ |
| `docs/design/SEQUENCE_FLOW.md` | §1.1〜§1.3 | メール登録（OTP）・ログイン・Google OAuth の詳細シーケンス図 |
| `docs/design/ERROR_CODES.md` | §2.2 認証・ユーザー | 認証系エラーコード一覧（`OTP_*` / `REGISTRATION_SESSION_INVALID`） |
| `docs/design/EMAIL_DESIGN.md` | AUTH-01 | OTP メールテンプレート・`sendRegistrationOtp` 仕様 |
| `docs/development/BACKEND_CODING_STANDARDS.md` | 全体 | レイヤー責務・Lombok 使用方法・例外設計・テスト方針 |
| `docs/development/FRONTEND_CODING_STANDARDS.md` | §1〜§10 | ディレクトリ構成・SC/CC 境界・TanStack Query・Zustand・フォーム規約 |
| `docs/development/FRONTEND_TEST_STRATEGY.md` | 全体 | Vitest/RTL/Playwright の使い方・MSW モック方針 |
| `docs/design/frontend/FRONTEND_API_CONTRACT.md` | §6.1 認証関連画面 | 画面×API 対応表・エラー UI 仕様 |
| `adr/ADR-004-jwt-strategy.md` | 全体 | JWT アルゴリズム選定理由（HS256 採用の背景） |
| `adr/ADR-005-uuid-primary-key.md` | 全体 | UUID 主キー採用理由 |
| `CLAUDE.md` | Conventions セクション | API・Soft Delete・Audit Log 規約 |

---

## 3. API Contract

### 3.1 共通仕様

- ベースパス: `/api/v1`
- 認証不要: `check-email`, `register/request-otp`, `register/verify-otp`, `register/complete`, `login`, `google`, `refresh`
- 認証必須: `logout`（`Authorization: Bearer <accessToken>`）
- エラー形式: RFC 9457 `ProblemDetail`（`Content-Type: application/problem+json`）
- 各エンドポイントの完全なリクエスト/レスポンス仕様は `docs/design/API_DESIGN.md §2` を参照すること

### 3.2 エンドポイント一覧

| エンドポイント | 認証 | 主なリクエストフィールド | レスポンス | 主なエラーコード |
|---|---|---|---|---|
| `POST /auth/check-email` | 不要 | `email` | 200 `{ available }`（登録済みは `available: false`） | `VALIDATION_FAILED` |
| `POST /auth/register/request-otp` | 不要 | `email` | 202 `{ message, expiresInSeconds }` | `EMAIL_ALREADY_REGISTERED`, `RATE_LIMIT_EXCEEDED`, `VALIDATION_FAILED` |
| `POST /auth/register/verify-otp` | 不要 | `email`, `otp`（6 桁） | 200 `{ registrationToken, expiresInSeconds }` | `OTP_INVALID`, `OTP_EXPIRED`, `OTP_MAX_ATTEMPTS_EXCEEDED`, `VALIDATION_FAILED` |
| `POST /auth/register/complete` | 不要 | `registrationToken`, `password`, `passwordConfirm`, `displayName?` | 201 Access + Refresh Token（自動ログイン） | `REGISTRATION_SESSION_INVALID`, `EMAIL_ALREADY_REGISTERED`, `VALIDATION_FAILED` |
| `POST /auth/login` | 不要 | `email`, `password` | 200 Access + Refresh Token | `INVALID_CREDENTIALS`, `USER_DEACTIVATED` |
| `POST /auth/google` | 不要 | `idToken`（Google ID Token） | 200 Access + Refresh Token | `GOOGLE_TOKEN_INVALID`, `USER_DEACTIVATED` |
| `POST /auth/refresh` | 不要 | `refreshToken` | 200 新 Access + 新 Refresh Token | `REFRESH_TOKEN_INVALID` |
| `POST /auth/logout` | 必須 | `refreshToken` | 204 No Content | — |

**補足事項:**
- `POST /auth/register/request-otp`: メール重複チェック後に 6 桁 OTP を生成し `reg:otp:{email}`（Redis, TTL 10 分）へ SHA-256 ハッシュ + 試行回数で保存、`EmailSender` 経由で送信する（dev: Mailpit / prod: Resend）。**この時点では `users` を作成しない。**
- `POST /auth/register/verify-otp`: OTP 一致で `reg:otp:{email}` を削除し、不透明な `registrationToken`（UUID v4）を発行して `reg:session:{registrationToken}` → `{email}`（Redis, TTL 30 分）へ保存する。検証失敗ごとに試行回数を加算し 5 回で OTP を失効させる。
- `POST /auth/register/complete`: `registrationToken` から認証済みメールを取得して `users` を新規作成（`ROLE_BUYER`）、`registrationToken` を削除し Access + Refresh Token を返して自動ログインさせる。完了は冪等でない（トークンはワンタイム消費）。
- `POST /auth/login`: メール認証はすでに完了済みのユーザーのみが `users` に存在するため、**メール確認状態のチェックは行わない**（`EMAIL_NOT_VERIFIED` は廃止）。
- `POST /auth/google`: 同メールの既存アカウントがある場合は `google_id` を紐づけて統合する（新規作成は行わない）。Google 検証済みのため確認フラグは持たない。
- `POST /auth/refresh`: Token Rotation により、レスポンスに**新しい** Refresh Token を含める（旧トークンは無効化）。

### 3.3 JWT ペイロード

Access Token のペイロードには `sub`（user_id UUID）と `role` のみを含める。ユーザーの詳細情報（名前・メール等）は含めない。詳細は `docs/architecture/SECURITY.md §2.2` を参照。

---

## 4. DB Migration / Redis / Seed Plan

### 4.1 Redis インフラ導入

OTP・登録セッションの一時ストレージとして Redis を導入する（[ADR-006](../../adr/ADR-006-email-otp-redis.md)）。

- `build.gradle`: `implementation 'org.springframework.boot:spring-boot-starter-data-redis'` を追加
- `docker-compose.yml`（プロジェクトルート）: `redis` サービスを追加（devcontainer には既存。本番想定の接続を main の compose にも追加する）
- `application.yml`: `spring.data.redis.{host,port,password}` を環境変数で外部化（`REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`）。OTP/セッションの TTL・桁数・試行上限・スロットリング値も `application.yml` に外部化する（例: `auth.otp.ttl=PT10M`, `auth.otp.length=6`, `auth.otp.max-attempts=5`, `auth.registration-session.ttl=PT30M`, `auth.otp.resend-cooldown=PT60S`, `auth.otp.max-per-hour=5`）
- `RedisConfig`: `StringRedisTemplate` を利用（値は JSON 文字列 or Hash）。専用 `ObjectMapper` 設定が必要なら Bean を定義

#### Redis キー設計

| キー | 値 | TTL | 用途 |
|---|---|---|---|
| `reg:otp:{email}` | `{otpHash: SHA-256(otp), attempts: int}` | 10 分 | OTP 検証。試行 5 回超過で失効 |
| `reg:session:{registrationToken}` | `{email}` | 30 分 | OTP 検証済みメールの登録セッション |
| `reg:otp:cooldown:{email}` | `1`（存在のみ） | 60 秒 | 再送信クールダウン（存在時は再送信拒否） |
| `reg:otp:count:{email}` | `int` | 1 時間 | 1 時間あたりの送信回数（上限 5） |

> OTP 平文・パスワードは Redis に保存しない（OTP は SHA-256 ハッシュのみ）。`registrationToken` は不透明な UUID v4。

### 4.2 Migration ファイル

`V1__init_schema.sql` はシニアが基底として作成済み。認証機能の追加テーブルは `V2__auth_tables.sql` で作成済み（旧方式）。**OTP + Redis 方式では `email_verification_tokens` テーブルと `users.email_verified` 列が不要**になるため、以下のいずれかで除去する。

```
kivio-backend/src/main/resources/db/migration/
├── V2__auth_tables.sql        # 既存（旧方式で email_verification_tokens を含む）
└── V3__drop_email_verification.sql  # ★ 追加（OTP 方式への移行）
```

- **`V2` がまだ `main` にマージされていない場合:** `V2__auth_tables.sql` を直接修正し、`email_verification_tokens` テーブルと `users.email_verified` 列の追加を削除する（マイグレーション履歴を汚さない）。
- **`V2` がマージ済み／適用済みの場合:** `V3__drop_email_verification.sql` を追加し、`DROP TABLE email_verification_tokens;` と `ALTER TABLE users DROP COLUMN email_verified;` を行う（適用済みマイグレーションは不変のため）。

**最終的な認証関連テーブル（OTP 方式）:**

- `users`（`email_verified` 列なし。`DB_DESIGN.md §3.1`）
- `refresh_tokens`（`DB_DESIGN.md §3.2`。`user_id`・`token_hash` にインデックス）
- メール認証コード（OTP）・登録セッションは **DB テーブルを持たず Redis**（`DB_DESIGN.md §3.3`）

### 4.3 Seed Data

| 内容 | 要否 | 理由 |
|---|---|---|
| テスト用ユーザー（BUYER） | 必要 | ローカル開発・E2E テストで即座に動作確認できるようにする |
| テスト用ユーザー（SELLER） | 任意 | Seller 画面の疎通確認に有用 |
| テスト用ユーザー（ADMIN） | 必要 | 管理者機能の動作確認に必須 |
| Google OAuth 設定 | 不要 | `platform_configs` テーブルは Phase 2 スコープ外 |

Seed ファイルは `V99__seed_dev.sql` としてマイグレーションとは分離し、`application-dev.yaml` でのみ適用する（本番実行禁止）。Seed ユーザーは**メール認証済みの完全なユーザー**として直接 INSERT する（OTP フローを経由しない）。

パスワードは共通の開発用パスワードを BCrypt（cost 12）でハッシュ化した値を使用すること。ハッシュ値は事前生成して埋め込む。

---

## 5. Backend Implementation Plan

### 5.1 前提条件

以下がシニア実装済みであること（ブランチ受け取り前に確認する）。

- [ ] `SecurityConfig`（Filter Chain・公開パス設定。`/api/v1/auth/register/**` を `permitAll`）が存在する
- [ ] `JwtAuthenticationFilter` が動作している
- [ ] `GlobalExceptionHandler`（ProblemDetail 形式）が存在する
- [ ] `SoftDeletableEntity` が `io.kivio.common` に存在する
- [ ] `V1__init_schema.sql` が適用済みで `users` テーブルが存在する
- [ ] Redis が起動している（docker-compose / devcontainer）

### 5.2 実装ファイル一覧

パッケージルート: `io.kivio.domain.identity`

```
io.kivio/
├── config/
│   └── RedisConfig.java                     # ★ 追加（StringRedisTemplate / ObjectMapper）
├── domain/identity/
│   ├── controller/
│   │   └── AuthController.java              # 🔄 改修（登録 3 エンドポイント）
│   ├── service/
│   │   ├── AuthService.java                 # 🔄 改修（requestOtp / verifyOtp / completeRegistration）
│   │   ├── OtpService.java                  # ★ 追加（OTP 生成・保存・検証・スロットリング / Redis）
│   │   └── RegistrationSessionService.java  # ★ 追加（registrationToken 発行・検証・消費 / Redis）
│   ├── domain/
│   │   ├── User.java                        # 🔄 改修（email_verified / verifyEmail() を削除）
│   │   ├── RefreshToken.java                # 既存
│   │   ├── UserRole.java                    # 既存（enum）
│   │   ├── UserStatus.java                  # 既存（enum）
│   │   └── vo/
│   │       └── Email.java                   # Value Object（@Embeddable）
│   │   # ❌ EmailVerificationToken.java を削除
│   ├── repository/
│   │   ├── UserRepository.java              # 既存
│   │   └── RefreshTokenRepository.java      # 既存
│   │   # ❌ EmailVerificationTokenRepository.java を削除
│   └── dto/
│       ├── request/
│       │   ├── CheckEmailRequest.java           # 既存
│       │   ├── RequestOtpRequest.java           # ★ 追加（email）
│       │   ├── VerifyOtpRequest.java            # ★ 追加（email, otp）
│       │   ├── CompleteRegistrationRequest.java # ★ 追加（registrationToken, password, passwordConfirm, displayName）
│       │   ├── LoginRequest.java
│       │   ├── GoogleLoginRequest.java
│       │   ├── RefreshRequest.java
│       │   └── LogoutRequest.java
│       │   # ❌ RegisterRequest.java / VerifyEmailRequest.java を削除
│       └── response/
│           ├── CheckEmailResponse.java          # 既存
│           ├── RequestOtpResponse.java          # ★ 追加（message, expiresInSeconds）
│           ├── VerifyOtpResponse.java           # ★ 追加（registrationToken, expiresInSeconds）
│           └── AuthTokenResponse.java           # 既存（accessToken, refreshToken, tokenType, expiresIn）
│           # ❌ RegisterResponse.java を削除
├── infra/google/
│   └── GoogleTokenVerifier.java             # 既存（Google ID Token 検証）
└── infra/email/
    ├── EmailSender.java                     # インターフェース（sendRegistrationOtp）
    ├── SmtpEmailSender.java                 # ★ dev 実装（@Profile("dev")・SMTP→Mailpit）
    ├── template/
    │   └── EmailTemplateFormatter.java      # ★ HTML テンプレートの {{変数}} 置換
    └── （ResendEmailSender.java は prod 実装として後続フェーズで追加。@Profile("prod")・EMAIL_DESIGN.md §5）
```

> **メール送信の方針（環境別トランスポート）:** `EmailSender` インターフェースを差し替え点（seam）とし、プロファイルで実装を選択する。
> **dev:** `SmtpEmailSender` が devcontainer の Mailpit（`MAIL_HOST:MAIL_PORT`）へ HTML メールを送信し、Web UI（http://localhost:8025）で本番同等のメールを目視確認する。
> **test:** `@MockitoBean EmailSender` でモックし、`ArgumentCaptor` で OTP を捕捉してフローを進める（実送信なし）。
> **prod:** Resend HTTP API 実装（`ResendEmailSender @Profile("prod")`）へ差し替える（後続タスク・OQ-4）。
> テンプレートは classpath の `emails/ja/registration-otp.html`（EMAIL_DESIGN.md AUTH-01）を `EmailTemplateFormatter` が読み込み、`{{otpCode}}` / `{{expiresIn}}` を置換する。差出人は `app.email`（`MAIL_FROM_ADDRESS` / `MAIL_FROM_NAME`）で外部化。

**削除する例外クラス:** `EmailNotVerifiedException` / `EmailVerificationTokenInvalidException` / `EmailVerificationTokenExpiredException`  
**追加する例外クラス:** `OtpInvalidException`（400 `OTP_INVALID`）/ `OtpExpiredException`（400 `OTP_EXPIRED`）/ `OtpMaxAttemptsExceededException`（429 `OTP_MAX_ATTEMPTS_EXCEEDED`）/ `RegistrationSessionInvalidException`（400 `REGISTRATION_SESSION_INVALID`）

### 5.3 実装ステップ（依存順）

#### ステップ 1: Redis インフラ + Migration

- `build.gradle` / `docker-compose.yml` / `application.yml` / `RedisConfig` を整備し、`StringRedisTemplate` が DI できることを確認する
- `email_verification_tokens` テーブル・`users.email_verified` 列を除去する（§4.2）

#### ステップ 2: Redis Store サービス（OtpService / RegistrationSessionService）

- `OtpService`:
  - `issue(email)`: クールダウン（`reg:otp:cooldown:{email}`）・時間あたり上限（`reg:otp:count:{email}`）を確認 → 6 桁 OTP 生成（`SecureRandom`）→ `reg:otp:{email}` に `SHA-256(otp)` + `attempts=0` を `SETEX`（TTL 10 分）→ 平文 OTP を呼び出し側へ返す（メール送信用）
  - `verify(email, otp)`: `reg:otp:{email}` 取得 → なければ `OTP_EXPIRED` → `attempts >= 5` なら `DEL` して `OTP_MAX_ATTEMPTS_EXCEEDED` → ハッシュ不一致なら `attempts` を `HINCRBY` して `OTP_INVALID` → 一致なら `DEL` して成功
- `RegistrationSessionService`:
  - `create(email)`: `registrationToken = UUID.randomUUID()` → `reg:session:{token}` に `{email}` を `SETEX`（TTL 30 分）→ token を返す
  - `consume(registrationToken)`: 取得 → なければ `REGISTRATION_SESSION_INVALID` → email を返し `DEL`（ワンタイム）
- いずれも TTL・桁数・上限は `application.yml` から `@ConfigurationProperties` で注入する

#### ステップ 3: Domain Entity / DTO の改修

- `User.java`: `email_verified` フィールドと `verifyEmail()` を削除。`UserRole` / `UserStatus` enum を利用
- DTO: `RegisterRequest` / `VerifyEmailRequest` / `RegisterResponse` を削除し、`RequestOtpRequest` / `VerifyOtpRequest` / `CompleteRegistrationRequest` / `RequestOtpResponse` / `VerifyOtpResponse` を追加
- `CompleteRegistrationRequest`: `passwordConfirm` 一致チェック（クラスレベル `@AssertTrue` or custom constraint）、`displayName` は `@Size(max=100)` で任意

#### ステップ 4: EmailSender の実装（dev: SMTP→Mailpit / prod: Resend）

- **トランスポートとユースケースを分離する**（「どう送るか」と「何を送るか」を別レイヤーに）:
  - **トランスポート層** `EmailSender#send(EmailMessage)`: 描画済みの `EmailMessage`（`to` / `subject` / `htmlBody`）をそのまま送るだけの薄い契約。profile で実装を差し替える。テンプレート・件名・変数を関知しない
  - **ユースケース層** `AuthEmailService#sendRegistrationOtp(String toEmail, String otpCode)`（`@Service`・profile 非依存・実装1つ）: テンプレート `emails/ja/registration-otp.html` を `EmailTemplateFormatter` で描画し、件名 `【Kivio】認証コード: {otpCode}` を組み立てて `EmailSender#send` へ委譲。`AuthService` はこれに依存する。メール種別が増えても増えるのはユースケース層のみで、dev/prod の各トランスポートにテンプレート処理が重複しない
- **dev 実装 `SmtpEmailSender`（`@Profile("dev")`）:** `spring-boot-starter-mail` の `JavaMailSender` で Mailpit（`spring.mail.host/port` = `MAIL_HOST:MAIL_PORT`、認証・TLS なし）へ HTML メールを送信する。差出人は `app.email`（`MAIL_FROM_ADDRESS` / `MAIL_FROM_NAME`）。**件名・本文に OTP を含むため、ログ出力は `to=` のみ（件名・本文は出さない）。**
- **prod 実装 `ResendEmailSender`（`@Profile("prod")`）:** 後続タスク（OQ-4）。`@Async` + `@Retryable` でメール失敗をビジネスロジックに伝播させない（EMAIL_DESIGN.md §5）
- **フォールバック `LogEmailSender`（`@Profile("!dev & !prod")`）:** dev/prod 以外（統合テストの `test` プロファイル・プロファイル未指定の `bootRun`）で有効化し、実送信せずログ出力のみで代替する。これが無いと `test` プロファイルに `EmailSender` 実体が存在せず、フルコンテキスト起動テスト（`KivioBackendApplicationTests`）が失敗する。`prod` は除外しているため Resend 実装（T-26）が入るまで意図的に起動失敗させ、メールの無言ドロップを防ぐ。プロファイル解決は `EmailSenderProfileTest`（Testcontainers 不要）で検証する
- メールの死活はアプリ readiness に連動させない（`management.health.mail.enabled=false`）

#### ステップ 5: AuthService の改修

メソッド一覧と責務:

| メソッド | 責務 |
|---|---|
| `checkEmail(CheckEmailRequest req)` | email の存在チェック → `available` フラグ返却（変更なし） |
| `requestOtp(RequestOtpRequest req)` | email 重複チェック → `OtpService.issue(email)` → `EmailSender.sendRegistrationOtp` → `RequestOtpResponse`（202） |
| `verifyOtp(VerifyOtpRequest req)` | `OtpService.verify(email, otp)` → 成功で `RegistrationSessionService.create(email)` → `VerifyOtpResponse`（registrationToken） |
| `completeRegistration(CompleteRegistrationRequest req)` | `RegistrationSessionService.consume(token)` で email 取得 → BCrypt ハッシュ化 → INSERT users（`ROLE_BUYER`）→ RefreshToken 生成 → `AuthTokenResponse`（201・自動ログイン）。INSERT で UNIQUE 違反時は `EMAIL_ALREADY_REGISTERED` |
| `login(LoginRequest req)` | email 検索 → BCrypt 検証 → `USER_DEACTIVATED` チェック → RefreshToken 生成 → `AuthTokenResponse`（**`email_verified` チェックは削除**） |
| `googleLogin(GoogleLoginRequest req)` | GoogleTokenVerifier 呼び出し → google_id / email で既存ユーザー検索 → 統合 or 新規作成 → RefreshToken 生成 → `AuthTokenResponse`（**`verifyEmail()` 呼び出しは削除**） |
| `refresh(RefreshRequest req)` | SHA-256(token) で検索 → 有効性確認 → Token Rotation → `AuthTokenResponse`（変更なし） |
| `logout(LogoutRequest req)` | SHA-256(token) で検索 → 削除（変更なし） |

**Token Rotation の実装方針:** 受信トークンを SHA-256 でハッシュ化して DB 検索 → 存在しない・revoked・期限切れのいずれかで `REFRESH_TOKEN_INVALID` → 正常な場合は既存レコードを削除し新しい RefreshToken を生成・保存 → 新 Access Token と新 Refresh Token を返却。

**盗難検出（Refresh Token Reuse Detection）:** 同じ token が 2 回使われた場合（revoked = true のものが使われた場合）は、そのユーザーの全 RefreshToken を削除してセッションを全無効化する。

#### ステップ 6: AuthController の改修

- 旧 `POST /auth/register`・`POST /auth/verify-email` を削除し、`request-otp` / `verify-otp` / `complete` を追加（サービス委譲のみ）
- Bean Validation によるリクエスト検証を行う
- 監査ログ: `complete`（登録完了）に `@Auditable(action="USER_REGISTERED")`、`login` 等に従来どおり付与する（`AUDIT.md` の命名規則）。`USER_EMAIL_VERIFIED` は廃止

---

## 6. Frontend Implementation Plan

### 6.1 前提条件

以下がシニア実装済みであること（ブランチ受け取り前に確認する）。

- [ ] `src/` ディレクトリ構成が確立している（`app/` → `src/app/` 移行済み）
- [ ] shadcn/ui がインストール済みで `components.json` が存在する
- [ ] TanStack Query プロバイダーが `src/app/layout.tsx` に設定済み
- [ ] `src/lib/api/client/` の HTTP クライアント（Bearer Token 自動付与・ProblemDetail パース）が存在する
- [ ] `src/types/api.ts` に `ProblemDetail` 型が定義済み
- [ ] `src/proxy.ts`（認証ガード）が存在する

### 6.2 実装ファイル一覧

```
src/
├── app/
│   └── (auth)/
│       ├── layout.tsx                        # 認証系レイアウト（Navbar なし）
│       ├── login/
│       │   └── page.tsx
│       └── register/
│           └── page.tsx                      # 3 ステップ（メール → OTP → パスワード）
│       # ❌ verify-email/page.tsx は不要（削除）
├── components/
│   └── auth/
│       ├── LoginForm.tsx                     # CC（"use client"）
│       ├── RegisterFlow.tsx                # CC：3 ステップの状態管理（email / registrationToken を保持）
│       ├── RegisterEmailStep.tsx             # CC：Step1 メール入力 → request-otp
│       ├── RegisterOtpStep.tsx               # CC：Step2 OTP 入力 → verify-otp（再送信ボタン）
│       ├── RegisterPasswordStep.tsx          # CC：Step3 パスワード設定 → complete
│       └── GoogleSignInButton.tsx            # CC（"use client"）
├── lib/
│   ├── api/
│   │   └── auth.ts                           # 認証系 API クライアント関数
│   └── validations/
│       └── auth.ts                           # Zod スキーマ
├── stores/
│   └── authStore.ts                          # Zustand: accessToken・user 情報
└── types/
    └── auth.ts                               # AuthTokenResponse・OTP 関連型
```

### 6.3 実装ステップ（依存順）

#### ステップ 1: 型定義

`src/types/auth.ts` に以下の型を定義する。詳細は `docs/design/frontend/FRONTEND_API_CONTRACT.md §2.2` および `API_DESIGN.md §2` に準拠する。

- `AuthTokens`: `accessToken` / `refreshToken` / `tokenType` / `expiresIn`（login・google・register/complete のレスポンス）
- `RequestOtpResponse`: `message` / `expiresInSeconds`
- `VerifyOtpResponse`: `registrationToken` / `expiresInSeconds`
- `AuthUser`: `id` / `email` / `displayName` / `avatarUrl` / `role` / `status` / `createdAt`（**`emailVerified` は持たない**）
- `UserRole`: `ROLE_BUYER` / `ROLE_SELLER` / `ROLE_ADMIN`

#### ステップ 2: Zod スキーマ

`src/lib/validations/auth.ts` に以下を定義する。

- `loginSchema`: email（メール形式）・password（8文字以上）
- `requestOtpSchema`: email（メール形式）
- `verifyOtpSchema`: otp（6 桁の数値・`/^\d{6}$/`）
- `completeRegistrationSchema`: password（8文字以上）・passwordConfirm（password と一致）・displayName（任意・100文字以内）

#### ステップ 3: API クライアント関数

`src/lib/api/auth.ts` に 3.2 の各エンドポイントに対応する関数を定義する。

- 対象: `checkEmail`, `requestOtp`, `verifyOtp`, `completeRegistration`, `login`, `googleLogin`, `refresh`, `logout`

#### ステップ 4: Zustand ストア

`src/stores/authStore.ts` に認証状態を管理するストアを定義する。

- 状態: Access Token・ログイン中ユーザー情報（id・role・displayName）
- アクション: 認証情報のセット・クリア
- Access Token はメモリ（Zustand state）に保持し、Refresh Token は HTTP-only Cookie に保持する（`SECURITY.md §2.1` 準拠）

#### ステップ 5: コンポーネント

- `LoginForm.tsx`: `react-hook-form` + `loginSchema` + `useMutation`（TanStack Query）
- `RegisterFlow.tsx`: 現在ステップ（email / otp / password）・`email`・`registrationToken` を内部状態で保持し、各ステップコンポーネントを切り替える
  - Step1（`RegisterEmailStep`）: `requestOtp`。`EMAIL_ALREADY_REGISTERED` でログイン誘導。成功で Step2 へ
  - Step2（`RegisterOtpStep`）: `verifyOtp`。`OTP_INVALID` は残り回数表示で再入力、`OTP_EXPIRED` / `OTP_MAX_ATTEMPTS_EXCEEDED` は「コードを再送信」ボタン（Step1 の `requestOtp` を再実行）。成功で `registrationToken` を保持し Step3 へ
  - Step3（`RegisterPasswordStep`）: `completeRegistration`。`REGISTRATION_SESSION_INVALID` で Step1 へ戻す。成功で `AuthTokens` を Zustand に保存 → `router.replace('/')`（自動ログイン）
- `GoogleSignInButton.tsx`: NextAuth の `signIn("google")` を呼び出す

#### ステップ 6: ページ

- `login/page.tsx`: `LoginForm` を配置する Server Component（"use client" 不要）
- `register/page.tsx`: `RegisterFlow` を配置する Server Component

#### ステップ 7: 認証ガード

`src/proxy.ts` に認証ガードの保護ルートを追加する。

- 認証必須ルート（未認証時 → `/login` リダイレクト）: マイアカウント・注文履歴・ウィッシュリスト・セラー画面・管理者画面
- 未認証専用ルート（認証済み時 → `/` リダイレクト）: `/login`, `/register`

ルートのパターン定義は `docs/design/frontend/FRONTEND_IA.md` の URL 一覧を参照すること（`/auth/verify-email` は廃止）。

---

## 7. Task Breakdown

各タスクには `depends_on` を明示する。並列実行できるタスクは `[並列可]` と記載。

> **凡例:** ✅ Done / 🔄 要改修（旧方式で実装済み・OTP 方式へ修正が必要） / ⬜ Todo

| ID | タスク | 担当 | 依存 | ステータス |
|---|---|---|---|---|
| T-22 | Redis インフラ導入（build.gradle / docker-compose / application.yaml / `AuthProperties`） | BE | なし | ✅ Done |
| T-01 | Migration 改修：`email_verification_tokens` テーブル（V12 削除）・`users.email_verified` 列（V2 直修正）の除去 | BE | なし | ✅ Done |
| T-23 | `OtpService` 実装（OTP 生成・保存・検証・スロットリング / Redis） | BE | T-22 | ✅ Done |
| T-24 | `RegistrationSessionService` 実装（registrationToken 発行・検証・消費 / Redis） | BE | T-22 | ✅ Done |
| T-02 | Domain Entity 改修：`User` から `email_verified` / `verifyEmail()` 削除、`EmailVerificationToken` 削除 | BE | T-01 | ✅ Done |
| T-03 | Repository 改修：`EmailVerificationTokenRepository` 削除 | BE | T-02 | ✅ Done |
| T-04 | DTO 改修：OTP 系 Request/Response 追加、`Register*`/`VerifyEmail*` 削除、例外クラス入替 | BE | なし | ✅ Done |
| T-05 | `GoogleTokenVerifier` 実装（infra 層） | BE | なし | ✅ Done |
| T-25 | トランスポート `EmailSender#send(EmailMessage)` + ユースケース `AuthEmailService`（テンプレート描画・件名生成を集約）+ dev 実装 `SmtpEmailSender`（SMTP→Mailpit）+ `EmailTemplateFormatter` + テンプレート `emails/ja/registration-otp.html` + フォールバック `LogEmailSender`（`@Profile("!dev & !prod")`、test/未指定プロファイルの起動用）+ `EmailSenderProfileTest` | BE | なし | ✅ Done |
| T-26 | prod 実装 `ResendEmailSender`（`@Profile("prod")`・`@Async`+`@Retryable`）+ Resend API Key 設定（EMAIL_DESIGN.md §5） | BE | T-25 | ⬜ Todo（後続フェーズ・OQ-4） |
| T-06 | `AuthService` 改修：`requestOtp` / `verifyOtp` / `completeRegistration`（旧 register/verifyEmail を置換） | BE | T-03, T-04, T-23, T-24, T-25 | ✅ Done |
| T-07 | `AuthService` 改修：`login` / `googleLogin`（`email_verified` チェック・`verifyEmail()` 呼び出しを削除） | BE | T-05, T-06 | ✅ Done |
| T-08 | `AuthService`：`refresh` / `logout`（変更なし・回帰確認のみ） | BE | T-06 | ✅ Done |
| T-09 | `AuthController` 改修：登録 3 エンドポイント差し替え + `@Auditable` 見直し | BE | T-06, T-07, T-08 | ✅ Done |
| T-10 | Backend 単体テスト改修（OTP / 登録セッション / login から email_verified 削除）＋ `OtpServiceTest` / `RegistrationSessionServiceTest` 追加（実 Redis Testcontainers） | BE | T-06, T-07, T-08 | ✅ Done |
| T-11 | Backend 統合テスト改修（登録 3 ステップ・Testcontainers + Redis） | BE | T-09 | ✅ Done |
| T-12 | FE: 型定義（`src/types/api/auth.ts` + `src/types/enums.ts`・既存スキャフォールド構成に合わせて配置） | FE | なし | ✅ Done |
| T-13 | FE: Zod スキーマ（`src/lib/validations/auth.ts`） | FE | なし | ✅ Done |
| T-14 | FE: API クライアント関数（`src/lib/api/client/auth.ts`・`lib/api/client/` 構成に合わせて配置） | FE | T-12 | ✅ Done |
| T-15 | FE: Zustand ストア（`src/stores/useAuthStore.ts`・規約 §5.1 命名に準拠） | FE | T-12 | ✅ Done |
| T-16 | FE: `LoginForm` / `RegisterFlow`（3 ステップ）/ `GoogleSignInButton` | FE | T-13, T-14, T-15 | ✅ Done |
| T-17 | FE: `login/page.tsx` / `register/page.tsx`（verify-email ページは作らない） | FE | T-16 | ✅ Done |
| T-18 | FE: `proxy.ts` に認証ガード追加 | FE | T-15 | ✅ Done |
| T-19 | FE: コンポーネントテスト（Vitest + RTL + MSW の OTP ハンドラ） | FE | T-16 | ✅ Done |
| T-20 | FE: E2E テスト（Playwright・3 ステップ登録） | FE | T-17, T-18 | ✅ Done |
| T-21 | Seed データ改修（`dev/V10__seed_development_data.sql`・`email_verified` 列を除去し認証済みユーザーを直接 INSERT） | BE | T-01 | ✅ Done |
| T-27 | BE: `GET /api/v1/users/me`（`UserController` + `UserService` + `UserResponse`）。`@AuthenticationPrincipal` の user_id からプロフィールを返す（API_DESIGN.md §3）。`SecurityConfig` は `anyRequest().authenticated()` で自動的に認証必須 | BE | なし | ✅ Done |
| T-28 | FE: `getCurrentUser`（`lib/api/client/users.ts`・store の accessToken を Bearer 付与）+ `LoginForm` / `RegisterPasswordStep` の成功時に `login`/`complete` → `getCurrentUser` → `setAuth` 配線 + MSW `users` ハンドラ + E2E に `/users/me` モック追加 | FE | T-27, T-15 | ✅ Done |
| T-29 | FE: 認証後ヘッダー（`HeaderActions` を `useAuthStore` に配線・hydration guard）+ `§4` 仕様反映（Guest にカート、BUYER/SELLER のアイコン行に地球追加・Heart を UserMenu へ移設）+ `UserMenu` のホバー開閉（制御化）・お気に入り・**ログアウト UI**（`logout()` → `clearAuth()` → `/`）。`design-system/pages/layout.md §4` を更新 | FE | T-28 | ✅ Done |
| T-30 | FE: **Google OAuth フロントエンド配線**（NextAuth v5 ハンドラ `src/app/api/auth/[...nextauth]/route.ts` + 設定 `src/auth.ts`（Google Provider・`AUTH_SECRET`/`AUTH_GOOGLE_ID`/`AUTH_GOOGLE_SECRET`）+ Google `id_token` を取り出すコールバック + ブリッジ `GoogleAuthBridge`（バックエンド `POST /api/v1/auth/google` と交換 → `getCurrentUser` → `useAuthStore.setAuth` → ホーム遷移、交換後 NextAuth セッションは破棄）+ `AuthSessionProvider` を認証レイアウトにスコープ + 型拡張 `types/next-auth.d.ts`）。`GoogleSignInButton` の `signIn('google')` が 404（`/api/auth/error`）に落ちる不備を解消する。env 設定（FE: `AUTH_*` / BE: `GOOGLE_CLIENT_ID`＝FE の `AUTH_GOOGLE_ID` と同一値・`GOOGLE_CLIENT_SECRET`）+ 実機ブラウザ確認 + R-1 の E2E skip 解除候補 | FE | T-16, T-28 | ✅ Done |

> **T-27〜T-29 の追加経緯（2026-06-14）:** §4「認証状態別ヘッダーアクション」が未実装で、画面上のログイン/ログアウトを目視確認できなかった。確認経路として認証状態を画面に反映させる必要があり、`GET /users/me` 連携（User ドメイン）と認証後ヘッダー・ログアウト UI（T-613 の「後続」分）をまとめて実装した。
>
> **T-30 の追加経緯（2026-06-17）:** Task Breakdown 全消化後、`/auth/login` の「Google で続行」押下が `/api/auth/error` で 404 になる事象を確認。バックエンド（T-05/T-07）と `GoogleSignInButton`（T-16）は実装済みだが、`signIn('google')` を受ける NextAuth ハンドラ・Provider 設定・`id_token`→`POST /auth/google` ブリッジが未実装で、当該配線タスクが Task Breakdown に欠落していた（OQ-3 の Client ID/Secret 未払い出しを理由に先送りされていた範囲）。OQ-3 が解決（クレデンシャル払い出し・env 設定完了）したため、フロント配線＋実機確認を T-30 として追加する。
>
> **T-30 実装メモ（2026-06-17）:** 方針＝NextAuth は **Google `id_token` 取得専用**とし、Kivio 認証の正はメモリ Zustand（既存のメール/パスワードログイン T-28 と同一経路で `setAuth`）。交換完了後に NextAuth セッションは `signOut({redirect:false})` で破棄する。`SessionProvider` は認証レイアウト（`auth/(auth-group)/layout.tsx`）配下にのみ適用しグローバル負荷を避ける。**自動検証済み:** `pnpm typecheck`/`lint`/`test`(23)/`build` グリーン、`/api/auth/[...nextauth]` がルート登録され `/api/auth/error` が 200（404 解消）、`signIn('google')` が `accounts.google.com` へ実 `client_id`＋`scope=openid` でリダイレクト、BE `/api/v1/auth/google` が稼働（不正トークンで 401＝`GOOGLE_TOKEN_INVALID`）。**実機確認済み（2026-06-17）:** ブラウザで実 Google アカウントの同意画面を通した E2E が成功（要件＝Google Cloud Console の「承認済みリダイレクト URI」に `http://localhost:3000/api/auth/callback/google` を登録）。なお BE の起動 env に注意：`./gradlew bootRun` は `.env` を自動ロードしないため、シェルの `GOOGLE_CLIENT_ID=dummy` が残っていると `id_token` の `aud` 照合に失敗し `GOOGLE_TOKEN_INVALID` になる（起動時に正しい `GOOGLE_CLIENT_ID`＝FE の `AUTH_GOOGLE_ID` を渡すこと）。**キャンセル/エラー時の遷移:** `src/auth.ts` に `pages.signIn`/`pages.error` = `/auth/login` を設定し、NextAuth 既定のエラーページ（`/api/auth/error?error=...`）ではなくログイン画面へ戻す。Google 同意画面で「キャンセル」した場合（`error=access_denied`）もログイン画面へ静かに復帰する（ログイン画面は `?error=` を表示しないため文言は出さない＝正常操作扱い）。トークン交換後の失敗（`GOOGLE_TOKEN_INVALID` 等）は経路が別で、従来どおり `GoogleAuthBridge` が `FormAlert` で文言表示する。**既知の制約:** リロード後の再ハイドレーション・Cookie ブリッジは OQ-6 の別タスク（accessToken はメモリのみ）。

**依存グラフ（クリティカルパス）:**

```
T-22 → T-23/T-24 ┐
T-01 → T-02 → T-03 ┼─► T-06 → T-07 → T-09 → T-11
T-04 ──────────────┤
T-25 ──────────────┘
T-05 ──────────────────────► T-07
T-08（既存・回帰確認）
```

---

## 8. Security Checklist

認証機能は特にセキュリティ要件が集中する。コードレビュー前に全項目を確認すること。

### パスワード・ハッシュ

- [x] BCrypt cost factor = **12** を使用している（11 以下・13 以上は不可）
- [x] パスワード平文を一切ログ・DB・**Redis**・レスポンスに出力していない
- [x] `passwordConfirm` フィールドの値をログに記録していない

### JWT

- [x] Access Token 有効期限 = **15 分**（`jwt.access-token-expiration=900`）
- [x] Refresh Token 有効期限 = **7 日**（`jwt.refresh-token-expiration=604800`）
- [x] JWT 署名アルゴリズム = **HS256**（Phase 2）
- [x] JWT ペイロードに含まれる情報が `sub`（user_id）と `role` のみである
- [x] JWT 秘密鍵が `application.yaml` にハードコードされていない（環境変数 `JWT_SECRET` 参照）

### Refresh Token

- [x] DB には **SHA-256 ハッシュ**のみ保存し平文は保存していない
- [x] Token Rotation を実装している（リフレッシュのたびに旧トークンを削除・新トークンを発行）
- [x] Refresh Token Reuse Detection を実装している（`revoked = true` のトークン使用時に全セッション無効化）
- [x] ログアウト時に DB からトークンを削除している

### OTP / 登録セッション（Redis）

- [x] OTP は平文を保存せず **SHA-256 ハッシュ**のみ Redis に保存している
- [x] OTP 有効期限 = **10 分**（Redis TTL）。`registrationToken` 有効期限 = **30 分**
- [x] OTP 検証は **5 回**まで。超過で `reg:otp:{email}` を失効させ `OTP_MAX_ATTEMPTS_EXCEEDED` を返す
- [x] OTP は `SecureRandom` で生成した 6 桁数値である
- [x] `registrationToken` は不透明な **UUID v4**。`reg:session:*` はワンタイム消費（complete 時に `DEL`）
- [x] `request-otp` に **メールアドレス単位**のスロットリング（60 秒に 1 回・1 時間に 5 回）を適用している（IP 単位だけではクロス IP のメール爆撃を防げない）
- [x] `users` レコードは OTP 検証 + パスワード設定の完了後にのみ作成している（未認証レコードを作らない）
- [x] OTP メールに個人情報を含めず、「コードを共有しない」旨を記載している

### Google OAuth

- [x] `audience`（Google Client ID）を検証している
- [x] `issuer`（`accounts.google.com`）を検証している
- [x] Google Client Secret を環境変数で管理している

### エラーレスポンス

- [x] ログイン失敗時にメールアドレス存在有無がわかる情報を返していない（`INVALID_CREDENTIALS` のみ返す）
- [x] スタックトレースを 500 エラーレスポンスに含めていない
- [x] `rejectedValue` にパスワード・OTP・トークン類を含めていない（`GlobalExceptionHandler` の `SENSITIVE_FIELDS` = `password`/`token`/`secret`/`credential`/`otp` でマスキング済み）

### レート制限

- [x] 認証系エンドポイント（`/api/v1/auth/*`）に **10 req/min/IP** の制限が適用されている
- [x] `request-otp` のメール単位スロットリングが Redis で実装されている
- [x] 制限超過時に `429 Too Many Requests` + `Retry-After` を返している

### CORS

- [x] 許可オリジンが環境変数 `ALLOWED_ORIGINS` で管理されており `*` を使用していない
- [x] `Access-Control-Allow-Credentials: true` が設定されている

---

## 9. Test Checklist

### 9.1 Backend テスト（JUnit 5 + Mockito + Testcontainers）

#### OtpService / RegistrationSessionService 単体テスト

<!-- 実装済み: 専用の単体テスト `OtpServiceTest` / `RegistrationSessionServiceTest`
     （`src/test/java/io/kivio/domain/identity/service/`）。実 Redis（Testcontainers・
     `disabledWithoutDocker=true`）に対し TTL・SHA-256 保存・attempts 加算・キー失効・
     スロットリングの実挙動を検証する。Docker 無し環境では自動スキップ。 -->

- [x] `OtpService.issue`: `reg:otp:{email}` に SHA-256 ハッシュ + `attempts=0` が TTL 付きで保存される
- [x] `OtpService.issue`: クールダウン中（`reg:otp:cooldown:{email}` 存在） → `RATE_LIMIT_EXCEEDED`
- [x] `OtpService.issue`: 1 時間の送信上限超過 → `RATE_LIMIT_EXCEEDED`
- [x] `OtpService.verify`: 正しい OTP → 成功し `reg:otp:{email}` が削除される
- [x] `OtpService.verify`: 誤った OTP → `attempts` が加算され `OTP_INVALID`
- [x] `OtpService.verify`: キーなし（期限切れ） → `OTP_EXPIRED`
- [x] `OtpService.verify`: 5 回超過 → `OTP_MAX_ATTEMPTS_EXCEEDED` + キー失効
- [x] `RegistrationSessionService.create/consume`: 発行 → 消費で email を返し再消費は `REGISTRATION_SESSION_INVALID`

#### AuthService 単体テスト

- [x] `checkEmail`: 利用可能なメール → `available: true` を返す
- [x] `checkEmail`: 登録済みメール → `available: false` を返す（エラーにせず 200。重複の権威的拒否は request-otp / complete が担う）
- [x] `requestOtp`: 利用可能なメール → OTP 保存 + `sendRegistrationOtp` が呼ばれ 202
- [x] `requestOtp`: 重複メール → `EMAIL_ALREADY_REGISTERED`（`users` は作成されない）
- [x] `verifyOtp`: 正しい OTP → `registrationToken` を返す
- [x] `verifyOtp`: 誤り / 期限切れ / 上限超過 → `OTP_INVALID` / `OTP_EXPIRED` / `OTP_MAX_ATTEMPTS_EXCEEDED`
- [x] `completeRegistration`: 有効な `registrationToken` → users に INSERT・Access + Refresh Token が返る・セッション削除
- [x] `completeRegistration`: 無効・期限切れ・再利用トークン → `REGISTRATION_SESSION_INVALID`
- [x] `completeRegistration`: 検証〜完了の間に同一メール登録（UNIQUE 違反） → `EMAIL_ALREADY_REGISTERED`
- [x] `login`: 正常系 → Access Token + Refresh Token が返る
- [x] `login`: 誤パスワード → `INVALID_CREDENTIALS`
- [x] `login`: 存在しないメール → `INVALID_CREDENTIALS`（メール存在有無を漏らさない）
- [x] `login`: 無効化済みアカウント → `USER_DEACTIVATED`（**`EMAIL_NOT_VERIFIED` のテストは削除**）
- [x] `googleLogin`: 有効 ID Token → Refresh Token 生成・Access Token 返却
- [x] `googleLogin`: 既存メールと同一 → google_id が既存ユーザーに紐づく（統合）
- [x] `googleLogin`: 無効 ID Token → `GOOGLE_TOKEN_INVALID`
- [x] `refresh`: 有効トークン → Token Rotation で新トークン発行
- [x] `refresh`: 無効 / 期限切れ → `REFRESH_TOKEN_INVALID`
- [x] `refresh`: 既に revoked なトークン（Reuse） → 全セッション無効化 + `REFRESH_TOKEN_INVALID`
- [x] `logout`: 正常系 → DB からトークン削除

#### AuthController 統合テスト（MockMvc + Testcontainers + Redis）

<!-- 実装済み: `AuthControllerIntegrationTest`（16 テスト）＋レート制限専用の
     `AuthRateLimitIntegrationTest`（1 テスト）。いずれも MockMvc + Testcontainers
     （PostgreSQL + Redis・@ServiceConnection）で全エンドポイントのレスポンス形式・
     ステータスコードを検証する。`@Testcontainers(disabledWithoutDocker=true)` のため
     Docker 未起動環境では自動スキップ。レート制限テストは test プロファイル既定容量
     （10000）を `@TestPropertySource` で本番既定値（10）に下げた専用コンテキストで実行。 -->

- [x] `POST /auth/check-email` 200（利用可/登録済みとも `available` フラグ）レスポンス形式が API_DESIGN.md と一致する
- [x] `POST /auth/register/request-otp` 202 レスポンス形式が一致する
- [x] `POST /auth/register/verify-otp` 200 / 400 / 429 レスポンス形式が一致する
- [x] `POST /auth/register/complete` 201（Access + Refresh Token）/ 400 レスポンス形式が一致する
- [x] 3 ステップを通した登録 → ログインが成功する（Testcontainers の Redis を使用）
- [x] `POST /auth/login` 200 / 401 / 403 レスポンス形式が一致する
- [x] `POST /auth/google` 200 / 401 レスポンス形式が一致する
- [x] `POST /auth/refresh` 200 / 401 レスポンス形式が一致する
- [x] `POST /auth/logout` 204 で Refresh Token が DB から削除される
- [x] 認証なしで `POST /auth/logout` → 401
- [x] レート制限（11 回目のリクエスト） → 429 + `Retry-After` ヘッダー

### 9.2 Frontend テスト（Vitest + RTL + Playwright）

#### コンポーネントテスト（Vitest + RTL + MSW）

- [x] `LoginForm`: メールとパスワードを入力して送信 → `login()` が呼ばれる（accessToken 保存 + ホーム遷移で検証）
- [x] `LoginForm`: バリデーションエラー時にエラーメッセージが表示される
- [x] `LoginForm`: API エラー（401）時にエラーメッセージが表示される
- [x] `RegisterFlow` Step1: メール入力 → `requestOtp()` が呼ばれ Step2 へ遷移
- [x] `RegisterFlow` Step2: 6 桁 OTP 入力 → `verifyOtp()` が呼ばれ Step3 へ遷移
- [x] `RegisterFlow` Step2: `OTP_INVALID` で残り回数表示・再入力できる（`OTP_EXPIRED` の再送信は E2E/手動で確認）
- [x] `RegisterFlow` Step3: パスワード不一致でバリデーションエラー（成功 → `completeRegistration()` は E2E で検証）
- [x] `GoogleSignInButton`: クリックで `signIn("google")` が呼ばれる

#### E2E テスト（Playwright）

- [x] 登録フロー: メール入力 → OTP 入力（モック）→ パスワード設定 → 自動ログインでホームへ
- [x] OTP 誤入力 → エラー表示・再入力できる
- [x] ログインフロー: login → ホームにリダイレクト → accessToken が Zustand に保存される
- [x] 誤パスワードログイン: エラーメッセージが表示される
- [x] ログアウト UI 実装（T-29）：`UserMenu` のログアウトで `logout()` → `clearAuth()` → `/` 遷移。ログアウト後に protected route へアクセス → `/auth/login` リダイレクトの検証は E2E 追加候補（後続）
- [x] 未認証ユーザーが protected route に直接アクセス → `/login` にリダイレクト
- [x] 認証済みユーザーが `/login` にアクセス → `/` にリダイレクト

---

## 10. Definition of Done

PR をマージするには以下を全て満たすこと。

### 機能要件

- [x] 全 8 エンドポイント（check-email / register×3 / login / google / refresh / logout）が実装されている（`AuthController` 8 メソッド）
- [x] `POST /auth/register/request-otp` で OTP メール送信が配線されている（`AuthService.requestOtp` → `AuthEmailService.sendRegistrationOtp` → dev: `SmtpEmailSender @Profile("dev")` → Mailpit SMTP。`EmailSenderProfileTest` でプロファイル解決を検証済み。**live の Mailpit 目視確認（`http://localhost:8025`）は `docker compose up` 環境での手動ステップ**。prod: Resend は T-26 で対応）
- [x] 3 ステップ（request-otp → verify-otp → complete）で登録が完了し、`complete` 成功でそのままログイン状態になる（`AuthControllerIntegrationTest#should_complete_registration_through_otp_flow_and_auto_login`）
- [x] `users` レコードが OTP 検証 + パスワード設定の完了後にのみ作成される（`requestOtp`/`verifyOtp` は `@Transactional(readOnly=true)` で `users` を作成せず、`completeRegistration` のみ `saveAndFlush`）
- [x] `POST /auth/google` で既存メールアカウントとの統合が動作する（`AuthService.linkGoogleIdToExistingAccount`・単体テストで検証済み）
- [x] Token Rotation（リフレッシュのたびに新 Refresh Token を発行）が動作する（`AuthService.refresh` で旧トークンを `revoke()` → 新ペア発行。Reuse Detection 付き）
- [x] ログアウト後、同じ Refresh Token でリフレッシュが失敗（401）する（`AuthControllerIntegrationTest#should_return_204_and_invalidate_token_when_logout_is_authenticated`）

### セキュリティ要件

- [x] Security Checklist の全項目を確認済み（§8 全項目 `[x]`。OTP の `rejectedValue` マスキングは `GlobalExceptionHandler.SENSITIVE_FIELDS` に `otp` を含むことをコードで確認・旧 NG コメントを是正）
- [x] BCrypt cost 12（`SecurityConfig`: `new BCryptPasswordEncoder(12)`）・JWT 有効期限（access 900s / refresh 604800s）・HS256（`Keys.hmacShaKeyFor` + `signWith`）・payload は `sub`+`role` のみ・Token Rotation・OTP の SHA-256 保存（`TokenHashUtils.sha256Hex`）/試行上限 5/メール単位スロットリング（cooldown 60s・1h 上限 5）をコードで確認済み

### テスト要件

- [x] Backend: `./gradlew test` がグリーン（Testcontainers PostgreSQL + Redis 含む・58 テスト 0 失敗 0 スキップ）
- [x] Backend: AuthService / OtpService / RegistrationSessionService の単体テストカバレッジ ≥ 80%（JaCoCo line: AuthService 98.9% / OtpService 93.8% / RegistrationSessionService 100%）
- [x] Frontend: `pnpm test` がグリーン（Vitest 23 テスト）
- [x] Frontend: `pnpm build` がエラーなし（Next.js 16 production build 成功）
- [x] Frontend: `pnpm lint` がエラーなし（eslint クリーン・`pnpm typecheck` 型エラーなし）

### コード品質

- [x] `BACKEND_CODING_STANDARDS.md` の規約に準拠している（DTO=record・Entity の Lombok パターン・レイヤー責務・`@Transactional(readOnly)`・例外は `KivioException` 階層・`@ConfigurationProperties` 外部化・構造化ログ＋機微情報非出力をコードで確認）
- [x] `FRONTEND_CODING_STANDARDS.md` の規約に準拠している（規約 §11.1 を「ルートファイルのみ `export default`、他コンポーネントは名前付き export」へ改訂し実装と整合。`'use client'` 葉限定・`function` 宣言・a11y 属性・Zod v4 `z.email()`・TanStack Query・Zustand セレクタも準拠）
- [x] ドメイン間の直接 import がない（`identity` パッケージ内で完結している。他 `domain.*` への import は `@Auditable`（`domain/audit`）の 1 件のみ＝規約 §11 が全ドメイン横断で規定する監査 AOP の意図的な cross-cutting 依存。`common`/`config`/`infra` は共有層のため対象外）
- [x] Controller がビジネスロジックを持っていない（Service への委譲のみ。`AuthController`・`UserController` ともに分岐・計算なし）
- [x] 旧方式の残骸（`EmailVerificationToken*` / `verify-email` / `email_verified` / `EMAIL_NOT_VERIFIED`）が完全に除去されている（`kivio-backend/src`・`kivio-frontend/src` を grep。ヒットは V2 マイグレーションの「列を持たない」旨のコメント 1 件のみ）
- [x] Swagger UI（`http://localhost:8080/swagger-ui.html`）で全エンドポイントが確認できる（springdoc 2.8.9・`OpenApiConfig`（Bearer scheme）・`@Tag`＋`@Operation`（Auth 8／User 1）・`SecurityConfig` が swagger-ui/v3/api-docs を `permitAll`。※ブラウザでの目視は §動作確認 で実施）

### 動作確認

- [x] `docker compose up` で全サービス（PostgreSQL + Redis + backend + frontend）が起動する（2026-06-18 検証。db/redis/backend が healthy・frontend が Next.js 16 で HTTP 200。**当初フロントエンドの Docker ビルドが `prepare` スクリプトの husky 呼び出しで失敗していたため修正**：monorepo 前提の `cd .. && ./kivio-frontend/node_modules/.bin/husky` を binary 存在ガード付き（`[ -x ... ] && ... || true`）に変更し、Docker/CI では no-op・ローカルでは従来どおり `/workspace/.husky` にフック設置。検証は devcontainer スタックとのポート衝突回避のためホスト側ポートのみ一時上書きして実施＝内部配線は不変）
- [x] Flyway マイグレーションが正常に適用される（email_verification 関連が除去されている）
- [x] ブラウザから 3 ステップ登録・ログイン・ログアウトが一通り操作できる

---

## 11. Risks / Open Questions

### Open Questions（着手前に確認が必要）

| # | 質問 | 影響タスク | 期限 |
|---|---|---|---|
| ~~OQ-1~~ | ✅ 解決：開発初期のため Migration を直接編集する方針で合意。`V2__create_identity_tables.sql` から `email_verified` 列を除去し、`V12__create_email_verification_tokens.sql` を削除した（新規 V13 は追加しない） | T-01 | — |
| OQ-2 | `POST /auth/refresh` のレスポンスに新しい `refreshToken` を含める仕様か？ API_DESIGN.md §2.4 のレスポンスに `refreshToken` フィールドがない → Token Rotation の結果をどう返すか確認 | T-08, T-14 | 着手前 |
| ~~OQ-3~~ | ✅ 解決（2026-06-17）：オーナーが Google Cloud Console で OAuth 2.0 クライアント（ウェブ）を払い出し、FE `.env.local`（`AUTH_SECRET`/`AUTH_GOOGLE_ID`/`AUTH_GOOGLE_SECRET`）・BE（`GOOGLE_CLIENT_ID`＝FE の `AUTH_GOOGLE_ID` と同一値・`GOOGLE_CLIENT_SECRET`）に設定済み。残作業はフロント配線（T-30） | T-05, T-07, T-30 | — |
| OQ-4 | OTP メール送信の実装範囲 | T-25, T-26 | **一部解決**：dev は `SmtpEmailSender`→Mailpit で実送信・目視確認まで完了（T-25）。トランスポート（`EmailSender#send`）とユースケース（`AuthEmailService`）を分離済み。test/未指定プロファイルはフォールバック `LogEmailSender` で起動可能。prod の Resend 連携（API Key 共有・`ResendEmailSender`＝`@Profile("prod")`）は後続タスク T-26 として分離（未実装のため現状 prod は起動不可） |
| OQ-5 | Redis は main の `docker-compose.yml` に追加するか（devcontainer には既存）。本番（Neon/Supabase 構成）の Redis ホスティング先は？ | T-22 | 着手前 |
| OQ-6 | **トークン Cookie ブリッジ層の未整備。** `proxy.ts` / `lib/api/server/base.ts` は `access_token` Cookie を前提とするが、現状この Cookie をセットする箇所（BFF Route Handler 等）が無く、バックエンドの login/refresh も Set-Cookie を返さない（`Authorization: Bearer` ヘッダー認証のみ）。クライアントの `apiFetch` も store の Bearer を付与しない（T-28 の `getCurrentUser` は個別に Bearer 付与で回避）。**影響:** ①ページリロード後は accessToken（メモリのみ・persist 対象外）が消えるため、persist された `isAuthenticated`/`user` でヘッダーは認証済み表示のままだが API 呼び出しは Bearer 無しになる。②`proxy.ts` の Cookie ベース認証ガードは Cookie 未発行のため実効しない。**要決定:** BFF Route Handler で login/refresh レスポンスから `access_token` を Cookie 化するか、`apiFetch` を store の Bearer 付与へ統一するか。リロード時の再ハイドレーション（refresh → `getCurrentUser`）も併せて設計する | 後続（認証配線の本格化） | 別タスク |

### Risks（既知のリスク）

| # | リスク | 影響度 | 対策 |
|---|---|---|---|
| R-1 | Google OAuth のローカル環境設定が完了していない場合、`POST /auth/google` の E2E テストが実施できない | 中 | Google OAuth は単体テストで GoogleTokenVerifier をモックして検証し、E2E は skip フラグを立てて後回しにする。**2026-06-17 にクレデンシャル払い出し・env 設定が完了（OQ-3 解決）したため、T-30 でフロント配線＋実機確認を行い、skip 解除を検討する** |
| R-2 | Resend がローカル環境で使えない場合、OTP メール送信部分のテストができない | 中 | **解決済み**：dev は `SmtpEmailSender`（`@Profile("dev")`）で devcontainer の Mailpit へ実送信し、Web UI（`http://localhost:8025`）で本番同等のメールを目視確認する。test は `@MockitoBean EmailSender` でモックし `ArgumentCaptor` で OTP を捕捉する（コンソールログ出力のダミー送信は廃止） |
| R-3 | `SecurityConfig` の公開設定が `/auth/register/**`（ワイルドカード）に更新されていない場合、登録系が 401 になる | 高 | 実装開始前に `SecurityConfig` の `permitAll()` 設定を確認する |
| R-4 | Refresh Token Reuse Detection（全セッション無効化）が実装されない場合、トークン盗難時に全端末ログアウトができない | 高 | Security Checklist に明記し、コードレビューで必ず確認する |
| R-5 | Next.js 16 の `proxy.ts`（旧 `middleware.ts`）の動作が未確認の場合、認証ガードが機能しない | 高 | シニアがスキャフォールド段階でサンプル実装を提供しているか確認する |
| R-6 | 旧方式の実装（T-01〜T-11 Done）の改修漏れにより、`email_verified` / `verify-email` の残骸が残ると整合性が崩れる | 高 | DoD の「旧方式の残骸が完全に除去」をレビュー観点に追加し、grep で機械的に確認する |
| R-7 | Testcontainers に Redis を追加しないと統合テストで OTP フローが検証できない | 中 | `@Container GenericContainer<>(redis:7-alpine)` を統合テスト基底クラスに追加する |
