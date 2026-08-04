package io.kivio.domain.identity.dto.response;

/**
 * 認証コード（OTP）送信レスポンスを表現します。
 */
public record RequestOtpResponse(
        /** 案内メッセージ */
        String message,
        /** OTP の有効期間（秒） */
        long expiresInSeconds
) {
    public static RequestOtpResponse of(long expiresInSeconds) {
        return new RequestOtpResponse("認証コードを送信しました", expiresInSeconds);
    }
}
