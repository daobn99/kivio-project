package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

/**
 * アクセストークン再発行リクエストを表現します。
 */
@Getter
@Builder
public class RefreshRequest {

    /** リフレッシュトークン */
    @NotBlank(message = "リフレッシュトークンは必須です")
    private String refreshToken;
}
