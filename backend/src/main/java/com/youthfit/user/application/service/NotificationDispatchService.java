package com.youthfit.user.application.service;

import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long historyId) {
        repository.findById(historyId)
                .ifPresent(h -> h.markSent(LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long historyId, String reason) {
        repository.findById(historyId)
                .ifPresent(h -> h.markFailed(LocalDateTime.now(), reason));
    }
}
