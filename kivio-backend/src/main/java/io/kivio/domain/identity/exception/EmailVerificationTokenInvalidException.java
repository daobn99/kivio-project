package io.kivio.domain.identity.exception;

import io.kivio.common.exception.BadRequestException;

/**
 * メールアドレス確認トークン無効例外を表現します。
 */
public class EmailVerificationTokenInvalidException extends BadRequestException {

    public EmailVerificationTokenInvalidException() {
        super("EMAIL_VERIFICATION_TOKEN_INVALID", "メール認証トークンが無効または使用済みです");
    }
}
