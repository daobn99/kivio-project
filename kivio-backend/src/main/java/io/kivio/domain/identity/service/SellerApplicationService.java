package io.kivio.domain.identity.service;

import io.kivio.common.exception.ResourceNotFoundException;
import io.kivio.domain.audit.annotation.Auditable;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * セラー申請のアプリケーションサービスを表現します。
 *
 * <p>
 * 申請者は常にトークンの {@code sub}（user_id）から解決し、リクエストからは受け取りません。
 * 却下後の再申請は既存レコードの更新ではなく新規レコードの作成で行うため、{@code REJECTED} の
 * 申請が何件あっても新規申請を妨げません。
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SellerApplicationService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final UserRepository userRepository;

    /**
     * セラー申請を送信します（常に {@code status = PENDING} の新規レコード）。
     *
     * @throws ResourceNotFoundException                  申請者が存在しない場合（404）
     * @throws SellerApplicationPendingException          審査中の申請が既にある場合（409）
     * @throws SellerApplicationAlreadyApprovedException  BUYER 以外、または承認済み申請がある場合（409）
     */
    @Auditable(action = "SELLER_APPLICATION_SUBMITTED", entityType = "SELLER_APPLICATION")
    public SellerApplicationResponse apply(UUID applicantId, CreateSellerApplicationRequest request) {
        User applicant = userRepository.findByIdOrThrow(applicantId);
        // Filter Chain にロールガードを置いていないため、このロール判定が
        // POST /seller-applications の唯一の認可ポイントになる。消さないこと
        if (applicant.getRole() != UserRole.ROLE_BUYER) {
            throw new SellerApplicationAlreadyApprovedException();
        }
        if (sellerApplicationRepository.existsByApplicantIdAndStatus(
                applicantId, SellerApplicationStatus.PENDING)) {
            throw new SellerApplicationPendingException();
        }
        // 承認時はロールが昇格するため上のロール判定と本来は同値だが、承認処理が未実装の間は
        // 「APPROVED 申請はあるがロールが未昇格」という不整合状態が起こりうる。二重に塞ぐ
        if (sellerApplicationRepository.existsByApplicantIdAndStatus(
                applicantId, SellerApplicationStatus.APPROVED)) {
            throw new SellerApplicationAlreadyApprovedException();
        }
        // status は Entity の @Builder.Default（PENDING）に委ね、リクエストからは受け取らない
        SellerApplication application = SellerApplication.builder()
                .applicantId(applicantId)
                .reason(request.reason())
                .build();
        return SellerApplicationResponse.from(sellerApplicationRepository.save(application));
    }

    /**
     * 認証中ユーザーの最新の申請 1 件を取得します。
     *
     * @throws ResourceNotFoundException 申請が 1 件も無い場合（404・「未申請」を表す正常状態）
     */
    @Transactional(readOnly = true)
    public SellerApplicationResponse getMyLatest(UUID applicantId) {
        return sellerApplicationRepository.findFirstByApplicantIdOrderByCreatedAtDesc(applicantId)
                .map(SellerApplicationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("セラー申請が見つかりません"));
    }
}
