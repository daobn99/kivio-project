package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * ビジネスルール違反例外（422）の基底クラスを表現します。
 */
public abstract class BusinessRuleException extends KivioException {

    protected BusinessRuleException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
