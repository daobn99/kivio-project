# テスト戦略

> 参照: [アーキテクチャ概要](../architecture/OVERVIEW.md)（レイヤー構成）

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
- **Redis に依存するサービス（OTP・登録セッション）はモックしない。** TTL の付与・SHA-256 ハッシュ保存・試行回数の加算・キー失効・再送スロットリングといった **Redis の実挙動そのもの**が検証対象であり、Mockito では `expire()` を呼んだ事実しか確認できず実装の写経になる。Testcontainers の実 Redis を使う（`disabledWithoutDocker=true` で Docker 不在時は自動スキップ。→ §3.2）。

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
| 設定・プロファイル | `ApplicationContextRunner` | 部分起動 | なし | < 500ms/件 |

> Redis に依存するサービス（OTP・登録セッション）は例外的に実 Redis（Testcontainers）でユニットテストする（→ §3.2）。Spring コンテキストは起動せず Redis コンテナのみ起動するため、フル統合テストより軽量。

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

#### Redis に依存するサービス（実 Redis で検証）

`OtpService` / `RegistrationSessionService` のように **Redis の実挙動そのもの**（TTL・SHA-256 保存・`attempts` 加算・キー失効・スロットリング）が検証対象のサービスは、Mockito では「`expire()` を呼んだ」ことしか確認できず実装の写経になる（→ §1）。Spring コンテキストは起動せず、Testcontainers の Redis コンテナへ直接つないだ `StringRedisTemplate` でサービスを手組みする。設定値（TTL・上限回数等）もテストから直接渡せる軽量構成。

```java
@Testcontainers(disabledWithoutDocker = true) // Docker 不在環境では自動スキップ
class OtpServiceTest {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private StringRedisTemplate redis;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        var cf = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll(); // 各テスト前にクリア
        otpService = new OtpService(redis, authProperties); // 設定値を直接注入
    }

    @Test
    void should_store_hash_and_ttl_when_issue() {
        String otp = otpService.issue("user@example.com");

        // 「呼んだか」ではなく「実際にどうなったか」をアサートする
        assertThat(redis.getExpire("reg:otp:user@example.com")).isBetween(590L, 600L);
    }
}
```

> 実装例: `src/test/java/io/kivio/domain/identity/service/OtpServiceTest.java` / `RegistrationSessionServiceTest.java`
> DooD（Docker-outside-of-Docker）環境では `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` により `getHost()` が解決される。

---

### 3.3 Web Layer テスト（Controller）

`@WebMvcTest` で Controller 層のみを起動。Service は `@MockitoBean` で差し替える。

> **`@MockBean` は使わない（Spring Boot 3.4 で非推奨）。** `org.springframework.test.context.bean.override.mockito.MockitoBean` を使用する。`@SpyBean` も同様に `@MockitoSpyBean` へ。

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProductService productService;

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
- Bean Validation エラー（422 のフィールド名・メッセージ＝`$.errors[].field` / `$.errors[].message`）
- 認証・認可（`@WithMockUser`, `@WithAnonymousUser`）

> **`@AuthenticationPrincipal` でカスタム principal（`KivioUserDetails`）を受け取る Controller** は `@WithMockUser` では検証できない（標準 `User` が注入され `userId`(UUID) を渡せない）。`SecurityMockMvcRequestPostProcessors.user(...)` で実際の principal を差し込む:
> ```java
> mockMvc.perform(patch("/api/v1/users/me")
>         .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
>         .contentType(MediaType.APPLICATION_JSON).content(body))
>     .andExpect(status().isOk());
> ```
> 認証なし（401）の検証は `.with(user(...))` を付けずにリクエストする。

---

### 3.4 Repository テスト

`@DataJpaTest` + Testcontainers で実際の PostgreSQL に対して JPA クエリを検証する。

コンテナの配線は共有基底クラス `RepositoryTestBase` に集約する（→ §4.2）。各 Repository テストは基底を継承するだけでよい。

```java
// RepositoryTestBase が @DataJpaTest + Testcontainers(@ServiceConnection) を提供する
class ProductRepositoryTest extends RepositoryTestBase {

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
// コンテナ配線は共有基底 IntegrationTestBase（@ServiceConnection + singleton 起動）に集約（→ §4.2）
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void should_issue_tokens_when_credentials_are_valid() {
        // メール登録は OTP 認証を伴う3ステップ（OTP は Redis）のため、
        // ログイン検証用のユーザーは認証済み状態で直接永続化する
        userRepository.save(User.builder()
            .email("test@example.com")
            .passwordHash(passwordEncoder.encode("password123"))
            .displayName("Test")
            .role(UserRole.ROLE_BUYER)
            .status(UserStatus.ACTIVE)
            .build());

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

#### メール送信のモック境界

メール送信を伴う統合テストでは、トランスポート（`EmailSender`）ではなく**ユースケースサービス（`{Context}EmailService`）を `@MockitoBean` で差し替える**。件名・本文の組み立てを跨がずに送信入力（OTP 等）を直接捕捉でき、SMTP/Resend への依存も排除できる。

```java
// OTP メール送信をモックし、生成された OTP を捕捉してフローを進める
@MockitoBean private AuthEmailService authEmailService;

private String captureSentOtp(String email) {
    ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
    verify(authEmailService).sendRegistrationOtp(eq(email), otpCaptor.capture());
    return otpCaptor.getValue();
}
```

### 3.6 設定・プロファイル解決テスト（Testcontainers 不要）

Bean のプロファイル別解決など、フル起動せずに検証できる設定面は `ApplicationContextRunner` で軽量にテストする。Docker 不要で常に実行されるため、フル統合テストが Docker 不在でスキップされる環境でも回帰を捕捉できる。

例として、環境ごとに `EmailSender` 実体が一意に解決され、`test` プロファイルにフォールバックが存在すること（これが無いとフルコンテキスト起動の `@SpringBootTest` が壊れる）を検証する。

```java
class EmailSenderProfileTest {
    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(LogEmailSender.class);

    @Test
    void fallback_is_active_under_test_profile() {
        runner.withPropertyValues("spring.profiles.active=test").run(context -> {
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context).getBean(EmailSender.class).isInstanceOf(LogEmailSender.class);
        });
    }

    @Test
    void fallback_is_excluded_under_prod_profile() {
        runner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).doesNotHaveBean(EmailSender.class));
    }
}
```

> プロファイルは `withPropertyValues` で明示指定する（実行環境の `SPRING_PROFILES_ACTIVE` に依存させない）。

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

### 4.2 共有コンテナ（基底クラス + `@ServiceConnection`）

テストクラスごとにコンテナを起動すると遅いため、JVM 内で1つのコンテナを共有する。配線は共有基底クラス（`src/test/java/io/kivio/support/`）に集約し、各テストはこれを継承するだけにする。

- **統合テスト**: `IntegrationTestBase`（`@SpringBootTest` + Mockmvc）
- **Repository テスト**: `RepositoryTestBase`（`@DataJpaTest`）

コンテナと Spring の接続は **`@ServiceConnection`**（Spring Boot 3.1+）で自動配線する。`@DynamicPropertySource` で `spring.datasource.*` を手書きする必要はない（`@ServiceConnection` が JDBC URL / 認証情報 / Redis 接続を解決する）。

```java
// src/test/java/io/kivio/support/IntegrationTestBase.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
// @Container は付けない（クラス単位の stop を避ける）。disabledWithoutDocker で Docker 不在時はスキップ
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer("postgres:17")
            .withDatabaseName("kivio_test").withUsername("test").withPassword("test");

    @ServiceConnection // image 名 "redis" を Spring Boot が認識し spring.data.redis を自動設定
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        // singleton 起動: 一度だけ start し、テストクラスをまたいで再利用する（stop しない）
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            REDIS.start();
        }
    }
}
```

```java
// 各テストは基底を継承するだけ（@SpringBootTest / @Testcontainers / @Container は不要）
class AddressControllerIntegrationTest extends IntegrationTestBase {
    @Autowired MockMvc mockMvc;
    // ...
}
```

> **⚠️ static コンテナを `@Container` でクラス単位管理しないこと。** `@Testcontainers` + `@Container static` にすると、最初に走ったクラスの `afterAll` で static コンテナが **stop** される。同一コンテキスト署名（モック Bean・プロパティが同一）を共有する後続クラスは、停止済みコンテナを指す**キャッシュ済み Spring コンテキスト**を再利用するため接続不能になる（`HikariPool ... total=0`）。`@Container` を付けず static 初期化子で一度だけ `start()` する singleton 方式とし、`disabledWithoutDocker` の挙動は `@Testcontainers`（フィールド管理なし）で温存する。
>
> **`withReuse(true)`（任意・ローカル高速化）:** さらに JVM 終了後もコンテナを再利用したい場合は `.withReuse(true)` を付け、`~/.testcontainers.properties` に `testcontainers.reuse.enable=true` を設定する。未設定でも singleton により JVM 内では1つに集約される。

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

> **`EmailSender` のフォールバックに注意。** プロファイル別 Bean（dev=`SmtpEmailSender` / prod=`ResendEmailSender`）はダミー設定だけでは解決されない。`test` プロファイルではフォールバック `LogEmailSender`（`@Profile("!dev & !prod")`）が `EmailSender` を提供するため、`@SpringBootTest` のフルコンテキストが起動できる。これが欠けると `EmailSender` の Bean 不在でコンテキスト起動が失敗する（→ 3.6 で回帰を防ぐ）。

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
| Redis（OTP・登録セッション） | **モック禁止**・実 Redis（Testcontainers） | TTL・ハッシュ保存・キー失効・スロットリングの実挙動が検証対象（→ 3.2） |
| 外部サービス（Stripe・Cloudinary・Resend） | `@MockitoBean` または WireMock | ネットワーク依存を排除 |
| メール送信 | ユースケースサービス（`{Context}EmailService`）を `@MockitoBean` | 送信入力（OTP 等）を直接捕捉でき、テンプレート描画やトランスポート（SMTP/Resend）に依存しない（→ 3.5 メール送信のモック境界） |
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

// テストでモック（@MockBean は Spring Boot 3.4 で非推奨 → @MockitoBean）
@MockitoBean PaymentGateway paymentGateway;
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
