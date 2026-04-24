package pl.kmalysiak.notificator.service;

import lombok.Getter;

@Getter
public class FcmException extends RuntimeException {

    private final ErrorCode errorCode;

    public FcmException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public enum ErrorCode {INVALID_TOKEN, TRANSIENT, RATE_LIMITED, UNKNOWN}
}
