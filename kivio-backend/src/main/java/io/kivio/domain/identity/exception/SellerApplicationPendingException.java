package io.kivio.domain.identity.exception;

import io.kivio.common.exception.ConflictException;

/**
 * 審査中のセラー申請が既に存在する場合の例外を表現します。
 */
public class SellerApplicationPendingException extends ConflictException {

    public SellerApplicationPendingException() {
        super("SELLER_APPLICATION_PENDING", "審査中の申請があります。結果をお待ちください");
    }
}
