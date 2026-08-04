package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * アクセストークン有効期限切れ例外を表現します。
 */
public class TokenExpiredException extends KivioException {

    public TokenExpiredException() {
        super("TOKEN_EXPIRED", "アクセストークンの有効期限が切れています", HttpStatus.UNAUTHORIZED);
    }
}
