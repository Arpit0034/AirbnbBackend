package com.airBnb.application.AirBnbApp.exception;

public class UnAuthorisedException extends RuntimeException {
    public UnAuthorisedException(String message){
        super(message) ;
    }
}
