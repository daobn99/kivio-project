CREATE TABLE reviews (
  id             UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
  order_item_id  UUID      NOT NULL REFERENCES order_items(id),
  product_id     UUID      REFERENCES products(id) ON DELETE SET NULL,
  reviewer_id    UUID      REFERENCES users(id) ON DELETE SET NULL,
  rating         SMALLINT  NOT NULL,
  comment        TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT reviews_order_item_unique UNIQUE (order_item_id),
  CONSTRAINT reviews_rating_check      CHECK (rating BETWEEN 1 AND 5)
);

COMMENT ON TABLE  reviews IS '商品レビュー。注文ステータスCOMPLETED後に1件のみ投稿可能。';
COMMENT ON COLUMN reviews.reviewer_id IS 'ユーザー匿名化後はNULL。コメントは匿名表示で継続。';

CREATE TABLE chat_rooms (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  buyer_id    UUID        NOT NULL REFERENCES users(id),
  shop_id     UUID        NOT NULL REFERENCES shops(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT chat_rooms_buyer_shop_unique UNIQUE (buyer_id, shop_id)
);

COMMENT ON TABLE chat_rooms IS 'バイヤーとショップの1対1チャットルーム。組み合わせで一意。';

CREATE TABLE chat_messages (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  chat_room_id UUID        NOT NULL REFERENCES chat_rooms(id),
  sender_id    UUID        REFERENCES users(id) ON DELETE SET NULL,
  body         TEXT        NOT NULL,
  read_at      TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  chat_messages IS 'チャットメッセージ。テキストのみ（画像送信はMVPスコープ外）。';
COMMENT ON COLUMN chat_messages.read_at IS 'NULL=未読。相手が開封した時刻を設定。';

-- インデックス
CREATE INDEX idx_reviews_product_id       ON reviews (product_id);
CREATE INDEX idx_reviews_reviewer_id      ON reviews (reviewer_id);
CREATE INDEX idx_chat_rooms_buyer_id      ON chat_rooms (buyer_id);
CREATE INDEX idx_chat_rooms_shop_id       ON chat_rooms (shop_id);
CREATE INDEX idx_chat_messages_room_created ON chat_messages (chat_room_id, created_at DESC);
CREATE INDEX idx_chat_messages_room_unread  ON chat_messages (chat_room_id, read_at)
  WHERE read_at IS NULL;
