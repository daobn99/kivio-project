package io.kivio.domain.identity.repository;

import io.kivio.domain.identity.domain.SellerApplication;
import io.kivio.domain.identity.domain.SellerApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * セラー申請リポジトリを表現します。
 *
 * <p>
 * どちらのクエリも {@code idx_seller_applications_applicant_id} が効きます。
 */
public interface SellerApplicationRepository extends JpaRepository<SellerApplication, UUID> {

    boolean existsByApplicantIdAndStatus(UUID applicantId, SellerApplicationStatus status);

    /**
     * 申請者の最新 1 件を取得します（申請履歴の一覧 API は仕様に存在しません）。
     */
    Optional<SellerApplication> findFirstByApplicantIdOrderByCreatedAtDesc(UUID applicantId);
}
