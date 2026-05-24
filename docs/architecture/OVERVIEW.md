# アーキテクチャ概要

> 参照: `CLAUDE.md`（コマンド・規約）、`REQUIREMENTS.md § 5`、[ADR/](../../adr/)（設計判断）

---

## 目次

1. [システム全体構成](#1-システム全体構成)
2. [バックエンドレイヤー構成](#2-バックエンドレイヤー構成)
3. [パッケージ構成](#3-パッケージ構成)
4. [ドメイン間通信](#4-ドメイン間通信)
5. [外部サービス統合](#5-外部サービス統合)
6. [技術スタック](#6-技術スタック)

---

## 1. システム全体構成

```
┌──────────────────────────────────────────────────────┐
│                     クライアント                        │
│          Next.js (App Router) + shadcn/ui             │
│               Vercel にデプロイ                         │
└─────────────────────┬────────────────────────────────┘
                      │ HTTPS / WebSocket(WSS)
┌─────────────────────▼────────────────────────────────┐
│               Spring Boot 4.x API Server              │
│            Render / Railway にデプロイ                  │
│                                                       │
│  ┌────────────────────────────────────────────────┐  │
│  │         Spring Security Filter Chain           │  │
│  │    JWT 検証 / Rate Limiting / CORS / MDC       │  │
│  └────────────────────────────────────────────────┘  │
│                                                       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐   │
│  │identity │ │ catalog │ │  order  │ │messaging │   │
│  └─────────┘ └─────────┘ └─────────┘ └──────────┘   │
│  ┌──────────────┐ ┌─────────┐ ┌─────────┐ ┌────────┐  │
│  │notification  │ │ review  │ │platform │ │ audit  │  │
│  └──────────────┘ └─────────┘ └─────────┘ └────────┘  │
│                                                       │
│  ┌────────────────────────────────────────────────┐  │
│  │           infra/（外部サービスクライアント）       │  │
│  │         Stripe / Cloudinary / Resend / Google  │  │
│  └────────────────────────────────────────────────┘  │
└──────────┬─────────────────────┬──────────────────────┘
           │ JPA / JDBC          │ STOMP
  ┌────────▼──────┐    ┌─────────▼───────┐
  │  PostgreSQL   │    │  In-Memory STOMP │
  │  (Neon/Supa.) │    │  Broker         │
  └───────────────┘    └─────────────────┘
```

**将来の拡張設計:**
- WebSocket スケーリング: In-Memory STOMP Broker → Redis Pub/Sub（interface 差し替えで対応）
- マイクロサービス化: 各ドメインパッケージが独立デプロイ可能な設計を維持

---

## 2. バックエンドレイヤー構成

リクエストは下方向にのみ流れる。逆方向の依存は禁止。

```
HTTP Request
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│  Security Filter Chain                                   │
│  ① MDC に correlation_id (UUID) を設定                   │
│  ② JWT 検証（Bearer token）                               │
│  ③ Rate Limiting チェック                                 │
│  ④ CORS チェック                                          │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│  @RestController                                         │
│  ・Bean Validation によるリクエスト検証                    │
│  ・認可チェック（@PreAuthorize / 所有者確認）               │
│  ・Application Service への委譲                           │
│  ・DTO → HTTP レスポンス変換                               │
└──────────────────────────┬───────────────────────────────┘
                           │ Command / Query オブジェクト
                           ▼
┌──────────────────────────────────────────────────────────┐
│  Application Service（@Service, @Transactional）          │
│  ・ユースケースを調整（複数集約・ドメインをまたぐ操作）        │
│  ・@Auditable AOP で監査ログを記録                         │
│  ・ApplicationEventPublisher でドメインイベント発行         │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│  Domain（集約ルート・エンティティ・Value Object）            │
│  ・ビジネスルールをカプセル化                               │
│  ・集約内の整合性を自己保証                                 │
│  ・状態変更は集約ルート経由のみ                              │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│  Repository（集約ルートごとに 1 インターフェース）            │
│  ・Spring Data JPA 実装                                   │
│  ・DB アクセス専用。ビジネスロジックを持たない               │
│  ・Soft Delete: @SQLRestriction("deleted_at IS NULL")     │
└──────────────────────────┬───────────────────────────────┘
                           │ JPA / JDBC
                           ▼
                     PostgreSQL
```

### レイヤー制約（絶対ルール）

| 禁止事項 | 理由 |
|---|---|
| Controller から Repository を直接呼ぶ | ビジネスロジックの分散防止 |
| ドメイン間の直接 import（例: identity → catalog の import） | 将来のマイクロサービス分割を阻害 |
| Domain オブジェクト内での DB アクセス | 単一責任の維持 |
| Application Service 内での生の SQL / JPQL 直書き | Repository 抽象化を破壊 |

---

## 3. パッケージ構成

```
com.kivio/
├── common/               # 全ドメイン共通
│   ├── PageResponse.java # Spring Page<T> をラップするレスポンス型
│   ├── exception/        # BusinessException, ResourceNotFoundException 等
│   └── entity/           # BaseEntity（id, created_at, updated_at）
│                         # SoftDeletableEntity（deleted_at 付き）
│
├── config/               # Spring 設定クラス
│   ├── SecurityConfig.java
│   ├── WebSocketConfig.java
│   ├── OpenApiConfig.java
│   └── CorrelationIdFilter.java  # MDC correlation_id 設定
│
├── domain/
│   ├── identity/         # User, Auth, SellerApplication
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/       # User, SellerApplication (集約), Email (VO) 等
│   │   ├── repository/
│   │   └── dto/
│   ├── catalog/          # Shop, Product, Category
│   ├── order/            # Cart, Order, Payment, Address
│   ├── messaging/        # ChatRoom, ChatMessage
│   ├── notification/     # Notification
│   ├── review/           # Review
│   ├── platform/         # PlatformConfig
│   └── audit/            # AuditLog, @Auditable, AuditLogAspect
│
└── infra/                # 外部サービスクライアント実装
    ├── stripe/            # StripePaymentGateway
    ├── cloudinary/        # CloudinaryImageStorage
    ├── resend/            # ResendEmailSender
    └── google/            # GoogleTokenVerifier
```

各ドメインパッケージ内の構成を統一する:

```
domain/{context}/
├── controller/    @RestController
├── service/       @Service + @Transactional（Application Service）
├── domain/        集約ルート・エンティティ・Value Object・Domain Event
├── repository/    @Repository インターフェース（JPA 実装は Spring が生成）
└── dto/           Request DTO・Response DTO・Mapper
```

---

## 4. ドメイン間通信

ドメイン間の直接 import は禁止。下記2つの手段のみ使用する。

### 4.1 Application Service 経由（同期・ユースケース内）

複数ドメインにまたがるユースケースは、各ドメインの Service / Repository を DI した Application Service が調整する。依存関係は interface 経由にすることでドメイン間の結合を避ける。

```
例: セラー申請承認（identity の操作が catalog のショップ作成を要求する）

SellerApplicationService（identity パッケージ）
    ├── SellerApplicationRepository（identity）
    ├── UserRepository（identity）
    └── ShopCreationUseCase（interface） ← catalog が実装を提供
```

### 4.2 Spring Application Events 経由（非同期・副作用）

状態変化の副作用（通知・監査ログ）には Spring Events を使用する。発行側はイベントクラスのみを知り、購読側のパッケージを参照しない。

```java
// 発行側（identity ドメイン）
applicationEventPublisher.publishEvent(new SellerApprovedEvent(userId, shopName));

// 購読側（notification ドメイン）
@EventListener
public void onSellerApproved(SellerApprovedEvent event) {
    // 通知を生成 — identity ドメインを import しない
}
```

| 通信手段 | 用途 |
|---|---|
| Application Service 経由 | ユースケース内のクロスドメイン操作（トランザクション境界を共有） |
| Spring Events | 副作用（通知生成・監査ログ）、ドメイン間の疎結合連携 |

詳細な Domain Events カタログは [DOMAIN_MODEL.md](./DOMAIN_MODEL.md) を参照。

---

## 5. 外部サービス統合

`infra/` パッケージに実装し、Domain 層は `common` または各ドメインの `domain/` に定義した interface のみを参照する。外部サービスの実装詳細がドメイン層に漏れるのを防ぐ。

| サービス | 用途 | infra パッケージ |
|---|---|---|
| Stripe | 決済・返金（PaymentIntent / Refund API） | `infra.stripe` |
| Cloudinary | 画像アップロード・自動リサイズ・WebP 変換 | `infra.cloudinary` |
| Resend | メール送信（確認メール・注文通知） | `infra.resend` |
| Google OAuth | ID Token 検証（Google ログイン） | `infra.google` |

---

## 6. 技術スタック

| カテゴリ | 採用技術 |
|---|---|
| 言語 | Java 25 |
| フレームワーク | Spring Boot 4.x |
| セキュリティ | Spring Security + OAuth2 Resource Server |
| ORM | Spring Data JPA / Hibernate |
| DB マイグレーション | Flyway |
| リアルタイム | Spring WebSocket / STOMP |
| バリデーション | Jakarta Bean Validation |
| API ドキュメント | springdoc-openapi（OpenAPI 3.0 / Swagger UI） |
| テスト | JUnit 5, Mockito, Testcontainers |
| ビルド | Gradle (Kotlin DSL) |

---

## 関連ドキュメント

| ドキュメント | 内容 |
|---|---|
| [DOMAIN_MODEL.md](./DOMAIN_MODEL.md) | DDD 集約・Value Object・Domain Events 設計 |
| [SECURITY.md](./SECURITY.md) | JWT 認証フロー・Security Filter Chain |
| [AUDIT.md](./AUDIT.md) | `@Auditable` AOP・監査ログ設計 |
| [../../adr/](../../adr/) | 設計判断の記録（ADR） |
| [../design/API_DESIGN.md](../design/API_DESIGN.md) | REST API エンドポイント詳細 |
| [../design/DB_DESIGN.md](../design/DB_DESIGN.md) | DB スキーマ・インデックス詳細 |
