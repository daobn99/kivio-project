package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * メールアドレス確認リクエストを表現します。
 */
public record VerifyEmailRequest(
        /** 確認トークン */
        @NotBlank(message = "認証トークンは必須です")
        String token
) {}
