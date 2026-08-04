package io.kivio.domain.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SellerApplication} のドメインメソッドと既定値を検証します。
 */
class SellerApplicationTest {

    @Test
    void should_default_status_to_pending_when_not_specified() {
        // 新規申請は常に PENDING。リクエスト由来の status を受け付けないことの土台
        SellerApplication application = SellerApplication.builder()
                .applicantId(UUID.randomUUID())
                .reason("ハンドメイド作品を販売したいため")
                .build();

        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(application.isPending()).isTrue();
        assertThat(application.isApproved()).isFalse();
    }

    @Test
    void should_report_approved_when_status_is_approved() {
        SellerApplication application = applicationWith(SellerApplicationStatus.APPROVED);

        assertThat(application.isApproved()).isTrue();
        assertThat(application.isPending()).isFalse();
    }

    @Test
    void should_report_neither_pending_nor_approved_when_status_is_rejected() {
        SellerApplication application = applicationWith(SellerApplicationStatus.REJECTED);

        assertThat(application.isPending()).isFalse();
        assertThat(application.isApproved()).isFalse();
    }

    @Test
    void should_compare_by_id_only() {
        // @EqualsAndHashCode(onlyExplicitlyIncluded = true) — 同一 ID なら他フィールドが違っても同一視する
        UUID id = UUID.randomUUID();
        SellerApplication one = SellerApplication.builder().id(id).reason("理由A").build();
        SellerApplication another = SellerApplication.builder().id(id).reason("理由B").build();

        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(SellerApplication.builder().id(UUID.randomUUID()).build());
    }

    private SellerApplication applicationWith(SellerApplicationStatus status) {
        return SellerApplication.builder()
                .applicantId(UUID.randomUUID())
                .reason("申請理由")
                .status(status)
                .build();
    }
}
