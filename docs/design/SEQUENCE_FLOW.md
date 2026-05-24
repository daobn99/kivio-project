# Kivio — シーケンス図 / フロー設計書

> 図は [Mermaid](https://mermaid.js.org/) で記述。  
> 参照: `API_DESIGN.md`（エンドポイント）、`ERROR_CODES.md`（エラーコード）、`REQUIREMENTS.md`（業務ルール）

---

## 目次

1. [Auth](#1-auth)
   - [1.1 メール登録](#11-メール登録)
   - [1.2 ログイン & JWT リフレッシュ / ログアウト](#12-ログイン--jwt-リフレッシュ--ログアウト)
   - [1.3 Google OAuth (NextAuth.js フロー)](#13-google-oauth-nextauthjs-フロー)
2. [Payment](#2-payment)
   - [2.1 Stripe PaymentIntent → 注文確定](#21-stripe-paymentintent--注文確定)
   - [2.2 キャンセル & 自動返金](#22-キャンセル--自動返金)
3. [Seller Application](#3-seller-application)
4. [Order Lifecycle](#4-order-lifecycle)
   - [4.1 状態遷移図](#41-状態遷移図)
   - [4.2 ステータス更新シーケンス](#42-ステータス更新シーケンス)

---

## 1. Auth

### 1.1 メール登録

**エンドポイント:** `POST /auth/check-email` → `POST /auth/register` → `POST /auth/verify-email`

```mermaid
sequenceDiagram
    participant C as Client (Next.js)
    participant S as Spring API
    participant DB as PostgreSQL
    participant M as Resend

    Note over C,M: ── ① メールアドレス重複チェック ──
    C->>S: POST /api/v1/auth/check-email<br/>{email}
    S->>DB: SELECT id FROM users<br/>WHERE email = ? AND deleted_at IS NULL
    alt メール登録済み
        DB-->>S: row found
        S-->>C: 409 EMAIL_ALREADY_REGISTERED
    else 利用可能
        DB-->>S: not found
        S-->>C: 200 {available: true}
    end

    Note over C,M: ── ② 新規登録 ──
    C->>S: POST /api/v1/auth/register<br/>{email, password, name}
    S->>S: BCrypt.hash(password, cost=12)
    S->>DB: INSERT INTO users<br/>(email, password_hash, display_name, role=BUYER)
    S->>DB: INSERT INTO email_verification_tokens<br/>(user_id, token, expires_at=+24h)
    S->>M: Send verification email (token リンク)
    M-->>S: 200 OK
    S-->>C: 201 {message: "確認メールを送信しました"}

    Note over C,M: ── ③ メール認証 ──
    C->>S: POST /api/v1/auth/verify-email<br/>{token}
    S->>DB: SELECT * FROM email_verification_tokens<br/>WHERE token = ? AND expires_at > NOW()
    alt トークン有効
        S->>DB: UPDATE users SET email_verified_at = NOW()
        S->>DB: DELETE FROM email_verification_tokens WHERE token = ?
        S-->>C: 200 {message: "メール認証が完了しました"}
    else トークン無効 / 期限切れ
        S-->>C: 400 TOKEN_INVALID
    end
```

---

### 1.2 ログイン & JWT リフレッシュ / ログアウト

**エンドポイント:** `POST /auth/login` | `POST /auth/refresh` | `POST /auth/logout`

```mermaid
sequenceDiagram
    participant C as Client (Next.js)
    participant S as Spring API
    participant DB as PostgreSQL

    Note over C,DB: ── ① ログイン ──
    C->>S: POST /api/v1/auth/login<br/>{email, password}
    S->>DB: SELECT * FROM users<br/>WHERE email = ? AND deleted_at IS NULL
    DB-->>S: user record (or not found)
    S->>S: BCrypt.verify(inputPassword, passwordHash)

    alt 認証成功 & メール認証済み
        S->>DB: INSERT INTO refresh_tokens<br/>(user_id, token, expires_at=+7d)
        S-->>C: 200 {<br/>  access_token (JWT, 15min),<br/>  refresh_token (7d),<br/>  user: {id, role, displayName}<br/>}
    else メール未認証
        S-->>C: 403 EMAIL_NOT_VERIFIED
    else パスワード不一致 / ユーザ不存在
        S-->>C: 401 INVALID_CREDENTIALS
    end

    Note over C,DB: ── ② アクセストークン リフレッシュ ──
    C->>S: POST /api/v1/auth/refresh<br/>{refresh_token}
    S->>DB: SELECT * FROM refresh_tokens<br/>WHERE token = ? AND expires_at > NOW()
    alt 有効なリフレッシュトークン
        S->>DB: DELETE FROM refresh_tokens WHERE token = ?  (ローテーション)
        S->>DB: INSERT INTO refresh_tokens (新トークン, expires_at=+7d)
        S-->>C: 200 {access_token (15min), refresh_token (7d)}
    else 無効 / 期限切れ
        S-->>C: 401 TOKEN_INVALID
    end

    Note over C,DB: ── ③ ログアウト ──
    C->>S: POST /api/v1/auth/logout<br/>{refresh_token}
    S->>DB: DELETE FROM refresh_tokens WHERE token = ?
    S-->>C: 204 No Content
```

> **JWT ペイロード:** `sub` (user_id), `role`, `iat`, `exp`  
> **署名:** HS256（Phase 2） / RS256（Phase 5+）

---

### 1.3 Google OAuth (NextAuth.js フロー)

**エンドポイント:** `POST /auth/google` （Spring が Google ID Token を検証）

```mermaid
sequenceDiagram
    participant C as Client (Next.js)
    participant N as NextAuth.js
    participant G as Google OAuth
    participant S as Spring API
    participant DB as PostgreSQL

    C->>N: signIn("google")
    N->>G: OAuth2 Authorization Request<br/>(scope: openid, email, profile)
    G-->>C: Google ログイン画面にリダイレクト
    C->>G: ユーザーが Google アカウントで同意
    G-->>N: Authorization Code

    N->>G: Code → Token 交換
    G-->>N: {id_token, access_token, profile}

    Note over N,S: NextAuth の callbacks.signIn から Spring に転送
    N->>S: POST /api/v1/auth/google<br/>{id_token}
    S->>G: Google 公開鍵で ID Token 署名検証<br/>+ audience / issuer 検証

    alt 検証成功
        S->>DB: SELECT * FROM users<br/>WHERE google_id = ? OR email = ?
        alt 既存ユーザー (google_id 未連携)
            S->>DB: UPDATE users SET google_id = ?
        else 新規ユーザー
            S->>DB: INSERT INTO users<br/>(google_id, email, display_name,<br/>role=BUYER, email_verified_at=NOW())
        end
        S->>DB: INSERT INTO refresh_tokens
        S-->>N: 200 {access_token (15min), refresh_token, user}
    else 検証失敗
        S-->>N: 401 GOOGLE_TOKEN_INVALID
        N-->>C: サインイン失敗エラー
    end

    N-->>C: NextAuth セッション Cookie 設定
    C->>C: access_token をメモリ or HTTP-only Cookie に保持
```

---

## 2. Payment

### 2.1 Stripe PaymentIntent → 注文確定

**エンドポイント:** `POST /orders/checkout` | Webhook `POST /webhooks/stripe`

```mermaid
sequenceDiagram
    participant C as Client (Next.js)
    participant S as Spring API
    participant DB as PostgreSQL
    participant ST as Stripe API
    participant W as Stripe Webhook
    participant WS as STOMP Broker

    Note over C,WS: ── ① チェックアウト (PaymentIntent 作成) ──
    C->>S: POST /api/v1/orders/checkout<br/>(Bearer: access_token)
    S->>DB: SELECT cart_items JOIN products<br/>WHERE user_id = ? AND cart.deleted_at IS NULL

    opt カートが空
        S-->>C: 400 CART_EMPTY
    end

    loop カート内商品ごとに在庫チェック
        S->>DB: SELECT stock_quantity FROM products<br/>WHERE id = ? FOR UPDATE
        opt stock_quantity < 注文数量
            S-->>C: 400 PRODUCT_OUT_OF_STOCK
        end
        opt seller が自分の商品を購入しようとしている
            S-->>C: 400 CANNOT_PURCHASE_OWN_PRODUCT
        end
    end

    S->>S: ショップ別に注文を分割 + 手数料計算<br/>(commission_rate FROM platform_configs)
    S->>ST: PaymentIntent.create({<br/>  amount: 合計金額 (JPY整数),<br/>  currency: "jpy",<br/>  metadata: {order_group_id}<br/>})
    ST-->>S: {payment_intent_id, client_secret}

    S->>DB: INSERT INTO orders[]<br/>(ショップ別, status=PENDING_PAYMENT,<br/>payment_intent_id, commission_rate_snapshot)
    S->>DB: INSERT INTO order_items[]<br/>(price_snapshot, product_name_snapshot)
    S->>DB: INSERT INTO payments<br/>(payment_intent_id, status=PENDING, amount)
    S-->>C: 201 {order_ids[], client_secret}

    Note over C,ST: ── ② クライアント側 Stripe.js 決済 ──
    C->>ST: stripe.confirmPayment({<br/>  client_secret,<br/>  payment_method: {card}<br/>})
    ST-->>C: {status: "succeeded" | "requires_action" | "failed"}

    Note over W,WS: ── ③ Stripe Webhook 処理 (非同期) ──
    ST->>W: POST /api/v1/webhooks/stripe<br/>Event: payment_intent.succeeded
    W->>W: Stripe-Signature ヘッダ検証<br/>(STRIPE_WEBHOOK_SECRET)
    W->>DB: SELECT status FROM payments<br/>WHERE payment_intent_id = ?
    opt 既に処理済み (status = COMPLETED)
        W-->>ST: 200 OK (冪等)
    end
    W->>DB: SELECT orders WHERE payment_intent_id = ?
    W->>DB: UPDATE payments SET status=COMPLETED
    W->>DB: UPDATE orders[] SET status=PAYMENT_CONFIRMED

    loop OrderItem ごと
        W->>DB: UPDATE products<br/>SET stock_quantity = stock_quantity - quantity
    end

    W->>DB: INSERT INTO audit_logs<br/>(action=ORDER_PAYMENT_CONFIRMED, correlation_id)

    loop 各 order ごと
        W->>WS: /topic/orders/{order_id}<br/>{status: PAYMENT_CONFIRMED}
        W->>DB: INSERT INTO notifications<br/>(user_id=buyer, type=ORDER_CONFIRMED)
        W->>DB: INSERT INTO notifications<br/>(user_id=seller, type=NEW_ORDER)
    end

    W-->>ST: 200 OK
```

---

### 2.2 キャンセル & 自動返金

**エンドポイント:** `POST /orders/{id}/cancel`

```mermaid
sequenceDiagram
    participant C as Client (Next.js)
    participant S as Spring API
    participant DB as PostgreSQL
    participant ST as Stripe API
    participant WS as STOMP Broker

    C->>S: POST /api/v1/orders/{id}/cancel<br/>(Bearer: access_token)
    S->>DB: SELECT * FROM orders WHERE id = ?
    DB-->>S: order record

    alt status が PAYMENT_CONFIRMED または PROCESSING
        S->>DB: SELECT payment_intent_id FROM payments<br/>WHERE order_id = ?
        S->>ST: Refund.create({<br/>  payment_intent: payment_intent_id,<br/>  amount: order.total_amount<br/>})
        alt 返金成功
            ST-->>S: {refund_id, status: "succeeded"}
            S->>DB: UPDATE orders SET status=CANCELLED
            S->>DB: UPDATE payments SET status=REFUNDED, refund_id=?

            loop OrderItem ごと (在庫戻し)
                S->>DB: UPDATE products<br/>SET stock_quantity = stock_quantity + quantity
            end

            S->>DB: INSERT INTO audit_logs<br/>(action=ORDER_CANCELLED)
            S->>WS: /topic/orders/{id} {status: CANCELLED}
            S->>DB: INSERT INTO notifications (buyer, seller)
            S-->>C: 200 {status: CANCELLED, refund_id}
        else 返金失敗
            ST-->>S: Stripe Error
            S-->>C: 500 REFUND_FAILED
        end
    else キャンセル不可ステータス
        Note right of S: SHIPPED / DELIVERED / COMPLETED<br/>はキャンセル不可
        S-->>C: 400 ORDER_NOT_CANCELLABLE
    end
```

---

## 3. Seller Application

**エンドポイント:** `POST /seller-applications` | `PATCH /admin/seller-applications/{id}/approve` | `PATCH /admin/seller-applications/{id}/reject`

```mermaid
sequenceDiagram
    participant B as Buyer
    participant S as Spring API
    participant DB as PostgreSQL
    participant A as Admin
    participant WS as STOMP Broker
    participant M as Resend

    Note over B,M: ── ① 出品者申請 ──
    B->>S: POST /api/v1/seller-applications<br/>(Bearer: access_token)<br/>{shopName, description, ...}
    S->>DB: SELECT * FROM seller_applications<br/>WHERE user_id = ? AND status IN (PENDING, APPROVED)

    alt 審査中の申請あり
        S-->>B: 409 SELLER_APPLICATION_PENDING
    else 承認済み (すでに SELLER)
        S-->>B: 409 ALREADY_APPROVED
    else 申請可能
        S->>DB: INSERT INTO seller_applications<br/>(user_id, status=PENDING, submitted_at=NOW())
        S->>DB: INSERT INTO audit_logs<br/>(action=SELLER_APPLICATION_SUBMITTED)
        S-->>B: 201 {application_id, status: PENDING}
    end

    Note over A,M: ── ② 管理者: 申請一覧確認 ──
    A->>S: GET /api/v1/admin/seller-applications<br/>?status=PENDING&page=0&size=20
    S->>DB: SELECT * FROM seller_applications<br/>WHERE status=PENDING ORDER BY submitted_at
    S-->>A: 200 PageResponse<SellerApplicationSummary>

    Note over A,M: ── ③a 承認 ──
    A->>S: PATCH /api/v1/admin/seller-applications/{id}/approve<br/>(Bearer: admin_token)
    S->>DB: SELECT * FROM seller_applications<br/>WHERE id = ?
    opt status が PENDING でない
        S-->>A: 409 NOT_REVIEWABLE
    end
    S->>DB: UPDATE seller_applications<br/>SET status=APPROVED, reviewed_by=admin_id, reviewed_at=NOW()
    S->>DB: UPDATE users SET role=SELLER WHERE id=applicant_user_id
    S->>DB: INSERT INTO audit_logs (action=SELLER_APPLICATION_APPROVED)
    S->>DB: INSERT INTO notifications<br/>(user_id=applicant, type=SELLER_APPROVED)
    S->>WS: /topic/notifications/{applicant_user_id}<br/>{type: SELLER_APPROVED}
    S->>M: Send approval email
    S-->>A: 200 {status: APPROVED}

    Note over A,M: ── ③b 拒否 ──
    A->>S: PATCH /api/v1/admin/seller-applications/{id}/reject<br/>{reason}
    S->>DB: SELECT * FROM seller_applications WHERE id = ?
    opt status が PENDING でない
        S-->>A: 409 NOT_REVIEWABLE
    end
    S->>DB: UPDATE seller_applications<br/>SET status=REJECTED, rejection_reason=?, reviewed_at=NOW()
    Note right of DB: users.role は BUYER のまま変更なし
    S->>DB: INSERT INTO audit_logs (action=SELLER_APPLICATION_REJECTED)
    S->>DB: INSERT INTO notifications<br/>(user_id=applicant, type=SELLER_REJECTED)
    S->>WS: /topic/notifications/{applicant_user_id}<br/>{type: SELLER_REJECTED, reason}
    S->>M: Send rejection email (reason 含む)
    S-->>A: 200 {status: REJECTED}
```

---

## 4. Order Lifecycle

### 4.1 状態遷移図

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT : checkout完了

    PENDING_PAYMENT --> PAYMENT_CONFIRMED : Stripe Webhook
    PENDING_PAYMENT --> CANCELLED : 決済タイムアウト / 失敗

    PAYMENT_CONFIRMED --> PROCESSING : Seller が処理開始
    PAYMENT_CONFIRMED --> CANCELLED : キャンセル → Stripe自動返金

    PROCESSING --> SHIPPED : Seller が発送登録
    PROCESSING --> CANCELLED : キャンセル → Stripe自動返金

    SHIPPED --> DELIVERED : Buyer が受取確認

    DELIVERED --> COMPLETED : 7日後バッチ(自動)

    COMPLETED --> [*]
    CANCELLED --> [*]
```

**ステータス変更権限まとめ:**

| 遷移 | トリガー | 実行者 |
|---|---|---|
| `PENDING_PAYMENT` → `PAYMENT_CONFIRMED` | Stripe Webhook | System (自動) |
| `PAYMENT_CONFIRMED` → `PROCESSING` | PATCH /orders/{id}/status | Seller |
| `PROCESSING` → `SHIPPED` | PATCH /orders/{id}/status | Seller |
| `SHIPPED` → `DELIVERED` | PATCH /orders/{id}/status | Buyer |
| `DELIVERED` → `COMPLETED` | バッチジョブ (7日後) | System (自動) |
| `PAYMENT_CONFIRMED` / `PROCESSING` → `CANCELLED` | POST /orders/{id}/cancel | Buyer or Seller |

---

### 4.2 ステータス更新シーケンス

```mermaid
sequenceDiagram
    participant B as Buyer
    participant Se as Seller
    participant S as Spring API
    participant DB as PostgreSQL
    participant WS as STOMP Broker
    participant Ba as Batch Job

    Note over Se,WS: ── PROCESSING (Seller が処理開始) ──
    Se->>S: PATCH /api/v1/orders/{id}/status<br/>{status: PROCESSING}<br/>(Bearer: seller_token)
    S->>DB: SELECT * FROM orders WHERE id = ?<br/>AND shop.seller_id = current_user
    opt status != PAYMENT_CONFIRMED
        S-->>Se: 400 ORDER_STATUS_TRANSITION_INVALID
    end
    S->>DB: UPDATE orders SET status=PROCESSING, updated_at=NOW()
    S->>DB: INSERT INTO audit_logs (action=ORDER_STATUS_UPDATED)
    S->>WS: /topic/orders/{id} {status: PROCESSING}
    S->>DB: INSERT INTO notifications (user_id=buyer, type=ORDER_PROCESSING)
    S-->>Se: 200 {status: PROCESSING}

    Note over Se,WS: ── SHIPPED (Seller が発送登録) ──
    Se->>S: PATCH /api/v1/orders/{id}/status<br/>{status: SHIPPED, trackingNumber: "xxxx"}<br/>(Bearer: seller_token)
    S->>DB: SELECT * FROM orders WHERE id = ?<br/>AND shop.seller_id = current_user
    opt status != PROCESSING
        S-->>Se: 400 ORDER_STATUS_TRANSITION_INVALID
    end
    S->>DB: UPDATE orders<br/>SET status=SHIPPED, tracking_number=?
    S->>DB: INSERT INTO audit_logs (action=ORDER_STATUS_UPDATED)
    S->>WS: /topic/orders/{id} {status: SHIPPED, trackingNumber}
    S->>DB: INSERT INTO notifications (user_id=buyer, type=ORDER_SHIPPED)
    S-->>Se: 200 {status: SHIPPED}

    Note over B,WS: ── DELIVERED (Buyer が受取確認) ──
    B->>S: PATCH /api/v1/orders/{id}/status<br/>{status: DELIVERED}<br/>(Bearer: buyer_token)
    S->>DB: SELECT * FROM orders WHERE id = ?<br/>AND user_id = current_user
    opt status != SHIPPED
        S-->>B: 400 ORDER_STATUS_TRANSITION_INVALID
    end
    S->>DB: UPDATE orders SET status=DELIVERED
    S->>DB: INSERT INTO audit_logs (action=ORDER_STATUS_UPDATED)
    S->>WS: /topic/orders/{id} {status: DELIVERED}
    S->>DB: INSERT INTO notifications (user_id=seller, type=ORDER_DELIVERED)
    S-->>B: 200 {status: DELIVERED}

    Note over Ba,WS: ── COMPLETED (7日後バッチ, 自動) ──
    Ba->>DB: SELECT id FROM orders<br/>WHERE status=DELIVERED<br/>AND updated_at < NOW() - INTERVAL '7 days'
    loop 対象注文ごと
        Ba->>DB: UPDATE orders SET status=COMPLETED
        Ba->>DB: INSERT INTO audit_logs (action=ORDER_COMPLETED)
        Ba->>WS: /topic/orders/{id} {status: COMPLETED}
        Ba->>DB: INSERT INTO notifications (buyer, seller)
    end
```

---

*最終更新: 2026-05-24*
