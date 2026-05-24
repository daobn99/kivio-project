# テスト戦略

> 参照: [アーキテクチャ概要](../architecture/OVERVIEW.md)（レイヤー構成）、[SETUP.md](./SETUP.md)（環境構築）

---

## 目次

1. [方針](#1-方針)
2. [テストピラミッド](#2-テストピラミッド)
3. [レイヤー別テスト設計](#3-レイヤー別テスト設計)
4. [Testcontainers セットアップ](#4-testcontainers-セットアップ)
5. [命名規則](#5-命名規則)
6. [モック方針](#6-モック方針)
7. [テスト実行](#7-テスト実行)

---

## 1. 方針

- **Repository 層のモック禁止。** JPA クエリ・soft delete フィルター・Flyway マイグレーションの正確な動作は実際の PostgreSQL でしか検証できない。Testcontainers を使用する。
- **ドメインモデルはモック不要。** 集約ルートと Value Object は純粋な Java クラスなので、インスタンスを直接生成してテストする。
- **外部サービス（Stripe / Cloudinary / Resend）は必ずモックまたはスタブ化する。** テスト実行に外部ネットワークへの依存を持ち込まない。
- **テストは独立して実行できること。** 実行順序・他のテストの副作用に依存しない。
- **H2 インメモリ DB は使用しない。** PostgreSQL 固有の型（`UUID`, `JSONB`, `TIMESTAMPTZ`）、`@SQLRestriction`、パーティショニングを正確に検証するため。

---

## 2. テストピラミッド

```
          ┌───────────────┐
          │  Integration  │  少数・遅い・全体フロー検証
          │   (@SpringBootTest + Testcontainers)
          ├───────────────┤
          │  Web Layer    │  Controller・セキュリティ・バリデーション
          │  (@WebMvcTest)│
          ├───────────────┤
          │   Service     │  ユースケース・ビジネスルール連携
          │   (Mockito)   │
          ├───────────────┤
          │    Domain     │  集約・Value Object のビジネスルール
          │  (Pure JUnit) │  多数・速い
          └───────────────┘
```

| テスト種別 | アノテーション | Spring Context | DB | 目安速度 |
|---|---|---|---|---|
| ドメインユニット | （なし） | なし | なし | < 10ms/件 |
| サービスユニット | `@ExtendWith(MockitoExtension.class)` | なし | モック | < 50ms/件 |
| Web Layer | `@WebMvcTest` | 部分起動 | なし | < 500ms/件 |
| Repository | `@DataJpaTest` + Testcontainers | JPA のみ | PostgreSQL | 数秒（初回のみ起動）|
| 統合テスト | `@SpringBootTest` + Testcontainers | フル起動 | PostgreSQL | 数秒〜十数秒 |

---

## 3. レイヤー別テスト設計

### 3.1 ドメインユニットテスト（集約・Value Object）

Spring を起動しない純粋な Java テスト。ビジネスルールの正確性を集中的に検証する。

```java
class OrderTest {

    @Test
    void should_allow_cancellation_when_status_is_payment_confirmed() {
        Order order = Order.create(...); // ファクトリメソッドで生成
        order.confirmPayment();

        assertThatNoException().isThrownBy(order::cancel);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void should_throw_when_cancellation_is_attempted_on_shipped_order() {
        Order order = buildOrderWithStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(order::cancel)
            .isInstanceOf(OrderNotCancellableException.class);
    }
}
```

**対象:** 集約ルートのビジネスルール、Value Object のバリデーション、ドメインイベントの発行

---

### 3.2 サービスユニットテスト

Repository・外部サービス・イベントパブリッシャーを `@Mock` で差し替え、ユースケースのオーケストレーションを検証する。

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock PaymentGateway paymentGateway;      // Stripe の抽象
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks OrderService orderService;

    @Test
    void should_publish_order_cancelled_event_when_cancel_succeeds() {
        Order order = buildCancellableOrder();
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        orderService.cancel(order.getId(), CancellationReason.BUYER_REQUEST);

        verify(paymentGateway).refund(order.getPayment().getStripePaymentId());
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    }
}
```

---

### 3.3 Web Layer テスト（Controller）

`@WebMvcTest` で Controller 層のみを起動。Service は `@MockBean` で差し替える。

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ProductService productService;

    @Test
    @WithMockUser(roles = "SELLER")
    void should_return_201_when_product_is_created() throws Exception {
        CreateProductRequest request = new CreateProductRequest("商品名", 3000, ...);
        given(productService.create(any())).willReturn(sampleProductResponse());

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value("商品名"));
    }

    @Test
    void should_return_401_when_request_is_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/products").content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void should_return_422_when_price_is_negative() throws Exception {
        // Bean Validation のエラーレスポンスを検証
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CreateProductRequest("名前", -1, ...))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("price"));
    }
}
```

**検証対象:**
- HTTP ステータスコード
- レスポンス JSON の構造（`jsonPath`）
- `Location` ヘッダー（POST 成功時）
- Bean Validation エラー（422 のフィールド名・メッセージ）
- 認証・認可（`@WithMockUser`, `@WithAnonymousUser`）

---

### 3.4 Repository テスト

`@DataJpaTest` + Testcontainers で実際の PostgreSQL に対して JPA クエリを検証する。

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("kivio_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired UserRepository userRepository;

    @Test
    void should_exclude_soft_deleted_users_from_findAll() {
        // deleted_at が設定されたユーザーが @SQLRestriction("deleted_at IS NULL") で
        // 自動除外されることを確認（users / shops / categories が対象）
        User deleted = createUser("deleted@example.com");
        deleted.softDelete();
        userRepository.save(deleted);

        User active = createUser("active@example.com");
        userRepository.save(active);

        List<User> result = userRepository.findAll();

        assertThat(result).containsExactly(active)
                          .doesNotContain(deleted);
    }

    @Test
    void should_exclude_status_deleted_products_from_findAll() {
        // Product の論理削除は status = 'DELETED' 管理（deleted_at カラムなし）
        // @SQLRestriction ではなく、Repository のカスタムクエリで除外する
        Product deleted = createProduct(ProductStatus.DELETED);
        Product active  = createProduct(ProductStatus.ACTIVE);
        productRepository.saveAll(List.of(deleted, active));

        List<Product> result = productRepository.findAllActive(); // status != DELETED

        assertThat(result).containsExactly(active)
                          .doesNotContain(deleted);
    }
}
```

> **Soft Delete の実装方式は エンティティによって異なる（CLAUDE.md 参照）:**
> - `users` / `shops` / `categories` → `deleted_at IS NULL` + `@SQLRestriction` で自動除外
> - `products` → `status = 'DELETED'` で管理。`@SQLRestriction` は適用しない

**検証対象:**
- `@SQLRestriction("deleted_at IS NULL")` による soft delete フィルター（users / shops / categories）
- `status != 'DELETED'` による商品フィルター（products）
- カスタム JPQL / Native Query の正確性
- Flyway マイグレーションが正常に適用されること（`@DataJpaTest` 起動時に自動実行）
- インデックスが期待どおり機能すること（`EXPLAIN ANALYZE` を JDBC で実行）

---

### 3.5 統合テスト（フルスタック）

`@SpringBootTest` + Testcontainers で HTTP リクエストから DB まで一貫して検証する。

```java
// @Testcontainers / @Container は不要 — SharedPostgresContainer の static initializer で起動済み
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
    }

    @Autowired TestRestTemplate restTemplate;

    @Test
    void should_issue_tokens_when_credentials_are_valid() {
        RegisterRequest register = new RegisterRequest("test@example.com", "password123");
        restTemplate.postForEntity("/api/v1/auth/register", register, Void.class);

        LoginRequest login = new LoginRequest("test@example.com", "password123");
        ResponseEntity<TokenResponse> response =
            restTemplate.postForEntity("/api/v1/auth/login", login, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
    }
}
```

**用途:** 認証フロー全体、Stripe Webhook 受信→注文ステータス更新フロー、クロスドメインのオーケストレーション

---

## 4. Testcontainers セットアップ

### 4.1 依存関係の追加

`build.gradle.kts` に以下を追加する（Phase 2 実装時）。

```kotlin
dependencies {
    // 既存の依存関係...
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
```

### 4.2 共有コンテナ（パフォーマンス最適化）

テストクラスごとにコンテナを起動すると遅くなるため、JVM 内で1つのコンテナを共有する。

```java
// src/test/java/io/kivio/support/SharedPostgresContainer.java
public final class SharedPostgresContainer {

    public static final PostgreSQLContainer<?> INSTANCE =
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("kivio_test")
            .withReuse(true);  // ローカルでコンテナを再利用（後述）

    static {
        INSTANCE.start();
    }

    private SharedPostgresContainer() {}
}
```

> **`withReuse(true)` を有効にするには:**
> `~/.testcontainers.properties` に以下を追加する。設定なしの場合はテストごとに新しいコンテナが起動される（遅いが問題はない）。
> ```properties
> testcontainers.reuse.enable=true
> ```

```java
// 各テストクラスでの使用（@Testcontainers / @Container は不要）
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SomeRepositoryTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
    }
}
```

### 4.3 テスト用 `application.yml`

`src/test/resources/application.yml` でテスト専用の設定を上書きする。

```yaml
spring:
  flyway:
    enabled: true   # テスト時もマイグレーションを実行
  jpa:
    show-sql: false  # テストログを減らす

# 外部サービスのダミー設定（起動エラーを防ぐ）
stripe:
  secret-key: sk_test_dummy
  webhook-secret: whsec_dummy
cloudinary:
  cloud-name: dummy
  api-key: dummy
  api-secret: dummy
resend:
  api-key: re_dummy
```

---

## 5. 命名規則

### テストクラス

```
{テスト対象クラス名}Test.java

例:
  UserServiceTest.java
  ProductControllerTest.java
  OrderRepositoryTest.java
  AuthIntegrationTest.java
```

### テストメソッド

```java
// 形式: should_{期待する結果}_when_{条件}
@Test
void should_return_404_when_product_does_not_exist() { ... }

@Test
void should_throw_InsufficientStockException_when_stock_is_zero() { ... }

@Test
void should_exclude_soft_deleted_users_from_search_results() { ... }
```

---

## 6. モック方針

| レイヤー / 対象 | モック方針 | 理由 |
|---|---|---|
| ドメインモデル（Entity・Value Object） | **モック禁止** | 直接インスタンス化してテスト |
| Repository | Service テストは `@Mock`、Repository テスト自体は Testcontainers | DB 依存の動作は実 PostgreSQL で検証 |
| 外部サービス（Stripe・Cloudinary・Resend） | `@MockBean` または WireMock | ネットワーク依存を排除 |
| ApplicationEventPublisher | `@Mock` + `verify()` | イベント発行の副作用を検証 |
| Spring Security | `@WithMockUser` / `@WithAnonymousUser` | 認証状態を差し替えてテスト |

### 外部サービスの抽象化

外部サービスはインターフェースで抽象化し、テスト時に差し替えやすくする。

```java
// インターフェース
public interface PaymentGateway {
    String createPaymentIntent(long amount, String currency);
    void refund(String paymentIntentId);
}

// 本番実装
@Service
public class StripePaymentGateway implements PaymentGateway { ... }

// テストでモック
@MockBean PaymentGateway paymentGateway;
given(paymentGateway.createPaymentIntent(anyLong(), any())).willReturn("pi_test_xxx");
```

---

## 7. テスト実行

```bash
# 全テスト
./gradlew test

# 特定のクラスのみ
./gradlew test --tests "io.kivio.domain.order.OrderTest"

# パッケージ単位（ドメインユニットテストのみ）
./gradlew test --tests "io.kivio.domain.*"

# 統合テストのみ（sourceSet 分離後）
./gradlew integrationTest

# テストレポートを開く（macOS）
open kivio-backend/build/reports/tests/test/index.html
```

### Gradle タスクの分離（Phase 2 以降・オプション）

統合テストを CI で分離実行したい場合は `build.gradle.kts` で `sourceSet` を分ける。
分離後は `./gradlew integrationTest` で統合テストのみ実行可能になる。

```kotlin
// build.gradle.kts に追加
val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations["integrationTestImplementation"]
    .extendsFrom(configurations["testImplementation"])

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")
    useJUnitPlatform()
}
```

統合テストクラスは `src/integrationTest/java/` に配置する。
