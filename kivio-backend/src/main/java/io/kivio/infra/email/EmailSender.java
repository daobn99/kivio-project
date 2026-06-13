package io.kivio.infra.email;

/**
 * メール送信トランスポートを表現します。
 *
 * <p>「どう送るか」（SMTP / HTTP API）のみを担う薄い契約。件名・本文の描画は
 * ユースケース層（例: {@code AuthEmailService}）が行い、ここには描画済みの {@link EmailMessage} が渡る。
 * 実装は profile で差し替える（dev: {@code SmtpEmailSender} / prod: {@code ResendEmailSender}）。
 */
public interface EmailSender {

    /**
     * 描画済みのメールを送信します。
     *
     * @param message 送信先・件名・本文（HTML）を保持する描画済みメッセージ
     */
    void send(EmailMessage message);
}
