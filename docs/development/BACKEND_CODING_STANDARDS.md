# バックエンドコーディング規約

> 参照: [アーキテクチャ概要](../architecture/OVERVIEW.md)、[SECURITY.md](../architecture/SECURITY.md)、[AUDIT.md](../architecture/AUDIT.md)

---

## 目次

1. [全体アーキテクチャ](#1-全体アーキテクチャ)
2. [ディレクトリ・パッケージ構成](#2-ディレクトリパッケージ構成)
3. [Lombok 規約](#3-lombok-規約)
4. [レイヤー別責務と実装規約](#4-レイヤー別責務と実装規約)
   - 4.1 [Controller](#41-controller)
   - 4.2 [Request DTO（Bean Validation）](#42-request-dtobean-validation)
   - 4.3 [Service（Application Service）](#43-serviceapplication-service)
   - 4.4 [Repository](#44-repository)
   - 4.5 [Domain（Entity・Value Object・集約）](#45-domainentityvalue-object集約)
   - 4.6 [Response DTO](#46-response-dto)
   - 4.7 [Mapper](#47-mapper)
5. [認証・認可（Spring Security + JWT）](#5-認証認可spring-security--jwt)
6. [例外処理（RFC 9457 ProblemDetail）](#6-例外処理rfc-9457-problemdetail)
7. [Enum 規約](#7-enum-規約)
8. [JPA・エンティティ規約](#8-jpaエンティティ規約)
9. [API レスポンス規約](#9-api-レスポンス規約)
10. [ドメイン間通信](#10-ドメイン間通信)
11. [監査ログ（@Auditable AOP）](#11-監査ログauditable-aop)
12. [データベース・マイグレーション規約（Flyway）](#12-データベースマイグレーション規約flyway)
13. [テスト規約（JUnit 5 + AssertJ + JaCoCo）](#13-テスト規約junit-5--assertj)
14. [コーディング規約](#14-コーディング規約)
   - 14.7 [コメント規約](#147-コメント規約)
15. [実装チェックリスト](#15-実装チェックリスト)

---

## 1. 全体アーキテクチャ

### 設計方針

Modular Monolith + DDD（Strategic + Tactical）を採用する。ドメインごとにパッケージを分離し、将来のマイクロサービス化に対応できる構造を維持する。

### レイヤーの依存方向

リクエストは下方向にのみ流れる。逆方向の依存は禁止。

```
HTTP Request
    │
    ▼
Security Filter Chain（JWT 検証・Rate Limiting・MDC）
    │
    ▼
@RestController（受け取り・委譲・レスポンス変換）
    │
    ▼
Application Service（@Service + @Transactional）（ユースケース調整）
    │
    ▼
Domain（Entity・Value Object・集約ルート）（ビジネスルール）
    │
    ▼
Repository（DB アクセス専用）
    │
    ▼
PostgreSQL
```

### 絶対ルール

| 禁止事項 | 理由 |
|---|---|
| Controller から Repository を直接呼ぶ | ビジネスロジックの分散防止 |
| ドメイン間の直接 import | 将来のマイクロサービス分割を阻害 |
| Domain オブジェクト内での DB アクセス | 単一責任の維持 |
| Application Service 内での生の SQL / JPQL 直書き | Repository 抽象化を破壊 |

---

## 2. ディレクトリ・パッケージ構成

### パッケージルート: `io.kivio`

```
io.kivio/
├── common/
│   ├── PageResponse.java          # Spring Page<T> をラップする汎用レスポンス型
│   ├── exception/                 # KivioException 基底、ドメイン例外
│   ├── entity/                    # BaseEntity、SoftDeletableEntity
│   └── usecase/                   # クロスドメイン interface（例: ShopCreationUseCase）
│
├── config/                        # Spring 設定クラス群
│   ├── SecurityConfig.java
│   ├── WebSocketConfig.java
│   ├── OpenApiConfig.java
│   └── CorrelationIdFilter.java
│
├── domain/
│   ├── identity/                  # User, Auth, RefreshToken, SellerApplication
│   ├── catalog/                   # Shop, Product, Category
│   ├── order/                     # Cart, Order, Payment, Address
│   ├── messaging/                 # ChatRoom, ChatMessage
│   ├── notification/              # Notification
│   ├── review/                    # Review, Wishlist
│   ├── platform/                  # PlatformConfig
│   └── audit/                     # AuditLog, @Auditable, AuditLogAspect
│
└── infra/                         # 外部サービスクライアント実装
    ├── stripe/
    ├── cloudinary/
    ├── resend/
    └── google/
```

### 各ドメインパッケージの内部構成（統一）

```
domain/{context}/
├── controller/    @RestController — HTTP の入出力のみ
├── service/       @Service + @Transactional — ユースケース調整
├── domain/        集約ルート・Entity・Value Object・Domain Event
├── repository/    @Repository インターフェース（実装は Spring が生成）
└── dto/           Request DTO（Lombok class）・Response DTO（record）・Mapper
```

---

## 3. Lombok 規約

### 使用可能なアノテーション

| アノテーション | 用途 | 対象 |
|---|---|---|
| `@Getter` | 全フィールドの getter を生成 | Entity, Request DTO |
| `@RequiredArgsConstructor` | `final` フィールドを引数に取るコンストラクタ生成 | Service, Controller |
| `@Builder` | Builder パターンを生成 | Entity, Request DTO |
| `@Slf4j` | `log` フィールドを生成（SLF4J Logger） | Service, Filter |
| `@ToString` | `toString()` を生成（`exclude` で機密フィールドを除外） | Entity（任意） |
| `@EqualsAndHashCode` | Entity の ID ベース equals/hashCode（`onlyExplicitlyIncluded = true`） | Entity |

### 禁止アノテーション

| アノテーション | 理由 |
|---|---|
| `@Data` | `@Setter` を含むため不変性を破壊する。`@Getter + @Builder` で代替 |
| `@Setter` | Entity のフィールドを外部から自由に変更できてしまう。ドメインメソッドで操作する |
| `@AllArgsConstructor`（public） | 外部から全フィールドを直接指定して構築できてしまう。Entity では `@Builder` 専用に `AccessLevel.PRIVATE` で使用する（後述）|

### Entity への Lombok 適用パターン

```java
@Entity
@Table(name = "products")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 用
@AllArgsConstructor(access = AccessLevel.PRIVATE)   // @Builder 用
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"shop", "category"})           // 循環参照防止
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    private String name;

    // 状態変更はドメインメソッドで行う
    public void publish() {
        if (this.status != ProductStatus.DRAFT) {
            throw new IllegalStateException("下書き状態の商品のみ公開できます");
        }
        this.status = ProductStatus.ACTIVE;
    }
}
```

### Request DTO への Lombok 適用パターン

```java
@Getter
@Builder
public class CreateProductRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Positive
    private int price;

    @NotNull
    private UUID categoryId;
}
```

---

## 4. レイヤー別責務と実装規約

### 4.1 Controller

**責務:** HTTP の入出力のみ。ビジネスロジックは持たない。

```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "商品管理")
public class ProductController {

    private final ProductService productService;

    // GET: ページネーション付きリスト
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.findAll(page, size));
    }

    // POST: 新規作成 → 201 + Location ヘッダー
    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.create(request);
        URI location = URI.create("/api/v1/products/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    // PATCH: 部分更新（PUT は使わない）
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    // DELETE: 204 No Content
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

**ルール:**
- `@RequestMapping` はクラスレベルにベースパスを定義する
- メソッドレベルのロール認可は `@PreAuthorize` で行う
- リソース所有者チェックは Service 層に移譲し、Controller では行わない
- 戻り値は常に `ResponseEntity<T>` でラップする
- Controller はバリデーション結果の受け取りのみ行い、バリデーションロジックは書かない

---

### 4.2 Request DTO（Bean Validation）

**形式:** `@Getter @Builder` の Lombok class を使用する。record は使わない（Bean Validation との相性上）。

```java
@Getter
@Builder
public class CreateProductRequest {

    @NotBlank(message = "商品名は必須です")
    @Size(max = 100, message = "商品名は100文字以内です")
    private String name;

    @NotBlank
    @Size(max = 2000)
    private String description;

    @Positive(message = "価格は正の整数で入力してください")
    private int price;

    @NotNull
    private UUID categoryId;

    // 日時フィールドは String ではなく Instant / OffsetDateTime を使う
    // Jackson の JavaTimeModule が自動で ISO 8601 形式を変換・検証する
    @FutureOrPresent
    private Instant publishedAt;
}
```

**カスタムバリデーター（必要な場合のみ作成）:**

```java
// アノテーション定義
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidPhoneNumber {
    String message() default "電話番号の形式が不正です";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// 実装
public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9,10}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true; // null チェックは @NotNull に委ねる
        return PHONE_PATTERN.matcher(value).matches();
    }
}
```

**日時フィールドの規約:**

```java
// OK: 型で受け取る（Jackson が ISO 8601 を自動検証・変換）
private Instant scheduledAt;
private OffsetDateTime publishedAt;

// NG: String で受け取らない
private String scheduledAt; // 形式検証・変換ロジックが必要になる
```

**クエリパラメータの日時:**

```java
// クエリパラメータの場合は @DateTimeFormat を直接付与する
@GetMapping
public ResponseEntity<PageResponse<OrderResponse>> list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    ...
}
```

---

### 4.3 Service（Application Service）

**責務:** ユースケースの調整。複数の集約・Repository を組み合わせてビジネスフローを実現する。

```java
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 読み取り専用は @Transactional(readOnly = true)
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PageResponse.of(productRepository.findAll(pageable).map(ProductResponse::from));
    }

    @Auditable(action = "PRODUCT_CREATED", entityType = "PRODUCT")
    public ProductResponse create(CreateProductRequest request) {
        Shop shop = shopRepository.findByOwnerIdOrThrow(SecurityHelper.getCurrentUserId());
        Product product = Product.builder()
                .name(request.getName())
                .price(request.getPrice())
                .shop(shop)
                .build();
        Product saved = productRepository.save(product);
        log.info("product_created id={} shopId={}", saved.getId(), shop.getId());
        return ProductResponse.from(saved);
    }

    @Auditable(action = "PRODUCT_DELETED", entityType = "PRODUCT", entityIdParam = "id")
    public void delete(UUID id) {
        Product product = productRepository.findByIdOrThrow(id);
        verifyOwnership(product);
        product.delete();  // status = DELETED（ソフトデリート）
        productRepository.save(product);
    }

    private void verifyOwnership(Product product) {
        UUID currentUserId = SecurityHelper.getCurrentUserId();
        if (!product.getShop().getOwnerId().equals(currentUserId)) {
            throw new AccessDeniedException("このリソースへのアクセス権がありません");
        }
    }
}
```

**ルール:**
- クラスレベルに `@Transactional` を付与し、読み取り専用メソッドには `@Transactional(readOnly = true)` を上書きする
- ビジネスロジックは Service ではなく Domain（集約ルート）に置く
- Service は「何を・どの順序で」だけを記述する（How は Domain が担う）
- 所有者チェックは Service 内の private メソッドとして切り出す

---

### 4.4 Repository

**責務:** DB アクセス専用。ビジネスロジックを持たない。

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // N+1 防止: 必要な関連は JOIN FETCH で一括取得
    @Query("SELECT p FROM Product p JOIN FETCH p.shop JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") UUID id);

    // ページネーション付きリスト（shop の N+1 回避）
    @Query(value = "SELECT p FROM Product p JOIN FETCH p.shop WHERE p.shop.id = :shopId",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.shop.id = :shopId")
    Page<Product> findByShopId(@Param("shopId") UUID shopId, Pageable pageable);

    // Projection: 一覧表示など必要なカラムのみ取得
    @Query("SELECT new io.kivio.domain.catalog.dto.ProductSummary(p.id, p.name, p.price) " +
           "FROM Product p WHERE p.status = 'ACTIVE'")
    Page<ProductSummary> findActiveSummaries(Pageable pageable);

    // 例外をスローするデフォルトメソッド（Service での Optional.orElseThrow を省略）
    default Product findByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Product", id));
    }
}
```

**ルール:**
- 集約ルートごとに Repository を 1 つ定義する（`OrderItem` の Repository は作らない）
- `findAll()` の安易な使用を避け、必要なフィールドだけを取得する Projection を検討する
- `findByIdOrThrow()` のような便利メソッドは `default` メソッドとして Repository に定義する
- ネイティブ SQL は原則禁止。`EXPLAIN ANALYZE` で検証が必要な場合のみ `@Query(nativeQuery = true)` を使う

---

### 4.5 Domain（Entity・Value Object・集約）

#### 集約ルート

```java
@Entity
@Table(name = "orders")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // 状態変更はドメインメソッドで行う。Setter は公開しない
    public void cancel() {
        if (!this.status.isCancellable()) {
            throw new OrderNotCancellableException(this.id, this.status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void confirmPayment() {
        if (this.status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(this.id, "支払い確認");
        }
        this.status = OrderStatus.PAYMENT_CONFIRMED;
    }
}
```

#### Value Object

不変性を型で表現する。`record` を使う。

```java
// Value Object: record を使って不変性を表現
public record Money(int amount, String currency) {

    public Money {
        if (amount < 0) throw new IllegalArgumentException("金額は0以上でなければなりません");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("通貨は必須です");
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("異なる通貨は加算できません");
        }
        return new Money(this.amount + other.amount, this.currency);
    }
}

// Email Value Object
public record Email(String value) {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@]+@[^@]+\\.[^@]+$");

    public Email {
        if (value == null || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: " + value);
        }
    }
}
```

#### ドメインイベント

```java
// イベントクラスは record で定義（不変・軽量）
public record OrderCancelledEvent(
        UUID orderId,
        UUID buyerId,
        String correlationId,
        Instant occurredAt
) {
    public static OrderCancelledEvent of(Order order) {
        return new OrderCancelledEvent(
                order.getId(),
                order.getBuyer().getId(),
                MDC.get("correlationId"),
                Instant.now()
        );
    }
}
```

**ルール:**
- フィールドは `private final` が原則。状態変更が必要なフィールドのみ `private`（Setter なし）
- 状態変更はドメインメソッドで行い、メソッド内で事前条件を検証する
- `@ManyToOne` は `fetch = FetchType.LAZY` を明示する（デフォルトが EAGER のため）
- Value Object は `record` で定義し、コンパクトコンストラクタで検証する

---

### 4.6 Response DTO

**形式:** `record` を使用する。`from()` 静的ファクトリメソッドで Entity から生成する。

```java
// 基本的な Response DTO
public record ProductResponse(
        UUID id,
        String name,
        int price,
        String status,
        Instant createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStatus().name(),
                product.getCreatedAt()
        );
    }
}

// ネストした Response DTO（関連エンティティを含む）
public record OrderResponse(
        UUID id,
        String status,
        List<OrderItemResponse> items,
        int totalAmount,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
```

**ルール:**
- Response DTO に Entity の参照を持たせない（`from()` 内で変換を完結させる）
- フィールド名は camelCase（JSON に `@JsonProperty` で別名を付ける場合は明示する）
- `null` を返す可能性があるフィールドには `@JsonInclude(NON_NULL)` をクラスに付与する

---

### 4.7 Mapper

Mapper ライブラリ（MapStruct 等）は使用しない。`from()` 静的ファクトリメソッドを Response DTO に直接定義する。

変換ロジックが複雑で DTO に収まらない場合のみ、`{Domain}Mapper` クラスを `dto/` パッケージに作成する。

```java
// 複雑な変換が必要な場合のみ Mapper クラスを作成
@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final ProductRepository productRepository;

    public OrderResponse toResponse(Order order) {
        // 複雑な変換ロジック（DB 参照が必要な場合など）
        ...
    }
}
```

---

## 5. 認証・認可（Spring Security + JWT）

詳細は [SECURITY.md](../architecture/SECURITY.md) を参照。以下はコーディング上の規約を補足する。

### 5.1 SecurityConfig

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers(GET, "/api/v1/products/**", "/api/v1/shops/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .xssProtection(Customizer.withDefaults())
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailAuthEntryPoint())
                        .accessDeniedHandler(problemDetailAccessDeniedHandler()))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

### 5.2 現在のユーザー情報の取得

SecurityContext から現在のユーザーを取得するヘルパーを `common` に定義し、Service 層から利用する。

```java
// common/SecurityHelper.java
public final class SecurityHelper {

    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("認証情報が見つかりません");
        }
        KivioUserDetails userDetails = (KivioUserDetails) auth.getPrincipal();
        return userDetails.getUserId();
    }

    public static boolean hasRole(String role) {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private SecurityHelper() {}
}
```

### 5.3 メソッドレベル認可

```java
// Service 層でのロールチェック
@PreAuthorize("hasRole('ADMIN')")
public void approveSellerApplication(UUID id) { ... }

// Controller 層での所有者チェック（Service に委譲するパターンを推奨）
// Service の verifyOwnership() を呼び出す
```

### 5.4 JWT Filter の実装規約

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        extractToken(req)
                .filter(jwtTokenProvider::isValid)
                .map(jwtTokenProvider::toAuthentication)
                .ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));

        chain.doFilter(req, res);
    }

    private Optional<String> extractToken(HttpServletRequest req) {
        return Optional.ofNullable(req.getHeader(HttpHeaders.AUTHORIZATION))
                .filter(h -> h.startsWith("Bearer "))
                .map(h -> h.substring(7));
    }
}
```

### 5.5 RateLimitingFilter の実装規約

IP 特定には `request.getRemoteAddr()` を使用し、`X-Forwarded-For` ヘッダーを直接読まない（クライアントが偽装可能なため）。
リバースプロキシ配下にデプロイする場合は `application.yml` に `server.forward-headers-strategy=NATIVE` を設定することで、Spring が転送ヘッダーを安全に処理し `getRemoteAddr()` が正しいクライアント IP を返す。

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        // X-Forwarded-For は偽装可能なため getRemoteAddr() を使用する
        // application.yml に server.forward-headers-strategy=NATIVE を設定すれば
        // プロキシ越しでも正しいクライアント IP が得られる
        String clientIp = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(clientIp, k -> createBucket(request));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("rate_limit_exceeded ip={} uri={}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"https://kivio.io/problems/rate-limit-exceeded",
                     "title":"Rate Limit Exceeded","status":429,
                     "detail":"リクエスト数が上限に達しました。60秒後に再試行してください"}
                    """);
        }
    }

    private Bucket createBucket(HttpServletRequest request) {
        boolean isAuthEndpoint = request.getRequestURI().startsWith("/api/v1/auth/");
        int capacity = isAuthEndpoint ? 10 : 100;
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, Duration.ofMinutes(1))))
                .build();
    }
}
```

---

## 6. 例外処理（RFC 9457 ProblemDetail）

### 6.1 例外クラス階層

```
KivioException（abstract）
├── ResourceNotFoundException（404）
├── BusinessRuleException（422）
│   ├── OrderNotCancellableException
│   ├── InsufficientStockException
│   └── InvalidOrderStateException
├── ConflictException（409）
│   └── DuplicateEmailException
└── ExternalServiceException（502）
    ├── StripeException
    └── CloudinaryException
```

```java
// 基底例外クラス
public abstract class KivioException extends RuntimeException {

    private final String errorCode;

    protected KivioException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

// 404: リソース未発見
public class ResourceNotFoundException extends KivioException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " が見つかりません: " + id, "RESOURCE_NOT_FOUND");
    }
}

// 422: ビジネスルール違反の基底（サブクラスで具体的な例外を定義）
public abstract class BusinessRuleException extends KivioException {

    protected BusinessRuleException(String message, String errorCode) {
        super(message, errorCode);
    }
}

// 422: 具体的なビジネスルール違反
public class InsufficientStockException extends BusinessRuleException {

    public InsufficientStockException(UUID productId, int requested, int available) {
        super(String.format("在庫不足: productId=%s requested=%d available=%d",
                productId, requested, available), "INSUFFICIENT_STOCK");
    }
}

public class OrderNotCancellableException extends BusinessRuleException {

    public OrderNotCancellableException(UUID orderId, OrderStatus currentStatus) {
        super(String.format("注文をキャンセルできません: orderId=%s status=%s", orderId, currentStatus),
                "ORDER_NOT_CANCELLABLE");
    }
}

// 409: 競合
public class ConflictException extends KivioException {

    protected ConflictException(String message, String errorCode) {
        super(message, errorCode);
    }
}

public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException(String email) {
        super("このメールアドレスはすでに登録されています: " + email, "DUPLICATE_EMAIL");
    }
}
```

### 6.2 グローバル例外ハンドラー

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // リソース未発見
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setProperty("errorCode", ex.getErrorCode());
        return detail;
    }

    // ビジネスルール違反
    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        detail.setProperty("errorCode", ex.getErrorCode());
        return detail;
    }

    // Bean Validation エラー
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        detail.setProperty("errorCode", "VALIDATION_ERROR");
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                .toList());
        return detail;
    }

    // 想定外のエラー（ログに残してから返す）
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("unexpected_error path={} correlationId={}",
                req.getRequestURI(), MDC.get("correlationId"), ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "予期しないエラーが発生しました");
    }
}
```

**エラーレスポンス形式（RFC 9457）:**

```json
{
  "type": "https://kivio.io/problems/insufficient-stock",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "在庫が不足しています",
  "errorCode": "INSUFFICIENT_STOCK"
}
```

> **設定:** `application.yml` に `spring.mvc.problemdetails.enabled=true` を追加すると、Spring MVC のデフォルト例外（`HttpRequestMethodNotSupportedException`・`MethodNotAllowedException` 等）も自動的に ProblemDetail 形式で返される。カスタムハンドラーと共存可能。

---

## 7. Enum 規約

### 7.1 定義

```java
public enum OrderStatus {
    PENDING,
    PAYMENT_CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    // ビジネスロジックを Enum に持たせる
    public boolean isCancellable() {
        return this == PENDING || this == PAYMENT_CONFIRMED;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
```

### 7.2 DB 永続化

`@Enumerated(EnumType.STRING)` を使用する（`ORDINAL` は禁止）。

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 50)
private OrderStatus status;
```

`AttributeConverter` は、DB の格納値と Java の Enum 名が異なる場合のみ使用する。

```java
// DB に "active" / "inactive" で格納し、Java では ACTIVE / INACTIVE を使いたい場合
@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus status) {
        return status.getDbValue();
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbValue) {
        return UserStatus.fromDbValue(dbValue);
    }
}
```

### 7.3 JSON シリアライズ

デフォルトで `name()` がシリアライズされる。クライアントに返す文字列を変えたい場合のみ `@JsonValue` を使う。

```java
public enum ProductStatus {
    ACTIVE,
    INACTIVE,
    DELETED;

    // 通常はそのまま "ACTIVE" / "INACTIVE" / "DELETED" として返す（@JsonValue 不要）
}
```

---

## 8. JPA・エンティティ規約

### 8.1 BaseEntity

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

> **必須設定:** `@CreatedDate` / `@LastModifiedDate` を機能させるには、`@Configuration` クラスに `@EnableJpaAuditing` を付与する。
>
> ```java
> @Configuration
> @EnableJpaAuditing
> public class JpaConfig {}
> ```

### 8.2 SoftDeletableEntity

`users` / `shops` / `categories` に適用する。

```java
@MappedSuperclass
@Getter
@SQLRestriction("deleted_at IS NULL")   // SELECT 時に自動フィルタリング
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
```

**ソフトデリートの実装方式:**

| エンティティ | 方式 | 理由 |
|---|---|---|
| `users`, `shops`, `categories` | `deleted_at` + `@SQLRestriction` | 完全な論理削除。ID・メール等の一意制約に注意 |
| `products` | `status = 'DELETED'` | ステータスで管理。`@SQLRestriction` は適用しない |
| `orders`, `payments` | 削除操作なし | 会計記録のため物理・論理ともに削除禁止 |

### 8.3 N+1 問題の防止

```java
// NG: N+1 が発生するパターン
List<Order> orders = orderRepository.findAll();
orders.forEach(o -> log.info(o.getItems().size())); // items を取得するたびに SELECT が走る

// OK: JOIN FETCH で一括取得
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.buyer.id = :buyerId")
List<Order> findByBuyerIdWithItems(@Param("buyerId") UUID buyerId);

// OK: Projection で必要なカラムのみ取得（一覧表示など）
@Query("SELECT new io.kivio.domain.order.dto.OrderSummary(o.id, o.status, o.totalAmount) " +
       "FROM Order o WHERE o.buyer.id = :buyerId")
Page<OrderSummary> findSummariesByBuyerId(@Param("buyerId") UUID buyerId, Pageable pageable);
```

**ルール:**
- `@OneToMany` はデフォルトが LAZY なので変更不要。ただし意図を明示するため `fetch = FetchType.LAZY` を書くことを推奨する
- `@ManyToOne` / `@OneToOne` はデフォルトが **EAGER** のため、必ず `fetch = FetchType.LAZY` を明示する
- 詳細画面（単一リソース）: `JOIN FETCH` で必要な関連を一括取得
- 一覧画面: Projection または `JOIN FETCH` でカラムを絞る

### 8.4 楽観的ロック

在庫管理など同時更新が発生するエンティティに適用する。

```java
@Version
@Column(nullable = false)
private Long version;
```

---

## 9. API レスポンス規約

### 9.1 基本規則

| 項目 | 規則 |
|---|---|
| ベースパス | `/api/v1` |
| JSON フィールド名 | camelCase |
| 日時フォーマット | ISO 8601 UTC（例: `"2026-05-26T10:00:00Z"`）|
| 金額 | 整数・円単位（例: `1500`）|
| 部分更新 | 常に `PATCH`（`PUT` は使わない）|
| エラーコード | `UPPER_SNAKE_CASE`（例: `PRODUCT_OUT_OF_STOCK`）|

### 9.2 HTTP ステータスコード

| 操作 | 成功 | エラー |
|---|---|---|
| GET（単一リソース） | `200 OK` | `404 Not Found` |
| GET（リスト） | `200 OK` | - |
| POST（新規作成） | `201 Created` + `Location` ヘッダー | `422 Unprocessable Entity`（バリデーション）|
| PATCH（更新） | `200 OK` | `404`, `422` |
| DELETE（論理削除） | `204 No Content` | `404` |
| 認証エラー | - | `401 Unauthorized` |
| 認可エラー | - | `403 Forbidden` |
| レート制限超過 | - | `429 Too Many Requests` |

### 9.3 PageResponse

Spring の `Page<T>` を直接返さず、`PageResponse<T>` でラップする。

```java
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
```

---

## 10. ドメイン間通信

ドメイン間の直接 import は禁止。以下の2つの手段のみ使用する。

### 10.1 Application Service 経由（同期・ユースケース内）

同一ユースケース内で複数ドメインの操作が必要な場合、interface 経由で依存を逆転させる。

```java
// identity パッケージの Service が catalog の操作を必要とする場合
// → interface を common に定義し、catalog が実装する
// → identity は common の interface のみに依存する（catalog を直接 import しない）

// common/usecase/ に定義（どのドメインにも依存しない）
public interface ShopCreationUseCase {
    UUID createShop(UUID ownerId, String shopName);
}

// catalog の service/ が common の interface を実装
@Service
@RequiredArgsConstructor
public class ShopService implements ShopCreationUseCase {
    @Override
    public UUID createShop(UUID ownerId, String shopName) { ... }
}

// identity の service/ が common の interface を DI（catalog パッケージを import しない）
@Service
@RequiredArgsConstructor
public class SellerApplicationService {
    private final ShopCreationUseCase shopCreationUseCase; // common の interface
    ...
}
```

### 10.2 Spring Application Events（非同期・副作用）

状態変化の副作用（通知・監査外ログ・メール送信）には Spring Events を使用する。

```java
// イベントクラス: 発行側ドメインの domain/ に定義（record を使う）
public record SellerApplicationApprovedEvent(
        UUID applicationId,
        UUID applicantUserId,
        String shopName,
        String correlationId,   // MDC.get("correlationId") を渡す
        Instant occurredAt
) {}

// 発行側（identity ドメイン）
applicationEventPublisher.publishEvent(
        new SellerApplicationApprovedEvent(
                application.getId(),
                application.getApplicantId(),
                application.getShopName(),
                MDC.get("correlationId"),
                Instant.now()
        )
);

// 購読側（notification ドメイン） — identity パッケージを import しない
@EventListener
@Async
public void onSellerApproved(SellerApplicationApprovedEvent event) {
    MDC.put("correlationId", event.correlationId());
    try {
        notificationService.sendSellerApprovalNotification(event.applicantUserId());
    } finally {
        MDC.remove("correlationId");
    }
}
```

**`@EventListener` vs `@TransactionalEventListener` の使い分け:**

| アノテーション | タイミング | 使いどころ |
|---|---|---|
| `@EventListener` | イベント発行時（トランザクション中） | ドメインロジックが他のドメインに依存する場合 |
| `@TransactionalEventListener(phase = AFTER_COMMIT)` | トランザクションコミット後 | メール送信・通知など副作用（コミット前に失敗した場合に送らない）|

```java
// メール送信はコミット確定後に行う
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void onOrderPlaced(OrderPlacedEvent event) {
    emailSender.sendOrderConfirmation(event.buyerEmail(), event.orderId());
}
```

> **必須設定:** `@Async` を使うには `@Configuration` クラスに `@EnableAsync` を付与する。
>
> ```java
> @Configuration
> @EnableAsync
> public class AsyncConfig {}
> ```

---

## 11. 監査ログ（@Auditable AOP）

詳細は [AUDIT.md](../architecture/AUDIT.md) を参照。以下はコーディング上の規約を補足する。

### 11.1 @Auditable の使い方

```java
// 状態変更を伴う Service メソッドに付与する
@Auditable(
        action = "SELLER_APPLICATION_APPROVED",
        entityType = "SELLER_APPLICATION",
        entityIdParam = "applicationId",
        captureOldValue = true,
        captureNewValue = true
)
public void approve(UUID applicationId, UUID reviewerId) {
    // ビジネスロジックのみ記述。監査ログ記録は AOP が行う
}
```

**ルール:**
- 読み取り操作（GET 系）には `@Auditable` を付与しない
- `action` の命名: `{ENTITY}_{VERB}` の `UPPER_SNAKE_CASE`（例: `ORDER_CANCELLED`）
- バッチ処理など AOP が使えない場合は `AuditLogRepository` を直接 DI して記録する
- `audit_logs` テーブルへの UPDATE/DELETE は絶対禁止

---

## 12. データベース・マイグレーション規約（Flyway）

### 12.1 ファイル命名規則

```
V{version}__{description}.sql

例:
  V1__create_users_table.sql
  V2__create_shops_table.sql
  V10__add_index_to_products_shop_id.sql
```

**ルール:**
- バージョン番号は整数（例: `V1`, `V2`, `V10`）。小数点は使わない
- description はスネークケース（`__` の後に続く）
- 1マイグレーションファイルは1つの論理的変更に限定する（テーブル追加とインデックス追加は別ファイル）

### 12.2 後方互換マイグレーションの原則

```sql
-- OK: NULL 許容のカラム追加（既存レコードに影響しない）
ALTER TABLE products ADD COLUMN thumbnail_url VARCHAR(500);

-- OK: 新しいテーブルの追加
CREATE TABLE wishlists (...);

-- 注意: NOT NULL カラムの追加はデフォルト値を設定してから制約を追加する
ALTER TABLE products ADD COLUMN currency VARCHAR(3) DEFAULT 'JPY';
ALTER TABLE products ALTER COLUMN currency SET NOT NULL;

-- 注意: カラム削除・リネームは2ステップで行う（アプリコードとの同時変更を避ける）
-- Step 1: アプリコードでカラムを使わなくなってから削除マイグレーションを実行する
```

### 12.3 禁止事項

- 既存のマイグレーションファイルを編集することは禁止（チェックサムエラーが発生する）
- `flyway:clean` は本番環境で絶対に使わない（開発環境でのみ許可）

---

## 13. テスト規約（JUnit 5 + AssertJ）

詳細は [TEST_STRATEGY.md](./TEST_STRATEGY.md) を参照。以下は実装規約を補足する。

### 13.1 テスト種別と使用アノテーション

| 種別 | アノテーション | DB | 速度目安 |
|---|---|---|---|
| ドメインユニット | なし | なし | < 10ms |
| サービスユニット | `@ExtendWith(MockitoExtension.class)` | `@Mock` | < 50ms |
| Web Layer | `@WebMvcTest` | なし | < 500ms |
| Repository | `@DataJpaTest` + Testcontainers | PostgreSQL | 数秒（初回のみ） |
| 統合テスト | `@SpringBootTest` + Testcontainers | PostgreSQL | 数秒〜十数秒 |

### 13.2 ドメインユニットテスト

```java
class OrderTest {

    @Test
    void should_cancel_when_status_is_payment_confirmed() {
        Order order = Order.builder()
                .status(OrderStatus.PAYMENT_CONFIRMED)
                .build();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void should_throw_when_cancel_is_attempted_on_shipped_order() {
        Order order = Order.builder()
                .status(OrderStatus.SHIPPED)
                .build();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(OrderNotCancellableException.class);
    }
}
```

### 13.3 サービスユニットテスト

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks OrderService orderService;

    @Test
    void should_publish_event_when_order_is_cancelled() {
        Order order = buildCancellableOrder();
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        orderService.cancel(order.getId());

        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    }
}
```

### 13.4 Web Layer テスト

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProductService productService;

    @Test
    @WithMockUser(roles = "SELLER")
    void should_return_201_when_product_is_created() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .name("テスト商品").price(1000).build();
        given(productService.create(any())).willReturn(sampleProductResponse());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/products/")))
                .andExpect(jsonPath("$.name").value("テスト商品"));
    }

    @Test
    void should_return_401_when_not_authenticated() throws Exception {
        mockMvc.perform(post("/api/v1/products").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void should_return_422_when_price_is_negative() throws Exception {
        CreateProductRequest invalid = CreateProductRequest.builder()
                .name("商品").price(-1).build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("price"));
    }
}
```

### 13.5 統合テスト（`@SpringBootTest` + Testcontainers）

アプリケーション全体（Security Filter Chain 含む）を起動して実際の HTTP リクエストをテストする。

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
    }

    @Test
    void should_return_200_and_tokens_when_login_with_valid_credentials() throws Exception {
        // 事前にユーザー登録 → ログイン → トークン取得の一連フローをテスト
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"test@example.com","password":"Password123!"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void should_return_429_when_auth_rate_limit_exceeded() throws Exception {
        // 認証系エンドポイントは 10 req/min/IP
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login").content("{}"));
        }
        mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
```

**ルール:**
- `@ActiveProfiles("test")` を必ず付与し、テスト専用の `application-test.yml` 設定を使用する
- `@SpringBootTest` と `@WebMvcTest` は使い分ける: Security Filter Chain の検証が必要 → `@SpringBootTest`、Controller 単体の HTTP 仕様検証 → `@WebMvcTest`

---

### 13.6 Repository テスト（Testcontainers + 実 PostgreSQL）

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
    }

    @Autowired UserRepository userRepository;

    @Test
    void should_exclude_soft_deleted_users() {
        User active = createUser("active@example.com");
        User deleted = createUser("deleted@example.com");
        deleted.softDelete();
        userRepository.saveAll(List.of(active, deleted));

        List<User> result = userRepository.findAll();

        assertThat(result).containsExactly(active).doesNotContain(deleted);
    }
}
```

### 13.7 テスト命名規則

```
テストクラス: {テスト対象クラス名}Test.java
テストメソッド: should_{期待する結果}_when_{条件}

例:
  should_return_404_when_product_not_found()
  should_throw_InsufficientStockException_when_stock_is_zero()
  should_exclude_soft_deleted_users_from_findAll()
```

### 13.8 カバレッジ（JaCoCo）

`build.gradle` に JaCoCo を設定し、ライン カバレッジ 80% 以上を CI で強制する。

```groovy
// build.gradle
plugins {
    id 'jacoco'
}

jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
    // DTO・設定クラス・generated コードをカバレッジ対象から除外
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                '**/dto/**', '**/config/**', '**/KivioApplication*'
            ])
        }))
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80
            }
        }
    }
}

// test → jacocoTestReport → jacocoTestCoverageVerification の順で実行
check.dependsOn jacocoTestCoverageVerification
test.finalizedBy jacocoTestReport
```

実行コマンド: `./gradlew test jacocoTestReport jacocoTestCoverageVerification`

---

## 14. コーディング規約

### 14.1 命名規則

| 種類 | 規則 | 例 |
|---|---|---|
| クラス・インターフェース | PascalCase | `OrderService`, `PaymentGateway` |
| メソッド・フィールド | camelCase | `findById`, `totalAmount` |
| 定数 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE`, `JWT_EXPIRY_MINUTES` |
| パッケージ | 全小文字 | `io.kivio.domain.order` |
| テストクラス | `{対象}Test` | `OrderServiceTest` |
| テストメソッド | スネークケース | `should_cancel_when_status_is_confirmed` |

### 14.2 不変性の原則

```java
// フィールドは final を優先する
private final OrderRepository orderRepository;

// 変数の再代入を避ける
// NG
String result = "";
if (condition) result = "A";
else result = "B";

// OK
String result = condition ? "A" : "B";

// コレクションは変更不可で返す
public List<OrderItem> getItems() {
    return Collections.unmodifiableList(items);
}
```

### 14.3 Optional の使い方

```java
// OK: find* メソッドの戻り値
Optional<Product> findById(UUID id);

// OK: map / flatMap / orElseThrow でチェーン
return productRepository.findById(id)
        .map(ProductResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("Product", id));

// NG: フィールドに Optional を使わない
private Optional<String> name; // フィールドには使わない

// NG: isPresent() + get() のペアを使わない
if (opt.isPresent()) return opt.get(); // orElseThrow / map で代替する
```

### 14.4 Stream の使い方

```java
// OK: 変換・フィルタリングに使う
List<ProductResponse> responses = products.stream()
        .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
        .map(ProductResponse::from)
        .toList();  // Java 16+ の toList()（不変リスト）

// NG: 副作用を Stream の中に書かない
products.stream().forEach(p -> p.publish()); // NG: 副作用は for-each で書く
for (Product p : products) p.publish();      // OK
```

### 14.5 ログ規約

```java
@Slf4j  // Lombok でフィールドを生成

// 構造化ログ: 重要な識別子をキー=値形式で付ける
log.info("product_created id={} shopId={} price={}", id, shopId, price);
log.error("payment_failed orderId={} reason={}", orderId, reason, ex);

// 禁止: 文字列結合でログを作らない（パフォーマンス・構造化ができない）
log.info("商品を作成しました id=" + id); // NG

// 禁止: 機密情報をログに記録しない（パスワード・JWT・クレカ番号・個人情報等）
log.info("login email={} password={}", email, password); // NG
log.info("login email={}", email);                        // OK

// 禁止: 例外を握り潰す
try {
    ...
} catch (Exception e) {
    // 何も書かない ← NG
}
```

### 14.6 設定値の外部化

```java
// OK: application.yml に定義し、@ConfigurationProperties で型安全に読む
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessTokenExpiryMinutes, long refreshTokenExpiryDays) {}

// NG: ハードコードしない
private static final String SECRET = "my-secret-key"; // NG
private static final long EXPIRY = 15 * 60 * 1000;    // NG
```

### 14.7 コメント規約

**原則:** 自明な処理にコメントを書かない。コードが「何をするか」は命名で伝え、コメントは「なぜその実装にしたか」を伝えるためにのみ使う。

#### ENUM・Domain・DTO のプロパティ

ENUM 値・エンティティフィールド・DTO フィールド（Request / Response 両方）にはすべて `/** */` で **DATA_DICTIONARY の論理名**を付ける。説明文や制約条件は書かない。ENUM 値は DATA_DICTIONARY の「概要/備考」欄に記載されている論理名を使う。

```java
// ENUM 値：DATA_DICTIONARY の概要/備考欄の論理名をそのまま書く
public enum OrderStatus {
    /** 注文中 */
    PENDING,
    /** 支払い済み */
    PAYMENT_CONFIRMED,
    /** 発送済み */
    SHIPPED,
    /** 受取済み */
    DELIVERED,
    /** キャンセル済み */
    CANCELLED;
}

// Domain Entity フィールド
@Entity
public class Product extends BaseEntity {
    /** 商品名 */
    private String name;

    /** 価格 */
    private int price;

    /** ステータス */
    @Enumerated(EnumType.STRING)
    private ProductStatus status;
}

// Request DTO フィールド
@Getter
@Builder
public class CreateProductRequest {
    /** 商品名 */
    @NotBlank
    @Size(max = 100)
    private String name;

    /** 価格 */
    @Positive
    private int price;
}

// Response DTO フィールド（record）
public record ProductResponse(
        /** 商品ID */
        UUID id,
        /** 商品名 */
        String name,
        /** 価格 */
        int price,
        /** ステータス */
        String status
) {}
```

#### メソッドの Javadoc

公開メソッドの戻り値・例外が名前だけでは非自明な場合のみ `/** */` を記述する。記述する場合は `@param` / `@return` / `@throws` タグを使う。

```java
// OK: 戻り値と例外が非自明
/**
 * 指定された商品を論理削除する（status = DELETED に変更）。
 *
 * @throws ResourceNotFoundException 商品が存在しない場合
 * @throws AccessDeniedException 呼び出しユーザーがショップオーナーでない場合
 */
public void delete(UUID productId) { ... }

// NG: メソッド名から自明なので Javadoc 不要
/** 全商品を取得する */
public PageResponse<ProductResponse> findAll(int page, int size) { ... }
```

#### インラインコメント（`//`）

複雑なビジネスロジックや非自明な制約には日本語でインラインコメントを書く。「何をするか」ではなく「なぜそうするか」を書く。

```java
// OK: ビジネスルールの理由
if (order.getStatus() == OrderStatus.SHIPPED) {
    // 出荷後はキャンセル不可（配送業者への取り消し連絡が必要になるため）
    throw new OrderNotCancellableException(order.getId(), order.getStatus());
}

// OK: 非自明な技術的制約
// SHA-256 ハッシュを保存し、生トークンはレスポンス後に保持しない（盗用防止）
String hashedToken = DigestUtils.sha256Hex(rawToken);

// NG: コードを読めばわかる自明な処理
// 注文をキャンセルする
order.cancel();
```

#### `@Deprecated` の書き方

削除予定のメソッドには `@Deprecated` + Javadoc で移行先を明示する。

```java
/**
 * @deprecated {@link #findByIdWithDetails(UUID)} を使用してください。
 */
@Deprecated(forRemoval = true)
public Optional<Product> findById(UUID id) { ... }
```

#### 禁止パターン

| 禁止 | 代替・理由 |
|---|---|
| `/* ... */` ブロックコメント | `//` を使う |
| コードをコメントアウトして残す | 削除する（`git` で復元可能）|
| 変更履歴コメント（`// 2026-05-01 修正: ...`）| `git log` / `git blame` が代替 |
| 自明なコメント（`// ゲッターを呼ぶ`）| 削除する |

---

### 14.8 禁止事項まとめ

| 禁止事項 | 代替手段 |
|---|---|
| `@Data`（Lombok） | `@Getter + @Builder` |
| `@Setter`（Lombok） | ドメインメソッドで状態変更 |
| `@Autowired`（フィールド注入） | コンストラクタ注入（`@RequiredArgsConstructor`） |
| static mutable state | DI でシングルトン Bean として管理 |
| `catch (Exception e) {}` の握り潰し | ログに残してから再スロー or ドメイン例外に変換 |
| `Page<T>` を直接返す | `PageResponse<T>` でラップ |
| `FetchType.EAGER`（`@ManyToOne` / `@OneToOne` のデフォルト） | `fetch = FetchType.LAZY` を明示 + 必要時に `JOIN FETCH` |
| 文字列で日時を扱う | `Instant` / `OffsetDateTime` を使う |
| ハードコードされた設定値 | `application.yml` + `@ConfigurationProperties` |
| `@Enumerated(EnumType.ORDINAL)` | `@Enumerated(EnumType.STRING)` |

---

## 15. 実装チェックリスト

### 新規エンドポイント追加時

- [ ] `@RequestMapping` のパスが `/api/v1` から始まる
- [ ] リクエストボディに `@Valid` が付いている
- [ ] POST の場合、`201 Created` + `Location` ヘッダーを返している
- [ ] 更新操作が `PATCH` を使っている（`PUT` を使っていない）
- [ ] ロール認可が `@PreAuthorize` で設定されている
- [ ] 所有者チェックが Service 層に実装されている
- [ ] Swagger の `@Tag` / `@Operation` が付いている

### 新規ドメイン（パッケージ）追加時

- [ ] `controller/` `service/` `domain/` `repository/` `dto/` の5ディレクトリ構成になっている
- [ ] 他ドメインの直接 import がない
- [ ] クロスドメイン操作が interface 経由または Spring Events 経由になっている
- [ ] 集約ルートに対して Repository が1つだけ定義されている
- [ ] 状態変更がドメインメソッド経由になっている（Setter 経由でない）

### 新規 DB マイグレーション追加時

- [ ] ファイル名が `V{version}__{description}.sql` の形式になっている
- [ ] 後方互換の変更になっている（NOT NULL 追加の場合はデフォルト値が設定されている）
- [ ] `./gradlew test` でマイグレーションが正常に完了することを確認した
- [ ] 既存のマイグレーションファイルを編集していない

### セキュリティ変更時

- [ ] 公開エンドポイントを追加した場合、`SecurityConfig` の `permitAll()` リストを更新した
- [ ] JWT の claims に機密情報（パスワード等）を含めていない
- [ ] レート制限の対象に含めた（認証系は 10 req/min/IP）
- [ ] 429 レスポンスに `Retry-After: 60` ヘッダーが付いている
- [ ] IP 特定が `request.getRemoteAddr()` を使っている（`X-Forwarded-For` を直接読んでいない）
- [ ] `./gradlew test` でセキュリティ関連テストがすべて通ることを確認した

### PR レビュー前の確認

- [ ] `./gradlew build` が成功する
- [ ] N+1 クエリが発生していないことを確認した（ログの SQL 件数を確認）
- [ ] 例外を握り潰している `catch` ブロックがない
- [ ] ハードコードされた設定値がない
- [ ] `@Data` / `@Setter` を使っていない
- [ ] ログに機密情報（パスワード・JWT・個人情報）が含まれていない
