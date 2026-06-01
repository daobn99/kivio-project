package io.kivio.common.exception;

import org.springframework.http.HttpStatusCode;

public abstract class BusinessRuleException extends KivioException {

    protected BusinessRuleException(String errorCode, String message) {
        super(errorCode, message, HttpStatusCode.valueOf(422));
    }
}
