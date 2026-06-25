# バリデーションルール & エラーメッセージ定義書
# マルチベンダー型マーケットプレイス「Kivio」

**作成日：** 2026年6月12日  
**作成者：** Dao Nguyen  
**バージョン：** 1.0（Phase 2 Auth/User スコープ）  
**参照元：** [ERROR_CODES.md](./ERROR_CODES.md)、[API_DESIGN.md](./API_DESIGN.md)、[DATA_DICTIONARY.md](./DATA_DICTIONARY.md)、[REQUIREMENTS.md](../requirements/REQUIREMENTS.md)

---

## 0. このドキュメントの責任分界

エラー文言には2系統あり、**所有ドキュメントを分離する**。本書が扱うのは **(A) のみ**。

| 種別 | 例 | 発生タイミング | 所有ドキュメント |
|---|---|---|---|
| **(A) 入力バリデーション** | 「パスワードは8文字以上で入力してください」 | 送信前 / フィールド単位（クライアント側 zod・サーバ側 Bean Validation） | **本書** |
| **(B) サーバー業務エラー** | `EMAIL_ALREADY_REGISTERED` →「このメールアドレスは既に使用されています」 | サーバ処理後（エラーコード） | [ERROR_CODES.md](./ERROR_CODES.md)（`title` / `detail`） |
| **(B) のUI表示方法** | Toast / インライン / リダイレクト | クライアント側で `code` により分岐 | [FRONTEND_API_CONTRACT.md §9（エラーコード → UI 表示方針）](./frontend/FRONTEND_API_CONTRACT.md) |

> **原則:** 同一のメッセージ文字列を2つのドキュメントに書かない。フィールド入力の制約と文言は本書、エラーコードの文言は ERROR_CODES.md が単一の正とする。サーバ側の Bean Validation 失敗は `VALIDATION_FAILED`（422）として `errors[]` にフィールド別詳細を返す（[ERROR_CODES.md §1.2](./ERROR_CODES.md) 参照）。
>
> **「定義」と「実装メカニズム」の区別:** 本書は (A) の**定義**（制約値・文言）を持つ。サーバの `VALIDATION_FAILED.errors[]` を react-hook-form の `setError` へ流し込む配線・フィールド名対応は [FRONTEND_API_CONTRACT.md §10（フォームバリデーションエラーのマッピング）](./frontend/FRONTEND_API_CONTRACT.md) が持つ（実装の責務）。両者は補完関係で重複しない。

---

## 1. UX ライティング原則

入力バリデーションのメッセージ文言は、以下のルールに統一する。

### 1.1 文体

| 状態 | パターン | 例 |
|---|---|---|
| 必須未入力 | `{ラベル}を入力してください` | メールアドレスを入力してください |
| 形式不正 | `{ラベル}の形式が正しくありません` | メールアドレスの形式が正しくありません |
| 文字数（下限） | `{ラベル}は{n}文字以上で入力してください` | パスワードは8文字以上で入力してください |
| 文字数（上限） | `{ラベル}は{n}文字以内で入力してください` | 表示名は100文字以内で入力してください |
| 不一致 | `{ラベル}が一致しません` | パスワードと確認用パスワードが一致しません |

- 敬体（です・ます）で統一。「〜せよ」「〜が必要」等の硬い表現は使わない。
- 句点（。）は付けない（フォーム直下のインライン表示のため）。
- ネガティブな断定（「無効です」）より、次の行動を促す表現（「〜を入力してください」）を優先。
- 技術用語（regex・トークン・null 等）をユーザー向け文言に出さない。

### 1.2 用語統一表

UI 表記は必ず左列に統一する。表記揺れを禁止する。

| 統一表記 | 禁止表記 |
|---|---|
| メールアドレス | Eメール / メール / メアド / email |
| パスワード | パスワード（確認） は「パスワード（確認用）」に統一 |
| 認証コード | OTP / ワンタイムパスワード / 確認コード |
| 表示名 | ユーザー名 / ニックネーム / お名前 |
| ログイン / ログアウト | サインイン / サインアウト |
| 新規登録 | サインアップ / 会員登録 |

---

## 2. フィールド別バリデーション規約（Phase 2: Auth / User）

各フィールドについて、**バックエンド（Bean Validation）とフロントエンド（zod）が同じ行を見て実装する**。両列が一致していることがレビュー観点。

### 2.1 共通フィールド

| フィールド | 必須 | 制約 | 根拠 | Bean Validation | zod | エラー文言 |
|---|---|---|---|---|---|---|
| `email` | ◯ | メール形式 / 255文字以内 | DATA_DICTIONARY `users.email`（VARCHAR 255・ログインID） | `@NotBlank @Email @Size(max=255)` | `z.email(...).max(255)` | 未入力:「メールアドレスを入力してください」 / 形式:「メールアドレスの形式が正しくありません」 |
| `password`<br>（新規登録 / `newPassword`） | ◯ | **8〜72文字**（複雑性要件なし） | 下限8: REQUIREMENTS AUTH-01-C / 上限72: BCrypt cost 12 の入力上限（72バイト超は切り詰められる） | `@NotBlank @Size(min=8, max=72)` | `z.string().min(8).max(72)` | 下限:「パスワードは8文字以上で入力してください」 / 上限:「パスワードは72文字以内で入力してください」 |
| `password`<br>（ログイン） | ◯ | **必須のみ**（長さ検証しない） | ログインは強度ポリシーを課す場ではない。不一致は `INVALID_CREDENTIALS`（401）で返す。`currentPassword`（§2.3）と同思想 | `@NotBlank` | `z.string().min(1)` | 「パスワードを入力してください」 |
| `passwordConfirm` | ◯ | `password` と一致 | 入力ミス防止 | （サーバ検証は任意・主にフロント責務） | `.refine(p === pc, path:['passwordConfirm'])` | 「パスワードと確認用パスワードが一致しません」 |
| `displayName` | ◯ | 1〜100文字 | DATA_DICTIONARY `users.display_name`（VARCHAR 100・必須）。空表示名による画面崩れ防止のため登録時必須 | `@NotBlank @Size(max=100)` | `z.string().trim().min(1).max(100)` | 未入力:「表示名を入力してください」 / 上限:「表示名は100文字以内で入力してください」 |
| `avatarUrl` | 任意 | URL形式 | PATCH /users/me（Cloudinary URL） | `@URL` | `z.url().optional()` | 「URLの形式が正しくありません」 |

### 2.2 OTP 登録フロー

| フィールド | 必須 | 制約 | 根拠 | Bean Validation | zod | エラー文言 |
|---|---|---|---|---|---|---|
| `otp` | ◯ | 6桁の数字（`^\d{6}$`） | API_DESIGN verify-otp（6桁数値OTP） | `@NotBlank @Pattern(regexp="^\\d{6}$")` | `z.string().regex(/^\d{6}$/)` | 「認証コードは6桁の数字で入力してください」 |
| `registrationToken` | ◯ | 不透明トークン（UUID v4） | verify-otp が発行。**フロントは形式検証しない**（サーバが Redis で検証） | `@NotBlank` | サーバ保持のため**フォーム対象外** | （ユーザー入力ではないため文言なし。失効時は `REGISTRATION_SESSION_INVALID` で処理） |

### 2.3 パスワード変更（`PATCH /users/me/password`）

| フィールド | 必須 | 制約 | 根拠 | Bean Validation | zod | エラー文言 |
|---|---|---|---|---|---|---|
| `currentPassword` | ◯ | 必須のみ（長さ検証しない） | 既存値との照合。不一致は `PASSWORD_CHANGE_FAILED`（400）で返す | `@NotBlank` | `z.string().min(1)` | 「現在のパスワードを入力してください」 |
| `newPassword` | ◯ | **8〜72文字**（`password` と同一規約） | §2.1 参照 | `@NotBlank @Size(min=8, max=72)` | `z.string().min(8).max(72)` | §2.1 の `password` と同一 |

---

## 3. 確定事項（決定済み・2026-06-12）

ドラフト段階で論点だった項目の決定結果。

| # | 項目 | 決定 | 補足 |
|---|---|---|---|
| 1 | パスワード**上限** | **72文字** | BCrypt cost 12 の入力上限（72バイト超は黙って切り詰められる）を明示的に弾く。`API_DESIGN.md` / `validations/auth.ts` / バックエンド DTO に反映する |
| 2 | パスワード**複雑性** | **要件なし**（長さのみ） | REQUIREMENTS が「8文字以上」しか定めないため。英数字・記号の必須化は行わない（NIST SP 800-63B 寄りの方針） |
| 3 | パスワード長の単位 | UI 文言は「文字」、内部制約は実質バイト | ASCII 前提のため通常は乖離しない。多バイト文字許容の是非を将来 SECURITY.md で明記する |

---

## 4. 実装同期状況（2026-06-12 時点）

`kivio-frontend/src/lib/validations/auth.ts` は本書 v1.0 に同期済み。

| フィールド | 旧実装 | 本書の規約 | 状態 |
|---|---|---|---|
| `password` | `min(8)` のみ | `min(8).max(72)` | ✅ `.max(72)` 追加済み |
| ログイン `password` | `min(8)` | 必須のみ `min(1)` | ✅ 反映済み |
| `email` | `z.email(...)` 上限なし | `.max(255)` | ✅ `.max(255)` 追加済み |
| 文言（password 下限） | 「パスワードは8文字以上です」 | 「パスワードは8文字以上で入力してください」 | ✅ 統一済み |
| 文言（email 形式） | 「有効なメールアドレスを入力してください」 | 「メールアドレスの形式が正しくありません」 | ✅ 差し替え済み |
| 文言（otp） | 「認証コードは6桁の数字です」 | 「認証コードは6桁の数字で入力してください」 | ✅ 統一済み |

**バックエンド（Bean Validation）同期状況:**

| DTO | フィールド | 状態 |
|---|---|---|
| `CompleteRegistrationRequest` | `password` | ✅ `@Size(min=8, max=72)` に修正済み（旧 `max=100`） |
| `CompleteRegistrationRequest` | `email` / `passwordConfirm` | ✅ 既に整合（255 / `@AssertTrue` 一致チェック） |
| `CompleteRegistrationRequest` | `displayName` | ✅ `@NotBlank @Size(max=100)` に変更済み（任意→必須） |
| `RequestOtpRequest` / `CheckEmailRequest` / `LoginRequest` | `email` | ✅ `@Email @Size(max=255)` で整合済み |
| `VerifyOtpRequest` | `otp` | ✅ `@Pattern("\\d{6}")` で整合済み |
| `ChangePasswordRequest`（`PATCH /users/me/password`） | `currentPassword` / `newPassword` | ✅ `currentPassword`=`@NotBlank` / `newPassword`=`@NotBlank @Size(min=8, max=72)`（2026-06-23・U-01） |
| `UpdateProfileRequest`（`PATCH /users/me`） | `displayName` / `avatarUrl` | ✅ 部分更新のため `displayName`=`@Size(min=1, max=100)`（`null` 許容＝未送信）/ `avatarUrl`=`@URL`（2026-06-23・U-01） |

---

## 5. 配送先住所（`POST` / `PATCH /users/me/addresses`）

`feature/user-profile` で追記（2026-06-23）。住所 CRUD のフィールド制約。`DB_DESIGN.md §3.10` / `DATA_DICTIONARY` の `addresses` を根拠とする。

> **PATCH（部分更新）と POST（新規作成）の差:** 列「必須（POST）」は新規作成時の必須性。`PATCH /users/me/addresses/{id}` は部分更新のため**送信フィールドのみ検証**し、未送信（`null`）は不変とする（`@NotBlank` は POST 用 DTO のみ。`PATCH` 用 DTO は `@Size`/`@Pattern` のみで `null` を許容）。

| フィールド | 必須（POST） | 制約 | 根拠 | Bean Validation（POST） | zod | エラー文言 |
|---|---|---|---|---|---|---|
| `recipientName` | ◯ | 1〜100文字 | DATA_DICTIONARY `addresses.recipient_name`（VARCHAR 100） | `@NotBlank @Size(max=100)` | `z.string().trim().min(1).max(100)` | 未入力:「宛名を入力してください」 / 上限:「宛名は100文字以内で入力してください」 |
| `postalCode` | ◯ | 郵便番号形式（`^\d{3}-?\d{4}$`・ハイフン任意） | DATA_DICTIONARY `addresses.postal_code`（VARCHAR 10）。日本の7桁郵便番号 | `@NotBlank @Pattern(regexp="^\\d{3}-?\\d{4}$")` | `z.string().regex(/^\d{3}-?\d{4}$/)` | 未入力:「郵便番号を入力してください」 / 形式:「郵便番号は7桁の数字で入力してください」 |
| `prefecture` | ◯ | 1〜20文字 | DATA_DICTIONARY `addresses.prefecture`（VARCHAR 20）。都道府県名 | `@NotBlank @Size(max=20)` | `z.string().trim().min(1).max(20)` | 未入力:「都道府県を選択してください」 / 上限:「都道府県は20文字以内で入力してください」 |
| `city` | ◯ | 1〜100文字 | DATA_DICTIONARY `addresses.city`（VARCHAR 100）。市区町村 | `@NotBlank @Size(max=100)` | `z.string().trim().min(1).max(100)` | 未入力:「市区町村を入力してください」 / 上限:「市区町村は100文字以内で入力してください」 |
| `addressLine` | ◯ | 1〜255文字 | DATA_DICTIONARY `addresses.address_line`（VARCHAR 255）。番地・建物名 | `@NotBlank @Size(max=255)` | `z.string().trim().min(1).max(255)` | 未入力:「番地・建物名を入力してください」 / 上限:「番地・建物名は255文字以内で入力してください」 |
| `phoneNumber` | ◯ | 電話番号形式（`^0\d{1,4}-?\d{1,4}-?\d{4}$`・ハイフン任意） | DATA_DICTIONARY `addresses.phone_number`（VARCHAR 20）。日本の固定/携帯番号 | `@NotBlank @Pattern(regexp="^0\\d{1,4}-?\\d{1,4}-?\\d{4}$")` | `z.string().regex(/^0\d{1,4}-?\d{1,4}-?\d{4}$/)` | 未入力:「電話番号を入力してください」 / 形式:「電話番号の形式が正しくありません」 |
| `isDefault` | 任意 | 真偽値（既定 `false`） | DATA_DICTIONARY `addresses.is_default`。ユーザーにつき 1 件のみ `true`（複数指定時はアプリ側で他住所を `false` に落とす） | `boolean`（検証なし） | `z.boolean().default(false)` | （ユーザー入力エラーなし） |

**確定事項（2026-06-23・OQ-5）:**

| # | 項目 | 決定 |
|---|---|---|
| 1 | `postalCode` 正規表現 | `^\d{3}-?\d{4}$`（ハイフン有無いずれも許容。保存値はそのまま） |
| 2 | `phoneNumber` 正規表現 | `^0\d{1,4}-?\d{1,4}-?\d{4}$`（先頭 0・ハイフン任意） |
| 3 | 一覧の並び順 | **デフォルト住所を先頭 → `created_at` 昇順** |

---

## 6. 今後の拡張（Phase 3 以降）

以下のフィールドは別フェーズで本書に追記する。

- **SellerApplication**: `shopName`（プラットフォーム全体で一意・100文字）、`category`、`description`
- **Shop**: `name`（部分UNIQUE・100文字）、ショップ説明、ロゴURL
- **Product**: `name`、`price`（整数・1円以上）、`stock`、`description`、`images[]`（最大5枚）
- **Review**: `rating`（1〜5）、`comment`

---

**以上（ドラフト）**

*このドキュメントはフィールド制約と入力バリデーション文言の単一の正とする。制約を変更する場合は本書・`API_DESIGN.md`・フロント（zod）・バックエンド（Bean Validation）の4箇所を同時に更新すること。*
</content>
</invoke>
