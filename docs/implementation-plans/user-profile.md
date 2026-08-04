# ユーザープロフィール・住所 実装計画 / 進捗管理
**ブランチ:** `feature/user-profile`  
**担当 Phase:** Phase 2  
**最終更新:** 2026-08-01  
**ステータス:** 🟢 U-00〜U-18 完了。バックエンド（プロフィール更新・パスワード変更・退会・住所 CRUD）とフロントエンド（`/profile/settings`・`/profile/addresses`）が実装済み。§8 Security Checklist・§9 Test Checklist・§10 Definition of Done を実施済み（§10 は `docker compose up` による一括起動 1 項目のみ devcontainer 内で実行不可のため代替検証・ホスト側での確認を PR レビュー時に残す）。UI 設計は `design-system/pages/user-profile.md` を正とする。

> **前提（`feature/auth` 由来の既存実装）:**  
> 認証スライス（`feature/auth`）で `User` エンティティ・`UserRepository`・`UserController`・`UserService`・`UserResponse` および `GET /api/v1/users/me`（T-27）が実装済み。本スライスはこれらを**拡張**する形で進める（新規作成ではなく改修が中心）。`KivioUserDetails`（`@AuthenticationPrincipal`）・`GlobalExceptionHandler`・`SecurityConfig`（`anyRequest().authenticated()`）も利用可能な状態にある。

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

`feature/user-profile` ブランチが担当するのは、認証済みユーザーが自分のアカウント情報と配送先住所を管理する一式。

| フロー | エンドポイント | 状態 |
|---|---|---|
| 自分のプロフィール取得 | `GET /api/v1/users/me` | ✅ T-27（`feature/auth`）で実装済み・回帰確認のみ |
| プロフィール更新（表示名・アバター） | `PATCH /api/v1/users/me` | ⬜ 本スライス |
| パスワード変更 | `PATCH /api/v1/users/me/password` | ⬜ 本スライス |
| 退会（論理削除） | `DELETE /api/v1/users/me` | ⬜ 本スライス |
| 配送先住所一覧 | `GET /api/v1/users/me/addresses` | ⬜ 本スライス |
| 配送先住所追加 | `POST /api/v1/users/me/addresses` | ⬜ 本スライス |
| 配送先住所更新 | `PATCH /api/v1/users/me/addresses/{id}` | ⬜ 本スライス |
| 配送先住所削除 | `DELETE /api/v1/users/me/addresses/{id}` | ⬜ 本スライス |

> **設計方針:**
> - **プロフィール更新は `PATCH`**（部分更新・全フィールド省略可）。`CLAUDE.md` 規約により `PUT` は使わない。
> - **退会は `deleted_at` を設定する論理削除**（`SoftDeletableEntity#softDelete()`）。物理削除は禁止。`users.deleted_at` 設定時は連動して `shops.deleted_at` も設定する（`DB_DESIGN.md §3.2`・本スライスでは Shop 未実装のため**連動はフックのみ用意し OQ-3 で扱う**）。退会後 90 日で匿名化バッチ（`feature/batch-jobs` スコープ）が走る。
> - **パスワード変更は Google ログイン専用ユーザー（`passwordHash` が null）には不可**。`currentPassword` 照合に失敗したら `PASSWORD_CHANGE_FAILED`（400）。
> - **住所削除は物理削除**（`addresses` には `deleted_at` を持たない。`DB_DESIGN.md §3.10`）。
> - **`isDefault = true` はユーザーにつき 1 件**。新規/更新で `true` を指定したら他住所の `is_default` を `false` に落とす（アプリ側で担保）。

### スコープ外

- **匿名化バッチ**（`UserAnonymizationJob`・退会 90 日後）は `feature/batch-jobs`（タスク #15）の担当。本スライスは `deleted_at` を立てるところまで（退会時の Refresh Token 削除のみ即時実行・§8 S-3）。**`addresses` の物理削除も同バッチの責務**として `RET-09` / `SEQUENCE_FLOW §9.1` に明記済み（R-6）。
- **アバター画像のアップロード**（Cloudinary 連携）は範囲外。`avatarUrl` は **URL 文字列を受け取るだけ**（`infra/cloudinary` の実装は後続）。
- **管理者によるユーザー操作**（`GET /admin/users`・`PATCH /admin/users/{id}/status`）は `feature/admin`（タスク #12）の担当。
- **チェックアウト時の住所参照**（`POST /orders/checkout` の `addressId`）は `feature/checkout`（タスク #7）の担当。本スライスは住所 CRUD のみを提供する。

### スコープ外（シニア先行実装済みを前提とする）

- `SecurityConfig`（`anyRequest().authenticated()` により `/api/v1/users/**` は自動的に認証必須）
- `JwtAuthenticationFilter` / `KivioUserDetails`（`@AuthenticationPrincipal` で `userId` を取得可能）
- `GlobalExceptionHandler`（RFC 9457 `ProblemDetail` 形式・`SENSITIVE_FIELDS` マスキング）
- `SoftDeletableEntity`（`io.kivio.common.entity`）・`BaseEntity`
- Flyway 基底マイグレーション（`V1`〜`V11`。`users` / `addresses` テーブルは作成済み）

### 実装順序サマリー

```
Backend: User 拡張（PATCH/password/withdraw）
  ├─► Address 集約（Entity / Repository / Service / Controller・order ドメイン）
  │     └─► Backend テスト（単体 + 統合・Testcontainers）
  └─► Frontend: 型 / Zod / API クライアント / プロフィール設定画面（Phase 2）
        └─► Frontend: 住所管理画面（Phase 3・OQ-2 次第）/ テスト
```

---

## 2. Required References

実装前にタスクに関連するドキュメントのみを必ず通読すること。

| ドキュメント | 参照箇所 | 理由 |
|---|---|---|
| `docs/design/API_DESIGN.md` | §3 ユーザー・住所（L417〜632） | 全エンドポイントのリクエスト/レスポンス仕様・エラーコード |
| `docs/design/DB_DESIGN.md` | §3.1 users（業務ルール）, §3.2 shops（連動削除）, §3.10 addresses, §インデックス | テーブル定義・`is_default` 業務ルール・退会/匿名化ポリシー |
| `docs/design/DATA_DICTIONARY.md` | users / addresses | 各カラムの意味・制約・用語統一 |
| `docs/design/VALIDATION_RULES.md` | §2.1 共通フィールド, §2.3 パスワード変更, §5 拡張（住所は本書で追記） | `displayName`/`avatarUrl`/`newPassword` 制約・Bean Validation↔zod 対応 |
| `docs/design/ERROR_CODES.md` | §共通（`VALIDATION_FAILED`/`RESOURCE_NOT_FOUND`/`ACCESS_DENIED`）, §認証（`PASSWORD_CHANGE_FAILED`） | エラーコード・HTTP ステータス・problem type |
| `docs/architecture/AUDIT.md` | §4 記録対象イベント, §命名規則 | `USER_PASSWORD_CHANGED` 等のアクション名・`@Auditable` 付与方針 |
| `docs/architecture/SECURITY.md` | §1 パスワード（BCrypt cost 12）, §所有権チェック | パスワード照合・他人リソースアクセス防止（`ACCESS_DENIED`） |
| `docs/architecture/OVERVIEW.md` | ドメイン間通信・パッケージ構成 | Address を `order` ドメインに置く判断（OQ-1） |
| `docs/development/BACKEND_CODING_STANDARDS.md` | 全体 | レイヤー責務・Lombok・例外設計・`@Transactional`・テスト方針 |
| `docs/development/FRONTEND_CODING_STANDARDS.md` | §1〜§11 | ディレクトリ構成・SC/CC 境界・TanStack Query（mutation）・Zustand・フォーム規約・export 規約 |
| `docs/development/FRONTEND_TEST_STRATEGY.md` | 全体 | Vitest/RTL/Playwright・MSW モック方針 |
| `docs/design/frontend/FRONTEND_API_CONTRACT.md` | §6.2 プロフィール設定, §6.16 配送先住所管理, §クエリキー | 画面×API 対応表・`queryKeys`・エラー UI |
| `docs/design/frontend/FRONTEND_IA.md` | `/profile/settings`（Phase 2）, `/profile/addresses`（Phase 3）, §4.11 | 画面 URL・ロール制限・コンテンツ優先順位・proxy ガード対象 |
| `adr/ADR-005-uuid-primary-key.md` | 全体 | UUID 主キー採用理由 |
| `CLAUDE.md` | Conventions / Soft Delete / Audit Log | API・部分更新は PATCH・論理削除・監査規約 |

---

## 3. API Contract

### 3.1 共通仕様

- ベースパス: `/api/v1`
- **全エンドポイント認証必須**（`Authorization: Bearer <accessToken>`）。`SecurityConfig` の `anyRequest().authenticated()` で自動適用。
- 対象ユーザーは常に**トークンの `sub`（user_id）から解決**する（`@AuthenticationPrincipal KivioUserDetails`）。パスに user_id を取らない（`/me` 固定）。
- エラー形式: RFC 9457 `ProblemDetail`（`Content-Type: application/problem+json`）
- 部分更新は `PATCH`（全フィールド省略可・送信フィールドのみ更新）
- 各エンドポイントの完全な仕様は `docs/design/API_DESIGN.md §3` を参照すること

### 3.2 エンドポイント一覧

| エンドポイント | 主なリクエストフィールド | レスポンス | 主なエラーコード |
|---|---|---|---|
| `GET /users/me` | — | 200 `UserResponse` | — |
| `PATCH /users/me` | `displayName?`, `avatarUrl?` | 200 `UserResponse` | `VALIDATION_FAILED` |
| `PATCH /users/me/password` | `currentPassword`, `newPassword` | 204 No Content | `PASSWORD_CHANGE_FAILED`(400), `VALIDATION_FAILED`(422) |
| `DELETE /users/me` | — | 204 No Content | — |
| `GET /users/me/addresses` | — | 200 `AddressResponse[]`（配列・非ページング） | — |
| `POST /users/me/addresses` | 住所全フィールド + `isDefault?` | 201 + `Location` + `AddressResponse` | `VALIDATION_FAILED` |
| `PATCH /users/me/addresses/{id}` | 住所フィールド（全省略可） | 200 `AddressResponse` | `RESOURCE_NOT_FOUND`(404), `ACCESS_DENIED`(403), `VALIDATION_FAILED`(422) |
| `DELETE /users/me/addresses/{id}` | — | 204 No Content | `RESOURCE_NOT_FOUND`(404), `ACCESS_DENIED`(403) |

**補足事項:**
- `PATCH /users/me`: `displayName`（送信時は 1〜100 文字・空文字不可）/ `avatarUrl`（URL 形式）。**送信されなかったフィールドは更新しない**（`null` と「未送信」を区別する必要がある。OQ-4）。
- `PATCH /users/me/password`: `currentPassword` を BCrypt で照合 → 不一致なら `PASSWORD_CHANGE_FAILED`。`passwordHash` が null（Google 専用ユーザー）の場合も `PASSWORD_CHANGE_FAILED`。成功で `passwordHash` を更新（BCrypt cost 12）。`USER_PASSWORD_CHANGED` を監査記録。
- `DELETE /users/me`: `User#softDelete()` で `deleted_at` を設定。以降 `@SQLRestriction` により本人を含む全クエリから除外される。冪等性は不要（既に削除済みなら `@SQLRestriction` で 404 相当になり得る点を OQ-3 で確認）。
- `GET /users/me/addresses`: **`PageResponse` ではなく素の配列**を返す（住所は件数が少なく全件取得が自然。`API_DESIGN.md §3` のレスポンス例が配列）。`created_at` 昇順 + デフォルト住所を先頭にするか並び順は OQ-5。
- `POST` / `PATCH /users/me/addresses`: `isDefault = true` の指定時は、保存前に同一ユーザーの他住所を `is_default = false` に更新（`DB_DESIGN.md §3.10` 業務ルール）。
- `PATCH` / `DELETE /users/me/addresses/{id}`: 対象住所の `user_id` がトークンの user_id と一致しない場合は **`ACCESS_DENIED`（403）**、存在しない場合は **`RESOURCE_NOT_FOUND`（404）**。（存在判定 → 所有判定の順。所有者でない場合に 404 で隠蔽せず 403 を返す方針は `API_DESIGN.md §3` に従う）
  - **例外クラスの注意:** `ACCESS_DENIED`（403）を返す既存の具象例外は**存在しない**（`common.exception.ForbiddenException` は抽象、唯一の具象 `UserDeactivatedException` はコード `USER_DEACTIVATED`）。本スライスで `ForbiddenException` を継承した具象例外（コード `ACCESS_DENIED`・例: `ResourceAccessDeniedException`）を**新規作成**する。`KivioException` はコード/ステータスを保持し `GlobalExceptionHandler` が自動描画するため、Spring Security の `org.springframework.security.access.AccessDeniedException`（名前衝突）とは別物として扱う。

### 3.3 DTO スキーマ

| DTO | フィールド |
|---|---|
| `UpdateProfileRequest` | `displayName`（`@Size(max=100)` ※空文字許容可否は OQ-4）, `avatarUrl`（`@URL`） |
| `ChangePasswordRequest` | `currentPassword`（`@NotBlank`）, `newPassword`（`@NotBlank @Size(min=8, max=72)`） |
| `CreateAddressRequest` | `recipientName`(`@NotBlank @Size(max=100)`), `postalCode`(`@NotBlank`+郵便番号パターン), `prefecture`(`@NotBlank @Size(max=20)`), `city`(`@NotBlank @Size(max=100)`), `addressLine`(`@NotBlank @Size(max=255)`), `phoneNumber`(`@NotBlank`+電話パターン), `isDefault`(`boolean`) |
| `UpdateAddressRequest` | `CreateAddressRequest` と同フィールド（全 `@Size` のみ・`@NotBlank` を外し部分更新を許容するか OQ-4 で統一） |
| `AddressResponse` | `id`, `recipientName`, `postalCode`, `prefecture`, `city`, `addressLine`, `phoneNumber`, `isDefault`, `createdAt` |

> `postalCode` / `phoneNumber` の正規表現は `VALIDATION_RULES` に住所節が未記載のため、**本スライスで `VALIDATION_RULES.md §5` に住所フィールドを追記**してから DTO に反映する（OQ-5）。暫定: `postalCode = ^\d{3}-?\d{4}$`、`phoneNumber = ^0\d{1,4}-?\d{1,4}-?\d{4}$`。

---

## 4. DB Migration / Seed Plan

### 4.1 Migration

**当初は追加マイグレーション不要と判断**。`users`（`V2`）・`addresses`（`V4`）ともに作成済みで、本スライスのスキーマ要件を満たす。以下は実マイグレーションを確認済み:

- [x] `addresses` に `idx_addresses_user_id`・`idx_addresses_user_default` インデックスが存在する（`V4__create_order_tables.sql` L122-123）
- [x] `users.deleted_at` カラム + `idx_users_deleted_at`（部分インデックス `WHERE deleted_at IS NOT NULL`）が存在する（`V2__create_identity_tables.sql` L13, L59）
- [x] `addresses` に `deleted_at` が**ない**（物理削除方針・`BaseEntity` 継承で `SoftDeletableEntity` ではない）

**変更（2026-08-02・R-4 対応）:** `V4__create_order_tables.sql` に部分 UNIQUE インデックス `idx_addresses_user_default_unique ON addresses (user_id) WHERE is_default` を**直接追記**した（既存の `idx_addresses_user_default` は**非 UNIQUE** で並行リクエストによる複数デフォルトを防げないため）。開発初期のため追加マイグレーションを起こさず create マイグレーションを編集する方針は `auth.md` OQ-1（`V2` の直接編集）の前例に従う。

> **適用手順:** `V4` のチェックサムが変わるため、既存の開発 DB では Flyway が `Migration checksum mismatch` で起動に失敗する。`docker compose down -v` でボリュームごと破棄して再作成すること（Testcontainers は毎回新規 DB のため CI・テストへの影響はない）。dev の `V13__seed_addresses.sql`（デフォルト 1 件 + 非デフォルト 1 件）は制約に適合するため変更不要。

### 4.2 Seed Data

| 内容 | 要否 | 理由 |
|---|---|---|
| Seed ユーザーの住所（BUYER に 2 件・うち 1 件 `is_default`） | 必要 | 住所一覧・デフォルト切替・削除の動作確認を即座に行うため |

`dev/V10__seed_development_data.sql` に既存 Seed ユーザー（`feature/auth` の T-21 で投入）の `id` を参照して `addresses` を INSERT する。**`V10` が `feature/auth` でマージ済みの場合は `dev/V13__seed_addresses.sql` を追加**（dev プロファイル専用・本番実行禁止）。

---

## 5. Backend Implementation Plan

### 5.1 アーキテクチャ判断: Address は `order` ドメイン（OQ-1）

`CLAUDE.md` のパッケージ構成・`DB_DESIGN.md`（addresses = order ドメイン #9）に従い、**Address 集約は `io.kivio.domain.order` に新規作成**する。URL は `/api/v1/users/me/addresses` だが、集約の所属は `order`（注文時に `orders` がスナップショット参照するため）。

- `User`（identity）と `Address`（order）の関連は **`user_id`（UUID）の値参照のみ**とし、JPA の `@ManyToOne` でドメインを跨いだエンティティ参照は張らない（`CLAUDE.md`「domain パッケージ間の直接 import 禁止」）。所有権チェックは `address.userId == principal.userId` の値比較で行う。
- 本スライスで `domain/order/` パッケージを**初めて作成**する（controller / service / domain / repository / dto の統一構成）。

### 5.2 実装ファイル一覧

```
io.kivio/
├── domain/identity/
│   ├── controller/
│   │   └── UserController.java              # 🔄 改修（PATCH /me・/me/password・DELETE /me を追加）
│   ├── service/
│   │   └── UserService.java                 # 🔄 改修（updateProfile / changePassword / withdraw を追加）
│   ├── domain/
│   │   └── User.java                        # 🔄 改修（updateProfile() / changePassword() ドメインメソッド追加）
│   ├── exception/
│   │   └── PasswordChangeFailedException.java   # ★ 追加（400 PASSWORD_CHANGE_FAILED）
│   └── dto/request/
│       ├── UpdateProfileRequest.java        # ★ 追加（displayName?, avatarUrl?）
│       └── ChangePasswordRequest.java       # ★ 追加（currentPassword, newPassword）
└── domain/order/                            # ★ 新規ドメインパッケージ
    ├── controller/
    │   └── AddressController.java           # ★ /api/v1/users/me/addresses（CRUD 4 本）
    ├── service/
    │   └── AddressService.java              # ★ @Service @Transactional
    ├── domain/
    │   └── Address.java                     # ★ 集約ルート Entity（BaseEntity 継承・deleted_at なし）
    ├── exception/
    │   └── ResourceAccessDeniedException.java   # ★ ForbiddenException 継承（403 ACCESS_DENIED）
    ├── repository/
    │   └── AddressRepository.java           # ★ findByUserId / findByIdAndUserId 等
    └── dto/
        ├── request/
        │   ├── CreateAddressRequest.java    # ★
        │   └── UpdateAddressRequest.java    # ★
        └── response/
            └── AddressResponse.java         # ★ from(Address)
```

> 既存の `UserResponse` は変更不要（`PATCH /me` の戻りも同形式）。`UserRepository#findByIdOrThrow` を再利用する。

### 5.3 実装ステップ（依存順）

#### ステップ 1: User プロフィール更新・退会

- `User.java` にドメインメソッドを追加:
  - `updateProfile(String displayName, String avatarUrl)`: 非 null のフィールドのみ更新（部分更新のセマンティクスは Service 側で「未送信」を判定し、ドメインには「更新する値」だけ渡す形でもよい。OQ-4）
  - `changePassword(String newHash)`: `passwordHash` を差し替え（照合は Service）
  - 退会は基底の `softDelete()` を使用
- `UserService.java`:
  - `updateProfile(UUID userId, UpdateProfileRequest)`: `findByIdOrThrow` → ドメイン更新 → `UserResponse.from`
  - `changePassword(UUID userId, ChangePasswordRequest)`: `findByIdOrThrow` → `hasPassword()` でない or `passwordEncoder.matches(current, hash)` 失敗 → `PasswordChangeFailedException` → `encode(new)` で更新
  - `withdraw(UUID userId)`: `findByIdOrThrow` → `softDelete()`（Shop 連動は OQ-3）
- DTO（`UpdateProfileRequest` / `ChangePasswordRequest`）を `record` で作成し Bean Validation を付与
- `PasswordChangeFailedException`（`KivioException` 階層・`"PASSWORD_CHANGE_FAILED"` / 400）

#### ステップ 2: UserController 改修

- `PATCH /me`（`@Valid UpdateProfileRequest` → `200 UserResponse`）
- `PATCH /me/password`（`@Valid ChangePasswordRequest` → `204`）
- `DELETE /me`（→ `204`）
- `@Auditable(action="USER_PASSWORD_CHANGED", entityType="USER")` を `changePassword` 経路に付与（`AUDIT.md §4`）。退会の監査アクションは `AUDIT.md` に未定義のため OQ-3 で確認（暫定 `USER_WITHDRAWN` を提案）。
- Controller はサービス委譲のみ（分岐・計算を持たない）

#### ステップ 3: Address 集約（order ドメイン）

- `Address.java`（`@Entity @Table(name="addresses")`・`BaseEntity` 継承＝`deleted_at` なし）。フィールドは `DB_DESIGN.md §3.10` に一致。`userId`（UUID 値）を保持。ドメインメソッド `update(...)` / `markDefault()` / `unsetDefault()`。
- `AddressRepository`:
  - `List<Address> findByUserIdOrderByCreatedAtAsc(UUID userId)`（並び順は OQ-5）
  - `Optional<Address> findByIdAndUserId(UUID id, UUID userId)`（所有権込み取得）※ ただし 404/403 を区別するため、`findById` で存在判定 → `userId` 比較で 403 判定する実装も検討（§3.2 補足参照）
  - `@Modifying` で `UPDATE addresses SET is_default=false WHERE user_id=:userId`（デフォルト付け替え）
- `AddressService`（`@Transactional`）:
  - `list(userId)` / `create(userId, req)` / `update(userId, id, req)` / `delete(userId, id)`
  - `isDefault=true` 時はトランザクション内で他住所の `is_default` を落としてから保存
  - 取得時に存在しなければ `ResourceNotFoundException`（`common`・コード `RESOURCE_NOT_FOUND`）、`userId` 不一致なら新規 `ResourceAccessDeniedException`（`ForbiddenException` 継承・コード `ACCESS_DENIED`）。`GlobalExceptionHandler` がコード/ステータスを自動描画する
- `AddressController`（`@RequestMapping("/api/v1/users/me/addresses")`）: CRUD 4 本・`@AuthenticationPrincipal` から userId 取得・委譲のみ。`POST` は `201 Created` + `Location` ヘッダー。

#### ステップ 4: バリデーションルールの追記

- `VALIDATION_RULES.md §5` に住所フィールド（`recipientName`/`postalCode`/`prefecture`/`city`/`addressLine`/`phoneNumber`）を追記し、Bean Validation（バックエンド）と zod（フロント）を同期させる（OQ-5）。

---

## 6. Frontend Implementation Plan

### 6.1 前提条件（`feature/auth` 実装済み）

- [ ] `useAuthStore`（`src/stores/useAuthStore.ts`）が存在し `accessToken` / `user` / `setAuth` / `clearAuth` を提供する
- [ ] `src/lib/api/client/users.ts` に `getCurrentUser` が存在する（拡張する）
- [ ] BFF プロキシ（`src/app/api/v1/[...path]`）が `access_token` Cookie を Bearer に詰め替え中継する
- [ ] `ApiError` / `ProblemDetail` 型・TanStack Query Provider が利用可能
- [x] `src/proxy.ts` の `PROTECTED_PREFIXES` に `/profile` が含まれる（確認済み・`refresh_token` Cookie の有無でソフト判定）

### 6.2 画面スコープ（フェーズ差に注意）

| 画面 | URL | フェーズ | 本スライスでの扱い |
|---|---|---|---|
| プロフィール設定 | `/profile/settings` | **Phase 2** | ✅ 実装する（表示名・アバター・パスワード変更・退会） |
| 配送先住所管理 | `/profile/addresses` | **Phase 3** | ⚠️ バックエンド API は本スライスで完成。FE 画面は IA 上 Phase 3。OQ-2 で「同時に作るか後続か」を判断 |

### 6.3 実装ファイル一覧

> **ルート配置（確定）:** `FRONTEND_IA.md §3` に従い **`(authenticated)`** ルートグループを新設した（当初案の仮称 `(protected)` は不採用）。`/profile` のソフト認証ガードは `src/proxy.ts` の `PROTECTED_PREFIXES`（`'/profile'` 既存）が担うため、ルートグループは**グローバル chrome を共有するため**に存在する。URL には影響しない。

```
src/
├── app/
│   └── (authenticated)/                     # ★ 新規（認証必須レイアウト共有用）。proxy.ts が /profile を既にガード
│       └── profile/
│           ├── settings/page.tsx            # ★ プロフィール設定（Phase 2）
│           └── addresses/page.tsx           # ★ 住所管理（Phase 3・OQ-2）
├── components/
│   ├── profile/
│   │   ├── ProfileSettingsForm.tsx          # ★ CC：表示名・アバター更新
│   │   ├── ChangePasswordForm.tsx           # ★ CC：パスワード変更
│   │   └── WithdrawDialog.tsx               # ★ CC：退会確認ダイアログ → clearAuth → /
│   └── address/
│       ├── AddressList.tsx                  # ★ CC：一覧 + デフォルト表示
│       ├── AddressFormSheet.tsx             # ★ CC：追加/編集フォーム（Sheet/Modal）
│       └── AddressCard.tsx                  # ★ 単一住所カード（編集・削除）
├── lib/
│   ├── api/client/
│   │   ├── users.ts                         # 🔄 updateProfile / changePassword / withdrawAccount を追加
│   │   └── addresses.ts                     # ★ list/create/update/delete
│   └── validations/
│       ├── profile.ts                       # ★ updateProfileSchema / changePasswordSchema
│       └── address.ts                       # ★ addressSchema
└── types/
    └── api/
        └── address.ts                       # ★ Address 型
```

### 6.4 実装ステップ（依存順）

1. **型定義**: `Address`（`types/api/address.ts`）。`AuthUser` は既存を再利用。
2. **Zod スキーマ**: `updateProfileSchema`（`displayName` 1〜100・`avatarUrl` URL 任意）/ `changePasswordSchema`（`newPassword` 8〜72）/ `addressSchema`（住所各フィールド・`VALIDATION_RULES` と同期）。`VALIDATION_RULES.md §2` の zod 例に準拠。
3. **API クライアント**: `users.ts` に `updateProfile`/`changePassword`/`withdrawAccount` 追加、`addresses.ts` 新規。いずれも BFF プロキシ（`/api/v1/...`）経由・401 は single-flight refresh に委ねる。
4. **プロフィール設定画面**（`/profile/settings`・Phase 2）:
   - `ProfileSettingsForm`: `react-hook-form` + `updateProfileSchema` + `useMutation`。成功で `useAuthStore.setAuth`（表示名がヘッダーに反映）+ `queryKeys.users.me` を invalidate。
   - `ChangePasswordForm`: `PASSWORD_CHANGE_FAILED` をフィールド/フォームエラーとして表示。
   - `WithdrawDialog`: 確認 → `DELETE /users/me` → `clearAuth()` → `router.replace('/')`（`FRONTEND_API_CONTRACT §6.2`）。
5. **住所管理画面**（`/profile/addresses`・Phase 3・OQ-2 次第）:
   - `AddressList` / `AddressFormSheet` / `AddressCard`。`useQuery(queryKeys.users.addresses)` + 各 mutation 後に invalidate（`FRONTEND_API_CONTRACT §クエリキー`）。
6. **認証ガード確認**: `src/proxy.ts` の保護対象に `/profile` が含まれることを確認（既存）。

---

## 7. Task Breakdown

各タスクには `depends_on` を明示する。並列実行できるタスクは `[並列可]`。

> **凡例:** ✅ Done / ⬜ Todo / ⚠️ 要判断（OQ 待ち）

| ID | タスク | 担当 | 依存 | ステータス |
|---|---|---|---|---|
| U-00 | 前提確認（`feature/auth` の User 関連・`addresses`/`users` スキーマ・インデックス・Seed ユーザー存在） | BE | なし | ✅ Done |
| U-01 | `UpdateProfileRequest` / `ChangePasswordRequest` DTO + `PasswordChangeFailedException` | BE | なし `[並列可]` | ✅ Done |
| U-02 | `User` ドメインメソッド（`updateProfile` / `changePassword`）追加 | BE | なし `[並列可]` | ✅ Done |
| U-03 | `UserService` 拡張（`updateProfile` / `changePassword` / `withdraw`） | BE | U-01, U-02 | ✅ Done |
| U-04 | `UserController` 拡張（`PATCH /me` / `PATCH /me/password` / `DELETE /me`）+ `@Auditable` | BE | U-03 | ✅ Done |
| U-05 | `VALIDATION_RULES.md §5` に住所フィールド追記（postalCode/phone 正規表現確定） | DOC | なし `[並列可]` | ✅ Done |
| U-06 | `order` ドメイン: `Address` Entity + `AddressRepository` | BE | U-00 | ✅ Done |
| U-07 | `CreateAddressRequest` / `UpdateAddressRequest` / `AddressResponse` DTO + `ResourceAccessDeniedException`（403 `ACCESS_DENIED`・`ForbiddenException` 継承） | BE | U-05 | ✅ Done |
| U-08 | `AddressService`（CRUD + `isDefault` 付け替え + 所有権チェック 403/404） | BE | U-06, U-07 | ✅ Done |
| U-09 | `AddressController`（`/users/me/addresses` CRUD 4 本） | BE | U-08 | ✅ Done |
| U-10 | Seed: 住所データ（`dev/V13__seed_addresses.sql`） | BE | U-00 | ✅ Done |
| U-11 | Backend 単体テスト（`UserServiceTest` 拡張・`AddressServiceTest` 新規・Mockito） | BE | U-03, U-08 | ✅ Done |
| U-12 | Backend Controller/統合テスト（`UserControllerTest` 拡張＝`ControllerTestBase` スライス・`AddressController` は `IntegrationTestBase` で 403/404・`isDefault` 付け替えを DB 検証） | BE | U-04, U-09 | ✅ Done |
| U-13 | FE: 型定義（`types/api/address.ts`） | FE | なし `[並列可]` | ✅ Done |
| U-14 | FE: Zod スキーマ（`profile.ts` / `address.ts`）+ `constants/prefectures.ts` | FE | U-05, U-13 | ✅ Done |
| U-15 | FE: API クライアント（`users.ts` 拡張 / `addresses.ts`） | FE | U-13 | ✅ Done |
| U-16 | FE: プロフィール設定画面（`/profile/settings`・3 フォーム + 退会） | FE | U-14, U-15 | ✅ Done |
| U-17 | FE: 住所管理画面（`/profile/addresses`） | FE | U-14, U-15 | ✅ Done（OQ-2 決着: 本スライスに含める） |
| U-18 | FE: コンポーネントテスト（Vitest + RTL + MSW） | FE | U-16 | ✅ Done（27 件追加・計 50 件グリーン） |

**依存グラフ（クリティカルパス）:**

```
U-00 ┬─► U-06 ──► U-08 ──► U-09 ──► U-12
     └─► U-10
U-01 ┐
U-02 ┼─► U-03 ──► U-04 ──► U-12
U-05 ┴─► U-07 ──► U-08
U-13 ─► U-14 ─► U-15 ─► U-16 ─► U-18
                         └─► U-17（OQ-2）
```

---

## 8. Security Checklist

コードレビュー前に全項目を確認すること。

**実施日:** 2026-08-01 / **結果:** 全 14 項目クリア（`./gradlew build` グリーン・146 tests・JaCoCo ゲート 0.80 維持）  
**追記（2026-08-02）:** S-3（退会時の Refresh Token 残存）と R-4（並行リクエストによる複数デフォルト）を対応済みに変更。テスト 6 件追加で **152 tests**・JaCoCo ゲート 0.80 維持。  
DB 挙動に依存する項目（`@SQLRestriction` 除外・BCrypt 実ハッシュ・トークン由来の対象解決）は
`UserControllerIntegrationTest`（本チェック実施時に新規追加）で裏付けを取得した。

### 認可・所有権

- [x] 全エンドポイントが認証必須（`SecurityConfig.anyRequest().authenticated()` で担保）であることをコードで確認
  - 根拠: `config/SecurityConfig.java:105`。`permitAll` 列挙に `/api/v1/users/**` は含まれない。未認証 401 を全 8 エンドポイントでテスト済み（`UserControllerTest` / `AddressControllerIntegrationTest`）
- [x] 操作対象ユーザーを**常にトークンの `sub`（`@AuthenticationPrincipal`）から解決**している（リクエストボディ/パスの user_id を信用しない）
  - 根拠: `UserController` / `AddressController` の全ハンドラーが `principal.getUserId()` のみを Service に渡す。パス・DTO に user_id を持たない。ボディに他人の `id`/`userId` を混ぜても無視されることを `UserControllerIntegrationTest#should_resolve_target_user_from_token_and_ignore_body_user_id` で検証
- [x] 住所の更新・削除で**所有権チェック**（`address.userId == principal.userId`）を行い、他人の住所には `ACCESS_DENIED`（403）を返している
  - 根拠: `AddressService#findOwned`（`service/AddressService.java:112`）を update / delete の両方が通る。`AddressControllerIntegrationTest` の 403 テスト 2 件で、他人の住所が改変されないこと（元の値保持）まで検証
- [x] 存在しない住所には `RESOURCE_NOT_FOUND`（404）を返している（403/404 の区別が `API_DESIGN.md §3` と一致）
  - 根拠: `AddressService.java:111`（存在判定 → 所有判定の順）。404 テスト 2 件

### パスワード

- [x] パスワード変更は BCrypt **cost 12** で再ハッシュしている
  - 根拠: `PasswordEncoder` Bean は `new BCryptPasswordEncoder(12)` の 1 つのみ（`SecurityConfig.java:118`）。DB 上のハッシュが `$2a$12$` で始まり旧ハッシュと異なることを `UserControllerIntegrationTest#should_rehash_password_with_bcrypt_cost_12` で検証
- [x] `currentPassword` 照合は `passwordEncoder.matches` で行い、不一致は `PASSWORD_CHANGE_FAILED`（400・存在情報を漏らさない）
  - 根拠: `UserService.java:68-71`。不一致理由（未設定 / 不一致）を区別しない単一例外。不一致時に `password_hash` が変化しないことも検証済み
- [x] Google 専用ユーザー（`passwordHash == null`）のパスワード変更を拒否している
  - 根拠: `user.hasPassword()` を短絡評価で先に判定（`UserService.java:68`）。`UserControllerIntegrationTest#should_reject_password_change_for_google_only_user`
- [x] `currentPassword` / `newPassword` をログ・レスポンスに出力していない（`GlobalExceptionHandler.SENSITIVE_FIELDS` に `password` が含まれることを確認）
  - 根拠: `GlobalExceptionHandler.java:37`（部分一致判定のため `currentPassword`/`newPassword` 双方がマスク対象）。`UserService`/`UserController`/`AddressService` にログ出力なし。`AuditLogAspect` はメソッド引数を保存せず、`entityIdParam` の UUID のみ抽出する（`USER_PASSWORD_CHANGED` の監査行に平文は乗らない）。`UserResponse` は `passwordHash` を持たない

### 退会・論理削除

- [x] 退会は `deleted_at` 設定の論理削除であり物理削除をしていない（`SoftDeletableEntity#softDelete`）
  - 根拠: `UserService.java:88`。退会後も行が残り `deleted_at IS NOT NULL` であることを生 SQL で検証（`should_soft_delete_user_and_keep_row_in_database`）
- [x] 退会後、`@SQLRestriction` により当該ユーザーが通常クエリから除外されることを確認
  - 根拠: `SoftDeletableEntity.java:15` の `@SQLRestriction` が `@MappedSuperclass` 経由で `User` に適用されることを実機（Testcontainers PostgreSQL）で確認。`findById` / `findByEmail` / `existsByEmail` がいずれも除外される（`should_exclude_withdrawn_user_from_normal_queries`）。有効期限内のアクセストークンで再アクセスしても `GET /users/me` は 404 `RESOURCE_NOT_FOUND`（OQ-3 の確定挙動・`should_reject_access_with_still_valid_token_after_withdrawal`）
- [x] 住所削除は仕様どおり物理削除（`addresses` に `deleted_at` を持たない）
  - 根拠: `Address` は `BaseEntity` を継承（`SoftDeletableEntity` ではない）。`AddressControllerIntegrationTest#should_physically_delete_own_address`

### 入力バリデーション

- [x] 文字数上限（`displayName` 100 / `newPassword` 72 / 住所各フィールド）を Bean Validation で弾いている
  - 根拠: `UpdateProfileRequest`（`@Size(min=1, max=100)`）・`ChangePasswordRequest`（`@Size(min=8, max=72)`）・`CreateAddressRequest` / `UpdateAddressRequest`（宛名 100 / 都道府県 20 / 市区町村 100 / 番地 255）。`PATCH` 用 DTO は `@NotBlank` を外し `@Size(min=1)` としているため「未送信＝不変・空文字＝422」が成立する
- [x] `avatarUrl` を `@URL` で検証している（任意の文字列を保存しない）
  - 根拠: `UpdateProfileRequest.avatarUrl` の `@URL`。空文字のみ例外的に通過し `User#updateProfile` で `null` 化（クリア要求・OQ-4 の決定どおり）
- [x] `postalCode` / `phoneNumber` の形式を正規表現で検証している（`VALIDATION_RULES §5`）
  - 根拠: `^\d{3}-?\d{4}$` / `^0\d{1,4}-?\d{1,4}-?\d{4}$` を Create / Update 両 DTO に付与。`should_return_422_when_postal_code_is_invalid`

### 実施時に検出・対応した事項

| # | 内容 | 重大度 | 対応 |
|---|---|---|---|
| S-1 | **デフォルト住所の消失**（R-4 の具体化）。既にデフォルトの住所へ `PATCH {"isDefault": true}` を再送すると、`clearDefaultForUser` の一括 UPDATE が対象住所自身も `false` に落とす一方、エンティティに変更がなく dirty checking で復元されないため、DB からデフォルト住所が消える（レスポンスは `isDefault: true` を返すため不整合に気付けない） | 中（データ整合性・チェックリスト項目外） | 対応済み。`AddressRepository#clearDefaultForUserExcept`（対象住所を除外）を追加し `AddressService#update` から使用。回帰テスト 3 件を `AddressControllerIntegrationTest` に追加（再指定でデフォルト維持 / 付け替え / 他ユーザーのデフォルトに影響しない） |
| S-2 | `PATCH /users/me/password` が**レート制限の「API 全般」バケット（100 req/min/user）**に入る。`RateLimitingFilter` は `/api/v1/auth/` 前缀のみを認証系（10 req/min/IP）として扱うため、アクセストークンを奪取した攻撃者が `currentPassword` を毎分 100 回試行できる | 低〜中（未対応・要判断） | 未対応。`SECURITY.md` のレート制限方針に関わるため本スライスでは変更せず提起にとどめる。対応するならパスワード変更を認証系バケット扱いにする（`RateLimitingFilter` にパス追加）のが最小 |
| S-3 | 退会時に当該ユーザーの `refresh_tokens` を削除していない。`@SQLRestriction` により `AuthService#refresh` は `RESOURCE_NOT_FOUND` で失敗するため**悪用は不可**だが、レコードが最長 7 日残存する | 低 | ✅ **対応済み（2026-08-02）**。`UserService#withdraw` で `refreshTokenRepository.deleteAllByUserId(userId)` を呼ぶ（`RefreshTokenPurgeJob` は `expires_at` 基準のため 90 日バッチを待てない）。同一 identity ドメイン内のためイベント不要。テスト 2 件追加: `UserServiceTest#should_delete_refresh_tokens_when_withdrawing`・`UserControllerIntegrationTest#should_delete_refresh_tokens_when_withdrawing`（有効期限内のトークン行が DB から消えることを実 DB で検証） |
| S-4 | `displayName` は `@Size(min=1)` のため空白のみ（`"   "`）を通す | 低（未対応・任意） | 未対応。`@Pattern` で非空白必須にするか、`VALIDATION_RULES.md §2.1` に合わせるかは UX 側の判断 |

---

## 9. Test Checklist

> **実施結果（2026-08-01・2026-08-02 に S-3 / R-4 対応で 6 件追加）:** 全項目実施済み。`./gradlew cleanTest test jacocoTestReport jacocoTestCoverageVerification` → **BUILD SUCCESSFUL / 152 tests・0 failed・0 skipped**（JaCoCo カバレッジゲート 0.80 通過）。`pnpm test` → **10 files / 51 tests 全 pass**、`pnpm lint` / `pnpm typecheck` もグリーン。  
> 本スライス該当クラスの行カバレッジ: `UserService` 100%（分岐 100%）・`AddressService` 97.0%（分岐 87.5%）・`UserController` / `AddressController` 100%。

### 9.1 Backend テスト（JUnit 5 + Mockito + Testcontainers）

> **既存テスト基盤を踏襲する（`io.kivio.support`）:**
> - **単体（service）**: Mockito（`UserServiceTest` が前例）。`AddressService` も同様にモックで。
> - **Controller スライス**: `ControllerTestBase`（`@WebMvcTest` + `@Import(SecurityConfig)` + `JwtProvider` モック・service は `@MockitoBean`）。`UserControllerTest` が前例で、**新規 `PATCH/DELETE` のレスポンス形式・ステータス・バリデーションはまずこのスライスで検証する**。
> - **フルスタック統合**: `IntegrationTestBase`（Testcontainers PostgreSQL）。`AuthControllerIntegrationTest` が前例。住所 CRUD の**所有権チェック（403/404）・`isDefault` 付け替え・退会後の `@SQLRestriction` 除外**など DB 挙動に依存するものはこちらで検証する。
> - **Repository**: `RepositoryTestBase`。`AddressRepository` のカスタムクエリ（デフォルト付け替え `@Modifying`・並び順）はこちらで検証可。  
>   → **実施時の判断:** 専用の `AddressRepositoryTest` は作成せず、`clearDefaultForUser` / `clearDefaultForUserExcept` / `findByUserIdOrderByIsDefaultDescCreatedAtAsc` は `AddressControllerIntegrationTest`（同じ Testcontainers PostgreSQL）から実挙動として検証した。`@Modifying` + dirty checking の相互作用（S-1）は API 経由でないと再現しないため。

#### UserService 単体テスト（既存 `UserServiceTest` を拡張）

- [x] `updateProfile`: 表示名・アバターを更新し `UserResponse` を返す — `should_update_display_name_and_avatar_when_both_provided`
- [x] `updateProfile`: 一部フィールドのみ送信 → 送信フィールドのみ更新される（未送信は不変）— `should_update_only_provided_fields_and_keep_unsent_fields_unchanged`
- [x] `updateProfile`: `avatarUrl` に空文字 `""` → null クリア（OQ-4 の確定挙動）— `should_clear_avatar_url_when_empty_string_is_sent`
- [x] `changePassword`: 正しい `currentPassword` → `passwordHash` が更新される — `should_rehash_password_when_current_password_matches`
- [x] `changePassword`: 誤った `currentPassword` → `PASSWORD_CHANGE_FAILED` — `should_fail_password_change_when_current_password_does_not_match`
- [x] `changePassword`: Google 専用ユーザー（`passwordHash == null`） → `PASSWORD_CHANGE_FAILED` — `should_fail_password_change_when_user_has_no_password`
- [x] `withdraw`: `deleted_at` が設定される — `should_set_deleted_at_when_withdrawing`
- [x] `withdraw`: 当該ユーザーの Refresh Token を削除する（§8 S-3）— `should_delete_refresh_tokens_when_withdrawing`
- [x] `getProfile`: `hasPassword` フラグが `passwordHash` の有無で切り替わる（FE のパスワード欄出し分けに使用）— `should_expose_has_password_true_when_password_is_set` / `..._false_for_google_only_user`

#### AddressService 単体テスト（新規 `AddressServiceTest`）

- [x] `list`: 自分の住所のみ返す — `should_return_only_own_addresses`
- [x] `create`: 住所を保存し `AddressResponse` を返す — `should_save_address_and_return_response`
- [x] `create(isDefault=true)`: 既存のデフォルト住所が `false` に落ちる — `should_clear_existing_default_when_creating_default_address`
- [x] `update`: 自分の住所を更新する — `should_update_own_address`
- [x] `update(isDefault=true)`: 既存のデフォルト住所が `false` に落ちる — `should_clear_existing_default_when_updating_address_to_default`
- [x] `update`: 他人の住所 → `ACCESS_DENIED`（403）— `should_deny_update_of_another_users_address`
- [x] `delete`: 他人の住所 → `ACCESS_DENIED`（403）— `should_deny_delete_of_another_users_address`
- [x] `update`/`delete`: 存在しない id → `RESOURCE_NOT_FOUND`（404）— `should_throw_not_found_when_updating_absent_address` / `..._when_deleting_absent_address`
- [x] `delete`: 自分の住所を物理削除する — `should_delete_own_address`

#### Controller テスト（スライス `ControllerTestBase` + 必要に応じ `IntegrationTestBase`）

- [x] `PATCH /users/me` 200・レスポンス形式が `API_DESIGN.md` と一致 — `UserControllerTest#should_return_updated_profile_when_patching_me`
- [x] `PATCH /users/me` 422（`displayName` 空白）/ 空文字 `avatarUrl` を許容（OQ-4）— `..._422_when_patching_me_with_blank_display_name` / `should_accept_empty_avatar_url_as_clear_request`
- [x] `PATCH /users/me` 更新対象はトークンの `sub` のみ（ボディの `id`/`userId` は無視）— `UserControllerIntegrationTest#should_resolve_target_user_from_token_and_ignore_body_user_id`
- [x] `PATCH /users/me/password` 204 / 400（不一致）/ 422（バリデーション）— `UserControllerTest` 3 件
- [x] `PATCH /users/me/password` BCrypt cost 12 で再ハッシュ / 失敗時は既存ハッシュ不変 / Google 専用ユーザーは拒否 — `UserControllerIntegrationTest` 3 件
- [x] `DELETE /users/me` 204 → 行は残り `deleted_at` が入る（物理削除でない）・`@SQLRestriction` で通常クエリから除外される — `UserControllerIntegrationTest` 2 件
- [x] `DELETE /users/me` で有効期限内の `refresh_tokens` 行も削除される（§8 S-3）— `UserControllerIntegrationTest#should_delete_refresh_tokens_when_withdrawing`
- [x] `DELETE /users/me` 後、**有効期限内のアクセストークンでも `GET /users/me` は 404 `RESOURCE_NOT_FOUND`**（OQ-3 の確定挙動）— `should_reject_access_with_still_valid_token_after_withdrawal`
- [x] `GET /users/me/addresses` 200（配列形式）・他人の住所を含まない — `should_return_only_own_addresses_as_array`
- [x] `GET /users/me/addresses` 並び順「デフォルト優先 → `created_at` 昇順」（OQ-5）— `should_order_addresses_by_default_first_then_created_at_asc`
- [x] `POST /users/me/addresses` 201 + `Location` ヘッダー / 422（`postalCode` 不正）— `AddressControllerIntegrationTest` 2 件
- [x] `PATCH /users/me/addresses/{id}` 200 / 403（他人）/ 404（不存在）— `AddressControllerIntegrationTest` 3 件
- [x] `DELETE /users/me/addresses/{id}` 204（物理削除）/ 403 / 404 — `AddressControllerIntegrationTest` 3 件
- [x] 認証なしで各エンドポイント → 401 — `UserControllerTest` 3 件 + `AddressControllerIntegrationTest` 4 件
- [x] **`isDefault` 付け替えの回帰（§8 S-1）**: ①既定の住所へ `{"isDefault": true}` を再送してもデフォルトが維持される ②付け替えで旧デフォルトが降格する ③他ユーザーのデフォルトに影響しない — `should_keep_default_when_reasserting_default_on_the_current_default_address` / `should_promote_address_to_default_and_demote_the_previous_one` / `should_not_touch_other_users_default_address_when_setting_own_default`
- [x] **デフォルト住所の一意性を DB 制約で担保（R-4）**: ①アプリの付け替え手順を経由せず 2 件目のデフォルトを挿入すると `DataIntegrityViolationException` ②非デフォルトは何件でも許容（部分インデックスであること）③一意性はユーザー単位で他ユーザーと衝突しない — `AddressControllerIntegrationTest` 3 件（`should_reject_second_default_address_at_database_level` / `should_allow_multiple_non_default_addresses` / `should_allow_one_default_address_per_user`）
- [x] **競合時の応答（R-4）**: `DataIntegrityViolationException` → 409 `DUPLICATE_ENTRY`・DB のメッセージ（制約名）を露出しない — `GlobalExceptionHandlerTest#should_map_data_integrity_violation_to_409_duplicate_entry`

### 9.2 Frontend テスト（Vitest + RTL + MSW / Playwright）

> OQ-2 は「本スライスで住所 UI も実装する」で決着済みのため、住所テストの「（住所 UI 実装時）」という条件付き表記は解消した。

- [x] `ProfileSettingsForm`: 表示名更新 → `updateProfile` 呼び出し・store（ヘッダー）反映 — `表示名を変更して保存すると store が更新され完了表示が出る`
- [x] `ProfileSettingsForm`: 部分更新（変更したフィールドのみ送信）/ アバター削除は空文字送信（OQ-4）— 2 件
- [x] `ProfileSettingsForm`: バリデーションエラー表示・サーバーエラー時のアラート・未変更時は保存非活性 — 3 件
- [x] `ChangePasswordForm`: `PASSWORD_CHANGE_FAILED`（400）でエラーメッセージ表示 — `PasswordSection.test.tsx`（`ChangePasswordForm` は `PasswordSection` 経由で描画されるためテストは同ファイルに集約）
- [x] `PasswordSection`: `hasPassword: false`（Google 専用）ではフォームを出さない / `undefined` では出す / 表示トグル — 3 件
- [x] `WithdrawDialog`: 退会確認 → `clearAuth` → `/` 遷移 — `退会に成功すると認証状態を破棄してトップへ遷移する`
- [x] `WithdrawDialog`: 同意チェックまで実行非活性 / 失敗時はダイアログを閉じない / 対象メール表示 — 3 件
- [x] `AddressFormSheet`: 追加/編集 → mutation 呼び出し・一覧 invalidate・404（他タブで削除済み）でシートを閉じる — 6 件
- [x] `AddressList`: デフォルト住所のバッジ表示・「デフォルトにする」で `isDefault: true` を PATCH・空状態・取得失敗・削除確認ダイアログ — 6 件
- [ ] **（未実施・後続）** コミット対象の Playwright E2E は `e2e/auth.spec.ts`（認証フロー）のみ。`/profile/settings`・`/profile/addresses` の E2E スペックは未作成。§10「動作確認」では使い捨ての Playwright スクリプト（scratchpad・非コミット）で実ブラウザ操作を確認済みだが、スイートへの常設は後続とする

---

## 10. Definition of Done

PR をマージするには以下を全て満たすこと。

### 機能要件

- [x] `PATCH /users/me`（プロフィール部分更新）が動作する
  - 根拠: `displayName` のみ送信 → 表示名のみ更新 / `avatarUrl` のみ送信 → 表示名は不変 / `avatarUrl: ""` → クリア（OQ-4 の確定挙動）を実 API で確認。`displayName: ""` は 422 `VALIDATION_FAILED`。ボディに他人の `id`/`userId` を混ぜても対象はトークンの `sub` のまま
- [x] `PATCH /users/me/password`（現パスワード照合 + 再ハッシュ）が動作し、不一致で 400 を返す
  - 根拠: 不一致 → 400 `PASSWORD_CHANGE_FAILED` / `newPassword` 7 文字 → 422（`errors[].rejectedValue` は出力されずマスク） / 成功 → 204。DB 上のハッシュが `$2a$12$` 始まりで旧ハッシュから変化し、新パスワードでのログインが 200。`audit_logs` に `USER_PASSWORD_CHANGED`（`entity_type=USER`）が記録される
- [x] `DELETE /users/me`（論理削除）で `deleted_at` が設定され、以後アクセスできない
  - 根拠: 使い捨てユーザーで 204 → 行は残り `deleted_at IS NOT NULL`。**有効期限内の同じアクセストークン**で `GET /users/me` は 404 `RESOURCE_NOT_FOUND`、再ログインは 401 `INVALID_CREDENTIALS`。`audit_logs` に `USER_WITHDRAWN`
- [x] 住所 CRUD 4 本が動作し、`isDefault` 付け替え・所有権チェック（403/404）が正しい
  - 根拠: `POST` 201 + `Location` ヘッダー / `postalCode` 不正で 422 / `PATCH` 200 / `DELETE` 204（DB から行が消える物理削除）。`isDefault: true` 指定で旧デフォルトが降格し DB 上のデフォルトは常に 1 件、既定住所への `isDefault: true` 再送でもデフォルトは維持（§8 S-1 の回帰）。他ユーザーの住所は `PATCH`/`DELETE` とも 403 `ACCESS_DENIED` で値も改変されず、存在しない id は 404 `RESOURCE_NOT_FOUND`。未認証は全て 401
- [x] `GET /users/me/addresses` が `API_DESIGN.md §3` の配列形式で返る
  - 根拠: `PageResponse` ラップなしの素の配列。並び順は「デフォルト優先 → `created_at` 昇順」（OQ-5）。他人の住所を含まない

### セキュリティ要件

- [x] §8 Security Checklist の全項目を確認済み（2026-08-01 実施・根拠は §8 に記載）
- [x] 所有権チェック・BCrypt cost 12・機微情報の非出力をコードで確認

### テスト要件

- [x] Backend: `./gradlew test` がグリーン（Testcontainers 含む）。JaCoCo カバレッジゲート（0.80）を維持
  - 根拠: `./gradlew cleanTest test jacocoTestReport jacocoTestCoverageVerification` → **BUILD SUCCESSFUL / 152 tests・0 failed・0 skipped**（2026-08-02 の S-3 / R-4 対応後）。全体行カバレッジ 95.5%・分岐 83.8%
- [x] Backend: `UserService` / `AddressService` の単体カバレッジ ≥ 80%
  - 根拠: `UserService` 行 100% / 分岐 100%、`AddressService` 行 97.0% / 分岐 87.5%。`UserController` / `AddressController` / `User` / `Address` はいずれも行 100%
- [x] Frontend: `pnpm test` / `pnpm lint` / `pnpm typecheck` / `pnpm build` がグリーン
  - 根拠: `pnpm test` → 10 files / 51 tests 全 pass。`pnpm lint`・`pnpm typecheck` は exit 0。`pnpm build` は成功し `/profile/settings`・`/profile/addresses` がルート一覧に出力される

### コード品質

- [x] `BACKEND_CODING_STANDARDS.md` 準拠（DTO=record・Lombok パターン・レイヤー責務・`@Transactional(readOnly)` の適切な使用・例外は `KivioException` 階層）
  - 根拠: 本スライスの DTO 5 本（`UpdateProfileRequest` / `ChangePasswordRequest` / `CreateAddressRequest` / `UpdateAddressRequest` / `AddressResponse`）は全て `public record`。Service は `@RequiredArgsConstructor` + コンストラクタインジェクション、参照系（`getById` / `list`）のみ `@Transactional(readOnly = true)`。例外は `PasswordChangeFailedException extends BadRequestException` / `ResourceAccessDeniedException extends ForbiddenException` でいずれも `KivioException` 階層
- [x] `FRONTEND_CODING_STANDARDS.md` 準拠（`'use client'` 葉限定・export 規約・TanStack Query mutation・Zustand セレクタ）
  - 根拠: `page.tsx` / `layout.tsx` は `'use client'` を持たない Server Component で、`'use client'` は `components/profile` / `components/address` の葉コンポーネントのみ（14 ファイル）。両ディレクトリに `export default` はなく named export に統一。mutation は `hooks/mutations/`（`useUpdateProfileMutation` / `useChangePasswordMutation` / `useWithdrawAccountMutation` / `useUpsertAddressMutation` / `useSetDefaultAddressMutation` / `useDeleteAddressMutation`）に分離し `queryKeys.user.*` を invalidate。Zustand は `useAuthStore((state) => ...)` のセレクタ形式
- [x] **ドメイン間の直接 import がない**（`identity` ↔ `order` は `userId`（UUID）値参照のみ・エンティティ参照を張らない）
  - 根拠: `domain/order/**` から他ドメインへの import は 0 件。`domain/identity/**` からの他ドメイン import は `domain.audit.annotation.Auditable`（横断関心の AOP アノテーション・`AuthService` に既存の前例あり）のみで、集約エンティティへの参照はない。`Address` は `userId` を `UUID` 値として保持し `@ManyToOne` を張らない
- [x] Controller がビジネスロジックを持たない（Service 委譲のみ）
  - 根拠: `UserController` / `AddressController` の全 8 ハンドラーが `principal.getUserId()` と DTO を Service に渡して結果を返すのみ。分岐・計算はなく、`AddressController#create` の `Location` ヘッダー組み立て（`ServletUriComponentsBuilder`）のみが HTTP 層の責務として残る
- [x] Swagger UI で全エンドポイントが確認できる（`@Tag` / `@Operation` 付与）
  - 根拠: `/swagger-ui/index.html` が 200。`/v3/api-docs` に本スライスの 8 エンドポイント全てが出力され、`@Tag`（`User` / `Address`）と `@Operation(summary)` が付いている

### 動作確認

- [x] **（未実施・代替検証済み）** `docker compose up` で全サービス起動
- [x] ブラウザから `/profile/settings` でプロフィール更新・パスワード変更・退会が一通り操作できる
  - 根拠: Playwright（Chromium）で実操作。①表示名を変更 → 「保存しました」表示 + ヘッダー（Zustand store）へ反映 → 復元、②誤った現パスワードで「現在のパスワードが正しくありません」表示 → 正しいパスワードで「パスワードを変更しました」→ 復元、③使い捨てユーザーで退会ダイアログ → 同意チェック → 実行 → `/` へ遷移。未認証時は `/profile/settings`・`/profile/addresses` とも `proxy.ts` により `/auth/login?from=...` へ 307
- [x] （住所 UI 実装時）住所の追加・編集・削除・デフォルト切替が操作できる
  - 根拠: 同じく Playwright で `/profile/addresses` を実操作。Seed の 2 件が表示され、追加（都道府県は Select コンボボックス）→ 一覧反映、「デフォルトにする」→ 当該カードのボタンが消えデフォルトが移る、削除 → 確認ダイアログ → 一覧から消える、まで一通り成功
- [x] Flyway マイグレーション（必要なら住所 Seed）が正常適用される
  - 根拠: `flyway_schema_history` の V1〜V11 + dev の V10（開発シード）・V13（住所シード）が全て `success = true`。`GET /users/me/addresses` で V13 の住所 2 件（うち 1 件がデフォルト）が返る

### 実施時に検出した事項（DoD 判定に影響しない）

| # | 内容 | 重大度 | 対応 |
|---|---|---|---|
| D-1 | `PATCH /users/me` に `displayName: ""` を送ると 422 になるが、メッセージが `@Size(min=1, max=100)` の一括メッセージ「表示名は100文字以内で入力してください」となり、下限違反に対して説明が噛み合わない | 低（未対応・任意） | 未対応。`@Size` を分割するか `VALIDATION_RULES.md §2.1` のメッセージを見直す。§8 S-4（空白のみを通す件）と併せて UX 側で判断 |
| D-2 | 認証後のページで `/cart`・`/orders`・`/messages`・`/seller/applications/new` への RSC プリフェッチが 404 を返す（ブラウザコンソールにエラー） | 低（本スライス範囲外） | 対応不要。いずれも Phase 3 以降で実装予定のルートで、グローバルナビのリンクがプリフェッチしているだけ。当該画面の実装時に解消する |
| D-3 | 認証系レート制限（10 req/min/IP）により、ログインを繰り返す E2E シナリオを連続実行するとログインが失敗しうる | 低（仕様どおり） | 対応不要。§8 S-2 と同じ `RateLimitingFilter` の挙動。E2E を常設する際はストレージステートの再利用でログイン回数を抑えるのが定石 |

---

## 11. Risks / Open Questions

### Open Questions（2026-08-01 時点で全件クローズ）

> UI 設計側で追加検討した OQ-P1〜P6 とその決定は `design-system/pages/user-profile.md §17` にまとめてある。

| # | 質問 | 影響タスク | 決定 |
|---|---|---|---|
| ~~OQ-1~~ ✅ | **Address 集約の所属ドメイン** — `CLAUDE.md`/`DB_DESIGN.md` では `order` ドメイン。URL は `/users/me/addresses`（identity 文脈）。どちらに置くか | U-06〜U-09 | **`order` ドメインに置く**（CLAUDE.md 準拠）。`identity` とは `userId`（UUID）値参照のみ。本スライスで `domain/order/` を新規作成 |
| ~~OQ-2~~ ✅ | **住所管理 UI（`/profile/addresses`）のフェーズ** — IA では Phase 3。バックエンド API は本スライス（Phase 2）で完成。FE 画面も同時に作るか後続スライスに委ねるか | U-17 | **決定: 本スライスで FE も実装する**（一覧/追加/編集/削除/デフォルト切替）。API が完成済みで UI 設計も確定しているため後続に残す理由がない。郵便番号→住所自動補完などの装飾的 UX のみ Phase 3 に残す |
| ~~OQ-3~~ ✅ | **退会時の連動処理と監査アクション** — `DB_DESIGN.md §3.2` は `users.deleted_at` 設定時に `shops.deleted_at` を連動。本スライスでは Shop 未実装。また退会の監査アクション名が `AUDIT.md §4` に未定義 | U-04 | Shop 連動は**フック/TODO のみ用意**し `feature/catalog` 側で結線（Spring Events 経由）。監査アクションは `USER_WITHDRAWN` を `AUDIT.md` に追記提案。退会済みユーザーへの再アクセス時の挙動（401 vs 404）も確定する |
| ~~OQ-4~~ ✅ | **PATCH の「未送信」と `null` の区別** — 部分更新で「フィールド未送信＝不変」「`null` 送信＝クリア」をどう扱うか。record + Bean Validation だけでは区別不可 | U-01, U-03, U-07 | **決定: `displayName` は空文字不可（`@Size(min=1)`）。`avatarUrl` は「未送信＝不変 / 空文字 `""`＝クリア（null 化）」**。`JsonNullable` の導入は見送り（依存を増やさず空文字で意思表示できるため）。`@URL` は空文字を有効と扱うのでバリデーションは通過する。`API_DESIGN.md` / `VALIDATION_RULES.md §2.1` に反映済み |
| ~~OQ-5~~ ✅ | **住所バリデーション正規表現と一覧の並び順** — `VALIDATION_RULES` に住所節が未記載。`postalCode`/`phoneNumber` のパターン、一覧のソート（デフォルト優先 or `created_at`） | U-05, U-07, U-08 | `VALIDATION_RULES.md §5` に追記してから実装。暫定 `postalCode=^\d{3}-?\d{4}$` / `phoneNumber=^0\d{1,4}-?\d{1,4}-?\d{4}$`。並び順は「デフォルト住所を先頭 → `created_at` 昇順」を提案 |

### Risks（既知のリスク）

| # | リスク | 影響度 | 対策 |
|---|---|---|---|
| R-1 | `feature/auth` のマージ状況により `User`/`UserController`/Seed が手元の `main` に無いと前提が崩れる | 高 | ✅ 解決。U-00 で確認済み。着手前リスクとして役目終了 |
| R-2 | Address を `order` ドメインに置くと、`order` パッケージが本スライスで初登場するため横断構成（controller/service/...）の前例がない | 中 | ✅ 解決。`domain/order/` を controller / service / domain / repository / dto / exception の統一構成で作成（`CLAUDE.md` 準拠）。以降のスライスはこれを前例にできる |
| R-3 | `addresses` テーブルの所有権チェック漏れ（他人の住所を操作できる）IDOR | 高 | ✅ 解決。`AddressService#findOwned`（存在判定 → 所有判定）を update / delete 双方が経由。403/404 テスト計 6 件 +「他人の住所が改変されないこと」まで検証（§8・§9） |
| R-4 | `isDefault` 付け替えの非原子性（複数デフォルトが同時に存在） | 中 | ✅ **解決（2026-08-02）**。① 単一リクエスト経路: 当初の対策文「他住所を false → 対象を true」**という手順自体が S-1 のデフォルト消失を招いた**ため、`clearDefaultForUserExcept`（対象住所を除外）に修正・回帰テスト 3 件。② 並行リクエスト: `V4__create_order_tables.sql` に**部分 UNIQUE インデックス** `idx_addresses_user_default_unique ON addresses (user_id) WHERE is_default` を追記し、複数デフォルトを構造的に不可能にした（従来の `idx_addresses_user_default` は非 UNIQUE で防げなかった）。開発初期のため追加マイグレーションではなく create マイグレーションを直接編集（`auth.md` OQ-1 の前例に準拠・既存開発 DB は `docker compose down -v` で再作成が必要）。③ 競合時の応答: `GlobalExceptionHandler` に `DataIntegrityViolationException` ハンドラーを追加し、500 ではなく **409 `DUPLICATE_ENTRY`**（再試行で解消可能・DB のメッセージは非露出）を返す |
| R-5 | パスワード変更で `currentPassword` 照合を省略すると乗っ取り時に被害拡大 | 高 | ✅ 解決。`hasPassword()` 短絡 + `passwordEncoder.matches` 照合（`UserService.java:68-71`）。単体 3 件 + 実 BCrypt での統合 3 件 |
| R-6 | 退会の論理削除がカスケードせず、`addresses`（全項目が PII）が残る。`users` は 90 日後に匿名化されるが `id` は保持されるため、住所が残ると `user_id` から個人が再特定でき**匿名化が実質的に無効化**される | ~~低~~ → **中**（当初「低」は過小評価。APPI「利用目的終了後に速やかに削除」に抵触） | ✅ **解決（2026-08-02）**。当初の対策文「匿名化バッチで `addresses` を含む PII 処理を行う前提を記録」は、**その前提が仕様のどこにも存在しなかった**（`REQUIREMENTS §15.3` の保持ポリシー表・`RET-01`〜`08`・`SEQUENCE_FLOW §9.1` のいずれにも `addresses` の記載なし）ため、`feature/batch-jobs` の担当者に引き継がれる経路がなかった。仕様 4 か所へ明記して口約束を要件に格上げ済み: ① `REQUIREMENTS §15.3` に `addresses` 行（90 日 / 物理削除）② `RET-09` 新設（ユーザー匿名化と同一トランザクションで物理削除）③ `REQUIREMENTS §15.4` に DELETE 文の実装例 ④ `SEQUENCE_FLOW §9.1` の `UserAnonymizationJob` に削除ステップ ⑤ `DATA_DICTIONARY §10` に保持ポリシー追記。**物理削除が安全な根拠**: 配送先は `orders.delivery_*`（NOT NULL スナップショット）に保存され、`orders.address_id` は `ON DELETE SET NULL`（`V4__create_order_tables.sql:44`）のため注文履歴は壊れない。実装は `feature/batch-jobs`（本スライスのスコープ外は維持） |
| R-7 | `carts` の退会時の扱い（旧 R-6 に併記されていた） | 低 | R-6 から分離。`carts` は PII を含まず（商品 ID と数量のみ）、テーブル自体が Phase 3。`feature/cart`（タスク #6）の責務とする |
| R-8 | `PATCH /users/me/password` が API 全般のレート制限バケット（100 req/min/user）に入り、アクセストークン奪取後に `currentPassword` を毎分 100 回試行できる（§8 S-2 の昇格） | 中 | 未対応。`RateLimitingFilter.java:34` の `AUTH_PATH_PREFIX = "/api/v1/auth/"` にのみ認証系（10 req/min/IP）が適用される。`SECURITY.md` のレート制限方針に関わるためスライスを跨ぐ判断が必要。最小の対応はパスワード変更を認証系バケット扱いにすること |
| R-9 | OQ-3 で決めた Shop 連動削除の「フック/TODO」が実体として存在しない。`UserService.java:80` の Javadoc 一文のみで、Spring Events も `TODO` マーカーもないため `grep TODO` に掛からず、`feature/catalog` で取りこぼす経路が残る | 中 | 未対応。`feature/catalog` 着手時に `UserWithdrawnEvent` を発行し `@TransactionalEventListener` で受ける形に結線する（`CLAUDE.md`「クロスドメインは Spring Events 経由」）。本スライスでイベント基盤を初導入するのはスコープ肥大のため見送り |
| R-10 | `/profile/settings`・`/profile/addresses` の Playwright E2E がスイートに常設されていない（§9.2 最終項目）ため、後続スライスの変更でリグレッションを検知できない | 低 | 未対応。実ブラウザ確認は使い捨てスクリプトで実施済み（§10）。常設時はストレージステート再利用でログイン回数を抑える（§10 D-3） |
