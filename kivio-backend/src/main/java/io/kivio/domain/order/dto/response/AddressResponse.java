package io.kivio.domain.order.dto.response;

import io.kivio.domain.order.domain.Address;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * 配送先住所レスポンスを表現します。
 */
@Builder
public record AddressResponse(
        /** 住所ID */
        UUID id,
        /** 宛名 */
        String recipientName,
        /** 郵便番号 */
        String postalCode,
        /** 都道府県 */
        String prefecture,
        /** 市区町村 */
        String city,
        /** 番地・建物名 */
        String addressLine,
        /** 電話番号 */
        String phoneNumber,
        /** デフォルト住所フラグ */
        boolean isDefault,
        /** 作成日時（ISO 8601 UTC） */
        Instant createdAt) {
    public static AddressResponse from(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .recipientName(address.getRecipientName())
                .postalCode(address.getPostalCode())
                .prefecture(address.getPrefecture())
                .city(address.getCity())
                .addressLine(address.getAddressLine())
                .phoneNumber(address.getPhoneNumber())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
