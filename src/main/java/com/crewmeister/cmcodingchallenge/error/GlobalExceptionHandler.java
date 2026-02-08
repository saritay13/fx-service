package com.crewmeister.cmcodingchallenge.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiError> handleUpstreamException(UpstreamServiceException ex, HttpServletRequest request){
       return build(HttpStatus.SERVICE_UNAVAILABLE, "Upstream service error", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        LOGGER.error("Internal server error Unexpected error occurred method={}, uri={}",
                request.getMethod(),
                request.getRequestURI(),
                ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Unexpected error occurred", request.getRequestURI());
    }

    @ExceptionHandler(RateNotFoundException.class)
    public ResponseEntity<ApiError> handleRateNotFoundExcetion(RateNotFoundException ex, HttpServletRequest request){
        return build(HttpStatus.NOT_FOUND, "Rate not found", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequestException(InvalidRequestException ex, HttpServletRequest request){
        return build(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), request.getRequestURI());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message, String path) {
        ApiError body = new ApiError(status.value(), error, message, path);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
