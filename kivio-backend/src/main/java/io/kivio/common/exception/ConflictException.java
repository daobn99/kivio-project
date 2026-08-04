package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * リソース競合例外（409）を表現します。
 */
public class ConflictException extends KivioException {

    protected ConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}
