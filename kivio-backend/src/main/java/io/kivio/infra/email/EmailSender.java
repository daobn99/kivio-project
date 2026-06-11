package io.kivio.infra.email;

/**
 * メール送信サービスを表現します。
 */
public interface EmailSender {

    /**
     * 登録時の認証コード（OTP）メールを送信します。
     *
     * @param to      送信先メールアドレス
     * @param otpCode 平文の認証コード（メール本文に記載する。ログ・DB には保存しない）
     */
    void sendRegistrationOtp(String to, String otpCode);
}
