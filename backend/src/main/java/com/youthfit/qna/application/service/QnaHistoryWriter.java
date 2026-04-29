package com.youthfit.qna.application.service;

import com.youthfit.qna.domain.model.QnaFailedReason;
import com.youthfit.qna.domain.model.QnaHistory;
import com.youthfit.qna.domain.repository.QnaHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class QnaHistoryWriter {

    private static final Logger log = LoggerFactory.getLogger(QnaHistoryWriter.class);

    private final QnaHistoryRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startInProgress(Long userId, Long policyId, String question) {
        QnaHistory history = QnaHistory.builder()
                .userId(userId)
                .policyId(policyId)
                .question(question)
                .build();
        return repository.save(history).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long historyId, String answer, String sources) {
        repository.findById(historyId).ifPresentOrElse(
                history -> {
                    history.markCompleted(answer, sources);
                    repository.save(history);
                },
                () -> log.warn("markCompleted 대상 history 누락: id={}", historyId)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long historyId, QnaFailedReason reason) {
        repository.findById(historyId).ifPresentOrElse(
                history -> {
                    history.markFailed(reason);
                    repository.save(history);
                },
                () -> log.warn("markFailed 대상 history 누락: id={}, reason={}", historyId, reason)
        );
    }
}
