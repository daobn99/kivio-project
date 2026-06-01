package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

public class TokenInvalidException extends KivioException {

    public TokenInvalidException() {
        super("TOKEN_INVALID", "トークンが無効です", HttpStatus.UNAUTHORIZED);
    }
}
