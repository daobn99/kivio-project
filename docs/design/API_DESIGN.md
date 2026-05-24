# API設計書
# マルチベンダー型マーケットプレイス「Kivio」

**作成日：** 2026年5月24日  
**作成者：** Dao Nguyen  
**バージョン：** 1.0  
**ステータス：** 確定  
**参照元：** [REQUIREMENTS.md § 8](./REQUIREMENTS.md)、[DB_DESIGN.md](./DB_DESIGN.md)、[ERROR_CODES.md](./ERROR_CODES.md)

---

## 目次

1. [共通仕様](#1-共通仕様)
2. [認証 (Auth)](#2-認証-auth)
3. [ユーザー・住所 (User / Address)](#3-ユーザー住所-user--address)
4. [セラー申請 (SellerApplication)](#4-セラー申請-sellerapplication)
5. [ショップ・配送ポリシー (Shop)](#5-ショップ配送ポリシー-shop)
6. [カテゴリー (Category)](#6-カテゴリー-category)
7. [商品・画像 (Product)](#7-商品画像-product)
8. [カート (Cart)](#8-カート-cart)
9. [注文・決済・Webhook (Order / Payment)](#9-注文決済webhook-order--payment)
10. [レビュー (Review)](#10-レビュー-review)
11. [チャット (Chat)](#11-チャット-chat)
12. [通知 (Notification)](#12-通知-notification)
13. [お気に入り (Wishlist)](#13-お気に入り-wishlist)
14. [セラーダッシュボード (Seller Dashboard)](#14-セラーダッシュボード-seller-dashboard)
15. [管理者 (Admin)](#15-管理者-admin)
16. [WebSocket / STOMP](#16-websocket--stomp)

---

## 1. 共通仕様

### 1.1 基本情報

| 項目 | 値 |
|---|---|
| ベースURL | `https://{host}/api/v1` |
| プロトコル | HTTPS / REST |
| Content-Type | `application/json`（リクエスト） |
| 日時フォーマット | ISO 8601 UTC: `2026-05-24T10:00:00Z` |
| 金額フォーマット | 整数（円単位）: `1500` = ¥1,500 |
| JSONフィールド命名 | `camelCase` |
| APIドキュメント | `/swagger-ui.html`（Swagger UI） |

### 1.2 認証

全APIエンドポイントは `Authorization: Bearer <accessToken>` ヘッダーで認証する（公開エンドポイントを除く）。

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### 1.3 ページネーション

リスト系エンドポイントはクエリパラメータ `page`（0始まり）・`size`（デフォルト20、最大100）を受け取り `PageResponse<T>` を返す。

**クエリパラメータ:**

| パラメータ | 型 | デフォルト | 説明 |
|---|---|---|---|
| `page` | integer | `0` | ページ番号（0始まり） |
| `size` | integer | `20` | 1ページあたり件数（最大100） |

**レスポンス形式:**

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false
}
```

### 1.4 レート制限

| 対象 | 制限 |
|---|---|
| 認証系 (`/auth/*`) | 10 req / 分 / IP |
| API全般（認証済み） | 100 req / 分 / ユーザー |
| 公開API（未認証） | 30 req / 分 / IP |

超過時: `429 Too Many Requests` + `Retry-After` ヘッダー

### 1.5 エラーレスポンス

RFC 9457 ProblemDetail 形式。詳細は [ERROR_CODES.md](./ERROR_CODES.md) 参照。

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "https://kivio.example.com/problems/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "ID '550e8400' の商品は存在しません",
  "instance": "/api/v1/products/550e8400"
}
```

---

## 2. 認証 (Auth)

### POST /api/v1/auth/check-email

**概要:** メールアドレス重複チェック（登録ステップ1）  
**認証:** 不要  
**権限:** 公開

#### リクエストボディ

```json
{
  "email": "alice@example.com"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `email` | string | ◯ | メール形式、255文字以内 |

#### レスポンス（200 OK）

```json
{
  "available": true
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `EMAIL_ALREADY_REGISTERED` | 409 | そのメールアドレスが既に登録済み |
| `VALIDATION_FAILED` | 422 | メール形式が不正 |

---

### POST /api/v1/auth/register

**概要:** 会員登録（登録ステップ2）  
**認証:** 不要  
**権限:** 公開

#### リクエストボディ

```json
{
  "email": "alice@example.com",
  "password": "password123",
  "passwordConfirm": "password123"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `email` | string | ◯ | メール形式、255文字以内 |
| `password` | string | ◯ | 8文字以上 |
| `passwordConfirm` | string | ◯ | `password` と一致すること |

#### レスポンス（201 Created）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "alice@example.com",
  "role": "ROLE_BUYER",
  "emailVerified": false,
  "createdAt": "2026-05-24T10:00:00Z"
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `EMAIL_ALREADY_REGISTERED` | 409 | メールアドレスが既に登録済み |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### POST /api/v1/auth/login

**概要:** メールアドレス・パスワードでログイン  
**認証:** 不要  
**権限:** 公開

#### リクエストボディ

```json
{
  "email": "alice@example.com",
  "password": "password123"
}
```

#### レスポンス（200 OK）

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

| フィールド | 説明 |
|---|---|
| `accessToken` | JWT Access Token（有効期限15分） |
| `refreshToken` | Refresh Token（有効期限7日、SHA-256ハッシュ化後DBに保存） |
| `expiresIn` | Access Token 有効期限（秒） |

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | メールアドレスまたはパスワードが不一致 |
| `EMAIL_NOT_VERIFIED` | 403 | メール未確認ユーザー |
| `USER_DEACTIVATED` | 403 | アカウントが無効化済み |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### POST /api/v1/auth/google

**概要:** Google ID Token 検証 → 自前 JWT 発行  
**認証:** 不要  
**権限:** 公開

#### リクエストボディ

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

#### レスポンス（200 OK）

`POST /auth/login` と同形式。  
同メールで既存アカウントがある場合はアカウント統合済みとして扱う。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `GOOGLE_TOKEN_INVALID` | 401 | Google ID Token の検証失敗 |
| `USER_DEACTIVATED` | 403 | アカウントが無効化済み |

---

### POST /api/v1/auth/refresh

**概要:** Refresh Token を使って Access Token を再発行  
**認証:** 不要  
**権限:** 公開

#### リクエストボディ

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

#### レスポンス（200 OK）

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `REFRESH_TOKEN_INVALID` | 401 | Refresh Token が無効・失効・期限切れ |

---

### POST /api/v1/auth/logout

**概要:** ログアウト（Refresh Token を無効化）  
**認証:** 必須  
**権限:** 全ロール

#### リクエストボディ

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

#### レスポンス（204 No Content）

---

## 3. ユーザー・住所 (User / Address)

### GET /api/v1/users/me

**概要:** 自分のプロフィール取得  
**認証:** 必須  
**権限:** 全ロール

#### レスポンス（200 OK）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "alice@example.com",
  "displayName": "Alice",
  "avatarUrl": "https://res.cloudinary.com/kivio/...",
  "role": "ROLE_BUYER",
  "status": "ACTIVE",
  "emailVerified": true,
  "createdAt": "2026-05-24T10:00:00Z"
}
```

---

### PATCH /api/v1/users/me

**概要:** プロフィール更新（表示名・アバター画像URL）  
**認証:** 必須  
**権限:** 全ロール

#### リクエストボディ（全フィールド省略可）

```json
{
  "displayName": "Alice Smith",
  "avatarUrl": "https://res.cloudinary.com/kivio/..."
}
```

| フィールド | 型 | 制約 |
|---|---|---|
| `displayName` | string | 100文字以内 |
| `avatarUrl` | string | URL形式 |

#### レスポンス（200 OK）

`GET /users/me` と同形式。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### PATCH /api/v1/users/me/password

**概要:** パスワード変更  
**認証:** 必須  
**権限:** 全ロール（Googleログインユーザーは不可）

#### リクエストボディ

```json
{
  "currentPassword": "oldpassword",
  "newPassword": "newpassword123"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `currentPassword` | string | ◯ | 現在のパスワード |
| `newPassword` | string | ◯ | 8文字以上 |

#### レスポンス（204 No Content）

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `PASSWORD_CHANGE_FAILED` | 400 | 現在のパスワードが不一致 |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### DELETE /api/v1/users/me

**概要:** 退会申請（`deleted_at` を設定する論理削除）  
**認証:** 必須  
**権限:** 全ロール

#### レスポンス（204 No Content）

---

### GET /api/v1/users/me/addresses

**概要:** 配送先住所一覧  
**認証:** 必須  
**権限:** 全ロール

#### レスポンス（200 OK）

```json
[
  {
    "id": "...",
    "recipientName": "山田 太郎",
    "postalCode": "150-0001",
    "prefecture": "東京都",
    "city": "渋谷区",
    "addressLine": "神南1-1-1 サンプルビル101",
    "phoneNumber": "090-1234-5678",
    "isDefault": true,
    "createdAt": "2026-05-24T10:00:00Z"
  }
]
```

---

### POST /api/v1/users/me/addresses

**概要:** 配送先住所追加  
**認証:** 必須  
**権限:** 全ロール

#### リクエストボディ

```json
{
  "recipientName": "山田 太郎",
  "postalCode": "150-0001",
  "prefecture": "東京都",
  "city": "渋谷区",
  "addressLine": "神南1-1-1 サンプルビル101",
  "phoneNumber": "090-1234-5678",
  "isDefault": false
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `recipientName` | string | ◯ | 100文字以内 |
| `postalCode` | string | ◯ | 例: `150-0001` |
| `prefecture` | string | ◯ | 都道府県名 |
| `city` | string | ◯ | 市区町村 |
| `addressLine` | string | ◯ | 番地・建物名 |
| `phoneNumber` | string | ◯ | 電話番号 |
| `isDefault` | boolean | | デフォルト住所に設定するか（true の場合、他住所の isDefault を false に更新） |

#### レスポンス（201 Created）

`Location: /api/v1/users/me/addresses/{id}` + 作成した住所オブジェクト

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### PATCH /api/v1/users/me/addresses/{id}

**概要:** 配送先住所更新  
**認証:** 必須  
**権限:** 全ロール（自分の住所のみ）

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 住所ID |

#### リクエストボディ（全フィールド省略可）

`POST /users/me/addresses` のボディと同フィールド。

#### レスポンス（200 OK）

更新後の住所オブジェクト。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 住所が存在しない |
| `ACCESS_DENIED` | 403 | 他人の住所 |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### DELETE /api/v1/users/me/addresses/{id}

**概要:** 配送先住所削除（物理削除）  
**認証:** 必須  
**権限:** 全ロール（自分の住所のみ）

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 住所ID |

#### レスポンス（204 No Content）

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 住所が存在しない |
| `ACCESS_DENIED` | 403 | 他人の住所 |

---

## 4. セラー申請 (SellerApplication)

### POST /api/v1/seller-applications

**概要:** セラー申請送信  
**認証:** 必須  
**権限:** `ROLE_BUYER`

#### リクエストボディ

```json
{
  "reason": "ハンドメイドアクセサリーを販売したいと思い申請します。"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `reason` | string | ◯ | 申請理由（1000文字以内） |

#### レスポンス（201 Created）

```json
{
  "id": "...",
  "applicantId": "...",
  "reason": "ハンドメイドアクセサリーを販売したいと思い申請します。",
  "status": "PENDING",
  "createdAt": "2026-05-24T10:00:00Z"
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `SELLER_APPLICATION_PENDING` | 409 | 審査中の申請が既に存在する |
| `SELLER_APPLICATION_ALREADY_APPROVED` | 409 | 既に `ROLE_SELLER` を持つ |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### GET /api/v1/seller-applications/me

**概要:** 自分の最新セラー申請状況確認  
**認証:** 必須  
**権限:** 全ロール

#### レスポンス（200 OK）

```json
{
  "id": "...",
  "status": "PENDING",
  "reason": "...",
  "reviewComment": null,
  "reviewedAt": null,
  "createdAt": "2026-05-24T10:00:00Z"
}
```

`status`: `PENDING` / `APPROVED` / `REJECTED`

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 申請が存在しない |

---

## 5. ショップ・配送ポリシー (Shop)

### GET /api/v1/shops

**概要:** 公開ショップ一覧（`status=ACTIVE`・`deleted_at IS NULL` のみ）  
**認証:** 不要  
**権限:** 公開

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `q` | string | ショップ名のキーワード検索 |
| `page` | integer | ページ番号（デフォルト: 0） |
| `size` | integer | 件数（デフォルト: 20） |

#### レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": "...",
      "name": "Alice's Atelier",
      "description": "ハンドメイドアクセサリーのお店",
      "logoUrl": "https://res.cloudinary.com/kivio/...",
      "productCount": 12,
      "createdAt": "2026-05-24T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

### GET /api/v1/shops/{id}

**概要:** ショップ詳細取得  
**認証:** 不要  
**権限:** 公開

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | ショップID |

#### レスポンス（200 OK）

```json
{
  "id": "...",
  "name": "Alice's Atelier",
  "description": "ハンドメイドアクセサリーのお店",
  "logoUrl": "https://res.cloudinary.com/kivio/...",
  "status": "ACTIVE",
  "shippingPolicy": {
    "shippingType": "CONDITIONAL_FREE",
    "fixedFee": null,
    "freeThreshold": 5000
  },
  "productCount": 12,
  "createdAt": "2026-05-24T10:00:00Z"
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `SHOP_NOT_FOUND` | 404 | ショップが存在しない / 削除済み |

---

### GET /api/v1/shops/me

**概要:** 自分のショップ取得  
**認証:** 必須  
**権限:** `ROLE_SELLER`

> **実装注意:** Spring MVC では `/shops/{id}` より先に `/shops/me` を定義すること。定義順が逆だと `me` が `{id}` の PathVariable に解釈される。

#### レスポンス（200 OK）

`GET /shops/{id}` と同形式。

---

### PATCH /api/v1/shops/me

**概要:** ショップ情報更新  
**認証:** 必須  
**権限:** `ROLE_SELLER`

#### リクエストボディ（全フィールド省略可）

```json
{
  "name": "New Shop Name",
  "description": "新しい紹介文",
  "logoUrl": "https://res.cloudinary.com/kivio/..."
}
```

| フィールド | 型 | 制約 |
|---|---|---|
| `name` | string | 100文字以内、プラットフォーム全体で一意（有効ショップ間） |
| `description` | string | テキスト |
| `logoUrl` | string | URL形式 |

#### レスポンス（200 OK）

更新後のショップオブジェクト。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `SHOP_NAME_ALREADY_TAKEN` | 409 | ショップ名が既に使用中 |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### PATCH /api/v1/shops/me/shipping-policy

**概要:** 配送ポリシー更新  
**認証:** 必須  
**権限:** `ROLE_SELLER`

#### リクエストボディ

```json
{
  "shippingType": "CONDITIONAL_FREE",
  "fixedFee": null,
  "freeThreshold": 5000
}
```

| フィールド | 型 | 必須 | 条件 |
|---|---|---|---|
| `shippingType` | string | ◯ | `FREE` / `FIXED` / `CONDITIONAL_FREE` |
| `fixedFee` | integer | `FIXED` 時 ◯ | 送料金額（円、正の整数） |
| `freeThreshold` | integer | `CONDITIONAL_FREE` 時 ◯ | 送料無料になる注文金額閾値（円） |

#### レスポンス（200 OK）

更新後の配送ポリシーオブジェクト。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `VALIDATION_FAILED` | 422 | `shippingType` と `fixedFee`/`freeThreshold` の組み合わせ不正 |

---

## 6. カテゴリー (Category)

### GET /api/v1/categories

**概要:** カテゴリー一覧（2階層ツリー構造）  
**認証:** 不要  
**権限:** 公開

#### レスポンス（200 OK）

```json
[
  {
    "id": "...",
    "name": "ハンドメイド",
    "slug": "handmade",
    "displayOrder": 1,
    "children": [
      {
        "id": "...",
        "name": "アクセサリー",
        "slug": "accessories",
        "displayOrder": 1,
        "children": []
      }
    ]
  }
]
```

---

## 7. 商品・画像 (Product)

### GET /api/v1/products

**概要:** 商品一覧（検索・フィルター）。`status=ACTIVE` のみ返す  
**認証:** 不要  
**権限:** 公開

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `q` | string | キーワード全文検索（商品名・説明文） |
| `categoryId` | UUID | カテゴリーID（子カテゴリーIDを指定） |
| `shopId` | UUID | ショップでフィルター |
| `minPrice` | integer | 最低価格（円） |
| `maxPrice` | integer | 最高価格（円） |
| `inStock` | boolean | 在庫ありのみ表示（`true`） |
| `sort` | string | ソート: `createdAt,desc`（新着）/ `price,asc`（価格昇順）/ `price,desc`（価格降順）/ `reviewCount,desc`（人気順） |
| `page` | integer | ページ番号（デフォルト: 0） |
| `size` | integer | 件数（デフォルト: 20） |

#### レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": "...",
      "name": "ハンドメイドレザーウォレット",
      "price": 8500,
      "stockQuantity": 3,
      "status": "ACTIVE",
      "thumbnailUrl": "https://res.cloudinary.com/kivio/...",
      "shop": {
        "id": "...",
        "name": "Alice's Atelier"
      },
      "category": {
        "id": "...",
        "name": "レザーグッズ"
      },
      "reviewCount": 12,
      "averageRating": 4.5
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false
}
```

---

### GET /api/v1/products/{id}

**概要:** 商品詳細取得  
**認証:** 不要  
**権限:** 公開

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 商品ID |

#### レスポンス（200 OK）

```json
{
  "id": "...",
  "name": "ハンドメイドレザーウォレット",
  "description": "本革を使用した手縫いのウォレットです。",
  "price": 8500,
  "stockQuantity": 3,
  "status": "ACTIVE",
  "images": [
    {
      "id": "...",
      "imageUrl": "https://res.cloudinary.com/kivio/...",
      "displayOrder": 0
    }
  ],
  "shop": {
    "id": "...",
    "name": "Alice's Atelier",
    "logoUrl": "..."
  },
  "category": {
    "id": "...",
    "name": "レザーグッズ",
    "parentName": "ハンドメイド"
  },
  "shippingPolicy": {
    "shippingType": "CONDITIONAL_FREE",
    "freeThreshold": 5000
  },
  "reviewCount": 12,
  "averageRating": 4.5,
  "createdAt": "2026-05-24T10:00:00Z",
  "updatedAt": "2026-05-24T10:00:00Z"
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 商品が存在しない / `DELETED` ステータス |

---

### POST /api/v1/products

**概要:** 商品登録（デフォルトステータス: `DRAFT`）  
**認証:** 必須  
**権限:** `ROLE_SELLER`

#### リクエストボディ

```json
{
  "name": "ハンドメイドレザーウォレット",
  "description": "本革を使用した手縫いのウォレットです。",
  "price": 8500,
  "stockQuantity": 5,
  "categoryId": "...",
  "status": "DRAFT"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `name` | string | ◯ | 200文字以内 |
| `description` | string | | テキスト |
| `price` | integer | ◯ | 1以上（円単位） |
| `stockQuantity` | integer | ◯ | 0以上 |
| `categoryId` | UUID | | カテゴリーID |
| `status` | string | | `DRAFT`（デフォルト）/ `ACTIVE` |

#### レスポンス（201 Created）

`Location: /api/v1/products/{id}` + 作成した商品オブジェクト

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### PATCH /api/v1/products/{id}

**概要:** 商品更新（自分のショップの商品のみ）  
**認証:** 必須  
**権限:** `ROLE_SELLER`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 商品ID |

#### リクエストボディ（全フィールド省略可）

`POST /products` と同フィールド。`status` には `DRAFT` / `ACTIVE` / `INACTIVE` を指定可能。

#### レスポンス（200 OK）

更新後の商品オブジェクト。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 商品が存在しない |
| `PRODUCT_NOT_OWNED` | 403 | 自分のショップの商品でない |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### DELETE /api/v1/products/{id}

**概要:** 商品削除（`status = 'DELETED'` に変更する論理削除）  
**認証:** 必須  
**権限:** `ROLE_SELLER`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 商品ID |

#### レスポンス（204 No Content）

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 商品が存在しない |
| `PRODUCT_NOT_OWNED` | 403 | 自分のショップの商品でない |

---

### POST /api/v1/products/{id}/images

**概要:** 商品画像アップロード（Cloudinary経由、最大5枚）  
**認証:** 必須  
**権限:** `ROLE_SELLER`  
**Content-Type:** `multipart/form-data`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 商品ID |

#### リクエストボディ

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `file` | binary | ◯ | 画像ファイル（JPEG / PNG / WebP） |

#### レスポンス（201 Created）

```json
{
  "id": "...",
  "cloudinaryId": "kivio/products/abc123",
  "imageUrl": "https://res.cloudinary.com/kivio/...",
  "displayOrder": 1
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `PRODUCT_IMAGE_LIMIT_EXCEEDED` | 409 | 画像が既に5枚存在する |
| `PRODUCT_NOT_OWNED` | 403 | 自分のショップの商品でない |
| `RESOURCE_NOT_FOUND` | 404 | 商品が存在しない |

---

### DELETE /api/v1/products/{id}/images/{imageId}

**概要:** 商品画像削除（Cloudinaryからも削除）  
**認証:** 必須  
**権限:** `ROLE_SELLER`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 商品ID |
| `imageId` | UUID | 画像ID |

#### レスポンス（204 No Content）

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 画像が存在しない |
| `PRODUCT_NOT_OWNED` | 403 | 自分のショップの商品でない |

---

## 8. カート (Cart)

### GET /api/v1/cart

**概要:** カート取得（在庫切れ・削除商品はフラグ付きで含む）  
**認証:** 必須  
**権限:** 全ロール

#### レスポンス（200 OK）

```json
{
  "id": "...",
  "items": [
    {
      "id": "...",
      "product": {
        "id": "...",
        "name": "ハンドメイドレザーウォレット",
        "price": 8500,
        "thumbnailUrl": "...",
        "status": "ACTIVE",
        "stockQuantity": 3
      },
      "quantity": 2,
      "subtotal": 17000,
      "isAvailable": true,
      "unavailableReason": null
    }
  ],
  "totalAmount": 17000
}
```

`isAvailable = false` の場合の `unavailableReason`: `"OUT_OF_STOCK"` / `"PRODUCT_DELETED"` / `"PRODUCT_INACTIVE"`

---

### POST /api/v1/cart/items

**概要:** カートに商品追加  
**認証:** 必須  
**権限:** 全ロール

#### リクエストボディ

```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "quantity": 2
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `productId` | UUID | ◯ | 存在する商品ID |
| `quantity` | integer | ◯ | 1以上 |

既にカートにある商品の場合は数量を加算する。

#### レスポンス（201 Created）

更新後のカートオブジェクト（`GET /cart` と同形式）。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `PRODUCT_NOT_ACTIVE` | 409 | 商品が `ACTIVE` でない |
| `PRODUCT_OUT_OF_STOCK` | 409 | 在庫数が0 |
| `CART_ITEM_QUANTITY_EXCEEDED` | 409 | カート内数量 + 追加数量 > 在庫数 |
| `CANNOT_PURCHASE_OWN_PRODUCT` | 409 | 自分のショップの商品 |
| `RESOURCE_NOT_FOUND` | 404 | 商品が存在しない |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

### PATCH /api/v1/cart/items/{itemId}

**概要:** カート明細の数量変更  
**認証:** 必須  
**権限:** 全ロール

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `itemId` | UUID | カート明細ID |

#### リクエストボディ

```json
{
  "quantity": 3
}
```

#### レスポンス（200 OK）

更新後のカートオブジェクト。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `CART_ITEM_QUANTITY_EXCEEDED` | 409 | 変更後の数量が在庫数を超過 |
| `RESOURCE_NOT_FOUND` | 404 | カート明細が存在しない |
| `ACCESS_DENIED` | 403 | 他人のカート明細 |

---

### DELETE /api/v1/cart/items/{itemId}

**概要:** カート明細削除  
**認証:** 必須  
**権限:** 全ロール

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `itemId` | UUID | カート明細ID |

#### レスポンス（204 No Content）

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | カート明細が存在しない |
| `ACCESS_DENIED` | 403 | 他人のカート明細 |

---

### DELETE /api/v1/cart

**概要:** カート全クリア  
**認証:** 必須  
**権限:** 全ロール

#### レスポンス（204 No Content）

---

## 9. 注文・決済・Webhook (Order / Payment)

### POST /api/v1/orders/checkout

**概要:** 注文作成 + Stripe PaymentIntent 生成  
複数ショップの商品がカートにある場合、ショップ単位に注文を分割して作成する。

**認証:** 必須  
**権限:** 全ロール

#### リクエストボディ

```json
{
  "addressId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `addressId` | UUID | ◯ | 自分の配送先住所ID |

#### レスポンス（201 Created）

```json
{
  "orders": [
    {
      "id": "...",
      "shopName": "Alice's Atelier",
      "subtotal": 17000,
      "shippingFee": 0,
      "totalAmount": 17000,
      "commissionRate": 0.0500,
      "commissionAmount": 850,
      "sellerAmount": 16150
    }
  ],
  "clientSecret": "pi_3NxKTxLLLLLLLLLL_secret_XXXXXXXX",
  "totalAmount": 17000
}
```

`clientSecret` はフロントエンドが Stripe.js で決済実行するために使用する。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `CART_EMPTY` | 409 | カートが空 |
| `PRODUCT_OUT_OF_STOCK` | 409 | カート内商品の在庫不足 |
| `PRODUCT_NOT_ACTIVE` | 409 | カート内商品が非公開・削除済み |
| `RESOURCE_NOT_FOUND` | 404 | 住所が存在しない / 自分の住所でない |
| `PAYMENT_FAILED` | 422 | Stripe PaymentIntent 作成失敗 |

---

### GET /api/v1/orders

**概要:** 注文一覧  
- `ROLE_BUYER`: 自分が購入した注文  
- `ROLE_SELLER`: 自分のショップへの注文  

**認証:** 必須  
**権限:** 全ロール

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `status` | string | ステータスでフィルター |
| `page` | integer | ページ番号 |
| `size` | integer | 件数 |

#### レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": "...",
      "status": "PROCESSING",
      "subtotal": 17000,
      "shippingFee": 0,
      "totalAmount": 17000,
      "shop": {
        "id": "...",
        "name": "Alice's Atelier"
      },
      "itemCount": 2,
      "createdAt": "2026-05-24T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 10,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

### GET /api/v1/orders/{id}

**概要:** 注文詳細取得  
**認証:** 必須  
**権限:** 注文のバイヤーまたはショップのセラー

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 注文ID |

#### レスポンス（200 OK）

```json
{
  "id": "...",
  "status": "PROCESSING",
  "buyer": {
    "id": "...",
    "displayName": "Alice"
  },
  "shop": {
    "id": "...",
    "name": "Bob's Shop"
  },
  "items": [
    {
      "id": "...",
      "productId": "...",
      "productName": "ハンドメイドレザーウォレット（注文時スナップショット）",
      "productImageUrl": "...",
      "unitPrice": 8500,
      "quantity": 2,
      "subtotal": 17000,
      "isReviewed": false
    }
  ],
  "deliveryAddress": {
    "recipientName": "山田 太郎",
    "postalCode": "150-0001",
    "prefecture": "東京都",
    "city": "渋谷区",
    "addressLine": "神南1-1-1",
    "phoneNumber": "090-1234-5678"
  },
  "subtotal": 17000,
  "shippingFee": 0,
  "totalAmount": 17000,
  "commissionRate": 0.0500,
  "commissionAmount": 850,
  "sellerAmount": 16150,
  "cancelledAt": null,
  "cancelReason": null,
  "createdAt": "2026-05-24T10:00:00Z"
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | 注文が存在しない |
| `ACCESS_DENIED` | 403 | バイヤーでもセラーでもない |

---

### PATCH /api/v1/orders/{id}/status

**概要:** 注文ステータス更新（セラーによるステータス進行）  
許可されるステータス遷移: `PAYMENT_CONFIRMED → PROCESSING → SHIPPED → DELIVERED → COMPLETED`

> `DELIVERED` は配送業者API連携（将来対応）または手動確認ステップで使用する。MVP段階では `SHIPPED → DELIVERED` を省略して `SHIPPED → COMPLETED` と進めることも可能。

**認証:** 必須  
**権限:** `ROLE_SELLER`（対象ショップのオーナーのみ）

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 注文ID |

#### リクエストボディ

```json
{
  "status": "SHIPPED"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `status` | string | ◯ | `PROCESSING` / `SHIPPED` / `DELIVERED` / `COMPLETED` |

#### レスポンス（200 OK）

更新後の注文サマリーオブジェクト。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | 注文が存在しない |
| `ACCESS_DENIED` | 403 | 自分のショップの注文でない |
| `ORDER_STATUS_TRANSITION_INVALID` | 409 | 許可されていないステータス遷移 |

---

### POST /api/v1/orders/{id}/cancel

**概要:** 注文キャンセル（バイヤー / セラー）  
キャンセル可能ステータス: `PAYMENT_CONFIRMED` / `PROCESSING`  
キャンセル成功時: Stripe Refunds API で全額返金 → 在庫数を戻す

**認証:** 必須  
**権限:** 注文のバイヤーまたは対象ショップのセラー

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 注文ID |

#### リクエストボディ

```json
{
  "reason": "商品の在庫が確保できないため"
}
```

| フィールド | 型 | 必須 |
|---|---|---|
| `reason` | string | | キャンセル理由（任意） |

#### レスポンス（200 OK）

更新後の注文オブジェクト（`status: "CANCELLED"`）。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | 注文が存在しない |
| `ACCESS_DENIED` | 403 | バイヤーでもセラーでもない |
| `ORDER_NOT_CANCELLABLE` | 409 | キャンセル可能ステータス（`PAYMENT_CONFIRMED` / `PROCESSING`）以外 |
| `REFUND_FAILED` | 500 | Stripe 返金処理失敗 |

---

### POST /api/v1/webhooks/stripe

**概要:** Stripe Webhook 受信（`payment_intent.succeeded` / `charge.refunded` 等）  
**認証:** 不要（Stripeシグネチャ `Stripe-Signature` ヘッダーで検証）  
**権限:** 公開

#### レスポンス（200 OK）

Stripe の仕様に従い処理成功時は `200` を返す。

---

## 10. レビュー (Review)

### GET /api/v1/products/{id}/reviews

**概要:** 商品のレビュー一覧  
**認証:** 不要  
**権限:** 公開

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 商品ID |

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `page` | integer | ページ番号 |
| `size` | integer | 件数（デフォルト: 20） |

#### レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": "...",
      "rating": 5,
      "comment": "素晴らしい品質です！",
      "reviewerDisplayName": "Alice",
      "createdAt": "2026-05-24T10:00:00Z"
    }
  ],
  "summary": {
    "averageRating": 4.5,
    "totalCount": 12,
    "ratingDistribution": {
      "5": 8,
      "4": 2,
      "3": 1,
      "2": 1,
      "1": 0
    }
  },
  "page": 0,
  "size": 20,
  "totalElements": 12,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

### POST /api/v1/order-items/{id}/review

**概要:** レビュー投稿（注文ステータスが `COMPLETED` の注文明細のみ）  
**認証:** 必須  
**権限:** `ROLE_BUYER`（注文のバイヤーのみ）

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 注文明細ID |

#### リクエストボディ

```json
{
  "rating": 5,
  "comment": "素晴らしい品質です！"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `rating` | integer | ◯ | 1〜5の整数 |
| `comment` | string | | テキスト（任意） |

#### レスポンス（201 Created）

```json
{
  "id": "...",
  "rating": 5,
  "comment": "素晴らしい品質です！",
  "createdAt": "2026-05-24T10:00:00Z"
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `REVIEW_ALREADY_SUBMITTED` | 409 | この注文明細へのレビューが既に存在する |
| `REVIEW_NOT_ELIGIBLE` | 403 | 注文が `COMPLETED` でない / 自分の注文でない |
| `VALIDATION_FAILED` | 422 | バリデーション失敗 |

---

## 11. チャット (Chat)

> **注意:** テキストメッセージのみ。メッセージの送受信はリアルタイムに WebSocket / STOMP で行う（[§16](#16-websocket--stomp) 参照）。このセクションの REST API は履歴取得・ルーム管理のみ。

### GET /api/v1/chat-rooms

**概要:** 参加しているチャットルーム一覧（最終メッセージ降順）  
**認証:** 必須  
**権限:** 全ロール

#### レスポンス（200 OK）

```json
[
  {
    "id": "...",
    "shop": {
      "id": "...",
      "name": "Alice's Atelier",
      "logoUrl": "..."
    },
    "buyer": {
      "id": "...",
      "displayName": "Bob"
    },
    "lastMessage": {
      "body": "在庫はありますか？",
      "createdAt": "2026-05-24T10:00:00Z"
    },
    "unreadCount": 2,
    "updatedAt": "2026-05-24T10:00:00Z"
  }
]
```

---

### GET /api/v1/chat-rooms/{id}

**概要:** チャットルーム詳細とメッセージ履歴  
**認証:** 必須  
**権限:** ルームのバイヤーまたはショップのセラー

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | チャットルームID |

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `page` | integer | ページ番号（デフォルト: 0、新しい順） |
| `size` | integer | 件数（デフォルト: 50） |

#### レスポンス（200 OK）

```json
{
  "id": "...",
  "shop": { "id": "...", "name": "Alice's Atelier" },
  "buyer": { "id": "...", "displayName": "Bob" },
  "messages": {
    "content": [
      {
        "id": "...",
        "senderId": "...",
        "body": "在庫はありますか？",
        "readAt": null,
        "createdAt": "2026-05-24T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 5,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `CHAT_ROOM_NOT_FOUND` | 404 | ルームが存在しない |
| `ACCESS_DENIED` | 403 | バイヤーでもセラーでもない |

---

### POST /api/v1/chat-rooms

**概要:** チャットルーム開始（バイヤー → ショップ）。同一バイヤー・ショップ間で既存ルームがある場合は既存ルームを返す。  
**認証:** 必須  
**権限:** 全ロール

#### リクエストボディ

```json
{
  "shopId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### レスポンス（201 Created / 200 OK）

既存ルームがある場合: `200 OK`  
新規ルームの場合: `201 Created` + `Location` ヘッダー

チャットルームオブジェクト（`GET /chat-rooms/{id}` と同形式、メッセージは空）。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `SHOP_NOT_FOUND` | 404 | ショップが存在しない |

---

## 12. 通知 (Notification)

### GET /api/v1/notifications

**概要:** 通知一覧（有効期限内のみ: `expires_at > NOW()`）  
**認証:** 必須  
**権限:** 全ロール

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `unreadOnly` | boolean | `true` = 未読のみ |
| `page` | integer | ページ番号 |
| `size` | integer | 件数（デフォルト: 20） |

#### レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": "...",
      "type": "ORDER_STATUS_CHANGED",
      "title": "注文のステータスが変更されました",
      "body": "注文 #1234 が「発送済み」になりました",
      "isRead": false,
      "readAt": null,
      "relatedEntityType": "ORDER",
      "relatedEntityId": "...",
      "expiresAt": "2026-08-22T10:00:00Z",
      "createdAt": "2026-05-24T10:00:00Z"
    }
  ],
  "unreadCount": 3,
  "page": 0,
  "size": 20,
  "totalElements": 10,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

`type` の種類: `ORDER_CONFIRMED` / `ORDER_STATUS_CHANGED` / `ORDER_CANCELLED` / `NEW_MESSAGE` / `SELLER_APPLICATION_APPROVED` / `SELLER_APPLICATION_REJECTED`

---

### PATCH /api/v1/notifications/{id}/read

**概要:** 通知を既読にする  
**認証:** 必須  
**権限:** 全ロール（自分の通知のみ）

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 通知ID |

#### レスポンス（200 OK）

更新後の通知オブジェクト（`isRead: true`）。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `NOTIFICATION_NOT_FOUND` | 404 | 通知が存在しない / 他人の通知 |

---

### PATCH /api/v1/notifications/read-all

**概要:** 全通知を既読にする  
**認証:** 必須  
**権限:** 全ロール

#### レスポンス（204 No Content）

---

## 13. お気に入り (Wishlist)

### GET /api/v1/wishlist

**概要:** お気に入り商品一覧  
**認証:** 必須  
**権限:** 全ロール

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `page` | integer | ページ番号 |
| `size` | integer | 件数（デフォルト: 20） |

#### レスポンス（200 OK）

`PageResponse<ProductSummaryResponse>`（`GET /products` の `content` 要素と同形式）。

---

### POST /api/v1/wishlist

**概要:** お気に入りに商品追加  
**認証:** 必須  
**権限:** 全ロール

#### リクエストボディ

```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### レスポンス（201 Created）

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `DUPLICATE_ENTRY` | 409 | 既にお気に入りに追加済み |
| `RESOURCE_NOT_FOUND` | 404 | 商品が存在しない |

---

### DELETE /api/v1/wishlist/{productId}

**概要:** お気に入りから商品削除  
**認証:** 必須  
**権限:** 全ロール

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `productId` | UUID | 商品ID |

#### レスポンス（204 No Content）

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | お気に入りが存在しない |

---

## 14. セラーダッシュボード (Seller Dashboard)

### GET /api/v1/seller/dashboard

**概要:** 売上サマリー・統計（当月デフォルト）  
**認証:** 必須  
**権限:** `ROLE_SELLER`

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `year` | integer | 対象年（デフォルト: 現在年） |
| `month` | integer | 対象月（1-12、デフォルト: 現在月） |

#### レスポンス（200 OK）

```json
{
  "period": {
    "year": 2026,
    "month": 5
  },
  "summary": {
    "totalRevenue": 85000,
    "totalOrders": 10,
    "sellerAmount": 80750,
    "commissionAmount": 4250
  },
  "recentOrders": [
    {
      "id": "...",
      "status": "PROCESSING",
      "totalAmount": 8500,
      "createdAt": "2026-05-24T10:00:00Z"
    }
  ],
  "dailyRevenue": [
    { "date": "2026-05-01", "revenue": 0 },
    { "date": "2026-05-24", "revenue": 85000 }
  ],
  "topProducts": [
    {
      "productId": "...",
      "productName": "ハンドメイドレザーウォレット",
      "soldCount": 5,
      "revenue": 42500
    }
  ],
  "unreadMessageCount": 2
}
```

---

## 15. 管理者 (Admin)

> 全エンドポイントで `ROLE_ADMIN` が必要。

### GET /api/v1/admin/seller-applications

**概要:** セラー申請一覧  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `status` | string | `PENDING` / `APPROVED` / `REJECTED` でフィルター |
| `page` | integer | ページ番号 |
| `size` | integer | 件数 |

#### レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": "...",
      "applicant": {
        "id": "...",
        "email": "alice@example.com",
        "displayName": "Alice"
      },
      "reason": "...",
      "status": "PENDING",
      "createdAt": "2026-05-24T10:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1,
  "first": true, "last": true
}
```

---

### POST /api/v1/admin/seller-applications/{id}/approve

**概要:** セラー申請承認（`ROLE_SELLER` 付与 + ショップ自動生成）  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 申請ID |

#### リクエストボディ

```json
{
  "comment": "承認しました。出品を楽しんでください！"
}
```

#### レスポンス（200 OK）

更新後の申請オブジェクト（`status: "APPROVED"`）。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 申請が存在しない |
| `SELLER_APPLICATION_NOT_REVIEWABLE` | 409 | ステータスが `PENDING` でない |

---

### POST /api/v1/admin/seller-applications/{id}/reject

**概要:** セラー申請却下  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 申請ID |

#### リクエストボディ

```json
{
  "comment": "申請理由が不明確なため却下します。再申請はいつでも可能です。"
}
```

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `comment` | string | ◯ | 却下理由（必須） |

#### レスポンス（200 OK）

更新後の申請オブジェクト（`status: "REJECTED"`）。

---

### GET /api/v1/admin/users

**概要:** ユーザー一覧  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `role` | string | ロールでフィルター |
| `status` | string | `ACTIVE` / `INACTIVE` でフィルター |
| `q` | string | メールアドレス・表示名の検索 |
| `page` | integer | ページ番号 |
| `size` | integer | 件数 |

#### レスポンス（200 OK）

`PageResponse<UserResponse>` (`GET /users/me` の要素と同形式)

---

### PATCH /api/v1/admin/users/{id}/status

**概要:** ユーザーアカウント有効化・無効化  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | ユーザーID |

#### リクエストボディ

```json
{
  "status": "INACTIVE"
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `status` | string | ◯ | `ACTIVE` / `INACTIVE` |

#### レスポンス（200 OK）

更新後のユーザーオブジェクト。

---

### GET /api/v1/admin/products

**概要:** 全商品一覧（モデレーション用、全ステータス含む）  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### クエリパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `status` | string | `DRAFT` / `ACTIVE` / `INACTIVE` / `DELETED` でフィルター |
| `shopId` | UUID | ショップでフィルター |
| `page` | integer | ページ番号 |
| `size` | integer | 件数 |

#### レスポンス（200 OK）

`PageResponse<ProductResponse>`

---

### PATCH /api/v1/admin/products/{id}/status

**概要:** 商品ステータス変更（不適切商品の強制非公開等）  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | 商品ID |

#### リクエストボディ

```json
{
  "status": "INACTIVE"
}
```

#### レスポンス（200 OK）

---

### GET /api/v1/admin/categories

**概要:** カテゴリー一覧（管理者用、削除済みを含む）  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### レスポンス（200 OK）

`CategoryTreeResponse[]`（`GET /categories` と同形式だが削除済みも含む）。

---

### POST /api/v1/admin/categories

**概要:** カテゴリー作成  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### リクエストボディ

```json
{
  "name": "レザーグッズ",
  "slug": "leather-goods",
  "parentId": "550e8400-e29b-41d4-a716-446655440000",
  "displayOrder": 3
}
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| `name` | string | ◯ | 100文字以内 |
| `slug` | string | ◯ | 英数字・ハイフンのみ、有効カテゴリー間で一意 |
| `parentId` | UUID | | 親カテゴリーID（省略時はルートカテゴリー） |
| `displayOrder` | integer | | 表示順序（デフォルト: 0） |

#### レスポンス（201 Created）

---

### PATCH /api/v1/admin/categories/{id}

**概要:** カテゴリー更新  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | カテゴリーID |

#### リクエストボディ（全フィールド省略可）

`POST /admin/categories` と同フィールド。

#### レスポンス（200 OK）

---

### DELETE /api/v1/admin/categories/{id}

**概要:** カテゴリー削除（`deleted_at` を設定する論理削除）  
紐づく商品の `category_id` は NULL になる（DB: ON DELETE SET NULL）。

**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `id` | UUID | カテゴリーID |

#### レスポンス（204 No Content）

---

### GET /api/v1/admin/platform-configs

**概要:** プラットフォーム設定一覧  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### レスポンス（200 OK）

```json
[
  {
    "id": "...",
    "configKey": "COMMISSION_RATE",
    "configValue": "0.0500",
    "description": "プラットフォーム手数料率（5%）",
    "updatedBy": {
      "id": "...",
      "displayName": "Admin"
    },
    "updatedAt": "2026-05-24T10:00:00Z"
  }
]
```

---

### PATCH /api/v1/admin/platform-configs/{key}

**概要:** プラットフォーム設定値更新  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### パスパラメータ

| パラメータ | 型 | 説明 |
|---|---|---|
| `key` | string | 設定キー（例: `COMMISSION_RATE`） |

#### リクエストボディ

```json
{
  "value": "0.0300"
}
```

#### レスポンス（200 OK）

更新後の設定オブジェクト。

#### エラー

| エラーコード | HTTP | 条件 |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | 設定キーが存在しない |
| `VALIDATION_FAILED` | 422 | 値の形式が不正 |

---

### GET /api/v1/admin/dashboard

**概要:** プラットフォーム全体統計  
**認証:** 必須  
**権限:** `ROLE_ADMIN`

#### レスポンス（200 OK）

```json
{
  "totalUsers": 150,
  "totalSellers": 20,
  "totalBuyers": 130,
  "totalOrders": 500,
  "totalRevenue": 4250000,
  "totalCommission": 212500,
  "pendingSellerApplications": 3,
  "activeProducts": 200
}
```

---

## 16. WebSocket / STOMP

REST API ではなく WebSocket（STOMP）を使用するリアルタイム機能。  
詳細は [REQUIREMENTS.md § 9](./REQUIREMENTS.md) を参照。

### 接続

```
wss://{host}/ws
```

JWT は HandshakeRequest の Query パラメータで送信する:

```
wss://{host}/ws?token=eyJhbGciOiJIUzI1NiIs...
```

### STOMPトピック一覧

| トピック | 方向 | 用途 | 対象者 |
|---|---|---|---|
| `/topic/chat/{chatRoomId}` | サーバー → クライアント | チャットメッセージ受信 | ルーム参加者 |
| `/topic/notifications/{userId}` | サーバー → クライアント | リアルタイム通知 | 個人 |
| `/topic/orders/{orderId}` | サーバー → クライアント | 注文ステータス変更 | バイヤー |
| `/app/chat.send` | クライアント → サーバー | チャットメッセージ送信 | - |

### メッセージ送信フォーマット（`/app/chat.send`）

```json
{
  "chatRoomId": "550e8400-e29b-41d4-a716-446655440000",
  "body": "在庫はありますか？"
}
```

### チャットメッセージ受信フォーマット（`/topic/chat/{chatRoomId}`）

```json
{
  "id": "...",
  "chatRoomId": "...",
  "senderId": "...",
  "senderDisplayName": "Alice",
  "body": "在庫はありますか？",
  "createdAt": "2026-05-24T10:00:00Z"
}
```

---

**以上**

*本API設計書は実装の参照仕様です。エンドポイント追加・変更時はこのドキュメントと `ERROR_CODES.md` の両方を更新し、OpenAPI仕様（Swagger UI）と整合性を保ってください。*
