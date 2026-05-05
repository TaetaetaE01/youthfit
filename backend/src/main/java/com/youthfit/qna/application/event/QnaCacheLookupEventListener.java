package com.youthfit.qna.application.event;

import com.youthfit.qna.domain.model.QnaCacheLookupLog;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QnaCacheLookupEventListener {

    private final QnaCacheLookupLogRepository repository;

    @Async
    @EventListener
    public void onLookup(QnaCacheLookupEvent event) {
        try {
            QnaCacheLookupLog logEntity = QnaCacheLookupLog.of(
                    event.policyId(),
                    event.questionText(),
                    event.normalizedText(),
                    event.result(),
                    event.matchedCachedId(),
                    event.similarityScore(),
                    event.thresholdApplied(),
                    event.llmCallMade(),
                    event.lookedUpAt()
            );
            repository.save(logEntity);
        } catch (Exception e) {
            log.warn("Q&A 캐시 lookup 로그 적재 실패 (정상 흐름 진행): policyId={}, result={}",
                    event.policyId(), event.result(), e);
        }
    }
}
