CREATE TABLE notifications (
  id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              UUID         NOT NULL REFERENCES users(id),
  type                 VARCHAR(50)  NOT NULL,
  title                VARCHAR(200) NOT NULL,
  body                 TEXT         NOT NULL,
  is_read              BOOLEAN      NOT NULL DEFAULT FALSE,
  read_at              TIMESTAMPTZ,
  related_entity_type  VARCHAR(50),
  related_entity_id    UUID,
  expires_at           TIMESTAMPTZ  NOT NULL,
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  notifications IS 'アプリ内通知。expires_at経過後は非表示→物理削除バッチ（毎日）。';
COMMENT ON COLUMN notifications.expires_at         IS '作成から90日後を設定（アプリ側で計算）。';
COMMENT ON COLUMN notifications.related_entity_type IS '通知に関連するエンティティ種別。フロントのリンク生成に使用。';

CREATE TABLE wishlists (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID        NOT NULL REFERENCES users(id),
  product_id  UUID        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT wishlists_user_product_unique UNIQUE (user_id, product_id)
);

COMMENT ON TABLE wishlists IS 'お気に入り商品。商品削除時はCASCADE削除。';

-- インデックス
CREATE INDEX idx_notifications_user_read    ON notifications (user_id, is_read, expires_at);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_expires_at   ON notifications (expires_at);
CREATE INDEX idx_wishlists_user_id          ON wishlists (user_id);
