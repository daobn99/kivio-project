CREATE TABLE email_verification_tokens (
  id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID         NOT NULL REFERENCES users(id),
  token_hash   VARCHAR(255) NOT NULL,   -- SHA-256ハッシュ（平文は保持しない）
  expires_at   TIMESTAMPTZ  NOT NULL,   -- created_at + 24時間
  used_at      TIMESTAMPTZ,             -- NULL=未使用。再利用防止のためDELETEせずused_atで管理
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT email_verification_tokens_token_hash_unique UNIQUE (token_hash)
);

COMMENT ON TABLE  email_verification_tokens IS 'メール認証トークン管理。使用済みトークンは削除せずused_atで管理（監査目的）。';
COMMENT ON COLUMN email_verification_tokens.token_hash IS 'トークンのSHA-256ハッシュ値。平文トークンはDBに保存しない。';
COMMENT ON COLUMN email_verification_tokens.used_at    IS 'NULL=未使用。使用後に現在時刻を設定。再利用防止のためレコードは削除しない。';

CREATE INDEX idx_email_verification_tokens_user_id    ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_tokens_token_hash ON email_verification_tokens (token_hash);
