package io.kivio.domain.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kivio.config.AuthProperties;
import io.kivio.domain.identity.exception.RegistrationSessionInvalidException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link RegistrationSessionService} の単体テスト。実 Redis（Testcontainers）に対して
 * TTL 付き保存・ワンタイム消費（getAndDelete による削除）を検証します。
 */
@Testcontainers(disabledWithoutDocker = true) // Docker 無し環境では自動スキップ（既存方針と統一）
class RegistrationSessionServiceTest {

    private static final String EMAIL = "session-test@example.com";

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private StringRedisTemplate redis;
    private RegistrationSessionService sessionService;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory cf =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        AuthProperties props = new AuthProperties(
                new AuthProperties.Otp(6, Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5),
                new AuthProperties.RegistrationSession(Duration.ofMinutes(30)));
        sessionService = new RegistrationSessionService(redis, props);
    }

    @Test
    void should_store_email_with_ttl_and_return_it_on_consume() {
        String token = sessionService.create(EMAIL);

        assertThat(token).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"); // UUID 形式
        String sessionKey = "reg:session:" + token;
        assertThat(redis.opsForValue().get(sessionKey)).isEqualTo(EMAIL);
        assertThat(redis.getExpire(sessionKey)).isBetween(1790L, 1800L); // TTL 30分

        assertThat(sessionService.consume(token)).isEqualTo(EMAIL);
    }

    @Test
    void should_invalidate_token_after_first_consume() {
        String token = sessionService.create(EMAIL);

        sessionService.consume(token);

        assertThat(redis.hasKey("reg:session:" + token)).isFalse();
        assertThatThrownBy(() -> sessionService.consume(token))
                .isInstanceOf(RegistrationSessionInvalidException.class);
    }

    @Test
    void should_throw_RegistrationSessionInvalidException_for_unknown_token() {
        assertThatThrownBy(() -> sessionService.consume("nonexistent-token"))
                .isInstanceOf(RegistrationSessionInvalidException.class);
    }
}
