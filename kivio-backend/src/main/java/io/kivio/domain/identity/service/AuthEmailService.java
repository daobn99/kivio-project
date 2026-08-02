package io.kivio.domain.identity.service;

import io.kivio.infra.email.EmailMessage;
import io.kivio.infra.email.EmailSender;
import io.kivio.infra.email.template.EmailTemplateFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * identity ドメインのメール送信ユースケースを表現します。
 *
 * <p>
 * テンプレート選択・変数組み立て・件名生成（いずれもトランスポート非依存）をここへ集約し、
 * 実際の送信は profile で切り替わる {@link EmailSender}（dev: SMTP→Mailpit / prod:
 * Resend）へ委譲する。
 * これにより dev/prod の各トランスポート実装にメール種別ごとのテンプレート処理が重複しない。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEmailService {

    /** 登録 OTP メールのテンプレート */
    private static final String OTP_TEMPLATE = "emails/ja/registration-otp.html";
    /** OTP 有効期限の説明文（AuthProperties.otp.ttl=PT10M と一致させること）。 */
    private static final String OTP_EXPIRES_IN = "10分";

    private final EmailTemplateFormatter templateFormatter;
    private final EmailSender emailSender;

    /**
     * 登録時の認証コード（OTP）メールを送信します。
     *
     * @param to      送信先メールアドレス
     * @param otpCode 平文の認証コード（メール本文に記載する。ログ・DB には保存しない）
     */
    public void sendRegistrationOtp(String to, String otpCode) {
        String html = templateFormatter.render(
                OTP_TEMPLATE, Map.of("otpCode", otpCode, "expiresIn", OTP_EXPIRES_IN));
        // SECURITY: 件名に OTP を含めるが、ログには OTP を出力しない
        emailSender.send(new EmailMessage(to, "【Kivio】認証コード: " + otpCode, html));
        log.info("Sent registration OTP email to={}", to);
    }
}
