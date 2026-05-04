package com.youthfit.user.application.service;

import com.youthfit.user.application.dto.command.UpdateNotificationSettingCommand;
import com.youthfit.user.application.dto.result.NotificationSettingResult;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationSettingServiceTest {

    @Test
    void updateNotificationSetting은_recommendationEnabled까지_저장한다() {
        NotificationSettingRepository repo = mock(NotificationSettingRepository.class);
        NotificationSetting existing = new NotificationSetting(1L);
        when(repo.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationSettingService service = new NotificationSettingService(repo);

        NotificationSettingResult result = service.updateNotificationSetting(
                1L, new UpdateNotificationSettingCommand(false, 14, true));

        assertThat(result.emailEnabled()).isFalse();
        assertThat(result.daysBeforeDeadline()).isEqualTo(14);
        assertThat(result.recommendationEnabled()).isTrue();
    }
}
