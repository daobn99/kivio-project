# エラーコード一覧
# マルチベンダー型マーケットプレイス「Kivio」

**作成日：** 2026年5月24日  
**作成者：** Dao Nguyen  
**バージョン：** 1.0  
**参照元：** [REQUIREMENTS.md § 8.6](./REQUIREMENTS.md)、[API_DESIGN.md](./API_DESIGN.md)

---

## 1. エラーコード体系

### 1.1 命名規則

- フォーマット: `UPPER_SNAKE_CASE`（例: `PRODUCT_OUT_OF_STOCK`）
- 構成: `{エンティティ}_{動詞/状態}` または汎用的な単語

### 1.2 エラーレスポンス形式（RFC 9457 Problem Details）

Spring Boot 3.x の `ProblemDetail` をベースとする。  
`Content-Type: application/problem+json`

```json
{
  "type": "https://kivio.example.com/problems/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "ID '550e8400' の商品は存在しないか削除されています",
  "instance": "/api/v1/products/550e8400"
}
```

バリデーションエラー時は `errors` フィールドを追加拡張する:

```json
{
  "type": "https://kivio.example.com/problems/validation-failed",
  "title": "Validation Failed",
  "status": 422,
  "code": "VALIDATION_FAILED",
  "detail": "リクエストの入力値に不正があります",
  "instance": "/api/v1/products",
  "errors": [
    {
      "field": "price",
      "message": "価格は1円以上である必要があります",
      "rejectedValue": -100
    }
  ]
}
```

| フィールド | 説明 |
|---|---|
| `type` | `https://kivio.example.com/problems/{error-code-kebab-case}` |
| `title` | エラー種別の短い英語名（固定値） |
| `status` | HTTPステータスコード（数値） |
| `code` | エラーコード（`UPPER_SNAKE_CASE`、フロントエンドでの分岐に使用） |
| `detail` | 具体的なエラー説明（日本語可） |
| `instance` | エラーが発生したリクエストパス |
| `errors` | バリデーションエラー時のフィールド別詳細（拡張フィールド） |

> **`code` フィールドについて:** RFC 9457 の拡張フィールドとして追加。`type` URI を parse するより `code` で switch するほうがクライアント実装が簡潔になる。Spring の `ProblemDetail.setProperty("code", errorCode)` で設定する。

> **`rejectedValue` のマスキング:** `password` / `token` / `secret` / `credential` を含むフィールド名では `rejectedValue` を出力しない。`GlobalExceptionHandler` でフィールド名を検査し、該当する場合は `rejectedValue` を省略すること。

---

## 2. エラーコード一覧

### 2.1 汎用エラー

| エラーコード | HTTPステータス | title | 発生条件 |
|---|---|---|---|
| `VALIDATION_FAILED` | 422 | Validation Failed | Bean Validation失敗（必須項目・文字数・範囲等） |
| `RESOURCE_NOT_FOUND` | 404 | Resource Not Found | 対象リソースが存在しない / soft delete済み |
| `ACCESS_DENIED` | 403 | Access Denied | 認証済みだが権限不足（他人のリソース・ロール不足） |
| `UNAUTHORIZED` | 401 | Unauthorized | 認証が必要なAPIへの未認証アクセス |
| `TOKEN_EXPIRED` | 401 | Token Expired | JWTアクセストークンが期限切れ（15分） |
| `TOKEN_INVALID` | 401 | Token Invalid | JWT署名不正・形式エラー |
| `RATE_LIMIT_EXCEEDED` | 429 | Rate Limit Exceeded | レート制限超過（認証系10req/min、API全般100req/min） |
| `INTERNAL_SERVER_ERROR` | 500 | Internal Server Error | サーバー内部エラー（詳細は絶対に露出しない） |
| `DUPLICATE_ENTRY` | 409 | Duplicate Entry | 汎用重複登録（複合ユニーク制約違反等） |

---

### 2.2 認証・ユーザー（Auth / User）

| エラーコード | HTTPステータス | title | 発生条件 | 該当エンドポイント |
|---|---|---|---|---|
| `EMAIL_ALREADY_REGISTERED` | 409 | Email Already Registered | 登録済みメールアドレスで新規登録を試みた | `POST /auth/check-email`, `POST /auth/register` |
| `INVALID_CREDENTIALS` | 401 | Invalid Credentials | メールアドレスまたはパスワードが不一致 | `POST /auth/login` |
| `EMAIL_NOT_VERIFIED` | 403 | Email Not Verified | メール未確認ユーザーのログイン試行 | `POST /auth/login` |
| `GOOGLE_TOKEN_INVALID` | 401 | Google Token Invalid | Google ID Tokenの検証失敗 | `POST /auth/google` |
| `REFRESH_TOKEN_INVALID` | 401 | Refresh Token Invalid | Refresh Tokenが無効・失効・期限切れ | `POST /auth/refresh` |
| `USER_DEACTIVATED` | 403 | User Deactivated | 管理者によって無効化されたアカウントのログイン | `POST /auth/login` |
| `PASSWORD_CHANGE_FAILED` | 400 | Password Change Failed | 現在のパスワードが不一致 | `PATCH /users/me/password` |

---

### 2.3 セラー申請（SellerApplication）

| エラーコード | HTTPステータス | title | 発生条件 | 該当エンドポイント |
|---|---|---|---|---|
| `SELLER_APPLICATION_PENDING` | 409 | Seller Application Pending | 審査中の申請が既に存在する（再申請不可） | `POST /seller-applications` |
| `SELLER_APPLICATION_ALREADY_APPROVED` | 409 | Seller Application Already Approved | 既に `ROLE_SELLER` を持つユーザーの申請 | `POST /seller-applications` |
| `SELLER_APPLICATION_NOT_REVIEWABLE` | 409 | Seller Application Not Reviewable | 対象申請が `PENDING` 以外のステータス | `POST /admin/seller-applications/{id}/approve`, `.../reject` |

---

### 2.4 ショップ（Shop）

| エラーコード | HTTPステータス | title | 発生条件 | 該当エンドポイント |
|---|---|---|---|---|
| `SHOP_NAME_ALREADY_TAKEN` | 409 | Shop Name Already Taken | 有効なショップ間でショップ名が重複 | `PATCH /shops/me` |
| `SHOP_NOT_FOUND` | 404 | Shop Not Found | ショップが存在しない / 削除済み | 各ショップ系エンドポイント |

---

### 2.5 商品（Product）

| エラーコード | HTTPステータス | title | 発生条件 | 該当エンドポイント |
|---|---|---|---|---|
| `PRODUCT_NOT_ACTIVE` | 409 | Product Not Active | `ACTIVE` 以外の商品をカートに追加 / チェックアウトしようとした | `POST /cart/items`, `POST /orders/checkout` |
| `PRODUCT_OUT_OF_STOCK` | 409 | Product Out of Stock | **在庫数が0**の商品を追加しようとした（在庫超過は `CART_ITEM_QUANTITY_EXCEEDED` を使用） | `POST /cart/items`, `POST /orders/checkout` |
| `PRODUCT_IMAGE_LIMIT_EXCEEDED` | 409 | Product Image Limit Exceeded | 商品画像が既に5枚あるのに追加しようとした | `POST /products/{id}/images` |
| `PRODUCT_NOT_OWNED` | 403 | Product Not Owned | 他セラーの商品を更新・削除しようとした | `PATCH /products/{id}`, `DELETE /products/{id}`, `POST /products/{id}/images`, `DELETE /products/{id}/images/{imageId}` |

---

### 2.6 カート（Cart）

| エラーコード | HTTPステータス | title | 発生条件 | 該当エンドポイント |
|---|---|---|---|---|
| `CART_ITEM_QUANTITY_EXCEEDED` | 409 | Cart Item Quantity Exceeded | **在庫数 > 0** だが指定数量が在庫数を超過（カート追加・数量変更の両方） | `POST /cart/items`, `PATCH /cart/items/{id}` |
| `CANNOT_PURCHASE_OWN_PRODUCT` | 409 | Cannot Purchase Own Product | セラーが自分のショップの商品をカートに追加 | `POST /cart/items` |

---

### 2.7 注文・決済（Order / Payment）

| エラーコード | HTTPステータス | title | 発生条件 | 該当エンドポイント |
|---|---|---|---|---|
| `CART_EMPTY` | 409 | Cart Empty | カートが空の状態でチェックアウトを試みた | `POST /orders/checkout` |
| `ORDER_NOT_CANCELLABLE` | 409 | Order Not Cancellable | キャンセル可能ステータス（`PAYMENT_CONFIRMED` / `PROCESSING`）以外の注文をキャンセルしようとした | `POST /orders/{id}/cancel` |
| `ORDER_STATUS_TRANSITION_INVALID` | 409 | Order Status Transition Invalid | 許可されていないステータス遷移（例: `PENDING_PAYMENT` → `SHIPPED`） | `PATCH /orders/{id}/status` |
| `PAYMENT_FAILED` | 422 | Payment Failed | Stripe決済処理に失敗 | `POST /orders/checkout` |
| `REFUND_FAILED` | 500 | Refund Failed | Stripe返金処理に失敗（リトライが必要） | `POST /orders/{id}/cancel` |
| `ORDER_NOT_FOUND` | 404 | Order Not Found | 注文が存在しない / アクセス権なし | 各注文系エンドポイント |

---

### 2.8 レビュー（Review）

| エラーコード | HTTPステータス | title | 発生条件 | 該当エンドポイント |
|---|---|---|---|---|
| `REVIEW_ALREADY_SUBMITTED` | 409 | Review Already Submitted | 同一注文明細へのレビュー重複投稿 | `POST /order-items/{id}/review` |
| `REVIEW_NOT_ELIGIBLE` | 403 | Review Not Eligible | 注文ステータスが `COMPLETED` でない / 自分の注文でない | `POST /order-items/{id}/review` |

---

### 2.9 チャット・通知（Chat / Notification）

| エラーコード | HTTPステータス | title | 発生条件 | 該当エンドポイント |
|---|---|---|---|---|
| `CHAT_ROOM_NOT_FOUND` | 404 | Chat Room Not Found | チャットルームが存在しない / アクセス権なし | `GET /chat-rooms/{id}` |
| `NOTIFICATION_NOT_FOUND` | 404 | Notification Not Found | 通知が存在しない / 他人の通知 | `PATCH /notifications/{id}/read` |

---

## 3. ステータスコードと用途の対応

| HTTPステータス | 用途 |
|---|---|
| `200 OK` | GET / PATCH（レスポンスボディあり） |
| `201 Created` | POST でリソース作成成功（`Location` ヘッダー付き） |
| `204 No Content` | DELETE / 副作用のみのPOST（レスポンスボディなし） |
| `400 Bad Request` | リクエスト形式が不正（JSONパースエラー・パスワード不一致等） |
| `401 Unauthorized` | 未認証 / トークン無効・期限切れ |
| `403 Forbidden` | 認証済みだが権限不足 |
| `404 Not Found` | リソースが存在しない |
| `409 Conflict` | 状態競合（在庫切れ・重複・キャンセル不可等） |
| `422 Unprocessable Entity` | Bean Validation失敗 / Stripe決済失敗 |
| `429 Too Many Requests` | レート制限超過 |
| `500 Internal Server Error` | サーバー内部エラー |

---

## 4. `type` URI とエラーコードのマッピング

`type` フィールドのパス部分はエラーコードを `kebab-case` に変換したもの。

| エラーコード（UPPER_SNAKE_CASE） | type URI パス |
|---|---|
| `VALIDATION_FAILED` | `/problems/validation-failed` |
| `RESOURCE_NOT_FOUND` | `/problems/resource-not-found` |
| `EMAIL_ALREADY_REGISTERED` | `/problems/email-already-registered` |
| `PRODUCT_OUT_OF_STOCK` | `/problems/product-out-of-stock` |
| `ORDER_NOT_CANCELLABLE` | `/problems/order-not-cancellable` |
| `SELLER_APPLICATION_PENDING` | `/problems/seller-application-pending` |
| `PAYMENT_FAILED` | `/problems/payment-failed` |
| （その他も同様） | |

**完全な `type` URI 例:**  
`https://kivio.example.com/problems/product-out-of-stock`

---

**以上**

*エラーコードの追加・変更はこのドキュメントと `API_DESIGN.md` の両方を更新してください。Spring の `GlobalExceptionHandler`（`@ControllerAdvice`）でコードと `ProblemDetail` の変換を一元管理します。*
