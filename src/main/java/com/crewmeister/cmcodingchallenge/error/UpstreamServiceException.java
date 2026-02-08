package com.crewmeister.cmcodingchallenge.error;

public class UpstreamServiceException extends RuntimeException{

    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
