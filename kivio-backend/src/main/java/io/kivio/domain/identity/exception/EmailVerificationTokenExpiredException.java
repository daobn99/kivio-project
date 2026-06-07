package io.kivio.domain.identity.exception;

import io.kivio.common.exception.BadRequestException;

/**
 * メールアドレス確認トークン有効期限切れ例外を表現します。
 */
public class EmailVerificationTokenExpiredException extends BadRequestException {

    public EmailVerificationTokenExpiredException() {
        super("EMAIL_VERIFICATION_TOKEN_EXPIRED", "メール認証トークンの有効期限が切れています。再度登録してください");
    }
}
