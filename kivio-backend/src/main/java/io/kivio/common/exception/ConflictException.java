package io.kivio.common.exception;

import org.springframework.http.HttpStatusCode;

public class ConflictException extends KivioException {

    protected ConflictException(String errorCode, String message) {
        super(errorCode, message, HttpStatusCode.valueOf(409));
    }
}
