package io.kivio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resend（本番メール送信）の接続設定を表現します。
 *
 * <p>{@code ResendEmailSender}（prod プロファイル限定）のみが使用する。
 * dev・test では API キー未設定のまま起動できる必要があるため、ここでは検証しない。
 * 必須チェックは実際に使用する {@code ResendEmailSender} のコンストラクタで行う。
 *
 * @param apiKey  Resend API キー（{@code RESEND_API_KEY}）
 * @param baseUrl Resend API のベース URL。通常は変更不要（テストでのスタブ差し替えを想定）
 */
@ConfigurationProperties(prefix = "app.email.resend")
public record ResendProperties(
        String apiKey,
        String baseUrl
) {
}
