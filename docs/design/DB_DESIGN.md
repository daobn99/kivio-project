# DB設計書
# マルチベンダー型マーケットプレイス「Kivio」

**作成日：** 2026年5月24日  
**作成者：** Dao Nguyen  
**バージョン：** 1.0  
**ステータス：** 確定  
**参照元：** [REQUIREMENTS.md § 7. データモデル](./REQUIREMENTS.md)、[REQUIREMENTS.md § 15. データ保持ポリシー](./REQUIREMENTS.md)

---

## 目次

1. [設計原則](#1-設計原則)
2. [テーブル一覧](#2-テーブル一覧)
3. [テーブル詳細定義](#3-テーブル詳細定義)
4. [インデックス設計](#4-インデックス設計)
5. [パーティショニング設計](#5-パーティショニング設計)
6. [Flywayマイグレーション規則](#6-flywayマイグレーション規則)
7. [初期データ（シード）](#7-初期データシード)
8. [ER図](#8-er図)

---

## 1. 設計原則

### 1.1 主キー

- すべてのテーブルで **UUID**（`gen_random_uuid()`）を採用する
- `audit_logs` のみ **BIGINT + シーケンス**（高頻度追記のためインデックスサイズ最小化）
- 外部キーから参照されるIDはすべて `UUID`

### 1.2 タイムスタンプ

- 日時カラムはすべて **`TIMESTAMPTZ`（タイムゾーン付き）** を使用する
- アプリケーション側で **UTC** で書き込む
- `created_at` はすべてのテーブルに設ける
- `updated_at` は行が更新されうるテーブルに設ける（追記専用の `order_items`・`chat_messages`・`audit_logs`・`product_images`・`wishlists` は不要）
- `updated_at` は Spring Data JPA の `@LastModifiedDate`（`@EnableJpaAuditing`）で自動更新

### 1.3 金額

- 金額カラムはすべて **`INTEGER`（円単位）** で保存する（小数なし）
- 手数料率のみ **`NUMERIC(5,4)`**（例: `0.0500` = 5%）

### 1.4 ステータス・列挙値

- `VARCHAR` + アプリケーション側でバリデーション（PostgreSQL ENUM は ALTER が困難なため不採用）
- 有効値はコメントで明記する

### 1.5 Soft Delete

| エンティティ | 実装方式 | 備考 |
|---|---|---|
| `users` / `shops` / `categories` | `deleted_at TIMESTAMPTZ`（NULL = 有効） | `@SQLRestriction("deleted_at IS NULL")` |
| `products` | `status = 'DELETED'` | `deleted_at` カラムなし |
| `orders` / `payments` | 削除不可 | 会計記録として永続保存 |

### 1.6 文字列長の基準

| 用途 | 型 |
|---|---|
| メールアドレス | `VARCHAR(255)` |
| 名前・タイトル系 | `VARCHAR(100)` or `VARCHAR(200)` |
| URLフィールド | `TEXT` |
| 説明文・本文 | `TEXT` |
| ステータス・区分 | `VARCHAR(30)` |
| 外部サービスID（Stripe等） | `VARCHAR(255)` |

### 1.7 全文検索

日本語テキストには **`pg_trgm`拡張** + GINインデックスを使用する（`tsvector`は日本語形態素解析が非対応のため）。

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

---

## 2. テーブル一覧

| # | テーブル名 | 論理名 | ドメイン | Soft Delete |
|---|---|---|---|---|
| 1 | `users` | ユーザー | identity | `deleted_at` |
| 2 | `refresh_tokens` | リフレッシュトークン | identity | - |
| 3 | `seller_applications` | セラー申請 | identity | - |
| 4 | `shops` | ショップ | catalog | `deleted_at` |
| 5 | `shop_shipping_policies` | ショップ配送ポリシー | catalog | - |
| 6 | `categories` | カテゴリー | catalog | `deleted_at` |
| 7 | `products` | 商品 | catalog | `status='DELETED'` |
| 8 | `product_images` | 商品画像 | catalog | - |
| 9 | `addresses` | 配送先住所 | order | - |
| 10 | `carts` | カート | order | - |
| 11 | `cart_items` | カート明細 | order | - |
| 12 | `orders` | 注文 | order | 削除不可 |
| 13 | `order_items` | 注文明細 | order | 削除不可 |
| 14 | `payments` | 決済 | order | 削除不可 |
| 15 | `reviews` | レビュー | review | - |
| 16 | `chat_rooms` | チャットルーム | messaging | - |
| 17 | `chat_messages` | チャットメッセージ | messaging | - |
| 18 | `notifications` | 通知 | notification | `expires_at` |
| 19 | `wishlists` | お気に入り | review | - |
| 20 | `platform_configs` | プラットフォーム設定 | platform | - |
| 21 | `audit_logs` | 監査ログ | audit | 削除禁止（期限後DROP） |

---

## 3. テーブル詳細定義

---

### 3.1 users（ユーザー）

```sql
CREATE TABLE users (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email            VARCHAR(255) NOT NULL,
  password_hash    VARCHAR(255),                          -- NULL: Google OAuth ユーザー
  google_id        VARCHAR(255),                          -- NULL: メール登録ユーザー
  display_name     VARCHAR(100) NOT NULL DEFAULT '',
  avatar_url       TEXT,
  role             VARCHAR(20)  NOT NULL DEFAULT 'ROLE_BUYER',
                                                          -- ROLE_BUYER | ROLE_SELLER | ROLE_ADMIN
  status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                                                          -- ACTIVE | INACTIVE
  email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  deleted_at       TIMESTAMPTZ,                           -- NULL: 有効, NOT NULL: soft delete済み

  CONSTRAINT users_email_unique   UNIQUE (email),
  CONSTRAINT users_google_id_unique UNIQUE (google_id),
  CONSTRAINT users_role_check     CHECK (role IN ('ROLE_BUYER', 'ROLE_SELLER', 'ROLE_ADMIN')),
  CONSTRAINT users_status_check   CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

COMMENT ON TABLE  users IS 'ユーザーアカウント。退会後は匿名化（90日後バッチ）。物理削除禁止。';
COMMENT ON COLUMN users.password_hash IS 'BCrypt cost factor 12。Google OAuthユーザーはNULL。';
COMMENT ON COLUMN users.role IS 'ROLE_BUYER（デフォルト）| ROLE_SELLER（セラー申請承認後）| ROLE_ADMIN（手動付与）';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete タイムスタンプ。NULL=有効。90日後に匿名化バッチ実行。';
```

**業務ルール：**
- `email` と `google_id` の両方がある場合はアカウント統合済みユーザー
- `password_hash` と `google_id` が両方 NULL になることはない（アプリ側で保証）
- `deleted_at IS NOT NULL` のユーザーは `@SQLRestriction` で通常クエリから自動除外
- 匿名化バッチでは `email`・`display_name`・`avatar_url`・`password_hash`・`google_id` をすべて上書きまたは NULL 化する（`google_id = NULL` にすることで UNIQUE スロットを解放）

---

### 3.2 refresh_tokens（リフレッシュトークン）

```sql
CREATE TABLE refresh_tokens (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID        NOT NULL REFERENCES users(id),
  token_hash   VARCHAR(255) NOT NULL,   -- SHA-256ハッシュ（平文は保持しない）
  expires_at   TIMESTAMPTZ  NOT NULL,
  revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash)
);

COMMENT ON TABLE  refresh_tokens IS 'JWTリフレッシュトークン管理。有効期限7日。ログアウト時にrevoked=TRUEに更新。';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'トークンのSHA-256ハッシュ値。平文トークンはDBに保存しない。';
```

**保持ポリシー：** 期限切れ（`expires_at < NOW()`）かつ `revoked = TRUE` は30日後に物理削除（バッチ）

---

### 3.3 seller_applications（セラー申請）

```sql
CREATE TABLE seller_applications (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  applicant_id     UUID        NOT NULL REFERENCES users(id),
  reason           TEXT        NOT NULL,
  status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                                             -- PENDING | APPROVED | REJECTED
  reviewer_id      UUID        REFERENCES users(id),         -- NULL: 未審査
  review_comment   TEXT,                                     -- 却下理由など
  reviewed_at      TIMESTAMPTZ,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT seller_applications_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

COMMENT ON TABLE  seller_applications IS 'セラー申請。却下後の再申請は新レコード作成。過去申請履歴は保持。';
COMMENT ON COLUMN seller_applications.reviewer_id IS '審査した管理者のユーザーID。PENDING中はNULL。';
```

**業務ルール：**
- 同一ユーザーの `PENDING` 申請が既存の場合、新規申請を拒否（アプリ層で制御）
- `APPROVED` 時にアプリ層で `users.role` を `ROLE_SELLER` に更新し、`shops` レコードを自動生成

---

### 3.4 shops（ショップ）

```sql
CREATE TABLE shops (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id     UUID        NOT NULL REFERENCES users(id),
  name         VARCHAR(100) NOT NULL,
  description  TEXT,
  logo_url     TEXT,
  status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                                                        -- ACTIVE | INACTIVE
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  deleted_at   TIMESTAMPTZ,

  CONSTRAINT shops_owner_unique UNIQUE (owner_id),
  -- shops.name の一意性は部分インデックスで担保（3.4節末尾を参照）
  CONSTRAINT shops_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- ショップ名は有効レコード間でのみ一意（soft delete済みの名前は再利用可能）
-- UNIQUE制約ではなく部分インデックスで実現（PostgreSQLはCONSTRAINT構文にWHEREを使えない）
CREATE UNIQUE INDEX idx_shops_name_active ON shops (name) WHERE deleted_at IS NULL;

COMMENT ON TABLE  shops IS 'セラーのショップ。セラー1名につき必ず1つ（owner_id UNIQUE）。';
COMMENT ON COLUMN shops.deleted_at IS 'users.deleted_at設定時に連動してソフト削除。';
```

---

### 3.5 shop_shipping_policies（ショップ配送ポリシー）

```sql
CREATE TABLE shop_shipping_policies (
  id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id             UUID        NOT NULL REFERENCES shops(id),
  shipping_type       VARCHAR(30)  NOT NULL,
                                              -- FREE | FIXED | CONDITIONAL_FREE
  fixed_fee           INTEGER,               -- shipping_type=FIXED 時のみ使用（円）
  free_threshold      INTEGER,               -- shipping_type=CONDITIONAL_FREE 時のみ使用（円）
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT shop_shipping_policies_shop_unique
    UNIQUE (shop_id),
  CONSTRAINT shop_shipping_policies_type_check
    CHECK (shipping_type IN ('FREE', 'FIXED', 'CONDITIONAL_FREE')),
  CONSTRAINT shop_shipping_policies_fixed_fee_check
    CHECK (shipping_type != 'FIXED' OR fixed_fee IS NOT NULL),
  CONSTRAINT shop_shipping_policies_threshold_check
    CHECK (shipping_type != 'CONDITIONAL_FREE' OR free_threshold IS NOT NULL)
);

COMMENT ON TABLE  shop_shipping_policies IS 'ショップ配送ポリシー。ショップに1:1で紐づく。送料はショップ全体に適用（商品単位ではない）。';
COMMENT ON COLUMN shop_shipping_policies.fixed_fee       IS '固定送料金額（円）。FIXED型のみ有効。';
COMMENT ON COLUMN shop_shipping_policies.free_threshold  IS '送料無料になる注文金額閾値（円）。CONDITIONAL_FREE型のみ有効。';
```

---

### 3.6 categories（カテゴリー）

```sql
CREATE TABLE categories (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  name           VARCHAR(100) NOT NULL,
  slug           VARCHAR(100) NOT NULL,
  display_order  INTEGER      NOT NULL DEFAULT 0,
  parent_id      UUID        REFERENCES categories(id),  -- NULL: ルートカテゴリー
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  deleted_at     TIMESTAMPTZ
  -- slug の一意性は部分インデックスで担保（soft delete済みのスラッグは再利用可能）
);

CREATE UNIQUE INDEX idx_categories_slug_active ON categories (slug) WHERE deleted_at IS NULL;

COMMENT ON TABLE  categories IS '2階層カテゴリー（親・子）。管理者のみ作成・編集可。';
COMMENT ON COLUMN categories.parent_id IS 'NULL=ルートカテゴリー。子カテゴリーは親IDを参照。最大2階層。';
COMMENT ON COLUMN categories.slug      IS 'URL用スラッグ（英数字・ハイフン）。例: handmade-accessories。';
```

---

### 3.7 products（商品）

```sql
CREATE TABLE products (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id         UUID        NOT NULL REFERENCES shops(id),
  category_id     UUID        REFERENCES categories(id),   -- NULL許容（カテゴリー削除後）
  name            VARCHAR(200) NOT NULL,
  description     TEXT,
  price           INTEGER      NOT NULL,
  stock_quantity  INTEGER      NOT NULL DEFAULT 0,
  status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
                                                            -- DRAFT | ACTIVE | INACTIVE | DELETED
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT products_price_check         CHECK (price > 0),
  CONSTRAINT products_stock_check         CHECK (stock_quantity >= 0),
  CONSTRAINT products_status_check        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'DELETED'))
);

COMMENT ON TABLE  products IS '商品。論理削除はstatus=DELETEDで実現。deleted_atカラムなし。';
COMMENT ON COLUMN products.category_id   IS 'カテゴリー削除時はNULLを許容。商品自体は保持。';
COMMENT ON COLUMN products.price         IS '販売価格（円単位、正の整数）。';
COMMENT ON COLUMN products.status        IS 'DRAFT=下書き, ACTIVE=公開, INACTIVE=非公開, DELETED=論理削除。';
```

**保持ポリシー：** `status = 'DELETED'` に変更後180日で物理削除バッチ

---

### 3.8 product_images（商品画像）

```sql
CREATE TABLE product_images (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id      UUID        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  cloudinary_id   VARCHAR(255) NOT NULL,
  image_url       TEXT        NOT NULL,
  display_order   INTEGER      NOT NULL DEFAULT 0,   -- 0が先頭（サムネイル）
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  product_images IS '商品画像。最大5枚。display_order=0がサムネイル。';
COMMENT ON COLUMN product_images.cloudinary_id IS 'Cloudinaryのpublic_id。削除時はCloudinary APIも呼び出す。';
COMMENT ON COLUMN product_images.display_order IS '表示順序。0が先頭（サムネイル）。重複値は非エラー（アプリで制御）。';
```

---

### 3.9 addresses（配送先住所）

```sql
CREATE TABLE addresses (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          UUID        NOT NULL REFERENCES users(id),
  recipient_name   VARCHAR(100) NOT NULL,
  postal_code      VARCHAR(10)  NOT NULL,   -- 例: 150-0001
  prefecture       VARCHAR(20)  NOT NULL,   -- 都道府県
  city             VARCHAR(100) NOT NULL,   -- 市区町村
  address_line     VARCHAR(255) NOT NULL,   -- 番地・建物名
  phone_number     VARCHAR(20)  NOT NULL,
  is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  addresses IS 'ユーザーの配送先住所。複数登録可。注文時にはordersテーブルへスナップショット保存。';
```

**業務ルール：** `is_default = TRUE` はユーザーにつき1件のみ（アプリ側で `UPDATE addresses SET is_default = FALSE WHERE user_id = ? AND id != ?` を実行）

---

### 3.10 carts（カート）

```sql
CREATE TABLE carts (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID        NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT carts_user_unique UNIQUE (user_id)
);

COMMENT ON TABLE carts IS 'ユーザーごとに1つのカート（user_id UNIQUE）。ユーザー登録時に自動生成。';
```

---

### 3.11 cart_items（カート明細）

```sql
CREATE TABLE cart_items (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  cart_id     UUID        NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
  product_id  UUID        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                          -- 商品物理削除バッチ実行時にカート明細も連動削除
  quantity    INTEGER      NOT NULL DEFAULT 1,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT cart_items_cart_product_unique UNIQUE (cart_id, product_id),
  CONSTRAINT cart_items_quantity_check      CHECK (quantity > 0)
);
```

---

### 3.12 orders（注文）

```sql
CREATE TABLE orders (
  id                       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  buyer_id                 UUID        NOT NULL REFERENCES users(id),
  shop_id                  UUID        NOT NULL REFERENCES shops(id),

  -- 注文時点の配送先スナップショット（住所変更・削除の影響を受けない）
  address_id               UUID        REFERENCES addresses(id) ON DELETE SET NULL,
  delivery_recipient_name  VARCHAR(100) NOT NULL,
  delivery_postal_code     VARCHAR(10)  NOT NULL,
  delivery_prefecture      VARCHAR(20)  NOT NULL,
  delivery_city            VARCHAR(100) NOT NULL,
  delivery_address_line    VARCHAR(255) NOT NULL,
  delivery_phone_number    VARCHAR(20)  NOT NULL,

  status                   VARCHAR(30)  NOT NULL DEFAULT 'PENDING_PAYMENT',
                           -- PENDING_PAYMENT | PAYMENT_CONFIRMED | PROCESSING
                           -- | SHIPPED | DELIVERED | COMPLETED | CANCELLED

  subtotal                 INTEGER      NOT NULL,   -- 商品合計（円）
  shipping_fee             INTEGER      NOT NULL,   -- 送料（円）
  total_amount             INTEGER      NOT NULL,   -- subtotal + shipping_fee（円）

  -- 注文確定時点のプラットフォーム設定スナップショット
  commission_rate          NUMERIC(5,4) NOT NULL,   -- 手数料率（例: 0.0500）
  commission_amount        INTEGER      NOT NULL,   -- 手数料額（円）
  seller_amount            INTEGER      NOT NULL,   -- セラー取り分（円）

  stripe_payment_intent_id VARCHAR(255),
  cancelled_at             TIMESTAMPTZ,
  cancel_reason            TEXT,
  created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT orders_status_check CHECK (
    status IN ('PENDING_PAYMENT', 'PAYMENT_CONFIRMED', 'PROCESSING',
               'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELLED')
  ),
  CONSTRAINT orders_amounts_check CHECK (
    total_amount = subtotal + shipping_fee
    AND commission_amount >= 0
    AND seller_amount >= 0
    AND seller_amount + commission_amount = total_amount  -- 三者の整合性を保証
  )
);

COMMENT ON TABLE  orders IS '注文。ショップ単位に分割。削除・更新禁止（会計記録）。配送先は注文時点でスナップショット。';
COMMENT ON COLUMN orders.address_id               IS '元の住所レコードへの参照（削除後はNULL、スナップショットは維持）。';
COMMENT ON COLUMN orders.commission_rate           IS '注文確定時のplatform_configsから取得した手数料率（スナップショット）。';
COMMENT ON COLUMN orders.stripe_payment_intent_id IS 'Stripe PaymentIntentのID。PENDING_PAYMENT中はNULLの場合あり。';
```

**保持ポリシー：** 7年間保持。PII項目（delivery_*）は匿名化バッチで `'匿名化済み'` に上書き

---

### 3.13 order_items（注文明細）

```sql
CREATE TABLE order_items (
  id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id           UUID        NOT NULL REFERENCES orders(id),
  product_id         UUID        REFERENCES products(id) ON DELETE SET NULL,  -- 商品削除後はNULL
  -- 注文時点の商品スナップショット
  product_name       VARCHAR(200) NOT NULL,
  product_image_url  TEXT,
  unit_price         INTEGER      NOT NULL,
  quantity           INTEGER      NOT NULL,
  subtotal           INTEGER      NOT NULL,   -- unit_price * quantity

  is_reviewed        BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT order_items_quantity_check CHECK (quantity > 0),
  CONSTRAINT order_items_subtotal_check CHECK (subtotal = unit_price * quantity)
);

COMMENT ON TABLE  order_items IS '注文明細。商品情報は注文時スナップショット。削除・更新禁止。';
COMMENT ON COLUMN order_items.product_id        IS '商品物理削除後はNULL。スナップショット情報は保持。';
COMMENT ON COLUMN order_items.is_reviewed       IS 'レビュー投稿済みフラグ。COMPLETED後に投稿可能。';
```

---

### 3.14 payments（決済）

```sql
CREATE TABLE payments (
  id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id          UUID        NOT NULL REFERENCES orders(id),
  stripe_payment_id VARCHAR(255) NOT NULL,
  amount            INTEGER      NOT NULL,      -- 円単位
  currency          VARCHAR(3)   NOT NULL DEFAULT 'JPY',
  status            VARCHAR(30)  NOT NULL,
                    -- PENDING | SUCCEEDED | FAILED | REFUNDED
  stripe_refund_id  VARCHAR(255),              -- NULL: 返金なし
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT payments_order_unique          UNIQUE (order_id),
  CONSTRAINT payments_stripe_id_unique      UNIQUE (stripe_payment_id),
  CONSTRAINT payments_currency_check        CHECK (currency = 'JPY'),
  CONSTRAINT payments_status_check          CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED'))
);

COMMENT ON TABLE  payments IS '決済レコード。Stripe Webhook受信後に生成。削除禁止（会計記録）。';
COMMENT ON COLUMN payments.stripe_refund_id IS 'Stripe Refunds APIのrefund ID。キャンセル時に設定。';
```

---

### 3.15 reviews（レビュー）

```sql
CREATE TABLE reviews (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_item_id   UUID        NOT NULL REFERENCES order_items(id),
  product_id      UUID        REFERENCES products(id) ON DELETE SET NULL,
  reviewer_id     UUID        REFERENCES users(id) ON DELETE SET NULL,  -- 匿名化後はNULL
  rating          SMALLINT     NOT NULL,
  comment         TEXT,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT reviews_order_item_unique UNIQUE (order_item_id),
  CONSTRAINT reviews_rating_check      CHECK (rating BETWEEN 1 AND 5)
);

COMMENT ON TABLE  reviews IS '商品レビュー。注文ステータスCOMPLETED後に1件のみ投稿可能。';
COMMENT ON COLUMN reviews.reviewer_id IS 'ユーザー匿名化後はNULL。コメントは匿名表示で継続。';
```

**保持ポリシー：** 作成から1年後に物理削除バッチ

---

### 3.16 chat_rooms（チャットルーム）

```sql
CREATE TABLE chat_rooms (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  buyer_id    UUID        NOT NULL REFERENCES users(id),
  shop_id     UUID        NOT NULL REFERENCES shops(id),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT chat_rooms_buyer_shop_unique UNIQUE (buyer_id, shop_id)
);

COMMENT ON TABLE chat_rooms IS 'バイヤーとショップの1対1チャットルーム。組み合わせで一意。';
```

---

### 3.17 chat_messages（チャットメッセージ）

```sql
CREATE TABLE chat_messages (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  chat_room_id UUID        NOT NULL REFERENCES chat_rooms(id),
  sender_id    UUID        REFERENCES users(id) ON DELETE SET NULL,
  body         TEXT        NOT NULL,
  read_at      TIMESTAMPTZ,    -- NULL: 未読
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  chat_messages IS 'チャットメッセージ。テキストのみ（画像送信はMVPスコープ外）。';
COMMENT ON COLUMN chat_messages.read_at IS 'NULL=未読。相手が開封した時刻を設定。';
```

**保持ポリシー：** 作成から1年後に物理削除バッチ

---

### 3.18 notifications（通知）

```sql
CREATE TABLE notifications (
  id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              UUID        NOT NULL REFERENCES users(id),
  type                 VARCHAR(50)  NOT NULL,
                       -- ORDER_CONFIRMED | ORDER_STATUS_CHANGED | ORDER_CANCELLED
                       -- | NEW_MESSAGE | SELLER_APPLICATION_APPROVED | SELLER_APPLICATION_REJECTED
  title                VARCHAR(200) NOT NULL,
  body                 TEXT        NOT NULL,
  is_read              BOOLEAN      NOT NULL DEFAULT FALSE,
  read_at              TIMESTAMPTZ,
  related_entity_type  VARCHAR(50),   -- ORDER | PRODUCT | SELLER_APPLICATION | CHAT_ROOM
  related_entity_id    UUID,
  expires_at           TIMESTAMPTZ  NOT NULL,   -- created_at + 90日
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()  -- is_read / read_at 更新時に変更
);

COMMENT ON TABLE  notifications IS 'アプリ内通知。expires_at経過後は非表示→物理削除バッチ（毎日）。';
COMMENT ON COLUMN notifications.expires_at          IS '作成から90日後を設定（アプリ側で計算）。';
COMMENT ON COLUMN notifications.related_entity_type IS '通知に関連するエンティティ種別。フロントのリンク生成に使用。';
```

---

### 3.19 wishlists（お気に入り）

```sql
CREATE TABLE wishlists (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID        NOT NULL REFERENCES users(id),
  product_id  UUID        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT wishlists_user_product_unique UNIQUE (user_id, product_id)
);

COMMENT ON TABLE wishlists IS 'お気に入り商品。商品削除時はCASCADE削除。';
```

---

### 3.20 platform_configs（プラットフォーム設定）

```sql
CREATE TABLE platform_configs (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  config_key   VARCHAR(100) NOT NULL,
  config_value TEXT        NOT NULL,
  description  TEXT,
  updated_by   UUID        REFERENCES users(id) ON DELETE SET NULL,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT platform_configs_key_unique UNIQUE (config_key)
);

COMMENT ON TABLE  platform_configs IS 'プラットフォーム設定。管理者のみ更新可。KVS形式。';
COMMENT ON COLUMN platform_configs.config_key IS '設定キー（UPPER_SNAKE_CASE）。例: COMMISSION_RATE, MAINTENANCE_MODE。';
```

**初期データ（シード）：**

| config_key | config_value | description |
|---|---|---|
| `COMMISSION_RATE` | `0.0500` | プラットフォーム手数料率（5%） |
| `MAINTENANCE_MODE` | `false` | メンテナンスモードフラグ |

---

### 3.21 audit_logs（監査ログ）

パーティショニングを適用するため、主キーに `created_at` を含める。

```sql
-- シーケンスを事前に作成（パーティションテーブルでBIGSERIALは使用不可）
CREATE SEQUENCE IF NOT EXISTS audit_logs_id_seq;

CREATE TABLE audit_logs (
  id              BIGINT       NOT NULL DEFAULT nextval('audit_logs_id_seq'),
  correlation_id  UUID         NOT NULL,   -- MDC経由のリクエストID（同一リクエスト内の複数ログを紐づけ）
  actor_id        UUID,                    -- NULL: バッチ・システム処理
  actor_role      VARCHAR(20),             -- 操作時点のロールスナップショット
  actor_email     VARCHAR(255),            -- 操作時点のメールスナップショット
  action          VARCHAR(100) NOT NULL,   -- USER_REGISTERED | ORDER_CANCELLED | ... (UPPER_SNAKE_CASE)
  entity_type     VARCHAR(50),             -- 対象エンティティ種別（USER | ORDER | PRODUCT等）
  entity_id       UUID,                    -- 対象エンティティID
  outcome         VARCHAR(10)  NOT NULL DEFAULT 'SUCCESS',
                  -- SUCCESS | FAILURE
  ip_address      INET,
  old_value       JSONB,                   -- 変更前の値（管理者操作・ステータス変更時）
  new_value       JSONB,                   -- 変更後の値
  error_message   TEXT,                   -- outcome=FAILURE 時のエラー詳細
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  PRIMARY KEY (id, created_at),           -- パーティションキーをPKに含める必須要件

  CONSTRAINT audit_logs_outcome_check CHECK (outcome IN ('SUCCESS', 'FAILURE'))
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  audit_logs IS '監査ログ。追記専用（UPDATE/DELETE禁止）。月別パーティション管理。';
COMMENT ON COLUMN audit_logs.correlation_id  IS 'リクエスト開始時にMDCで生成するUUID。1リクエスト複数ログの紐づけに使用。';
COMMENT ON COLUMN audit_logs.actor_id        IS 'NULL=バッチやシステム自動処理。ユーザー操作時は必ず設定。';
COMMENT ON COLUMN audit_logs.old_value       IS '変更前の状態（JSONB）。管理者操作・ステータス変更時に記録。';
```

**パーティション作成（月次 Flyway マイグレーションで追加）：**

```sql
-- Phase 2 開始時に当面の分を作成
CREATE TABLE audit_logs_2026_05 PARTITION OF audit_logs
  FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE audit_logs_2026_06 PARTITION OF audit_logs
  FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
-- 以降、毎月追加
```

---

## 4. インデックス設計

### 4.1 インデックス一覧

```sql
-- ── users ────────────────────────────────────────────────────────────────
-- UNIQUE 制約でインデックス自動生成: email, google_id
CREATE INDEX idx_users_deleted_at     ON users (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_users_role           ON users (role);

-- ── refresh_tokens ───────────────────────────────────────────────────────
-- UNIQUE 制約でインデックス自動生成: token_hash
CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);  -- 削除バッチ用

-- ── seller_applications ──────────────────────────────────────────────────
CREATE INDEX idx_seller_applications_applicant_id ON seller_applications (applicant_id);
CREATE INDEX idx_seller_applications_status       ON seller_applications (status);

-- ── shops ────────────────────────────────────────────────────────────────
-- UNIQUE 制約でインデックス自動生成: owner_id
-- idx_shops_name_active（部分UNIQUE）はテーブル定義内で作成済み
CREATE INDEX idx_shops_status     ON shops (status);
CREATE INDEX idx_shops_deleted_at ON shops (deleted_at) WHERE deleted_at IS NOT NULL;

-- ── categories ───────────────────────────────────────────────────────────
-- idx_categories_slug_active（部分UNIQUE）はテーブル定義内で作成済み
CREATE INDEX idx_categories_parent_id  ON categories (parent_id);
CREATE INDEX idx_categories_deleted_at ON categories (deleted_at) WHERE deleted_at IS NOT NULL;

-- ── products ─────────────────────────────────────────────────────────────
CREATE INDEX idx_products_shop_id            ON products (shop_id);
CREATE INDEX idx_products_category_id        ON products (category_id);
CREATE INDEX idx_products_status             ON products (status);
CREATE INDEX idx_products_status_created_at  ON products (status, created_at DESC);  -- 新着一覧
CREATE INDEX idx_products_price              ON products (price);                     -- 価格フィルター
CREATE INDEX idx_products_stock_quantity     ON products (stock_quantity);            -- 在庫フィルター

-- 全文検索用（pg_trgm 拡張が必要）
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm        ON products USING GIN (name gin_trgm_ops);
CREATE INDEX idx_products_description_trgm ON products USING GIN (description gin_trgm_ops);

-- ── product_images ───────────────────────────────────────────────────────
CREATE INDEX idx_product_images_product_order ON product_images (product_id, display_order);

-- ── addresses ────────────────────────────────────────────────────────────
CREATE INDEX idx_addresses_user_id         ON addresses (user_id);
CREATE INDEX idx_addresses_user_default    ON addresses (user_id, is_default);

-- ── cart_items ───────────────────────────────────────────────────────────
CREATE INDEX idx_cart_items_cart_id ON cart_items (cart_id);

-- ── orders ───────────────────────────────────────────────────────────────
CREATE INDEX idx_orders_buyer_id          ON orders (buyer_id);
CREATE INDEX idx_orders_shop_id           ON orders (shop_id);
CREATE INDEX idx_orders_status            ON orders (status);
CREATE INDEX idx_orders_buyer_created_at  ON orders (buyer_id, created_at DESC);  -- バイヤー注文履歴
CREATE INDEX idx_orders_shop_created_at   ON orders (shop_id, created_at DESC);   -- セラー受注一覧
CREATE INDEX idx_orders_stripe_pi_id      ON orders (stripe_payment_intent_id);   -- Webhook検索

-- ── order_items ──────────────────────────────────────────────────────────
CREATE INDEX idx_order_items_order_id          ON order_items (order_id);
CREATE INDEX idx_order_items_product_id        ON order_items (product_id);
CREATE INDEX idx_order_items_review_eligible   ON order_items (order_id, is_reviewed)
  WHERE is_reviewed = FALSE;                                                        -- レビュー未投稿検索

-- ── reviews ──────────────────────────────────────────────────────────────
-- UNIQUE 制約でインデックス自動生成: order_item_id
CREATE INDEX idx_reviews_product_id  ON reviews (product_id);
CREATE INDEX idx_reviews_reviewer_id ON reviews (reviewer_id);

-- ── chat_rooms ───────────────────────────────────────────────────────────
-- UNIQUE 制約でインデックス自動生成: (buyer_id, shop_id)
CREATE INDEX idx_chat_rooms_buyer_id ON chat_rooms (buyer_id);
CREATE INDEX idx_chat_rooms_shop_id  ON chat_rooms (shop_id);

-- ── chat_messages ────────────────────────────────────────────────────────
CREATE INDEX idx_chat_messages_room_created  ON chat_messages (chat_room_id, created_at DESC);
CREATE INDEX idx_chat_messages_room_unread   ON chat_messages (chat_room_id, read_at)
  WHERE read_at IS NULL;                                                            -- 未読カウント

-- ── notifications ────────────────────────────────────────────────────────
CREATE INDEX idx_notifications_user_read     ON notifications (user_id, is_read, expires_at);
CREATE INDEX idx_notifications_user_created  ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_expires_at    ON notifications (expires_at);        -- 削除バッチ用

-- ── wishlists ────────────────────────────────────────────────────────────
-- UNIQUE 制約でインデックス自動生成: (user_id, product_id)
CREATE INDEX idx_wishlists_user_id ON wishlists (user_id);

-- ── audit_logs（パーティション別に自動継承）────────────────────────────────
CREATE INDEX idx_audit_logs_actor_id         ON audit_logs (actor_id, created_at DESC);
CREATE INDEX idx_audit_logs_action           ON audit_logs (action, created_at DESC);
CREATE INDEX idx_audit_logs_entity           ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_logs_correlation_id   ON audit_logs (correlation_id);
```

### 4.2 インデックス設計の判断基準

| ケース | 対応 |
|---|---|
| WHERE句で頻繁に使われる単一カラム | 通常のB-treeインデックス |
| ソート + フィルターの複合条件 | 複合インデックス（フィルター先、ソート後） |
| バッチ削除用の期限チェック | 部分インデックス（`WHERE expires_at IS NOT NULL`等） |
| LIKE '%keyword%' による日本語全文検索 | pg_trgm GINインデックス |
| JOIN先の外部キー | 基本的にすべてインデックス追加 |

---

## 5. パーティショニング設計

### 5.1 対象テーブル

`audit_logs` のみパーティショニングを適用する。他のテーブルはMVPスコープでパーティション不要。

### 5.2 パーティション管理フロー

```
毎月1日: Flywayマイグレーション（翌月分のパーティション作成）
         ↓
1年後:   古いパーティションのDROP（= 大量DELETEより高速）
         S3アーカイブ後にDROP（Phase 5以降）
```

### 5.3 パーティション命名規則

```
audit_logs_{YYYY}_{MM}
例: audit_logs_2026_05
```

### 5.4 翌月パーティション自動作成（バッチ）

```java
// AuditLogPartitionJob: 毎月25日に翌月分のパーティションを作成
@Scheduled(cron = "0 0 4 25 * *")
public void createNextMonthPartition() {
    // Flyway マイグレーションとして登録 or JdbcTemplate で直接実行
}
```

---

## 6. Flywayマイグレーション規則

### 6.1 フェーズ別の編集方針

Flywayの「既存ファイル編集禁止」ルールは、**そのマイグレーションがDBに適用済みでデータが存在する場合**に厳格に適用する。  
開発フェーズ中のローカル環境（データなし）では、既存マイグレーションファイルの直接編集を許可する。

| 状況 | 方針 |
|---|---|
| **フェーズ開発中**（ローカル・データなし） | 既存ファイルを直接編集 + `flyway clean → migrate` でリセット |
| **ステージング投入後**（テストデータあり） | 新バージョンのマイグレーションを追加する |
| **本番投入後** | 厳格に新バージョン追加のみ。既存ファイル編集は絶対禁止 |

**フェーズ完了のタイミングで現行マイグレーションを「凍結」し、次フェーズから新規ファイルを追加する。**  
例: Phase 2 完了時に `V1〜V8` を凍結 → Phase 3 では `V9` 以降を追加。

### 6.2 ファイル命名規則

```
V{バージョン}__{説明}.sql

例（Phase 2 分）:
V1__create_extensions.sql                  -- pg_trgm等の拡張
V2__create_identity_tables.sql             -- users, refresh_tokens, seller_applications
V3__create_catalog_tables.sql              -- shops, policies, categories, products, product_images
V4__create_order_tables.sql                -- addresses, carts, cart_items, orders, order_items, payments
V5__create_messaging_and_review_tables.sql -- reviews, chat_rooms, chat_messages
V6__create_notification_tables.sql         -- notifications, wishlists
V7__create_platform_and_audit_tables.sql   -- platform_configs, audit_logs
V8__create_audit_logs_partitions.sql       -- 初期パーティション（当月〜翌3ヶ月分）
V9__seed_platform_configs.sql              -- 初期データ（手数料率等）
V10__seed_development_data.sql             -- 開発用シードデータ（本番環境では実行しない）
```

### 6.3 プロファイル別の安全設定

`src/main/resources/application-local.yml`（ローカル開発のみ）:
```yaml
spring:
  flyway:
    clean-disabled: false            # flyway clean を許可（開発中のリセット用）
    clean-on-validation-error: true  # チェックサム不一致時に自動 clean（編集を反映）
```

`src/main/resources/application-prod.yml`（本番・ステージング）:
```yaml
spring:
  flyway:
    clean-disabled: true             # clean を完全禁止（データ保護）
    clean-on-validation-error: false
```

> `clean-on-validation-error: true` はローカル専用。本番で有効にするとデータが全消去される危険があるため、プロファイルで必ず分離すること。

### 6.4 開発中のリセット手順

スキーマ変更を既存ファイルに加えた後、ローカルDBに反映する手順:

```bash
# Gradle タスク（Spring Boot Flyway Plugin 経由）
./gradlew flywayClean flywayMigrate

# または Docker Compose でDBごとリセット
docker compose down -v && docker compose up -d db
./gradlew flywayMigrate
```

### 6.5 その他のルール

- DDL（`CREATE TABLE`等）とDML（`INSERT`）は別ファイルに分ける
- 開発用シードデータは `application-local` プロファイル条件付きで実行（`spring.flyway.locations` を活用）
- CI（GitHub Actions）は常に clean なしで `flywayMigrate` のみ実行し、マイグレーション全体の通し検証を行う
- Testcontainersでテスト実行時もマイグレーションをすべて適用し、本番同等のスキーマで検証する

---

## 7. 初期データ（シード）

`V{n}__seed_*.sql` または `ApplicationRunner` でPhase 2開始時に投入する。

| テーブル | シード内容 |
|---|---|
| `platform_configs` | `COMMISSION_RATE=0.0500`, `MAINTENANCE_MODE=false` |
| `users` | ADMIN 1名、SELLER 5名、BUYER 3名（BCryptハッシュ化済みパスワード） |
| `shops` | 5店舗（各SELLERに1つ） |
| `shop_shipping_policies` | 各ショップに1件（FREEまたはFIXED） |
| `categories` | 親5件 + 子10件程度 |
| `products` | 各ショップに5〜10商品 |
| `orders` / `order_items` / `payments` | 各BUYERに完了済み注文 2〜3件 |
| `reviews` | 一部商品に3〜5件 |

---

## 8. ER図

```
[users] 1────────────────1 [carts] 1──* [cart_items] *──1 [products]
  │ 1                                                           │ 1
  │ ├──────────────────1 [shops] 1──1 [shop_shipping_policies]  │
  │ │                     │ 1                                   │
  │ │                     └──* [products] 1──* [product_images]  │
  │ │                                │                          │
  │ │                    [categories] ──* ┘                     │
  │ │                                                           │
  │ ├──* [addresses]                                            │
  │ │                                                           │
  │ ├──* [seller_applications]                                  │
  │ │                                                           │
  │ ├──* [orders] ──* [order_items] ──* [products]              │
  │ │       │ 1              │ 1                                │
  │ │       └──1 [payments]  └──0..1 [reviews] *──1 [products]  │
  │ │                                                           │
  │ ├──* [notifications]                                        │
  │ │                                                           │
  │ ├──* [wishlists] *──1 [products]                            │
  │ │                                                           │
  │ └──* [chat_rooms] *──1 [shops]                              │
  │            │ 1                                              │
  │            └──* [chat_messages]                             │
  │                                                             │
  └──* [audit_logs]（追記のみ、パーティション管理）              │
  │                                                             │
  └──* [refresh_tokens]                                         │
                                                               │
[platform_configs]（KVS形式、管理者のみ更新）                   │
```

---

**以上**

*本DB設計書はREQUIREMENTS.md § 7（データモデル）の物理設計詳細版です。スキーマ変更はFlywayマイグレーションとして追加し、本ドキュメントを同時に更新してください。設計上の判断はADRに記録します。*
