package io.kivio.domain.identity.exception;

import io.kivio.common.exception.BadRequestException;

/**
 * 認証コード（OTP）有効期限切れ例外を表現します。
 */
public class OtpExpiredException extends BadRequestException {

    public OtpExpiredException() {
        super("OTP_EXPIRED", "認証コードの有効期限が切れました。コードを再送信してください");
    }
}
