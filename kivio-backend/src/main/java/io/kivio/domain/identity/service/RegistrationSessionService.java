package io.kivio.domain.identity.service;

import io.kivio.config.AuthProperties;
import io.kivio.domain.identity.exception.RegistrationSessionInvalidException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 登録セッション（registrationToken）の発行・検証・消費を表現します。
 *
 * <p>OTP 検証済みメールアドレスを Redis に TTL 付きで保存します。
 * registrationToken はワンタイムで、登録完了時に消費（削除）します。
 */
@Service
@RequiredArgsConstructor
public class RegistrationSessionService {

    private static final String SESSION_KEY_PREFIX = "reg:session:";

    private final StringRedisTemplate redis;
    private final AuthProperties authProperties;

    /**
     * OTP 検証済みメールアドレスに対する登録セッションを発行し、registrationToken を返します。
     */
    public String create(String email) {
        String registrationToken = UUID.randomUUID().toString();
        redis.opsForValue().set(
                sessionKey(registrationToken), email, authProperties.registrationSession().ttl());
        return registrationToken;
    }

    /**
     * 登録セッションを消費（検証して削除）し、認証済みメールアドレスを返します。
     *
     * @throws RegistrationSessionInvalidException トークンが無効・期限切れ・使用済みの場合
     */
    public String consume(String registrationToken) {
        String email = redis.opsForValue().getAndDelete(sessionKey(registrationToken));
        if (email == null) {
            throw new RegistrationSessionInvalidException();
        }
        return email;
    }

    private String sessionKey(String registrationToken) {
        return SESSION_KEY_PREFIX + registrationToken;
    }
}
