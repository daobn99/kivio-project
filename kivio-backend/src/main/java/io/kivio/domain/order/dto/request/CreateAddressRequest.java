package io.kivio.domain.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 配送先住所追加リクエスト（{@code POST /users/me/addresses}）を表現します。
 */
public record CreateAddressRequest(
        /** 宛名（1〜100文字） */
        @NotBlank(message = "宛名を入力してください") @Size(max = 100, message = "宛名は100文字以内で入力してください") String recipientName,

        /** 郵便番号（7桁・ハイフン任意） */
        @NotBlank(message = "郵便番号を入力してください") @Pattern(regexp = "^\\d{3}-?\\d{4}$", message = "郵便番号は7桁の数字で入力してください") String postalCode,

        /** 都道府県（1〜20文字） */
        @NotBlank(message = "都道府県を選択してください") @Size(max = 20, message = "都道府県は20文字以内で入力してください") String prefecture,

        /** 市区町村（1〜100文字） */
        @NotBlank(message = "市区町村を入力してください") @Size(max = 100, message = "市区町村は100文字以内で入力してください") String city,

        /** 番地・建物名（1〜255文字） */
        @NotBlank(message = "番地・建物名を入力してください") @Size(max = 255, message = "番地・建物名は255文字以内で入力してください") String addressLine,

        /** 電話番号（先頭 0・ハイフン任意） */
        @NotBlank(message = "電話番号を入力してください") @Pattern(regexp = "^0\\d{1,4}-?\\d{1,4}-?\\d{4}$", message = "電話番号の形式が正しくありません") String phoneNumber,

        /** デフォルト住所に設定するか（既定 false） */
        boolean isDefault) {
}
