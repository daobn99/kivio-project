# データ定義書
# マルチベンダー型マーケットプレイス「Kivio」

**作成日：** 2026年5月24日  
**作成者：** Dao Nguyen  
**バージョン：** 1.0  
**参照元：** [DB_DESIGN.md](./DB_DESIGN.md)（物理設計・DDL）、[REQUIREMENTS.md](./REQUIREMENTS.md)

---

## 凡例

| 列名 | 説明 |
|---|---|
| 項目名（論理） | 人間が読む日本語名 |
| 項目名（物理） | DBカラム名（snake_case）/ JPAフィールド名（camelCase） |
| Java型 | JPAエンティティで使用するJava型 |
| PK | 主キー（◯） |
| FK | 外部キー（◯）。参照先は「参照先」列に記載 |
| UQ | UNIQUE制約（◯）。`◯*` は有効レコードのみ対象の部分UNIQUE |
| 必須 | NOT NULL（◯）。空欄はNULL許容 |
| 桁数 | VARCHAR は最大文字数、NUMERIC は精度。UUID・Integer等は `-` |
| デフォルト値 | DB定義のDEFAULT値。`-` は設定なし |
| 概要/備考 | 用途・制約・業務ルールなど |
| 参照先 | FK先の「テーブル論理名.項目名（論理）」 |

**Java型の対応表：**

| PostgreSQL型 | Java型 |
|---|---|
| UUID | `UUID` |
| VARCHAR / TEXT | `String` |
| INTEGER | `Integer` |
| BIGINT | `Long` |
| SMALLINT | `Short` |
| BOOLEAN | `Boolean` |
| NUMERIC(5,4) | `BigDecimal` |
| TIMESTAMPTZ | `Instant` |
| JSONB | `String`（JSON文字列） |
| INET | `String` |

---

## テーブル一覧

| # | テーブル論理名 | テーブル物理名 | ドメイン |
|---|---|---|---|
| 1 | [ユーザー](#1-ユーザー) | `users` | identity |
| 2 | [リフレッシュトークン](#2-リフレッシュトークン) | `refresh_tokens` | identity |
| 3 | [セラー申請](#3-セラー申請) | `seller_applications` | identity |
| 4 | [ショップ](#4-ショップ) | `shops` | catalog |
| 5 | [ショップ配送ポリシー](#5-ショップ配送ポリシー) | `shop_shipping_policies` | catalog |
| 6 | [カテゴリー](#6-カテゴリー) | `categories` | catalog |
| 7 | [商品](#7-商品) | `products` | catalog |
| 8 | [商品画像](#8-商品画像) | `product_images` | catalog |
| 9 | [配送先住所](#9-配送先住所) | `addresses` | order |
| 10 | [カート](#10-カート) | `carts` | order |
| 11 | [カート明細](#11-カート明細) | `cart_items` | order |
| 12 | [注文](#12-注文) | `orders` | order |
| 13 | [注文明細](#13-注文明細) | `order_items` | order |
| 14 | [決済](#14-決済) | `payments` | order |
| 15 | [レビュー](#15-レビュー) | `reviews` | review |
| 16 | [チャットルーム](#16-チャットルーム) | `chat_rooms` | messaging |
| 17 | [チャットメッセージ](#17-チャットメッセージ) | `chat_messages` | messaging |
| 18 | [通知](#18-通知) | `notifications` | notification |
| 19 | [お気に入り](#19-お気に入り) | `wishlists` | review |
| 20 | [プラットフォーム設定](#20-プラットフォーム設定) | `platform_configs` | platform |
| 21 | [監査ログ](#21-監査ログ) | `audit_logs` | audit |

---

## 1. ユーザー

**テーブル名：** `users`  
**概要：** プラットフォームに登録した全ユーザー。退会は論理削除（`deleted_at`）で管理し、90日後に個人情報を匿名化する。物理削除禁止。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| ユーザーID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| メールアドレス | email | `String` | | | ◯ | ◯ | 255 | - | ログインID。退会後は`deleted_{id}@kivio.invalid`に匿名化 | - |
| パスワードハッシュ | password_hash | `String` | | | | | 255 | null | BCrypt cost 12。Google OAuthユーザーはnull | - |
| Google ID | google_id | `String` | | | ◯ | | 255 | null | Google OAuthのsub値。メール登録ユーザーはnull。匿名化時にnull化 | - |
| 表示名 | display_name | `String` | | | | ◯ | 100 | `""` | プロフィール表示名 | - |
| アバター画像URL | avatar_url | `String` | | | | | - | null | Cloudinaryの画像URL | - |
| ロール | role | `String` | | | | ◯ | 20 | `ROLE_BUYER` | `ROLE_BUYER`（バイヤー）/ `ROLE_SELLER`（セラー）/ `ROLE_ADMIN`（管理者） | - |
| ステータス | status | `String` | | | | ◯ | 20 | `ACTIVE` | `ACTIVE`（有効）/ `INACTIVE`（無効・停止中） | - |
| メール確認済みフラグ | email_verified | `Boolean` | | | | ◯ | - | `false` | `true`のユーザーのみログイン可能 | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |
| ソフト削除日時 | deleted_at | `Instant` | | | | | - | null | null=有効。退会申請時に設定。90日後に匿名化バッチ実行 | - |

---

## 2. リフレッシュトークン

**テーブル名：** `refresh_tokens`  
**概要：** JWTリフレッシュトークンの管理テーブル。有効期限7日。ログアウト時に失効フラグを立てる。期限切れ後30日で物理削除バッチ実行。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| トークンID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| ユーザーID | user_id | `UUID` | | ◯ | | ◯ | - | - | トークン発行対象ユーザー | ユーザー.ユーザーID |
| トークンハッシュ | token_hash | `String` | | | ◯ | ◯ | 255 | - | SHA-256ハッシュ値。平文はDBに保存しない | - |
| 有効期限 | expires_at | `Instant` | | | | ◯ | - | - | 発行から7日後を設定 | - |
| 失効フラグ | revoked | `Boolean` | | | | ◯ | - | `false` | ログアウト時に`true`に更新 | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |

---

## 3. セラー申請

**テーブル名：** `seller_applications`  
**概要：** バイヤーがセラーになるための申請。却下後の再申請は新規レコードを作成する。過去の申請履歴は保持。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| セラー申請ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| 申請者ユーザーID | applicant_id | `UUID` | | ◯ | | ◯ | - | - | 申請したバイヤー | ユーザー.ユーザーID |
| 申請理由 | reason | `String` | | | | ◯ | - | - | セラー申請の動機・理由テキスト | - |
| 審査ステータス | status | `String` | | | | ◯ | 20 | `PENDING` | `PENDING`（審査待ち）/ `APPROVED`（承認）/ `REJECTED`（却下） | - |
| 審査者ユーザーID | reviewer_id | `UUID` | | ◯ | | | - | null | 審査した管理者。未審査中はnull | ユーザー.ユーザーID |
| 審査コメント | review_comment | `String` | | | | | - | null | 却下理由など。任意 | - |
| 審査日時 | reviewed_at | `Instant` | | | | | - | null | 審査完了日時。未審査中はnull | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

---

## 4. ショップ

**テーブル名：** `shops`  
**概要：** セラーが開設するストア。セラー1名につき必ず1つ（1:1）。ショップ名はアクティブなショップ間でのみ一意。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| ショップID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| オーナーユーザーID | owner_id | `UUID` | | ◯ | ◯ | ◯ | - | - | セラー1名につき1ショップ（1:1） | ユーザー.ユーザーID |
| ショップ名 | name | `String` | | | ◯* | ◯ | 100 | - | プラットフォーム全体で一意（有効レコード間のみ）。`◯*`=部分UNIQUE | - |
| 紹介文 | description | `String` | | | | | - | null | ショップの説明文 | - |
| ロゴ画像URL | logo_url | `String` | | | | | - | null | CloudinaryのロゴURL | - |
| ステータス | status | `String` | | | | ◯ | 20 | `ACTIVE` | `ACTIVE`（公開中）/ `INACTIVE`（非公開） | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |
| ソフト削除日時 | deleted_at | `Instant` | | | | | - | null | null=有効。ユーザー退会時に連動設定 | - |

> `◯*`：`deleted_at IS NULL` の行のみを対象とした部分UNIQUEインデックス（soft delete済みのショップ名は再利用可能）

---

## 5. ショップ配送ポリシー

**テーブル名：** `shop_shipping_policies`  
**概要：** ショップごとの送料設定。ショップに1:1で紐づく。送料はショップ全体に適用（商品単位ではない）。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 配送ポリシーID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| ショップID | shop_id | `UUID` | | ◯ | ◯ | ◯ | - | - | ショップに1:1で紐づく | ショップ.ショップID |
| 配送タイプ | shipping_type | `String` | | | | ◯ | 30 | - | `FREE`（送料無料）/ `FIXED`（固定送料）/ `CONDITIONAL_FREE`（条件付き無料） | - |
| 固定送料 | fixed_fee | `Integer` | | | | | - | null | FIXED時の送料（円）。他タイプはnull | - |
| 送料無料閾値 | free_threshold | `Integer` | | | | | - | null | CONDITIONAL_FREE時の無料条件金額（円）。他タイプはnull | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

---

## 6. カテゴリー

**テーブル名：** `categories`  
**概要：** 商品分類。管理者のみ作成・編集可能。最大2階層（親・子）。スラッグはアクティブなカテゴリー間でのみ一意。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| カテゴリーID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| カテゴリー名 | name | `String` | | | | ◯ | 100 | - | 表示用のカテゴリー名称 | - |
| スラッグ | slug | `String` | | | ◯* | ◯ | 100 | - | URL用識別子（例: `handmade-accessories`）。`◯*`=部分UNIQUE | - |
| 表示順序 | display_order | `Integer` | | | | ◯ | - | `0` | 一覧表示時のソート順（昇順） | - |
| 親カテゴリーID | parent_id | `UUID` | | ◯ | | | - | null | null=ルートカテゴリー。最大2階層 | カテゴリー.カテゴリーID |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |
| ソフト削除日時 | deleted_at | `Instant` | | | | | - | null | null=有効。管理者がカテゴリー削除時に設定 | - |

> `◯*`：`deleted_at IS NULL` の行のみを対象とした部分UNIQUEインデックス

---

## 7. 商品

**テーブル名：** `products`  
**概要：** セラーが出品する商品。論理削除は`status = DELETED`で表現（`deleted_at`カラムなし）。`status = DELETED`設定後180日で物理削除バッチ実行。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 商品ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| ショップID | shop_id | `UUID` | | ◯ | | ◯ | - | - | 商品を出品するショップ | ショップ.ショップID |
| カテゴリーID | category_id | `UUID` | | ◯ | | | - | null | カテゴリーのsoft delete後もnullを許容し商品は保持 | カテゴリー.カテゴリーID |
| 商品名 | name | `String` | | | | ◯ | 200 | - | 商品タイトル | - |
| 商品説明文 | description | `String` | | | | | - | null | 商品の詳細説明テキスト | - |
| 価格 | price | `Integer` | | | | ◯ | - | - | 販売価格（円単位、1以上） | - |
| 在庫数 | stock_quantity | `Integer` | | | | ◯ | - | `0` | 0の場合はACTIVEでも購入不可 | - |
| ステータス | status | `String` | | | | ◯ | 20 | `DRAFT` | `DRAFT`（下書き）/ `ACTIVE`（公開中）/ `INACTIVE`（非公開）/ `DELETED`（削除済み） | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

---

## 8. 商品画像

**テーブル名：** `product_images`  
**概要：** 商品に紐づく画像。最大5枚。`display_order = 0`がサムネイル。商品削除時はCASCADE削除（Cloudinary APIの呼び出しはアプリ層で事前実行）。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 商品画像ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| 商品ID | product_id | `UUID` | | ◯ | | ◯ | - | - | 紐づく商品。商品削除時はCASCADE削除 | 商品.商品ID |
| Cloudinary ID | cloudinary_id | `String` | | | | ◯ | 255 | - | Cloudinaryのpublic_id。削除時にCloudinary APIも呼び出す | - |
| 画像URL | image_url | `String` | | | | ◯ | - | - | CloudinaryのCDN配信URL | - |
| 表示順序 | display_order | `Integer` | | | | ◯ | - | `0` | 0が先頭（サムネイル）。並び替えはアプリ側で制御 | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |

---

## 9. 配送先住所

**テーブル名：** `addresses`  
**概要：** ユーザーが登録する配送先住所。複数登録可。注文確定時に`orders`テーブルへスナップショット保存するため、住所変更・削除の影響を受けない。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 住所ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| ユーザーID | user_id | `UUID` | | ◯ | | ◯ | - | - | 住所を登録したユーザー | ユーザー.ユーザーID |
| 受取人名 | recipient_name | `String` | | | | ◯ | 100 | - | 荷物受取人の氏名 | - |
| 郵便番号 | postal_code | `String` | | | | ◯ | 10 | - | 例: `150-0001` | - |
| 都道府県 | prefecture | `String` | | | | ◯ | 20 | - | 都道府県名 | - |
| 市区町村 | city | `String` | | | | ◯ | 100 | - | 市区町村名 | - |
| 番地・建物名 | address_line | `String` | | | | ◯ | 255 | - | 番地・建物名・部屋番号など | - |
| 電話番号 | phone_number | `String` | | | | ◯ | 20 | - | 配送連絡用電話番号 | - |
| デフォルトフラグ | is_default | `Boolean` | | | | ◯ | - | `false` | `true`はユーザーにつき1件のみ（アプリ側で制御） | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

---

## 10. カート

**テーブル名：** `carts`  
**概要：** ユーザーごとに1つのカート。ユーザー登録時に自動生成。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| カートID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| ユーザーID | user_id | `UUID` | | ◯ | ◯ | ◯ | - | - | ユーザーにつき1カート（1:1）。登録時に自動生成 | ユーザー.ユーザーID |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

---

## 11. カート明細

**テーブル名：** `cart_items`  
**概要：** カートに追加された商品明細。同一商品は1行で数量管理（複合UNIQUE制約）。商品物理削除時はCASCADE削除。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| カート明細ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| カートID | cart_id | `UUID` | | ◯ | | ◯ | - | - | 所属するカート。カート削除時はCASCADE削除 | カート.カートID |
| 商品ID | product_id | `UUID` | | ◯ | | ◯ | - | - | カートに追加した商品。商品物理削除時はCASCADE削除 | 商品.商品ID |
| 数量 | quantity | `Integer` | | | | ◯ | - | `1` | 1以上。在庫数を超える値は不可（アプリ側で検証） | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

> ※ `(カートID, 商品ID)` の複合UNIQUEあり（同一カートへの同一商品の重複追加を防止）

---

## 12. 注文

**テーブル名：** `orders`  
**概要：** 注文レコード。複数ショップの商品はショップ単位に分割して複数注文を生成。配送先は注文時点のスナップショットを保持。削除禁止（会計記録）。7年間保持後にPII項目のみ匿名化。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 注文ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| バイヤーユーザーID | buyer_id | `UUID` | | ◯ | | ◯ | - | - | 注文したバイヤー | ユーザー.ユーザーID |
| ショップID | shop_id | `UUID` | | ◯ | | ◯ | - | - | 注文先ショップ | ショップ.ショップID |
| 住所ID | address_id | `UUID` | | ◯ | | | - | null | 元住所への参照。住所削除後はnull（スナップショットは維持） | 配送先住所.住所ID |
| 配送先受取人名 | delivery_recipient_name | `String` | | | | ◯ | 100 | - | 注文時点のスナップショット | - |
| 配送先郵便番号 | delivery_postal_code | `String` | | | | ◯ | 10 | - | 注文時点のスナップショット | - |
| 配送先都道府県 | delivery_prefecture | `String` | | | | ◯ | 20 | - | 注文時点のスナップショット | - |
| 配送先市区町村 | delivery_city | `String` | | | | ◯ | 100 | - | 注文時点のスナップショット | - |
| 配送先番地・建物名 | delivery_address_line | `String` | | | | ◯ | 255 | - | 注文時点のスナップショット | - |
| 配送先電話番号 | delivery_phone_number | `String` | | | | ◯ | 20 | - | 注文時点のスナップショット | - |
| 注文ステータス | status | `String` | | | | ◯ | 30 | `PENDING_PAYMENT` | `PENDING_PAYMENT`（決済待ち）/ `PAYMENT_CONFIRMED`（決済完了）/ `PROCESSING`（処理中）/ `SHIPPED`（発送済み）/ `DELIVERED`（配達済み）/ `COMPLETED`（完了）/ `CANCELLED`（キャンセル） | - |
| 商品小計 | subtotal | `Integer` | | | | ◯ | - | - | 商品合計金額（円） | - |
| 送料 | shipping_fee | `Integer` | | | | ◯ | - | - | 送料（円） | - |
| 合計金額 | total_amount | `Integer` | | | | ◯ | - | - | subtotal + shipping_fee（円） | - |
| 手数料率 | commission_rate | `BigDecimal` | | | | ◯ | (5,4) | - | 注文確定時のplatform_configsスナップショット（例: `0.0500`） | - |
| 手数料額 | commission_amount | `Integer` | | | | ◯ | - | - | total_amount × commission_rate（円） | - |
| セラー取り分 | seller_amount | `Integer` | | | | ◯ | - | - | total_amount − commission_amount（円） | - |
| Stripe決済意図ID | stripe_payment_intent_id | `String` | | | | | 255 | null | Stripe PaymentIntentのID | - |
| キャンセル日時 | cancelled_at | `Instant` | | | | | - | null | キャンセル完了時に設定 | - |
| キャンセル理由 | cancel_reason | `String` | | | | | - | null | キャンセル理由テキスト | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

---

## 13. 注文明細

**テーブル名：** `order_items`  
**概要：** 注文に含まれる商品の明細。商品情報（名前・価格・画像）は注文時点のスナップショットとして保存。削除・更新禁止（会計記録）。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 注文明細ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| 注文ID | order_id | `UUID` | | ◯ | | ◯ | - | - | 紐づく注文 | 注文.注文ID |
| 商品ID | product_id | `UUID` | | ◯ | | | - | null | 商品物理削除後はnull（スナップショットで情報保持） | 商品.商品ID |
| 商品名（スナップショット） | product_name | `String` | | | | ◯ | 200 | - | 注文時点の商品名 | - |
| 商品画像URL（スナップショット） | product_image_url | `String` | | | | | - | null | 注文時点のサムネイルURL | - |
| 単価（スナップショット） | unit_price | `Integer` | | | | ◯ | - | - | 注文時点の販売価格（円） | - |
| 数量 | quantity | `Integer` | | | | ◯ | - | - | 購入数量（1以上） | - |
| 小計 | subtotal | `Integer` | | | | ◯ | - | - | unit_price × quantity（円） | - |
| レビュー済みフラグ | is_reviewed | `Boolean` | | | | ◯ | - | `false` | レビュー投稿済みなら`true` | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |

---

## 14. 決済

**テーブル名：** `payments`  
**概要：** Stripe決済レコード。Stripe Webhookの`payment_intent.succeeded`受信後に生成。削除禁止（会計記録）。注文と1:1。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 決済ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| 注文ID | order_id | `UUID` | | ◯ | ◯ | ◯ | - | - | 注文と1:1 | 注文.注文ID |
| Stripe決済ID | stripe_payment_id | `String` | | | ◯ | ◯ | 255 | - | Stripe PaymentIntentのID | - |
| 金額 | amount | `Integer` | | | | ◯ | - | - | 決済金額（円） | - |
| 通貨 | currency | `String` | | | | ◯ | 3 | `JPY` | 通貨コード（JPYのみ） | - |
| 決済ステータス | status | `String` | | | | ◯ | 30 | - | `PENDING`（処理中）/ `SUCCEEDED`（成功）/ `FAILED`（失敗）/ `REFUNDED`（返金済み） | - |
| Stripe返金ID | stripe_refund_id | `String` | | | | | 255 | null | Stripe Refunds APIのrefund ID。キャンセル時に設定 | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

---

## 15. レビュー

**テーブル名：** `reviews`  
**概要：** 商品レビュー。注文ステータスが`COMPLETED`になった注文明細に対して1件のみ投稿可能。作成から1年後に物理削除バッチ実行。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| レビューID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| 注文明細ID | order_item_id | `UUID` | | ◯ | ◯ | ◯ | - | - | 注文明細ごとに1件のみ（1:1） | 注文明細.注文明細ID |
| 商品ID | product_id | `UUID` | | ◯ | | | - | null | 商品削除後はnull。レビュー自体は保持 | 商品.商品ID |
| レビュアーユーザーID | reviewer_id | `UUID` | | ◯ | | | - | null | ユーザー匿名化後はnull | ユーザー.ユーザーID |
| 評価 | rating | `Short` | | | | ◯ | - | - | 1〜5の整数 | - |
| コメント | comment | `String` | | | | | - | null | レビューテキスト（任意） | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

---

## 16. チャットルーム

**テーブル名：** `chat_rooms`  
**概要：** バイヤーとショップの1対1チャット。同一の組み合わせに対してルームは1つのみ（複合UNIQUE制約）。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| チャットルームID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| バイヤーユーザーID | buyer_id | `UUID` | | ◯ | | ◯ | - | - | チャットを開始したバイヤー | ユーザー.ユーザーID |
| ショップID | shop_id | `UUID` | | ◯ | | ◯ | - | - | 対象ショップ | ショップ.ショップID |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

> ※ `(バイヤーユーザーID, ショップID)` の複合UNIQUEあり

---

## 17. チャットメッセージ

**テーブル名：** `chat_messages`  
**概要：** チャットルーム内のメッセージ。テキストのみ（画像送信はMVPスコープ外）。作成から1年後に物理削除バッチ実行。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| チャットメッセージID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| チャットルームID | chat_room_id | `UUID` | | ◯ | | ◯ | - | - | 所属するチャットルーム | チャットルーム.チャットルームID |
| 送信者ユーザーID | sender_id | `UUID` | | ◯ | | | - | null | 送信者。ユーザー匿名化後はnull | ユーザー.ユーザーID |
| 本文 | body | `String` | | | | ◯ | - | - | メッセージ本文 | - |
| 既読日時 | read_at | `Instant` | | | | | - | null | null=未読。相手が開封した日時 | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |

---

## 18. 通知

**テーブル名：** `notifications`  
**概要：** アプリ内通知。`expires_at`（作成から90日後）経過後は非表示とし、毎日のバッチで物理削除する。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 通知ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| ユーザーID | user_id | `UUID` | | ◯ | | ◯ | - | - | 通知を受け取るユーザー | ユーザー.ユーザーID |
| 通知タイプ | type | `String` | | | | ◯ | 50 | - | `ORDER_CONFIRMED`（注文確定）/ `ORDER_STATUS_CHANGED`（注文ステータス変更）/ `ORDER_CANCELLED`（注文キャンセル）/ `NEW_MESSAGE`（新着メッセージ）/ `SELLER_APPLICATION_APPROVED`（セラー申請承認）/ `SELLER_APPLICATION_REJECTED`（セラー申請却下） | - |
| タイトル | title | `String` | | | | ◯ | 200 | - | 通知タイトル | - |
| 本文 | body | `String` | | | | ◯ | - | - | 通知内容テキスト | - |
| 既読フラグ | is_read | `Boolean` | | | | ◯ | - | `false` | 既読で`true` | - |
| 既読日時 | read_at | `Instant` | | | | | - | null | ユーザーが既読にした日時 | - |
| 関連エンティティ種別 | related_entity_type | `String` | | | | | 50 | null | `ORDER`（注文）/ `SELLER_APPLICATION`（セラー申請）/ `CHAT_ROOM`（チャットルーム）等。フロントのリンク生成に使用 | - |
| 関連エンティティID | related_entity_id | `UUID` | | | | | - | null | 関連エンティティのID | - |
| 有効期限 | expires_at | `Instant` | | | | ◯ | - | - | 作成から90日後。期限後は非表示→物理削除バッチ | - |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | 既読更新時などに自動更新 | - |

---

## 19. お気に入り

**テーブル名：** `wishlists`  
**概要：** ユーザーがお気に入り登録した商品。同一ユーザー・商品の組み合わせは1件のみ（複合UNIQUE制約）。商品削除時はCASCADE削除。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| お気に入りID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| ユーザーID | user_id | `UUID` | | ◯ | | ◯ | - | - | お気に入りを登録したユーザー | ユーザー.ユーザーID |
| 商品ID | product_id | `UUID` | | ◯ | | ◯ | - | - | お気に入り登録した商品。商品削除時はCASCADE削除 | 商品.商品ID |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |

> ※ `(ユーザーID, 商品ID)` の複合UNIQUEあり

---

## 20. プラットフォーム設定

**テーブル名：** `platform_configs`  
**概要：** プラットフォーム全体の設定をKVS形式で管理。管理者のみ更新可能。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| 設定ID | id | `UUID` | ◯ | | | ◯ | - | auto | 主キー | - |
| 設定キー | config_key | `String` | | | ◯ | ◯ | 100 | - | `UPPER_SNAKE_CASE`。例: `COMMISSION_RATE`、`MAINTENANCE_MODE` | - |
| 設定値 | config_value | `String` | | | | ◯ | - | - | 設定値（文字列形式。型解釈はアプリ側で行う） | - |
| 説明 | description | `String` | | | | | - | null | 設定の用途説明 | - |
| 更新者ユーザーID | updated_by | `UUID` | | ◯ | | | - | null | 設定を変更した管理者。削除後はnull | ユーザー.ユーザーID |
| 作成日時 | created_at | `Instant` | | | | ◯ | - | auto | レコード作成日時（UTC） | - |
| 更新日時 | updated_at | `Instant` | | | | ◯ | - | auto | レコード更新日時（UTC、自動更新） | - |

**初期データ：**

| 設定キー | 設定値 | 説明 |
|---|---|---|
| `COMMISSION_RATE` | `0.0500` | プラットフォーム手数料率（5%） |
| `MAINTENANCE_MODE` | `false` | メンテナンスモードフラグ |

---

## 21. 監査ログ

**テーブル名：** `audit_logs`  
**概要：** 認証イベント・管理者操作・重要な状態変更を追記専用で記録する。UPDATE/DELETE禁止。月別パーティションで管理し、1年経過後のパーティションをDROPして削除する。主キーはパーティションキー（`created_at`）を含む複合PK。

| 項目名（論理） | 項目名（物理） | Java型 | PK | FK | UQ | 必須 | 桁数 | デフォルト値 | 概要/備考 | 参照先 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|---|---|
| ログID | id | `Long` | ◯ | | | ◯ | - | auto(seq) | 主キー。パーティション対応のためBIGINT+シーケンス | - |
| 相関ID | correlation_id | `UUID` | | | | ◯ | - | - | リクエスト単位のID（MDC経由）。同一リクエスト内の複数ログを紐づける | - |
| 操作者ユーザーID | actor_id | `UUID` | | | | | - | null | 操作したユーザー。バッチ・システム処理はnull | - |
| 操作者ロール | actor_role | `String` | | | | | 20 | null | 操作時点のロールスナップショット | - |
| 操作者メール | actor_email | `String` | | | | | 255 | null | 操作時点のメールスナップショット | - |
| アクション | action | `String` | | | | ◯ | 100 | - | `UPPER_SNAKE_CASE`。例: `USER_REGISTERED`、`ORDER_CANCELLED` | - |
| エンティティ種別 | entity_type | `String` | | | | | 50 | null | 操作対象のエンティティ種別（`USER` / `ORDER`等） | - |
| エンティティID | entity_id | `UUID` | | | | | - | null | 操作対象エンティティのID | - |
| 結果 | outcome | `String` | | | | ◯ | 10 | `SUCCESS` | `SUCCESS`（成功）/ `FAILURE`（失敗） | - |
| IPアドレス | ip_address | `String` | | | | | - | null | リクエスト元IPアドレス | - |
| 変更前の値 | old_value | `String` | | | | | - | null | 変更前の状態（JSON形式）。管理者操作・ステータス変更時に記録 | - |
| 変更後の値 | new_value | `String` | | | | | - | null | 変更後の状態（JSON形式）。管理者操作・ステータス変更時に記録 | - |
| エラーメッセージ | error_message | `String` | | | | | - | null | `outcome = FAILURE`の場合のエラー詳細 | - |
| 作成日時 | created_at | `Instant` | ◯ | | | ◯ | - | auto | 記録日時（UTC）。月別パーティションキー | - |

> ※ 主キーは `(id, created_at)` の複合PK（PostgreSQLのパーティションテーブルはパーティションキーをPKに含める必要がある）  
> ※ `actor_id` は外部キーを設定しない（audit_logsの追記性を保ち、ユーザー削除の影響を受けないようにするため）

---

**以上**

*本データ定義書は物理設計の詳細（DDL・インデックス・パーティション）を [DB_DESIGN.md](./DB_DESIGN.md) に委譲しています。スキーマ変更時は両ドキュメントを同時に更新してください。*
