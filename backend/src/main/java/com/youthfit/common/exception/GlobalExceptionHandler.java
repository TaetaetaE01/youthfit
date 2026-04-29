package com.youthfit.common.exception;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.policy.domain.exception.AttachmentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(YouthFitException.class)
    public ResponseEntity<ApiResponse<Void>> handleYouthFitException(
            YouthFitException e, HttpServletRequest request) {
        log.warn("YouthFitException: {} - {}", e.getErrorCode().getCode(), e.getMessage());
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), e.getMessage()));
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAttachmentNotFound(AttachmentNotFoundException e) {
        log.warn("attachment not found: id={}", e.getAttachmentId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("ATTACHMENT_NOT_FOUND", "첨부 파일을 찾을 수 없습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }
}
