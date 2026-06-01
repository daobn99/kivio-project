package io.kivio.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends KivioException {

    public ResourceNotFoundException(String detail) {
        super("RESOURCE_NOT_FOUND", detail, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String entityName, Object id) {
        super("RESOURCE_NOT_FOUND",
                "ID '%s' の %s は存在しないか削除されています".formatted(id, entityName),
                HttpStatus.NOT_FOUND);
    }
}
