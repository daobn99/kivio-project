package io.kivio.infra.email;

import io.kivio.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * 開発環境向けの SMTP メール送信トランスポートです（dev プロファイル限定）。
 *
 * <p>devcontainer 上の Mailpit（SMTP {@code MAIL_HOST:MAIL_PORT}）へ HTML メールを送信し、
 * Web UI（http://localhost:8025）で本番同等のメールを目視確認できる。
 * 本番では Resend HTTP API 実装（{@code @Profile("prod")}）へ差し替えます。
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    @Override
    public void send(EmailMessage message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(emailProperties.fromAddress(), emailProperties.fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("メールの作成に失敗しました", e);
        }
        mailSender.send(mimeMessage);
        // SECURITY: 件名に OTP 等の機微情報が含まれ得るため、ログには件名・本文を出力しない
        log.debug("Sent email to={}", message.to());
    }
}
