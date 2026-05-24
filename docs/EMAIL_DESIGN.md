# Kivio — メール設計書

> 送信基盤: [Resend](https://resend.com/)  
> 参照: `SEQUENCE_FLOW.md`（送信タイミング）、`API_DESIGN.md`（エンドポイント）、`ERROR_CODES.md`（エラーコード）

---

## 目次

1. [概要](#1-概要)
2. [トリガー一覧](#2-トリガー一覧)
3. [テンプレート定義](#3-テンプレート定義)
   - [AUTH-01: メール認証](#auth-01-メール認証)
   - [SEL-01: 出品者申請 承認通知](#sel-01-出品者申請-承認通知)
   - [SEL-02: 出品者申請 拒否通知](#sel-02-出品者申請-拒否通知)
   - [ORD-01: 注文確定通知（Buyer）](#ord-01-注文確定通知buyer)
   - [ORD-02: 新規注文受付通知（Seller）](#ord-02-新規注文受付通知seller)
   - [ORD-03: 発送完了通知（Buyer）](#ord-03-発送完了通知buyer)
   - [ORD-04: 注文キャンセル通知（Buyer）](#ord-04-注文キャンセル通知buyer)
   - [ORD-05: 注文キャンセル通知（Seller）](#ord-05-注文キャンセル通知seller)
4. [i18n 対応方針](#4-i18n-対応方針)
5. [Spring 実装ガイドライン](#5-spring-実装ガイドライン)
6. [Resend API リファレンス](#6-resend-api-リファレンス)

---

## 1. 概要

### 送信設定

| 項目 | 値 |
|---|---|
| メール送信サービス | Resend |
| 送信元アドレス | `noreply@kivio.example.com` |
| 送信元表示名 | `Kivio` |
| 本文言語 | 日本語（将来の i18n 対応は § 4 参照） |
| 環境変数 | `RESEND_API_KEY`, `MAIL_FROM_ADDRESS`, `APP_BASE_URL` |

### フェーズ別実装スケジュール

| フェーズ | 対象テンプレート |
|---|---|
| Phase 2 | AUTH-01, SEL-01, SEL-02 |
| Phase 5 | ORD-01, ORD-02, ORD-03, ORD-04, ORD-05 |

---

## 2. トリガー一覧

| ID | イベント | Phase | 送信先 | 送信タイミング / API 起点 |
|---|---|---|---|---|
| AUTH-01 | メール認証 | 2 | 登録ユーザ | `POST /api/v1/auth/register` 完了後 |
| SEL-01 | 出品者申請 承認通知 | 2 | 申請者 | `PATCH /api/v1/admin/seller-applications/{id}/approve` |
| SEL-02 | 出品者申請 拒否通知 | 2 | 申請者 | `PATCH /api/v1/admin/seller-applications/{id}/reject` |
| ORD-01 | 注文確定通知 | 5 | Buyer | Stripe Webhook `payment_intent.succeeded` |
| ORD-02 | 新規注文受付通知 | 5 | Seller | Stripe Webhook `payment_intent.succeeded` |
| ORD-03 | 発送完了通知 | 5 | Buyer | `PATCH /api/v1/orders/{id}/status` (→ SHIPPED) |
| ORD-04 | 注文キャンセル通知 | 5 | Buyer | `POST /api/v1/orders/{id}/cancel` |
| ORD-05 | 注文キャンセル通知 | 5 | Seller | `POST /api/v1/orders/{id}/cancel` |

**スコープ外（今フェーズ未定義）:**  
パスワードリセット（API未定義）、レビュー催促、ウィッシュリスト在庫復活

---

## 3. テンプレート定義

### 本文共通構造

全テンプレートは以下のセクション構成を持つ:

```
[1] ヘッダ      — Kivio ロゴ + ブランドカラー帯
[2] タイトル    — メール種別を端的に示す1文
[3] 挨拶        — "{{userName}} 様"
[4] 本文        — イベント固有のメッセージと詳細情報
[5] CTA ボタン  — 主要アクションリンク（任意）
[6] フッタ      — サービス名・サポートURL・配信停止案内
```

---

### AUTH-01: メール認証

**送信タイミング:** 新規登録直後 (`POST /auth/register`)  
**有効期限:** 24時間  
**SEQUENCE_FLOW.md 参照:** § 1.1

| 項目 | 内容 |
|---|---|
| 件名（ja） | `【Kivio】ご登録メールアドレスの確認をお願いします` |
| 件名（en） | _(未定義)_ |
| テンプレートキー | `emails/ja/email-verification.html` |
| 送信先 | 登録者のメールアドレス |

#### 本文変数

| 変数名 | 型 | 説明 | 取得元 |
|---|---|---|---|
| `{{userName}}` | String | 登録時の表示名 | `users.display_name` |
| `{{verificationUrl}}` | String | 認証リンク（トークン埋め込み済み） | `APP_BASE_URL + /auth/verify?token=` |
| `{{expiresIn}}` | String | 有効期限の説明文 | 固定: `"24時間"` |

#### 本文構造

```
[タイトル]
メールアドレスのご確認

[本文]
{{userName}} 様

このたびはKivioにご登録いただき、誠にありがとうございます。

以下のボタンをクリックして、メールアドレスの確認を完了してください。
このリンクは {{expiresIn}} 後に失効します。

[CTA]
「メールアドレスを確認する」→ {{verificationUrl}}

[補足]
※ このメールにお心当たりのない場合は、そのまま削除してください。
  アカウントが有効化されることはありません。
※ ボタンが機能しない場合は、以下のURLをブラウザに直接貼り付けてください。
  {{verificationUrl}}
```

---

### SEL-01: 出品者申請 承認通知

**送信タイミング:** Admin が申請を承認した直後  
**SEQUENCE_FLOW.md 参照:** § 3 (③a 承認)

| 項目 | 内容 |
|---|---|
| 件名（ja） | `【Kivio】出品者登録が完了しました — ショップを開設しましょう` |
| 件名（en） | _(未定義)_ |
| テンプレートキー | `emails/ja/seller-approved.html` |
| 送信先 | `seller_applications.user_id` のメールアドレス |

#### 本文変数

| 変数名 | 型 | 説明 | 取得元 |
|---|---|---|---|
| `{{userName}}` | String | 申請者の表示名 | `users.display_name` |
| `{{shopSetupUrl}}` | String | ショップ設定ページ URL | `APP_BASE_URL + /seller/shop/setup` |

#### 本文構造

```
[タイトル]
出品者登録が完了しました

[本文]
{{userName}} 様

おめでとうございます！
Kivioへの出品者申請が承認されました。

これよりKivioマーケットプレイスで商品を販売いただけます。
まずは以下の初期設定を完了してスタートしましょう。

・ショップ名・プロフィール・アイコンの設定
・配送ポリシー・発送元住所の登録
・売上受取口座の設定
・はじめての商品を出品

[CTA]
「ショップを開設する」→ {{shopSetupUrl}}

出品に関するご不明点は、サポートページからいつでもお問い合わせください。
引き続きKivioをよろしくお願いいたします。
```

---

### SEL-02: 出品者申請 拒否通知

**送信タイミング:** Admin が申請を拒否した直後  
**SEQUENCE_FLOW.md 参照:** § 3 (③b 拒否)

| 項目 | 内容 |
|---|---|
| 件名（ja） | `【Kivio】出品者申請の審査結果のご連絡` |
| 件名（en） | _(未定義)_ |
| テンプレートキー | `emails/ja/seller-rejected.html` |
| 送信先 | `seller_applications.user_id` のメールアドレス |

#### 本文変数

| 変数名 | 型 | 説明 | 取得元 |
|---|---|---|---|
| `{{userName}}` | String | 申請者の表示名 | `users.display_name` |
| `{{rejectionReason}}` | String | 拒否理由 | `seller_applications.rejection_reason` |
| `{{reapplyUrl}}` | String | 再申請ページ URL | `APP_BASE_URL + /seller/apply` |

#### 本文構造

```
[タイトル]
出品者申請の審査結果について

[本文]
{{userName}} 様

このたびはKivioへの出品者申請にお申し込みいただき、誠にありがとうございました。
慎重に審査いたしました結果、誠に恐れながら、今回は承認が叶いませんでした。

【審査コメント】
{{rejectionReason}}

上記の内容をご確認・改善いただいたうえで、再度ご申請いただくことが可能です。
Kivioでのご出品を心よりお待ちしております。

[CTA]
「再申請する」→ {{reapplyUrl}}

[補足]
審査結果に関するご不明な点は、サポートページよりお問い合わせください。
```

---

### ORD-01: 注文確定通知（Buyer）

**送信タイミング:** Stripe Webhook `payment_intent.succeeded` 処理後  
**SEQUENCE_FLOW.md 参照:** § 2.1 (③ Stripe Webhook)

| 項目 | 内容 |
|---|---|
| 件名（ja） | `【Kivio】ご注文の確認 {{orderNumber}}` |
| 件名（en） | _(未定義)_ |
| テンプレートキー | `emails/ja/order-confirmed.html` |
| 送信先 | 注文者 (`orders.user_id`) のメールアドレス |

#### 本文変数

| 変数名 | 型 | 説明 | 取得元 |
|---|---|---|---|
| `{{userName}}` | String | 購入者の表示名 | `users.display_name` |
| `{{orderNumber}}` | String | 注文番号（表示用） | `orders.id`（UUID先頭8文字、例: `#a1b2c3d4`） |
| `{{shopName}}` | String | 出品ショップ名 | `shops.name` |
| `{{orderItems}}` | List | 商品名・数量・単価のリスト | `order_items` |
| `{{totalAmount}}` | String | 合計金額（フォーマット済み） | `orders.total_amount` → `¥1,500` 形式 |
| `{{orderedAt}}` | String | 注文日時（JST） | `orders.created_at` → `2026年5月24日 10:00` 形式 |
| `{{shippingAddress}}` | String | 配送先住所 | `orders.shipping_address`（スナップショット） |
| `{{orderDetailUrl}}` | String | 注文詳細ページ URL | `APP_BASE_URL + /orders/{{orderNumber}}` |

#### 本文構造

```
[タイトル]
ご注文ありがとうございます

[本文]
{{userName}} 様

ご注文を承りました。出品者が発送準備を開始します。
商品の発送が完了次第、発送通知メールをお送りします。

━━━━━━━━━━━━━━━━━━
注文番号: {{orderNumber}}
注文日時: {{orderedAt}}
ショップ: {{shopName}}
━━━━━━━━━━━━━━━━━━

【ご注文商品】
(orderItems の繰り返し)
  {{item.productName}} × {{item.quantity}}    ¥{{item.unitPrice}}

合計金額: {{totalAmount}}

【お届け先】
{{shippingAddress}}

[CTA]
「注文詳細を確認する」→ {{orderDetailUrl}}

ご不明な点はサポートページよりお問い合わせください。
引き続きKivioをご利用いただきありがとうございます。
```

---

### ORD-02: 新規注文受付通知（Seller）

**送信タイミング:** Stripe Webhook `payment_intent.succeeded` 処理後（ORD-01 と同タイミング）  
**SEQUENCE_FLOW.md 参照:** § 2.1 (③ Stripe Webhook)

| 項目 | 内容 |
|---|---|
| 件名（ja） | `【Kivio】新規注文のお知らせ — {{orderNumber}}` |
| 件名（en） | _(未定義)_ |
| テンプレートキー | `emails/ja/order-received.html` |
| 送信先 | ショップオーナー (`shops.seller_id`) のメールアドレス |

#### 本文変数

| 変数名 | 型 | 説明 | 取得元 |
|---|---|---|---|
| `{{sellerName}}` | String | 出品者の表示名 | `users.display_name` |
| `{{orderNumber}}` | String | 注文番号 | `orders.id` |
| `{{orderItems}}` | List | 商品名・数量・単価のリスト | `order_items` |
| `{{totalAmount}}` | String | 注文合計金額 | `orders.total_amount` → `¥フォーマット` |
| `{{orderedAt}}` | String | 注文日時（JST） | `orders.created_at` |
| `{{shippingAddress}}` | String | 購入者の配送先住所 | `orders.shipping_address`（スナップショット） |
| `{{shippingDeadline}}` | String | 発送期限（JST） | 注文日時 + 3営業日（サービス側で計算） |
| `{{orderManageUrl}}` | String | 注文管理ページ URL | `APP_BASE_URL + /seller/orders/{{orderNumber}}` |

#### 本文構造

```
[タイトル]
新しい注文が届きました

[本文]
{{sellerName}} 様

Kivioに新しい注文が届きました。
発送期限までに発送手続きをお願いいたします。

━━━━━━━━━━━━━━━━━━
注文番号: {{orderNumber}}
注文日時: {{orderedAt}}
発送期限: {{shippingDeadline}}
━━━━━━━━━━━━━━━━━━

【注文商品】
(orderItems の繰り返し)
  {{item.productName}} × {{item.quantity}}    ¥{{item.unitPrice}}

合計金額: {{totalAmount}}

【配送先】
{{shippingAddress}}

[CTA]
「注文を確認・発送する」→ {{orderManageUrl}}

期限内に発送手続きが完了しない場合、自動的にキャンセルとなる場合があります。
ご不明な点はサポートページよりお問い合わせください。
```

---

### ORD-03: 発送完了通知（Buyer）

**送信タイミング:** Seller が注文ステータスを SHIPPED に更新した後  
**SEQUENCE_FLOW.md 参照:** § 4.2 (SHIPPED)

| 項目 | 内容 |
|---|---|
| 件名（ja） | `【Kivio】お荷物を発送しました — {{orderNumber}}` |
| 件名（en） | _(未定義)_ |
| テンプレートキー | `emails/ja/order-shipped.html` |
| 送信先 | 注文者 (`orders.user_id`) のメールアドレス |

#### 本文変数

| 変数名 | 型 | 説明 | 取得元 |
|---|---|---|---|
| `{{userName}}` | String | 購入者の表示名 | `users.display_name` |
| `{{orderNumber}}` | String | 注文番号 | `orders.id` |
| `{{shopName}}` | String | 出品ショップ名 | `shops.name` |
| `{{carrierName}}` | String | 配送業者名（例: ヤマト運輸） | Seller 入力値 |
| `{{trackingNumber}}` | String | 追跡番号 | `orders.tracking_number` |
| `{{shippedAt}}` | String | 発送日時（JST） | ステータス変更時点のタイムスタンプ |
| `{{estimatedDeliveryDate}}` | String | 配達予定日（任意・Seller 入力値） | Seller 入力値（未入力時は非表示） |
| `{{orderDetailUrl}}` | String | 注文詳細ページ URL | `APP_BASE_URL + /orders/{{orderNumber}}` |

> `{{shippedAt}}` はステータス変更イベントのタイムスタンプを渡すこと。`orders.updated_at` は後続の更新で上書きされる可能性があるため使用しない。

#### 本文構造

```
[タイトル]
お荷物を発送しました

[本文]
{{userName}} 様

{{shopName}} よりお荷物を発送いたしました。
到着まで今しばらくお待ちください。

━━━━━━━━━━━━━━━━━━
注文番号: {{orderNumber}}
発送日時: {{shippedAt}}
配送業者: {{carrierName}}
追跡番号: {{trackingNumber}}
配達予定: {{estimatedDeliveryDate}}  ← 入力がある場合のみ表示
━━━━━━━━━━━━━━━━━━

商品がお手元に届きましたら、受取確認をお願いします。
受取確認後、取引が完了となります。

[CTA]
「荷物の状況を確認する」→ {{orderDetailUrl}}

ご不明な点はサポートページよりお問い合わせください。
```

---

### ORD-04: 注文キャンセル通知（Buyer）

**送信タイミング:** `POST /orders/{id}/cancel` 処理後（返金処理完了後）  
**SEQUENCE_FLOW.md 参照:** § 2.2 (キャンセル & 自動返金)

| 項目 | 内容 |
|---|---|
| 件名（ja） | `【Kivio】ご注文のキャンセルが完了しました — {{orderNumber}}` |
| 件名（en） | _(未定義)_ |
| テンプレートキー | `emails/ja/order-cancelled-buyer.html` |
| 送信先 | 注文者 (`orders.user_id`) のメールアドレス |

#### 本文変数

| 変数名 | 型 | 説明 | 取得元 |
|---|---|---|---|
| `{{userName}}` | String | 購入者の表示名 | `users.display_name` |
| `{{orderNumber}}` | String | 注文番号 | `orders.id` |
| `{{shopName}}` | String | 出品ショップ名 | `shops.name` |
| `{{refundAmount}}` | String | 返金額（フォーマット済み） | `payments.amount` → `¥フォーマット` |
| `{{cancelledAt}}` | String | キャンセル日時（JST） | キャンセルイベントのタイムスタンプ |
| `{{orderDetailUrl}}` | String | 注文詳細ページ URL | `APP_BASE_URL + /orders/{{orderNumber}}` |

> `{{cancelledAt}}` はキャンセルイベントのタイムスタンプを渡すこと。`orders.updated_at` は使用しない。

#### 本文構造

```
[タイトル]
ご注文のキャンセルが完了しました

[本文]
{{userName}} 様

以下のご注文のキャンセルが完了しました。

━━━━━━━━━━━━━━━━━━
注文番号: {{orderNumber}}
ショップ: {{shopName}}
キャンセル日時: {{cancelledAt}}
━━━━━━━━━━━━━━━━━━

【返金について】
返金金額: {{refundAmount}}
ご利用のクレジットカードに返金いたします。
反映までの目安は3〜10営業日です（カード会社により異なります）。

[CTA]
「注文詳細を確認する」→ {{orderDetailUrl}}

返金に関してご不明な点がございましたら、サポートページよりお問い合わせください。
引き続きKivioをよろしくお願いいたします。
```

---

### ORD-05: 注文キャンセル通知（Seller）

**送信タイミング:** `POST /orders/{id}/cancel` 処理後（ORD-04 と同タイミング）  
**SEQUENCE_FLOW.md 参照:** § 2.2 (キャンセル & 自動返金)

| 項目 | 内容 |
|---|---|
| 件名（ja） | `【Kivio】【重要】注文キャンセルのお知らせ — {{orderNumber}}` |
| 件名（en） | _(未定義)_ |
| テンプレートキー | `emails/ja/order-cancelled-seller.html` |
| 送信先 | ショップオーナー (`shops.seller_id`) のメールアドレス |

#### 本文変数

| 変数名 | 型 | 説明 | 取得元 |
|---|---|---|---|
| `{{sellerName}}` | String | 出品者の表示名 | `users.display_name` |
| `{{orderNumber}}` | String | 注文番号 | `orders.id` |
| `{{orderItems}}` | List | キャンセルされた商品リスト | `order_items` |
| `{{cancelledAt}}` | String | キャンセル日時（JST） | キャンセルイベントのタイムスタンプ |
| `{{orderManageUrl}}` | String | 注文管理ページ URL | `APP_BASE_URL + /seller/orders` |

#### 本文構造

```
[タイトル]
注文がキャンセルされました

[本文]
{{sellerName}} 様

以下の注文がキャンセルされました。
キャンセルに伴い、対象商品の在庫は自動的に元に戻されています。

━━━━━━━━━━━━━━━━━━
注文番号: {{orderNumber}}
キャンセル日時: {{cancelledAt}}
━━━━━━━━━━━━━━━━━━

【キャンセル対象商品】
(orderItems の繰り返し)
  {{item.productName}} × {{item.quantity}}

すでに発送手続きを行っていた場合は、返送対応が必要になることがあります。
詳細はサポートページの「キャンセル時の対応ガイド」をご確認ください。

[CTA]
「注文管理を確認する」→ {{orderManageUrl}}
```

---

## 4. i18n 対応方針

### 現状（Phase 2〜5）

- 本文言語: **日本語のみ**（REQUIREMENTS.md I18N-01 に基づく）
- テンプレートファイルパス: `src/main/resources/templates/emails/ja/{template-id}.html`

### 将来の多言語化に向けた構造化

```
src/main/resources/templates/emails/
├── ja/
│   ├── email-verification.html
│   ├── seller-approved.html
│   ├── seller-rejected.html
│   ├── order-confirmed.html
│   ├── order-received.html
│   ├── order-shipped.html
│   ├── order-cancelled-buyer.html
│   └── order-cancelled-seller.html
└── en/                          ← 将来追加（Phase 5+）
    └── ...
```

### 変数フォーマット規則

| データ種別 | フォーマット | 例 |
|---|---|---|
| 金額 | `¥` + 整数（カンマ区切り） | `¥1,500` |
| 日時 | `YYYY年M月D日 HH:mm`（JST） | `2026年5月24日 10:00` |
| 追跡番号 | そのまま文字列で渡す | `1234-5678-9012` |
| 注文番号 | UUID の先頭8文字に `#` を付与 | `#a1b2c3d4` |

> フォーマット処理は `EmailTemplateFormatter` サービス側で行い、テンプレートには加工済み文字列を渡す。

---

## 5. Spring 実装ガイドライン

### パッケージ配置

```
com.kivio/
└── infra/
    └── email/
        ├── EmailService.java          ← インターフェース
        ├── ResendEmailService.java    ← Resend 実装
        ├── ResendClient.java          ← HTTP クライアントラッパー
        ├── dto/
        │   └── OrderEmailDto.java     ← Order 系メール用 DTO
        └── template/
            └── EmailTemplateFormatter.java  ← 変数フォーマット
```

### EmailService インターフェース

```java
public interface EmailService {
    void sendEmailVerification(String toEmail, String userName, String verificationUrl);
    void sendSellerApproved(String toEmail, String userName);
    void sendSellerRejected(String toEmail, String userName, String rejectionReason);
    void sendOrderConfirmed(String toEmail, OrderEmailDto order);            // ORD-01
    void sendOrderReceived(String toEmail, OrderEmailDto order);             // ORD-02
    void sendOrderShipped(String toEmail, String userName, String orderNumber,
                          String carrierName, String trackingNumber,
                          String shippedAt, String estimatedDeliveryDate);  // ORD-03
    void sendOrderCancelledToBuyer(String toEmail, String userName,
                                   String orderNumber, String shopName,
                                   String refundAmount, String cancelledAt); // ORD-04
    void sendOrderCancelledToSeller(String toEmail, String sellerName,
                                    OrderEmailDto order);                    // ORD-05
}
```

### 実装方針

```java
@Service
@RequiredArgsConstructor
public class ResendEmailService implements EmailService {

    private final ResendClient resendClient;
    private final EmailTemplateFormatter formatter;

    @Async                                            // メール失敗でビジネスロジックを止めない
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Override
    public void sendEmailVerification(String toEmail, String userName, String verificationUrl) {
        var body = formatter.render("emails/ja/email-verification.html",
            Map.of("userName", userName, "verificationUrl", verificationUrl, "expiresIn", "24時間"));
        resendClient.send(toEmail, "【Kivio】ご登録メールアドレスの確認をお願いします", body);
    }
    // ... 他メソッド
}
```

### 失敗ハンドリング

| ケース | 対応 |
|---|---|
| Resend 一時エラー（429 / 5xx） | `@Retryable` で最大3回リトライ（exponential backoff） |
| 3回リトライ後も失敗 | `log.error` でメールアドレス・テンプレートIDを記録し、ビジネスロジックへは伝播させない |
| 送信先アドレス不正（4xx） | リトライせず即座に `log.warn` |
| 全件失敗監視 | Phase 5+ で Dead Letter Queue または管理画面アラートを検討 |

> **ERROR_CODES.md 追記提案:** `EMAIL_SEND_FAILED` (500) — Resend 送信失敗（管理者ログ用、クライアントには非公開）

---

## 6. Resend API リファレンス

### エンドポイント

```
POST https://api.resend.com/emails
Authorization: Bearer {RESEND_API_KEY}
Content-Type: application/json
```

### リクエスト

```json
{
  "from": "Kivio <noreply@kivio.example.com>",
  "to": ["user@example.com"],
  "subject": "【Kivio】ご登録メールアドレスの確認をお願いします",
  "html": "<html>...</html>"
}
```

### レスポンス

| ステータス | 意味 |
|---|---|
| 200 / 201 | 送信キュー投入成功 |
| 400 | リクエスト不正（アドレス形式エラー等） |
| 401 | API キー無効 |
| 429 | レート制限超過（リトライ対象） |
| 5xx | Resend サーバエラー（リトライ対象） |

### Spring RestClient 実装例

```java
@Component
@RequiredArgsConstructor
public class ResendClient {

    private final RestClient restClient;

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${mail.from-address}")
    private String fromAddress;

    public void send(String to, String subject, String html) {
        restClient.post()
            .uri("https://api.resend.com/emails")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "from", "Kivio <" + fromAddress + ">",
                "to", List.of(to),
                "subject", subject,
                "html", html
            ))
            .retrieve()
            .toBodilessEntity();
    }
}
```

---

*最終更新: 2026-05-24*
