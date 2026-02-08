package com.crewmeister.cmcodingchallenge.error;

public class RateNotFoundException extends RuntimeException{

    public  RateNotFoundException(String message){
        super(message);
    }
}
