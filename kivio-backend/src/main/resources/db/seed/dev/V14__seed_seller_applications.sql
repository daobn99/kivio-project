-- 開発用シードデータ：セラー申請（本番環境では実行しない）
-- application-dev.yaml の spring.flyway.locations に dev ディレクトリを追加することで読み込まれる。
--
-- /seller/applications/new は 1 URL に 4 状態（未申請 / PENDING / REJECTED / APPROVED）が同居する。
-- Seed だけで 4 状態すべてを再現できるようユーザーを割り当てる:
--   buyer1  … 申請なし    → 「申請フォーム」（/profile/* の動作確認にも使う主力ユーザーのため未申請のまま残す）
--   buyer2  … PENDING     → 「審査中」
--   buyer3  … REJECTED    → 「却下理由 + 再申請フォーム」
--   seller1 … APPROVED    → リダイレクト（ロールが ROLE_SELLER で申請と整合が取れた状態）

-- 検証用の BUYER を 2 名追加する。パスワードは buyer1 と同じ "Password1!"（V10 のハッシュを流用）
INSERT INTO users (id, email, password_hash, display_name, role, status) VALUES
  ('00000000-0000-0000-0000-000000000004', 'buyer2@kivio.example.com',
   '$2a$12$LZ/ytM.tKlqwpviBhR0ppuFlS.0nZB7AO1Pv17N9/hByXDy1Xvz8W',
   'テストバイヤー2（審査中）', 'ROLE_BUYER', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000005', 'buyer3@kivio.example.com',
   '$2a$12$LZ/ytM.tKlqwpviBhR0ppuFlS.0nZB7AO1Pv17N9/hByXDy1Xvz8W',
   'テストバイヤー3（却下済み）', 'ROLE_BUYER', 'ACTIVE');

-- カートはユーザーごとに 1 つ（V10 の buyer1 と同じ扱い）
INSERT INTO carts (user_id) VALUES
  ('00000000-0000-0000-0000-000000000004'),
  ('00000000-0000-0000-0000-000000000005');

INSERT INTO seller_applications
  (id, applicant_id, reason, status, reviewer_id, review_comment, reviewed_at, created_at) VALUES
  -- seller1: 承認済み（審査者は admin）
  ('00000000-0000-0000-0005-000000000001',
   '00000000-0000-0000-0000-000000000002',
   'ハンドメイドのアクセサリーを制作しており、Kivio で販売したいと考えています。',
   'APPROVED',
   '00000000-0000-0000-0000-000000000001',
   '審査の結果、出品者として承認いたします。',
   NOW() - INTERVAL '29 days',
   NOW() - INTERVAL '30 days'),

  -- buyer2: 審査中（reviewer_id / review_comment / reviewed_at は NULL）
  ('00000000-0000-0000-0005-000000000002',
   '00000000-0000-0000-0000-000000000004',
   '自家焙煎のコーヒー豆を販売したいです。実店舗を 3 年運営しており、オンラインにも販路を広げたいと考えています。',
   'PENDING',
   NULL, NULL, NULL,
   NOW() - INTERVAL '2 days'),

  -- buyer3: 却下（再申請フォームの確認用。REJECTED は新規申請を妨げない）
  ('00000000-0000-0000-0005-000000000003',
   '00000000-0000-0000-0000-000000000005',
   '古着を売りたい',
   'REJECTED',
   '00000000-0000-0000-0000-000000000001',
   '申請理由の記載が不十分です。取り扱う商品の詳細と仕入れ方法を具体的にご記入のうえ、再度ご申請ください。',
   NOW() - INTERVAL '5 days',
   NOW() - INTERVAL '7 days');
