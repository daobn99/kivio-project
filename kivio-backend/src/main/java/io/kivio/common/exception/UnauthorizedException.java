package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 認証エラー例外（401）の基底クラスを表現します。
 */
public abstract class UnauthorizedException extends KivioException {

    protected UnauthorizedException(String code, String message) {
        super(code, message, HttpStatus.UNAUTHORIZED);
    }
}
