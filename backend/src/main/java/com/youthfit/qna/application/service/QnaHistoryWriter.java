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

/**
 * QnaHistory 상태 전이 전용 라이터.
 * <p>
 * 모든 메서드는 {@link Propagation#REQUIRES_NEW} 로 외부 트랜잭션과 분리한다.
 * 현재 호출 측인 {@code QnaService.askQuestion} 은 SSE 스트리밍 특성상
 * {@code @Transactional} 을 두지 않으므로 실효적으로는 {@code REQUIRED} 와 동일하지만,
 * 향후 호출 측이 트랜잭션 안에 들어가더라도 답변 생성 실패가 history 기록까지
 * 롤백시키지 않도록 방어적으로 별도 트랜잭션을 잡는다.
 */
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
                () -> log.error("markCompleted 대상 history 누락 (state divergence): id={}", historyId)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long historyId, QnaFailedReason reason) {
        repository.findById(historyId).ifPresentOrElse(
                history -> {
                    history.markFailed(reason);
                    repository.save(history);
                },
                () -> log.error("markFailed 대상 history 누락 (state divergence): id={}, reason={}", historyId, reason)
        );
    }
}
