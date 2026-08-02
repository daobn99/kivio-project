package io.kivio.domain.identity.domain;

/**
 * 審査ステータスを表現します。
 */
public enum SellerApplicationStatus {
    /** 審査待ち */
    PENDING,
    /** 承認 */
    APPROVED,
    /** 却下 */
    REJECTED
}
