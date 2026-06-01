CREATE TABLE users (
  id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  email            VARCHAR(255) NOT NULL,
  password_hash    VARCHAR(255),
  google_id        VARCHAR(255),
  display_name     VARCHAR(100) NOT NULL DEFAULT '',
  avatar_url       TEXT,
  role             VARCHAR(20)  NOT NULL DEFAULT 'ROLE_BUYER',
  status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  deleted_at       TIMESTAMPTZ,

  CONSTRAINT users_email_unique     UNIQUE (email),
  CONSTRAINT users_google_id_unique UNIQUE (google_id),
  CONSTRAINT users_role_check       CHECK (role IN ('ROLE_BUYER', 'ROLE_SELLER', 'ROLE_ADMIN')),
  CONSTRAINT users_status_check     CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

COMMENT ON TABLE  users IS 'ユーザーアカウント。退会後は匿名化（90日後バッチ）。物理削除禁止。';
COMMENT ON COLUMN users.password_hash IS 'BCrypt cost factor 12。Google OAuthユーザーはNULL。';
COMMENT ON COLUMN users.role         IS 'ROLE_BUYER（デフォルト）| ROLE_SELLER（セラー申請承認後）| ROLE_ADMIN（手動付与）';
COMMENT ON COLUMN users.deleted_at   IS 'Soft delete タイムスタンプ。NULL=有効。90日後に匿名化バッチ実行。';

CREATE TABLE refresh_tokens (
  id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID         NOT NULL REFERENCES users(id),
  token_hash   VARCHAR(255) NOT NULL,
  expires_at   TIMESTAMPTZ  NOT NULL,
  revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash)
);

COMMENT ON TABLE  refresh_tokens IS 'JWTリフレッシュトークン管理。有効期限7日。ログアウト時にrevoked=TRUEに更新。';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'トークンのSHA-256ハッシュ値。平文トークンはDBに保存しない。';

CREATE TABLE seller_applications (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  applicant_id     UUID        NOT NULL REFERENCES users(id),
  reason           TEXT        NOT NULL,
  status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewer_id      UUID        REFERENCES users(id),
  review_comment   TEXT,
  reviewed_at      TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT seller_applications_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

COMMENT ON TABLE  seller_applications IS 'セラー申請。却下後の再申請は新レコード作成。過去申請履歴は保持。';
COMMENT ON COLUMN seller_applications.reviewer_id IS '審査した管理者のユーザーID。PENDING中はNULL。';

-- インデックス
CREATE INDEX idx_users_deleted_at                ON users (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_users_role                      ON users (role);
CREATE INDEX idx_refresh_tokens_user_id          ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at       ON refresh_tokens (expires_at);
CREATE INDEX idx_seller_applications_applicant_id ON seller_applications (applicant_id);
CREATE INDEX idx_seller_applications_status      ON seller_applications (status);
