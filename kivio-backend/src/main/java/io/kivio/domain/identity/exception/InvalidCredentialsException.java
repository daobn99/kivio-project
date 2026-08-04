package io.kivio.domain.identity.exception;

import io.kivio.common.exception.UnauthorizedException;

/**
 * 認証情報不正例外を表現します。
 */
public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "メールアドレスまたはパスワードが正しくありません");
    }
}
