# ドメインモデル（DDD）

> 参照: [OVERVIEW.md](./OVERVIEW.md)、[../design/DB_DESIGN.md](../design/DB_DESIGN.md)、[../../adr/ADR-002-ddd-adoption.md](../../adr/ADR-002-ddd-adoption.md)

---

## 目次

1. [DDD 採用方針](#1-ddd-採用方針)
2. [Bounded Context マップ](#2-bounded-context-マップ)
3. [集約ルート一覧](#3-集約ルート一覧)
4. [Value Object カタログ](#4-value-object-カタログ)
5. [Domain Events カタログ](#5-domain-events-カタログ)
6. [クロスドメイン通信設計](#6-クロスドメイン通信設計)
7. [Repository 設計](#7-repository-設計)

---

## 1. DDD 採用方針

本プロジェクトでは **Strategic DDD + Tactical DDD** を採用する。

| パターン | 採用範囲 |
|---|---|
| **Strategic DDD** | Bounded Context の分割（パッケージ境界の定義） |
| **Tactical DDD** | 集約・集約ルート・Value Object・Domain Events の実装 |

### 適用レベル

ポートフォリオプロジェクトのため、DDD の全パターンを厳密に適用するのではなく、**保守性とコードの意図明確化**を主目的として以下の範囲で適用する。

- **集約（Aggregate）**: 一貫性の境界を明確にし、無効な状態への遷移を防ぐ
- **Value Object**: 値の等価性・不変性を型として表現する（`Money`, `Email` 等）
- **Domain Events**: ドメイン間の疎結合な連携を実現する（Spring Application Events）
- **Bounded Context**: パッケージ境界 = マイクロサービスへの将来的な分割単位

---

## 2. Bounded Context マップ

```
┌──────────────────────────────────────────────────────────────┐
│                      Kivio マーケットプレイス                  │
│                                                              │
│  ┌─────────────┐     申請承認      ┌──────────────────────┐  │
│  │  identity   │ ────────────────► │      catalog         │  │
│  │             │  (SellerApproved  │                      │  │
│  │  User       │   Event →         │  Shop                │  │
│  │  Auth       │   Shop自動生成)    │  Product             │  │
│  │  Seller     │                   │  Category            │  │
│  │  Application│                   └──────────────────────┘  │
│  └─────────────┘                                             │
│         │                          ┌──────────────────────┐  │
│         │ UserContext              │        order         │  │
│         │ (User IDの参照)           │                      │  │
│         │                          │  Cart                │  │
│         └────────────────────────► │  Order               │  │
│                                    │  Payment             │  │
│                                    │  Address             │  │
│                                    └──────────┬───────────┘  │
│                                               │              │
│  ┌─────────────┐    ┌─────────────┐           │ OrderEvent   │
│  │  messaging  │    │   review    │           │              │
│  │             │    │             │           ▼              │
│  │  ChatRoom   │    │  Review     │  ┌─────────────────────┐ │
│  │  ChatMessage│    │  Wishlist   │  │    notification     │ │
│  └─────────────┘    └─────────────┘  │                     │ │
│                                      │  Notification       │ │
│  ┌─────────────┐    ┌─────────────┐  └─────────────────────┘ │
│  │  platform   │    │    audit    │                          │
│  │             │    │             │                          │
│  │ PlatformCon │    │  AuditLog   │                          │
│  │ fig         │    │ (@Auditable)│                          │
│  └─────────────┘    └─────────────┘                          │
└──────────────────────────────────────────────────────────────┘
```

### コンテキスト間の依存関係

| 発信元 | 受信先 | 通信手段 | 内容 |
|---|---|---|---|
| identity | catalog | Spring Event | SellerApproved → Shop 自動生成 |
| identity | notification | Spring Event | 各種認証イベント → 通知生成 |
| identity | audit | @Auditable AOP | 認証・管理者操作のログ記録 |
| catalog | audit | @Auditable AOP | 商品強制非公開等のログ記録 |
| order | notification | Spring Event | 注文イベント → バイヤー・セラー通知 |
| order | audit | @Auditable AOP | 注文キャンセル等のログ記録 |
| platform | audit | @Auditable AOP | 設定変更のログ記録 |

---

## 3. 集約ルート一覧

集約の境界は「一貫性の境界」である。集約内のエンティティは集約ルートを経由してのみ変更する。

### identity ドメイン

| 集約ルート | 含むエンティティ | 主な不変条件 |
|---|---|---|
| `User` | - | メールアドレスは一意、メール未確認ユーザーはログイン不可 |
| `RefreshToken` | - | トークンは失効後に再使用不可 |
| `SellerApplication` | - | PENDING 申請が存在する間は新規申請不可 |

### catalog ドメイン

| 集約ルート | 含むエンティティ | 主な不変条件 |
|---|---|---|
| `Shop` | `ShopShippingPolicy` | セラー 1 名につきショップ 1 つ、ショップ名は有効レコード間で一意 |
| `Product` | `ProductImage` | 画像は最大 5 枚、価格は正の整数 |
| `Category` | - | 最大 2 階層（親・子） |

### order ドメイン

| 集約ルート | 含むエンティティ | 主な不変条件 |
|---|---|---|
| `Cart` | `CartItem` | ユーザーにつき 1 カート、在庫数を超えた数量を追加不可 |
| `Order` | `OrderItem` | ショップ単位に分割、注文後は商品名・価格をスナップショット保持 |
| `Payment` | - | 注文に 1:1、Stripe ID は一意 |
| `Address` | - | ユーザーにつきデフォルト住所は 1 件のみ |

### messaging ドメイン

| 集約ルート | 含むエンティティ | 主な不変条件 |
|---|---|---|
| `ChatRoom` | - | バイヤーとショップの組み合わせにつき 1 ルーム |
| `ChatMessage` | - | 同一ルーム内での時系列整合性を保証 |

> **設計注記:** `ChatMessage` は `ChatRoom` の子エンティティに見えるが、メッセージは大量蓄積されページネーション検索が必須なため**独立した集約ルートとして分離**する。`ChatMessageRepository` を通じて `ChatRoom` とは別に管理する。

### notification ドメイン

| 集約ルート | 含むエンティティ | 主な不変条件 |
|---|---|---|
| `Notification` | - | 有効期限 90 日、期限超過後は非表示 |

### review ドメイン

| 集約ルート | 含むエンティティ | 主な不変条件 |
|---|---|---|
| `Review` | - | 注文明細につき 1 件、COMPLETED 注文のみ投稿可能 |
| `Wishlist` | - | ユーザーと商品の組み合わせにつき 1 件のみ（複合一意） |

### platform ドメイン

| 集約ルート | 含むエンティティ | 主な不変条件 |
|---|---|---|
| `PlatformConfig` | - | キーは一意、管理者のみ更新可能 |

### audit ドメイン

`AuditLog` は伝統的な集約ではなく**追記専用の記録**として扱う。Repository を通じた INSERT のみ許可し、UPDATE/DELETE は禁止。

---

## 4. Value Object カタログ

Value Object は不変（immutable）で等値性（equality）はフィールドの値で判断する。Java では `record` で実装する。

| Value Object | フィールド | 用途 |
|---|---|---|
| `Money` | `amount: int`, `currency: String = "JPY"` | 価格・送料・手数料の表現 |
| `Email` | `value: String` | メールアドレスの検証付きラッパー |
| `CommissionRate` | `rate: BigDecimal` | 手数料率（0.0000 〜 1.0000） |
| `DeliveryAddress` | `recipientName`, `postalCode`, `prefecture`, `city`, `addressLine`, `phoneNumber` | 注文時の配送先スナップショット（Order に埋め込み） |
| `OrderStatus` | `value: OrderStatusEnum` | 状態遷移ルールをカプセル化 |

> **注意:** `ShopShippingPolicy` は DB 上で独自 UUID PK と `created_at` / `updated_at` を持つため Value Object ではなく **`Shop` 集約内の子エンティティ** として扱う。`ShopRepository` 経由で Shop と一緒にロードする。

### Value Object 実装例

```java
// Money — 不変、円単位の金額
public record Money(int amount) {
    public Money {
        if (amount < 0) throw new IllegalArgumentException("金額は0以上");
    }
    public Money add(Money other) { return new Money(this.amount + other.amount); }
    public Money multiply(CommissionRate rate) {
        return new Money((int)(this.amount * rate.rate().doubleValue()));
    }
}

// OrderStatus — 状態遷移ルールをカプセル化
public record OrderStatus(OrderStatusEnum value) {
    public OrderStatus transitionTo(OrderStatusEnum next) {
        if (!value.canTransitionTo(next)) {
            throw new IllegalStateException(value + " → " + next + " の遷移は無効");
        }
        return new OrderStatus(next);
    }
}
```

---

## 5. Domain Events カタログ

Domain Events はドメイン間の疎結合な通信に使用する。イベントクラスは `common/event/` または発行元ドメインの `domain/event/` に定義する。

### 認証・ユーザー系（identity → 各ドメイン）

| イベント名 | 発行タイミング | 主な購読側 |
|---|---|---|
| `UserRegisteredEvent` | 会員登録完了 | audit |
| `UserEmailVerifiedEvent` | メールアドレス確認完了 | audit |
| `UserLoggedInEvent` | ログイン成功 | audit |
| `UserLoginFailedEvent` | ログイン失敗 | audit |
| `UserLoggedOutEvent` | ログアウト | audit |
| `UserPasswordChangedEvent` | パスワード変更 | audit |
| `UserDeactivatedEvent` | ユーザー無効化（管理者） | audit, notification |
| `UserActivatedEvent` | ユーザー有効化（管理者） | audit |

### セラー申請系（identity → 各ドメイン）

| イベント名 | 発行タイミング | 主な購読側 |
|---|---|---|
| `SellerApplicationSubmittedEvent` | セラー申請送信 | audit |
| `SellerApplicationApprovedEvent` | セラー申請承認 | catalog（Shop 自動生成）, notification, audit |
| `SellerApplicationRejectedEvent` | セラー申請却下 | notification, audit |

### 注文・決済系（order → 各ドメイン）

| イベント名 | 発行タイミング | 主な購読側 |
|---|---|---|
| `PaymentConfirmedEvent` | Stripe Webhook 受信・決済確認 | notification, audit |
| `OrderCancelledEvent` | 注文キャンセル（バイヤー/セラー） | notification, audit |
| `OrderStatusChangedEvent` | 注文ステータス更新 | notification, audit |

### 管理者操作系（catalog / platform → 各ドメイン）

| イベント名 | 発行タイミング | 主な購読側 |
|---|---|---|
| `ProductForcefullyDeactivatedEvent` | 管理者による商品強制非公開 | audit |
| `PlatformConfigUpdatedEvent` | プラットフォーム設定変更 | audit |

### イベント実装規約

```java
// イベントクラスの構造（record で不変に）
public record SellerApplicationApprovedEvent(
    UUID applicationId,
    UUID applicantUserId,
    String shopName,
    Instant occurredAt
) {}

// 発行（Application Service 内）
applicationEventPublisher.publishEvent(
    new SellerApplicationApprovedEvent(
        application.getId(),
        application.getApplicantId(),
        shopName,
        Instant.now()
    )
);

// 購読（別ドメインのイベントハンドラ）
@EventListener
@Async  // 非同期処理の場合
public void handle(SellerApplicationApprovedEvent event) {
    shopCreationService.createShopFor(event.applicantUserId(), event.shopName());
}
```

---

## 6. クロスドメイン通信設計

### 同期通信（Application Service 経由）

トランザクションの境界を共有する必要がある場合に使用する。依存方向を制御するため、クロスドメイン操作は interface を定義して DI する。

```
SellerApplicationService（identity）
  ↓ @Autowired（interface）
ShopCreationUseCase（interface in identity domain）
  ↑ implements
ShopCreationService（catalog）
```

### 非同期通信（Spring Application Events）

副作用的な処理（通知生成・監査ログ）や疎結合が望ましいドメイン間連携に使用する。

```
@TransactionalEventListener(phase = AFTER_COMMIT)
```

を使用することで、元のトランザクションのコミット後にイベントが処理される。これにより副作用が DB コミット前に発生するのを防ぐ。

---

## 7. Repository 設計

**1集約ルートにつき1Repositoryインターフェース。**

- `JpaRepository<T, UUID>` を継承する
- Soft Delete 済みレコードは `@SQLRestriction("deleted_at IS NULL")` で自動除外
- カスタムクエリは `@Query` または Specifications で実装
- 戻り値はドメインオブジェクト（Entity）。DTO への変換は Service/Mapper が行う

```java
// 集約ルートごとに Repository を定義
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    // 複合クエリは @Query で明示
    @Query("SELECT o FROM Order o WHERE o.buyerId = :buyerId ORDER BY o.createdAt DESC")
    Page<Order> findByBuyerId(@Param("buyerId") UUID buyerId, Pageable pageable);
}
```

> **注意:** `audit_logs` は追記専用のため `AuditLogRepository` では `save()` のみ公開し、`delete*()` メソッドを使用しない。
