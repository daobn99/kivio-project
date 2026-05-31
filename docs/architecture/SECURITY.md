# セキュリティアーキテクチャ

> 参照: [OVERVIEW.md](./OVERVIEW.md)、[../design/SEQUENCE_FLOW.md](../design/SEQUENCE_FLOW.md)（認証フロー図）、[../../adr/ADR-004-jwt-strategy.md](../../adr/ADR-004-jwt-strategy.md)

---

## 目次

1. [認証方式の全体像](#1-認証方式の全体像)
2. [JWT 設計](#2-jwt-設計)
3. [Spring Security Filter Chain](#3-spring-security-filter-chain)
4. [Rate Limiting 設計](#4-rate-limiting-設計)
5. [認可設計](#5-認可設計)
6. [パスワード設計](#6-パスワード設計)
7. [CORS 設計](#7-cors-設計)
8. [WebSocket セキュリティ](#8-websocket-セキュリティ)
9. [セキュリティ要件まとめ](#9-セキュリティ要件まとめ)

---

## 1. 認証方式の全体像

Kivio は **2種類の認証方式**をサポートする。どちらも認証後は同じ自前 JWT を発行し、以降のリクエストは JWT のみで認可する。

| 方式 | フロー | 実装 |
|---|---|---|
| メール + パスワード | ユーザーが直接登録・ログイン | Spring Security + BCrypt |
| Google OAuth 2.0 | NextAuth.js が ID Token を取得 → バックエンドが検証 | Google ID Token 検証 |

認証フローの詳細シーケンス図は [SEQUENCE_FLOW.md § 1](../design/SEQUENCE_FLOW.md) を参照。

```
フロントエンド                  バックエンド
     │                              │
     │ POST /auth/login             │
     │ {email, password}            │
     │ ─────────────────────────► │
     │                              │ BCrypt 検証
     │                              │ RefreshToken 生成・保存
     │ 200 {accessToken, refreshToken}
     │ ◄───────────────────────── │
     │                              │
     │ GET /api/v1/... (Bearer: accessToken)
     │ ─────────────────────────► │
     │                              │ JWT 検証
     │                              │ SecurityContext に User 設定
     │ 200 {data}                   │
     │ ◄───────────────────────── │
     │                              │
     │ POST /auth/refresh           │
     │ {refreshToken}              │
     │ ─────────────────────────► │
     │                              │ RefreshToken 検証・ローテーション
     │ 200 {accessToken, newRefreshToken}
     │ ◄───────────────────────── │
```

---

## 2. JWT 設計

### 2.1 トークン仕様

| 項目 | Access Token | Refresh Token |
|---|---|---|
| 有効期限 | 15 分 | 7 日 |
| 保存場所（クライアント） | メモリ（Redux/Zustand）または HTTP-only Cookie | HTTP-only Cookie |
| 保存場所（バックエンド） | 保存なし（ステートレス） | SHA-256 ハッシュ値を DB に保存 |
| 失効方法 | 期限切れのみ（ブラックリストなし） | DB から削除 / `revoked = TRUE` |

### 2.2 JWT ペイロード

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // user_id (UUID)
  "role": "ROLE_SELLER",
  "iat": 1748080800,
  "exp": 1748081700
}
```

Access Token には最低限の情報のみ含める。ユーザーの詳細情報（名前・メール等）は `/users/me` API で取得する。

### 2.3 署名アルゴリズム

| フェーズ | アルゴリズム | 理由 |
|---|---|---|
| Phase 2（現在） | **HS256**（共有秘密鍵） | 実装シンプル・シングルサービス |
| Phase 5+（将来） | **RS256**（RSA 非対称鍵） | 公開鍵を別サービスと共有可能 |

詳細な判断理由は [ADR-004](../../adr/ADR-004-jwt-strategy.md) を参照。

### 2.4 Refresh Token のローテーション

セキュリティ向上のため、リフレッシュリクエストのたびに Refresh Token を新しいものに差し替える（Token Rotation）。

```
1. POST /auth/refresh {refreshToken: "old_token"}
2. DB で old_token を検索・有効性確認
3. old_token を DB から削除（または revoked = TRUE）
4. 新しい refreshToken を生成・DB に保存
5. 新しい accessToken + refreshToken を返却
```

盗まれた Refresh Token が使用された場合（検出: 同じトークンが2回目に使用された時）は、そのユーザーの全 Refresh Token を無効化する。

### 2.5 Google ID Token 検証

```
フロントエンド          Google          バックエンド
     │                   │                  │
     │ Google ログイン     │                  │
     │ ─────────────────►│                  │
     │ ID Token           │                  │
     │ ◄─────────────────│                  │
     │                   │                  │
     │ POST /auth/google {idToken}          │
     │ ─────────────────────────────────── ►│
     │                   │ 公開鍵取得         │
     │                   │ ◄─────────────── │
     │                   │ 公開鍵             │
     │                   │ ──────────────── ►│
     │                   │                  │ 署名・audience・issuer 検証
     │ 200 {accessToken, refreshToken}      │
     │ ◄─────────────────────────────────── │
```

---

## 3. Spring Security Filter Chain

フィルターはリクエストごとにこの順序で実行される。

```
Request
  │
  ▼
┌────────────────────────────────────────────────────────┐
│  1. CorrelationIdFilter（OncePerRequestFilter）         │
│     MDC に correlation_id (UUID) を設定                 │
│     レスポンスヘッダー X-Correlation-Id にも付与          │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│  2. CorsFilter                                         │
│     許可オリジン（フロントエンドの URL のみ）              │
│     Preflight リクエストに 200 を返す                    │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│  3. RateLimitingFilter                                 │
│     Bucket4j（または Resilience4j）でレート制限           │
│     超過時: 429 Too Many Requests                       │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│  4. JwtAuthenticationFilter（OncePerRequestFilter）     │
│     Authorization: Bearer {token} を解析                │
│     JWT 検証（署名・期限・claims）                        │
│     SecurityContextHolder に Authentication を設定      │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│  5. ExceptionTranslationFilter                         │
│     AuthenticationException → 401                      │
│     AccessDeniedException → 403                        │
│     ProblemDetail 形式でレスポンス                       │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
                      Controller
```

### エンドポイント別の認証設定

```java
// SecurityConfig の概要
http.authorizeHttpRequests(auth -> auth
    // 認証不要（公開エンドポイント）
    .requestMatchers(POST, "/api/v1/auth/check-email").permitAll()
    .requestMatchers(POST, "/api/v1/auth/register").permitAll()
    .requestMatchers(POST, "/api/v1/auth/login").permitAll()
    .requestMatchers(POST, "/api/v1/auth/google").permitAll()
    .requestMatchers(POST, "/api/v1/auth/refresh").permitAll()
    .requestMatchers(POST, "/api/v1/webhooks/stripe").permitAll()
    .requestMatchers(GET,  "/api/v1/products/**").permitAll()
    .requestMatchers(GET,  "/api/v1/shops/**").permitAll()
    .requestMatchers(GET,  "/api/v1/categories").permitAll()
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

    // ロール別制限
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    .requestMatchers(POST, "/api/v1/seller-applications").hasRole("BUYER")
    .requestMatchers(POST, "/api/v1/products").hasRole("SELLER")

    // その他は認証必須
    .anyRequest().authenticated()
);
```

---

## 4. Rate Limiting 設計

| エンドポイント区分 | 制限 | 制限単位 |
|---|---|---|
| 認証系（`/api/v1/auth/*`） | 10 リクエスト / 分 | IP アドレス |
| API 全般（認証済みユーザー） | 100 リクエスト / 分 | ユーザー ID |
| 公開 API（未認証） | 30 リクエスト / 分 | IP アドレス |
| Stripe Webhook | 制限なし | - |

超過時のレスポンス:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 60

{
  "type": "https://kivio.example.com/problems/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "リクエスト数が上限に達しました。60秒後に再試行してください"
}
```

---

## 5. 認可設計

### 5.1 ロールベース認可

Spring Security の `@PreAuthorize` + メソッドセキュリティで実装する。

```java
@PreAuthorize("hasRole('ADMIN')")
public void approveSellerApplication(UUID id) { ... }

@PreAuthorize("hasRole('SELLER')")
public void updateProduct(UUID productId, UpdateProductRequest req) { ... }
```

### 5.2 リソース所有者チェック

ロールチェックだけでは不十分なため、**自分のリソースのみアクセス可能**という所有者チェックを実施する。Controller の冒頭または Application Service 内で確認する。

```java
// Controller 内での所有者チェック例
@PatchMapping("/orders/{id}/status")
public ResponseEntity<?> updateOrderStatus(@PathVariable UUID id, ...) {
    UUID currentUserId = securityHelper.getCurrentUserId();
    Order order = orderService.findById(id);

    // 所有者（バイヤー）またはショップのセラーのみ操作可能
    if (!order.getBuyerId().equals(currentUserId)
            && !order.getShop().getOwnerId().equals(currentUserId)) {
        throw new AccessDeniedException("このリソースへのアクセス権がありません");
    }
    ...
}
```

### 5.3 認可エラー

| 状況 | レスポンス |
|---|---|
| JWT なし / 無効 / 期限切れ | `401 Unauthorized` |
| ロール不足 | `403 Forbidden` |
| 他人のリソースへのアクセス | `403 Forbidden` |

---

## 6. パスワード設計

- **アルゴリズム**: BCrypt
- **コストファクター**: 12（ブルートフォースへの耐性とレスポンス速度のバランス）
- **検証**: Spring Security の `BCryptPasswordEncoder` を使用

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

Google OAuth ユーザー（メール登録なし）は `password_hash = NULL` とし、パスワード認証不可にする。

---

## 7. CORS 設計

- 許可オリジン: フロントエンドの URL のみ（環境変数 `ALLOWED_ORIGINS` で設定）
- 許可メソッド: `GET, POST, PATCH, DELETE, OPTIONS`
- 許可ヘッダー: `Authorization, Content-Type`
- Credentials: `true`（Cookie / Authorization ヘッダーを許可）

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    ...
}
```

---

## 8. WebSocket セキュリティ

### 8.1 WebSocket 接続時の JWT 認証

SockJS はブラウザから WebSocket ハンドシェイク時にカスタム HTTP ヘッダーを付与できない制約があるため、JWT は接続 URL の **クエリパラメータ** で送信する。

```
wss://{host}/ws?token=eyJhbGciOiJIUzI1NiIs...
```

バックエンドは `HandshakeInterceptor` でリクエスト到達時にクエリパラメータから JWT を取り出し、HTTP リクエストと同じ `JwtAuthenticator` で検証する。検証成功後、`Principal` を WebSocket セッションに設定する。

```java
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String query = request.getURI().getQuery();  // "token=eyJ..."
        String token = extractToken(query);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Authentication auth = jwtAuthenticator.authenticate(token);
        attributes.put("authentication", auth);
        return true;
    }
}
```

> **セキュリティ上の注意:** クエリパラメータは Web サーバーのアクセスログに記録される可能性がある。Access Token の短命設計（15 分）でリスクを最小化する。本番環境ではロードバランサー／リバースプロキシのアクセスログにトークンが残らないよう設定を確認すること。

---

### 8.2 トピック購読の認可

SimpleBroker はデフォルトで全クライアントが任意のトピックを購読できる。これを防ぐため `SUBSCRIBE` フレームでトピックと認証ユーザーの紐づきを検証する。

| トピックパターン | 許可条件 |
|---|---|
| `/topic/notifications/{userId}` | `userId == currentUser.id` |
| `/topic/chat/{roomId}` | `chat_rooms` テーブルで `buyer_id` または `shop.seller_id` が `currentUser.id` に一致 |
| `/topic/orders/{orderId}` | `orders` テーブルで `user_id`（buyer）または `shop.seller_id` が `currentUser.id` に一致 |

```java
if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
    String destination = accessor.getDestination();
    UUID currentUserId = ((KivioUserDetails) accessor.getUser().getPrincipal()).getId();

    if (destination.startsWith("/topic/notifications/")) {
        UUID targetUserId = extractUserId(destination);
        if (!targetUserId.equals(currentUserId)) {
            throw new AccessDeniedException("他ユーザーの通知トピックは購読できません");
        }
    } else if (destination.startsWith("/topic/chat/")) {
        UUID roomId = extractRoomId(destination);
        if (!chatAuthorizationService.isMember(roomId, currentUserId)) {
            throw new AccessDeniedException("このチャットルームへのアクセス権がありません");
        }
    } else if (destination.startsWith("/topic/orders/")) {
        UUID orderId = extractOrderId(destination);
        if (!orderAuthorizationService.isParticipant(orderId, currentUserId)) {
            throw new AccessDeniedException("この注文へのアクセス権がありません");
        }
    }
}
```

---

### 8.3 WebSocket 接続のセキュリティ設定

`HandshakeInterceptor` で認証した `Principal` は STOMP セッション全体に伝搬するため、`@MessageMapping` ハンドラーでは通常の `Principal` 引数から認証済みユーザーを取得できる。

```java
@Configuration
public class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        messages
            .simpSubscribeDestMatchers("/topic/notifications/**").authenticated()
            .simpSubscribeDestMatchers("/topic/chat/**").authenticated()
            .simpSubscribeDestMatchers("/topic/orders/**").authenticated()
            .anyMessage().authenticated();
    }

    @Override
    protected boolean sameOriginDisabled() {
        return true; // CORS は HTTP レイヤーの CorsFilter で管理するため無効化
    }
}
```

---

### 8.4 その他の WebSocket セキュリティ考慮事項

| 脅威 | 対策 |
|---|---|
| JWT 未提供 / 無効での接続 | `HandshakeInterceptor` で検証し、無効なら HTTP 401 を返して接続を拒否 |
| 他ユーザーのトピック購読 | `SUBSCRIBE` フレームで所有者チェック（§ 8.2） |
| STOMP メッセージインジェクション | `@MessageMapping` 引数は Bean Validation で検証 |
| WebSocket ハイジャック | SockJS エンドポイントに CORS 制限（`setAllowedOriginPatterns`） |
| クエリパラメータのトークン露出 | アクセスログへの記録リスクあり（SockJS の制約）。Access Token の短命設計（15分）で影響を最小化（§ 8.1 参照） |
| 大量接続（DoS） | Rate Limiting は HTTP レイヤーで実施。WebSocket 接続数はホスティング側（Render / Railway）のリソース制限で管理 |
| Access Token 期限切れ中の接続継続 | WebSocket 接続はステートフル。接続確立時に JWT を検証するが、接続中の失効検出は行わない（MVP 許容）。Access Token 15 分の短命設計で影響を最小化 |

---

## 9. セキュリティ要件まとめ

| 要件 | 実装方法 |
|---|---|
| パスワードハッシュ化 | BCrypt cost factor 12 |
| JWT 署名 | HS256（Phase 2）/ RS256（Phase 5+） |
| Access Token 有効期限 | 15 分 |
| Refresh Token 有効期限 | 7 日、DB にハッシュ保存・ローテーション |
| Rate Limiting | Bucket4j / IP またはユーザー単位 |
| SQL インジェクション対策 | JPA パラメータ化クエリのみ使用 |
| クレジットカード情報 | Kivio システムに保持しない（Stripe に委譲） |
| HTTPS | 本番環境のみ（ホスティング側で設定） |
| XSS 対策 | フロントエンドでサニタイズ、バックエンドでは HTML を許可しない |
| CORS | フロントエンドオリジンのみ許可 |
