-- audit_logs をパーティションテーブルから通常テーブルに変換する
-- Hibernate 7.x の schema validator がパーティションテーブル (relkind='p') を
-- JDBC getTables() 経由で正しく検出できないため、Phase 2 では通常テーブルを使用する。

-- 既存のパーティションテーブルとすべての子パーティションを削除（CASCADE で子も削除）
DROP TABLE IF EXISTS audit_logs CASCADE;

-- 通常テーブルとして再作成
CREATE TABLE audit_logs (
  id              BIGINT        NOT NULL DEFAULT nextval('audit_logs_id_seq'),
  correlation_id  UUID          NOT NULL,
  actor_id        UUID,
  actor_role      VARCHAR(20),
  actor_email     VARCHAR(255),
  action          VARCHAR(100)  NOT NULL,
  entity_type     VARCHAR(50),
  entity_id       UUID,
  outcome         VARCHAR(10)   NOT NULL DEFAULT 'SUCCESS',
  ip_address      VARCHAR(45),
  old_value       JSONB,
  new_value       JSONB,
  error_message   TEXT,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

  PRIMARY KEY (id, created_at),

  CONSTRAINT audit_logs_outcome_check CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

COMMENT ON TABLE  audit_logs IS '監査ログ。追記専用（UPDATE/DELETE禁止）。';
COMMENT ON COLUMN audit_logs.correlation_id IS 'リクエスト開始時にMDCで生成するUUID。1リクエスト複数ログの紐づけに使用。';
COMMENT ON COLUMN audit_logs.actor_id       IS 'NULL=バッチやシステム自動処理。ユーザー操作時は必ず設定。';
COMMENT ON COLUMN audit_logs.old_value      IS '変更前の状態（JSONB）。管理者操作・ステータス変更時に記録。';

CREATE INDEX idx_audit_logs_actor_id       ON audit_logs (actor_id, created_at DESC);
CREATE INDEX idx_audit_logs_action         ON audit_logs (action, created_at DESC);
CREATE INDEX idx_audit_logs_entity         ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_logs_correlation_id ON audit_logs (correlation_id);
