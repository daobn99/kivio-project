package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends KivioException {

    public TokenExpiredException() {
        super("TOKEN_EXPIRED", "アクセストークンの有効期限が切れています", HttpStatus.UNAUTHORIZED);
    }
}
