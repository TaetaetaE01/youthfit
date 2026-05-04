package com.youthfit.user.application.service;

import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationHistoryRepository repository;

    /**
     * PENDING 행을 별도 트랜잭션(REQUIRES_NEW)으로 INSERT 하고 즉시 commit 한다.
     * 동일 (userId, policyId, type) 행이 이미 존재하면 null 을 반환하여 호출자가 skip 하도록 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationHistory reservePending(Long userId, Long policyId, NotificationType type) {
        if (repository.existsByUserIdAndPolicyIdAndNotificationType(userId, policyId, type)) {
            return null;
        }
        try {
            return repository.save(NotificationHistory.pending(userId, policyId, type));
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 동일 키로 INSERT 한 race condition
            return null;
        }
    }

    /**
     * PENDING 행을 SENT 로 전이한다. 멱등하지 않음 — 이미 SENT/FAILED 인 경우 IllegalStateException 을 던진다.
     * 호출자(스케줄러/디스패처)는 이를 catch 하지 않고 자연 전파시켜 잘못된 흐름을 노출한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long historyId) {
        repository.findById(historyId)
                .ifPresentOrElse(
                        h -> h.markSent(LocalDateTime.now()),
                        () -> log.error("NotificationHistory 누락 (state divergence): id={}", historyId));
    }

    /**
     * PENDING 행을 FAILED 로 전이하고 실패 사유를 저장한다. 멱등하지 않음 — 이미 SENT/FAILED 인 경우 IllegalStateException 을 던진다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long historyId, String reason) {
        repository.findById(historyId)
                .ifPresentOrElse(
                        h -> h.markFailed(LocalDateTime.now(), reason),
                        () -> log.error("NotificationHistory 누락 (state divergence): id={}", historyId));
    }
}
