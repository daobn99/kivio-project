package io.kivio.infra.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 実送信を行わないフォールバックのメール送信トランスポートです（dev・prod 以外で有効）。
 *
 * <p>dev は {@code SmtpEmailSender}（SMTP→Mailpit）、prod は Resend 実装を使う。
 * それ以外の環境（test・プロファイル未指定の {@code bootRun} 等）では実トランスポートが存在しないため、
 * このフォールバックを有効化してコンテキストを起動可能に保つ。送信はログ出力のみで代替する。
 */
@Slf4j
@Component
@Profile("!dev & !prod")
public class LogEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        // SECURITY: 件名・本文に OTP 等の機微情報が含まれ得るため、送信先のみログ出力する
        log.info("LogEmailSender (no real delivery) to={}", message.to());
    }
}
