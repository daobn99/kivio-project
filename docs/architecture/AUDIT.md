# 監査ログアーキテクチャ

> 参照: [OVERVIEW.md](./OVERVIEW.md)、[../design/DB_DESIGN.md § 3.21](../design/DB_DESIGN.md)、`REQUIREMENTS.md § 4.7`

---

## 目次

1. [@Auditable AOP 設計](#1-auditable-aop-設計)
2. [correlation_id の伝搬](#2-correlation_id-の伝搬)
3. [audit_logs 書き込みルール](#3-audit_logs-書き込みルール)
4. [記録対象イベント一覧](#4-記録対象イベント一覧)
5. [実装パターン](#5-実装パターン)

---

## 1. @Auditable AOP 設計

ビジネスロジックに監査コードを混在させず、Spring AOP（`@Aspect`）でサービス層に横断的に実装する。

### 1.1 動作フロー

```
HTTP Request
    │
    ▼
CorrelationIdFilter ── MDC に correlation_id を設定
    │
    ▼
Controller
    │
    ▼
Application Service（@Auditable アノテーション付きメソッド）
    │
    ├── AuditLogAspect（Around Advice）が起動
    │   │
    │   ├── ① SecurityContext から actor 情報を取得
    │   │   （actor_id, actor_role, actor_email）
    │   │
    │   ├── ② MDC から correlation_id を取得
    │   │
    │   ├── ③ old_value が必要な場合はメソッド実行前に取得
    │   │
    │   ├── ④ 元のメソッドを実行（proceed）
    │   │
    │   ├── ⑤ new_value が必要な場合はメソッド実行後に取得
    │   │
    │   └── ⑥ audit_logs に INSERT（outcome=SUCCESS）
    │       ※ 例外発生時は outcome=FAILURE + error_message を記録
    │
    ▼
Repository → DB
```

### 1.2 アノテーション定義

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();                     // 例: "USER_REGISTERED"
    String entityType() default "";     // 例: "USER"
    String entityIdParam() default "";  // old/new value 取得に使うメソッド引数名（例: "applicationId"）
    boolean captureOldValue() default false;
    boolean captureNewValue() default false;
}
```

### 1.3 使用例

```java
// Application Service でのアノテーション適用
@Service
@Transactional
public class SellerApplicationService {

    @Auditable(
        action = "SELLER_APPLICATION_APPROVED",
        entityType = "SELLER_APPLICATION",
        entityIdParam = "applicationId",   // この引数で集約を特定して old/new value を取得
        captureOldValue = true,
        captureNewValue = true
    )
    public void approve(UUID applicationId, UUID reviewerId) {
        // 通常のビジネスロジックのみ記述
        // 監査ログの記録は AOP が自動で行う
        SellerApplication application = repository.findById(applicationId)
            .orElseThrow(ResourceNotFoundException::new);
        application.approve(reviewerId);
        repository.save(application);
    }
}
```

### 1.4 Aspect 実装概要

```java
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        String correlationId = MDC.get("correlationId");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // actor 情報を取得（未認証の場合は null）
        UUID actorId = extractUserId(auth);
        String actorRole = extractRole(auth);
        String actorEmail = extractEmail(auth);

        // old_value の取得（設定されている場合）
        Object oldValue = auditable.captureOldValue()
            ? captureEntityState(pjp)
            : null;

        String outcome = "SUCCESS";
        String errorMessage = null;
        Object result = null;

        try {
            result = pjp.proceed();
        } catch (Exception e) {
            outcome = "FAILURE";
            errorMessage = e.getMessage();
            throw e; // 例外を再スローして通常の例外処理に委ねる
        } finally {
            // audit_logs への INSERT（成功・失敗どちらでも記録）
            auditLogRepository.save(AuditLog.builder()
                .correlationId(UUID.fromString(correlationId))
                .actorId(actorId)
                .actorRole(actorRole)
                .actorEmail(actorEmail)
                .action(auditable.action())
                .entityType(auditable.entityType())
                .outcome(outcome)
                .errorMessage(errorMessage)
                .ipAddress(getCurrentIpAddress())
                .build()
            );
        }
        return result;
    }
}
```

---

## 2. correlation_id の伝搬

### 2.1 生成タイミング

リクエスト受信の最初のフィルターで生成し、リクエスト終了時に MDC をクリアする。

```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        // クライアントからの相関IDを受け入れるか、なければ新規生成
        String correlationId = Optional
            .ofNullable(request.getHeader(CORRELATION_ID_HEADER))
            .filter(id -> !id.isBlank())
            .orElse(UUID.randomUUID().toString());

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);  // リクエスト終了時に必ずクリア
        }
    }
}
```

### 2.2 非同期処理での伝搬

`@Async` メソッドや Spring Events の非同期ハンドラでは MDC が引き継がれないため、イベントオブジェクトに `correlationId` を含めて渡す。

```java
// イベントに correlationId を含める
public record SellerApplicationApprovedEvent(
    UUID applicationId,
    UUID applicantUserId,
    String shopName,
    String correlationId,  // MDC.get("correlationId") を設定
    Instant occurredAt
) {}

// 購読側で MDC に復元
@EventListener
public void handle(SellerApplicationApprovedEvent event) {
    MDC.put("correlationId", event.correlationId());
    try {
        // 処理...
    } finally {
        MDC.remove("correlationId");
    }
}
```

### 2.3 correlation_id の活用

同一リクエスト内で複数の audit_logs レコードが生成される場合、すべてに同一の `correlation_id` が付与される。これにより、1回のリクエスト内の操作系列を追跡できる。

```sql
-- 同一リクエストの監査ログをまとめて確認
SELECT action, entity_type, entity_id, outcome, created_at
FROM audit_logs
WHERE correlation_id = 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
ORDER BY created_at;
```

---

## 3. audit_logs 書き込みルール

### 3.1 絶対ルール

| ルール | 理由 |
|---|---|
| **INSERT のみ許可（UPDATE/DELETE 禁止）** | 監査ログの改ざん防止 |
| 読み取り系（GET）操作は記録しない | ノイズ排除とパフォーマンス維持 |
| `actor_role` / `actor_email` は操作時点のスナップショット | ユーザー情報変更後も操作時点の記録を保持 |
| `actor_id` は nullable（バッチ・システム処理は null） | 自動処理での柔軟な記録 |

### 3.2 テーブルスキーマ（主要カラム）

```
audit_logs
├── id              BIGINT（シーケンス、パーティション管理のため）
├── correlation_id  UUID（MDC 経由のリクエスト ID）
├── actor_id        UUID? （null = バッチ/システム処理）
├── actor_role      VARCHAR? （操作時点のスナップショット）
├── actor_email     VARCHAR? （操作時点のスナップショット）
├── action          VARCHAR（UPPER_SNAKE_CASE、例: ORDER_CANCELLED）
├── entity_type     VARCHAR?（USER / ORDER / PRODUCT 等）
├── entity_id       UUID?
├── outcome         VARCHAR（SUCCESS / FAILURE）
├── ip_address      INET?
├── old_value       JSONB?（変更前の状態）
├── new_value       JSONB?（変更後の状態）
├── error_message   TEXT?（FAILURE 時のエラー詳細）
└── created_at      TIMESTAMPTZ（パーティションキー）
```

`DB_DESIGN.md § 3.21` に完全な DDL を記載。

### 3.3 パーティション管理

`created_at` による月別レンジパーティショニング。

```
保持期間:
  0〜1年   → PostgreSQL（月別パーティション、即時検索可）
  1〜3年   → S3 / GCS アーカイブ（Phase 5 以降）
  3年超    → 削除

月次バッチで翌月パーティションを事前作成:
  AuditLogPartitionJob: 毎月25日 04:00 に翌月分を CREATE
```

---

## 4. 記録対象イベント一覧

| カテゴリ | アクション名 | トリガー | old/new 値 |
|---|---|---|---|
| **認証** | `USER_REGISTERED` | 新規会員登録完了 | - |
| 認証 | `USER_EMAIL_VERIFIED` | メールアドレス確認完了 | - |
| 認証 | `USER_LOGGED_IN` | ログイン成功 | - |
| 認証 | `USER_LOGIN_FAILED` | ログイン失敗 | - |
| 認証 | `USER_LOGGED_OUT` | ログアウト | - |
| 認証 | `USER_PASSWORD_CHANGED` | パスワード変更 | - |
| **管理者操作** | `SELLER_APPLICATION_APPROVED` | セラー申請承認 | status の変更前後 |
| 管理者操作 | `SELLER_APPLICATION_REJECTED` | セラー申請却下 | status の変更前後 |
| 管理者操作 | `USER_DEACTIVATED` | ユーザーアカウント無効化 | status の変更前後 |
| 管理者操作 | `USER_ACTIVATED` | ユーザーアカウント有効化 | status の変更前後 |
| 管理者操作 | `PRODUCT_FORCEFULLY_DEACTIVATED` | 管理者による商品強制非公開 | status の変更前後 |
| 管理者操作 | `PLATFORM_CONFIG_UPDATED` | 手数料率等の設定変更 | config_value の変更前後 |
| **重要状態変更** | `ORDER_CANCELLED` | 注文キャンセル | order status の変更前後 |

### アクション名命名規則

```
{ENTITY}_{VERB}
例: USER_REGISTERED, ORDER_CANCELLED, PLATFORM_CONFIG_UPDATED

ENTITY: 大文字スネークケース（USER, ORDER, PRODUCT, SELLER_APPLICATION 等）
VERB:   過去形（REGISTERED, LOGGED_IN, APPROVED, CANCELLED, UPDATED 等）
```

---

## 5. 実装パターン

### 5.1 バッチ処理の記録

バッチジョブ（自動処理）は `actor_id = null` で記録する。

```java
// バッチ処理での監査ログ記録例
@Scheduled(cron = "0 0 3 * * SUN")
@Transactional
public void anonymizeExpiredUsers() {
    List<UUID> targets = userRepository.findUsersToAnonymize(
        LocalDateTime.now().minusDays(90)
    );

    targets.forEach(userId -> {
        userRepository.anonymize(userId);

        // バッチは @Auditable が使えないため直接 INSERT
        auditLogRepository.save(AuditLog.builder()
            .correlationId(UUID.randomUUID())  // バッチ実行ごとに生成
            .actorId(null)                     // バッチ = null
            .action("USER_ANONYMIZED")
            .entityType("USER")
            .entityId(userId)
            .outcome("SUCCESS")
            .build()
        );
    });
}
```

### 5.2 @Auditable を使わない場合

Spring Events 経由の非同期処理や、AOP で取得しにくい情報が必要な場合は `AuditLogRepository` を直接 DI して記録する。ただし、ビジネスロジックと監査ログが混在しないようにメソッドを分離する。

### 5.3 old_value / new_value の記録

`captureOldValue = true` の場合、AOP が元のメソッドを呼ぶ前にエンティティの現在状態を JSON にシリアライズして保存する。フィールドの取得方法はアノテーションに `entityIdParam` を指定して決定する。

```java
@Auditable(
    action = "SELLER_APPLICATION_APPROVED",
    entityType = "SELLER_APPLICATION",
    entityIdParam = "applicationId",  // この引数名を手がかりに集約を特定
    captureOldValue = true,
    captureNewValue = true
)
public void approve(UUID applicationId, UUID reviewerId) { ... }

// AOP 内での old_value 取得の流れ:
// 1. entityIdParam = "applicationId" からメソッド引数の値（UUID）を取得
// 2. entityType = "SELLER_APPLICATION" からリポジトリを特定
// 3. findById でエンティティをロードし JSON シリアライズ → old_value に設定
// 4. メソッド実行後、同様に new_value を取得
```
