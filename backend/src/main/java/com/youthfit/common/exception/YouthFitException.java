package com.youthfit.common.exception;

import lombok.Getter;

@Getter
public class YouthFitException extends RuntimeException {

    private final ErrorCode errorCode;

    public YouthFitException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public YouthFitException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
