package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

/**
 * メールアドレス確認リクエストを表現します。
 */
@Getter
@Builder
public class VerifyEmailRequest {

    /** 確認トークン */
    @NotBlank(message = "認証トークンは必須です")
    private String token;
}
