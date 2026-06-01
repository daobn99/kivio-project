CREATE TABLE addresses (
  id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          UUID         NOT NULL REFERENCES users(id),
  recipient_name   VARCHAR(100) NOT NULL,
  postal_code      VARCHAR(10)  NOT NULL,
  prefecture       VARCHAR(20)  NOT NULL,
  city             VARCHAR(100) NOT NULL,
  address_line     VARCHAR(255) NOT NULL,
  phone_number     VARCHAR(20)  NOT NULL,
  is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE addresses IS 'ユーザーの配送先住所。複数登録可。注文時にはordersテーブルへスナップショット保存。';

CREATE TABLE carts (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID        NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT carts_user_unique UNIQUE (user_id)
);

COMMENT ON TABLE carts IS 'ユーザーごとに1つのカート（user_id UNIQUE）。ユーザー登録時に自動生成。';

CREATE TABLE cart_items (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  cart_id     UUID        NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
  product_id  UUID        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  quantity    INTEGER     NOT NULL DEFAULT 1,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT cart_items_cart_product_unique UNIQUE (cart_id, product_id),
  CONSTRAINT cart_items_quantity_check      CHECK (quantity > 0)
);

CREATE TABLE orders (
  id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  buyer_id                 UUID         NOT NULL REFERENCES users(id),
  shop_id                  UUID         NOT NULL REFERENCES shops(id),
  address_id               UUID         REFERENCES addresses(id) ON DELETE SET NULL,
  delivery_recipient_name  VARCHAR(100) NOT NULL,
  delivery_postal_code     VARCHAR(10)  NOT NULL,
  delivery_prefecture      VARCHAR(20)  NOT NULL,
  delivery_city            VARCHAR(100) NOT NULL,
  delivery_address_line    VARCHAR(255) NOT NULL,
  delivery_phone_number    VARCHAR(20)  NOT NULL,
  status                   VARCHAR(30)  NOT NULL DEFAULT 'PENDING_PAYMENT',
  subtotal                 INTEGER      NOT NULL,
  shipping_fee             INTEGER      NOT NULL,
  total_amount             INTEGER      NOT NULL,
  commission_rate          NUMERIC(5,4) NOT NULL,
  commission_amount        INTEGER      NOT NULL,
  seller_amount            INTEGER      NOT NULL,
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
    AND seller_amount + commission_amount = total_amount
  )
);

COMMENT ON TABLE  orders IS '注文。ショップ単位に分割。削除・更新禁止（会計記録）。配送先は注文時点でスナップショット。';
COMMENT ON COLUMN orders.address_id               IS '元の住所レコードへの参照（削除後はNULL、スナップショットは維持）。';
COMMENT ON COLUMN orders.commission_rate          IS '注文確定時のplatform_configsから取得した手数料率（スナップショット）。';
COMMENT ON COLUMN orders.stripe_payment_intent_id IS 'Stripe PaymentIntentのID。PENDING_PAYMENT中はNULLの場合あり。';

CREATE TABLE order_items (
  id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id           UUID         NOT NULL REFERENCES orders(id),
  product_id         UUID         REFERENCES products(id) ON DELETE SET NULL,
  product_name       VARCHAR(200) NOT NULL,
  product_image_url  TEXT,
  unit_price         INTEGER      NOT NULL,
  quantity           INTEGER      NOT NULL,
  subtotal           INTEGER      NOT NULL,
  is_reviewed        BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT order_items_quantity_check CHECK (quantity > 0),
  CONSTRAINT order_items_subtotal_check CHECK (subtotal = unit_price * quantity)
);

COMMENT ON TABLE  order_items IS '注文明細。商品情報は注文時スナップショット。削除・更新禁止。';
COMMENT ON COLUMN order_items.product_id  IS '商品物理削除後はNULL。スナップショット情報は保持。';
COMMENT ON COLUMN order_items.is_reviewed IS 'レビュー投稿済みフラグ。COMPLETED後に投稿可能。';

CREATE TABLE payments (
  id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id          UUID         NOT NULL REFERENCES orders(id),
  stripe_payment_id VARCHAR(255) NOT NULL,
  amount            INTEGER      NOT NULL,
  currency          VARCHAR(3)   NOT NULL DEFAULT 'JPY',
  status            VARCHAR(30)  NOT NULL,
  stripe_refund_id  VARCHAR(255),
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT payments_order_unique     UNIQUE (order_id),
  CONSTRAINT payments_stripe_id_unique UNIQUE (stripe_payment_id),
  CONSTRAINT payments_currency_check   CHECK (currency = 'JPY'),
  CONSTRAINT payments_status_check     CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED'))
);

COMMENT ON TABLE  payments IS '決済レコード。Stripe Webhook受信後に生成。削除禁止（会計記録）。';
COMMENT ON COLUMN payments.stripe_refund_id IS 'Stripe Refunds APIのrefund ID。キャンセル時に設定。';

-- インデックス
CREATE INDEX idx_addresses_user_id         ON addresses (user_id);
CREATE INDEX idx_addresses_user_default    ON addresses (user_id, is_default);
CREATE INDEX idx_cart_items_cart_id        ON cart_items (cart_id);
CREATE INDEX idx_orders_buyer_id           ON orders (buyer_id);
CREATE INDEX idx_orders_shop_id            ON orders (shop_id);
CREATE INDEX idx_orders_status             ON orders (status);
CREATE INDEX idx_orders_buyer_created_at   ON orders (buyer_id, created_at DESC);
CREATE INDEX idx_orders_shop_created_at    ON orders (shop_id, created_at DESC);
CREATE INDEX idx_orders_stripe_pi_id       ON orders (stripe_payment_intent_id);
CREATE INDEX idx_order_items_order_id      ON order_items (order_id);
CREATE INDEX idx_order_items_product_id    ON order_items (product_id);
CREATE INDEX idx_order_items_review_eligible ON order_items (order_id, is_reviewed)
  WHERE is_reviewed = FALSE;
