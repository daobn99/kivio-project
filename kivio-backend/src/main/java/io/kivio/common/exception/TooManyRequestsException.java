package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * リクエスト過多例外（429）の基底クラスを表現します。
 */
public abstract class TooManyRequestsException extends KivioException {

    protected TooManyRequestsException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
