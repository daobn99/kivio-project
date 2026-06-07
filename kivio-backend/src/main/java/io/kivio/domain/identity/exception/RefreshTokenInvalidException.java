package io.kivio.domain.identity.exception;

import io.kivio.common.exception.UnauthorizedException;

/**
 * リフレッシュトークン無効例外を表現します。
 */
public class RefreshTokenInvalidException extends UnauthorizedException {

    public RefreshTokenInvalidException() {
        super("REFRESH_TOKEN_INVALID", "リフレッシュトークンが無効・失効・または期限切れです");
    }
}
