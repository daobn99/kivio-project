package io.kivio.domain.identity.exception;

import io.kivio.common.exception.ConflictException;

/**
 * 既に出品者として承認済みのユーザーによる申請の例外を表現します。
 */
public class SellerApplicationAlreadyApprovedException extends ConflictException {

    public SellerApplicationAlreadyApprovedException() {
        super("SELLER_APPLICATION_ALREADY_APPROVED", "すでに出品者として承認されています");
    }
}
