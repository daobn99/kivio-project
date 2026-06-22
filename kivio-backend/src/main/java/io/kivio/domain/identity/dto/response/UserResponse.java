package io.kivio.domain.identity.dto.response;

import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.domain.UserRole;
import io.kivio.domain.identity.domain.UserStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * ユーザープロフィールレスポンスを表現します。
 */
@Builder
public record UserResponse(
        /** ユーザーID */
        UUID id,
        /** メールアドレス */
        String email,
        /** 表示名 */
        String displayName,
        /** アバター画像URL（未設定時は null） */
        String avatarUrl,
        /** ロール */
        UserRole role,
        /** ステータス */
        UserStatus status,
        /** アカウント作成日時（ISO 8601 UTC） */
        Instant createdAt) {
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
