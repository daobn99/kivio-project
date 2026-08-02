package io.kivio.domain.identity.dto.response;

import io.kivio.domain.identity.domain.SellerApplication;
import io.kivio.domain.identity.domain.SellerApplicationStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * セラー申請レスポンスを表現します。
 *
 * <p>
 * {@code POST} と {@code GET /me} で共用する単一 DTO です。
 * {@code spring.jackson.default-property-inclusion: non_null} により、未審査の申請では
 * {@code reviewComment} / {@code reviewedAt} がレスポンス JSON から省略されます。
 */
@Builder
public record SellerApplicationResponse(
        /** セラー申請ID */
        UUID id,
        /** 申請者ユーザーID */
        UUID applicantId,
        /** 申請理由 */
        String reason,
        /** 審査ステータス */
        SellerApplicationStatus status,
        /** 審査コメント */
        String reviewComment,
        /** 審査日時 */
        Instant reviewedAt,
        /** 作成日時（ISO 8601 UTC） */
        Instant createdAt) {
    public static SellerApplicationResponse from(SellerApplication application) {
        return SellerApplicationResponse.builder()
                .id(application.getId())
                .applicantId(application.getApplicantId())
                .reason(application.getReason())
                .status(application.getStatus())
                .reviewComment(application.getReviewComment())
                .reviewedAt(application.getReviewedAt())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
