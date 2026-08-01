# ユーザープロフィール・住所 実装計画 / 進捗管理
**ブランチ:** `feature/user-profile`  
**担当 Phase:** Phase 2  
**最終更新:** 2026-08-01  
**ステータス:** 🟢 U-00〜U-18 完了。バックエンド（プロフィール更新・パスワード変更・退会・住所 CRUD）とフロントエンド（`/profile/settings`・`/profile/addresses`）が実装済み。UI 設計は `design-system/pages/user-profile.md` を正とする。

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

- **匿名化バッチ**（`UserAnonymizationJob`・退会 90 日後）は `feature/batch-jobs`（タスク #15）の担当。本スライスは `deleted_at` を立てるところまで。
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

**追加マイグレーション不要**。`users`（`V2`）・`addresses`（`V4`）ともに作成済みで、本スライスのスキーマ要件を満たす。以下は実マイグレーションを確認済み:

- [x] `addresses` に `idx_addresses_user_id`・`idx_addresses_user_default` インデックスが存在する（`V4__create_order_tables.sql` L122-123）
- [x] `users.deleted_at` カラム + `idx_users_deleted_at`（部分インデックス `WHERE deleted_at IS NOT NULL`）が存在する（`V2__create_identity_tables.sql` L13, L59）
- [x] `addresses` に `deleted_at` が**ない**（物理削除方針・`BaseEntity` 継承で `SoftDeletableEntity` ではない）

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

### 認可・所有権

- [ ] 全エンドポイントが認証必須（`SecurityConfig.anyRequest().authenticated()` で担保）であることをコードで確認
- [ ] 操作対象ユーザーを**常にトークンの `sub`（`@AuthenticationPrincipal`）から解決**している（リクエストボディ/パスの user_id を信用しない）
- [ ] 住所の更新・削除で**所有権チェック**（`address.userId == principal.userId`）を行い、他人の住所には `ACCESS_DENIED`（403）を返している
- [ ] 存在しない住所には `RESOURCE_NOT_FOUND`（404）を返している（403/404 の区別が `API_DESIGN.md §3` と一致）

### パスワード

- [ ] パスワード変更は BCrypt **cost 12** で再ハッシュしている
- [ ] `currentPassword` 照合は `passwordEncoder.matches` で行い、不一致は `PASSWORD_CHANGE_FAILED`（400・存在情報を漏らさない）
- [ ] Google 専用ユーザー（`passwordHash == null`）のパスワード変更を拒否している
- [ ] `currentPassword` / `newPassword` をログ・レスポンスに出力していない（`GlobalExceptionHandler.SENSITIVE_FIELDS` に `password` が含まれることを確認）

### 退会・論理削除

- [ ] 退会は `deleted_at` 設定の論理削除であり物理削除をしていない（`SoftDeletableEntity#softDelete`）
- [ ] 退会後、`@SQLRestriction` により当該ユーザーが通常クエリから除外されることを確認
- [ ] 住所削除は仕様どおり物理削除（`addresses` に `deleted_at` を持たない）

### 入力バリデーション

- [ ] 文字数上限（`displayName` 100 / `newPassword` 72 / 住所各フィールド）を Bean Validation で弾いている
- [ ] `avatarUrl` を `@URL` で検証している（任意の文字列を保存しない）
- [ ] `postalCode` / `phoneNumber` の形式を正規表現で検証している（`VALIDATION_RULES §5`）

---

## 9. Test Checklist

### 9.1 Backend テスト（JUnit 5 + Mockito + Testcontainers）

> **既存テスト基盤を踏襲する（`io.kivio.support`）:**
> - **単体（service）**: Mockito（`UserServiceTest` が前例）。`AddressService` も同様にモックで。
> - **Controller スライス**: `ControllerTestBase`（`@WebMvcTest` + `@Import(SecurityConfig)` + `JwtProvider` モック・service は `@MockitoBean`）。`UserControllerTest` が前例で、**新規 `PATCH/DELETE` のレスポンス形式・ステータス・バリデーションはまずこのスライスで検証する**。
> - **フルスタック統合**: `IntegrationTestBase`（Testcontainers PostgreSQL）。`AuthControllerIntegrationTest` が前例。住所 CRUD の**所有権チェック（403/404）・`isDefault` 付け替え・退会後の `@SQLRestriction` 除外**など DB 挙動に依存するものはこちらで検証する。
> - **Repository**: `RepositoryTestBase`。`AddressRepository` のカスタムクエリ（デフォルト付け替え `@Modifying`・並び順）はこちらで検証可。

#### UserService 単体テスト（既存 `UserServiceTest` を拡張）

- [ ] `updateProfile`: 表示名・アバターを更新し `UserResponse` を返す
- [ ] `updateProfile`: 一部フィールドのみ送信 → 送信フィールドのみ更新される（未送信は不変）
- [ ] `changePassword`: 正しい `currentPassword` → `passwordHash` が更新される
- [ ] `changePassword`: 誤った `currentPassword` → `PASSWORD_CHANGE_FAILED`
- [ ] `changePassword`: Google 専用ユーザー（`passwordHash == null`） → `PASSWORD_CHANGE_FAILED`
- [ ] `withdraw`: `deleted_at` が設定される

#### AddressService 単体テスト（新規 `AddressServiceTest`）

- [ ] `list`: 自分の住所のみ返す
- [ ] `create`: 住所を保存し `AddressResponse` を返す
- [ ] `create(isDefault=true)`: 既存のデフォルト住所が `false` に落ちる
- [ ] `update`: 自分の住所を更新する
- [ ] `update`: 他人の住所 → `ACCESS_DENIED`（403）
- [ ] `update`/`delete`: 存在しない id → `RESOURCE_NOT_FOUND`（404）
- [ ] `delete`: 自分の住所を物理削除する

#### Controller テスト（スライス `ControllerTestBase` + 必要に応じ `IntegrationTestBase`）

- [ ] `PATCH /users/me` 200・レスポンス形式が `API_DESIGN.md` と一致
- [ ] `PATCH /users/me/password` 204 / 400（不一致）/ 422（バリデーション）
- [ ] `DELETE /users/me` 204 → 以後 `GET /users/me` が 401/404 相当（OQ-3）
- [ ] `GET /users/me/addresses` 200（配列形式）
- [ ] `POST /users/me/addresses` 201 + `Location` ヘッダー
- [ ] `PATCH /users/me/addresses/{id}` 200 / 403（他人）/ 404（不存在）
- [ ] `DELETE /users/me/addresses/{id}` 204 / 403 / 404
- [ ] 認証なしで各エンドポイント → 401

### 9.2 Frontend テスト（Vitest + RTL + MSW / Playwright）

- [ ] `ProfileSettingsForm`: 表示名更新 → `updateProfile` 呼び出し・ヘッダー反映
- [ ] `ProfileSettingsForm`: バリデーションエラー表示
- [ ] `ChangePasswordForm`: `PASSWORD_CHANGE_FAILED`（400）でエラーメッセージ表示
- [ ] `WithdrawDialog`: 退会確認 → `clearAuth` → `/` 遷移
- [ ] （住所 UI 実装時・OQ-2）`AddressFormSheet`: 追加/編集 → mutation 呼び出し・一覧 invalidate
- [ ] （住所 UI 実装時）デフォルト住所の表示・付け替え

---

## 10. Definition of Done

PR をマージするには以下を全て満たすこと。

### 機能要件

- [ ] `PATCH /users/me`（プロフィール部分更新）が動作する
- [ ] `PATCH /users/me/password`（現パスワード照合 + 再ハッシュ）が動作し、不一致で 400 を返す
- [ ] `DELETE /users/me`（論理削除）で `deleted_at` が設定され、以後アクセスできない
- [ ] 住所 CRUD 4 本が動作し、`isDefault` 付け替え・所有権チェック（403/404）が正しい
- [ ] `GET /users/me/addresses` が `API_DESIGN.md §3` の配列形式で返る

### セキュリティ要件

- [ ] §8 Security Checklist の全項目を確認済み
- [ ] 所有権チェック・BCrypt cost 12・機微情報の非出力をコードで確認

### テスト要件

- [ ] Backend: `./gradlew test` がグリーン（Testcontainers 含む）。JaCoCo カバレッジゲート（0.80）を維持
- [ ] Backend: `UserService` / `AddressService` の単体カバレッジ ≥ 80%
- [ ] Frontend: `pnpm test` / `pnpm lint` / `pnpm typecheck` / `pnpm build` がグリーン

### コード品質

- [ ] `BACKEND_CODING_STANDARDS.md` 準拠（DTO=record・Lombok パターン・レイヤー責務・`@Transactional(readOnly)` の適切な使用・例外は `KivioException` 階層）
- [ ] `FRONTEND_CODING_STANDARDS.md` 準拠（`'use client'` 葉限定・export 規約・TanStack Query mutation・Zustand セレクタ）
- [ ] **ドメイン間の直接 import がない**（`identity` ↔ `order` は `userId`（UUID）値参照のみ・エンティティ参照を張らない）
- [ ] Controller がビジネスロジックを持たない（Service 委譲のみ）
- [ ] Swagger UI で全エンドポイントが確認できる（`@Tag` / `@Operation` 付与）

### 動作確認

- [ ] `docker compose up` で全サービス起動
- [ ] ブラウザから `/profile/settings` でプロフィール更新・パスワード変更・退会が一通り操作できる
- [ ] （住所 UI 実装時）住所の追加・編集・削除・デフォルト切替が操作できる
- [ ] Flyway マイグレーション（必要なら住所 Seed）が正常適用される

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
| R-1 | `feature/auth` のマージ状況により `User`/`UserController`/Seed が手元の `main` に無いと前提が崩れる | 高 | U-00 で `feature/auth` のマージ状況・必要クラスの存在を確認してから着手 |
| R-2 | Address を `order` ドメインに置くと、`order` パッケージが本スライスで初登場するため横断構成（controller/service/...）の前例がない | 中 | `identity` パッケージの構成をテンプレートとして踏襲。`CLAUDE.md` の統一構成に厳密に従う |
| R-3 | `addresses` テーブルの所有権チェック漏れ（他人の住所を操作できる）IDOR | 高 | 全ての更新/削除を `findByIdAndUserId`（or 取得後 `userId` 比較）で実装し、§9 の 403/404 テストを必須化 |
| R-4 | `isDefault` 付け替えの非原子性（複数デフォルトが同時に存在） | 中 | `@Transactional` 内で「他住所を false → 対象を true」の順に実行。テストで複数デフォルトが残らないことを検証 |
| R-5 | パスワード変更で `currentPassword` 照合を省略すると乗っ取り時に被害拡大 | 高 | 必ず `matches` 照合・Google 専用ユーザーの拒否をテストで担保 |
| R-6 | 退会の論理削除がカスケードせず、孤立した `addresses`/`carts` が残る | 低 | 本スライスは住所まで。匿名化バッチ（`feature/batch-jobs`）で `addresses` を含む PII 処理を行う前提を引き継ぎ事項に記録 |
