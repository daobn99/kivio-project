package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * アクセストークン再発行リクエストを表現します。
 */
public record RefreshRequest(
        /** リフレッシュトークン */
        @NotBlank(message = "リフレッシュトークンは必須です")
        String refreshToken
) {}
