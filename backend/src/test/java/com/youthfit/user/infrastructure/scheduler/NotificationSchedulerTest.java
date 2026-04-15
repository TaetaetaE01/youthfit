package com.youthfit.user.infrastructure.scheduler;

import com.youthfit.user.application.service.NotificationScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@DisplayName("NotificationScheduler")
@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @InjectMocks
    private NotificationScheduler notificationScheduler;

    @Mock
    private NotificationScheduleService notificationScheduleService;

    @Test
    @DisplayName("스케줄러 실행 시 NotificationScheduleService에 위임한다")
    void sendDeadlineNotifications_delegatesToService() {
        // when
        notificationScheduler.sendDeadlineNotifications();

        // then
        then(notificationScheduleService).should().sendDeadlineNotifications();
    }
}
