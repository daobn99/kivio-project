package io.kivio.domain.identity.exception;

import io.kivio.common.exception.UnauthorizedException;

/**
 * Google IDトークン検証失敗例外を表現します。
 */
public class GoogleTokenInvalidException extends UnauthorizedException {

    public GoogleTokenInvalidException() {
        super("GOOGLE_TOKEN_INVALID", "Google IDトークンの検証に失敗しました");
    }
}
