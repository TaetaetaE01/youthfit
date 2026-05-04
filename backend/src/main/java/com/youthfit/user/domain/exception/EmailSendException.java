package com.youthfit.user.domain.exception;

/**
 * 이메일 발송 어댑터에서 외부 게이트웨이(SES 등) 호출이 실패했을 때 던지는 도메인 예외.
 * 호출자(NotificationScheduleService 등) 가 catch 하여 NotificationHistory 를 FAILED 로 마킹한다.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
