package com.ranju.fenbi.api;

public class FenbiException extends Exception {
    public FenbiException(String message) {
        super(message);
    }

    public FenbiException(String message, Throwable cause) {
        super(message, cause);
    }
}
