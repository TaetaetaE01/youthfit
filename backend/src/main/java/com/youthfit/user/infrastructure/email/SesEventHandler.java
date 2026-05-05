package com.youthfit.user.infrastructure.email;

import com.youthfit.user.domain.model.EmailSendAttempt;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SesEventHandler {

    private final EmailSendAttemptRepository repository;
    private final Clock clock;

    @Transactional
    public void handle(SesEvent event) {
        Optional<EmailSendAttempt> maybeAttempt = repository.findBySesMessageId(event.messageId());
        if (maybeAttempt.isEmpty()) {
            log.warn("매칭되는 EmailSendAttempt 없음 — 무시 messageId={} eventType={}",
                     event.messageId(), event.eventType());
            return;
        }
        EmailSendAttempt attempt = maybeAttempt.get();
        LocalDateTime now = LocalDateTime.now(clock);

        try {
            switch (event.eventType()) {
                case DELIVERY -> attempt.markDelivered(now);
                case BOUNCE -> attempt.markBounced(now, event.bounceType(), event.diagnosticReason());
                case COMPLAINT -> attempt.markComplained(now);
            }
            repository.save(attempt);
        } catch (IllegalStateException e) {
            log.warn("상태 전이 거절 attemptId={} status={} eventType={}",
                     attempt.getId(), attempt.getStatus(), event.eventType(), e);
        }
    }
}
