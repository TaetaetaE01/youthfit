package com.youthfit.user.infrastructure.scheduler;

import com.youthfit.user.application.service.RecommendationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private final RecommendationDispatchService dispatchService;

    @Scheduled(cron = "0 0 9 ? * MON")
    public void sendWeekly() {
        log.info("주간 추천 알림 스케줄러 실행");
        dispatchService.dispatchWeekly();
        log.info("주간 추천 알림 스케줄러 종료");
    }
}
