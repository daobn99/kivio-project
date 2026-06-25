package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * パスワード変更リクエスト（{@code PATCH /users/me/password}）を表現します。
 */
public record ChangePasswordRequest(
        /** 現在のパスワード（照合用。長さ検証はしない） */
        @NotBlank(message = "現在のパスワードを入力してください") String currentPassword,

        /** 新しいパスワード */
        @NotBlank(message = "パスワードを入力してください") @Size(min = 8, max = 72, message = "パスワードは8文字以上72文字以内で入力してください") String newPassword) {
}
