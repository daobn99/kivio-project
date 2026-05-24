# 要件定義書
# マルチベンダー型マーケットプレイス「Kivio」

**作成日：** 2026年5月24日  
**作成者：** Dao Nguyen  
**バージョン：** 1.0  
**ステータス：** 確定  
**参照元：** [RFP v1.0](./RFP.md)

---

## 目次

1. [プロジェクト概要](#1-プロジェクト概要)
2. [ユーザーロールと権限](#2-ユーザーロールと権限)
3. [機能要件](#3-機能要件)
4. [非機能要件](#4-非機能要件)（4.6 データ整合性・削除方針 追加）
5. [システムアーキテクチャ](#5-システムアーキテクチャ)
6. [技術スタック](#6-技術スタック)
7. [データモデル](#7-データモデル)
8. [API設計方針](#8-api設計方針)
9. [リアルタイム通信設計](#9-リアルタイム通信設計)
10. [決済フロー設計](#10-決済フロー設計)
11. [開発・デプロイ要件](#11-開発デプロイ要件)
12. [フェーズ別スコープ](#12-フェーズ別スコープ)
13. [スコープ外（将来対応）](#13-スコープ外将来対応)
14. [用語集](#14-用語集)
15. [データ保持ポリシー](#15-データ保持ポリシー)

---

## 1. プロジェクト概要

### 1.1 サービス概要

| 項目 | 内容 |
|---|---|
| サービス名 | Kivio |
| コンセプト | 誰でも5分でお店が開ける、売り手と買い手がつながるマーケットプレイス |
| 目的 | 個人・小規模事業者向けの低コスト・低障壁なECプラットフォームの提供 |
| 用途 | ポートフォリオ（実商取引なし、テストデータのみ） |

### 1.2 ターゲットユーザー

| ロール | 説明 |
|---|---|
| バイヤー（買い手） | ユニークな商品を探す一般消費者 |
| セラー（売り手） | ハンドメイド作家・副業物販者・小規模事業者 |
| アドミン（管理者） | プラットフォーム運営者（開発者自身） |

---

## 2. ユーザーロールと権限

### 2.1 システムロール定義

```
ROLE_BUYER   → 商品閲覧・購入・レビュー・チャット
ROLE_SELLER  → バイヤー権限 + ショップ・商品管理・注文管理・売上確認
ROLE_ADMIN   → 全機能 + ユーザー管理・セラー承認・プラットフォーム設定
```

### 2.2 ロール付与ルール

- ユーザー登録時のデフォルトロールは `ROLE_BUYER`
- セラー申請（`SellerApplication`）が管理者に **APPROVED** された時点で `ROLE_SELLER` に昇格
- `ROLE_ADMIN` は手動付与のみ（DBシーディングまたは管理者UIから）
- ロールは排他ではなく、セラーはバイヤーとしても購入可能

### 2.3 将来拡張（設計考慮のみ、MVP実装外）

将来的なマルチメンバーショップ対応のため、以下のドメインモデルを設計上考慮する：

```
ShopMember(shopId, userId, shopRole: OWNER | MANAGER | STAFF)
SystemRole: ROLE_ADMIN | ROLE_SUPER_ADMIN
```

将来の二段階認証（2FA）対応のため、`users` テーブルには2FA用カラムを追加せず、別テーブルで管理する設計を考慮する：

```
user_mfa_methods（将来テーブル）:
  id            UUID PK
  user_id       UUID FK → users
  method_type   ENUM(EMAIL_OTP, TOTP, SMS)
  secret        TEXT（暗号化保存）
  is_verified   BOOLEAN
  is_primary    BOOLEAN
  created_at    TIMESTAMP

この設計により:
  - 1ユーザーが複数の2FA方式を保持可能
  - 方式追加は enum 値追加のみで対応可能
  - 現在の users テーブルへの影響ゼロ
```

---

## 3. 機能要件

### 3.1 認証・認可

#### 3.1.1 メール認証

| # | 要件 |
|---|---|
| AUTH-01 | ユーザーはメールアドレス・パスワードで新規登録できる（2ステップフォーム形式） |
| AUTH-01-A | ステップ1：メールアドレスのみ入力し「次へ」ボタンでメール重複チェックを行う。既登録の場合は `EMAIL_ALREADY_REGISTERED` エラーを返す |
| AUTH-01-B | ステップ2：パスワード（8文字以上）とパスワード確認を入力し「アカウントを作成する」ボタンで本登録を完了する。表示名は登録後のプロフィール設定で変更可能 |
| AUTH-02 | 登録後、確認メールが送信される（Resend経由） |
| AUTH-03 | メール確認済みのユーザーのみログイン可能 |
| AUTH-04 | ログイン成功時、Access Token（有効期限15分）とRefresh Token（有効期限7日）が発行される |
| AUTH-05 | Refresh TokenによるAccess Token再発行ができる |
| AUTH-06 | ログアウト時にRefresh Tokenが無効化される |

#### 3.1.2 Google OAuth 認証

| # | 要件 |
|---|---|
| AUTH-10 | NextAuth.js経由でGoogleアカウントによるログインができる |
| AUTH-11 | フロントエンドはGoogle ID Tokenをバックエンドへ送信する |
| AUTH-12 | バックエンドはGoogle ID Tokenを検証し、ユーザーレコードを作成または取得後、自前JWTを発行する |
| AUTH-13 | Googleアカウントと同じメールで既にメール登録がある場合、アカウントを統合する |

#### 3.1.3 認可

| # | 要件 |
|---|---|
| AUTH-20 | 全APIエンドポイントはJWT（Bearer Token）で認可される |
| AUTH-21 | 自分のリソース以外（他人の注文・チャット等）へのアクセスは403を返す |
| AUTH-22 | ロール不足の場合は403を返す（例：バイヤーがセラー専用APIを呼ぶ） |

#### 3.1.4 二段階認証（将来対応）

> **ステータス：スコープ外（将来実装予定）** — 現時点では実装しないが、DBおよびアーキテクチャを拡張しやすい設計とする。

| # | 要件 |
|---|---|
| AUTH-2FA-01 | ユーザーは複数の2FA方式（メールOTP・TOTP・SMS）を登録・管理できる |
| AUTH-2FA-02 | 2FA有効化済みユーザーはログイン時に追加認証コードの入力が求められる |
| AUTH-2FA-03 | 2FA方式はユーザーごとに複数設定可能とし、方式の追加・削除・プライマリ変更ができる |

### 3.2 ユーザー管理

| # | 要件 |
|---|---|
| USER-01 | ユーザーはプロフィール画像・表示名・配送先住所を設定できる |
| USER-02 | ユーザーはパスワードを変更できる |
| USER-03 | ユーザーは退会申請ができる（論理削除） |

### 3.3 セラー申請・ショップ管理

#### 3.3.1 セラー申請フロー

```
[BUYER登録] → [セラー申請] → [PENDING] → [管理者承認/却下] → [APPROVED/REJECTED]
                                                                      ↓
                                                              ROLE_SELLER付与
```

| # | 要件 |
|---|---|
| SELLER-01 | `ROLE_BUYER` のユーザーはセラー申請を送信できる |
| SELLER-02 | 申請ステータスは `PENDING` / `APPROVED` / `REJECTED` の3種類 |
| SELLER-03 | 申請承認時、ユーザーロールが `ROLE_SELLER` に変更され、ショップレコードが自動生成される |
| SELLER-04 | 申請却下時、理由をコメントとして保存し、申請者に通知が届く |
| SELLER-05 | 却下された場合、再申請が可能（同一ユーザーで新しい申請を作成） |

#### 3.3.2 ショップ管理

| # | 要件 |
|---|---|
| SHOP-01 | セラーはショップ名・紹介文・ロゴ画像を設定・変更できる |
| SHOP-02 | ショップには送料ポリシー（固定送料金額 / 無料 / 無料になる閾値）を設定できる |
| SHOP-03 | ショップの送料設定はショップ全体に適用される（商品単位ではない） |
| SHOP-04 | セラー1人につきショップは1つ |

### 3.4 商品管理

#### 3.4.1 カテゴリー

| # | 要件 |
|---|---|
| CAT-01 | カテゴリーは管理者があらかじめ定義する（階層構造：親カテゴリー・子カテゴリー） |
| CAT-02 | セラーは商品登録時に管理者定義のカテゴリーから選択する |

#### 3.4.2 商品CRUD

| # | 要件 |
|---|---|
| PRD-01 | セラーは商品を登録・編集・削除できる（論理削除） |
| PRD-02 | 商品には商品名・説明文・価格（JPY）・カテゴリー・在庫数・ステータスを設定できる |
| PRD-03 | 商品ステータスは `DRAFT` / `ACTIVE` / `INACTIVE` / `DELETED` の4種類 |
| PRD-04 | `ACTIVE` 状態の商品のみ一般公開される |
| PRD-05 | 在庫数が0の場合、商品は「在庫切れ」表示となり購入不可になる |
| PRD-06 | 商品には最大5枚の画像をアップロードできる（Cloudinary経由） |
| PRD-07 | アップロード時、Cloudinaryの変換機能で自動リサイズ・WebP変換・圧縮を行う |
| PRD-08 | 1枚目の画像が商品のサムネイルとなる（並び順変更可能） |

#### 3.4.3 商品検索・フィルター

| # | 要件 |
|---|---|
| SRCH-01 | キーワードによる全文検索（商品名・説明文）ができる（PostgreSQL LIKE + インデックス） |
| SRCH-02 | カテゴリー・価格帯・在庫ありフィルターが利用できる |
| SRCH-03 | 新着順・価格昇順/降順・人気順（レビュー数）でソートできる |
| SRCH-04 | 検索結果はページネーション（デフォルト20件/ページ）される |

### 3.5 カートと決済

#### 3.5.1 カート

| # | 要件 |
|---|---|
| CART-01 | ログイン済みバイヤーは商品をカートに追加できる |
| CART-02 | カートには複数ショップの商品を混在させることができる |
| CART-03 | カート内の商品数量を変更・削除できる |
| CART-04 | カートには在庫数を超えた数量を追加できない |
| CART-05 | 商品が削除・在庫切れになった場合、カート内でその旨を表示する |

#### 3.5.2 チェックアウト・決済

| # | 要件 |
|---|---|
| PAY-01 | 決済はStripe（テストモード）を使用する |
| PAY-02 | バイヤーは配送先住所を選択または入力して注文を確定できる |
| PAY-03 | 注文確定前に商品小計・送料・手数料（プラットフォーム設定値）・合計金額を表示する |
| PAY-04 | StripeのPayment Intentを利用し、クレジットカードで決済する |
| PAY-05 | クレジットカード情報はStripe側で管理し、Kivioのシステムには保持しない |
| PAY-06 | 決済成功時、注文レコードが作成され、注文確認メールが送信される |
| PAY-07 | 複数ショップの商品をカートに入れた場合、ショップごとに注文が分割される |

#### 3.5.3 手数料計算モデル

```
注文時の手数料計算（プラットフォーム管理）:
  totalAmount      = 商品合計 + 送料
  commissionRate   = PlatformConfigから取得（例: 0.05 = 5%）
  commissionAmount = totalAmount × commissionRate
  sellerAmount     = totalAmount − commissionAmount

※ MVPではStripe Connectは使わず、DBに計算値を保存するのみ
```

### 3.6 注文管理

#### 3.6.1 注文ステータスフロー

```
PENDING_PAYMENT → PAYMENT_CONFIRMED → PROCESSING → SHIPPED → DELIVERED → COMPLETED
                                           ↓
                                       CANCELLED（発送前のみ）
```

| # | 要件 |
|---|---|
| ORD-01 | バイヤーは注文履歴一覧・注文詳細を閲覧できる |
| ORD-02 | 注文ステータスの変更はリアルタイムでバイヤーの画面に反映される（WebSocket） |
| ORD-03 | セラーは注文管理画面でステータスを `PROCESSING → SHIPPED → COMPLETED` に変更できる |
| ORD-04 | バイヤーは `PAYMENT_CONFIRMED` または `PROCESSING` 状態の注文をキャンセルできる |
| ORD-05 | セラーも `PAYMENT_CONFIRMED` または `PROCESSING` 状態の注文をキャンセルできる |
| ORD-06 | キャンセル時、StripeのRefund APIを呼び出し返金処理を行う |
| ORD-07 | 注文ステータス変更時、バイヤーとセラーに通知が届く |

### 3.7 リアルタイムチャット

| # | 要件 |
|---|---|
| CHAT-01 | バイヤーは商品詳細画面からセラーへチャットを開始できる |
| CHAT-02 | チャットルームはバイヤーとセラー（ショップ）の組み合わせごとに1つ |
| CHAT-03 | メッセージは送信した瞬間に相手の画面に表示される（WebSocket/STOMP） |
| CHAT-04 | 未読メッセージ数がチャット一覧とナビゲーションバーに表示される |
| CHAT-05 | チャット履歴はDBに永続化される |
| CHAT-06 | 画像送信は対象外（テキストのみ） |

### 3.8 通知機能

| # | 要件 |
|---|---|
| NOTIF-01 | アプリ内通知はリアルタイムでナビゲーションバーに表示される（WebSocket） |
| NOTIF-02 | 通知には「既読」「未読」の状態があり、クリックで既読になる |
| NOTIF-03 | 通知の保存期間は90日（バッチ処理またはソフトデリート） |
| NOTIF-04 | 通知が発生するイベント：注文確定・ステータス変更・キャンセル・新着チャット・セラー申請結果 |

#### 3.8.2 メール通知（Should）

| # | 要件 |
|---|---|
| MAIL-01 | 会員登録確認メールを送信する（Resend） |
| MAIL-02 | 注文確定メールをバイヤーに送信する |
| MAIL-03 | 注文受付メールをセラーに送信する |
| MAIL-04 | ステータス変更メールをバイヤーに送信する |

### 3.9 セラーダッシュボード

| # | 要件 |
|---|---|
| DASH-01 | 今月の売上金額・注文数・セラー取り分を表示する |
| DASH-02 | 直近30日間の日別売上推移グラフを表示する（Should） |
| DASH-03 | 人気商品ランキング（売上数順）を表示する（Should） |
| DASH-04 | 最近の注文一覧（最新10件）を表示する |
| DASH-05 | 未読メッセージ数の通知を表示する |

### 3.10 商品レビュー（Should）

| # | 要件 |
|---|---|
| REV-01 | レビューは「その商品を含む注文が `COMPLETED` になったバイヤー」のみ投稿できる |
| REV-02 | 1つの注文明細につきレビューは1件のみ（重複投稿不可） |
| REV-03 | レビューには評価（1〜5の整数）とコメント（任意）を投稿できる |
| REV-04 | 商品詳細画面にレビュー一覧・平均評価・レビュー件数を表示する |

### 3.11 お気に入り（Should）

| # | 要件 |
|---|---|
| FAV-01 | ログイン済みバイヤーは商品をお気に入りに追加・削除できる |
| FAV-02 | お気に入り一覧画面を表示できる |

### 3.12 管理者機能

| # | 要件 |
|---|---|
| ADM-01 | セラー申請の一覧を確認し、承認または却下できる |
| ADM-02 | 不適切な商品を `INACTIVE` にできる（管理者によるモデレーション） |
| ADM-03 | ユーザーアカウントを無効化・有効化できる |
| ADM-04 | プラットフォーム手数料率を設定・変更できる（`PlatformConfig`） |
| ADM-05 | 全体統計（総ユーザー数・総注文数・総売上）をダッシュボードで確認できる（Should） |
| ADM-06 | カテゴリーの作成・編集・削除ができる |

---

## 4. 非機能要件

### 4.1 セキュリティ

| # | 要件 |
|---|---|
| SEC-01 | パスワードは BCrypt（コストファクター12）でハッシュ化して保存する |
| SEC-02 | クレジットカード情報はKivioシステムに保持しない（Stripeに委譲） |
| SEC-03 | JWTはRS256（非対称鍵）または HS256（共有秘密鍵）で署名し、秘密鍵は環境変数で管理する |
| SEC-04 | Access Tokenの有効期限は15分、Refresh Tokenは7日 |
| SEC-05 | 全APIエンドポイントでロールと所有者チェックを行い、越権アクセスを防ぐ |
| SEC-06 | 認証エンドポイントにはレート制限を設ける（例：IPごとに10リクエスト/分） |
| SEC-07 | HTTPS通信のみ（本番環境） |
| SEC-08 | CORS設定はフロントエンドのオリジンのみ許可する |
| SEC-09 | SQLインジェクション対策：JPA/パラメータ化クエリのみ使用 |
| SEC-10 | XSS対策：フロントエンドでサニタイズし、バックエンドではHTMLを許可しない |

### 4.2 パフォーマンス

| # | 要件 |
|---|---|
| PERF-01 | 商品一覧APIのレスポンスは500ms以内（通常時） |
| PERF-02 | 画像はCloudinaryによりWebP変換・リサイズ・CDN配信される |
| PERF-03 | 商品検索クエリには適切なインデックスを設計する |
| PERF-04 | ページネーションを全リスト系APIに実装する |
| PERF-05 | Next.jsのISR / React Server Componentsを活用しフロントエンドの表示速度を最適化する |

### 4.3 可用性・拡張性

| # | 要件 |
|---|---|
| SCAL-01 | バックエンドはドメイン（ユーザー・カタログ・注文・チャット）ごとにパッケージを分離し、将来のマイクロサービス化を可能にする |
| SCAL-02 | WebSocketサーバーは将来的にRedis Pub/Subで複数インスタンス対応できる設計とする |
| SCAL-03 | 配送業者API連携の拡張ポイント（インターフェース）を設計する |

### 4.4 レスポンシブ対応

| # | 要件 |
|---|---|
| RES-01 | スマートフォン（375px〜）・タブレット（768px〜）・PC（1280px〜）に対応する |
| RES-02 | Tailwind CSSのブレークポイントを統一して使用する |

### 4.5 国際化（i18n）

| # | 要件 |
|---|---|
| I18N-01 | UIの表示言語は日本語とする |
| I18N-02 | next-intl または next-i18next を使用し、将来の多言語対応ができる構造にする |
| I18N-03 | 通貨はJPYのみ。金額表示は `Intl.NumberFormat` を使用する |

### 4.6 データ整合性・削除方針

| # | 要件 |
|---|---|
| DEL-01 | ユーザーデータ・ショップ・カテゴリーの削除は物理削除（hard delete）を行わず、`deleted_at` タイムスタンプによる論理削除（soft delete）とする |
| DEL-02 | 商品の削除は既存のステータス管理（`status = 'DELETED'`）で代替し、`deleted_at` カラムは追加しない |
| DEL-03 | 注文・注文明細・決済レコードは会計記録として削除不可とし、ステータス管理のみ行う |
| DEL-04 | Soft delete されたリソースは通常の一覧・検索APIから自動的に除外される（`deleted_at IS NULL` 条件をバックエンドで強制） |
| DEL-05 | バックエンド実装では `SoftDeletableEntity` 基底クラス（`@MappedSuperclass`）と `@SQLRestriction("deleted_at IS NULL")` を使用し、soft delete フィルタリングを統一する |

**Soft delete 適用エンティティ一覧:**

| エンティティ | 削除方式 | 備考 |
|---|---|---|
| `users` | `deleted_at` | USER-03 退会申請に対応 |
| `shops` | `deleted_at` | ユーザー退会時に連動 |
| `categories` | `deleted_at` | 紐づき商品がある場合も安全に削除可能 |
| `products` | `status = 'DELETED'` | ステータス管理で代替 |
| `orders` / `payments` | 削除不可 | 会計記録として永続保存 |
| `notifications` | `expires_at` | 90日期限切れで非表示（論理削除相当） |
| `refresh_tokens` | `revoked` フラグ | 期限切れ・失効で無効化 |

### 4.7 監査ログ

| # | 要件 |
|---|---|
| AUDIT-01 | 認証イベント・管理者操作・重要な状態変更を `audit_logs` テーブルに追記専用で記録する |
| AUDIT-02 | 読み取り系（GET）操作は記録しない（ノイズ排除とパフォーマンス維持のため） |
| AUDIT-03 | 監査ログレコードは削除・更新を行わない（追記のみ） |
| AUDIT-04 | 実装は Spring AOP（`@Aspect` + カスタムアノテーション `@Auditable`）を使用し、サービス層のビジネスロジックに監査コードを混在させない |
| AUDIT-05 | 同一リクエスト内の複数操作を `correlation_id`（MDC経由で伝搬するリクエストID）で紐づけ可能にする |
| AUDIT-06 | `actor_role` / `actor_email` は操作時点のスナップショットとして保存する（後からユーザー情報が変更されても操作時点の情報が残る） |
| AUDIT-07 | 管理者操作・ステータス変更系イベントでは `old_value` / `new_value`（JSONB）に変更前後の状態を記録する |
| AUDIT-08 | 操作の成否を `outcome`（`SUCCESS` / `FAILURE`）で記録し、失敗時は `error_message` を保存する |

#### 記録対象イベント一覧

| カテゴリ | アクション名 | トリガー |
|---|---|---|
| 認証 | `USER_REGISTERED` | 新規会員登録完了 |
| 認証 | `USER_EMAIL_VERIFIED` | メールアドレス確認完了 |
| 認証 | `USER_LOGGED_IN` | ログイン成功 |
| 認証 | `USER_LOGIN_FAILED` | ログイン失敗（メール不一致・パスワード不一致） |
| 認証 | `USER_LOGGED_OUT` | ログアウト |
| 認証 | `USER_PASSWORD_CHANGED` | パスワード変更 |
| 管理者操作 | `SELLER_APPLICATION_APPROVED` | セラー申請承認 |
| 管理者操作 | `SELLER_APPLICATION_REJECTED` | セラー申請却下 |
| 管理者操作 | `USER_DEACTIVATED` | ユーザーアカウント無効化 |
| 管理者操作 | `USER_ACTIVATED` | ユーザーアカウント有効化 |
| 管理者操作 | `PRODUCT_FORCEFULLY_DEACTIVATED` | 管理者による商品強制非公開 |
| 管理者操作 | `PLATFORM_CONFIG_UPDATED` | 手数料率などプラットフォーム設定変更 |
| 重要状態変更 | `ORDER_CANCELLED` | 注文キャンセル（バイヤー / セラー問わず） |

---

## 5. システムアーキテクチャ

### 5.1 全体構成

```
┌─────────────────────────────────────────────────────┐
│                    クライアント                        │
│         Next.js (App Router) + shadcn/ui             │
│              Vercel にデプロイ                         │
└────────────────────┬────────────────────────────────┘
                     │ HTTPS / WebSocket(WSS)
┌────────────────────▼────────────────────────────────┐
│              Spring Boot 3.x API Server              │
│           Render / Railway にデプロイ                  │
│                                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │  Auth    │ │ Catalog  │ │  Order   │ │  Chat  │ │
│  │ Domain   │ │  Domain  │ │  Domain  │ │ Domain │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │          Spring Security + JWT Filter            │ │
│  └─────────────────────────────────────────────────┘ │
└──────┬────────────────────────────┬─────────────────┘
       │ JPA / JDBC                  │ WebSocket/STOMP
┌──────▼──────┐             ┌───────▼──────┐
│  PostgreSQL  │             │  WebSocket   │
│  Neon /      │             │  Message     │
│  Supabase    │             │  Broker      │
└─────────────┘             └─────────────┘
                     ┌───────────────────────────┐
                     │  外部サービス               │
                     │  Stripe (決済)             │
                     │  Cloudinary (画像)         │
                     │  Resend (メール)            │
                     │  Google OAuth              │
                     └───────────────────────────┘
```

### 5.2 ドメイン境界（モジュラーモノリス）

将来のマイクロサービス分割を見据え、バックエンドは以下のドメインパッケージで分離する。

| ドメイン | 責務 | 主要エンティティ |
|---|---|---|
| `identity` | 認証・認可・ユーザー管理 | User, SellerApplication |
| `catalog` | ショップ・商品・カテゴリー管理 | Shop, Product, Category, ProductImage |
| `order` | カート・注文・決済 | Cart, CartItem, Order, OrderItem, Payment, Address |
| `messaging` | チャット | ChatRoom, ChatMessage |
| `notification` | 通知 | Notification |
| `review` | レビュー | Review |
| `platform` | プラットフォーム設定 | PlatformConfig |

**パッケージ依存ルール：**
- ドメイン間の直接依存を禁止し、Application Serviceレイヤーまたはイベントで連携する
- 共通ユーティリティは `common` パッケージに配置する

---

## 6. 技術スタック

### 6.1 バックエンド

| カテゴリ | 採用技術 | バージョン |
|---|---|---|
| 言語 | Java | 25 (LTS) |
| フレームワーク | Spring Boot | 4.x |
| セキュリティ | Spring Security + OAuth2 Resource Server | - |
| ORM | Spring Data JPA / Hibernate | - |
| DB マイグレーション | Flyway | - |
| リアルタイム | Spring WebSocket / STOMP | - |
| バリデーション | Spring Validation (Jakarta Bean Validation) | - |
| APIドキュメント | springdoc-openapi (OpenAPI 3.0 / Swagger UI) | - |
| テスト | JUnit 5, Mockito, Spring Boot Test, Testcontainers | - |
| ビルド | Gradle (Kotlin DSL) | - |

### 6.2 フロントエンド

| カテゴリ | 採用技術 | バージョン |
|---|---|---|
| フレームワーク | Next.js (App Router) | latest |
| 言語 | TypeScript | 5.x |
| UIコンポーネント | shadcn/ui | latest |
| スタイリング | Tailwind CSS | 4.x |
| 認証クライアント | NextAuth.js (Auth.js v5) | 5.x |
| 状態管理 | Zustand | - |
| データフェッチ | TanStack Query (React Query) | 5.x |
| HTTPクライアント | Axios | - |
| フォーム | React Hook Form + Zod | - |
| グラフ | Recharts | - |
| WebSocket | STOMP.js / @stomp/stompjs | - |
| 国際化 | next-intl | - |
| Linter | ESLint (`eslint-config-next`) | - |
| フォーマッター | Prettier + `eslint-config-prettier` | - |

### 6.3 インフラ・外部サービス

| カテゴリ | 採用技術 |
|---|---|
| DB | PostgreSQL 16（Neon または Supabase） |
| 画像ストレージ | Cloudinary（自動リサイズ・WebP変換・CDN配信） |
| 決済 | Stripe（テストモード） |
| メール | Resend |
| OAuth Provider | Google |
| フロントエンドホスティング | Vercel |
| バックエンドホスティング | Render または Railway |
| CI/CD | GitHub Actions |
| コンテナ | Docker + devcontainer（開発環境） |
| バージョン管理 | GitHub |

---

## 7. データモデル

> **注意：** 本セクションは論理データモデルの定義です。物理設計（データ型・桁数・インデックス・DDL）は別途 `docs/DB_DESIGN.md` に記載します。

### 7.1 エンティティ一覧

| エンティティ（論理名） | テーブル名（物理名） | 主な属性 | 主な関連 |
|---|---|---|---|
| ユーザー | `users` | メールアドレス、パスワード（ハッシュ）、Google ID、表示名、アバター画像URL、ロール、ステータス、メール確認済みフラグ、**deleted_at**（soft delete） | - |
| リフレッシュトークン | `refresh_tokens` | トークンハッシュ、有効期限、失効フラグ | ユーザー（多:1） |
| セラー申請 | `seller_applications` | 申請理由、審査状態、審査コメント、審査者、審査日時 | ユーザー（申請者）、ユーザー（審査者） |
| ショップ | `shops` | ショップ名、紹介文、ロゴ画像URL、公開ステータス、**deleted_at**（soft delete） | ユーザー・セラー（1:1） |
| ショップ配送ポリシー | `shop_shipping_policies` | 配送タイプ、固定送料金額、送料無料条件金額 | ショップ（1:1） |
| カテゴリー | `categories` | カテゴリー名、スラッグ、表示順序、親カテゴリー、**deleted_at**（soft delete） | カテゴリー（自己参照・親子） |
| 商品 | `products` | 商品名、説明文、価格（円）、在庫数、公開ステータス（DELETED = soft delete相当） | ショップ（多:1）、カテゴリー（多:1） |
| 商品画像 | `product_images` | Cloudinary ID、画像URL、表示順序 | 商品（多:1） |
| 配送先住所 | `addresses` | 受取人名、郵便番号、都道府県、市区町村、番地・建物名、電話番号、デフォルトフラグ | ユーザー（多:1） |
| カート | `carts` | - | ユーザー（1:1） |
| カート明細 | `cart_items` | 数量 | カート（多:1）、商品（多:1） |
| 注文 | `orders` | 審査状態、商品小計、送料、合計金額、手数料率、手数料額、セラー取り分、Stripe決済ID、キャンセル日時、キャンセル理由 | バイヤー（多:1）、ショップ（多:1）、配送先住所（多:1） |
| 注文明細 | `order_items` | 注文時商品名（スナップショット）、注文時画像URL、単価、数量、小計、レビュー済みフラグ | 注文（多:1）、商品（多:1） |
| 決済 | `payments` | Stripe決済ID、金額、通貨、決済状態、Stripe返金ID | 注文（1:1） |
| レビュー | `reviews` | 評価（1〜5の整数）、コメント | 注文明細（1:1）、商品（多:1）、ユーザー（多:1） |
| チャットルーム | `chat_rooms` | - | バイヤー（多:1）、ショップ（多:1） |
| チャットメッセージ | `chat_messages` | 本文、既読日時 | チャットルーム（多:1）、送信者（多:1） |
| 通知 | `notifications` | 通知タイプ、タイトル、本文、既読フラグ、既読日時、関連エンティティ種別・ID、有効期限 | ユーザー（多:1） |
| お気に入り | `wishlists` | - | ユーザー（多:1）、商品（多:1） |
| プラットフォーム設定 | `platform_configs` | 設定キー（一意）、設定値、説明、更新者 | ユーザー（更新者） |
| 監査ログ | `audit_logs` | correlation_id（リクエストID）、actor_id、actor_role（スナップショット）、actor_email（スナップショット）、action、entity_type、entity_id、outcome、ip_address、old_value（JSONB）、new_value（JSONB）、error_message、created_at | ユーザー（actor、nullable） |

### 7.2 主要エンティティ関連図

```
[ユーザー] ─────────────────────────────────────────────────────┐
    │                                                           │
    ├── 1:1 ──[ショップ]──────────────────── 1:* ──[商品]        │
    │              │                                  │         │
    │              │                                  ├── 1:* ──[商品画像]
    │              │                                  │
    │              └── 1:* ──[注文]──── 1:* ──[注文明細]
    │                             │             │
    │                             │             └── 0..1 ──[レビュー]
    │                             │
    │                             └── 1:1 ──[決済]
    │
    ├── 1:1 ──[カート]──── 1:* ──[カート明細]
    │
    ├── 1:* ──[配送先住所]
    │
    ├── 1:* ──[通知]
    │
    ├── 1:* ──[お気に入り]
    │
    └── 1:* ──[セラー申請]

[バイヤー] ─── * ─── [チャットルーム] ─── * ─── [ショップ]
                              │
                              └── 1:* ──[チャットメッセージ]
```

### 7.3 主要なビジネスルール

#### ユーザー・ロール
- 同一メールアドレスは1アカウントのみ登録可能
- Googleログインユーザーはパスワードを持たない
- ロールは `ROLE_BUYER` / `ROLE_SELLER` / `ROLE_ADMIN` の3種
- セラーへのロール昇格はセラー申請の承認経由のみ

#### 削除（Soft Delete）
- `users` / `shops` / `categories` は `deleted_at` タイムスタンプによる論理削除のみ（物理削除は行わない）
- 商品の「削除」は `status = 'DELETED'` への変更で表現する
- Soft delete済みリソースはAPI上から自動的に除外され、他ユーザーには見えない
- 注文・決済データは永続保存し、削除操作を提供しない

#### 監査ログ
- `audit_logs` レコードは追記専用。UPDATE / DELETE を行わない
- `actor_id` は nullable（バッチやシステム自動処理の場合は null）
- `actor_role` / `actor_email` は操作時点のスナップショット値を保存する（ユーザー情報の後変更に影響されない）
- アクション名は `{ENTITY}_{VERB}` 形式の UPPER_SNAKE_CASE で統一する（例: `ORDER_CANCELLED`）
- `correlation_id` は MDC（Mapped Diagnostic Context）経由でリクエスト開始時に生成・伝搬し、同一リクエスト内の複数監査レコードを紐づけ可能にする

#### ショップ
- セラー1人につきショップは必ず1つ（1対1）
- ショップ名はプラットフォーム全体で一意
- ショップの配送ポリシーはショップ全体に適用（商品単位ではない）
- 配送タイプは `FREE`（無料）/ `FIXED`（固定送料）/ `CONDITIONAL_FREE`（一定金額以上で無料）の3種

#### 商品
- 商品ステータスは `DRAFT` / `ACTIVE` / `INACTIVE` / `DELETED` の4種（`ACTIVE` のみ公開）
- 在庫数が0の場合は `ACTIVE` であっても購入不可
- 商品画像は最大5枚

#### 注文
- 注文はショップ単位に分割される（複数ショップまたがりの一括購入は複数注文）
- 注文明細の商品名・画像URLは注文時点のスナップショットとして保存（商品後変更の影響を受けない）
- 手数料率は注文確定時点の `platform_configs` から取得して注文レコードに記録する

#### レビュー
- 注文ステータスが `COMPLETED` になった注文明細に対してのみ1件投稿可能

#### チャット
- バイヤーとショップの組み合わせごとにチャットルームは1つ（複合一意）

#### 通知
- 有効期限は作成日時から90日後に設定する（期限切れ後は非表示にする）

---

## 8. API設計方針

### 8.1 基本方針

| 項目 | 内容 |
|---|---|
| プロトコル | REST API / JSON |
| ベースパス | `/api/v1` |
| Content-Type | `application/json` / `application/problem+json`（エラー時） |
| 文字コード | UTF-8 |
| 日時フォーマット | ISO 8601（UTC）: `2026-05-24T10:00:00Z` |
| 金額フォーマット | 整数（円単位）: `1500`（¥1,500） |
| JSONフィールド命名 | **camelCase**（Spring Boot / Jackson デフォルト） |
| 認証方式 | `Authorization: Bearer <accessToken>` ヘッダー |
| APIドキュメント | OpenAPI 3.0（Swagger UI: `/swagger-ui.html`） |
| バージョニング | URLパスバージョニング（`/api/v1`）|

---

### 8.2 URLルール

#### 命名規則

```
# リソース名は複数形・kebab-case・名詞
GET  /api/v1/products           ✓
GET  /api/v1/product            ✗  (単数形)
GET  /api/v1/getProducts        ✗  (動詞を含む)

# パスパラメータは {id} 形式（Spring PathVariable）
GET  /api/v1/products/{id}      ✓
GET  /api/v1/products/:id       ✗  (Express形式はSpringでは使わない)

# サブリソースで関係を表現
GET  /api/v1/shops/{shopId}/products   ✓

# 状態遷移アクション（CRUDに収まらない操作）はPOSTで動詞サブパス
POST /api/v1/orders/{id}/cancel        ✓
POST /api/v1/admin/seller-applications/{id}/approve  ✓
```

#### 主要エンドポイント一覧

```
# ── 認証 ────────────────────────────────────────────────────
POST   /api/v1/auth/check-email           # メールアドレス重複チェック（登録ステップ1）
POST   /api/v1/auth/register              # 会員登録（登録ステップ2）
POST   /api/v1/auth/login                 # メールログイン
POST   /api/v1/auth/google                # Google ID Token 検証 → JWT発行
POST   /api/v1/auth/refresh               # Access Token 再発行
POST   /api/v1/auth/logout                # ログアウト（Refresh Token 無効化）

# ── ユーザー ─────────────────────────────────────────────────
GET    /api/v1/users/me                   # 自分のプロフィール取得
PATCH  /api/v1/users/me                   # プロフィール更新
GET    /api/v1/users/me/addresses         # 配送先住所一覧
POST   /api/v1/users/me/addresses         # 配送先住所追加
PATCH  /api/v1/users/me/addresses/{id}    # 配送先住所更新
DELETE /api/v1/users/me/addresses/{id}    # 配送先住所削除

# ── セラー申請 ────────────────────────────────────────────────
POST   /api/v1/seller-applications        # セラー申請送信（BUYER）
GET    /api/v1/seller-applications/me     # 自分の申請状況確認

# ── ショップ ─────────────────────────────────────────────────
GET    /api/v1/shops                      # ショップ一覧
GET    /api/v1/shops/{id}                 # ショップ詳細
GET    /api/v1/shops/me                   # 自分のショップ取得（SELLER）
PATCH  /api/v1/shops/me                   # ショップ情報更新（SELLER）
PATCH  /api/v1/shops/me/shipping-policy   # 配送ポリシー更新（SELLER）

# ── 商品 ─────────────────────────────────────────────────────
GET    /api/v1/products                   # 商品一覧（検索・フィルター）
GET    /api/v1/products/{id}              # 商品詳細
POST   /api/v1/products                   # 商品登録（SELLER）
PATCH  /api/v1/products/{id}              # 商品更新（SELLER）
DELETE /api/v1/products/{id}              # 商品削除・論理削除（SELLER）
POST   /api/v1/products/{id}/images       # 商品画像アップロード（SELLER）
DELETE /api/v1/products/{id}/images/{imageId}  # 商品画像削除（SELLER）

# ── カート ───────────────────────────────────────────────────
GET    /api/v1/cart                       # カート取得
POST   /api/v1/cart/items                 # カートに商品追加
PATCH  /api/v1/cart/items/{itemId}        # カート明細の数量変更
DELETE /api/v1/cart/items/{itemId}        # カート明細削除
DELETE /api/v1/cart                       # カート全クリア

# ── 注文・決済 ────────────────────────────────────────────────
POST   /api/v1/orders/checkout            # 注文作成 + Stripe Payment Intent生成
GET    /api/v1/orders                     # 注文一覧（バイヤー: 自分の / セラー: ショップへの）
GET    /api/v1/orders/{id}                # 注文詳細
PATCH  /api/v1/orders/{id}/status         # 注文ステータス更新（SELLER: PROCESSING→SHIPPED→COMPLETED）
POST   /api/v1/orders/{id}/cancel         # 注文キャンセル（バイヤー / セラー）
POST   /api/v1/webhooks/stripe            # Stripe Webhook受信（認証不要）

# ── レビュー ─────────────────────────────────────────────────
GET    /api/v1/products/{id}/reviews      # 商品レビュー一覧
POST   /api/v1/order-items/{id}/review    # レビュー投稿（購入済みユーザーのみ）

# ── チャット ─────────────────────────────────────────────────
GET    /api/v1/chat-rooms                 # チャットルーム一覧
GET    /api/v1/chat-rooms/{id}            # チャットルーム詳細（メッセージ履歴）
POST   /api/v1/chat-rooms                 # チャットルーム開始（バイヤー→ショップ）

# ── 通知 ─────────────────────────────────────────────────────
GET    /api/v1/notifications              # 通知一覧
PATCH  /api/v1/notifications/{id}/read    # 通知を既読にする
PATCH  /api/v1/notifications/read-all     # 全通知を既読にする

# ── お気に入り ────────────────────────────────────────────────
GET    /api/v1/wishlist                   # お気に入り一覧
POST   /api/v1/wishlist                   # お気に入り追加
DELETE /api/v1/wishlist/{productId}       # お気に入り削除

# ── セラーダッシュボード ──────────────────────────────────────
GET    /api/v1/seller/dashboard           # 売上サマリー・統計

# ── カテゴリー ────────────────────────────────────────────────
GET    /api/v1/categories                 # カテゴリー一覧（ツリー構造）

# ── 管理者 ───────────────────────────────────────────────────
GET    /api/v1/admin/seller-applications             # セラー申請一覧
POST   /api/v1/admin/seller-applications/{id}/approve  # セラー申請承認
POST   /api/v1/admin/seller-applications/{id}/reject   # セラー申請却下
GET    /api/v1/admin/users                           # ユーザー一覧
PATCH  /api/v1/admin/users/{id}/status               # ユーザー有効化・無効化
GET    /api/v1/admin/products                        # 商品一覧（モデレーション）
PATCH  /api/v1/admin/products/{id}/status            # 商品ステータス変更
GET    /api/v1/admin/categories                      # カテゴリー管理
POST   /api/v1/admin/categories                      # カテゴリー作成
PATCH  /api/v1/admin/categories/{id}                 # カテゴリー更新
DELETE /api/v1/admin/categories/{id}                 # カテゴリー削除
GET    /api/v1/admin/platform-configs                # プラットフォーム設定一覧
PATCH  /api/v1/admin/platform-configs/{key}          # 設定値更新（手数料率等）
GET    /api/v1/admin/dashboard                       # 全体統計
```

---

### 8.3 HTTPメソッドとステータスコード

#### メソッドのセマンティクス

| メソッド | 用途 | 冪等性 |
|---|---|---|
| `GET` | リソース取得 | あり |
| `POST` | リソース作成・アクション実行 | なし |
| `PATCH` | リソースの部分更新 | なし |
| `DELETE` | リソース削除 | あり |

> `PUT`（全体置換）は使用しない。部分更新には常に `PATCH` を使用する。

#### ステータスコード規則

| コード | 用途 |
|---|---|
| `200 OK` | GET / PATCH（レスポンスボディあり） |
| `201 Created` | POST でリソース作成成功（`Location` ヘッダー付き） |
| `204 No Content` | DELETE / 副作用のみのPOST（レスポンスボディなし） |
| `400 Bad Request` | リクエスト形式が不正（JSONパースエラー等） |
| `401 Unauthorized` | 認証が必要 / JWTが無効・期限切れ |
| `403 Forbidden` | 認証済みだが権限不足（他人のリソースへのアクセス等） |
| `404 Not Found` | リソースが存在しない |
| `409 Conflict` | 状態競合（在庫切れ・キャンセル不可・重複登録等） |
| `422 Unprocessable Entity` | バリデーションエラー（Bean Validation失敗） |
| `429 Too Many Requests` | レート制限超過 |
| `500 Internal Server Error` | サーバー内部エラー（詳細は絶対に露出しない） |

---

### 8.4 レスポンス形式の標準化

#### 8.4.1 成功レスポンス（単一リソース）

Spring Boot の `@RestController` の標準に従い、単一リソースはDTOを直接返す（エンベロープ不要）。

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "ハンドメイドレザーウォレット",
  "price": 8500,
  "stockQuantity": 3,
  "status": "ACTIVE",
  "createdAt": "2026-05-24T10:00:00Z"
}
```

POST でリソース作成成功時は `201 Created` + `Location` ヘッダーを返す。

```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/products/550e8400-e29b-41d4-a716-446655440000

{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  ...
}
```

#### 8.4.2 コレクションレスポンス（ページネーション）

Spring Data の `Page<T>` を直接返さず、カスタム `PageResponse<T>` にラップして返す。フィールドは **camelCase**。

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "content": [
    { "id": "...", "name": "商品A", "price": 3000 },
    { "id": "...", "name": "商品B", "price": 5500 }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false
}
```

> `page` は0始まり。クライアントへの表示は `page + 1` で行う。

#### 8.4.3 エラーレスポンス（RFC 9457 / Problem Details）

Spring Boot 3.x は RFC 9457（Problem Details for HTTP APIs）をネイティブサポートする。  
`spring.mvc.problemdetails.enabled=true` を設定し、`ProblemDetail` を標準エラー形式として採用する。

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "https://kivio.example.com/problems/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "ID '550e8400' の商品は存在しないか、削除されています",
  "instance": "/api/v1/products/550e8400"
}
```

| フィールド | 説明 |
|---|---|
| `type` | エラー種別を示すURI（ドキュメントURLまたは `about:blank`） |
| `title` | エラー種別の短い説明（英語・固定値） |
| `status` | HTTPステータスコード（数値） |
| `detail` | この具体的なエラーの詳細説明（日本語可） |
| `instance` | エラーが発生したリクエストパス |

#### 8.4.4 バリデーションエラーレスポンス

`ProblemDetail` をカスタム拡張し、フィールドレベルのエラー詳細を追加する。

```http
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "https://kivio.example.com/problems/validation-failed",
  "title": "Validation Failed",
  "status": 422,
  "detail": "リクエストの入力値に不正があります",
  "instance": "/api/v1/products",
  "errors": [
    {
      "field": "name",
      "message": "商品名は必須です",
      "rejectedValue": ""
    },
    {
      "field": "price",
      "message": "価格は1円以上である必要があります",
      "rejectedValue": -100
    }
  ]
}
```

> サーバー内部エラー（500）では `detail` にスタックトレースやSQL文を絶対に含めない。汎用メッセージ「予期しないエラーが発生しました」のみ返す。

---

### 8.5 ページネーション・フィルタリング・ソート

#### ページネーション（クエリパラメータ）

```
GET /api/v1/products?page=0&size=20
```

| パラメータ | デフォルト | 説明 |
|---|---|---|
| `page` | `0` | ページ番号（0始まり） |
| `size` | `20` | 1ページあたりの件数（最大100） |

#### フィルタリング

```
# 単一値フィルター
GET /api/v1/products?categoryId={id}&status=ACTIVE

# 価格範囲フィルター
GET /api/v1/products?minPrice=1000&maxPrice=5000

# 在庫ありのみ
GET /api/v1/products?inStock=true
```

#### ソート

```
# 単一フィールドソート（prefix - で降順）
GET /api/v1/products?sort=price,asc
GET /api/v1/products?sort=createdAt,desc

# Spring Data の Pageable に準拠した形式
```

#### 全文検索

```
GET /api/v1/products?q=レザーウォレット
```

---

### 8.6 エラーコード体系

`type` URIのパス部分に対応するエラーコード（UPPER_SNAKE_CASE）を定義する。

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `VALIDATION_FAILED` | 422 | Bean Validation 失敗 |
| `RESOURCE_NOT_FOUND` | 404 | 対象リソースが存在しない |
| `ACCESS_DENIED` | 403 | 権限不足（他人のリソース等） |
| `UNAUTHORIZED` | 401 | 認証が必要 |
| `TOKEN_EXPIRED` | 401 | JWTトークンが期限切れ |
| `TOKEN_INVALID` | 401 | JWTトークンが不正 |
| `DUPLICATE_ENTRY` | 409 | 重複登録（同一メール等） |
| `EMAIL_ALREADY_REGISTERED` | 409 | メールアドレスが既に登録済み（登録ステップ1チェック） |
| `RATE_LIMIT_EXCEEDED` | 429 | レート制限超過 |
| `PRODUCT_OUT_OF_STOCK` | 409 | 商品の在庫不足 |
| `ORDER_NOT_CANCELLABLE` | 409 | キャンセル不可の注文ステータス |
| `SELLER_APPLICATION_PENDING` | 409 | 審査中の申請が既に存在する |
| `PAYMENT_FAILED` | 402 | Stripe決済失敗 |
| `INTERNAL_SERVER_ERROR` | 500 | サーバー内部エラー |

---

### 8.7 レート制限

認証エンドポイントとAPIコール全体にレート制限を設ける。超過時は `429 Too Many Requests` を返す。

```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1748080800
```

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 60

{
  "type": "https://kivio.example.com/problems/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "リクエスト数が上限に達しました。60秒後に再試行してください",
  "instance": "/api/v1/auth/login"
}
```

| エンドポイント区分 | 制限 |
|---|---|
| 認証系（`/auth/*`） | 10リクエスト / 分 / IP |
| API全般（認証済み） | 100リクエスト / 分 / ユーザー |
| 公開API（未認証） | 30リクエスト / 分 / IP |

---

## 9. リアルタイム通信設計

### 9.1 WebSocket エンドポイント

```
ws(s)://{host}/ws
```

### 9.2 STOMPトピック設計

| トピック | 用途 | 購読者 |
|---|---|---|
| `/topic/chat/{chatRoomId}` | チャットメッセージ受信 | バイヤー / セラー |
| `/topic/notifications/{userId}` | リアルタイム通知 | 個人 |
| `/topic/orders/{orderId}` | 注文ステータス変更 | バイヤー |
| `/app/chat.send` | チャットメッセージ送信（クライアント→サーバー） | - |

### 9.3 WebSocket認証

- HTTP HandshakeリクエストのQueryパラメータまたはHeaderでJWTを送信
- Springの `ChannelInterceptor` でトークン検証を行う

### 9.4 将来のRedis Pub/Sub対応

複数バックエンドインスタンス展開時に備え、`MessageBroker` インターフェースを抽象化し、実装を差し替え可能にする：

```java
// Phase 1: Spring内部ブローカー
// Phase 2: Redis Pub/Sub ブローカーに差し替え
```

---

## 10. 決済フロー設計

### 10.1 Stripe Payment Intent フロー

```
1. バイヤーが「注文確定」ボタンをクリック
   ↓
2. フロントエンド → バックエンド: POST /api/v1/orders/checkout
   ↓
3. バックエンドがStripe Payment Intentを作成
   → clientSecret をフロントエンドへ返す
   ↓
4. フロントエンドがStripe.js + Payment Elementで決済実行
   ↓
5. Stripe Webhook → バックエンド: payment_intent.succeeded
   ↓
6. バックエンドが注文ステータスを PAYMENT_CONFIRMED に更新
   → 在庫数を減算
   → 通知を送信（WebSocket + メール）
```

### 10.2 キャンセル・返金フロー

```
1. バイヤー/セラーがキャンセルリクエスト
   ↓
2. 注文ステータスが PAYMENT_CONFIRMED または PROCESSING の場合のみ許可
   ↓
3. Stripe Refunds API を呼び出し（全額返金）
   ↓
4. 注文ステータスを CANCELLED に更新
   ↓
5. 在庫数を戻す
   ↓
6. 双方に通知
```

---

## 11. 開発・デプロイ要件

### 11.1 開発環境

- devcontainer（VS Code Dev Container）を使用し、ローカル環境差異を排除する
- `docker-compose.yml` でPostgreSQL・バックエンド・フロントエンドを一括起動できる

### 11.2 ディレクトリ構造

```
kivio/
├── kivio-backend/        # Spring Boot アプリケーション
├── kivio-frontend/       # Next.js アプリケーション
├── .devcontainer/        # devcontainer 設定
├── adr/                  # Architecture Decision Records
├── docs/                 # RFP・要件定義書等
├── ai-context/           # AI向けコンテキスト
├── .claude/              # Claude Code 設定
├── infra/                # インフラ設定（将来のIaCなど）
├── CLAUDE.md
└── README.md
```

### 11.3 バックエンドパッケージ構造

```
com.kivio/
├── common/               # 共通ユーティリティ・例外・レスポンス型
├── config/               # Spring設定（Security, WebSocket, etc.）
├── domain/
│   ├── identity/         # User, Auth, SellerApplication
│   ├── catalog/          # Shop, Product, Category
│   ├── order/            # Cart, Order, Payment
│   ├── messaging/        # ChatRoom, ChatMessage
│   ├── notification/     # Notification
│   ├── review/           # Review
│   ├── platform/         # PlatformConfig
│   └── audit/            # AuditLog（@Aspect + @Auditable + AuditLogRepository）
└── infra/                # 外部サービス統合（Stripe, Cloudinary, Resend）
```

### 11.4 GitHub Actions CI/CD

```yaml
# PRトリガー: Lint + Unit Test + Integration Test
# mainへのpush:
#   - Backend: Gradleビルド → Docker image → Render/Railway デプロイ
#   - Frontend: Vercel は GitHub連携により自動デプロイ
```

### 11.5 テスト方針

| レイヤー | テスト種別 | ツール |
|---|---|---|
| Service層 | ユニットテスト | JUnit 5 + Mockito |
| Controller層 | Webレイヤーテスト | MockMvc / @WebMvcTest |
| Repository層 | 統合テスト | Testcontainers + PostgreSQL |
| API全体 | 統合テスト | @SpringBootTest + Testcontainers |

### 11.6 サンプルデータ（Seed）

デモ用に以下のシードデータを `flyway` または `ApplicationRunner` で投入する：

| データ | 内容 |
|---|---|
| ユーザー | バイヤー3名・セラー5名・アドミン1名 |
| ショップ | 5店舗（各セラーに1つ） |
| カテゴリー | 10カテゴリー（ハンドメイド・アクセサリー・インテリア等） |
| 商品 | 各ショップに5〜10商品（画像付き） |
| 注文 | 各バイヤーに2〜3件の完了済み注文 |
| レビュー | 一部の商品に3〜5件のレビュー |

---

## 12. フェーズ別スコープ

| フェーズ | 内容 | Must機能 |
|---|---|---|
| **Phase 1** | 要件確定・ドキュメント・ADR作成・devcontainer構築 | 本ドキュメント |
| **Phase 2** | 基盤構築・認証・ユーザー管理・セラー申請・ショップ・商品CRUD・監査ログ基盤 | AUTH, USER, SELLER, SHOP, PRD, AUDIT |
| **Phase 3** | 商品検索・カート・チェックアウト・Stripe決済・注文管理 | SRCH, CART, PAY, ORD |
| **Phase 4** | リアルタイムチャット・通知・セラーダッシュボード・管理者機能 | CHAT, NOTIF, DASH, ADM |
| **Phase 5** | Should機能（レビュー・お気に入り・メール通知）・テスト強化・デプロイ・ドキュメント整備 | REV, FAV, MAIL |

---

## 13. スコープ外（将来対応）

以下は本プロジェクトのスコープ外とし、将来の拡張として設計上の考慮のみ行う。

| 機能 | 理由 |
|---|---|
| 売上金出金（Stripe Connect） | 実装コスト高・MVPには不要 |
| 配送業者API連携 | ポートフォリオスコープ外 |
| 商品レコメンド | Phase 5以降で検討 |
| SEO最適化（OGP・サイトマップ等） | 基本的なメタタグは実装、高度なSEOは後回し |
| 複数スタッフによるショップ管理 | ドメインモデルに設計考慮済み、実装は将来 |
| Redis Pub/Sub（WebSocketスケーリング） | インターフェース設計済み、実装は将来 |
| モバイルアプリ（iOS/Android） | Web（レスポンシブ）のみ |
| 多言語対応 | 構造のみi18n対応、UIは日本語のみ |
| 二段階認証（2FA）複数方式 | DB設計考慮済み（`user_mfa_methods`）、実装は将来 |
| 物理削除（Hard Delete）機能 | 全削除操作はSoft deleteで統一し、物理削除は提供しない |

---

## 14. 用語集

| 用語 | 定義 |
|---|---|
| バイヤー | 商品を購入するユーザー（`ROLE_BUYER`） |
| セラー | ショップを開設・商品を販売するユーザー（`ROLE_SELLER`） |
| アドミン | プラットフォーム管理者（`ROLE_ADMIN`） |
| ショップ | セラーが開設するストア。セラーと1対1 |
| 手数料率 | プラットフォームが徴収する割合（`PlatformConfig.commission_rate`） |
| セラー取り分 | `totalAmount × (1 - commissionRate)` |
| STOMP | Simple Text Oriented Messaging Protocol。WebSocket上で動作するメッセージングプロトコル |
| モジュラーモノリス | 単一デプロイ単位だが、内部はドメイン境界で明確に分離されたアーキテクチャ |

---

## 15. データ保持ポリシー

### 15.1 基本方針

データ保持期間は **法的義務** と **ビジネス要件** の2軸で決定する。
「削除」は物理削除ではなく **匿名化** を原則とし、外部キー参照の整合性と会計記録の継続性を保つ。

### 15.2 法的根拠（日本）

| データ種別 | 法的根拠 | 最低保持期間 |
|---|---|---|
| 注文・決済・請求記録 | 法人税法・青色申告・電子帳簿保存法 | **7年** |
| ユーザー個人情報（PII） | 個人情報保護法（APPI） | 利用目的終了後に速やかに削除 |
| チャット・通知など | 特定法令なし | 任意（ポリシー定義） |

### 15.3 エンティティ別保持ポリシー

| エンティティ | Soft Delete後の保持期間 | 期限到達後の処置 | 理由 |
|---|---|---|---|
| `users` | 90日 | **匿名化**（PII削除・IDは保持） | 注文履歴との参照整合性維持 |
| `shops` | 90日 | 匿名化（ショップ名・説明文を削除） | ユーザー退会時に連動 |
| `categories` | 180日 | 物理削除 | 商品の`category_id`はNULL許容で設計 |
| `products` | 180日（`status='DELETED'`後） | 物理削除 | 注文明細はスナップショット保存のため影響なし |
| `orders` / `order_items` | **7年** | 匿名化（PII項目のみ） | 法人税法・青色申告要件 |
| `payments` | **7年** | 匿名化不可（Stripe IDは保持必須） | 会計・監査要件 |
| `reviews` | 1年（投稿後） | 物理削除 | ユーザー退会済み後は匿名化で表示継続 |
| `chat_messages` | 1年 | 物理削除 | プライバシー・ストレージコスト |
| `notifications` | 90日（`expires_at`設計済み） | 非表示→物理削除 | NOTIF-03で規定済み |
| `refresh_tokens` | 30日（期限切れ後） | 物理削除 | 失効済みトークンは不要 |
| `audit_logs` | 1年（DB保持）→ 3年（アーカイブ） | アーカイブ後に物理削除 | PCI DSS準拠・将来のStripe Connect対応 |

### 15.4 匿名化の実装方針

単純な物理削除は外部キー参照を破壊するため、ユーザー・注文系は **匿名化（Anonymization）** で対応する。

```sql
-- users 匿名化の例（90日経過後のバッチ処理）
UPDATE users SET
  email        = 'deleted_' || id || '@kivio.invalid',
  name         = '退会済みユーザー',
  password_hash = NULL,
  avatar_url   = NULL,
  google_id    = NULL
WHERE deleted_at < NOW() - INTERVAL '90 days'
  AND email NOT LIKE 'deleted_%';  -- 冪等性確保
```

**匿名化後も保持するフィールド：** `id`、`created_at`、`deleted_at`、ロール情報（統計用途）

### 15.5 audit_logs のアーカイブ戦略

`audit_logs` は追記専用かつ大量データになるため、**PostgreSQLパーティショニング**で管理する。

```sql
-- 月別パーティション（RANGE on created_at）
CREATE TABLE audit_logs (
  id             BIGINT,
  created_at     TIMESTAMPTZ NOT NULL,
  ...
) PARTITION BY RANGE (created_at);

-- 月次で自動生成、古いパーティションをDROPで一括削除
-- DROP TABLE audit_logs_2025_01;  ← DELETEより100倍高速
```

| 期間 | 保存場所 | 検索可否 |
|---|---|---|
| 0〜1年 | PostgreSQL（パーティション済み） | 即時検索可能 |
| 1〜3年 | S3 / GCS（JSON/Parquet形式）※Phase 5以降 | 必要時のみ |
| 3年超 | 削除 | - |

> **MVP（Phase 2〜4）では** パーティショニングの実装のみ行い、S3アーカイブはPhase 5以降で対応する。

### 15.6 バッチ処理設計

Spring `@Scheduled` によるバッチジョブを実装する（Spring Batchは現スコープでは過剰）。

| ジョブ名 | 実行サイクル | 処理内容 |
|---|---|---|
| `UserAnonymizationJob` | 毎週日曜3:00 | soft delete後90日超のユーザーを匿名化 |
| `ProductPurgeJob` | 毎週日曜3:30 | `DELETED`ステータス後180日超の商品を物理削除 |
| `ChatMessagePurgeJob` | 毎月1日4:00 | 作成から1年超のチャットメッセージを物理削除 |
| `NotificationPurgeJob` | 毎日2:00 | `expires_at < NOW()` の通知を物理削除 |
| `RefreshTokenPurgeJob` | 毎日2:30 | 期限切れ・失効済みRefreshTokenを物理削除 |
| `AuditLogArchiveJob` | 毎月1日5:00 | 1年超の監査ログをアーカイブ（Phase 5） |

```java
// 実装例（Phase 2以降）
@Scheduled(cron = "0 0 3 * * SUN")
@Transactional
public void anonymizeExpiredUsers() {
    userRepository.anonymizeUsersDeletedBefore(
        LocalDateTime.now().minusDays(90)
    );
}
```

**バッチ実行ログ** は `audit_logs` にアクター `actor_id = null`（システム実行）として記録する。

### 15.7 要件まとめ

| # | 要件 |
|---|---|
| RET-01 | ユーザー・ショップは soft delete後90日経過で匿名化する（物理削除不可） |
| RET-02 | 商品は `DELETED` ステータス遷移後180日経過で物理削除する |
| RET-03 | 注文・決済レコードは7年間保持し、PII項目のみ匿名化する（Stripe IDは保持） |
| RET-04 | チャットメッセージ・レビューは作成から1年後に物理削除する |
| RET-05 | 通知は `expires_at`（90日）経過後に物理削除する |
| RET-06 | `audit_logs` は1年間DBに保持し、パーティションDROPで期限管理する |
| RET-07 | バッチジョブは `@Scheduled` で実装し、実行結果を `audit_logs` に記録する |
| RET-08 | `audit_logs` テーブルはcreated_atによる月別パーティショニングを採用する |

---

**以上**

*本要件定義書はヒアリング結果を基に作成されました。設計上の疑問点や仕様変更が発生した場合は、[ADR（Architecture Decision Record）](../adr/)に記録します。*
