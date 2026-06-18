# 引き継ぎメモ: OtpService / RegistrationSessionService 単体テスト（方法B: Testcontainers）

**作成日:** 2026-06-17
**目的:** devcontainer リビルドでチャット履歴が消えても作業を継続できるようにするための引き継ぎメモ。
**関連:** `docs/implementation-plans/auth.md` §9.1「OtpService / RegistrationSessionService 単体テスト」（チェックリスト 554〜561 行・未実施）

---

## 0. これは何のメモか

`OtpService` / `RegistrationSessionService` の専用単体テストが未実装（implementation-plans/auth.md の Test Checklist で唯一残っている穴）。
これらは Redis の実挙動（TTL・SHA-256 ハッシュ保存・attempts 加算・キー削除・スロットリング）を検証したいので、
**方法B = Testcontainers の実 Redis** で書く方針に決定済み。

ただし元の環境には罠があった：

| 環境 | Java/Gradle | Docker デーモン |
|---|---|---|
| devcontainer の中 | ✅ | ❌（ソケット未マウント） |
| ホスト（Docker Desktop） | ❌ | ✅ |

→ Java と Docker が別の場所にあり、Testcontainers がどこでも走らなかった。
これを解消するため **Docker-outside-of-Docker（DooD）** を devcontainer に設定した（下記 §1）。

---

## 1. 完了済み: DooD セットアップ（このリビルドの目的）

ホストの Docker Desktop を devcontainer 内の JVM から使えるようにした。変更ファイルは3つ：

1. **`.devcontainer/Dockerfile`**
   - Docker CLI（`docker-ce-cli`）を追加。※ Testcontainers 自体はソケット直叩きで CLI 不要。`docker ps` 等のデバッグ用。

2. **`.devcontainer/docker-compose.yml`**（`app` サービス）
   - `volumes` に `- /var/run/docker.sock:/var/run/docker.sock` を追加（ホストの Docker デーモン共有）
   - `extra_hosts: - "host.docker.internal:host-gateway"` を追加
   - `environment` に `TESTCONTAINERS_HOST_OVERRIDE: host.docker.internal` を追加
     → Testcontainers が起動する兄弟コンテナはホスト側に立ち、公開ポートはホストにマップされる。
       devcontainer 内の `localhost` ではなく `host.docker.internal` 経由で到達する必要があるため。

3. **`.devcontainer/devcontainer.json`**
   - `postStartCommand: "sudo chmod 666 /var/run/docker.sock || true"` を追加
     → 起動ごとにソケット権限を開放（vscode / ubuntu どちらのユーザーでもアクセス可能にする）。

### リビルド後の動作確認（最初にやること）

```bash
# 1. devcontainer 内から Docker デーモンに到達できるか
docker info        # → Server 情報が出れば成功（出なければ §4 トラブルシュート）
docker ps          # → ホストの起動中コンテナが見える

# 2. Testcontainers が使えるか（実テスト前の疎通確認）
docker run --rm hello-world
```

`docker info` が「Cannot connect to the Docker daemon」になる場合は §4 を参照。

---

## 2. TODO: テスト実装（リビルド後の本作業）

対象ファイル（新規作成）:
- `kivio-backend/src/test/java/io/kivio/domain/identity/service/OtpServiceTest.java`
- `kivio-backend/src/test/java/io/kivio/domain/identity/service/RegistrationSessionServiceTest.java`

### 2.1 推奨アプローチ: Redis のみの軽量 Testcontainers テスト

フルコンテキスト（`IntegrationTestBase` は Postgres + Web 全部起動）は重いので、
**Redis コンテナだけ起動して、サービスを手で組み立てる**のが最適。Spring コンテキスト不要・TTL 等の設定値もテストから自由に渡せる。

```java
package io.kivio.domain.identity.service;

import io.kivio.config.AuthProperties;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

@Testcontainers(disabledWithoutDocker = true) // Docker 無し環境では自動スキップ（既存方針と統一）
class OtpServiceTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private StringRedisTemplate redis;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        // getHost() は TESTCONTAINERS_HOST_OVERRIDE により host.docker.internal を返す（DooD）
        LettuceConnectionFactory cf =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll(); // 各テスト前にクリア

        AuthProperties props = new AuthProperties(
                new AuthProperties.Otp(6, Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5),
                new AuthProperties.RegistrationSession(Duration.ofMinutes(30)));
        otpService = new OtpService(redis, props);
    }
    // ... テストケース（§2.2）
}
```

> 代替案: `IntegrationTestBase` を継承して `@Autowired OtpService` + `@Autowired StringRedisTemplate` を使う方法もある。
> ただし Postgres も起動し設定値が test プロファイル固定になるので、上記の軽量版を推奨。

### 2.2 OtpServiceTest のケース（auth.md 554〜560 行）

実 Redis なので「呼んだか」ではなく「実際にどうなったか」をアサートする。

- [ ] `issue`: 戻り値の平文 OTP は6桁数字。`reg:otp:{email}` に `otpHash`（= `TokenHashUtils.sha256Hex(otp)`）と `attempts="0"` が入り、`redis.getExpire(key)` が ~600 秒（TTL 10分）。
- [ ] `issue`（クールダウン）: 連続2回 `issue` → 2回目は `OtpResendThrottledException`（`reg:otp:cooldown:{email}` 存在のため）。
- [ ] `issue`（1時間上限）: クールダウンを跨ぐため、各 `issue` の前に `redis.delete("reg:otp:cooldown:"+email)` でクールダウンキーを消しつつ繰り返す。`maxPerHour`(=5) 超過の6回目で `OtpResendThrottledException`。
- [ ] `verify`（成功）: `issue` の戻り OTP で `verify` → 例外なし、`reg:otp:{email}` が削除される（`redis.hasKey` が false）。
- [ ] `verify`（不一致）: 誤 OTP → `OtpInvalidException`、`attempts` が 1 に加算される。
- [ ] `verify`（期限切れ/未発行）: `issue` せず `verify` → `OtpExpiredException`。
- [ ] `verify`（試行上限）: `issue` 後に誤 OTP で `verify` を**6回**呼ぶ。attempts は 0→…→5 と増え、6回目の入口で `attempts>=5` 判定 → `OtpMaxAttemptsExceededException` かつキー失効。
      （1〜5回目は `OtpInvalidException`。6回目で MaxAttempts。下記「試行回数ロジック」参照）

**試行回数ロジック（OtpService.verify）:** 入口で `attempts >= maxAttempts` なら失効。一致しなければ `attempts` を +1 して `OtpInvalidException`。
→ maxAttempts=5 のとき、誤り1〜5回目は Invalid（attempts が 1..5 になる）、6回目の入口で 5>=5 となり MaxAttemptsExceeded + キー削除。

### 2.3 RegistrationSessionServiceTest のケース（auth.md 561 行）

```java
AuthProperties props = new AuthProperties(
        new AuthProperties.Otp(6, Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5),
        new AuthProperties.RegistrationSession(Duration.ofMinutes(30)));
RegistrationSessionService sessionService = new RegistrationSessionService(redis, props);
```

- [ ] `create` → `consume`: `create(email)` が UUID 形式の token を返し、`reg:session:{token}` に email が入り `getExpire` が ~1800 秒（30分）。`consume(token)` が同じ email を返す。
- [ ] ワンタイム性: `consume` 後にキーが消え、同じ token で再度 `consume` すると `RegistrationSessionInvalidException`。
- [ ] 無効トークン: 存在しない token で `consume` → `RegistrationSessionInvalidException`。

### 2.4 参照すべき実装・既存テスト

- 被テスト: `kivio-backend/src/main/java/io/kivio/domain/identity/service/OtpService.java`
- 被テスト: `.../service/RegistrationSessionService.java`
- 設定: `.../config/AuthProperties.java`（record。`Otp(length, ttl, maxAttempts, resendCooldown, maxPerHour)` / `RegistrationSession(ttl)`）
- ハッシュ: `.../service/TokenHashUtils.java`（`sha256Hex` — 期待ハッシュ計算に使用）
- 例外: `.../exception/`（`OtpInvalidException` / `OtpExpiredException` / `OtpMaxAttemptsExceededException` / `OtpResendThrottledException` / `RegistrationSessionInvalidException`）
- 既存パターン: `src/test/java/io/kivio/support/IntegrationTestBase.java`（Testcontainers + `disabledWithoutDocker=true` の書き方）
- 既存パターン: `src/test/java/io/kivio/domain/identity/service/AuthServiceTest.java`（テストの書き味・命名 `should_...`）
- 間接カバーしている統合テスト: `src/test/java/io/kivio/domain/identity/controller/AuthControllerIntegrationTest.java`

---

## 3. 実装後にやること

```bash
cd /workspace/kivio-backend
./gradlew test --tests "io.kivio.domain.identity.service.OtpServiceTest" \
               --tests "io.kivio.domain.identity.service.RegistrationSessionServiceTest"
./gradlew build   # 全体グリーン確認
```

そのうえで **`docs/implementation-plans/auth.md` を更新**:
- §9.1 のチェックリスト 554〜561 行の `[ ]` を `[x]` にする。
- 550〜552 行の HTML コメント（「専用の単体テストが存在しない…」）を削除 or 「実装済み」に更新する。
- 必要なら 447 行 T-10 のステータス補足に単体テスト追加を反映。

---

## 4. トラブルシュート（DooD）

- **`docker info` が「Cannot connect to the Docker daemon」**
  → `ls -l /var/run/docker.sock` で存在と権限を確認。権限不足なら `sudo chmod 666 /var/run/docker.sock`。
    マウント自体が無い場合は compose の volume 反映漏れ → devcontainer を完全リビルド。
- **Testcontainers が起動するが接続タイムアウト**
  → `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` が効いているか `env | grep TESTCONTAINERS` で確認。
    `getHost()` が `localhost` を返すと DooD では到達できない。
- **`host.docker.internal` が解決できない**
  → compose の `extra_hosts: host.docker.internal:host-gateway` が反映されているか確認（要リビルド）。
- **Ryuk（リソース掃除コンテナ）でエラー**
  → 最終手段として環境変数 `TESTCONTAINERS_RYUK_DISABLED=true` を compose に追加（掃除は手動 `docker ps -a` で）。
- **Docker Desktop 側でソケットが見えない（Mac）**
  → Docker Desktop の Settings → Advanced で "Allow the default Docker socket to be used" を有効化。

---

## 5. 補足: なぜ方法B（実 Redis）か（方法A との比較・決定理由）

`OtpService` の検証対象は「TTL が付くか」「5回でキーが消えるか」「SHA-256 で保存されるか」という **Redis の実挙動そのもの**。
Mockito モック（方法A）だと `redis.expire(...)` を呼んだことを `verify()` するだけになり、TTL の実値や失効が本当に効くかは検証できない（テストが実装の写経になる）。
よって実 Redis（方法B）を採用。`disabledWithoutDocker=true` なので Docker の無い環境では自動スキップされ、CI（Docker あり）と DooD 化した devcontainer で実行される。
