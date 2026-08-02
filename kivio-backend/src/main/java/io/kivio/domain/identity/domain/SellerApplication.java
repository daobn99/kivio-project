package io.kivio.domain.identity.domain;

import io.kivio.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;
import java.util.UUID;

/**
 * セラー申請を表現する集約ルートです。
 *
 * <p>
 * {@code identity} ドメインに属し、{@code User} とは {@code applicantId} /
 * {@code reviewerId}（UUID）の値参照のみで関連付けます（{@code @ManyToOne} は張りません）。
 * {@code User} が {@code @SQLRestriction("deleted_at IS NULL")} を持つため、エンティティ参照に
 * すると退会ユーザーの申請取得時に予期しない挙動を招くためです。
 *
 * <p>
 * 論理削除を持たない（{@code deleted_at} カラムが無い）ため {@link BaseEntity} を継承します。
 * 却下後の再申請は既存レコードの更新ではなく新規レコードの作成で行うため、状態を巻き戻す
 * ドメインメソッドは持ちません。
 */
@Entity
@Table(name = "seller_applications")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class SellerApplication extends BaseEntity {

    /** セラー申請ID */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    /** 申請者ユーザーID */
    @Column(name = "applicant_id", nullable = false, updatable = false)
    private UUID applicantId;

    /** 申請理由 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** 審査ステータス */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SellerApplicationStatus status = SellerApplicationStatus.PENDING;

    /** 審査者ユーザーID */
    @Column(name = "reviewer_id")
    private UUID reviewerId;

    /** 審査コメント */
    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    /** 審査日時 */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public boolean isPending() {
        return status == SellerApplicationStatus.PENDING;
    }

    public boolean isApproved() {
        return status == SellerApplicationStatus.APPROVED;
    }
}
