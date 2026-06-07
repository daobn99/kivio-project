package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 不正リクエスト例外（400）の基底クラスを表現します。
 */
public abstract class BadRequestException extends KivioException {

    protected BadRequestException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.BAD_REQUEST);
    }
}
