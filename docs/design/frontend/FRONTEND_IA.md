# フロントエンド情報アーキテクチャ（IA）
# マルチベンダー型マーケットプレイス「Kivio」

**作成日：** 2026年5月31日  
**対象スタック：** Next.js App Router + shadcn/ui + Tailwind CSS  
**参照：** `docs/requirements/REQUIREMENTS.md`、`docs/design/USER_FLOW.md`

---

## 1. 画面 URL 一覧

### 1.1 公開画面（認証不要）

| パス | 画面名 | ロール制限 | 認証 | フェーズ |
|---|---|---|---|---|
| `/` | ホーム（商品一覧） | 全ユーザー | 不要 | Phase 2 |
| `/products/[id]` | 商品詳細 | 全ユーザー | 不要 | Phase 2 |
| `/shops/[id]` | ショップ詳細 | 全ユーザー | 不要 | Phase 2 |
| `/search` | 商品検索 | 全ユーザー | 不要 | Phase 3 |
| `/auth/login` | ログイン | 未認証のみ | 不要 | Phase 2 |
| `/auth/register` | 新規登録（2ステップ） | 未認証のみ | 不要 | Phase 2 |
| `/auth/verify-email` | メール認証・自動ログイン処理 | 未認証のみ | 不要 | Phase 2 |

### 1.2 バイヤー画面（BUYER 以上）

> SELLER は BUYER 権限も保持するため、以下ページはすべて SELLER からもアクセス可能（要件 §2.2 「ロールは排他でない」）。

| パス | 画面名 | ロール制限 | 認証 | フェーズ |
|---|---|---|---|---|
| `/cart` | カート | BUYER 以上 | 必要 | Phase 3 |
| `/checkout` | チェックアウト | BUYER 以上 | 必要 | Phase 3 |
| `/orders` | 注文履歴一覧 | 本人のみ | 必要 | Phase 3 |
| `/orders/[id]` | 注文詳細 | 本人のみ | 必要 | Phase 3 |
| `/wishlist` | お気に入り | BUYER 以上 | 必要 | Phase 5 |
| `/messages` | チャット一覧 | BUYER / SELLER | 必要 | Phase 4 |
| `/messages/[roomId]` | チャットルーム | 当事者のみ | 必要 | Phase 4 |

### 1.2b 認証済み共通画面（全ロール）

| パス | 画面名 | ロール制限 | 認証 | フェーズ |
|---|---|---|---|---|
| `/profile/settings` | プロフィール設定 | 本人のみ（BUYER / SELLER / ADMIN） | 必要 | Phase 2 |
| `/profile/addresses` | 配送先住所管理 | 本人のみ（BUYER / SELLER） | 必要 | Phase 3 |

### 1.3 セラー画面（SELLER 以上）

| パス | 画面名 | ロール制限 | 認証 | フェーズ |
|---|---|---|---|---|
| `/seller/applications/new` | セラー申請フォーム / 審査状況表示 | **BUYER のみ**（SELLER・ADMIN は不可） | 必要 | Phase 2 |
| `/seller/dashboard` | セラーダッシュボード | SELLER 以上 | 必要 | Phase 2 |
| `/seller/products` | 商品管理一覧 | SELLER（本人） | 必要 | Phase 2 |
| `/seller/products/new` | 商品登録フォーム | SELLER（本人） | 必要 | Phase 2 |
| `/seller/products/[id]/edit` | 商品編集フォーム | SELLER（本人） | 必要 | Phase 2 |
| `/seller/orders` | 注文管理 | SELLER（本人） | 必要 | Phase 3 |
| `/seller/shop/settings` | ショップ設定 | SELLER（本人） | 必要 | Phase 2 |

### 1.4 管理者画面（ADMIN のみ）

| パス | 画面名 | ロール制限 | 認証 | フェーズ |
|---|---|---|---|---|
| `/admin/dashboard` | 管理者ダッシュボード | ADMIN のみ | 必要 | Phase 4 |
| `/admin/seller-applications` | セラー申請一覧 | ADMIN のみ | 必要 | Phase 2 |
| `/admin/users` | ユーザー管理 | ADMIN のみ | 必要 | Phase 4 |
| `/admin/products` | 商品モデレーション | ADMIN のみ | 必要 | Phase 4 |
| `/admin/categories` | カテゴリー管理 | ADMIN のみ | 必要 | Phase 4 |
| `/admin/platform-configs` | プラットフォーム設定 | ADMIN のみ | 必要 | Phase 4 |

### 1.5 システム画面

| パス | 画面名 | フェーズ |
|---|---|---|
| `/not-found` (not-found.tsx) | 404 ページ | Phase 2 |
| `/error` (error.tsx) | 500 エラーページ | Phase 2 |
| `/checkout/success` | 決済完了・注文サンクスページ | Phase 3 |

> `/checkout/success?orderId={id}` — Stripe のリダイレクト後に表示。注文番号・金額・次のアクション（注文詳細を見る）を表示する。`/orders/{id}` へのリンクを提供。

---

## 2. ナビゲーション構造

### 2.1 グローバルヘッダー（全画面共通）

```
グローバルヘッダー
├── 未認証
│   ├── ロゴ（Kivio）→ / へリンク
│   ├── 商品検索バー → /search
│   ├── ログインボタン → /auth/login
│   └── 新規登録ボタン → /auth/register
│
├── BUYER
│   ├── ロゴ（Kivio）→ / へリンク
│   ├── 商品検索バー → /search
│   ├── ハートアイコン（お気に入り件数バッジ）→ /wishlist
│   ├── カートアイコン（カート件数バッジ）→ /cart
│   ├── チャットアイコン（未読数バッジ）→ /messages
│   ├── 通知ベルアイコン（未読数バッジ）→ 通知ドロップダウン
│   └── アバターメニュー（DropdownMenu）
│       ├── プロフィール設定 → /profile/settings
│       ├── 注文履歴 → /orders
│       ├── セラー申請 → /seller/applications/new
│       │   ※ 申請未済かつ PENDING 申請なしの場合のみ表示
│       └── ログアウト
│
├── SELLER
│   │   ※ SELLER は BUYER 権限も持つため、購入系 UI も表示する
│   ├── ロゴ（Kivio）→ / へリンク
│   ├── 商品検索バー → /search
│   ├── ハートアイコン（お気に入り件数バッジ）→ /wishlist
│   ├── カートアイコン（カート件数バッジ）→ /cart
│   │   ※ 自ショップの商品はカートに追加不可（CANNOT_PURCHASE_OWN_PRODUCT）
│   ├── チャットアイコン（未読数バッジ）→ /messages
│   ├── 通知ベルアイコン（未読数バッジ）→ 通知ドロップダウン
│   ├── セラーダッシュボードボタン → /seller/dashboard
│   └── アバターメニュー（DropdownMenu）
│       ├── プロフィール設定 → /profile/settings
│       ├── 注文履歴（バイヤーとして）→ /orders
│       └── ログアウト
│
└── ADMIN
    ├── ロゴ（Kivio - Admin）→ /admin/dashboard
    ├── 通知ベルアイコン → 通知ドロップダウン
    ├── 管理メニュー（DropdownMenu）
    │   ├── セラー申請管理 → /admin/seller-applications
    │   ├── ユーザー管理 → /admin/users
    │   ├── 商品モデレーション → /admin/products
    │   ├── カテゴリー管理 → /admin/categories
    │   └── プラットフォーム設定 → /admin/platform-configs
    └── アバターメニュー → ログアウト
```

### 2.2 セラーサイドナビ（/seller/* 共通）

```
セラーサイドナビ（SellerSidebar）
├── ダッシュボード → /seller/dashboard
│   └── 売上サマリー・直近注文
├── 商品管理 → /seller/products
│   ├── 商品一覧
│   └── 商品登録 → /seller/products/new（ボタン）
├── 注文管理 → /seller/orders
│   └── ステータス別フィルター
└── ショップ設定 → /seller/shop/settings
    ├── 基本情報（名前・説明・ロゴ）
    └── 配送ポリシー
```

### 2.3 モバイルナビ（375px）

```
モバイルナビ（Sheet コンポーネントで実装）
├── ハンバーガーアイコンでトグル
├── 全メニュー項目を縦並びで表示
└── オーバーレイ背景でクローズ
```

---

## 3. Next.js App Router レイアウト構造

```
src/app/
├── layout.tsx                         # RootLayout（ThemeProvider・Navbar・Footer）
│
├── (public)/                          # 認証不要グループ
│   ├── page.tsx                       # ホーム /
│   ├── products/[id]/page.tsx         # 商品詳細 /products/[id]
│   ├── shops/[id]/page.tsx            # ショップ詳細 /shops/[id]
│   └── search/page.tsx                # 商品検索 /search
│
├── auth/                              # /auth/* セグメント（ルートグループではなく実パス）
│   ├── (auth-group)/                  # AuthLayout 適用グループ（URLに影響しない）
│   │   ├── layout.tsx                 # AuthLayout（Navbar なし・ロゴ + カード）
│   │   ├── login/page.tsx             # ログイン /auth/login
│   │   ├── register/page.tsx          # 新規登録 /auth/register
│   │   └── verify-email/page.tsx      # メール認証 + 自動ログイン /auth/verify-email
│   │       #   token を URL から取得 → Body に詰め替えて API コール
│   │       #   成功: JWT 受け取り → router.replace('/') → ホームへ
│   │       #   失敗: エラー表示 + 再送信ボタン
│
├── (authenticated)/                   # ルートグループ（URLに影響しない）                   # 全認証ユーザー共通グループ（BUYER・SELLER・ADMIN）
│   ├── profile/
│   │   ├── settings/page.tsx         # プロフィール設定 /profile/settings
│   │   └── addresses/page.tsx        # 配送先住所管理 /profile/addresses
│   └── seller/
│       └── applications/
│           └── new/page.tsx          # セラー申請 /seller/applications/new
│               ※ BUYERのみ。SELLER/ADMIN はミドルウェアが/にリダイレクト
│
├── (buyer)/                           # BUYER 認証必須グループ（SELLER も利用可）
│   ├── cart/page.tsx                  # カート /cart
│   ├── checkout/page.tsx              # チェックアウト /checkout
│   ├── orders/
│   │   ├── page.tsx                   # 注文履歴 /orders
│   │   └── [id]/page.tsx             # 注文詳細 /orders/[id]
│   ├── wishlist/page.tsx              # お気に入り /wishlist
│   └── messages/
│       ├── page.tsx                   # チャット一覧 /messages
│       └── [roomId]/page.tsx         # チャットルーム /messages/[roomId]
│
├── seller/
│   └── (management)/                  # SELLER 認証必須グループ（SellerLayout: サイドナビ付き）
│       ├── layout.tsx                 # SellerLayout（SellerSidebar）
│       ├── dashboard/page.tsx         # ダッシュボード /seller/dashboard
│       ├── products/
│       │   ├── page.tsx               # 商品管理一覧 /seller/products
│       │   ├── new/page.tsx           # 商品登録 /seller/products/new
│       │   └── [id]/edit/page.tsx    # 商品編集 /seller/products/[id]/edit
│       ├── orders/page.tsx            # 注文管理 /seller/orders
│       └── shop/settings/page.tsx     # ショップ設定 /seller/shop/settings
│
├── (admin)/                           # ADMIN 認証必須グループ
│   ├── layout.tsx                     # AdminLayout（管理者ナビ）
│   └── admin/
│       ├── dashboard/page.tsx         # 管理者ダッシュボード /admin/dashboard
│       ├── seller-applications/page.tsx  # セラー申請管理
│       ├── users/page.tsx             # ユーザー管理
│       ├── products/page.tsx          # 商品モデレーション
│       ├── categories/page.tsx        # カテゴリー管理
│       └── platform-configs/page.tsx  # プラットフォーム設定
│
├── not-found.tsx                      # 404 ページ
└── error.tsx                          # 500 エラーページ
```

---

## 4. 画面ごとのコンテンツ優先順位

### 4.1 ホーム（/）

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 商品検索バー（ヒーロー） | — |
| 2 | 新着商品グリッド（20件） | `GET /api/v1/products?sort=createdAt,desc` |
| 3 | カテゴリー絞り込みタブ | `GET /api/v1/categories` |
| 4 | ページネーション | — |

### 4.2 商品詳細（/products/[id]）

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 商品画像ギャラリー | `product.images` |
| 2 | 商品名・価格（¥形式）・在庫状況 | `GET /api/v1/products/{id}` |
| 3 | 購入ボタン（モバイル: スティッキー） | `POST /api/v1/cart/items` |
| 4 | ショップ情報（ロゴ・名前） | `product.shop` |
| 5 | タブ: 商品説明 / レビュー一覧 | `GET /api/v1/products/{id}/reviews` |

### 4.3 セラーダッシュボード（/seller/dashboard）

| 優先度 | コンテンツ | データソース | フェーズ |
|---|---|---|---|
| 1 | KPIカード（今月売上・注文数・セラー取り分） | `GET /api/v1/seller/dashboard` | Phase 2 |
| 2 | 直近の注文一覧（10件） | `GET /api/v1/orders?size=10` | Phase 2 |
| 3 | 日別売上グラフ（30日間）DASH-02 Should | `GET /api/v1/seller/dashboard` | Phase 4 |
| 4 | 人気商品ランキング（売上数順）DASH-03 Should | `GET /api/v1/seller/dashboard` | Phase 4 |

### 4.4 管理者セラー申請一覧（/admin/seller-applications）

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | PENDING 申請一覧 | `GET /api/v1/admin/seller-applications?status=PENDING` |
| 2 | 申請詳細（申請者情報・理由） | インライン展開 |
| 3 | 承認 / 却下ボタン | `POST /api/v1/admin/seller-applications/{id}/approve|reject` |

### 4.5 商品検索（/search）— Phase 3

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 検索バー（クエリ表示・クリア） | URLクエリ `?q=` |
| 2 | フィルターパネル（カテゴリー・価格帯・在庫あり） | `GET /api/v1/categories` |
| 3 | ソート選択（新着・価格昇降順・人気順） | URLクエリ `?sort=` |
| 4 | 商品グリッド（20件/ページ） | `GET /api/v1/products?q=&categoryId=&sort=` |
| 5 | ページネーション | `PageResponse.totalPages` |
| 6 | Empty State（0件時） | — |

### 4.6 カート（/cart）— Phase 3

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | カート明細一覧（商品画像・名前・単価・数量） | `GET /api/v1/cart` |
| 2 | 数量変更スピナー・削除ボタン | `PATCH / DELETE /api/v1/cart/items/{itemId}` |
| 3 | 在庫切れ・削除済み商品の警告バナー | `cartItem.product.status` |
| 4 | 小計・合計サマリー | ローカル計算 |
| 5 | チェックアウトへ進むボタン（スティッキー） | → `/checkout` |
| 6 | Empty State（カートが空の場合） | — |

### 4.7 チェックアウト（/checkout）— Phase 3

> **2ステップ UI:** ステップインジケーター（住所 → 支払い）を表示し、ユーザーに進捗を伝える（UX: multi-step-progress）。  
> **マルチショップ分割:** PAY-07 により複数ショップの注文は分割表示する（ショップ別に区分けして金額・送料を明示）。

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | ステップインジケーター（Step 1: 住所 / Step 2: 支払い） | ローカル状態 |
| 2 | 注文明細サマリー（ショップ別グループ・商品・数量） | カートデータ引き継ぎ |
| 3 | 配送先住所選択／新規入力フォーム | `GET /api/v1/users/me/addresses` |
| 4 | 金額内訳（商品小計・送料・手数料・合計）ショップ別 | バックエンド計算値 |
| 5 | Stripe Payment Element | `clientSecret` from checkout API |
| 6 | 注文確定ボタン（決済中はローディング・ボタン無効化） | `POST /api/v1/orders/checkout` |

### 4.8 注文履歴（/orders）— Phase 3

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 注文カード一覧（注文番号・日時・ステータスバッジ・合計） | `GET /api/v1/orders` |
| 2 | ステータスフィルタータブ | URLクエリ `?status=` |
| 3 | ページネーション | `PageResponse` |
| 4 | Empty State（注文なし） | — |

### 4.9 注文詳細（/orders/[id]）— Phase 3

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | ステータス表示（プログレスステッパー） | `GET /api/v1/orders/{id}` |
| 2 | 注文明細リスト（スナップショット: 商品名・画像・単価・数量） | `order.orderItems` |
| 3 | 配送先住所 | `order.address` |
| 4 | 金額内訳（小計・送料・手数料・合計） | `order.*Amount` |
| 5 | キャンセルボタン（PAYMENT_CONFIRMED・PROCESSING のみ表示） | `POST /api/v1/orders/{id}/cancel` |
| 6 | WebSocket によるステータス自動更新 | `/topic/orders/{orderId}` |

### 4.10 セラー注文管理（/seller/orders）— Phase 3

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 注文一覧（ステータスフィルタータブ付き） | `GET /api/v1/orders` |
| 2 | 注文明細・配送先・合計金額 | インライン展開 |
| 3 | ステータス更新ボタン（次のステータスへ） | `PATCH /api/v1/orders/{id}/status` |
| 4 | キャンセルボタン（対象ステータスのみ） | `POST /api/v1/orders/{id}/cancel` |

### 4.11 配送先住所管理（/profile/addresses）— Phase 3

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 登録済み住所一覧（デフォルト住所ハイライト） | `GET /api/v1/users/me/addresses` |
| 2 | 住所追加フォーム（Sheet/Modal） | `POST /api/v1/users/me/addresses` |
| 3 | 住所編集・削除 | `PATCH / DELETE /api/v1/users/me/addresses/{id}` |

### 4.12 チャット一覧（/messages）— Phase 4

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | チャットルーム一覧（相手アバター・最終メッセージ・日時・未読バッジ） | `GET /api/v1/chat-rooms` |
| 2 | ルームをクリックで遷移 | → `/messages/{roomId}` |
| 3 | Empty State（チャット未開始） | — |

### 4.13 チャットルーム（/messages/[roomId]）— Phase 4

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | メッセージ一覧（バブル形式・送信者アバター・日時） | `GET /api/v1/chat-rooms/{id}` |
| 2 | メッセージ入力欄（送信ボタン・Enter 送信） | STOMP `/app/chat.send` |
| 3 | WebSocket リアルタイム受信 | `/topic/chat/{chatRoomId}` |
| 4 | 無限スクロール（過去履歴をページネーション取得） | `GET /api/v1/chat-rooms/{id}?page=` |

### 4.14 管理者ダッシュボード（/admin/dashboard）— Phase 4

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | KPI カード（総ユーザー数・総注文数・総売上） | `GET /api/v1/admin/dashboard` |
| 2 | PENDING セラー申請件数リンク | `GET /api/v1/admin/seller-applications?status=PENDING` |
| 3 | 最近の注文一覧（10件） | `GET /api/v1/admin/dashboard` |
| 4 | ユーザー増加グラフ（Should） | — |

### 4.15 管理者ユーザー管理（/admin/users）— Phase 4

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | ユーザー一覧（メール・ロール・ステータス・登録日） | `GET /api/v1/admin/users` |
| 2 | 検索・フィルター（ロール・ステータス別） | URLクエリ |
| 3 | 無効化 / 有効化ボタン | `PATCH /api/v1/admin/users/{id}/status` |

### 4.16 管理者商品モデレーション（/admin/products）— Phase 4

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 商品一覧（画像・名前・ショップ・ステータス） | `GET /api/v1/admin/products` |
| 2 | ステータスフィルター | URLクエリ `?status=` |
| 3 | INACTIVE 化ボタン（モデレーション） | `PATCH /api/v1/admin/products/{id}/status` |

### 4.17 管理者カテゴリー管理（/admin/categories）— Phase 4

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | カテゴリーツリー表示（親・子の階層） | `GET /api/v1/admin/categories` |
| 2 | カテゴリー新規追加フォーム | `POST /api/v1/admin/categories` |
| 3 | インライン編集・削除（確認ダイアログ付き） | `PATCH / DELETE /api/v1/admin/categories/{id}` |

### 4.18 管理者プラットフォーム設定（/admin/platform-configs）— Phase 4

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 設定一覧（キー・現在値・説明） | `GET /api/v1/admin/platform-configs` |
| 2 | インライン編集フォーム | `PATCH /api/v1/admin/platform-configs/{key}` |
| 3 | 変更履歴（監査ログ参照） | — |

### 4.19 お気に入り（/wishlist）— Phase 5

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | お気に入り商品グリッド | `GET /api/v1/wishlist` |
| 2 | ハートアイコンでお気に入り解除 | `DELETE /api/v1/wishlist/{productId}` |
| 3 | 商品カードクリックで商品詳細へ | → `/products/{id}` |
| 4 | Empty State（お気に入りなし） | — |

### 4.20 商品詳細レビュータブ — Phase 5

| 優先度 | コンテンツ | データソース |
|---|---|---|
| 1 | 平均評価（星表示）・レビュー件数 | `product.averageRating` |
| 2 | レビュー一覧（評価・コメント・投稿者・日時） | `GET /api/v1/products/{id}/reviews` |
| 3 | レビュー投稿フォーム（購入済みユーザーのみ表示） | `POST /api/v1/order-items/{id}/review` |
| 4 | 「レビューを書く」ボタン（COMPLETED 注文明細がある場合のみ） | — |

---

## 5. middleware.ts 認証ガード設計

```ts
// ① 未認証ガード — 未ログインなら /auth/login?redirect=<元のURL> へ
const AUTH_REQUIRED_PATHS = [
  '/cart', '/checkout', '/orders', '/wishlist',
  '/messages', '/profile', '/seller', '/admin',
]

// ② SELLER 専用ガード — ROLE_SELLER のみ通過
const SELLER_ONLY_PATHS = [
  '/seller/dashboard',
  '/seller/products',
  '/seller/orders',
  '/seller/shop',
]
// 違反: BUYER / ADMIN → / にリダイレクト

// ③ BUYER 限定ガード — ROLE_BUYER かつ申請状況を確認
const BUYER_ONLY_PATHS = ['/seller/applications/new']
// SELLER / ADMIN → /seller/dashboard または / にリダイレクト
// PENDING 申請あり → 申請状況ページへリダイレクト

// ④ ADMIN 専用ガード — ROLE_ADMIN のみ通過
const ADMIN_ONLY_PATHS = ['/admin']
// 違反: / にリダイレクト

// ⑤ 認証済みリダイレクト — ログイン済みユーザーが /auth/* にアクセス
// → ロールに応じて / または /seller/dashboard にリダイレクト
```

---

## 6. Kivio デザイン方針書

### 6.1 ブランド方針

| 項目 | 方針 |
|---|---|
| ターゲット印象 | 信頼感・親しみやすさ・ECとしての明瞭さ |
| スタイル方向 | ミニマル・クリーン（過度な装飾を避ける） |
| トーン | 温かみのあるプロフェッショナル（冷たさを排除） |
| ユーザー層 | 個人・小規模事業者 → 難しくなく、わかりやすいUI |

### 6.2 デザイントークン方針（/ui-ux-pro-max で選定予定）

| 項目 | 方針 | 選定タイミング |
|---|---|---|
| カラー方向 | 中性系または暖色系（過度な寒色を避ける） | Step 2: `/ui-ux-pro-max` で選定 |
| プライマリカラー | ブランドを象徴する1色（CTA・リンク・アクセント） | Step 2 |
| フォント（日本語） | Noto Sans JP（必須） | Step 2 |
| フォント（欧文） | Noto Sans JP と相性の良い欧文フォント | Step 2: `/ui-ux-pro-max` で選定 |
| ダークモード | 対応する（shadcn/ui ThemeProvider） | Step 2 |
| ボーダーラジウス | 0.5rem（shadcn/ui デフォルト・角丸で親しみやすさを演出） | Step 2 |

### 6.3 レスポンシブブレークポイント

| 名前 | 幅 | 対象デバイス |
|---|---|---|
| モバイル | 375px〜 | スマートフォン（ベースライン） |
| タブレット | 768px〜 | タブレット・大型スマートフォン |
| デスクトップ | 1280px〜 | PC・ラップトップ |

**実装方針:** モバイルファースト（`sm:` → `md:` → `lg:` の順で上書き）

### 6.4 アクセシビリティ方針

- WCAG 2.1 AA 準拠を目標
- フォーカスリング: `focus-visible:` クラスで常に表示
- カラーコントラスト比: テキスト 4.5:1 以上、大テキスト 3:1 以上
- shadcn/ui の `aria-*` 属性をそのまま活用
- 全インタラクティブ要素にキーボード操作対応

### 6.5 インタラクション統一値

| 用途 | duration | easing |
|---|---|---|
| ボタン Hover | 100ms | ease-in |
| モーダル / Sheet | 200ms | ease-out |
| ページ遷移 | 150ms | ease-in-out |
| Skeleton パルス | 1500ms（ループ） | ease-in-out |
| Toast 表示 / 消去 | 300ms / 200ms | ease-in-out |

### 6.6 コンポーネント状態の実装方針

全コンポーネントで以下の3状態を標準実装する:

| 状態 | 実装方法 |
|---|---|
| **Loading / Skeleton** | `<Skeleton>` コンポーネントで骨格表示 |
| **Empty State** | `<EmptyState icon title description action />` 汎用コンポーネント |
| **Error State** | `error.tsx`（Next.js）+ インライン `<ErrorFallback>` |

---

## 7. Phase 別チェックリスト

### 7.1 Phase 2 チェックリスト — 完了

#### USER_FLOW.md

- [x] バイヤー：認証フロー（新規登録・ログイン）の Mermaid 図
- [x] バイヤー：未認証リダイレクトフローの Mermaid 図
- [x] バイヤー：購入フロー（Phase 3 参考）の Mermaid 図
- [x] セラー：申請 → 承認 → 商品登録フローの Mermaid 図
- [x] 管理者：セラー承認フローの Mermaid 図
- [x] 共通：Token リフレッシュフローの Mermaid 図

#### FRONTEND_IA.md

- [x] Phase 2 対象 URL の一覧確定（認証要否・ロール制限）
- [x] グローバルヘッダーの 4 状態（未認証・BUYER・SELLER・ADMIN）を定義
- [x] セラーサイドナビの構造を定義
- [x] Next.js App Router のレイアウト構造を設計
- [x] 主要画面のコンテンツ優先順位を定義
- [x] middleware.ts の認証ガード設計
- [x] デザイン方針書（ブランド・カラー・フォント・アクセシビリティ）を記述

---

### 7.2 Phase 3 チェックリスト（商品検索・カート・決済・注文管理）

#### USER_FLOW.md

- [x] 商品検索フロー（キーワード・フィルター・ソート）の Mermaid 図
- [x] カート操作フロー（追加・数量変更・削除・在庫切れ警告）の Mermaid 図
- [x] チェックアウト・Stripe 決済フロー（Payment Intent・Webhook）の Mermaid 図
- [x] バイヤー注文管理フロー（履歴・詳細・キャンセル・WebSocket 更新）の Mermaid 図
- [x] セラー注文受付・ステータス更新フロー（PROCESSING → SHIPPED → COMPLETED）の Mermaid 図

#### FRONTEND_IA.md

- [x] 商品検索（/search）のコンテンツ優先順位を定義
- [x] カート（/cart）のコンテンツ優先順位を定義
- [x] チェックアウト（/checkout）のコンテンツ優先順位を定義
- [x] 注文履歴（/orders）のコンテンツ優先順位を定義
- [x] 注文詳細（/orders/[id]）のコンテンツ優先順位を定義
- [x] セラー注文管理（/seller/orders）のコンテンツ優先順位を定義
- [x] 配送先住所管理（/profile/addresses）のコンテンツ優先順位を定義

#### 実装タスク（Phase 3 開始時に確認）

- [ ] `POST /api/v1/orders/checkout` → Stripe clientSecret の受け取りと Payment Element の組み込み
- [ ] Stripe Webhook エンドポイント（`/api/v1/webhooks/stripe`）の署名検証を実装
- [ ] WebSocket で `/topic/orders/{orderId}` を購読し注文詳細画面をリアルタイム更新
- [ ] カート内の在庫切れ・削除済み商品の警告 UI
- [ ] チェックアウト画面でショップ別の送料計算ロジックを表示

---

### 7.3 Phase 4 チェックリスト（チャット・通知・管理者機能）

#### USER_FLOW.md

- [x] チャット開始フロー（バイヤー → セラー、チャットルーム作成/取得）の Mermaid 図
- [x] チャット・リアルタイムメッセージフロー（STOMP WebSocket）の Mermaid 図
- [x] 通知管理フロー（詳細版: 個別既読・全既読・フィルター）の Mermaid 図
- [x] 管理者カテゴリー管理フロー（ツリー表示・作成・編集・削除）の Mermaid 図
- [x] プラットフォーム設定フロー（手数料率更新・監査ログ）の Mermaid 図
- [x] 管理者ダッシュボードフロー（KPI・申請件数・注文一覧）の Mermaid 図

#### FRONTEND_IA.md

- [x] チャット一覧（/messages）のコンテンツ優先順位を定義
- [x] チャットルーム（/messages/[roomId]）のコンテンツ優先順位を定義
- [x] 管理者ダッシュボード（/admin/dashboard）のコンテンツ優先順位を定義
- [x] ユーザー管理（/admin/users）のコンテンツ優先順位を定義
- [x] 商品モデレーション（/admin/products）のコンテンツ優先順位を定義
- [x] カテゴリー管理（/admin/categories）のコンテンツ優先順位を定義
- [x] プラットフォーム設定（/admin/platform-configs）のコンテンツ優先順位を定義

#### 実装タスク（Phase 4 開始時に確認）

- [ ] `@stomp/stompjs` で WebSocket 接続を共通フック（`useWebSocket`）に集約
- [ ] `/topic/chat/{chatRoomId}` の購読とメッセージバブル UI
- [ ] `/topic/notifications/{userId}` の購読とナビバー通知バッジのリアルタイム更新
- [ ] 管理者カテゴリーツリー UI（Radix Tree / 独自実装）
- [ ] セラーダッシュボードの日別売上グラフ（Recharts LineChart）

---

### 7.4 Phase 5 チェックリスト（レビュー・お気に入り・メール通知）

#### USER_FLOW.md

- [x] レビュー投稿フロー（COMPLETED 注文明細限定・重複チェック）の Mermaid 図
- [x] お気に入りフロー（追加・削除・未認証リダイレクト）の Mermaid 図
- [x] メール通知フロー（会員登録確認・注文確定・ステータス変更・キャンセル）の Mermaid 図

#### FRONTEND_IA.md

- [x] お気に入り（/wishlist）のコンテンツ優先順位を定義
- [x] 商品詳細レビュータブのコンテンツ優先順位を定義

#### 実装タスク（Phase 5 開始時に確認）

- [ ] 注文詳細 `/orders/[id]` に「レビューを書く」ボタンを COMPLETED かつ未レビュー明細のみ表示
- [ ] レビュー投稿フォーム（星評価 UI + コメント Textarea + Zod バリデーション）
- [ ] 商品詳細の平均評価星表示コンポーネント（Recharts または カスタム）
- [ ] お気に入り状態の Zustand ストアまたは TanStack Query によるキャッシュ管理
- [ ] ハートアイコンのオプティミスティック UI 更新
- [ ] メール通知テンプレート（Resend + React Email）の実装確認

---

**以上**

*Step 2（デザインシステム初期化）では `/ui-ux-pro-max` スキルを使用してカラーパレットとフォントペアを決定し、`globals.css` と `tailwind.config.ts` に反映する。*
