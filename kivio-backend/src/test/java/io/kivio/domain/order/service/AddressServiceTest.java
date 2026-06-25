package io.kivio.domain.order.service;

import io.kivio.common.exception.ResourceNotFoundException;
import io.kivio.domain.order.domain.Address;
import io.kivio.domain.order.dto.request.CreateAddressRequest;
import io.kivio.domain.order.dto.request.UpdateAddressRequest;
import io.kivio.domain.order.dto.response.AddressResponse;
import io.kivio.domain.order.exception.ResourceAccessDeniedException;
import io.kivio.domain.order.repository.AddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AddressService} の CRUD・所有権チェック・デフォルト付け替えを単体検証します。
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;
    @InjectMocks
    private AddressService addressService;

    private Address address(UUID id, UUID userId, boolean isDefault) {
        return Address.builder()
                .id(id)
                .userId(userId)
                .recipientName("山田太郎")
                .postalCode("100-0001")
                .prefecture("東京都")
                .city("千代田区")
                .addressLine("千代田1-1")
                .phoneNumber("03-1234-5678")
                .isDefault(isDefault)
                .build();
    }

    private CreateAddressRequest createRequest(boolean isDefault) {
        return new CreateAddressRequest("佐藤花子", "150-0002", "東京都", "渋谷区",
                "渋谷2-2", "090-1111-2222", isDefault);
    }

    // ============================================================
    // list
    // ============================================================

    @Test
    void should_return_only_own_addresses() {
        UUID userId = UUID.randomUUID();
        Address a1 = address(UUID.randomUUID(), userId, true);
        Address a2 = address(UUID.randomUUID(), userId, false);
        given(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId))
                .willReturn(List.of(a1, a2));

        List<AddressResponse> result = addressService.list(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(a1.getId());
        assertThat(result.get(0).isDefault()).isTrue();
    }

    // ============================================================
    // create
    // ============================================================

    @Test
    void should_save_address_and_return_response() {
        UUID userId = UUID.randomUUID();
        given(addressRepository.save(any(Address.class))).willAnswer(inv -> inv.getArgument(0));

        AddressResponse response = addressService.create(userId, createRequest(false));

        assertThat(response.recipientName()).isEqualTo("佐藤花子");
        assertThat(response.postalCode()).isEqualTo("150-0002");
        assertThat(response.isDefault()).isFalse();
        // デフォルト指定なしでは付け替えを行わない
        verify(addressRepository, never()).clearDefaultForUser(userId);
    }

    @Test
    void should_clear_existing_default_when_creating_default_address() {
        UUID userId = UUID.randomUUID();
        given(addressRepository.save(any(Address.class))).willAnswer(inv -> inv.getArgument(0));

        AddressResponse response = addressService.create(userId, createRequest(true));

        assertThat(response.isDefault()).isTrue();
        verify(addressRepository).clearDefaultForUser(userId);
    }

    // ============================================================
    // update
    // ============================================================

    @Test
    void should_update_own_address() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, userId, false);
        given(addressRepository.findById(addressId)).willReturn(Optional.of(address));

        UpdateAddressRequest request = new UpdateAddressRequest("新宛名", null, null, null, null, null, null);
        AddressResponse response = addressService.update(userId, addressId, request);

        assertThat(response.recipientName()).isEqualTo("新宛名");
        assertThat(address.getRecipientName()).isEqualTo("新宛名");
        // 他フィールドは未送信のため不変
        assertThat(address.getCity()).isEqualTo("千代田区");
    }

    @Test
    void should_clear_existing_default_when_updating_address_to_default() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, userId, false);
        given(addressRepository.findById(addressId)).willReturn(Optional.of(address));

        UpdateAddressRequest request =
                new UpdateAddressRequest(null, null, null, null, null, null, true);
        AddressResponse response = addressService.update(userId, addressId, request);

        assertThat(response.isDefault()).isTrue();
        assertThat(address.isDefault()).isTrue();
        verify(addressRepository).clearDefaultForUser(userId);
    }

    @Test
    void should_deny_update_of_another_users_address() {
        UUID userId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Address othersAddress = address(addressId, ownerId, false);
        given(addressRepository.findById(addressId)).willReturn(Optional.of(othersAddress));

        UpdateAddressRequest request = new UpdateAddressRequest("乗っ取り", null, null, null, null, null, null);
        assertThatThrownBy(() -> addressService.update(userId, addressId, request))
                .isInstanceOf(ResourceAccessDeniedException.class);

        // 他人の住所が書き換わっていないこと
        assertThat(othersAddress.getRecipientName()).isEqualTo("山田太郎");
    }

    @Test
    void should_throw_not_found_when_updating_absent_address() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        given(addressRepository.findById(addressId)).willReturn(Optional.empty());

        UpdateAddressRequest request = new UpdateAddressRequest("新宛名", null, null, null, null, null, null);
        assertThatThrownBy(() -> addressService.update(userId, addressId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // delete
    // ============================================================

    @Test
    void should_delete_own_address() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, userId, false);
        given(addressRepository.findById(addressId)).willReturn(Optional.of(address));

        addressService.delete(userId, addressId);

        verify(addressRepository).delete(address);
    }

    @Test
    void should_deny_delete_of_another_users_address() {
        UUID userId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Address othersAddress = address(addressId, ownerId, false);
        given(addressRepository.findById(addressId)).willReturn(Optional.of(othersAddress));

        assertThatThrownBy(() -> addressService.delete(userId, addressId))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(addressRepository, never()).delete(any(Address.class));
    }

    @Test
    void should_throw_not_found_when_deleting_absent_address() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        given(addressRepository.findById(addressId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.delete(userId, addressId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
