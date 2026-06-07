package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

/**
 * ログアウトリクエストを表現します。
 */
@Getter
@Builder
public class LogoutRequest {

    /** リフレッシュトークン */
    @NotBlank(message = "リフレッシュトークンは必須です")
    private String refreshToken;
}
