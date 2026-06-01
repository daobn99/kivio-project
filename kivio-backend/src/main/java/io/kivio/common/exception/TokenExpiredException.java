package io.kivio.common.exception;

import org.springframework.http.HttpStatusCode;

public class TokenExpiredException extends KivioException {

    public TokenExpiredException() {
        super("TOKEN_EXPIRED", "アクセストークンの有効期限が切れています", HttpStatusCode.valueOf(401));
    }
}
