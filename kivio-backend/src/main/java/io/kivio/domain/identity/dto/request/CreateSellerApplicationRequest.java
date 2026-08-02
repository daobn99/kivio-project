package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * セラー申請リクエストを表現します。
 *
 * <p>
 * 受け取るのは {@code reason} の 1 つだけです。{@code status} / {@code reviewerId} /
 * {@code reviewComment} / {@code reviewedAt} はサーバーが決める値であり、本 DTO に
 * フィールドを持たないことが mass assignment に対する唯一の防御線です
 * （{@code fail-on-unknown-properties: false} のため未知フィールドは黙って無視されます）。
 *
 * <p>
 * 上限 1000 文字は DB 上の制約ではなくアプリケーション上の方針値です（列型は TEXT）。
 * 下限は {@code @Size(min = 1)} ではなく {@code @NotBlank} で担保します
 * （{@code @Size(min = 1)} は空白のみの文字列を通してしまうため）。
 */
public record CreateSellerApplicationRequest(
        /** 申請理由 */
        @NotBlank(message = "申請理由を入力してください") @Size(max = 1000, message = "申請理由は1000文字以内で入力してください") String reason) {
}
