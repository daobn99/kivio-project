package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * プロフィール部分更新リクエストを表現します。
 *
 * <p>
 * 全フィールドが省略可（{@code null} = 未送信＝更新しない）。{@code avatarUrl} は空文字で
 * クリアを要求できる（{@code @URL} は空文字を有効と扱うためバリデーションを通過する）。
 */
public record UpdateProfileRequest(
        /** 表示名 */
        @Size(min = 1, max = 100, message = "表示名は100文字以内で入力してください") String displayName,

        /** アバター画像URL */
        @URL(message = "URLの形式が正しくありません") String avatarUrl) {
}
