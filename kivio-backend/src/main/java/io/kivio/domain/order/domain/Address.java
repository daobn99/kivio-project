package io.kivio.domain.order.domain;

import io.kivio.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 配送先住所を表現する集約ルートです。
 *
 * <p>
 * {@code order} ドメインに属しますが、{@code identity} の {@code User} とは
 * {@code userId}（UUID）の値参照のみで関連付けます（ドメインを跨いだエンティティ参照は張りません）。
 * 物理削除方針のため {@link io.kivio.common.entity.SoftDeletableEntity} ではなく
 * {@link BaseEntity} を継承します。
 */
@Entity
@Table(name = "addresses")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Address extends BaseEntity {

    /** 住所ID */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    /** 所有ユーザーID（identity ドメインへの値参照） */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** 宛名 */
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    /** 郵便番号 */
    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    /** 都道府県 */
    @Column(nullable = false, length = 20)
    private String prefecture;

    /** 市区町村 */
    @Column(nullable = false, length = 100)
    private String city;

    /** 番地・建物名 */
    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    /** 電話番号 */
    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    /** デフォルト住所フラグ（ユーザーにつき 1 件のみ true） */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    /**
     * 所有者を判定します（所有権チェック用）。
     */
    public boolean isOwnedBy(UUID candidateUserId) {
        return this.userId.equals(candidateUserId);
    }

    /**
     * 住所フィールドを部分更新します。
     *
     * <p>
     * {@code null} のフィールドは更新しません（{@code PATCH} の「未送信＝不変」セマンティクス）。
     * 値の検証は DTO（Bean Validation）側で済んでいる前提です。{@code isDefault} は
     * {@link #markDefault()} / {@link #unsetDefault()} で別途扱います。
     */
    public void update(String recipientName, String postalCode, String prefecture,
            String city, String addressLine, String phoneNumber) {
        if (recipientName != null) {
            this.recipientName = recipientName;
        }
        if (postalCode != null) {
            this.postalCode = postalCode;
        }
        if (prefecture != null) {
            this.prefecture = prefecture;
        }
        if (city != null) {
            this.city = city;
        }
        if (addressLine != null) {
            this.addressLine = addressLine;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }
}
