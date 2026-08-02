package io.kivio.domain.order.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 配送先住所更新リクエスト（{@code PATCH /users/me/addresses/{id}}）を表現します.
 *
 * <p>
 * 部分更新セマンティクス: 各フィールドは省略可（{@code null} = 未送信＝更新しない）。
 * {@code @NotBlank} を外し {@code @Size}/{@code @Pattern} のみとすることで {@code null}
 * を素通りさせる。{@code isDefault} も {@code Boolean}（nullable）とし、未送信なら不変。
 */
public record UpdateAddressRequest(
        /** 宛名（送信時は 1〜100文字） */
        @Size(min = 1, max = 100, message = "宛名は100文字以内で入力してください") String recipientName,

        /** 郵便番号（送信時は 7桁・ハイフン任意） */
        @Pattern(regexp = "^\\d{3}-?\\d{4}$", message = "郵便番号は7桁の数字で入力してください") String postalCode,

        /** 都道府県（送信時は 1〜20文字） */
        @Size(min = 1, max = 20, message = "都道府県は20文字以内で入力してください") String prefecture,

        /** 市区町村（送信時は 1〜100文字） */
        @Size(min = 1, max = 100, message = "市区町村は100文字以内で入力してください") String city,

        /** 番地・建物名（送信時は 1〜255文字） */
        @Size(min = 1, max = 255, message = "番地・建物名は255文字以内で入力してください") String addressLine,

        /** 電話番号（送信時は 先頭 0・ハイフン任意） */
        @Pattern(regexp = "^0\\d{1,4}-?\\d{1,4}-?\\d{4}$", message = "電話番号の形式が正しくありません") String phoneNumber,

        /** デフォルト住所に設定するか（未送信＝null は不変） */
        Boolean isDefault) {
}
