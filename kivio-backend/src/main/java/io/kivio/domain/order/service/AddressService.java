package io.kivio.domain.order.service;

import io.kivio.common.exception.ResourceNotFoundException;
import io.kivio.domain.order.domain.Address;
import io.kivio.domain.order.dto.request.CreateAddressRequest;
import io.kivio.domain.order.dto.request.UpdateAddressRequest;
import io.kivio.domain.order.dto.response.AddressResponse;
import io.kivio.domain.order.exception.ResourceAccessDeniedException;
import io.kivio.domain.order.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 配送先住所のアプリケーションサービスを表現します。
 *
 * <p>
 * 所有権は {@code address.userId == principal.userId} の値比較で担保し、
 * 存在しなければ {@link ResourceNotFoundException}（404）、他人の住所なら
 * {@link ResourceAccessDeniedException}（403）を送出します（存在判定 → 所有判定の順）。
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    /**
     * 認証中ユーザーの配送先住所を一覧取得します（デフォルト住所を先頭 → 作成日時昇順）。
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    /**
     * 配送先住所を追加します。
     *
     * <p>
     * {@code isDefault = true} の場合、保存前に同一ユーザーの他住所の {@code is_default} を
     * {@code false} に落とします（ユーザーにつきデフォルトは 1 件）。
     */
    @Transactional
    public AddressResponse create(UUID userId, CreateAddressRequest request) {
        if (request.isDefault()) {
            addressRepository.clearDefaultForUser(userId);
        }
        Address address = Address.builder()
                .userId(userId)
                .recipientName(request.recipientName())
                .postalCode(request.postalCode())
                .prefecture(request.prefecture())
                .city(request.city())
                .addressLine(request.addressLine())
                .phoneNumber(request.phoneNumber())
                .isDefault(request.isDefault())
                .build();
        return AddressResponse.from(addressRepository.save(address));
    }

    /**
     * 配送先住所を部分更新します。
     *
     * <p>
     * 未送信（{@code null}）のフィールドは更新しません。{@code isDefault = true} の指定時は
     * 同一ユーザーの他住所の {@code is_default} を {@code false} に落としてから当該住所を
     * デフォルトに設定します。
     *
     * @throws ResourceNotFoundException     住所が存在しない場合（404）
     * @throws ResourceAccessDeniedException 他人の住所の場合（403）
     */
    @Transactional
    public AddressResponse update(UUID userId, UUID addressId, UpdateAddressRequest request) {
        Address address = findOwned(userId, addressId);
        address.update(request.recipientName(), request.postalCode(), request.prefecture(),
                request.city(), request.addressLine(), request.phoneNumber());
        if (request.isDefault() != null) {
            if (request.isDefault()) {
                // 対象住所自身は除外する（既定住所への再指定でデフォルトが消失するのを防ぐ）
                addressRepository.clearDefaultForUserExcept(userId, addressId);
                address.markDefault();
            } else {
                address.unsetDefault();
            }
        }
        return AddressResponse.from(address);
    }

    /**
     * 配送先住所を物理削除します（{@code addresses} は論理削除を持たない）。
     *
     * @throws ResourceNotFoundException     住所が存在しない場合（404）
     * @throws ResourceAccessDeniedException 他人の住所の場合（403）
     */
    @Transactional
    public void delete(UUID userId, UUID addressId) {
        Address address = findOwned(userId, addressId);
        addressRepository.delete(address);
    }

    /**
     * 住所を取得し所有権を検証します（存在判定 → 所有判定の順）。
     */
    private Address findOwned(UUID userId, UUID addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        if (!address.isOwnedBy(userId)) {
            throw new ResourceAccessDeniedException();
        }
        return address;
    }
}
