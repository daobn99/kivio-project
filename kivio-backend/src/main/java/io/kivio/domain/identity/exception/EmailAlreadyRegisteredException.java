package io.kivio.domain.identity.exception;

import io.kivio.common.exception.ConflictException;

/**
 * メールアドレス重複登録例外を表現します。
 */
public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException() {
        super("EMAIL_ALREADY_REGISTERED", "このメールアドレスは既に登録されています");
    }
}
