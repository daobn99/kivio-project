package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * ログアウトリクエストを表現します。
 */
public record LogoutRequest(
        /** リフレッシュトークン */
        @NotBlank(message = "リフレッシュトークンは必須です")
        String refreshToken
) {}
