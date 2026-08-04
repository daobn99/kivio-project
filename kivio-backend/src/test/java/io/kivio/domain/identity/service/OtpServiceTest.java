package io.kivio.domain.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kivio.config.AuthProperties;
import io.kivio.domain.identity.exception.OtpExpiredException;
import io.kivio.domain.identity.exception.OtpInvalidException;
import io.kivio.domain.identity.exception.OtpMaxAttemptsExceededException;
import io.kivio.domain.identity.exception.OtpResendThrottledException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link OtpService} の単体テスト。実 Redis（Testcontainers）に対して TTL・SHA-256 保存・
 * attempts 加算・キー失効・スロットリングといった「実挙動」を検証します。
 *
 * <p>Spring コンテキストを起動せず、Redis コンテナへ直接つないだ {@link StringRedisTemplate} で
 * サービスを手組みします（設定値もテストから自由に渡せる軽量構成）。
 */
@Testcontainers(disabledWithoutDocker = true) // Docker 無し環境では自動スキップ（既存方針と統一）
class OtpServiceTest {

    private static final String EMAIL = "otp-test@example.com";
    private static final String OTP_KEY = "reg:otp:" + EMAIL;
    private static final String COOLDOWN_KEY = "reg:otp:cooldown:" + EMAIL;

    @SuppressWarnings("resource")
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

    @Test
    void should_store_hash_attempts_and_ttl_when_issue() {
        String otp = otpService.issue(EMAIL);

        assertThat(otp).matches("\\d{6}");
        assertThat(redis.<String, String>opsForHash().get(OTP_KEY, "otpHash"))
                .isEqualTo(TokenHashUtils.sha256Hex(otp));
        assertThat(redis.<String, String>opsForHash().get(OTP_KEY, "attempts")).isEqualTo("0");
        assertThat(redis.getExpire(OTP_KEY)).isBetween(590L, 600L); // TTL 10分
    }

    @Test
    void should_throw_OtpResendThrottledException_when_issue_during_cooldown() {
        otpService.issue(EMAIL);

        assertThatThrownBy(() -> otpService.issue(EMAIL))
                .isInstanceOf(OtpResendThrottledException.class);
    }

    @Test
    void should_throw_OtpResendThrottledException_when_hourly_limit_exceeded() {
        // クールダウンを跨ぐため、各 issue 前にクールダウンキーを消す。maxPerHour=5。
        for (int i = 0; i < 5; i++) {
            redis.delete(COOLDOWN_KEY);
            otpService.issue(EMAIL);
        }
        redis.delete(COOLDOWN_KEY);

        assertThatThrownBy(() -> otpService.issue(EMAIL))
                .isInstanceOf(OtpResendThrottledException.class);
    }

    @Test
    void should_pass_and_delete_key_when_verify_with_correct_otp() {
        String otp = otpService.issue(EMAIL);

        assertThatCode(() -> otpService.verify(EMAIL, otp)).doesNotThrowAnyException();
        assertThat(redis.hasKey(OTP_KEY)).isFalse();
    }

    @Test
    void should_throw_OtpInvalidException_and_increment_attempts_when_verify_mismatch() {
        otpService.issue(EMAIL);

        assertThatThrownBy(() -> otpService.verify(EMAIL, "000000"))
                .isInstanceOf(OtpInvalidException.class);
        assertThat(redis.<String, String>opsForHash().get(OTP_KEY, "attempts")).isEqualTo("1");
    }

    @Test
    void should_throw_OtpExpiredException_when_verify_without_issue() {
        assertThatThrownBy(() -> otpService.verify(EMAIL, "123456"))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void should_throw_OtpMaxAttemptsExceededException_and_expire_key_on_sixth_attempt() {
        otpService.issue(EMAIL);
        String wrong = "000000";

        // 誤り 1〜5 回目は Invalid（attempts が 1..5 に加算される）
        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> otpService.verify(EMAIL, wrong))
                    .isInstanceOf(OtpInvalidException.class);
        }
        assertThat(redis.<String, String>opsForHash().get(OTP_KEY, "attempts")).isEqualTo("5");

        // 6 回目の入口で attempts>=maxAttempts(5) と判定 → MaxAttempts + キー失効
        assertThatThrownBy(() -> otpService.verify(EMAIL, wrong))
                .isInstanceOf(OtpMaxAttemptsExceededException.class);
        assertThat(redis.hasKey(OTP_KEY)).isFalse();
    }
}
