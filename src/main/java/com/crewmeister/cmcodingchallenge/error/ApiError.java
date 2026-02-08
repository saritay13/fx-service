package com.crewmeister.cmcodingchallenge.error;


import java.time.Instant;

public record ApiError(
        int status,
        String error,
        String message,
        String path
) {}