package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends KivioException {

    protected ConflictException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }
}
