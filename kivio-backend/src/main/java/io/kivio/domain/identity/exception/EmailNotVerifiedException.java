package io.kivio.domain.identity.exception;

import io.kivio.common.exception.ForbiddenException;

/**
 * メールアドレス未確認例外を表現します。
 */
public class EmailNotVerifiedException extends ForbiddenException {

    public EmailNotVerifiedException() {
        super("EMAIL_NOT_VERIFIED", "メールアドレスの確認が完了していません。確認メールをご確認ください");
    }
}
