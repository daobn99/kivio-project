# 認証機能 実装計画 / 進捗管理
**ブランチ:** `feature/auth`  
**担当 Phase:** Phase 2  
**最終更新:** 2026-06-07  
**ステータス:** 🟡 実装中（T-01〜T-11 完了）

---

## 目次

1. [Overview](#1-overview)
2. [Required References](#2-required-references)
3. [API Contract](#3-api-contract)
4. [DB Migration / Seed Plan](#4-db-migration--seed-plan)
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
| メールアドレス重複チェック | `POST /api/v1/auth/check-email` |
| メール＋パスワード登録 | `POST /api/v1/auth/register` |
| メール認証（verify-email） | `POST /api/v1/auth/verify-email` |
| メール＋パスワードログイン | `POST /api/v1/auth/login` |
| Google OAuth ログイン | `POST /api/v1/auth/google` |
| トークンリフレッシュ | `POST /api/v1/auth/refresh` |
| ログアウト | `POST /api/v1/auth/logout` |

### スコープ外（シニア先行実装済みを前提とする）

以下はスキャフォールディング（フェーズ 1-3 / 1-4 / 1-5）として先行実装されていること。

- `SecurityConfig`（Filter Chain 定義・公開エンドポイント設定）
- `JwtAuthenticationFilter`（既存リクエストへのトークン検証）
- `CorrelationIdFilter`・`GlobalExceptionHandler`・`RateLimitingFilter`
- Flyway 基底マイグレーション（`V1__init_schema.sql`）

### 実装順序サマリー

```
DB Migration（V2）
  └─► Backend: Entity / Repository / Service / Controller
        └─► Frontend: API クライアント / Zod スキーマ / UI コンポーネント / ページ
```

---

## 2. Required References

実装前にタスクに関連するドキュメントのみを必ず通読すること。

| ドキュメント | 参照箇所 | 理由 |
|---|---|---|
| `docs/architecture/SECURITY.md` | §1〜§6 全体 | JWT 設計・BCrypt 設定・Refresh Token Rotation・Google ID Token 検証フロー |
| `docs/design/API_DESIGN.md` | §2 認証 (Auth) | 全エンドポイントのリクエスト/レスポンス仕様・エラーコード |
| `docs/design/DB_DESIGN.md` | §3.1 users, §3.2 refresh_tokens | テーブル定義・制約・業務ルール |
| `docs/design/SEQUENCE_FLOW.md` | §1.1〜§1.3 | メール登録・ログイン・Google OAuth の詳細シーケンス図 |
| `docs/design/ERROR_CODES.md` | §2.2 認証・ユーザー | 認証系エラーコード一覧 |
| `docs/development/BACKEND_CODING_STANDARDS.md` | 全体 | レイヤー責務・Lombok 使用方法・例外設計・テスト方針 |
| `docs/development/FRONTEND_CODING_STANDARDS.md` | §1〜§10 | ディレクトリ構成・SC/CC 境界・TanStack Query・Zustand・フォーム規約 |
| `docs/development/FRONTEND_TEST_STRATEGY.md` | 全体 | Vitest/RTL/Playwright の使い方・MSW モック方針 |
| `docs/design/FRONTEND_API_CONTRACT.md` | 認証関連画面 | 画面×API 対応表・エラー UI 仕様 |
| `adr/ADR-004-jwt-strategy.md` | 全体 | JWT アルゴリズム選定理由（HS256 採用の背景） |
| `adr/ADR-005-uuid-primary-key.md` | 全体 | UUID 主キー採用理由 |
| `CLAUDE.md` | Conventions セクション | API・Soft Delete・Audit Log 規約 |

---

## 3. API Contract

### 3.1 共通仕様

- ベースパス: `/api/v1`
- 認証不要: `check-email`, `register`, `verify-email`, `login`, `google`, `refresh`
- 認証必須: `logout`（`Authorization: Bearer <accessToken>`）
- エラー形式: RFC 9457 `ProblemDetail`（`Content-Type: application/problem+json`）
- 各エンドポイントの完全なリクエスト/レスポンス仕様は `docs/design/API_DESIGN.md §2` を参照すること

### 3.2 エンドポイント一覧

| エンドポイント | 認証 | 主なリクエストフィールド | レスポンス | 主なエラーコード |
|---|---|---|---|---|
| `POST /auth/check-email` | 不要 | `email` | 200 `{ available }` | `EMAIL_ALREADY_REGISTERED`, `VALIDATION_FAILED` |
| `POST /auth/register` | 不要 | `email`, `password`, `passwordConfirm` | 201 ユーザー情報 | `EMAIL_ALREADY_REGISTERED`, `VALIDATION_FAILED` |
| `POST /auth/verify-email` | 不要 | `token`（ボディで送信。URL パラメータ禁止） | 200 Access + Refresh Token | `EMAIL_VERIFICATION_TOKEN_INVALID`, `EMAIL_VERIFICATION_TOKEN_EXPIRED` |
| `POST /auth/login` | 不要 | `email`, `password` | 200 Access + Refresh Token | `INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `USER_DEACTIVATED` |
| `POST /auth/google` | 不要 | `idToken`（Google ID Token） | 200 Access + Refresh Token | `GOOGLE_TOKEN_INVALID`, `USER_DEACTIVATED` |
| `POST /auth/refresh` | 不要 | `refreshToken` | 200 新 Access + 新 Refresh Token | `REFRESH_TOKEN_INVALID` |
| `POST /auth/logout` | 必須 | `refreshToken` | 204 No Content | — |

**補足事項:**
- `POST /auth/verify-email`: 成功時に Access + Refresh Token を返し自動ログインさせる
- `POST /auth/google`: 同メールの既存アカウントがある場合は `google_id` を紐づけて統合する（新規作成は行わない）
- `POST /auth/refresh`: Token Rotation により、レスポンスに**新しい** Refresh Token を含める（旧トークンは無効化）

### 3.3 JWT ペイロード

Access Token のペイロードには `sub`（user_id UUID）と `role` のみを含める。ユーザーの詳細情報（名前・メール等）は含めない。詳細は `docs/architecture/SECURITY.md §2.2` を参照。

---

## 4. DB Migration / Seed Plan

### 4.1 Migration ファイル

`V1__init_schema.sql` はシニアが基底として作成済み。認証機能が必要とする追加テーブルは `V2__auth_tables.sql` として作成する。

```
kivio-backend/src/main/resources/db/migration/
└── V2__auth_tables.sql
```

#### V2__auth_tables.sql に含めるべき内容

**追加テーブル:**

- `email_verification_tokens`（`SEQUENCE_FLOW.md §1.1` に登場するが `DB_DESIGN.md` には未記載のため追加が必要 → Open Questions #1）
  - 必須カラム: `user_id`（FK → users）, `token_hash`（SHA-256 ハッシュのみ保存。平文禁止）, `expires_at`（+24時間）, `used_at`（NULL=未使用。再利用防止のため DELETE せず `used_at` で管理）, `created_at`
  - 制約: `token_hash` に UNIQUE 制約
- `refresh_tokens`（`DB_DESIGN.md §3.2` 参照。`V1` に未含有の場合のみ追加）

**追加インデックス:**

- `refresh_tokens`: `user_id`, `token_hash` にそれぞれインデックス
- `email_verification_tokens`: `user_id`, `token_hash` にそれぞれインデックス

### 4.2 Seed Data

| 内容 | 要否 | 理由 |
|---|---|---|
| テスト用ユーザー（BUYER） | 必要 | ローカル開発・E2E テストで即座に動作確認できるようにする |
| テスト用ユーザー（SELLER） | 任意 | Seller 画面の疎通確認に有用 |
| テスト用ユーザー（ADMIN） | 必要 | 管理者機能の動作確認に必須 |
| Google OAuth 設定 | 不要 | `platform_configs` テーブルは Phase 2 スコープ外 |

Seed ファイルは `V99__seed_dev.sql` としてマイグレーションとは分離し、`application-dev.yaml` でのみ適用する（本番実行禁止）。

パスワードは共通の開発用パスワードを BCrypt（cost 12）でハッシュ化した値を使用すること。ハッシュ値は事前生成して埋め込む。

---

## 5. Backend Implementation Plan

### 5.1 前提条件

以下がシニア実装済みであること（ブランチ受け取り前に確認する）。

- [ ] `SecurityConfig`（Filter Chain・公開パス設定）が存在する
- [ ] `JwtAuthenticationFilter` が動作している
- [ ] `GlobalExceptionHandler`（ProblemDetail 形式）が存在する
- [ ] `SoftDeletableEntity` が `io.kivio.common` に存在する
- [ ] `V1__init_schema.sql` が適用済みで `users` テーブルが存在する

### 5.2 実装ファイル一覧

パッケージルート: `io.kivio.domain.identity`

```
io.kivio/
├── domain/identity/
│   ├── controller/
│   │   └── AuthController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   └── EmailVerificationService.java
│   ├── domain/
│   │   ├── User.java                      # 集約ルート（@Entity）
│   │   ├── RefreshToken.java              # @Entity
│   │   ├── EmailVerificationToken.java    # @Entity
│   │   └── vo/
│   │       └── Email.java                 # Value Object（@Embeddable）
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── RefreshTokenRepository.java
│   │   └── EmailVerificationTokenRepository.java
│   └── dto/
│       ├── request/
│       │   ├── CheckEmailRequest.java
│       │   ├── RegisterRequest.java
│       │   ├── VerifyEmailRequest.java
│       │   ├── LoginRequest.java
│       │   ├── GoogleLoginRequest.java
│       │   ├── RefreshRequest.java
│       │   └── LogoutRequest.java
│       └── response/
│           ├── CheckEmailResponse.java
│           ├── RegisterResponse.java
│           └── AuthTokenResponse.java
└── infra/google/
    └── GoogleTokenVerifier.java           # Google ID Token 検証（infra 層）
```

### 5.3 実装ステップ（依存順）

#### ステップ 1: DB Migration（V2）

- `V2__auth_tables.sql` を作成し `./gradlew bootRun` でマイグレーションを確認する

#### ステップ 2: Domain Entity

- `User.java`: `SoftDeletableEntity` を継承。`DB_DESIGN.md §3.1` のカラム定義に準拠してフィールドを定義する。Soft Delete フィルタを付与すること
- `RefreshToken.java`: `DB_DESIGN.md §3.2` に準拠。`tokenHash`（平文禁止）・`expiresAt`・`revoked` を持つ
- `EmailVerificationToken.java`: `tokenHash`（平文禁止）・`expiresAt`・`usedAt`（再利用防止）を持つ

#### ステップ 3: Repository

各 Repository に必要な検索クエリの種類を整理する。

- `UserRepository`: email 検索・google_id 検索
- `RefreshTokenRepository`: token_hash 検索・token_hash 削除・user_id 単位の全件削除（Reuse Detection 用）
- `EmailVerificationTokenRepository`: token_hash かつ未使用（`used_at IS NULL`）での検索

#### ステップ 4: DTO / Request・Response クラス

- Bean Validation アノテーション付きで定義する（`@NotBlank`, `@Email`, `@Size`）
- `RegisterRequest` には `passwordConfirm` の一致チェック（クラスレベル `@AssertTrue` または custom constraint）

#### ステップ 5: infra — GoogleTokenVerifier

- `google-auth-library-oauth2-http` を使って Google の公開鍵で ID Token を検証する
- `audience`（Google Client ID）と `issuer`（`accounts.google.com`）を検証する

#### ステップ 6: AuthService

メソッド一覧と責務:

| メソッド | 責務 |
|---|---|
| `checkEmail(String email)` | email の存在チェック → `available` フラグ返却 |
| `register(RegisterRequest req)` | BCrypt ハッシュ化 → INSERT users → email_verification_tokens 生成 → Resend で確認メール送信 |
| `verifyEmail(String token)` | SHA-256(token) で検索 → 有効性確認 → `used_at` 更新 → `email_verified = true` → RefreshToken 生成 → AuthTokenResponse 返却 |
| `login(LoginRequest req)` | email 検索 → BCrypt 検証 → email_verified チェック → RefreshToken 生成 → AuthTokenResponse 返却 |
| `googleLogin(String idToken)` | GoogleTokenVerifier 呼び出し → google_id / email で既存ユーザー検索 → 統合 or 新規作成 → RefreshToken 生成 → AuthTokenResponse 返却 |
| `refresh(String refreshToken)` | SHA-256(token) で検索 → 有効性確認 → Token Rotation → AuthTokenResponse 返却 |
| `logout(String refreshToken)` | SHA-256(token) で検索 → 削除 |

**Token Rotation の実装方針:**

受信トークンを SHA-256 でハッシュ化して DB 検索 → 存在しない・revoked・期限切れのいずれかで `REFRESH_TOKEN_INVALID` → 正常な場合は既存レコードを削除し新しい RefreshToken を生成・保存 → 新 Access Token と新 Refresh Token を返却。

**盗難検出（Refresh Token Reuse Detection）:**

同じ token が 2 回使われた場合（revoked = true のものが使われた場合）は、そのユーザーの全 RefreshToken を削除してセッションを全無効化する。

#### ステップ 7: AuthController

- サービス呼び出しのみ行い、ビジネスロジックは持たない
- Bean Validation によるリクエスト検証を行う
- 監査ログが必要なメソッド（登録・ログイン・ログアウト等）に `@Auditable` を付与する（アクション名は `AUDIT.md` の命名規則に従う）

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
│       ├── register/
│       │   └── page.tsx
│       └── verify-email/
│           └── page.tsx                      # マウント時に /auth/verify-email を呼び出す
├── components/
│   └── auth/
│       ├── LoginForm.tsx                     # CC（"use client"）
│       ├── RegisterForm.tsx                  # CC（"use client"）
│       └── GoogleSignInButton.tsx            # CC（"use client"）
├── lib/
│   ├── api/
│   │   └── auth.ts                           # 認証系 API クライアント関数
│   └── validations/
│       └── auth.ts                           # Zod スキーマ（login / register）
├── stores/
│   └── authStore.ts                          # Zustand: accessToken・user 情報
└── types/
    └── auth.ts                               # AuthTokenResponse・RegisterResponse 型
```

### 6.3 実装ステップ（依存順）

#### ステップ 1: 型定義

`src/types/auth.ts` に以下の型を定義する。型の詳細なフィールド構成は `docs/design/API_DESIGN.md §2` のレスポンス仕様に準拠すること。

- `AuthTokenResponse`: Access Token・Refresh Token・tokenType・expiresIn を含むトークンレスポンス型
- `RegisterResponse`: ユーザー登録成功時のレスポンス型（id・email・role・emailVerified・createdAt）
- `Role`: ユーザーロールの Union Type または Enum（`ROLE_BUYER` / `ROLE_SELLER` / `ROLE_ADMIN`）

#### ステップ 2: Zod スキーマ

`src/lib/validations/auth.ts` に以下のスキーマを定義する。

- `loginSchema`: email（メール形式）・password（8文字以上）
- `registerSchema`: email・password（8文字以上）・passwordConfirm（password と一致すること）

#### ステップ 3: API クライアント関数

`src/lib/api/auth.ts` に 3.2 の各エンドポイントに対応する関数を定義する。

- 対象: `checkEmail`, `register`, `verifyEmail`, `login`, `googleLogin`, `refresh`, `logout`

#### ステップ 4: Zustand ストア

`src/stores/authStore.ts` に認証状態を管理するストアを定義する。

- 状態: Access Token・ログイン中ユーザー情報（id・role・displayName）
- アクション: 認証情報のセット・クリア
- Access Token はメモリ（Zustand state）に保持し、Refresh Token は HTTP-only Cookie に保持する（`SECURITY.md §2.1` 準拠）

#### ステップ 5: コンポーネント

- `LoginForm.tsx`: `react-hook-form` + `loginSchema` + `useMutation`（TanStack Query）
- `RegisterForm.tsx`: `react-hook-form` + `registerSchema` + `useMutation`
- `GoogleSignInButton.tsx`: NextAuth の `signIn("google")` を呼び出す

#### ステップ 6: ページ

- `login/page.tsx`: `LoginForm` を配置する Server Component（"use client" 不要）
- `register/page.tsx`: `RegisterForm` を配置する Server Component
- `verify-email/page.tsx`: Client Component。マウント時に `verifyEmail(token)` を呼び出し、成功で `/` へ `router.replace('/')`、失敗でエラーメッセージを表示

#### ステップ 7: 認証ガード

`src/proxy.ts` に認証ガードの保護ルートを追加する。

- 認証必須ルート（未認証時 → `/login` リダイレクト）: マイアカウント・注文履歴・ウィッシュリスト・セラー画面・管理者画面
- 未認証専用ルート（認証済み時 → `/` リダイレクト）: `/login`, `/register`

ルートのパターン定義は `docs/design/frontend/FRONTEND_IA.md` の URL 一覧を参照すること。

---

## 7. Task Breakdown

各タスクには `depends_on` を明示する。並列実行できるタスクは `[並列可]` と記載。

| ID | タスク | 担当 | 依存 | ステータス |
|---|---|---|---|---|
| T-01 | `V2__auth_tables.sql` 作成・マイグレーション確認 | BE | なし | ✅ Done |
| T-02 | Domain Entity（User / RefreshToken / EmailVerificationToken）実装 | BE | T-01 | ✅ Done |
| T-03 | Repository インターフェース実装 | BE | T-02 | ✅ Done |
| T-04 | DTO クラス実装（Request / Response） | BE | なし | ✅ Done |
| T-05 | `GoogleTokenVerifier` 実装（infra 層） | BE | なし | ✅ Done |
| T-06 | `AuthService` 実装（check-email / register / verifyEmail） | BE | T-03, T-04 | ✅ Done |
| T-07 | `AuthService` 実装（login / googleLogin） | BE | T-05, T-06 | ✅ Done |
| T-08 | `AuthService` 実装（refresh / logout） | BE | T-06 | ✅ Done |
| T-09 | `AuthController` 実装 + `@Auditable` 付与 | BE | T-06, T-07, T-08 | ✅ Done |
| T-10 | Backend 単体テスト（Service 層） | BE | T-06, T-07, T-08 | ✅ Done |
| T-11 | Backend 統合テスト（Controller 層・Testcontainers） | BE | T-09 | ✅ Done |
| T-12 | FE: 型定義（`src/types/auth.ts`） | FE | なし | ⬜ Todo [並列可] |
| T-13 | FE: Zod スキーマ（`src/lib/validations/auth.ts`） | FE | なし | ⬜ Todo [並列可] |
| T-14 | FE: API クライアント関数（`src/lib/api/auth.ts`） | FE | T-12 | ⬜ Todo |
| T-15 | FE: Zustand ストア（`src/stores/authStore.ts`） | FE | T-12 | ⬜ Todo [並列可 with T-14] |
| T-16 | FE: `LoginForm` / `RegisterForm` / `GoogleSignInButton` コンポーネント | FE | T-13, T-14, T-15 | ⬜ Todo |
| T-17 | FE: `login/page.tsx` / `register/page.tsx` / `verify-email/page.tsx` | FE | T-16 | ⬜ Todo |
| T-18 | FE: `proxy.ts` に認証ガード追加 | FE | T-15 | ⬜ Todo |
| T-19 | FE: コンポーネントテスト（Vitest + RTL） | FE | T-16 | ⬜ Todo |
| T-20 | FE: E2E テスト（Playwright） | FE | T-17, T-18 | ⬜ Todo |
| T-21 | Seed データ作成（`V99__seed_dev.sql`） | BE | T-01 | ⬜ Todo [並列可 with T-02] |

**依存グラフ（クリティカルパス）:**

```
T-01 → T-02 → T-03 → T-06 → T-07 → T-09 → T-11
T-04 ──────────────────┘
T-05 ──────────────────────────┘
T-08 ──────────────────────────────────────┘
```

---

## 8. Security Checklist

認証機能は特にセキュリティ要件が集中する。コードレビュー前に全項目を確認すること。

### パスワード・ハッシュ

- [ ] BCrypt cost factor = **12** を使用している（11 以下・13 以上は不可）
- [ ] パスワード平文を一切ログ・DB・レスポンスに出力していない
- [ ] `passwordConfirm` フィールドの値をログに記録していない

### JWT

- [ ] Access Token 有効期限 = **15 分**（`jwt.access-token-expiration=900`）
- [ ] Refresh Token 有効期限 = **7 日**（`jwt.refresh-token-expiration=604800`）
- [ ] JWT 署名アルゴリズム = **HS256**（Phase 2）
- [ ] JWT ペイロードに含まれる情報が `sub`（user_id）と `role` のみである
- [ ] JWT 秘密鍵が `application.yaml` にハードコードされていない（環境変数 `JWT_SECRET` 参照）

### Refresh Token

- [ ] DB には **SHA-256 ハッシュ**のみ保存し平文は保存していない
- [ ] Token Rotation を実装している（リフレッシュのたびに旧トークンを削除・新トークンを発行）
- [ ] Refresh Token Reuse Detection を実装している（`revoked = true` のトークン使用時に全セッション無効化）
- [ ] ログアウト時に DB からトークンを削除している

### Email Verification

- [ ] 確認トークンは DB に **SHA-256 ハッシュ**のみ保存している
- [ ] 確認トークン有効期限 = **24 時間**
- [ ] 使用済みトークンを `DELETE` せず `used_at` で管理している（監査目的）
- [ ] フロントエンドはトークンを URL クエリパラメータではなく **リクエストボディ**で送信している（サーバーログ露出防止）

### Google OAuth

- [ ] `audience`（Google Client ID）を検証している
- [ ] `issuer`（`accounts.google.com`）を検証している
- [ ] Google Client Secret を環境変数で管理している

### エラーレスポンス

- [ ] ログイン失敗時にメールアドレス存在有無がわかる情報を返していない（`INVALID_CREDENTIALS` のみ返す）
- [ ] スタックトレースを 500 エラーレスポンスに含めていない
- [ ] `rejectedValue` にパスワード・トークン類を含めていない（`GlobalExceptionHandler` でマスキング済み）

### レート制限

- [ ] 認証系エンドポイント（`/api/v1/auth/*`）に **10 req/min/IP** の制限が適用されている
- [ ] 制限超過時に `429 Too Many Requests` + `Retry-After: 60` を返している

### CORS

- [ ] 許可オリジンが環境変数 `ALLOWED_ORIGINS` で管理されており `*` を使用していない
- [ ] `Access-Control-Allow-Credentials: true` が設定されている

---

## 9. Test Checklist

### 9.1 Backend テスト（JUnit 5 + Mockito + Testcontainers）

#### AuthService 単体テスト

- [ ] `checkEmail`: 利用可能なメール → `available: true` を返す
- [ ] `checkEmail`: 登録済みメール → `KivioException(EMAIL_ALREADY_REGISTERED)` をスロー
- [ ] `register`: 正常系 → users テーブルに INSERT・email_verification_tokens に INSERT
- [ ] `register`: 重複メール → `EMAIL_ALREADY_REGISTERED` をスロー
- [ ] `verifyEmail`: 有効トークン → `used_at` が更新・Access Token + Refresh Token が返る
- [ ] `verifyEmail`: 無効トークン（not found / used_at != null） → `EMAIL_VERIFICATION_TOKEN_INVALID`
- [ ] `verifyEmail`: 期限切れトークン → `EMAIL_VERIFICATION_TOKEN_EXPIRED`
- [ ] `login`: 正常系 → Access Token + Refresh Token が返る
- [ ] `login`: 誤パスワード → `INVALID_CREDENTIALS`
- [ ] `login`: 存在しないメール → `INVALID_CREDENTIALS`（メール存在有無を漏らさない）
- [ ] `login`: メール未確認 → `EMAIL_NOT_VERIFIED`
- [ ] `login`: 無効化済みアカウント → `USER_DEACTIVATED`
- [ ] `googleLogin`: 有効 ID Token → Refresh Token 生成・Access Token 返却
- [ ] `googleLogin`: 既存メールと同一 → google_id が既存ユーザーに紐づく（統合）
- [ ] `googleLogin`: 無効 ID Token → `GOOGLE_TOKEN_INVALID`
- [ ] `refresh`: 有効トークン → Token Rotation で新トークン発行
- [ ] `refresh`: 無効トークン → `REFRESH_TOKEN_INVALID`
- [ ] `refresh`: 期限切れトークン → `REFRESH_TOKEN_INVALID`
- [ ] `refresh`: 既に revoked なトークン（Reuse） → 全セッション無効化 + `REFRESH_TOKEN_INVALID`
- [ ] `logout`: 正常系 → DB からトークン削除

#### AuthController 統合テスト（MockMvc + Testcontainers）

- [ ] `POST /auth/check-email` 200 / 409 レスポンス形式が API_DESIGN.md と一致する
- [ ] `POST /auth/register` 201 レスポンス形式が一致する
- [ ] `POST /auth/login` 200 / 401 / 403 レスポンス形式が一致する
- [ ] `POST /auth/google` 200 / 401 レスポンス形式が一致する
- [ ] `POST /auth/refresh` 200 / 401 レスポンス形式が一致する
- [ ] `POST /auth/logout` 204 で Refresh Token が DB から削除される
- [ ] 認証なしで `POST /auth/logout` → 401
- [ ] レート制限（11 回目のリクエスト） → 429 + `Retry-After` ヘッダー

### 9.2 Frontend テスト（Vitest + RTL + Playwright）

#### コンポーネントテスト（Vitest + RTL + MSW）

- [ ] `LoginForm`: メールとパスワードを入力して送信 → `login()` が呼ばれる
- [ ] `LoginForm`: バリデーションエラー時にエラーメッセージが表示される
- [ ] `LoginForm`: API エラー（401）時にエラーメッセージが表示される
- [ ] `RegisterForm`: 全フィールド入力して送信 → `register()` が呼ばれる
- [ ] `RegisterForm`: パスワード不一致でバリデーションエラーが表示される
- [ ] `GoogleSignInButton`: クリックで `signIn("google")` が呼ばれる

#### E2E テスト（Playwright）

- [ ] メール登録フロー: check-email → register → （確認メールが送信される）
- [ ] ログインフロー: login → ホームにリダイレクト → accessToken が Zustand に保存される
- [ ] 誤パスワードログイン: エラーメッセージが表示される
- [ ] ログアウト: ログアウト後に protected route へアクセス → `/login` にリダイレクト
- [ ] 未認証ユーザーが protected route に直接アクセス → `/login` にリダイレクト
- [ ] 認証済みユーザーが `/login` にアクセス → `/` にリダイレクト

---

## 10. Definition of Done

PR をマージするには以下を全て満たすこと。

### 機能要件

- [ ] 全 7 エンドポイントが実装されている
- [ ] `POST /auth/register` で確認メールが Resend 経由で送信される
- [ ] `POST /auth/verify-email` 成功後、フロントエンドがログイン状態になる
- [ ] `POST /auth/google` で既存メールアカウントとの統合が動作する
- [ ] Token Rotation（リフレッシュのたびに新 Refresh Token を発行）が動作する
- [ ] ログアウト後、同じ Refresh Token でリフレッシュが失敗（401）する

### セキュリティ要件

- [ ] Security Checklist の全項目を確認済み
- [ ] BCrypt cost 12・JWT 有効期限・Token Rotation の実装をコードで確認済み

### テスト要件

- [ ] Backend: `./gradlew test` がグリーン
- [ ] Backend: AuthService の単体テストカバレッジ ≥ 80%（Service 層）
- [ ] Frontend: `pnpm test` がグリーン
- [ ] Frontend: `pnpm build` がエラーなし
- [ ] Frontend: `pnpm lint` がエラーなし（型エラーなし）

### コード品質

- [ ] `BACKEND_CODING_STANDARDS.md` の規約に準拠している
- [ ] `FRONTEND_CODING_STANDARDS.md` の規約に準拠している
- [ ] ドメイン間の直接 import がない（`identity` パッケージ内で完結している）
- [ ] Controller がビジネスロジックを持っていない（Service への委譲のみ）
- [ ] Swagger UI（`http://localhost:8080/swagger-ui.html`）で全エンドポイントが確認できる

### 動作確認

- [ ] `docker compose up` で全サービスが起動する
- [ ] Flyway マイグレーション（V2）が正常に適用される
- [ ] ブラウザから登録・ログイン・ログアウトが一通り操作できる

---

## 11. Risks / Open Questions

### Open Questions（着手前に確認が必要）

| # | 質問 | 影響タスク | 期限 |
|---|---|---|---|
| OQ-1 | `email_verification_tokens` テーブルは `V1__init_schema.sql`（シニア作成）に含まれるか？ → 含まれない場合 `V2` に追加 | T-01 | ブランチ受け取り時 |
| OQ-2 | `POST /auth/refresh` のレスポンスに新しい `refreshToken` を含める仕様か？ API_DESIGN.md §2.4 のレスポンスに `refreshToken` フィールドがない → Token Rotation の結果をどう返すか確認 | T-08, T-14 | 着手前 |
| OQ-3 | Google OAuth の Client ID / Secret は誰が払い出すか？ ローカル開発用の `.env` をシニアが用意するか？ | T-05, T-07 | 着手前 |
| OQ-4 | 確認メール（Resend）は Phase 2 スコープ内か？ Resend API Key の共有・`infra/resend/` のスキャフォールドは完了しているか？ | T-06 | 着手前 |

### Risks（既知のリスク）

| # | リスク | 影響度 | 対策 |
|---|---|---|---|
| R-1 | Google OAuth のローカル環境設定が完了していない場合、`POST /auth/google` の E2E テストが実施できない | 中 | Google OAuth は単体テストで GoogleTokenVerifier をモックして検証し、E2E は skip フラグを立てて後回しにする |
| R-2 | Resend がローカル環境で使えない場合、メール送信部分のテストができない | 中 | 開発環境向けのダミー送信（コンソールログ出力）実装を `spring.profiles.active=dev` で切り替え可能にする |
| R-3 | `SecurityConfig` でのエンドポイント公開設定がシニア実装と齟齬を生じた場合、認証フィルターが通ってしまう | 高 | 実装開始前に `SecurityConfig` の内容を確認し、`permitAll()` の設定漏れがないかチェックする |
| R-4 | Refresh Token Reuse Detection（全セッション無効化）が実装されない場合、トークン盗難時に全端末ログアウトができない | 高 | Security Checklist に明記し、コードレビューで必ず確認する |
| R-5 | Next.js 16 の `proxy.ts`（旧 `middleware.ts`）の動作が未確認の場合、認証ガードが機能しない | 高 | シニアがスキャフォールド段階でサンプル実装を提供しているか確認する |
