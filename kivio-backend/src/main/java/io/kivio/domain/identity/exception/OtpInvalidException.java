package io.kivio.domain.identity.exception;

import io.kivio.common.exception.BadRequestException;

/**
 * 認証コード（OTP）不一致例外を表現します。
 */
public class OtpInvalidException extends BadRequestException {

    public OtpInvalidException() {
        super("OTP_INVALID", "認証コードが正しくありません");
    }
}
