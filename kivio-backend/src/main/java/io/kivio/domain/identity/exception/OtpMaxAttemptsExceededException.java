package io.kivio.domain.identity.exception;

import io.kivio.common.exception.TooManyRequestsException;

/**
 * 認証コード（OTP）検証試行回数超過例外を表現します。
 */
public class OtpMaxAttemptsExceededException extends TooManyRequestsException {

    public OtpMaxAttemptsExceededException() {
        super("OTP_MAX_ATTEMPTS_EXCEEDED", "認証コードの試行回数が上限に達しました。コードを再送信してください");
    }
}
