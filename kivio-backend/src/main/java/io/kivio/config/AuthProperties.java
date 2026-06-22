package io.kivio.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 登録フロー（OTP・登録セッション）の設定値を表現します。
 *
 * <p>OTP・登録セッションは Redis に TTL 付きで一時保存します。
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotNull Otp otp,
        @NotNull RegistrationSession registrationSession
) {

    /**
     * メール認証コード（OTP）の設定値を表現します。
     */
    public record Otp(
            /** OTP の桁数 */
            @Positive int length,
            /** OTP の有効期限（Redis TTL） */
            @NotNull Duration ttl,
            /** 検証試行の上限回数（超過で失効） */
            @Positive int maxAttempts,
            /** 同一メールへの再送信クールダウン */
            @NotNull Duration resendCooldown,
            /** 同一メールへの 1 時間あたり送信上限 */
            @Positive int maxPerHour
    ) {}

    /**
     * 登録セッション（registrationToken）の設定値を表現します。
     */
    public record RegistrationSession(
            /** registrationToken の有効期限（Redis TTL） */
            @NotNull Duration ttl
    ) {}
}
