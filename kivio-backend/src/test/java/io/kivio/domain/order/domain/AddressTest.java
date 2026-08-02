package io.kivio.domain.order.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Address} 集約ルートのドメインルール（部分更新・所有権・デフォルト切替）を
 * Spring を起動しない純粋 JUnit で検証します。
 */
class AddressTest {

    private Address sample(UUID userId) {
        return Address.builder()
                .userId(userId)
                .recipientName("山田太郎")
                .postalCode("100-0001")
                .prefecture("東京都")
                .city("千代田区")
                .addressLine("千代田1-1")
                .phoneNumber("03-1234-5678")
                .isDefault(false)
                .build();
    }

    @Test
    void should_update_all_fields_when_all_provided() {
        Address address = sample(UUID.randomUUID());

        address.update("佐藤花子", "150-0002", "東京都", "渋谷区", "渋谷2-2", "090-1111-2222");

        assertThat(address.getRecipientName()).isEqualTo("佐藤花子");
        assertThat(address.getPostalCode()).isEqualTo("150-0002");
        assertThat(address.getCity()).isEqualTo("渋谷区");
        assertThat(address.getAddressLine()).isEqualTo("渋谷2-2");
        assertThat(address.getPhoneNumber()).isEqualTo("090-1111-2222");
    }

    @Test
    void should_keep_unsent_fields_unchanged_when_null_is_passed() {
        Address address = sample(UUID.randomUUID());

        // recipientName のみ更新・他は null（未送信）
        address.update("佐藤花子", null, null, null, null, null);

        assertThat(address.getRecipientName()).isEqualTo("佐藤花子");
        assertThat(address.getPostalCode()).isEqualTo("100-0001");
        assertThat(address.getPrefecture()).isEqualTo("東京都");
        assertThat(address.getCity()).isEqualTo("千代田区");
        assertThat(address.getAddressLine()).isEqualTo("千代田1-1");
        assertThat(address.getPhoneNumber()).isEqualTo("03-1234-5678");
    }

    @Test
    void should_return_true_when_owned_by_the_given_user() {
        UUID userId = UUID.randomUUID();
        Address address = sample(userId);

        assertThat(address.isOwnedBy(userId)).isTrue();
    }

    @Test
    void should_return_false_when_owned_by_another_user() {
        Address address = sample(UUID.randomUUID());

        assertThat(address.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void should_mark_and_unset_default() {
        Address address = sample(UUID.randomUUID());
        assertThat(address.isDefault()).isFalse();

        address.markDefault();
        assertThat(address.isDefault()).isTrue();

        address.unsetDefault();
        assertThat(address.isDefault()).isFalse();
    }
}
