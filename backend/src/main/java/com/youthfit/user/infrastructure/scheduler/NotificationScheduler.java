package com.youthfit.user.infrastructure.scheduler;

import com.youthfit.user.application.service.NotificationScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationScheduleService notificationScheduleService;

    @Scheduled(cron = "0 0 9 * * *")
    public void sendDeadlineNotifications() {
        log.info("마감일 알림 스케줄러 실행");
        notificationScheduleService.sendDeadlineNotifications();
        log.info("마감일 알림 스케줄러 완료");
    }
}
