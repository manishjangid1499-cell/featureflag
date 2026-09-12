package com.featureflag.flag_service.exception;

/** A valid request that cannot be applied under the domain's rules. */
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
