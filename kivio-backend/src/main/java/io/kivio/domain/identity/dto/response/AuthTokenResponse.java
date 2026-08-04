package io.kivio.domain.identity.dto.response;

import lombok.Builder;

/**
 * 認証トークンレスポンスを表現します。
 *
 * <p>refreshTokenはToken Rotationで新発行した場合のみ含まれます。
 * Jacksonのdefault-property-inclusion: non_nullによりnullフィールドは自動除外されます。
 */
@Builder
public record AuthTokenResponse(
        /** アクセストークン */
        String accessToken,
        /** リフレッシュトークン */
        String refreshToken,
        /** トークン種別 */
        String tokenType,
        /** アクセストークン有効期間（秒） */
        long expiresIn
) {
    public static AuthTokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .build();
    }

    public static AuthTokenResponse withoutRefresh(String accessToken, long expiresIn) {
        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .build();
    }
}
