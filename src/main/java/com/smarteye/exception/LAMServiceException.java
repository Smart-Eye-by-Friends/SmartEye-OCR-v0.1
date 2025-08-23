package com.smarteye.exception;

/**
 * LAM 서비스 관련 예외 클래스
 */
public class LAMServiceException extends RuntimeException {

    public LAMServiceException(String message) {
        super(message);
    }

    public LAMServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public LAMServiceException(Throwable cause) {
        super(cause);
    }
}
