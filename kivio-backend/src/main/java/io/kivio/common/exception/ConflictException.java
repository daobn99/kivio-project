package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * リソース競合例外（409）を表現します。
 */
public class ConflictException extends KivioException {

    protected ConflictException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }
}
