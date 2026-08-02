# 出品者申請 実装計画 / 進捗管理
**ブランチ:** `feature/seller-application`  
**担当 Phase:** Phase 2（`SENIOR_SETUP_PLAN.md` バーティカルスライス #3）  
**最終更新:** 2026-08-02  
**ステータス:** 🟡 バックエンド実装・テスト（SA-00〜SA-12）完了（2026-08-02）。残: フロントエンド一式（SA-14〜SA-19）。**`V2` のチェックサムが変わったため、開発 DB の `docker compose down -v` が未実施**（オーナー対応待ち）。

> **⚠️ 着手前の必須手順（OQ-3 の決定に伴う）:** 本スライスは `V2__create_identity_tables.sql` を**直接編集**する。既存の開発 DB では Flyway が `Migration checksum mismatch` で起動に失敗するため、**実装開始前に `docker compose down -v` で全テーブルをドロップして作り直すこと**（実施はプロジェクトオーナーが行う）。Testcontainers は毎回新規 DB を立てるため CI・テストへの影響はない。

> **前提（`feature/auth` / `feature/user-profile` 由来の既存実装）:**  
> `User` / `UserRole` / `UserRepository`（`findByIdOrThrow`）・`KivioUserDetails`（`@AuthenticationPrincipal`）・`GlobalExceptionHandler`（RFC 9457）・`KivioException` 階層（`ConflictException` / `ResourceNotFoundException`）・`@Auditable` AOP・`BaseEntity` が利用可能。フロントは `(authenticated)` ルートグループ・`apiFetch`（BFF 経由・single-flight refresh）・`queryKeys.sellerApplication.me`・`ApiErrorCode` の 3 コードが**既に定義済み**で、グローバルナビ（`UserMenu` / `MobileMenuSheet` / `GlobalFooter`）から `/seller/applications/new` へのリンクも**既に張られている（現状 404）**。本スライスはその受け皿を作る。

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

`feature/seller-application` が担当するのは、**BUYER が出品者（SELLER）になるための申請を送信し、自分の申請状況を確認する**一式。審査そのものは含まない。

| フロー | エンドポイント | 状態 |
|---|---|---|
| セラー申請の送信 | `POST /api/v1/seller-applications` | ⬜ 本スライス |
| 自分の最新申請状況の確認 | `GET /api/v1/seller-applications/me` | ⬜ 本スライス |
| 申請フォーム / 審査状況画面 | `/seller/applications/new` | ⬜ 本スライス |

対応する要件は `REQUIREMENTS.md §3.3.1` の **SELLER-01 / SELLER-02 / SELLER-05**（申請送信・3 ステータス・却下後の再申請）。SELLER-03（承認時のロール昇格・ショップ自動生成）・SELLER-04（却下理由の保存と通知）は**管理者側の操作**であり本スライス外。

> **設計方針:**
> - **申請者は常にトークンの `sub`（`applicant_id`）から解決**する。リクエストボディは `reason` のみを受け取り、`status` / `reviewerId` / `reviewComment` / `reviewedAt` は**一切受け付けない**（mass assignment 防止）。新規レコードは常に `status = PENDING`。
> - **却下後の再申請は新レコードの作成**（`DB_DESIGN.md §3.4`「却下後の再申請は新レコード作成。過去申請履歴は保持」）。既存レコードの `status` を書き戻さない。
> - **`GET /me` は最新 1 件**（`created_at DESC` の先頭）を返す。申請履歴の一覧 API は仕様に存在しない。
> - **申請が 1 件も無い場合は 404 `RESOURCE_NOT_FOUND`**（`API_DESIGN.md §4`）。これは「未申請」という**正常状態**であり、フロントはエラーとして扱ってはならない（§6.4・R-3）。
> - **`seller_applications` は論理削除を持たない**（`deleted_at` カラムなし）。`BaseEntity` を継承する（`SoftDeletableEntity` ではない）。
> - **監査ログは `SELLER_APPLICATION_SUBMITTED`**（`SEQUENCE_FLOW §3` に記載あり・`AUDIT.md §4` には**未記載**のため本スライスで追記する。OQ-6）。

### スコープ外

- **管理者による審査**（`GET /admin/seller-applications`・`POST /admin/seller-applications/{id}/approve|reject`）は `feature/admin`（タスク #12）の担当。**承認時のロール昇格（`ROLE_SELLER`）とショップ自動生成（SELLER-03）も同様**。
- **審査結果の通知**（アプリ内通知・NOTIF-04）は `feature/notification`（タスク #10）、**メール通知**は `feature/mail-notification`（タスク #14）の担当。
- **セラーダッシュボード**（`/seller/dashboard`）は `feature/seller-dashboard`（タスク #11）の担当。未実装のため、本スライスの APPROVED / SELLER / ADMIN のリダイレクト先は暫定的に `/` とする（OQ-4）。
- **ショップ設定・商品 CRUD** は `feature/catalog`（タスク #4）の担当。

### スコープ外（シニア先行実装済みを前提とする）

- `SecurityConfig`（`anyRequest().authenticated()`）。ただし `POST /api/v1/seller-applications` の `hasRole("BUYER")`（`SecurityConfig.java:103`）は**本スライスで削除する**（OQ-1 の決定）。シニア所管ファイルへの変更のため、PR の説明にその旨を明記すること
- `JwtAuthenticationFilter` / `KivioUserDetails`（`@AuthenticationPrincipal` から `userId` を取得）
- `GlobalExceptionHandler`（RFC 9457 `ProblemDetail`・`KivioException` の `code` / `status` を自動描画）
- `@Auditable` + `AuditLogAspect`（`entityIdParam` はメソッド引数の `UUID` のみ抽出。戻り値からは取得しない — §5.3 注記）
- Flyway 基底マイグレーション（`V1`〜`V11`。`seller_applications` テーブルとインデックス 2 本は `V2__create_identity_tables.sql` で**作成済み**）
- フロント: BFF プロキシ（`src/app/api/v1/[...path]`）・`apiFetch`・`QueryProvider`・`useAuthStore`・`resolveApiError`

### 実装順序サマリー

```
Backend: SellerApplication Entity / Repository / DTO / 例外
  └─► SellerApplicationService（申請可否判定 + @Auditable）
        └─► SellerApplicationController（POST / GET me）
              └─► Backend テスト（単体 Mockito + 統合 Testcontainers）
Docs: AUDIT.md / VALIDATION_RULES.md / REQUIREMENTS §15.3 追記（並列可）
Frontend: 型 / Zod / API クライアント（404→null）
  └─► useSellerApplicationQuery / useCreateSellerApplicationMutation
        └─► /seller/applications/new（4 状態の出し分け）
              └─► Frontend テスト（Vitest + RTL + MSW）
```

---

## 2. Required References

実装前にタスクに関連するドキュメントのみを必ず通読すること。

| ドキュメント | 参照箇所 | 理由 |
|---|---|---|
| `docs/design/API_DESIGN.md` | §4 セラー申請（L642〜710） | 2 エンドポイントのリクエスト/レスポンス仕様・エラーコード |
| `docs/design/DB_DESIGN.md` | §3.4 `seller_applications`（業務ルール）, §インデックス（L732〜734） | テーブル定義・「PENDING 重複拒否はアプリ層で制御」「却下後は新レコード」 |
| `docs/design/DATA_DICTIONARY.md` | §4 セラー申請 | 各カラムの意味・Java 型・NULL 許容 |
| `docs/design/ERROR_CODES.md` | §2.3 セラー申請, §problem type 一覧（L212 付近） | `SELLER_APPLICATION_PENDING` / `..._ALREADY_APPROVED` の HTTP ステータス・problem type slug |
| `docs/design/SEQUENCE_FLOW.md` | §3 Seller Application（L316〜375） | 申請〜審査の全体像・`SELLER_APPLICATION_SUBMITTED` 監査記録。**ただし列名 `user_id`/`submitted_at` は誤り**（正: `applicant_id`/`created_at`。R-5） |
| `docs/requirements/REQUIREMENTS.md` | §3.3.1 SELLER-01〜05, §15.3 保持ポリシー | 業務要件・再申請可否・PII 保持ポリシー（`seller_applications` は**未記載**。R-7） |
| `docs/design/VALIDATION_RULES.md` | §0 責任分界, §1 UX ライティング原則, §2.1 共通フィールド | `reason` のメッセージ文言・Bean Validation ↔ zod 対応。**`reason` の節は未記載のため本スライスで追記**（SA-05） |
| `docs/architecture/AUDIT.md` | §4 記録対象イベント, §アクション名命名規則, §5 実装パターン | `SELLER_APPLICATION_SUBMITTED` の追記・`@Auditable` の付与方針 |
| `docs/architecture/SECURITY.md` | **§3.1（Filter Chain に置かない認可）**, §3 Filter Chain, §4 レート制限 | セラー申請のロール制限を Service 層に置く判断とその基準（OQ-1 の決定を反映済み） |
| `docs/architecture/OVERVIEW.md` | パッケージ構成・ドメイン間通信 | `SellerApplication` を `identity` に置く根拠（`CLAUDE.md` と一致） |
| `docs/development/BACKEND_CODING_STANDARDS.md` | 全体 | レイヤー責務・DTO=record・Lombok・例外設計・`@Transactional`・テスト方針 |
| `docs/development/FRONTEND_CODING_STANDARDS.md` | §1〜§11 | ディレクトリ構成・SC/CC 境界・TanStack Query・フォーム規約・export 規約 |
| `docs/development/FRONTEND_TEST_STRATEGY.md` | 全体 | Vitest / RTL / MSW モック方針 |
| `docs/design/frontend/FRONTEND_API_CONTRACT.md` | §6.3 セラー申請（L530〜538）, §クエリキー（L387・L472） | 4 状態の UI 出し分け・`queryKeys.sellerApplication.me` |
| `docs/design/frontend/FRONTEND_IA.md` | §1.3（L48）, §2 ナビゲーション（L101）, §3 ルート構成（L185〜188）, §5 ガード（L435〜437） | 画面 URL・BUYER 限定ガード・`(authenticated)` 配下への配置 |
| `docs/design/frontend/USER_FLOW.md` | §2.1 セラー申請フロー（L128〜153） | 未申請 / PENDING / APPROVED / REJECTED の分岐 |
| `design-system/MASTER.md` | 全体 | カラー・タイポグラフィ・コンポーネント仕様 |
| `design-system/pages/user-profile.md` | §1 設計原則, §7 入力方針 | **本スライスに専用の UI 設計書は存在しない**（SA-14 で作成する。OQ-8）。設定画面系の前例として参照 |
| `docs/implementation-plans/user-profile.md` | §8 S-1, §11 R-4 / R-6 | 直前スライスの教訓（一意性は DB 制約で担保・PII 保持ポリシーを仕様に明記） |
| `CLAUDE.md` | Conventions / Audit Log / Security | API 規約・監査規約・パッケージ制約 |

---

## 3. API Contract

### 3.1 共通仕様

- ベースパス: `/api/v1`
- **全エンドポイント認証必須**（`Authorization: Bearer <accessToken>`）
- 申請者は常に**トークンの `sub`（user_id）から解決**する（`@AuthenticationPrincipal KivioUserDetails`）。パス・ボディに user_id を取らない
- エラー形式: RFC 9457 `ProblemDetail`（`Content-Type: application/problem+json`）
- **`spring.jackson.default-property-inclusion: non_null`** のため、`null` フィールドは**レスポンス JSON から省略される**。`API_DESIGN.md §4` の例に `"reviewComment": null` とあるが実際には**キーごと消える**。フロントの型は `reviewComment?: string | null` として「未送信 = null」を許容すること（R-8）
- 各エンドポイントの完全な仕様は `docs/design/API_DESIGN.md §4` を参照すること

### 3.2 エンドポイント一覧

| エンドポイント | 権限 | リクエスト | レスポンス | 主なエラーコード |
|---|---|---|---|---|
| `POST /seller-applications` | `ROLE_BUYER`（**Service 層で判定**・OQ-1） | `reason`（必須・1000 文字以内） | 201 `SellerApplicationResponse` | `SELLER_APPLICATION_PENDING`(409), `SELLER_APPLICATION_ALREADY_APPROVED`(409), `VALIDATION_FAILED`(422), `UNAUTHORIZED`(401) |
| `GET /seller-applications/me` | 全ロール | — | 200 `SellerApplicationResponse` | `RESOURCE_NOT_FOUND`(404 — 申請が 1 件も無い), `UNAUTHORIZED`(401) |

**補足事項:**

- **`POST` の申請可否判定は 3 段（順序が重要）:**
  1. 申請者の `role` が `ROLE_BUYER` でない → `SELLER_APPLICATION_ALREADY_APPROVED`（409）
  2. 同一 `applicant_id` に `status = PENDING` の申請が存在する → `SELLER_APPLICATION_PENDING`（409）
  3. 同一 `applicant_id` に `status = APPROVED` の申請が存在する → `SELLER_APPLICATION_ALREADY_APPROVED`（409）
  - ①と③は本来同値（承認時にロールが昇格するため）だが、**承認処理が別スライス**である以上「APPROVED 申請はあるがロールが未昇格」という不整合状態が開発中に起こりうる。両方チェックして二重に塞ぐ。
  - `REJECTED` の既存申請は**何件あっても新規申請を妨げない**（SELLER-05）。
- **`POST` は `Location` ヘッダーを付けない。** 単一リソースを指す `GET /seller-applications/{id}` が仕様に存在せず、指す先が無いため（`AddressController` が `Location` を付けているのは `PATCH`/`DELETE /users/me/addresses/{id}` が存在するため）。
- **`GET /me` は「最新 1 件」** = `findFirstByApplicantIdOrderByCreatedAtDesc`。却下 → 再申請済みのユーザーには**新しい PENDING が返る**（却下履歴は返らない）。
- **本スライスは 403 を返さない（OQ-1 の決定）。** `SecurityConfig` からロールガードを外し、ロール違反は Service が 409 `SELLER_APPLICATION_ALREADY_APPROVED` として返す。他人のリソースを指すエンドポイントも無い（`/me` 固定）ため IDOR の余地自体が無く、403 の出番が存在しない。**「未認証 = 401 / ロール違反 = 409 / 存在しない = 404」の 3 種で閉じる。**

### 3.3 DTO スキーマ

| DTO | フィールド |
|---|---|
| `CreateSellerApplicationRequest`（record） | `reason`（`@NotBlank @Size(max = 1000)`） |
| `SellerApplicationResponse`（record） | `id`(UUID), `applicantId`(UUID), `reason`(String), `status`(`SellerApplicationStatus`), `reviewComment`(String・null 可), `reviewedAt`(Instant・null 可), `createdAt`(Instant) |

> **単一 DTO 案を採用する（OQ-2）。** `API_DESIGN.md` の `POST` レスポンス例には `reviewComment` / `reviewedAt` が無く、`GET /me` の例には `applicantId` が無いが、**`non_null` 設定により未審査時は `reviewComment` / `reviewedAt` が自動的に省略される**ため、単一の superset DTO で両方の例を満たせる。`applicantId` が `GET /me` にも含まれる点だけが仕様例との差分だが、自分自身の ID であり情報漏洩にはあたらない。DTO を 2 本に割る積極的な理由が無い。

> **`reason` のバリデーションは `VALIDATION_RULES.md §6` が正**（SA-05 で追記済み）。要点のみ再掲:
>
> | フィールド | 制約 | Bean Validation | zod | エラーメッセージ |
> |---|---|---|---|---|
> | `reason` | 必須・1〜1000 文字（空白のみ不可） | `@NotBlank @Size(max=1000)` | `z.string().trim().min(1).max(1000)` | 未入力: 「申請理由を入力してください」／ 超過: 「申請理由は1000文字以内で入力してください」 |
>
> 下限は `@NotBlank` で担保する（`@Size(min=1)` は使わない）。`user-profile.md §8 S-4`（`@Size(min=1)` は空白のみを通す）の指摘を踏まえた決定。

---

## 4. DB Migration / Seed Plan

### 4.1 Migration

**テーブル自体は追加マイグレーション不要。** `seller_applications` は `V2__create_identity_tables.sql`（L40〜57）で作成済みで、インデックス 2 本（`idx_seller_applications_applicant_id` / `idx_seller_applications_status`）も存在する。着手時に以下を実マイグレーションで確認すること:

- [ ] `seller_applications` の全カラムが `DB_DESIGN.md §3.4` と一致する（`applicant_id` / `reason` / `status` / `reviewer_id` / `review_comment` / `reviewed_at` / `created_at` / `updated_at`）
- [ ] `seller_applications_status_check`（`PENDING` / `APPROVED` / `REJECTED`）が存在する
- [ ] `deleted_at` カラムが**無い**（論理削除を持たない → `BaseEntity` 継承）
- [ ] `idx_seller_applications_applicant_id` / `idx_seller_applications_status` が存在する

**追加が必要なもの（OQ-3・決定済み）:** 同一ユーザーの `PENDING` 申請を **DB 制約で 1 件に制限**する部分 UNIQUE インデックス。**新規マイグレーションは起こさず、`V2__create_identity_tables.sql` の末尾のインデックス定義群に直接追記する。**

```sql
-- V2__create_identity_tables.sql のインデックス節に追記
CREATE INDEX idx_seller_applications_applicant_id ON seller_applications (applicant_id);
CREATE INDEX idx_seller_applications_status      ON seller_applications (status);
-- ↓ 本スライスで追加
CREATE UNIQUE INDEX idx_seller_applications_pending_unique
  ON seller_applications (applicant_id)
  WHERE status = 'PENDING';

COMMENT ON INDEX idx_seller_applications_pending_unique
  IS '同一ユーザーの PENDING 申請は 1 件まで。二重送信・並行リクエストによる重複申請を構造的に防ぐ。';
```

> **⚠️ 適用手順（実装前に必須）:** `V2` のチェックサムが変わるため、既存の開発 DB では Flyway が `Migration checksum mismatch` で起動に失敗する。**実装開始前に `docker compose down -v` でボリュームごと破棄し、全テーブルを作り直すこと**（プロジェクトオーナーが実施）。Testcontainers は毎回新規 DB を立てるため CI・テストへの影響はない。開発シード（`dev/V10` / `dev/V13`）は制約に適合するため変更不要。
>
> **判断の根拠（OQ-3）:** 当初は「`V2` は `main` マージ済みなので増分の `V12` を起こす」を推したが、**開発段階でありスキーマ定義を綺麗に保ちたい**というプロジェクトオーナーの判断により直接編集とした。移行コストは事前ドロップで解消される。`user-profile` の `V4` 直接編集と同じ流儀に揃う。
>
> **競合時の応答:** `DataIntegrityViolationException` → 409 `DUPLICATE_ENTRY` へのマッピングは `GlobalExceptionHandler` に**既に存在する**（`user-profile.md` R-4 で追加済み）。ただしユーザー向けには `SELLER_APPLICATION_PENDING` が返るほうが親切なため、**アプリ層の事前チェックを主・DB 制約を最後の砦**とする（正常系では制約に到達しない）。

### 4.2 Seed Data

| 内容 | 要否 | 理由 |
|---|---|---|
| PENDING 申請を持つ BUYER | 必要 | 「審査中」UI と `SELLER_APPLICATION_PENDING`（409）を即座に確認するため |
| REJECTED 申請を持つ BUYER | 必要 | 「却下理由 + 再申請フォーム」UI を確認するため |
| APPROVED 申請 | 任意 | 既存 Seed の `seller1`（`ROLE_SELLER`）に紐づく APPROVED 申請を入れておくと、承認済みユーザーの画面挙動（`/seller/dashboard` へのリダイレクト）を確認できる |

`dev/V14__seed_seller_applications.sql` を追加する（dev プロファイル専用・`application-dev.yaml` の `spring.flyway.locations` に `classpath:db/migration/dev` が含まれる。本番では読み込まれない）。

- 既存 Seed ユーザー（`V10__seed_development_data.sql`）を参照する:
  - `00000000-0000-0000-0000-000000000002` = `seller1`（`ROLE_SELLER`）→ APPROVED 申請
  - `00000000-0000-0000-0000-000000000003` = `buyer1`（`ROLE_BUYER`）→ **どの状態を割り当てるかは要検討**。`buyer1` は `/profile/*` の動作確認にも使われる主力ユーザーであり、ここに PENDING を入れると「未申請フォーム」が一切確認できなくなる
- **推奨: 検証用の BUYER を 2 名追加する**（`buyer2` = PENDING / `buyer3` = REJECTED）。`buyer1` は**未申請のまま残す**ことで、4 状態すべてを Seed だけで再現できる。パスワードハッシュは `V10` の `buyer1` のものを流用してよい（開発用）
- ID 採番規約は既存 Seed に合わせる（住所が `00000000-0000-0000-0004-...` を使用しているため、セラー申請は `00000000-0000-0000-0005-...` を割り当てる）

---

## 5. Backend Implementation Plan

### 5.1 アーキテクチャ判断: `SellerApplication` は `identity` ドメイン

`CLAUDE.md` のパッケージ構成（`domain/identity/  # User, RefreshToken, SellerApplication`）・`DB_DESIGN.md §2`（`seller_applications` = identity ドメイン）・`DATA_DICTIONARY.md §1`（同）がすべて一致しているため、**判断の余地なく `io.kivio.domain.identity` に置く**（`user-profile` の OQ-1 のような論点は発生しない）。

- `SellerApplication` と `User` は**同一ドメイン内**のため、`SellerApplicationService` から `UserRepository` を直接参照してよい（`CLAUDE.md` の「domain パッケージ間の直接 import 禁止」に抵触しない）。ロール判定のために `User` を読む。
- ただし **JPA の `@ManyToOne` は張らない**。`applicantId` / `reviewerId` は `UUID` 値として保持する。理由: ① 集約境界を跨いだエンティティ参照を作らない（`CLAUDE.md`「集約内のエンティティは集約ルート経由でのみ変更する」）② `User` は `@SQLRestriction("deleted_at IS NULL")` を持つため、`@ManyToOne` にすると退会ユーザーの申請取得時に予期しない挙動を招く ③ `Address`（`user-profile`）で確立した前例と揃う。
- 承認時のロール昇格・ショップ生成（SELLER-03）は `feature/admin` の責務。本スライスでは**イベント発行もフックも置かない**（`user-profile.md` R-9 で「実体の無いフック」が却って取りこぼしを生んだ反省。必要になったスライスで `SellerApplicationApprovedEvent` を導入する）。

### 5.2 実装ファイル一覧

```
io.kivio/
└── domain/identity/
    ├── controller/
    │   └── SellerApplicationController.java              # ★ /api/v1/seller-applications（POST / GET me）
    ├── service/
    │   └── SellerApplicationService.java                 # ★ @Service @Transactional
    ├── domain/
    │   ├── SellerApplication.java                        # ★ 集約ルート Entity（BaseEntity 継承）
    │   └── SellerApplicationStatus.java                  # ★ enum（PENDING / APPROVED / REJECTED）
    ├── repository/
    │   └── SellerApplicationRepository.java              # ★ existsBy... / findFirstBy...
    ├── exception/
    │   ├── SellerApplicationPendingException.java        # ★ ConflictException 継承（409）
    │   └── SellerApplicationAlreadyApprovedException.java # ★ ConflictException 継承（409）
    └── dto/
        ├── request/
        │   └── CreateSellerApplicationRequest.java       # ★ reason のみ
        └── response/
            └── SellerApplicationResponse.java            # ★ from(SellerApplication)

src/main/resources/db/migration/
├── V2__create_identity_tables.sql                        # 🔄 部分 UNIQUE インデックスを直接追記（OQ-3）
└── dev/
    └── V14__seed_seller_applications.sql                 # ★ 開発用シード

io.kivio.config/
└── SecurityConfig.java                                   # 🔄 seller-applications の hasRole("BUYER") を削除（OQ-1）
```

> `ConflictException` のコンストラクタは `protected` のため、409 系の具象例外は**サブクラスとして作る**（`ResourceAccessDeniedException`（`ForbiddenException` 継承）と同じ流儀）。`GlobalExceptionHandler` が `KivioException` の `code` / `status` を自動描画するため、ハンドラー側の追加実装は不要。

### 5.3 実装ステップ（依存順）

#### ステップ 0: `SecurityConfig` のロールガード削除 + `V2` へのインデックス追記

OQ-1 / OQ-3 の決定に伴う先行変更。**どちらもシニア所管ファイルへの変更なので最初にまとめて行い、単独のコミットに分ける**（レビュー時に意図が読み取れるように）。

- `SecurityConfig.java:103` の `.requestMatchers(HttpMethod.POST, "/api/v1/seller-applications").hasRole("BUYER")` を**削除**する。以降 `anyRequest().authenticated()` が適用され、ロール判定は `SellerApplicationService` の責務になる（§3.2）
  - 直下の `.requestMatchers(HttpMethod.POST, "/api/v1/products").hasRole("SELLER")` は**触らない**（`feature/catalog` の所管）
- `V2__create_identity_tables.sql` のインデックス節に部分 UNIQUE インデックスを追記する（§4.1 の SQL）
- **この時点で `docker compose down -v` 済みであることを確認する**（未実施だと Flyway が `checksum mismatch` で起動しない）

#### ステップ 1: Entity / enum / Repository

- `SellerApplicationStatus`: `PENDING` / `APPROVED` / `REJECTED`。`@Enumerated(EnumType.STRING)` で永続化（`User.role` と同じ流儀）。
- `SellerApplication`（`@Entity @Table(name = "seller_applications")`・`BaseEntity` 継承）:
  - フィールド: `id`(UUID・`@GeneratedValue(strategy = GenerationType.UUID)`), `applicantId`(UUID・not null), `reason`(String・`columnDefinition = "TEXT"`・not null), `status`(enum・not null・`@Builder.Default = PENDING`), `reviewerId`(UUID・null 可), `reviewComment`(String・TEXT・null 可), `reviewedAt`(Instant・null 可)
  - Lombok: `@Getter @Builder @NoArgsConstructor(access = PROTECTED) @AllArgsConstructor(access = PRIVATE) @EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)`（`User` / `Address` と同一パターン）
  - ドメインメソッド: `isPending()` / `isApproved()`（`status` の比較を呼び出し側に散らさない）。**`approve()` / `reject()` は本スライスでは作らない**（`feature/admin` の責務。未使用の公開 API を先出しすると JaCoCo のカバレッジゲートも割る）
- `SellerApplicationRepository extends JpaRepository<SellerApplication, UUID>`:
  - `boolean existsByApplicantIdAndStatus(UUID applicantId, SellerApplicationStatus status)`
  - `Optional<SellerApplication> findFirstByApplicantIdOrderByCreatedAtDesc(UUID applicantId)`
  - `idx_seller_applications_applicant_id` がどちらのクエリにも効く

#### ステップ 2: DTO と例外

- `CreateSellerApplicationRequest`（record）: `reason`（`@NotBlank(message = "...")` + `@Size(max = 1000, message = "...")`）。メッセージは `VALIDATION_RULES.md §6`（追記済み）と一字一句そろえる。
- `SellerApplicationResponse`（record + `@Builder` + `static from(SellerApplication)`）: §3.3 のフィールド。`UserResponse` と同じ流儀。
- `SellerApplicationPendingException extends ConflictException`: コード `SELLER_APPLICATION_PENDING`・メッセージ「審査中の申請があります。結果をお待ちください」
- `SellerApplicationAlreadyApprovedException extends ConflictException`: コード `SELLER_APPLICATION_ALREADY_APPROVED`・メッセージ「すでに出品者として承認されています」
- どちらも**他ユーザーの情報を含まない**汎用文言にする（§8）

#### ステップ 3: `SellerApplicationService`

```java
@Transactional
@Auditable(action = "SELLER_APPLICATION_SUBMITTED", entityType = "SELLER_APPLICATION")
public SellerApplicationResponse apply(UUID applicantId, CreateSellerApplicationRequest request) {
    User applicant = userRepository.findByIdOrThrow(applicantId);
    // OQ-1 の決定により SecurityConfig のロールガードを外したため、
    // このロール判定が POST /seller-applications の唯一の認可ポイントになる。消さないこと
    if (applicant.getRole() != UserRole.ROLE_BUYER) {
        throw new SellerApplicationAlreadyApprovedException();
    }
    if (repository.existsByApplicantIdAndStatus(applicantId, PENDING)) {
        throw new SellerApplicationPendingException();
    }
    if (repository.existsByApplicantIdAndStatus(applicantId, APPROVED)) {
        throw new SellerApplicationAlreadyApprovedException();
    }
    // status は Entity の @Builder.Default（PENDING）に委ね、リクエストからは受け取らない
    return SellerApplicationResponse.from(repository.save(
            SellerApplication.builder().applicantId(applicantId).reason(request.reason()).build()));
}

@Transactional(readOnly = true)
public SellerApplicationResponse getMyLatest(UUID applicantId) {
    return repository.findFirstByApplicantIdOrderByCreatedAtDesc(applicantId)
            .map(SellerApplicationResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("SellerApplication", applicantId));
}
```

> **`@Auditable` の `entityIdParam` について:** `AuditLogAspect#extractEntityId` は**メソッド引数の中から名前一致する `UUID` を探す**実装で、戻り値からは取得しない（`AuditLogAspect.java:79-88`）。したがって `entityIdParam = "applicantId"` と書くと**申請 ID ではなく申請者の user_id** が `entity_id` に入り、`entityType = "SELLER_APPLICATION"` と食い違う。**`entityIdParam` は指定しない**（`AuthService` の `USER_REGISTERED` と同じ扱い＝`entity_id` は null）。申請 ID を残したい場合は Aspect 側の拡張が必要で、それは本スライスの範囲外（R-9）。

> **`ResourceNotFoundException("SellerApplication", applicantId)` の第 2 引数に注意:** このコンストラクタはメッセージに ID を埋め込む（「ID '...' の SellerApplication は存在しないか削除されています」）。ここで渡るのは**申請 ID ではなく申請者自身の user_id** で、自分の ID なので漏洩にはならないが、文言としては不自然。`new ResourceNotFoundException("セラー申請が見つかりません")`（単一引数版）を使うほうが適切。

#### ステップ 4: `SellerApplicationController`

- `@RestController @RequestMapping("/api/v1/seller-applications") @Tag(name = "SellerApplication", description = "セラー申請")`
- `POST ""` → `@Valid @RequestBody CreateSellerApplicationRequest` → `201 Created` + body（`ResponseEntity.status(HttpStatus.CREATED).body(...)`・`Location` なし）
- `GET "/me"` → `200 SellerApplicationResponse`
- 両方とも `@AuthenticationPrincipal KivioUserDetails principal` から `principal.getUserId()` のみを Service へ渡す。**Controller は委譲のみ**（分岐・計算を持たない）
- `@Operation(summary = ...)` を各ハンドラーに付与（Swagger UI 表示）

#### ステップ 5: ドキュメント同期 — ✅ 実施済み（2026-08-02）

実装より先に仕様側の穴を埋めた。**実装時はこれらを正として参照すること**（暫定案ではない）。

| ドキュメント | 変更内容 |
|---|---|
| `AUDIT.md §4` | `SELLER_APPLICATION_SUBMITTED` をカテゴリ「セラー申請」として追加。あわせて**新規作成イベントの `entity_id` が `null` になる**理由と対処方針を表下に注記（R-9） |
| `VALIDATION_RULES.md` | `§6 セラー申請` を新設（`reason` の制約・Bean Validation ↔ zod 対応・文言・確定事項 3 件）。旧 `§6 今後の拡張` を `§7` に繰り下げ、そこにあった実スキーマと不一致の記述（`shopName` / `category` / `description`）を削除。`§4 実装同期状況` に `CreateSellerApplicationRequest` 行を追加。ヘッダーのバージョンを 1.2 に更新し改訂履歴を追記 |
| `REQUIREMENTS.md` | `§15.3` 保持ポリシー表に `seller_applications` 行（90日 / 匿名化）。`§15.4` に匿名化 SQL と「レコードを残す理由」を追記。`§15.7` に **`RET-10`** を新設 |
| `SEQUENCE_FLOW.md §9.1` | `UserAnonymizationJob` に `seller_applications` の匿名化ステップを追加（RET-10） |
| `DATA_DICTIONARY.md §4` | テーブル概要に保持ポリシーを追記。`reason` / `review_comment` の備考に匿名化の旨と文字数制約を追記 |
| `SEQUENCE_FLOW.md §3` | 列名（`user_id`→`applicant_id` / `submitted_at`→`created_at` / `reviewed_by`→`reviewer_id` / `rejection_reason`→`review_comment`）・リクエストボディ（`{shopName, description, ...}`→`{reason}`）・審査 API の HTTP メソッド（`PATCH`→`POST`）・エラーコード名（`ALREADY_APPROVED`→`SELLER_APPLICATION_ALREADY_APPROVED` 等）を実スキーマ / `API_DESIGN` に合わせて訂正。申請可否判定の分岐も 3 段の実装方針に合わせた。図の先頭に「何を正とするか」の注記を追加（R-5） |
| `API_DESIGN.md §4` | `POST` に「リクエストは `reason` のみ・サーバー決定フィールドは受け付けない」注記。`GET /me` のレスポンス例に `applicantId` を追加し、**`null` フィールドがレスポンスから省略される**（`non_null`）旨と「常に最新 1 件」を注記。404 の説明に「＝未申請・クライアントは正常状態として扱う」を明記（R-3 / R-8） |

---

## 6. Frontend Implementation Plan

### 6.1 前提条件（`feature/auth` / `feature/user-profile` 実装済み）

- [ ] `useAuthStore` が `user`（`AuthUser`・`role` を含む）/ `isAuthenticated` を提供する
- [ ] `useAuthHydrated()` で Zustand persist の復元完了を待てる（`src/hooks/useAuthHydrated.ts`・`useSyncExternalStore` ベースで SSR 時は `false`）
- [ ] `ROUTES`（`src/lib/constants/index.ts`）に `ROUTES.seller.dashboard` が定義済み。**本スライスで 2 つ追加する**: `applicationNew: '/seller/applications/new'` と `applicationRedirect: '/'`（OQ-4・#11 完了時に `'/seller/dashboard'` へ差し替える 1 箇所）
- [ ] `apiFetch`（`src/lib/api/client/base.ts`）が BFF 経由で 401 の single-flight refresh を処理する
- [ ] `queryKeys.sellerApplication.me`（`['seller-application', 'me']`）が**定義済み**（`src/lib/queryKeys.ts:58`）
- [ ] `ApiErrorCode` に `SELLER_APPLICATION_PENDING` / `..._ALREADY_APPROVED` / `..._NOT_REVIEWABLE` が**定義済み**（`src/types/api/error-codes.ts`）
- [ ] `src/proxy.ts` の `PROTECTED_PATHS` に `/seller` が**含まれている**（未認証は `/auth/login?from=...` へ）
- [ ] `(authenticated)/layout.tsx`（グローバル chrome + skip link）が存在する
- [ ] グローバルナビから `/seller/applications/new` へのリンクが**既に存在する**（`UserMenu.tsx:94`（`isBuyer` 条件付き）/ `MobileMenuSheet.tsx:117` / `GlobalFooter/index.tsx:5`）→ 本スライスで画面を作れば 404 が解消する

### 6.2 画面仕様: 1 URL・4 状態

`/seller/applications/new` は URL こそ「新規作成」だが、**`GET /seller-applications/me` の結果に応じて 4 つの顔を持つ**（`FRONTEND_API_CONTRACT.md §6.3` / `USER_FLOW.md §2.1`）。

| 申請状況 | 画面 | 主な要素 |
|---|---|---|
| 未申請（404） | **申請フォーム** | 制度説明・`reason` テキストエリア（1000 文字・カウンター）・送信ボタン |
| `PENDING` | **審査中** | ステータスバッジ・申請日時・申請理由の読み返し・**再申請ボタンは出さない** |
| `REJECTED` | **却下 + 再申請** | 却下理由（`reviewComment`）・審査日時・前回の申請理由・**再申請フォーム**（新規申請として送信） |
| `APPROVED` | **リダイレクト** | `ROUTES.seller.applicationRedirect`（暫定 `/`。`/seller/dashboard` は未実装のため。OQ-4） |

**ロールによる事前分岐（`FRONTEND_IA.md §5 ③ BUYER_ONLY_PATHS`）:**
- `ROLE_SELLER` / `ROLE_ADMIN` → 画面を見せず `ROUTES.seller.applicationRedirect` へリダイレクト（OQ-4）
- `ROLE_BUYER` → 上表の 4 状態へ

> **リダイレクト先の定数化（OQ-4）:** `ROUTES.seller` に `applicationRedirect: '/'` を追加し、**SELLER / ADMIN / APPROVED の 3 経路すべてがこの 1 つの定数を参照する**。`feature/seller-dashboard`（#11）が入ったら値を `'/seller/dashboard'` に変えるだけで済む。分岐ごとに文字列リテラルを直書きしないこと。

> **ガードの置き場所:** `src/proxy.ts` はミドルウェアであり **Cookie しか読めない**（ロールは `access_token` の中にあるが、ミドルウェアで JWT を検証する仕組みは現状無く、Zustand も参照できない）。したがって**ロール分岐はクライアント側で行う**。`proxy.ts` は従来どおり「未認証 → ログイン」のソフトガードのみを担う。
>
> **サーバー側の実効的な防御は `SellerApplicationService` のロール判定ただ 1 つになる**（OQ-1 で `SecurityConfig` のロールガードを外したため）。フロントの分岐は UX 上の振り分けにすぎず、認可の根拠にしてはならない。

### 6.3 実装ファイル一覧

```
src/
├── app/
│   └── (authenticated)/
│       └── seller/
│           └── applications/
│               └── new/
│                   └── page.tsx                     # ★ SC：metadata + 見出し + <SellerApplicationView />
├── components/
│   └── seller/                                      # 既存だが .gitkeep のみ
│       ├── SellerApplicationView.tsx                # ★ CC：ロール分岐 + 4 状態の出し分け（この 1 枚だけが状態を持つ）
│       ├── SellerApplicationForm.tsx                # ★ CC：reason 入力 + 送信（未申請 / 却下後で共用）
│       ├── SellerApplicationStatusCard.tsx          # ★ CC：PENDING / REJECTED の状況表示
│       ├── SellerApplicationIntro.tsx               # ★ 制度説明（出品者になると何ができるか）
│       └── SellerApplicationSkeleton.tsx            # ★ ローディング
├── hooks/
│   ├── queries/
│   │   └── useSellerApplicationQuery.ts             # ★ queryKeys.sellerApplication.me
│   └── mutations/
│       └── useCreateSellerApplicationMutation.ts    # ★ 成功で me を invalidate
├── lib/
│   ├── api/client/
│   │   └── sellerApplications.ts                    # ★ get（404→null）/ create
│   └── validations/
│       └── sellerApplication.ts                     # ★ sellerApplicationSchema
└── types/
    └── api/
        ├── seller-application.ts                    # ★ SellerApplication 型
        └── index.ts                                 # 🔄 re-export を追加
```

### 6.4 実装ステップ（依存順）

1. **型定義**（`types/api/seller-application.ts`）:
   ```ts
   export const SellerApplicationStatus = {
     PENDING: 'PENDING', APPROVED: 'APPROVED', REJECTED: 'REJECTED',
   } as const
   export type SellerApplicationStatus = (typeof SellerApplicationStatus)[keyof typeof SellerApplicationStatus]

   export interface SellerApplication {
     id: string
     applicantId: string
     reason: string
     status: SellerApplicationStatus
     /** 審査コメント。未審査ならレスポンスから省略される（§3.1） */
     reviewComment?: string | null
     /** ISO 8601 UTC。未審査ならレスポンスから省略される */
     reviewedAt?: string | null
     createdAt: string
   }
   ```
   enum の置き場所は `types/enums.ts`（バックエンド enum の集約先）に寄せるか型ファイル同居かを既存慣習に合わせること（`UserRole` は `enums.ts` にある → **`enums.ts` に置くのが一貫**）。

2. **Zod スキーマ**（`lib/validations/sellerApplication.ts`）: `reason` を `.trim().min(1).max(1000)`。メッセージは `VALIDATION_RULES.md`（SA-05）とバックエンドの Bean Validation に一字一句そろえる。

3. **API クライアント**（`lib/api/client/sellerApplications.ts`）:
   ```ts
   /** 未申請（404）は「エラー」ではなく正常状態のため null を返す */
   export async function getMySellerApplication(): Promise<SellerApplication | null> {
     try {
       return await apiFetch<SellerApplication>('/seller-applications/me')
     } catch (error) {
       if (error instanceof ApiError && error.status === 404) return null
       throw error
     }
   }
   export function createSellerApplication(body: { reason: string }): Promise<SellerApplication> {
     return apiFetch('/seller-applications', { method: 'POST', body: JSON.stringify(body) })
   }
   ```
   **404 をここで吸収するのが要点**（R-3）。`apiFetch` は非 2xx をすべて `ApiError` として throw するため、これを怠ると TanStack Query が未申請ユーザー全員をエラー扱いし、リトライまで走る。

4. **クエリ / ミューテーション**:
   - `useSellerApplicationQuery()`: `queryKey: queryKeys.sellerApplication.me`・`queryFn: getMySellerApplication`。`data === null` が「未申請」。**`retry` の既定値に注意**（404 は既に null 化されているので通常のリトライ方針でよい）
   - `useCreateSellerApplicationMutation()`: 成功時に `queryClient.invalidateQueries({ queryKey: queryKeys.sellerApplication.me })`（`FRONTEND_API_CONTRACT.md §クエリキー` L472 の対応表どおり）

5. **画面**（`/seller/applications/new`）:
   - `page.tsx` は **Server Component**（`'use client'` を書かない）。`metadata`（title: `セラー申請 | Kivio`）と見出しだけを持ち、中身は `<SellerApplicationView />` に委譲する
   - `SellerApplicationView`（CC）の分岐順序:
     1. `useAuthHydrated()` が false → スケルトン（**ストア復元前にロール判定してはならない**。誤リダイレクトの原因）
     2. `user.role !== 'ROLE_BUYER'` → `router.replace(ROUTES.seller.applicationRedirect)`
     3. クエリ `isPending` → スケルトン
     4. クエリ `isError` → エラー表示 + 再試行
     5. `data === null` → `<SellerApplicationIntro />` + `<SellerApplicationForm />`
     6. `data.status === 'PENDING'` → `<SellerApplicationStatusCard />`（フォーム無し）
     7. `data.status === 'REJECTED'` → `<SellerApplicationStatusCard />` + `<SellerApplicationForm />`（再申請）
     8. `data.status === 'APPROVED'` → `router.replace(ROUTES.seller.applicationRedirect)`
   - `SellerApplicationForm`: `react-hook-form` + `zodResolver` + 文字数カウンター。送信中は二重送信を防ぐ（`isPending` でボタン非活性）。エラー文言は `resolveApiError` の `overrides` で:
     | コード | 文言（暫定） |
     |---|---|
     | `SELLER_APPLICATION_PENDING` | 「審査中の申請があります。結果が出るまでお待ちください。」＋ 一覧を invalidate して画面を PENDING 表示へ切り替える |
     | `SELLER_APPLICATION_ALREADY_APPROVED` | 「すでに出品者として承認されています。」＋ invalidate |
     | `VALIDATION_FAILED` | 「入力内容を確認してください。」 |
   - **409 は「他タブ・別デバイスで状態が進んだ」サイン**なので、文言表示に加えて必ず invalidate し、画面を実際の状態へ追随させる（`AddressFormSheet` が 404 でシートを閉じるのと同じ思想）

6. **ナビゲーションの整合確認**: `UserMenu.tsx:93` の表示条件は現在 `isBuyer` のみ。`FRONTEND_IA.md §2`（L101-102）は「申請未済かつ PENDING 申請なしの場合のみ表示」と規定しているが、**ヘッダーで `GET /seller-applications/me` を常時フェッチするのは割に合わない**（全ページで 1 リクエスト増える）。**`isBuyer` のみの現行条件を維持し、PENDING 中にリンクを踏んだら審査中 UI が出る**挙動で許容する（IA 側に注記を追加）。判断は OQ-7。

7. **UI 設計書**: `design-system/pages/` に本画面の設計書が無い（`auth.md` / `layout.md` / `user-profile.md` のみ）。`user-profile.md` の前例に倣い `design-system/pages/seller-application.md` を起こすか、実装のみで進めるかは OQ-8。

---

## 7. Task Breakdown

各タスクには `depends_on` を明示する。並列実行できるタスクは `[並列可]`。

> **凡例:** ✅ Done / ⬜ Todo / ⚠️ 要判断（OQ 待ち）

| ID | タスク | 担当 | 依存 | ステータス |
|---|---|---|---|---|
| SA-00 | 前提確認（`seller_applications` スキーマ・インデックス・`SecurityConfig` L103・Seed ユーザー・FE の既存リンク/queryKeys/ApiErrorCode） | BE | なし | ✅ Done（2026-08-02・全項目が計画書の記述どおりであることを確認） |
| SA-01 | `SecurityConfig` から `POST /api/v1/seller-applications` の `hasRole("BUYER")` を削除（OQ-1）※`SECURITY.md §3.1` への反映は完了済み | BE | SA-00 | ✅ Done（`SecurityConfig.java` の 1 行削除のみ） |
| SA-02 | `SellerApplicationStatus` enum + `SellerApplication` Entity | BE | SA-00 `[並列可]` | ✅ Done |
| SA-03 | `SellerApplicationRepository`（`existsByApplicantIdAndStatus` / `findFirstBy...OrderByCreatedAtDesc`） | BE | SA-02 | ✅ Done |
| SA-04 | DTO 2 本 + 例外 2 本（`ConflictException` 継承） | BE | なし `[並列可]` | ✅ Done（文言は `VALIDATION_RULES.md §6` と一致） |
| SA-05 | `VALIDATION_RULES.md` に `reason` の節を追記（§6 新設・旧 §6→§7 繰り下げ・§4 実装同期状況も更新） | DOC | なし `[並列可]` | ✅ Done（2026-08-02） |
| SA-06 | `AUDIT.md §4` に `SELLER_APPLICATION_SUBMITTED` を追記（+ 新規作成イベントの `entity_id` が null になる旨の注記） | DOC | なし `[並列可]` | ✅ Done（2026-08-02） |
| SA-07 | `SellerApplicationService`（3 段の申請可否判定 + `@Auditable`） | BE | SA-03, SA-04, SA-01 | ✅ Done |
| SA-08 | `SellerApplicationController`（POST 201 / GET me 200・Swagger アノテーション） | BE | SA-07 | ✅ Done |
| SA-09 | `V2__create_identity_tables.sql` に部分 UNIQUE インデックスを直接追記（OQ-3）※事前に `docker compose down -v` 済みであること | BE | SA-00 | ✅ Done（**開発 DB の作り直しは未実施 — オーナー対応待ち**） |
| SA-10 | Seed: `dev/V14__seed_seller_applications.sql`（+ 検証用 BUYER 2 名） | BE | SA-00 | ✅ Done（OQ-5 の推奨どおり `buyer1` 未申請 / `buyer2` PENDING / `buyer3` REJECTED / `seller1` APPROVED） |
| SA-11 | Backend 単体テスト（`SellerApplicationServiceTest`・Mockito） | BE | SA-07 | ✅ Done（2026-08-02・`SellerApplicationServiceTest` 11 件 + `SellerApplicationTest`（Entity）4 件） |
| SA-12 | Backend Controller/統合テスト（`ControllerTestBase` スライス + `IntegrationTestBase` で 409 / 404 / ロールガード / 部分 UNIQUE を DB 検証） | BE | SA-08, SA-09 | ✅ Done（2026-08-02・`SellerApplicationControllerTest` 21 件 + `SellerApplicationControllerIntegrationTest` 18 件） |
| SA-13 | 保持ポリシーの明記（R-7）: `REQUIREMENTS.md §15.3` 表・§15.4 匿名化 SQL・§15.7 `RET-10` / `SEQUENCE_FLOW §9.1` の `UserAnonymizationJob` / `DATA_DICTIONARY §4` | DOC | なし `[並列可]` | ✅ Done（2026-08-02） |
| SA-13b | 仕様不整合の訂正（R-5 / R-8）: `SEQUENCE_FLOW §3` の列名・リクエストボディ・HTTP メソッド・エラーコード名 / `API_DESIGN.md §4` の `non_null` 省略と `reason` のみ受け取る旨 | DOC | なし `[並列可]` | ✅ Done（2026-08-02） |
| SA-14 | UI 設計書 `design-system/pages/seller-application.md`（OQ-8 未決着） | FE | なし `[並列可]` | ⚠️ 要判断 |
| SA-15 | FE: 型定義（`types/api/seller-application.ts` + `enums.ts` + `index.ts` re-export）+ `ROUTES.seller` に `applicationNew` / `applicationRedirect` を追加 | FE | なし `[並列可]` | ⬜ Todo |
| SA-16 | FE: Zod スキーマ（`validations/sellerApplication.ts`） | FE | SA-05, SA-15 | ⬜ Todo |
| SA-17 | FE: API クライアント（`sellerApplications.ts`・**404→null**）+ query / mutation フック | FE | SA-15 | ⬜ Todo |
| SA-18 | FE: 申請画面（`/seller/applications/new`・4 状態 + ロール分岐） | FE | SA-16, SA-17, SA-14 | ⬜ Todo |
| SA-19 | FE: コンポーネントテスト（Vitest + RTL + MSW・4 状態 + 409 分岐） | FE | SA-18 | ⬜ Todo |

**依存グラフ（クリティカルパス）:**

```
（前提: 実装開始前に docker compose down -v で全テーブルをドロップ済み）

SA-00 ─┬─► SA-01（SecurityConfig）─┐
       ├─► SA-09（V2 追記）────────┤
       ├─► SA-02 ─► SA-03 ─────────┼─► SA-07 ─► SA-08 ─► SA-12
       └─► SA-10                   │        └─► SA-11
SA-04 ──────────────────────────────┘
SA-15 ─► SA-16 ─► SA-18 ─► SA-19
      └─► SA-17 ─► SA-18
SA-14（独立・OQ-8 待ち）
（SA-05 / SA-06 / SA-13 / SA-13b は完了済み。SA-04 と SA-16 は SA-05 の内容を正として実装する）
```

---

## 8. Security Checklist

コードレビュー前に全項目を確認すること。**根拠（ファイル:行・テスト名）を各項目に書き添えること**（`user-profile.md §8` の書式に従う）。

### 認可・ロール

- [x] 両エンドポイントが認証必須である（`SecurityConfig.java:89-104` の `permitAll` 列挙に `/api/v1/seller-applications` は無く `anyRequest().authenticated()` が適用される）。未認証 → 401 をテストで裏付ける（スライス: `should_return_401_when_applying_unauthenticated` / `should_return_401_when_reading_status_unauthenticated`、統合: `should_return_401_when_applying_without_authentication` / `should_return_401_when_reading_status_without_authentication`）
- [x] **申請者を常にトークンの `sub`（`@AuthenticationPrincipal`）から解決**している（`SellerApplicationController.java:44-58`）。パス・ボディに `applicantId` を取らず、ボディに他人の `applicantId` を混ぜても無視されることをテストで確認（`should_pass_only_token_subject_as_applicant_id` / 統合 `should_ignore_server_controlled_fields_in_the_request_body`）
- [x] **`ROLE_BUYER` 以外の申請を拒否**している（`SellerApplicationService.java:49-51` が唯一の認可ポイント）。SELLER / ADMIN のトークンで `POST` して 409 `SELLER_APPLICATION_ALREADY_APPROVED` になること、および**申請レコードが作られないこと**を統合テストで裏付け済み（`should_return_409_and_create_no_row_when_applicant_is_not_a_buyer`）。スライス側の `should_return_409_not_403_when_non_buyer_applies` が **403 で弾かれていない**ことを追加で保証する
- [x] `SecurityConfig` の変更が `POST /api/v1/seller-applications` の 1 行削除のみに留まっている（`git show 2d46609 -- SecurityConfig.java` が 1 行削除のみ。`/api/v1/admin/**` の `hasRole("ADMIN")`（L102）・`/api/v1/products` の `hasRole("SELLER")`（L103）は現存）
- [x] `GET /me` が**自分の申請しか返さない**（`should_return_only_own_application`。2 ユーザー分の申請を投入して検証）

### 入力・mass assignment

- [x] リクエスト DTO が **`reason` のみ**を持ち、`status` / `reviewerId` / `reviewComment` / `reviewedAt` を**受け付けない**（`CreateSellerApplicationRequest.java:20-23`）。実 DB での回帰テスト: `should_ignore_server_controlled_fields_in_the_request_body`（`status: "APPROVED"` / `reviewerId` / `reviewComment` / `reviewedAt` を全部混ぜても DB は `PENDING` / NULL のまま）
- [x] 新規申請の `status` が**常に `PENDING`** で作られる（`SellerApplication.java:63-64` の `@Builder.Default`）。`should_persist_pending_status_and_applicant_id_from_token`（`ArgumentCaptor`）・`SellerApplicationTest#should_default_status_to_pending_when_not_specified`・統合 `should_persist_pending_application_for_token_subject`
- [x] `reason` が `@NotBlank` + `@Size(max = 1000)` で検証されている（空白のみを弾く）（`should_return_422_when_reason_is_blank` ほか。**ただし全角スペース（U+3000）のみは通過する** — R-10 参照）
- [ ] `reason` は自由入力テキストであり、**フロントで `dangerouslySetInnerHTML` を使わない**（React の既定エスケープに委ねる）。バックエンドで HTML サニタイズはしない（保存は原文・表示時にエスケープ）→ **FE 未着手（SA-18）。バックエンド側は保存を原文のまま行っており正しい**

### 重複・整合性

- [x] 同一ユーザーの `PENDING` 申請が 2 件作られない（アプリ層の事前チェック + `V2` に追記した部分 UNIQUE インデックス）（`should_return_409_without_creating_a_second_row_when_pending_exists` + `should_reject_second_pending_application_at_database_level`）
- [x] 二重送信（同一リクエストの並行実行）で 500 にならず、409 が返る（`DataIntegrityViolationException` → `DUPLICATE_ENTRY` のハンドラーが既存）（DB 制約が `DataIntegrityViolationException` を投げることを `should_reject_second_pending_application_at_database_level` で確認。**真の並行実行そのものは自動テスト化していない** — 通常経路ではアプリ層の事前チェックが先に 409 を返すため）
- [x] `REJECTED` 申請が何件あっても新規申請を妨げない（SELLER-05）（`should_create_a_new_row_when_only_rejected_applications_exist` + `should_allow_multiple_rejected_applications_for_the_same_applicant`）

### 情報漏洩・監査

- [x] 409 / 404 のエラーメッセージに**他ユーザーの情報・内部 ID・DB 制約名が含まれない**（例外 2 本は固定文言・`SellerApplicationPendingException.java:10` / `SellerApplicationAlreadyApprovedException.java:10`。404 は `ResourceNotFoundException` の**単一引数版**を使い ID を埋め込まない（`SellerApplicationService.java:79`）。`should_throw_not_found_when_applicant_has_no_application` が文言を固定化している）
- [x] `SELLER_APPLICATION_SUBMITTED` が `audit_logs` に記録される（`correlation_id` 付き）。`reason` の全文が監査ログに載らないこと（`should_write_audit_log_when_application_is_submitted` で `correlation_id` 非 NULL・`old_value` / `new_value` が NULL であることを実 DB で確認）
- [x] `GlobalExceptionHandler` の `SENSITIVE_FIELDS` マスキングが本スライスのフィールドに悪影響を与えていない（`reason` はマスク対象外。422 レスポンスに `errors[].field = "reason"` が出ることを `should_return_422_when_reason_is_blank` で確認済み）

### レート制限

- [x] `POST /seller-applications` が API 全般バケット（100 req/min/user）に入ることを認識している。**未申請ユーザーが 100 件/分の申請を作れる**わけではないこと（1 件目成功 → 2 件目以降は 409）は `should_return_409_without_creating_a_second_row_when_pending_exists` が担保する（レート制限そのものは既存の `AuthRateLimitIntegrationTest` の担当・テストプロファイルでは緩和されている）

---

## 9. Test Checklist

> **実施結果（Backend・2026-08-02）:** `./gradlew clean build` / `cleanTest test jacocoTestReport jacocoTestCoverageVerification` ともに BUILD SUCCESSFUL。
> **全体 206 件・失敗 0 件**（本スライス追加分 54 件: Service 11 / Entity 4 / Controller スライス 21 / 統合 18）。
> JaCoCo ゲート（INSTRUCTION 0.80）通過。本スライスのクラスは `SellerApplicationService` / `SellerApplication` / `SellerApplicationController` / 例外 2 本すべて **INSTRUCTION 100% / BRANCH 100%**。
> Checkstyle（`checkstyleMain` / `checkstyleTest`）も通過。
>
> **Frontend:** 未実施（SA-15〜SA-19 が未着手のため）。

### 9.1 Backend テスト（JUnit 5 + Mockito + Testcontainers）

> **既存テスト基盤を踏襲する（`io.kivio.support`）:**
> - **単体（service）**: Mockito。`UserServiceTest` / `AddressServiceTest` が前例
> - **Controller スライス**: `ControllerTestBase`（`@WebMvcTest` + `@Import(SecurityConfig)` + `JwtProvider` モック・service は `@MockitoBean`）。`UserControllerTest` が前例。**`SecurityConfig` を `@Import` しているためロールガード（`hasRole("BUYER")`）もこのスライスで検証できる**
> - **フルスタック統合**: `IntegrationTestBase`（Testcontainers PostgreSQL）。DB 制約（部分 UNIQUE）・実データでの 409 / 404・`audit_logs` の記録はこちらで検証する
> - **Entity 単体**: `UserTest` / `AddressTest` が前例。ドメインメソッド（`isPending()` 等）の検証に使う

#### `SellerApplicationServiceTest`（単体・Mockito）— ✅ 全項目実施済み（11 件）

- [x] `apply`: BUYER・既存申請なし → `PENDING` で保存され `SellerApplicationResponse` を返す（`should_create_pending_application_when_buyer_has_no_existing_application`）
- [x] `apply`: 保存される Entity の `status` が `PENDING`・`applicantId` がトークン由来である（`ArgumentCaptor` で検証）（`should_persist_pending_status_and_applicant_id_from_token`。`reviewerId` / `reviewComment` / `reviewedAt` が null であることも併せて検証）
- [x] `apply`: `ROLE_SELLER` のユーザー → `SELLER_APPLICATION_ALREADY_APPROVED`（`should_reject_application_when_applicant_is_seller`。`verifyNoInteractions(repository)` で **DB を触らずに弾く**ことも検証）
- [x] `apply`: `ROLE_ADMIN` のユーザー → `SELLER_APPLICATION_ALREADY_APPROVED`（`should_reject_application_when_applicant_is_admin`）
- [x] `apply`: `PENDING` 申請が既存 → `SELLER_APPLICATION_PENDING`（**保存が呼ばれない**ことも検証）（`should_reject_application_when_pending_application_already_exists`）
- [x] `apply`: `APPROVED` 申請が既存（ロールは BUYER のまま＝不整合状態） → `SELLER_APPLICATION_ALREADY_APPROVED`（`should_reject_application_when_approved_application_exists_despite_buyer_role`）
- [x] `apply`: `REJECTED` 申請のみ既存 → **新規申請が作成される**（SELLER-05）（`should_not_consider_rejected_applications_as_a_blocker`。単体では **`REJECTED` を条件にした照会が発生しないこと**を検証し、実データでの 2 件目作成は統合テスト側で裏付ける）
- [x] `apply`: 存在しないユーザー → `RESOURCE_NOT_FOUND`（`should_propagate_not_found_when_applicant_does_not_exist`）
- [x] `getMyLatest`: 申請が複数ある → **`created_at` が最新の 1 件**を返す（`should_return_latest_application_when_applicant_has_applications` + `should_expose_review_fields_when_latest_application_is_rejected`。**並び順そのものは DB の責務**のため単体では委譲先メソッドと DTO 変換のみを検証し、実際の並び順は統合テスト `should_return_the_latest_application_when_reapplied_after_rejection` で裏付ける）
- [x] `getMyLatest`: 申請が 0 件 → `RESOURCE_NOT_FOUND`（`should_throw_not_found_when_applicant_has_no_application`。文言が「セラー申請が見つかりません」= 内部 ID を含まない汎用文言であることも検証）

#### `SellerApplicationTest`（Entity 単体）— ✅ 実施済み（4 件）

計画 §9.1 の前提「Entity 単体: `UserTest` / `AddressTest` が前例。ドメインメソッド（`isPending()` 等）の検証に使う」に対応。`isPending()` / `isApproved()` は本スライスの本番コードからは未使用（`feature/admin` で使う想定）のため、ここで検証しないと JaCoCo 上 0% のまま残る。

- [x] `status` 未指定時の既定値が `PENDING`（`@Builder.Default`）
- [x] `isPending()` / `isApproved()` が 3 ステータスすべてで正しく判定する
- [x] `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` により `id` のみで同一性が決まる

#### `SellerApplicationControllerTest`（スライス・`ControllerTestBase`）— ✅ 全項目実施済み（21 件）

- [x] `POST` 201・レスポンス形式が `API_DESIGN.md §4` と一致（`id` / `applicantId` / `reason` / `status` / `createdAt`）（`should_return_201_with_application_when_buyer_applies`。**`Location` ヘッダーが付かない**ことも検証）
- [x] `POST` 422（`reason` 未送信 / 空文字 / 空白のみ / 1001 文字）（`should_return_422_when_reason_is_blank`（`@ParameterizedTest`: `""` / 半角空白 / `\n` / `\t`）・`should_return_422_when_reason_is_missing`・`should_return_422_when_reason_exceeds_1000_characters`。境界値として **1000 文字ちょうどは 201** も検証: `should_accept_reason_of_exactly_1000_characters`）
- [x] `POST` 409（`SELLER_APPLICATION_PENDING` / `SELLER_APPLICATION_ALREADY_APPROVED`）で `application/problem+json` と `code` が返る（`should_return_409_when_pending_application_exists` / `should_return_409_when_applicant_is_already_approved`）
- [x] `POST` 未認証 → 401（`should_return_401_when_applying_unauthenticated`。Service が呼ばれないことも検証）
- [x] `POST` `ROLE_SELLER` / `ROLE_ADMIN` のトークン → **409 `SELLER_APPLICATION_ALREADY_APPROVED`**（403 ではない。OQ-1 の決定）（`should_return_409_not_403_when_non_buyer_applies`・`@ParameterizedTest`。**Service まで到達したこと**を `verify` で裏付けており、`SecurityConfig` の削除漏れ（R-1）があれば 403 で落ちる）
- [x] `GET /me` 200・`reviewComment` / `reviewedAt` が未審査時に**レスポンスから省略される**（`non_null`）（`should_omit_review_fields_when_latest_application_is_pending`。REJECTED 時に値が返ることは `should_return_review_comment_when_latest_application_is_rejected` で検証し、あわせて **`reviewerId` が DTO に含まれない**ことも確認）
- [x] `GET /me` 404（未申請）（`should_return_404_when_applicant_has_no_application`・`application/problem+json` も検証）
- [x] `GET /me` 未認証 → 401（`should_return_401_when_reading_status_unauthenticated`）
- [x] `GET /me` は SELLER / ADMIN でも 200 が返る（`API_DESIGN.md` の「権限: 全ロール」）（`should_allow_all_roles_to_read_own_application_status`・`@ParameterizedTest` で 3 ロール）
- [x] （追加）ボディに他人の `applicantId` / `status` を混ぜても Service にはトークンの `sub` だけが渡る（`should_pass_only_token_subject_as_applicant_id`）

#### `SellerApplicationControllerIntegrationTest`（`IntegrationTestBase` + Testcontainers）— ✅ 全項目実施済み（18 件）

DB の検証は `JdbcTemplate`（生 SQL）で行う。Repository に検証専用のメソッド（件数取得等）を追加せず、本番コードを増やさないため。

- [x] `POST` → DB に `status = 'PENDING'`・`applicant_id` がトークンの `sub`・`reviewer_id` / `reviewed_at` が NULL の行が 1 件できる（`should_persist_pending_application_for_token_subject`）
- [x] ボディに `status: "APPROVED"` / `reviewerId` / `reviewComment` を混ぜても**無視され** `PENDING` で保存される（mass assignment 回帰）（`should_ignore_server_controlled_fields_in_the_request_body`。**他人の ID に紐づく申請が作られない**ことも検証）
- [x] `PENDING` がある状態で再 `POST` → 409・**DB の行数が増えない**（`should_return_409_without_creating_a_second_row_when_pending_exists`）
- [x] `REJECTED` のみの状態で `POST` → 201・**行が 2 件になる**（履歴が残る・SELLER-05）（`should_create_a_new_row_when_only_rejected_applications_exist`。却下履歴が上書きされず `REJECTED` + `PENDING` の 2 行になることを検証）
- [x] `GET /me` が他ユーザーの申請を返さない（2 ユーザー分の申請を投入して検証）（`should_return_only_own_application`）
- [x] `GET /me` が最新 1 件（却下 → 再申請の順で投入し、返るのが新しい PENDING であること）（`should_return_the_latest_application_when_reapplied_after_rejection`。`created_at` を分離するため投入間に `Thread.sleep(10)` を挟む＝`AddressControllerIntegrationTest` と同じ流儀）
- [x] **部分 UNIQUE インデックスの検証**: アプリの事前チェックを経由せず 2 件目の `PENDING` を直接 INSERT すると `DataIntegrityViolationException` になる（`should_reject_second_pending_application_at_database_level`）／`REJECTED` は同一ユーザーで何件でも INSERT できる（`should_allow_multiple_rejected_applications_for_the_same_applicant`）／PENDING の一意性はユーザー単位で他ユーザーと衝突しない（`should_allow_one_pending_application_per_user`）
- [x] SELLER / ADMIN のトークンでの `POST` が 409 になり、**DB に申請レコードが作られない**（`should_return_409_and_create_no_row_when_applicant_is_not_a_buyer`・`@ParameterizedTest`。DB 上のロールと JWT の `role` クレームを揃えて投入している）
- [x] `audit_logs` に `action = 'SELLER_APPLICATION_SUBMITTED'` / `entity_type = 'SELLER_APPLICATION'` の行が記録される（`UserAuditIntegrationTest` が前例）（`should_write_audit_log_when_application_is_submitted`。`correlation_id` 非 NULL・`actor_role = 'BUYER'`・**`entity_id` が null**（R-9 の想定どおり）・`old_value` / `new_value` が null（`reason` が監査ログに複製されない）まで検証。ロール判定で弾かれた場合に FAILURE 行が残ることは `should_write_failure_audit_log_when_application_is_rejected_by_role_check`）
- [x] JaCoCo カバレッジゲート（0.80）を維持する（`jacocoTestCoverageVerification` 通過。本スライスのクラスは INSTRUCTION / BRANCH ともに 100%）
- [x] （追加）`reason` が空白のみの `POST` → 422 かつ **DB に行が作られない**（`should_return_422_when_reason_is_blank`）／`POST` 未認証 → 401 ／`GET /me` 未申請 → 404 ／`GET /me` 未認証 → 401

### 9.2 Frontend テスト（Vitest + RTL + MSW）

> MSW ハンドラーは `src/test/mocks/handlers/sellerApplications.ts` を新規作成し `server.ts` に登録する（`addresses.ts` が前例）。

- [ ] `getMySellerApplication`: 404 レスポンスで **`null` を返す**（throw しない）／500 は throw する
- [ ] `SellerApplicationView`: 未申請（null） → 制度説明 + 申請フォームが出る
- [ ] `SellerApplicationView`: `PENDING` → 審査中表示・**フォームが出ない**
- [ ] `SellerApplicationView`: `REJECTED` → 却下理由（`reviewComment`）が表示され、再申請フォームが出る
- [ ] `SellerApplicationView`: `APPROVED` → リダイレクトが呼ばれる（`router.replace` をモックして検証）
- [ ] `SellerApplicationView`: `ROLE_SELLER` / `ROLE_ADMIN` → 申請フォームを出さずリダイレクト
- [ ] `SellerApplicationView`: ストア復元前（`useAuthHydrated` が false）は**リダイレクトを走らせない**（誤リダイレクト回帰）
- [ ] `SellerApplicationView`: 取得失敗（500）→ エラー表示 + 再試行
- [ ] `SellerApplicationForm`: 空送信 → zod のエラーメッセージ（`VALIDATION_RULES` と一致）
- [ ] `SellerApplicationForm`: 1001 文字 → クライアント側で弾く／文字数カウンターが機能する
- [ ] `SellerApplicationForm`: 正常送信 → `POST` が呼ばれ `queryKeys.sellerApplication.me` が invalidate され、審査中表示に切り替わる
- [ ] `SellerApplicationForm`: 送信中はボタン非活性（二重送信防止）
- [ ] `SellerApplicationForm`: 409 `SELLER_APPLICATION_PENDING` → 専用文言が出て invalidate される
- [ ] `SellerApplicationForm`: 409 `SELLER_APPLICATION_ALREADY_APPROVED` → 専用文言が出て invalidate される
- [ ] `pnpm test` / `pnpm lint` / `pnpm typecheck` / `pnpm build` がグリーン
- [ ] （任意・後続可）Playwright E2E。`user-profile.md §9.2` の最終項目と同じく、常設は後続スライスに委ねてよい

---

## 10. Definition of Done

PR をマージするには以下を全て満たすこと。**各項目に根拠（実行結果・テスト名・確認手順）を書き添えること。**

### 機能要件

- [x] `POST /seller-applications` が BUYER の申請を受け付け、201 で `PENDING` の申請を返す（`should_persist_pending_application_for_token_subject`）
- [x] 審査中の申請がある状態での再申請が 409 `SELLER_APPLICATION_PENDING` で拒否される（`should_return_409_without_creating_a_second_row_when_pending_exists`）
- [x] 既に SELLER / ADMIN のユーザーの申請が 409 `SELLER_APPLICATION_ALREADY_APPROVED` で拒否される（OQ-1）（`should_return_409_and_create_no_row_when_applicant_is_not_a_buyer`）
- [x] 却下後の再申請が新規レコードとして作成され、過去の申請履歴が残る（SELLER-05）（`should_create_a_new_row_when_only_rejected_applications_exist`）
- [x] `GET /seller-applications/me` が最新 1 件を返し、未申請なら 404 を返す（`should_return_the_latest_application_when_reapplied_after_rejection` / `should_return_404_when_applicant_has_never_applied`）
- [ ] `/seller/applications/new` が 4 状態（未申請 / PENDING / REJECTED / APPROVED）を正しく出し分ける
- [ ] グローバルナビ（`UserMenu` / `MobileMenuSheet` / `GlobalFooter`）の「セラー申請」リンクが**404 にならず**画面へ到達する

### セキュリティ要件

- [x] §8 Security Checklist の全項目を確認済み（根拠を §8 に記載）※ `dangerouslySetInnerHTML` の項目のみ FE 未着手（SA-18 で確認する）
- [x] mass assignment（`status` / `reviewerId` 等の外部指定）が不可能であることをコードとテストで確認（`CreateSellerApplicationRequest` が `reason` のみ + `should_ignore_server_controlled_fields_in_the_request_body`）

### テスト要件

- [x] Backend: `./gradlew cleanTest test jacocoTestReport jacocoTestCoverageVerification` がグリーン。JaCoCo ゲート（0.80）を維持（2026-08-02・**206 件 / 失敗 0**・`clean build` も BUILD SUCCESSFUL）
- [x] Backend: `SellerApplicationService` の単体カバレッジ ≥ 80%（分岐が多いので分岐カバレッジも確認）（**INSTRUCTION 100% / BRANCH 100%**。`SellerApplication` Entity も 100% / 100%）
- [ ] Frontend: `pnpm test` / `pnpm lint` / `pnpm typecheck` / `pnpm build` がグリーン

### コード品質

- [x] `BACKEND_CODING_STANDARDS.md` 準拠（DTO = record・Lombok パターン・レイヤー責務・`@Transactional(readOnly)` の適切な使用・例外は `KivioException` 階層）※ テストは §13.1 の種別どおり（Entity 単体 / `@ExtendWith(MockitoExtension)` / `@WebMvcTest` / `@SpringBootTest` + Testcontainers）。`checkstyleMain` / `checkstyleTest` 通過
- [ ] `FRONTEND_CODING_STANDARDS.md` 準拠（`'use client'` は葉のみ・named export・TanStack Query の query/mutation 分離・Zustand セレクタ形式）
- [ ] Controller がビジネスロジックを持たない（Service 委譲のみ）
- [ ] **ドメイン間の直接 import がない**（`SellerApplication` は `identity` 内。`applicantId` / `reviewerId` は `UUID` 値参照で `@ManyToOne` を張らない）
- [ ] Swagger UI に 2 エンドポイントが表示される（`@Tag` / `@Operation` 付与・`/v3/api-docs` で確認）

### ドキュメント同期

- [x] `AUDIT.md §4` に `SELLER_APPLICATION_SUBMITTED` を追記済み（SA-06・2026-08-02）
- [x] `VALIDATION_RULES.md §6` に `reason` の節を追記済み（SA-05・2026-08-02）
- [x] `REQUIREMENTS.md §15.3` / `§15.4` / `§15.7`（`RET-10`）・`SEQUENCE_FLOW §9.1`・`DATA_DICTIONARY §4` に保持ポリシーを追記済み（SA-13・2026-08-02）
- [x] `SEQUENCE_FLOW §3` の列名・メソッド・リクエストボディ、`API_DESIGN §4` の `non_null` 注記を訂正済み（SA-13b・2026-08-02）
- [ ] 実装した Bean Validation と zod のメッセージが `VALIDATION_RULES.md §6` と一字一句一致している（SA-04 / SA-16 の完了時に確認）
- [x] OQ-1 の決定（`POST /seller-applications` の認可は Service 層で行い 409 を返す・403 は発生しない）を `SECURITY.md §3.1` に反映済み（2026-08-02）
- [ ] `SecurityConfig.java` の実コードが `SECURITY.md §3` のサンプルと一致している（`seller-applications` の行が両方から消えていること）

### 動作確認

- [ ] `docker compose up` で全サービスが起動する
- [ ] ドロップ後のクリーン DB で Flyway マイグレーション（編集した `V2` + dev の `V14`）が正常適用される（`flyway_schema_history` が全て `success = true`・`idx_seller_applications_pending_unique` が `pg_indexes` に存在する）
- [ ] ブラウザで 4 状態すべてを確認する（Seed の未申請 / PENDING / REJECTED ユーザー + `seller1` でログインし直す）
- [ ] 申請送信後に `audit_logs` へ `SELLER_APPLICATION_SUBMITTED` が記録されていることを DB で確認

---

## 11. Risks / Open Questions

### Open Questions（OQ-8 を除き 2026-08-02 時点で全件クローズ）

| # | 質問 | 影響タスク | 推奨 |
|---|---|---|---|
| ~~OQ-1~~ ✅ | **`SecurityConfig` のロールガードと `SELLER_APPLICATION_ALREADY_APPROVED` の到達不能問題。** `SecurityConfig.java:103` に `POST /api/v1/seller-applications` → `hasRole("BUYER")` が既に宣言されている。このためロールが `ROLE_SELLER` / `ROLE_ADMIN` のユーザーは**フィルターチェーンで 403 `ACCESS_DENIED` になり、Service に到達しない**。結果として `ERROR_CODES.md §2.3` / `API_DESIGN.md §4` が定める **409 `SELLER_APPLICATION_ALREADY_APPROVED`（「既に `ROLE_SELLER` を持つユーザーの申請」）が事実上到達不能**になる（BUYER のまま APPROVED 申請を持つ不整合状態でしか出ない）。仕様とコードのどちらを正とするか | SA-01, SA-07, SA-08, SA-12 | **決定（2026-08-02）: `SecurityConfig` から `.requestMatchers(HttpMethod.POST, "/api/v1/seller-applications").hasRole("BUYER")` を削除し（`anyRequest().authenticated()` に委ねる）、ロール判定を `SellerApplicationService` に一本化する。** 理由: ① 仕様が定めた 409 とエラーコードがそのまま生きる ② 「すでに出品者として承認されています」という**具体的な説明**を返せる（403 の汎用文言では利用者に何も伝わらない）③ 認可の強度は変わらない（Service で必ず弾き、テストで担保する）。**`POST /seller-applications` に対する 403 は発生しなくなる**（未認証は 401、ロール違反は 409）|
| **OQ-2** ✅ | **レスポンス DTO を 1 本にするか 2 本に割るか。** `API_DESIGN.md` の `POST` 例と `GET /me` 例でフィールド構成が異なる | SA-04 | **決定: 単一 `SellerApplicationResponse`（superset）。** `spring.jackson.default-property-inclusion: non_null` により未審査時は `reviewComment` / `reviewedAt` が自動で省略され、両方の例を満たせる。DTO を割る積極的理由が無い |
| ~~OQ-3~~ ✅ | **`PENDING` の一意性を DB 制約で担保するか。担保するなら `V2` 直接編集か新規 `V12` か** | SA-09, SA-12 | **決定（2026-08-02）: 担保する（`user-profile.md` R-4 の教訓）+ `V2__create_identity_tables.sql` を直接編集する。** 当初は「`V2` は `main` マージ済みなので新規 `V12`」を推したが、**プロジェクトオーナーの判断で「開発段階でありスキーマを綺麗に保ちたい。実装前に全テーブルをドロップする」**とした。移行コスト（`checksum mismatch`）は事前のドロップで解消されるため、マイグレーション履歴に増分インデックスを積まずに済む方を採る。`user-profile` の `V4` 直接編集と同じ流儀に揃う |
| ~~OQ-4~~ ✅ | **APPROVED ユーザー / SELLER / ADMIN のリダイレクト先。** `FRONTEND_IA.md §5` は「`/seller/dashboard` または `/`」とするが、**`/seller/dashboard` は未実装**（タスク #11）でリダイレクトすると 404 に落ちる | SA-18, SA-19 | **決定（2026-08-02）: 本スライスでは SELLER / ADMIN / APPROVED いずれも `/` へリダイレクトする。** リダイレクト先を `ROUTES.seller.applicationRedirect`（値: `'/'`）として定数化し、`feature/seller-dashboard`（#11）着手時に**この 1 箇所を `ROUTES.seller.dashboard` に差し替えるだけ**で済む形にする（`user-profile.md` R-9 の反省から、**コメントだけの「TODO フック」にはせず、定数として実体を残す**） |
| **OQ-5** ⬜ | **Seed データの割り当て。** `buyer1` は `/profile/*` の動作確認にも使われる主力ユーザー。ここに PENDING を入れると「未申請フォーム」を Seed だけで確認できなくなる | SA-10 | **推奨: `buyer1` は未申請のまま残し、検証用に `buyer2`（PENDING）/ `buyer3`（REJECTED）を `V14` で追加する。** `seller1` には APPROVED 申請を紐づけ、4 状態すべてを Seed だけで再現できるようにする |
| ~~OQ-6~~ ✅ | **`SELLER_APPLICATION_SUBMITTED` が `AUDIT.md §4` の記録対象イベント表に無い**（`SEQUENCE_FLOW §3` には記載あり）。監査対象として正式に採用するか | SA-06, SA-07 | **決定: 採用。`AUDIT.md §4` にカテゴリ「セラー申請」として追記済み（2026-08-02）。** ロール昇格につながる申請行為は追跡対象として妥当で、承認/却下（既に表にある）とペアで初めて監査証跡が完結する |
| **OQ-7** ⬜ | **ヘッダーの「セラー申請」リンクの表示条件。** `FRONTEND_IA.md §2`（L101-102）は「申請未済かつ PENDING 申請なしの場合のみ表示」とするが、実装するとグローバルヘッダーが全ページで `GET /seller-applications/me` を叩くことになる | SA-18 | **推奨: 現行の `isBuyer` のみを維持し、IA 側に注記を追加する。** PENDING 中にリンクを踏んでも審査中 UI が出るだけで害はない。全ページ +1 リクエストのコストのほうが大きい |
| **OQ-8** ⬜ | **UI 設計書（`design-system/pages/seller-application.md`）を起こすか。** `user-profile` では実装前に設計書を作り、実装計画から参照する運用にした | SA-14, SA-18 | **判断を仰ぐ。** 本画面は 1 URL に 4 状態が同居し「フォーム」と「ステータス表示」が切り替わる特殊な構造で、`user-profile.md` の「台帳」原則がそのままは当てはまらない。運用の一貫性を取るなら作成、スコープを絞るなら `MASTER.md` + `user-profile.md §7`（入力方針）に準拠して実装のみで進める |

### Risks（既知のリスク）

| # | リスク | 影響度 | 対策 |
|---|---|---|---|
| R-1 | ~~OQ-1 未決着のまま実装すると `ALREADY_APPROVED` のテストが行き詰まる~~ → **論点は解消したが、決定の実行漏れリスクが残る。** `SecurityConfig` の 1 行を削除し忘れたまま Service のロール判定だけ書くと、SELLER のトークンは 403 で弾かれ続け、Service の判定が**一度も実行されないまま**「動いているように見える」（テストを書かなければ気付かない） | 中 | SA-01 を SA-07 の前提として依存グラフに残す。§9 の Controller テストに「SELLER / ADMIN → 409（403 ではない）」を必須項目として置いてあり、削除漏れがあればこのテストが落ちる |
| R-1b | `SecurityConfig` はシニア所管かつ全エンドポイントの認可が集中するファイルで、編集ミスが他ドメインの認可を壊しうる | 中 | 変更を**1 行の削除のみ**に限定し、単独コミットに分ける（§5.3 ステップ 0）。§8 に「他の `requestMatchers` に影響していないこと」の確認項目を置いた。既存の `AuthControllerIntegrationTest` / `UserControllerIntegrationTest` の 401/403 系テストが回帰の網になる |
| R-2 | 二重送信（ダブルクリック・並行リクエスト）で `PENDING` が 2 件でき、以降その利用者は永久に 409 `PENDING` から抜け出せない（削除 API が無いため**管理者の手動 DB 操作でしか復旧できない**） | 中 | ① フロントで送信中ボタン非活性 ② Service の事前チェック ③ **部分 UNIQUE インデックス**（OQ-3・`V2` に直接追記）で構造的に不可能にする。`user-profile.md` R-4 と同型のリスクで、同じ対策が有効 |
| R-2b | `V2` の直接編集はチェックサムを変えるため、**ドロップを忘れた環境ではアプリが起動しない**（`Migration checksum mismatch`）。ブランチを切り替えた他の開発者も同じ状態に落ちる | 中 | 実装開始前にプロジェクトオーナーが `docker compose down -v` を実施する（本書冒頭とステップ 0 に明記）。**PR の説明にも「このブランチを取得したら DB の作り直しが必要」と書くこと**。Testcontainers は毎回新規 DB のため CI は影響を受けない |
| R-3 | `GET /seller-applications/me` の 404 は「未申請」という**正常状態**だが、`apiFetch` は非 2xx を一律 `ApiError` として throw する。素直に `useQuery` に渡すと未申請ユーザー全員がエラー画面になり、リトライまで走る | 中 | API クライアント関数（`getMySellerApplication`）で **404 を `null` に変換**する（§6.4 ステップ 3）。FE テストで明示的に回帰を張る（§9.2 第 1 項目） |
| R-4 | `/seller/dashboard` が未実装のため、APPROVED ユーザーのリダイレクト先が 404 になる | 低 | ✅ 対策確定（OQ-4）。`ROUTES.seller.applicationRedirect = '/'` として定数化し、SELLER / ADMIN / APPROVED の 3 経路すべてがこれを参照する。#11 完了時に値を 1 箇所差し替える |
| R-5 | **`SEQUENCE_FLOW.md §3` の記述が実スキーマと食い違っていた。** 列名が `user_id`（正: `applicant_id`）/ `submitted_at`（正: `created_at`・そんな列は無い）/ `reviewed_by`（正: `reviewer_id`）/ `rejection_reason`（正: `review_comment`）、リクエストボディが `{shopName, description, ...}`（正: `{reason}`）、管理者の承認/却下が `PATCH`（`API_DESIGN.md §15` / `REQUIREMENTS.md §API 一覧` は `POST`）。鵜呑みにすると存在しない列を参照するコードを書く | ~~中~~ → 解消 | ✅ **解決（2026-08-02・SA-13b）**。`SEQUENCE_FLOW §3` を実スキーマ / `API_DESIGN` に合わせて全面訂正し、図の先頭に「`DB_DESIGN.md §3.4` の実スキーマを正とする」注記を追加した。HTTP メソッドは 3 ドキュメント中 2 つ（`API_DESIGN` / `REQUIREMENTS`）が `POST` で一致していたため `SEQUENCE_FLOW` 側を `POST` に寄せた。**実装の判断基準は従来どおり `DB_DESIGN.md §3.4` と実マイグレーション（`V2`）** |
| R-6 | 承認処理が別スライスのため、本スライス単体では **APPROVED 状態を正規の経路で作れない**。E2E / 動作確認では Seed か手動 UPDATE で APPROVED を作る必要があり、「承認したのにロールが `ROLE_BUYER` のまま」という**本番では起こらない不整合状態**でテストすることになる | 低 | Seed（`V14`）で `seller1`（既に `ROLE_SELLER`）に APPROVED 申請を紐づけ、**ロールと申請の整合が取れた状態**を用意する。Service の判定を「ロール優先 + APPROVED 申請も見る」の 2 段にしてあるため（§3.2）、どちらの状態でも正しく弾ける |
| R-7 | **`seller_applications` が `REQUIREMENTS.md §15.3`（エンティティ別保持ポリシー）に載っていなかった。** `reason` は自由記述で、申請者が氏名・屋号・連絡先・事業内容などの PII を書き込みうる。退会 90 日後に `users` は匿名化されるが `id` は保持されるため、`applicant_id` から個人を再特定できてしまい、**`user-profile.md` R-6 と同一構造の匿名化の実質無効化**が起きる | 中 | ✅ **仕様化まで完了（2026-08-02・SA-13）。実装は `feature/batch-jobs`（#15）に残る。** 方針は「90 日 / ユーザー匿名化と同一トランザクションで `reason` と `review_comment` を `(削除済み)` に置換」。**物理削除ではなく匿名化**にしたのは、`ROLE_SELLER` への権限昇格が「いつ・誰の承認で行われたか」が監査証跡として必要なため（`addresses` の RET-09 とはここが異なる）。仕様 5 か所に明記: ① `REQUIREMENTS §15.3` の表 ② `§15.4` の匿名化 SQL（冪等性条件付き）③ `§15.7` に `RET-10` 新設 ④ `SEQUENCE_FLOW §9.1` の `UserAnonymizationJob` に処理ステップ ⑤ `DATA_DICTIONARY §4` の概要とカラム備考。**仕様に書かなければ引き継がれない**というのが直前スライスの教訓（`user-profile.md` R-6） |
| R-8 | `spring.jackson.default-property-inclusion: non_null` により、`API_DESIGN.md §4` の `GET /me` 例にある `"reviewComment": null` は**実際にはキーごと省略される**。フロントの型を `reviewComment: string \| null`（必須）で定義すると、実レスポンスとズレて `undefined` が流れ込む | 低 | ✅ **解決（2026-08-02・SA-13b）**。`API_DESIGN.md §4` のレスポンス例に「`null` フィールドは省略される・クライアント型は省略を許容する形で定義すること」を注記した（全エンドポイント共通の挙動である旨も明記）。実装側は FE 型を `reviewComment?: string \| null` にする（§6.4 ステップ 1） |
| R-10 | **`@NotBlank` は全角スペース（U+3000）のみの `reason` を通す。** Hibernate Validator の `NotBlankValidator` は `charSequence.toString().trim().length() > 0` で判定し、Java の `String.trim()` は U+0020 以下しか除去しないため。一方フロントの zod は `.trim()`（JS の `String.trim()` は U+3000 も除去する）で弾く想定のため、**FE を通せば弾かれるが API を直接叩くと `reason = "　"` の申請が 201 で通る**。実害は「中身のない申請が 1 件でき、管理者が却下する」程度だが、`VALIDATION_RULES.md §6` の「空白のみ不可」とは食い違う | 低 | SA-11 / SA-12 の範囲外のため**未修正**。テストでは全角スペースのケースを意図的に除外し、`SellerApplicationControllerTest#should_return_422_when_reason_is_blank` の直上にコメントで理由を残した。修正する場合の選択肢は ① `CreateSellerApplicationRequest` に `@Pattern(regexp = "(?sU).*\\S.*")` を追加する（**`(?U)` が必須**。Java の `\s` は既定で US-ASCII のみのため `(?s).*\S.*` では U+3000 が「非空白」と判定されて素通りする。JDK 25 で実測: `"　".matches("(?s).*\\S.*")` → `true` / `"　".matches("(?sU).*\\S.*")` → `false`）② Service で `reason.strip()` した結果が空なら弾く（`String.strip()` は `Character.isWhitespace` 基準で U+3000 を除去する。実測: `"　".strip().length()` → `0`、`"　".trim().length()` → `1`）。**どちらを採るか、そもそも直すかはオーナー判断**。直す場合は `VALIDATION_RULES.md §6` と zod 側の整合も併せて見直すこと |
| R-9 | `@Auditable` の `entityIdParam` はメソッド**引数**からしか `UUID` を拾えないため、新規作成された申請の `id` を `audit_logs.entity_id` に残せない（null になる）。承認/却下の監査行（`feature/admin`）とは `entity_id` で突き合わせられない | 低 | 本スライスでは `entityIdParam` を指定せず null を許容する（`AuthService#register` の `USER_REGISTERED` と同じ扱い）。戻り値から `entityId` を拾う Aspect 拡張は横断的変更なので、必要になった時点で別途起票する |

---

## 12. 引き継ぎ事項 (Handover)

### 12.1 後続スライスへの申し送り

| 宛先 | 内容 |
|---|---|
| `feature/admin`（#12） | 承認時の ① `users.role` → `ROLE_SELLER` ② `shops` レコード自動生成（SELLER-03）③ `SELLER_APPLICATION_APPROVED` / `_REJECTED` の監査記録 ④ `SellerApplication#approve()` / `reject()` ドメインメソッドの追加（本スライスでは未使用のため意図的に作っていない）⑤ `SELLER_APPLICATION_NOT_REVIEWABLE`（409）の実装。`SEQUENCE_FLOW §3` の `PATCH` 表記と `API_DESIGN` の `POST` 表記の不一致も同スライスで決着させること（R-5） |
| `feature/notification`（#10） | 審査結果の通知（NOTIF-04）。`FRONTEND_API_CONTRACT.md` L744 のとおり、通知タイプ `SELLER_APPLICATION` のリンク先は `/seller/applications/new`（本スライスで実装する画面）である |
| `feature/mail-notification`（#14） | 審査結果メール（`EMAIL_DESIGN.md` を参照） |
| `feature/seller-dashboard`（#11） | `ROUTES.seller.applicationRedirect` の値を `'/'` から `'/seller/dashboard'` に差し替える（OQ-4）。**この定数 1 箇所の変更だけで SELLER / ADMIN / APPROVED の 3 経路すべてが切り替わる** |
| `feature/batch-jobs`（#15） | R-7 の `seller_applications` 匿名化。SA-13 で `REQUIREMENTS.md §15.3` に明記される前提 |

### 12.2 ステータス凡例

⬜ Todo / ⚠️ 要判断（OQ 待ち）/ 🟡 進行中 / ✅ Done
