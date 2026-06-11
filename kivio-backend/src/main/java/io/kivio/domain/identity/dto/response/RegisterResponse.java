package io.kivio.domain.identity.dto.response;

import io.kivio.domain.identity.domain.User;

import java.time.Instant;
import java.util.UUID;

/**
 * ユーザー登録レスポンスを表現します。
 */
public record RegisterResponse(
        /** ユーザーID */
        UUID id,
        /** メールアドレス */
        String email,
        /** ロール */
        String role,
        /** メール確認済みフラグ */
        boolean emailVerified,
        /** 作成日時 */
        Instant createdAt
) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
