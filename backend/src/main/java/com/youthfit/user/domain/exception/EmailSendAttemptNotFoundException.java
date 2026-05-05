package com.youthfit.user.domain.exception;

public class EmailSendAttemptNotFoundException extends RuntimeException {
    public EmailSendAttemptNotFoundException(Long id) {
        super("EmailSendAttempt not found: " + id);
    }
}
