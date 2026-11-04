package com.phytion.dashboard.adapter.ci;

public class CiToolException extends RuntimeException {

    public CiToolException(String message) {
        super(message);
    }

    public CiToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
