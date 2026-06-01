CREATE TABLE shops (
  id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id     UUID         NOT NULL REFERENCES users(id),
  name         VARCHAR(100) NOT NULL,
  description  TEXT,
  logo_url     TEXT,
  status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  deleted_at   TIMESTAMPTZ,

  CONSTRAINT shops_owner_unique UNIQUE (owner_id),
  CONSTRAINT shops_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX idx_shops_name_active ON shops (name) WHERE deleted_at IS NULL;

COMMENT ON TABLE  shops IS 'セラーのショップ。セラー1名につき必ず1つ（owner_id UNIQUE）。';
COMMENT ON COLUMN shops.deleted_at IS 'users.deleted_at設定時に連動してソフト削除。';

CREATE TABLE shop_shipping_policies (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id         UUID        NOT NULL REFERENCES shops(id),
  shipping_type   VARCHAR(30) NOT NULL,
  fixed_fee       INTEGER,
  free_threshold  INTEGER,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT shop_shipping_policies_shop_unique
    UNIQUE (shop_id),
  CONSTRAINT shop_shipping_policies_type_check
    CHECK (shipping_type IN ('FREE', 'FIXED', 'CONDITIONAL_FREE')),
  CONSTRAINT shop_shipping_policies_fixed_fee_check
    CHECK (shipping_type != 'FIXED' OR fixed_fee IS NOT NULL),
  CONSTRAINT shop_shipping_policies_threshold_check
    CHECK (shipping_type != 'CONDITIONAL_FREE' OR free_threshold IS NOT NULL)
);

COMMENT ON TABLE  shop_shipping_policies IS 'ショップ配送ポリシー。ショップに1:1で紐づく。';
COMMENT ON COLUMN shop_shipping_policies.fixed_fee      IS '固定送料金額（円）。FIXED型のみ有効。';
COMMENT ON COLUMN shop_shipping_policies.free_threshold IS '送料無料になる注文金額閾値（円）。CONDITIONAL_FREE型のみ有効。';

CREATE TABLE categories (
  id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  name           VARCHAR(100) NOT NULL,
  slug           VARCHAR(100) NOT NULL,
  display_order  INTEGER      NOT NULL DEFAULT 0,
  parent_id      UUID         REFERENCES categories(id),
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  deleted_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_categories_slug_active ON categories (slug) WHERE deleted_at IS NULL;

COMMENT ON TABLE  categories IS '2階層カテゴリー（親・子）。管理者のみ作成・編集可。';
COMMENT ON COLUMN categories.parent_id IS 'NULL=ルートカテゴリー。子カテゴリーは親IDを参照。最大2階層。';
COMMENT ON COLUMN categories.slug      IS 'URL用スラッグ（英数字・ハイフン）。';

CREATE TABLE products (
  id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id         UUID         NOT NULL REFERENCES shops(id),
  category_id     UUID         REFERENCES categories(id),
  name            VARCHAR(200) NOT NULL,
  description     TEXT,
  price           INTEGER      NOT NULL,
  stock_quantity  INTEGER      NOT NULL DEFAULT 0,
  status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT products_price_check   CHECK (price > 0),
  CONSTRAINT products_stock_check   CHECK (stock_quantity >= 0),
  CONSTRAINT products_status_check  CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'DELETED'))
);

COMMENT ON TABLE  products IS '商品。論理削除はstatus=DELETEDで実現。deleted_atカラムなし。';
COMMENT ON COLUMN products.category_id IS 'カテゴリー削除時はNULLを許容。商品自体は保持。';
COMMENT ON COLUMN products.price       IS '販売価格（円単位、正の整数）。';
COMMENT ON COLUMN products.status      IS 'DRAFT=下書き, ACTIVE=公開, INACTIVE=非公開, DELETED=論理削除。';

CREATE TABLE product_images (
  id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id     UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  cloudinary_id  VARCHAR(255) NOT NULL,
  image_url      TEXT         NOT NULL,
  display_order  INTEGER      NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  product_images IS '商品画像。最大5枚。display_order=0がサムネイル。';
COMMENT ON COLUMN product_images.cloudinary_id  IS 'Cloudinaryのpublic_id。削除時はCloudinary APIも呼び出す。';
COMMENT ON COLUMN product_images.display_order  IS '表示順序。0が先頭（サムネイル）。重複値は非エラー（アプリで制御）。';

-- インデックス
CREATE INDEX idx_shops_status                ON shops (status);
CREATE INDEX idx_shops_deleted_at            ON shops (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_categories_parent_id        ON categories (parent_id);
CREATE INDEX idx_categories_deleted_at       ON categories (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_products_shop_id            ON products (shop_id);
CREATE INDEX idx_products_category_id        ON products (category_id);
CREATE INDEX idx_products_status             ON products (status);
CREATE INDEX idx_products_status_created_at  ON products (status, created_at DESC);
CREATE INDEX idx_products_price              ON products (price);
CREATE INDEX idx_products_stock_quantity     ON products (stock_quantity);
CREATE INDEX idx_products_name_trgm          ON products USING GIN (name gin_trgm_ops);
CREATE INDEX idx_products_description_trgm   ON products USING GIN (description gin_trgm_ops);
CREATE INDEX idx_product_images_product_order ON product_images (product_id, display_order);
