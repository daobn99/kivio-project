package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 不正なトークン例外を表現します。
 */
public class TokenInvalidException extends KivioException {

    public TokenInvalidException() {
        super("TOKEN_INVALID", "トークンが無効です", HttpStatus.UNAUTHORIZED);
    }
}
