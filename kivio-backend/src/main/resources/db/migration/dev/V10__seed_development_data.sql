-- 開発用シードデータ（本番環境では実行しない）
-- このファイルは application-dev.yaml の spring.flyway.locations に dev ディレクトリを追加することで読み込まれる

-- パスワードは全ユーザー "Password1!" の BCrypt ハッシュ（cost factor 12）
-- $2a$12$YourHashHere は実際の実装時に生成すること

INSERT INTO users (id, email, password_hash, display_name, role, status, email_verified) VALUES
  ('00000000-0000-0000-0000-000000000001', 'admin@kivio.example.com',
   '$2a$12$LX4d.xdSmPdl3R0IG1WNiucX4ePCnBM.gJI/YEoNMRYFdxkMUWMry',
   'システム管理者', 'ROLE_ADMIN', 'ACTIVE', true),
  ('00000000-0000-0000-0000-000000000002', 'seller1@kivio.example.com',
   '$2a$12$LX4d.xdSmPdl3R0IG1WNiucX4ePCnBM.gJI/YEoNMRYFdxkMUWMry',
   'テストセラー1', 'ROLE_SELLER', 'ACTIVE', true),
  ('00000000-0000-0000-0000-000000000003', 'buyer1@kivio.example.com',
   '$2a$12$LX4d.xdSmPdl3R0IG1WNiucX4ePCnBM.gJI/YEoNMRYFdxkMUWMry',
   'テストバイヤー1', 'ROLE_BUYER', 'ACTIVE', true);

INSERT INTO shops (id, owner_id, name, description, status) VALUES
  ('00000000-0000-0000-0001-000000000001',
   '00000000-0000-0000-0000-000000000002',
   'テストショップ', '開発用テストショップです', 'ACTIVE');

INSERT INTO shop_shipping_policies (shop_id, shipping_type) VALUES
  ('00000000-0000-0000-0001-000000000001', 'FREE');

INSERT INTO categories (id, name, slug, display_order) VALUES
  ('00000000-0000-0000-0002-000000000001', 'ファッション', 'fashion', 1),
  ('00000000-0000-0000-0002-000000000002', 'ハンドメイド', 'handmade', 2);

INSERT INTO products (id, shop_id, category_id, name, description, price, stock_quantity, status) VALUES
  ('00000000-0000-0000-0003-000000000001',
   '00000000-0000-0000-0001-000000000001',
   '00000000-0000-0000-0002-000000000002',
   'テスト商品1', '開発用テスト商品です', 1500, 10, 'ACTIVE'),
  ('00000000-0000-0000-0003-000000000002',
   '00000000-0000-0000-0001-000000000001',
   '00000000-0000-0000-0002-000000000002',
   'テスト商品2（在庫なし）', '在庫なしテスト用', 3000, 0, 'ACTIVE');

INSERT INTO carts (user_id) VALUES
  ('00000000-0000-0000-0000-000000000003');
