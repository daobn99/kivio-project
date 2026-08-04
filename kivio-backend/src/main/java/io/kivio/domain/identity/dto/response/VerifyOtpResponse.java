package io.kivio.domain.identity.dto.response;

/**
 * 認証コード（OTP）検証レスポンスを表現します。
 */
public record VerifyOtpResponse(
        /** 登録セッショントークン（パスワード設定で使用する不透明な UUID） */
        String registrationToken,
        /** 登録セッションの有効期間（秒） */
        long expiresInSeconds
) {
    public static VerifyOtpResponse of(String registrationToken, long expiresInSeconds) {
        return new VerifyOtpResponse(registrationToken, expiresInSeconds);
    }
}
