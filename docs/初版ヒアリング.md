# 初版ヒアリング記録
# マルチベンダー型マーケットプレイス「Kivio」

**実施日：** 2026年5月23〜24日  
**参加者：** プロジェクトオーナー（Dao Nguyen）、シニアエンジニア  
**目的：** 要件定義書（REQUIREMENTS.md）作成のための情報収集  
**ステータス：** 完了

---

## 第1回ヒアリング

### A. アーキテクチャ・インフラ

---

**A-1. デプロイ先について**

> デプロイについては、開発完了後、考え直す予定ですが、ポートフォリオ用途のため、コストと開発速度を重視して以下を想定しています。
> - Frontend: Vercel (Next.js)
> - Backend: Render または Railway (Spring Boot)
> - DB: PostgreSQL (Neon / Supabase)
>
> ただし、AWS構成（ECS / RDS）にも興味があり、就活向けアピールとして採用する可能性もあります。

**決定事項：** Vercel + Render/Railway + PostgreSQL (Neon/Supabase) を初期デプロイ先とする。AWS移行は将来検討。

---

**A-2. データベースについて**

> PostgreSQLでお願いします。

**決定事項：** PostgreSQL 16 を採用。

---

**A-3. 全文検索・商品検索の性能要件**

> まずは PostgreSQL の LIKE検索＋Indexでお願いします。

**決定事項：** PostgreSQL の LIKE検索 + インデックスで実装。Elasticsearch は将来対応。

---

### B. ビジネスロジック

---

**B-1. 手数料モデル**

> 初期状態は無料。システム管理者画面から簡単に手数料の設定を行えるようにする。

**決定事項：** デフォルト手数料率は0%（無料）。管理者が `PlatformConfig` テーブルから変更可能。

---

**B-2. 配送について**

> 初期段階では、実装コストと運用シンプルさを優先し、「送料込み」または「送料別（セラー設定）」程度を想定しています。
>
> 具体的には、ショップ単位で固定送料や送料無料条件を設定できる構成を考えています。
>
> ヤマト運輸・佐川急便などの配送業者API連携については、現時点では不要ですが、将来的に拡張できる設計は意識すること。

**決定事項：** ショップ単位の送料設定（固定送料 / 無料 / 無料になる閾値）を実装。配送業者API連携は将来拡張として設計上考慮のみ。

---

**B-3. 在庫管理**

> 商品に在庫数の概念は必要。

**決定事項：** `products.stock_quantity` で在庫数を管理。在庫0で購入不可。

---

**B-4. 返品・返金ポリシー**

> MVP段階では、複雑な返品・返金フローまでは含めず、「発送前のキャンセル」程度の簡易対応を想定しています。

**決定事項：** `PAYMENT_CONFIRMED` または `PROCESSING` ステータスのみキャンセル可能。Stripe Refunds APIで全額返金。

---

### C. リアルタイム機能

---

**C-1. リアルタイム実装方式**

> ポートフォリオとしてリアルタイム通信の実装経験を見せたいため、初期段階では Spring WebSocket / STOMP を利用した構成を想定しています。
>
> 主な用途は、購入者・出店者間チャット、注文通知などを想定しています。
>
> ただし、Redis Pub/Sub を利用した分散構成については、将来的なスケーラビリティ対応として拡張可能な設計を意識すること。

**決定事項：** Spring WebSocket / STOMP を採用。Redis Pub/Sub は抽象インターフェースを設計し、将来差し替え可能にする。

---

### D. 言語・地域

---

**D-1. 対応言語・通貨**

> 日本語メイン + i18n対応を意識した設計。通貨はまずJPYのみ。

**決定事項：** UIは日本語のみ。next-intl を使用し将来の多言語対応に備えた構造にする。通貨はJPY（円）のみ。

---

## 第2回ヒアリング

### E. 認証・セキュリティ

---

**E-1. 認証方式**

> JWT, OAuth2でお願いします。

**決定事項：** JWT（Access Token 15分 + Refresh Token 7日）+ OAuth2 を採用。

---

**E-2. Google OAuth の実装方針**

> フロントエンドとバックエンドを分離した構成を想定しているため、
> - NextAuth.js 側で Google OAuth ログイン
> - Google の ID Token をバックエンドへ送信
> - Spring Boot 側でトークン検証後、自前JWTを発行
>
> という構成を考えています。認証UIやセッション管理は NextAuth.js に任せつつ、認可・ロール管理・API保護は Spring Security + JWT で管理したいと考えています。

**決定事項：** NextAuth.js → Google ID Token送信 → Spring Boot検証 → 自前JWT発行の構成で実装。

---

**E-3. セラー申請・承認フロー**

> シンプル：PENDING → APPROVED / REJECTED。承認は管理者が手動で行う想定です。

**決定事項：** `SellerApplication` のステータスは `PENDING / APPROVED / REJECTED` の3種類。管理者が手動承認。

---

### F. 商品・カテゴリー管理

---

**F-1. 商品カテゴリーの運用**

> 管理者があらかじめ定義したカテゴリーからセラーが選ぶ方針でお願いします。

**決定事項：** カテゴリーは管理者が定義（親・子の階層構造）。セラーは選択のみ。

---

**F-2. 商品レビューの条件**（第3回で確定）

→ 後述

---

### G. 通知・メール

---

**G-1. メール通知**

> Resendでお願いします。

**決定事項：** Resend を使用。会員登録確認・注文確認・ステータス変更メールを送信。

---

**G-2. アプリ内通知の既読管理**

> 通知に「既読 / 未読」の概念は必要です。また、通知の保存期間についてまだ決めていないが、おすすめ方法でOKです。

**決定事項（エンジニア提案）：** 通知は既読/未読を管理し、保存期間は90日（`expires_at` カラムで管理）。

---

### H. フロントエンド

---

**H-1. UIコンポーネントライブラリ**

> shadcn/ui + Tailwind CSS

**決定事項：** shadcn/ui + Tailwind CSS 4.x を採用。

---

**H-2. 管理者パネルの分離**

> 同じNext.jsアプリ内で /admin/* ルートで実現

**決定事項：** 同一Next.jsアプリの `/admin/*` ルートで管理者画面を実装。ロールガードでアクセス制御。

---

### I. API設計・開発プロセス

---

**I-1. API仕様**

> REST API + Swagger

**決定事項：** REST API + springdoc-openapi（OpenAPI 3.0）。Swagger UIは `/swagger-ui.html` で公開。

---

**I-2. CI/CD**

> ビルド + テスト + 自動デプロイ（Render/Railway/Vercel連携）

**決定事項：** GitHub Actions でビルド・テスト・自動デプロイを実装。フロントエンドはVercel GitHub連携で自動デプロイ。

---

**I-3. テスト方針**

> ユニット + 統合テスト（APIレベル）

**決定事項：** JUnit 5 + Mockito（ユニット）+ Testcontainers + PostgreSQL（統合）を採用。E2Eは対象外。

---

## 追加質問・回答

---

### マルチテナント型権限設計について

**質問（オーナー）：**
> 買い手側とプラットフォーム管理者側にもさらに権限を分ける（1つのショップに複数の）といったマルチテナント型の権限設計にすべきですか。提案をお願いします。

**回答（エンジニア提案）：**

シンプルRBAC + 拡張可能なショップロール設計を推奨。

| フェーズ | 内容 |
|---|---|
| MVP | `ROLE_BUYER` / `ROLE_SELLER` / `ROLE_ADMIN` の3ロール。セラー1人 = ショップ1つ |
| 将来拡張 | `ShopMember(shopId, userId, shopRole: OWNER / MANAGER / STAFF)` を設計上考慮 |

ポートフォリオとして「設計の思慮深さ」を見せるために、将来の拡張ポイントをADRに明記しつつMVPはシンプルに保つ方針。

**オーナーの決定：**
> わかりました。提案してくれたシンプルRBAC + 拡張可能なショップロール設計でお願いします。

---

## 第3回ヒアリング（最終）

### J. その他

---

**J-1. 商品レビューの投稿条件**

> レビューは「その商品を購入済みのユーザーのみ」に限定。

**決定事項：** 注文が `COMPLETED` になった `OrderItem` に紐付くユーザーのみレビュー投稿可能。1 OrderItem につきレビューは1件。

---

**J-2. 画像ストレージ**

> Cloudinaryでお願いします。

**決定事項：** Cloudinary を採用。自動リサイズ・WebP変換・CDN配信を活用。商品画像は最大5枚。

---

**J-3. 売上金の出金（振込申請）機能**

> 実装コストが高いため、MVP段階では対象外としたい。

**決定事項：** 出金機能（Stripe Connect）はMVPスコープ外。将来対応として設計上考慮のみ。

---

**J-4. 手数料のStripe連携**

> MVP段階では、Stripe Connect を利用した自動分配までは行わず、「プラットフォーム側で決済を受け取り、システム上で手数料・セラー売上を計算する」シンプルな構成を想定しています。

**決定事項：** Stripeは全額受け取り。注文レコードに `commission_rate`・`commission_amount`・`seller_amount` を保存する計算モデルで実装。

---

## 決定事項サマリー

| カテゴリ | 決定内容 |
|---|---|
| デプロイ | Vercel + Render/Railway + PostgreSQL (Neon/Supabase) |
| バックエンド | Java 21 + Spring Boot 3.x + Gradle (Kotlin DSL) |
| フロントエンド | Next.js 15 (App Router) + TypeScript + shadcn/ui + Tailwind CSS 4.x |
| 認証 | NextAuth.js (Google OAuth) + Spring Security + JWT |
| DB | PostgreSQL 16 |
| 検索 | PostgreSQL LIKE + インデックス |
| リアルタイム | Spring WebSocket / STOMP（Redis Pub/Sub は将来拡張） |
| 画像 | Cloudinary（最大5枚/商品、自動変換・CDN） |
| 決済 | Stripe テストモード（Stripe Connectなし） |
| メール | Resend |
| 権限 | シンプルRBAC（BUYER / SELLER / ADMIN） |
| 在庫 | あり（stockQuantity管理） |
| キャンセル | 発送前のみ（PAYMENT_CONFIRMED / PROCESSING） |
| レビュー | 購入済みユーザーのみ（COMPLETED注文） |
| 通知 | アプリ内（WebSocket）+ メール（Resend）、90日保存 |
| テスト | ユニット + 統合テスト（Testcontainers） |
| CI/CD | GitHub Actions（ビルド + テスト + 自動デプロイ） |
| 出金 | MVPスコープ外 |

---

*本ヒアリング記録は `docs/REQUIREMENTS.md` の作成に使用されました。*
