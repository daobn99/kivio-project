package io.kivio.infra.email;

/**
 * メール送信サービスを表現します。
 */
public interface EmailSender {

    /**
     * メールアドレス確認メールを送信します。
     *
     * @param to       送信先メールアドレス
     * @param rawToken 平文の確認トークン（メール本文に埋め込む）
     */
    void sendVerificationEmail(String to, String rawToken);
}
