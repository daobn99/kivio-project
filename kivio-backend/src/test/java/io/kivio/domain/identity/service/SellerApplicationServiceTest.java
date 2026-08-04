package io.kivio.domain.identity.service;

import io.kivio.common.exception.ResourceNotFoundException;
import io.kivio.domain.identity.domain.SellerApplication;
import io.kivio.domain.identity.domain.SellerApplicationStatus;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.domain.UserRole;
import io.kivio.domain.identity.dto.request.CreateSellerApplicationRequest;
import io.kivio.domain.identity.dto.response.SellerApplicationResponse;
import io.kivio.domain.identity.exception.SellerApplicationAlreadyApprovedException;
import io.kivio.domain.identity.exception.SellerApplicationPendingException;
import io.kivio.domain.identity.repository.SellerApplicationRepository;
import io.kivio.domain.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link SellerApplicationService} の申請可否判定（3 段）と最新 1 件取得を単体検証します。
 *
 * <p>
 * ロール判定は {@code SecurityConfig} からロールガードを外した結果
 * （実装計画 OQ-1）、{@code POST /seller-applications} の唯一の認可ポイントです。
 * ここが落ちる = 認可が壊れている、と読むこと。
 */
@ExtendWith(MockitoExtension.class)
class SellerApplicationServiceTest {

    @Mock
    private SellerApplicationRepository sellerApplicationRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private SellerApplicationService sellerApplicationService;

    @Captor
    private ArgumentCaptor<SellerApplication> applicationCaptor;

    private static final CreateSellerApplicationRequest REQUEST =
            new CreateSellerApplicationRequest("ハンドメイド作品を販売したいため");

    // ============================================================
    // apply — 正常系
    // ============================================================

    @Test
    void should_create_pending_application_when_buyer_has_no_existing_application() {
        UUID applicantId = UUID.randomUUID();
        givenApplicant(applicantId, UserRole.ROLE_BUYER);
        givenNoExistingApplication(applicantId);
        givenSaveReturnsArgument();

        SellerApplicationResponse response = sellerApplicationService.apply(applicantId, REQUEST);

        assertThat(response.applicantId()).isEqualTo(applicantId);
        assertThat(response.reason()).isEqualTo("ハンドメイド作品を販売したいため");
        assertThat(response.status()).isEqualTo(SellerApplicationStatus.PENDING);
        // 審査前のため審査系フィールドは未設定（レスポンス JSON からは non_null で省略される）
        assertThat(response.reviewComment()).isNull();
        assertThat(response.reviewedAt()).isNull();
    }

    @Test
    void should_persist_pending_status_and_applicant_id_from_token() {
        UUID applicantId = UUID.randomUUID();
        givenApplicant(applicantId, UserRole.ROLE_BUYER);
        givenNoExistingApplication(applicantId);
        givenSaveReturnsArgument();

        sellerApplicationService.apply(applicantId, REQUEST);

        verify(sellerApplicationRepository).save(applicationCaptor.capture());
        SellerApplication saved = applicationCaptor.getValue();
        // status はリクエスト由来の値ではなく Entity の @Builder.Default（mass assignment 防止）
        assertThat(saved.getStatus()).isEqualTo(SellerApplicationStatus.PENDING);
        // 申請者は常にトークンの sub から解決する
        assertThat(saved.getApplicantId()).isEqualTo(applicantId);
        assertThat(saved.getReason()).isEqualTo("ハンドメイド作品を販売したいため");
        assertThat(saved.getReviewerId()).isNull();
        assertThat(saved.getReviewComment()).isNull();
        assertThat(saved.getReviewedAt()).isNull();
    }

    @Test
    void should_not_consider_rejected_applications_as_a_blocker() {
        // 却下後の再申請は新レコード作成（SELLER-05）。REJECTED の有無は一切問わないため、
        // REJECTED を条件にした照会が発生しないこと自体を検証する
        UUID applicantId = UUID.randomUUID();
        givenApplicant(applicantId, UserRole.ROLE_BUYER);
        givenNoExistingApplication(applicantId);
        givenSaveReturnsArgument();

        SellerApplicationResponse response = sellerApplicationService.apply(applicantId, REQUEST);

        assertThat(response.status()).isEqualTo(SellerApplicationStatus.PENDING);
        verify(sellerApplicationRepository, never())
                .existsByApplicantIdAndStatus(applicantId, SellerApplicationStatus.REJECTED);
        verify(sellerApplicationRepository).save(any(SellerApplication.class));
    }

    // ============================================================
    // apply — ロール判定（唯一の認可ポイント）
    // ============================================================

    @Test
    void should_reject_application_when_applicant_is_seller() {
        UUID applicantId = UUID.randomUUID();
        givenApplicant(applicantId, UserRole.ROLE_SELLER);

        assertThatThrownBy(() -> sellerApplicationService.apply(applicantId, REQUEST))
                .isInstanceOf(SellerApplicationAlreadyApprovedException.class);

        // ロール判定は既存申請の照会より前に行う（DB を触らずに弾く）
        verifyNoInteractions(sellerApplicationRepository);
    }

    @Test
    void should_reject_application_when_applicant_is_admin() {
        UUID applicantId = UUID.randomUUID();
        givenApplicant(applicantId, UserRole.ROLE_ADMIN);

        assertThatThrownBy(() -> sellerApplicationService.apply(applicantId, REQUEST))
                .isInstanceOf(SellerApplicationAlreadyApprovedException.class);

        verifyNoInteractions(sellerApplicationRepository);
    }

    // ============================================================
    // apply — 重複申請
    // ============================================================

    @Test
    void should_reject_application_when_pending_application_already_exists() {
        UUID applicantId = UUID.randomUUID();
        givenApplicant(applicantId, UserRole.ROLE_BUYER);
        given(sellerApplicationRepository.existsByApplicantIdAndStatus(
                applicantId, SellerApplicationStatus.PENDING)).willReturn(true);

        assertThatThrownBy(() -> sellerApplicationService.apply(applicantId, REQUEST))
                .isInstanceOf(SellerApplicationPendingException.class);

        verify(sellerApplicationRepository, never()).save(any(SellerApplication.class));
    }

    @Test
    void should_reject_application_when_approved_application_exists_despite_buyer_role() {
        // 承認処理が別スライスのため「APPROVED 申請はあるがロールが未昇格」という
        // 不整合状態が開発中に起こりうる。ロール判定に加えてここでも塞ぐ
        UUID applicantId = UUID.randomUUID();
        givenApplicant(applicantId, UserRole.ROLE_BUYER);
        given(sellerApplicationRepository.existsByApplicantIdAndStatus(
                applicantId, SellerApplicationStatus.PENDING)).willReturn(false);
        given(sellerApplicationRepository.existsByApplicantIdAndStatus(
                applicantId, SellerApplicationStatus.APPROVED)).willReturn(true);

        assertThatThrownBy(() -> sellerApplicationService.apply(applicantId, REQUEST))
                .isInstanceOf(SellerApplicationAlreadyApprovedException.class);

        verify(sellerApplicationRepository, never()).save(any(SellerApplication.class));
    }

    @Test
    void should_propagate_not_found_when_applicant_does_not_exist() {
        UUID applicantId = UUID.randomUUID();
        given(userRepository.findByIdOrThrow(applicantId))
                .willThrow(new ResourceNotFoundException("User", applicantId));

        assertThatThrownBy(() -> sellerApplicationService.apply(applicantId, REQUEST))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(sellerApplicationRepository);
    }

    // ============================================================
    // getMyLatest
    // ============================================================

    @Test
    void should_return_latest_application_when_applicant_has_applications() {
        // 「最新 1 件」の並び順自体は findFirstBy...OrderByCreatedAtDesc（DB）の責務のため、
        // ここでは委譲先と変換だけを検証する（実際の並び順は統合テストで裏付ける）
        UUID applicantId = UUID.randomUUID();
        SellerApplication latest = SellerApplication.builder()
                .id(UUID.randomUUID())
                .applicantId(applicantId)
                .reason("再申請の理由")
                .status(SellerApplicationStatus.PENDING)
                .build();
        given(sellerApplicationRepository.findFirstByApplicantIdOrderByCreatedAtDesc(applicantId))
                .willReturn(Optional.of(latest));

        SellerApplicationResponse response = sellerApplicationService.getMyLatest(applicantId);

        assertThat(response.id()).isEqualTo(latest.getId());
        assertThat(response.applicantId()).isEqualTo(applicantId);
        assertThat(response.reason()).isEqualTo("再申請の理由");
        assertThat(response.status()).isEqualTo(SellerApplicationStatus.PENDING);
    }

    @Test
    void should_expose_review_fields_when_latest_application_is_rejected() {
        UUID applicantId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        SellerApplication rejected = SellerApplication.builder()
                .id(UUID.randomUUID())
                .applicantId(applicantId)
                .reason("申請理由")
                .status(SellerApplicationStatus.REJECTED)
                .reviewerId(reviewerId)
                .reviewComment("事業内容の記載が不十分です")
                .reviewedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
        given(sellerApplicationRepository.findFirstByApplicantIdOrderByCreatedAtDesc(applicantId))
                .willReturn(Optional.of(rejected));

        SellerApplicationResponse response = sellerApplicationService.getMyLatest(applicantId);

        assertThat(response.status()).isEqualTo(SellerApplicationStatus.REJECTED);
        assertThat(response.reviewComment()).isEqualTo("事業内容の記載が不十分です");
        assertThat(response.reviewedAt()).isEqualTo("2026-07-01T00:00:00Z");
        // 審査者 ID はレスポンス DTO に含めない（誰が審査したかは申請者に開示しない）
    }

    @Test
    void should_throw_not_found_when_applicant_has_no_application() {
        // 404 は「未申請」という正常状態を表す（フロントはエラーとして扱わない）
        UUID applicantId = UUID.randomUUID();
        given(sellerApplicationRepository.findFirstByApplicantIdOrderByCreatedAtDesc(applicantId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerApplicationService.getMyLatest(applicantId))
                .isInstanceOf(ResourceNotFoundException.class)
                // 他人の情報・内部 ID を含まない汎用文言であること
                .hasMessage("セラー申請が見つかりません");
    }

    // ============================================================
    // helpers
    // ============================================================

    private void givenApplicant(UUID applicantId, UserRole role) {
        given(userRepository.findByIdOrThrow(applicantId)).willReturn(User.builder()
                .id(applicantId)
                .email("applicant@example.com")
                .displayName("申請者")
                .role(role)
                .build());
    }

    private void givenNoExistingApplication(UUID applicantId) {
        given(sellerApplicationRepository.existsByApplicantIdAndStatus(
                applicantId, SellerApplicationStatus.PENDING)).willReturn(false);
        given(sellerApplicationRepository.existsByApplicantIdAndStatus(
                applicantId, SellerApplicationStatus.APPROVED)).willReturn(false);
    }

    private void givenSaveReturnsArgument() {
        given(sellerApplicationRepository.save(any(SellerApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }
}
