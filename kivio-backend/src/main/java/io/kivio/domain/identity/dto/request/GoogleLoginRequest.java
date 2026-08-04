package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Google OAuthログインリクエストを表現します。
 */
public record GoogleLoginRequest(
        /** Google ID Token */
        @NotBlank(message = "Google ID Tokenは必須です")
        String idToken
) {}
