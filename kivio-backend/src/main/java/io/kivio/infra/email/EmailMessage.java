package io.kivio.infra.email;

/**
 * 送信する 1 通のメール（描画済み）を表現します。
 *
 * <p>件名・本文はユースケース層（例: {@code AuthEmailService}）でテンプレート描画済みの値を渡す。
 * {@link EmailSender} 実装はこの値をそのまま送信するだけで、テンプレートや変数を関知しない。
 * 差出人は {@code EmailProperties} から各 {@link EmailSender} 実装が付与する。
 *
 * @param to       送信先メールアドレス
 * @param subject  件名（描画済み）
 * @param htmlBody 本文 HTML（描画済み）
 */
public record EmailMessage(String to, String subject, String htmlBody) {
}
