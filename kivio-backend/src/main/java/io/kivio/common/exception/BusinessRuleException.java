package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

public abstract class BusinessRuleException extends KivioException {

    protected BusinessRuleException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
