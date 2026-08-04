# ADR-006: メール認証コード（OTP）方式と Redis 一時ストレージ

**ステータス:** 採用済み（Phase 2）  
**作成日:** 2026-06-11  
**作成者:** Dao Nguyen

---

## コンテキスト

新規会員登録（メール + パスワード）のメールアドレス確認方式を決める必要があった。当初案は「登録時に `users` を作成 → メールにマジックリンク（`/auth/verify-email?token=uuid`）→ クリックで確認」だったが、以下の問題が指摘された:

1. **未確認アカウントの滞留** — 確認しないまま放置された `users` レコードが DB に残り続け、クリーンアップバッチが必要になる。
2. **未確認状態での挙動の複雑さ** — 「メール未確認だがレコードは存在する」という中間状態を全フローで考慮する必要があり、`email_verified` フラグ・`EMAIL_NOT_VERIFIED` エラー・ログイン制御が必要になる。
3. **期限切れ／使用済みリンクの再送信の問題** — 未認証状態でワンクリック再送信できると、第三者によるメール爆撃や「1年前の未確認リンクの再送信」など、UX とセキュリティ両面で破綻しやすい。

実務の大手サービス（Stripe / Shopify 等）でも、未認証状態でのワンクリック再送信は提供せず、コード入力（OTP）または再登録に倒す設計が一般的である。

---

## 決定事項

### 1. メール確認は「メール認証コード（OTP）方式」を採用する

6 桁の数値 OTP をメール送信し、ユーザーが登録画面の入力欄に入力して検証する（マジックリンクは使わない）。

### 2. `users` レコードはメール認証完了後にのみ作成する

登録は 3 ステップに分割し、最終ステップ（パスワード設定）完了時に初めて `users` を INSERT する。

| ステップ | エンドポイント | 処理 |
|---|---|---|
| ① コード送信 | `POST /auth/register/request-otp` | メール重複チェック → OTP 生成・メール送信 |
| ② コード検証 | `POST /auth/register/verify-otp` | OTP 照合 → `registrationToken` 発行 |
| ③ 登録完了 | `POST /auth/register/complete` | `users` INSERT → 自動ログイン（トークン発行） |

### 3. OTP・登録セッションは Redis に TTL 付きで一時保存する（DB テーブルを持たない）

| キー | 値 | TTL | 用途 |
|---|---|---|---|
| `reg:otp:{email}` | `{otpHash: SHA-256(otp), attempts: int}` | 10 分 | OTP 検証。試行 5 回超過で失効 |
| `reg:session:{registrationToken}` | `{email}` | 30 分 | OTP 検証済みメールの登録セッション |

### 4. `email_verified` 列・`EMAIL_NOT_VERIFIED` エラー・`email_verification_tokens` テーブルを廃止する

メール登録ユーザーは OTP 検証済み、Google OAuth ユーザーは Google 側で検証済みであり、`users` に未認証ユーザーが存在しえないため。

---

## 理由

### OTP 方式 + 認証後作成を選ぶ理由

| 観点 | マジックリンク + 先行作成（旧案） | OTP + 認証後作成（採用） |
|---|---|---|
| 未確認アカウントの滞留 | 発生する → cleanup バッチ必要 | **発生しない**（`users` は確定後のみ） |
| 中間状態の考慮 | `email_verified` / `EMAIL_NOT_VERIFIED` が必要 | **不要**（全 `users` が認証済み） |
| 即ログイン問題 | フラグで制御が必要 | レコードが存在しないため**原理的に不可** |
| 期限切れ時の挙動 | 再送信が破綻しやすい | 同じ画面でコード再送信（自然） |
| URL 露出リスク | トークンが URL/ログに残る懸念 | コード入力のため URL に何も載らない |

### Redis（DB テーブルではなく）を一時ストレージに選ぶ理由

- OTP・登録セッションは**短命（分単位）で大量に発生し得る一時データ**であり、RDB に置くと書き込み負荷と掃除コストが増える。
- Redis は **TTL が組み込み機能**であり、期限切れデータが自動消滅するためクリーンアップバッチが不要。
- OTP のメール単位スロットリング（爆撃対策）や試行回数カウンタも Redis の TTL/アトミック操作で簡潔に実装できる。
- devcontainer / docker-compose に Redis を導入し、将来のレート制限カウンタや WebSocket Pub/Sub にも転用できる。

---

## セキュリティ

- OTP は平文で保存せず **SHA-256 ハッシュ**で Redis に保存する。`registrationToken` は不透明な UUID v4。
- OTP 検証は **5 回まで**。超過で `reg:otp:{email}` を失効させ `OTP_MAX_ATTEMPTS_EXCEEDED` を返す（ブルートフォース対策）。
- `request-otp` は IP 単位（認証系 10 req/min）に加え、**メールアドレス単位**でもスロットリングする（60 秒に 1 回・1 時間に 5 回まで）。これによりクロス IP のメール爆撃を防ぐ。
- OTP 送信メールには宛名に個人情報を含めず、「コードを共有しない」旨を明記する。

---

## 結果

### 得られるもの

- `users` テーブルが常に「認証済みの完全なユーザー」のみを保持する（不変条件がシンプル）。
- `email_verified` フラグ・`EMAIL_NOT_VERIFIED` 分岐・未確認アカウント cleanup バッチ・`email_verification_tokens` テーブルがすべて不要になる。
- 再送信まわりの UX/セキュリティ問題が構造的に解消する。

### 受け入れるトレードオフ

- Redis への依存が増える（Phase 2 でインフラに Redis を追加）。
- 登録が 3 ステップになり、フロントの状態管理（OTP 入力・registrationToken 保持）が増える。
- OTP 配送はメール到達性に依存する（マジックリンクと同様）。

---

## 影響範囲（更新済みドキュメント）

- `docs/design/API_DESIGN.md` — `/auth/register/{request-otp,verify-otp,complete}` 追加、旧 `/auth/register`・`/auth/verify-email` 廃止
- `docs/design/ERROR_CODES.md` — `OTP_INVALID` / `OTP_EXPIRED` / `OTP_MAX_ATTEMPTS_EXCEEDED` / `REGISTRATION_SESSION_INVALID` 追加、`EMAIL_VERIFICATION_TOKEN_*` / `EMAIL_NOT_VERIFIED` 廃止
- `docs/design/SEQUENCE_FLOW.md` § 1.1、`docs/design/frontend/USER_FLOW.md` § 1.1 — フロー刷新
- `docs/design/DB_DESIGN.md` / `DATA_DICTIONARY.md` / `er_diagram.dbml` — `email_verification_tokens` テーブル・`users.email_verified` 列を削除
- `docs/design/EMAIL_DESIGN.md` — AUTH-01 を OTP メールへ変更
- `docs/design/frontend/FRONTEND_IA.md` / `FRONTEND_API_CONTRACT.md` — 画面・型・エラー UI 更新
- `docs/architecture/OVERVIEW.md` / `SECURITY.md` — Redis 導入・OTP レート制限追記
- `docs/architecture/AUDIT.md` / `docs/architecture/DOMAIN_MODEL.md` — `USER_EMAIL_VERIFIED` アクション・`UserEmailVerifiedEvent` 廃止
- `docs/requirements/REQUIREMENTS.md` — AUTH-01〜（3 ステップ OTP）更新、AUTH-03 廃止
- `docs/development/SENIOR_SETUP_PLAN.md` / `TEST_STRATEGY.md` / `FRONTEND_TEST_STRATEGY.md` — エンドポイント一覧・テスト例（MSW・結合テスト seed）更新

> 実装計画（`docs/implementation-plans/auth.md`）および `docker-compose.yml`・`build.gradle`・`application.yml` への Redis 設定追加は実装フェーズで対応する。

---

## 参照

- `REQUIREMENTS.md § 3.1.1 AUTH-01`（登録要件）
- [API_DESIGN.md § 2 認証](../docs/design/API_DESIGN.md)
- [SEQUENCE_FLOW.md § 1.1](../docs/design/SEQUENCE_FLOW.md)
- [SECURITY.md § 4 Rate Limiting](../docs/architecture/SECURITY.md)
- [ADR-004-jwt-strategy.md](./ADR-004-jwt-strategy.md)
