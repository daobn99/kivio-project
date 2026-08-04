CREATE TABLE platform_configs (
  id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  config_key   VARCHAR(100) NOT NULL,
  config_value TEXT         NOT NULL,
  description  TEXT,
  updated_by   UUID         REFERENCES users(id) ON DELETE SET NULL,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT platform_configs_key_unique UNIQUE (config_key)
);

COMMENT ON TABLE  platform_configs IS 'プラットフォーム設定。管理者のみ更新可。KVS形式。';
COMMENT ON COLUMN platform_configs.config_key IS '設定キー（UPPER_SNAKE_CASE）。例: COMMISSION_RATE, MAINTENANCE_MODE。';

-- audit_logs はパーティションテーブルのため BIGSERIAL 不可、シーケンスを事前作成
CREATE SEQUENCE IF NOT EXISTS audit_logs_id_seq;

CREATE TABLE audit_logs (
  id              BIGINT       NOT NULL DEFAULT nextval('audit_logs_id_seq'),
  correlation_id  UUID         NOT NULL,
  actor_id        UUID,
  actor_role      VARCHAR(20),
  actor_email     VARCHAR(255),
  action          VARCHAR(100) NOT NULL,
  entity_type     VARCHAR(50),
  entity_id       UUID,
  outcome         VARCHAR(10)  NOT NULL DEFAULT 'SUCCESS',
  ip_address      INET,
  old_value       JSONB,
  new_value       JSONB,
  error_message   TEXT,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  PRIMARY KEY (id, created_at),

  CONSTRAINT audit_logs_outcome_check CHECK (outcome IN ('SUCCESS', 'FAILURE'))
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  audit_logs IS '監査ログ。追記専用（UPDATE/DELETE禁止）。月別パーティション管理。';
COMMENT ON COLUMN audit_logs.correlation_id IS 'リクエスト開始時にMDCで生成するUUID。1リクエスト複数ログの紐づけに使用。';
COMMENT ON COLUMN audit_logs.actor_id       IS 'NULL=バッチやシステム自動処理。ユーザー操作時は必ず設定。';
COMMENT ON COLUMN audit_logs.old_value      IS '変更前の状態（JSONB）。管理者操作・ステータス変更時に記録。';

-- インデックス（パーティション別に自動継承）
CREATE INDEX idx_audit_logs_actor_id       ON audit_logs (actor_id, created_at DESC);
CREATE INDEX idx_audit_logs_action         ON audit_logs (action, created_at DESC);
CREATE INDEX idx_audit_logs_entity         ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_logs_correlation_id ON audit_logs (correlation_id);
