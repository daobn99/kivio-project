package io.kivio.common.exception;

import org.springframework.http.HttpStatusCode;

public class TokenInvalidException extends KivioException {

    public TokenInvalidException() {
        super("TOKEN_INVALID", "トークンが無効です", HttpStatusCode.valueOf(401));
    }
}
