package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 認可エラー例外（403）の基底クラスを表現します。
 */
public abstract class ForbiddenException extends KivioException {

    protected ForbiddenException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.FORBIDDEN);
    }
}
