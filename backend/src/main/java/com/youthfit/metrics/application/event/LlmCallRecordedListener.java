package com.youthfit.metrics.application.event;

import com.youthfit.metrics.application.service.LlmCostBucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCallRecordedListener {

    private final LlmCostBucketService service;

    @Async
    @EventListener
    public void onLlmCall(LlmCallRecorded event) {
        try {
            service.recordCall(event);
        } catch (Exception e) {
            log.warn("LLM 비용 적재 실패 (정상 흐름 진행): module={}, model={}, calledAt={}",
                    event.module(), event.model(), event.calledAt(), e);
        }
    }
}
