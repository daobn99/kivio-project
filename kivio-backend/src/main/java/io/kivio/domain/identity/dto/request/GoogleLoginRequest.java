package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

/**
 * Google OAuthログインリクエストを表現します。
 */
@Getter
@Builder
public class GoogleLoginRequest {

    /** Google ID Token */
    @NotBlank(message = "Google ID Tokenは必須です")
    private String idToken;
}
