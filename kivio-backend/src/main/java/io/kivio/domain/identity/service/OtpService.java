package io.kivio.domain.identity.service;

import io.kivio.config.AuthProperties;
import io.kivio.domain.identity.exception.OtpExpiredException;
import io.kivio.domain.identity.exception.OtpInvalidException;
import io.kivio.domain.identity.exception.OtpMaxAttemptsExceededException;
import io.kivio.domain.identity.exception.OtpResendThrottledException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;

/**
 * メール認証コード（OTP）の生成・検証・スロットリングを表現します。
 *
 * <p>OTP は Redis に SHA-256 ハッシュ + 試行回数で TTL 付き保存します（平文は保存しません）。
 * 期限切れデータは TTL により自動消滅するためクリーンアップバッチは不要です。
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final String OTP_KEY_PREFIX = "reg:otp:";
    private static final String COOLDOWN_KEY_PREFIX = "reg:otp:cooldown:";
    private static final String COUNT_KEY_PREFIX = "reg:otp:count:";
    private static final String FIELD_HASH = "otpHash";
    private static final String FIELD_ATTEMPTS = "attempts";
    private static final Duration COUNT_WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 認証コード（OTP）を生成して Redis に保存し、平文の OTP を返します。
     *
     * <p>返却した平文 OTP はメール送信にのみ使用し、ログ・DB には保存しないでください。
     *
     * @throws OtpResendThrottledException 再送信クールダウン中、または 1 時間あたりの送信上限を超えた場合
     */
    public String issue(String email) {
        checkResendAllowed(email);

        String otp = generateOtp();
        String key = otpKey(email);
        redis.delete(key);

        BoundHashOperations<String, String, String> hash = redis.boundHashOps(key);
        hash.put(FIELD_HASH, TokenHashUtils.sha256Hex(otp));
        hash.put(FIELD_ATTEMPTS, "0");
        redis.expire(key, otp().ttl());

        // 再送信クールダウンを設定（メール単位のスロットリング）
        redis.opsForValue().set(cooldownKey(email), "1", otp().resendCooldown());
        return otp;
    }

    /**
     * 認証コード（OTP）を検証します。成功時は OTP キーを削除します。
     *
     * @throws OtpExpiredException             OTP が存在しない（期限切れ・未発行）場合
     * @throws OtpMaxAttemptsExceededException 検証試行回数が上限に達している場合
     * @throws OtpInvalidException             OTP が一致しない場合
     */
    public void verify(String email, String otp) {
        String key = otpKey(email);
        BoundHashOperations<String, String, String> hash = redis.boundHashOps(key);
        Map<String, String> entries = hash.entries();

        if (entries == null || entries.isEmpty()) {
            throw new OtpExpiredException();
        }

        int attempts = parseAttempts(entries.get(FIELD_ATTEMPTS));
        if (attempts >= otp().maxAttempts()) {
            redis.delete(key);
            throw new OtpMaxAttemptsExceededException();
        }

        String expectedHash = entries.get(FIELD_HASH);
        if (expectedHash == null || !constantTimeEquals(expectedHash, TokenHashUtils.sha256Hex(otp))) {
            hash.increment(FIELD_ATTEMPTS, 1);
            throw new OtpInvalidException();
        }

        // 検証成功：再利用を防ぐため即時削除する
        redis.delete(key);
    }

    private void checkResendAllowed(String email) {
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey(email)))) {
            throw new OtpResendThrottledException();
        }
        Long count = redis.opsForValue().increment(countKey(email));
        if (count != null && count == 1L) {
            redis.expire(countKey(email), COUNT_WINDOW);
        }
        if (count != null && count > otp().maxPerHour()) {
            throw new OtpResendThrottledException();
        }
    }

    private String generateOtp() {
        int length = otp().length();
        int bound = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", secureRandom.nextInt(bound));
    }

    private int parseAttempts(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private AuthProperties.Otp otp() {
        return authProperties.otp();
    }

    private String otpKey(String email) {
        return OTP_KEY_PREFIX + email;
    }

    private String cooldownKey(String email) {
        return COOLDOWN_KEY_PREFIX + email;
    }

    private String countKey(String email) {
        return COUNT_KEY_PREFIX + email;
    }
}
