package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * プロフィール部分更新リクエスト（{@code PATCH /users/me}）を表現します。
 *
 * <p>
 * 部分更新セマンティクス: 各フィールドは省略可（{@code null} = 未送信＝更新しない）。
 * {@code displayName} は送信時のみ 1〜100 文字を要求する（{@code @Size} は {@code null} を素通り）。
 */
public record UpdateProfileRequest(
        /** 表示名（送信時は 1〜100 文字・空文字不可。未送信は更新しない） */
        @Size(min = 1, max = 100, message = "表示名は100文字以内で入力してください") String displayName,

        /** アバター画像URL（送信時は URL 形式。未送信は更新しない） */
        @URL(message = "URLの形式が正しくありません") String avatarUrl) {
}
