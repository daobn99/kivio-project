package io.kivio.infra.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 開発・テスト環境向けのコンソールログ出力メール送信を表現します。
 *
 * <p>本番環境では Resend 等の実装に差し替えてください（infra/resend/）。
 */
@Slf4j
@Component
public class LogEmailSender implements EmailSender {

    @Override
    public void sendRegistrationOtp(String to, String otpCode) {
        // 本番では Resend API を呼び出す。開発中は OTP をログに出力して代替する
        log.info("DEV_REGISTRATION_OTP to={} otp={}", to, otpCode);
    }
}
