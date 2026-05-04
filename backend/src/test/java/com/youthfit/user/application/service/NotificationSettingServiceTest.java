package com.youthfit.user.application.service;

import com.youthfit.user.application.dto.command.UpdateNotificationSettingCommand;
import com.youthfit.user.application.dto.result.NotificationSettingResult;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("NotificationSettingService")
@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @InjectMocks
    private NotificationSettingService notificationSettingService;

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Nested
    @DisplayName("findNotificationSetting")
    class FindNotificationSetting {

        @Test
        @DisplayName("기존 설정이 있으면 해당 설정을 반환한다")
        void existingSetting_returnsIt() {
            NotificationSetting setting = new NotificationSetting(1L);
            given(notificationSettingRepository.findByUserId(1L))
                    .willReturn(Optional.of(setting));

            NotificationSettingResult result = notificationSettingService.findNotificationSetting(1L);

            assertThat(result.emailEnabled()).isTrue();
            assertThat(result.daysBeforeDeadline()).isEqualTo(7);
            assertThat(result.recommendationEnabled()).isFalse();
            then(notificationSettingRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("기존 설정이 없으면 기본 설정을 생성하여 반환한다")
        void noSetting_createsDefault() {
            NotificationSetting newSetting = new NotificationSetting(1L);
            given(notificationSettingRepository.findByUserId(1L))
                    .willReturn(Optional.empty());
            given(notificationSettingRepository.save(any()))
                    .willReturn(newSetting);

            NotificationSettingResult result = notificationSettingService.findNotificationSetting(1L);

            assertThat(result.emailEnabled()).isTrue();
            assertThat(result.daysBeforeDeadline()).isEqualTo(7);
            assertThat(result.recommendationEnabled()).isFalse();
            then(notificationSettingRepository).should().save(any());
        }
    }

    @Nested
    @DisplayName("updateNotificationSetting")
    class UpdateNotificationSetting {

        @Test
        @DisplayName("기존 설정이 있으면 세 필드를 모두 변경하고 반환한다")
        void existingSetting_updatesAndReturns() {
            NotificationSetting setting = new NotificationSetting(1L);
            given(notificationSettingRepository.findByUserId(1L))
                    .willReturn(Optional.of(setting));
            UpdateNotificationSettingCommand command = new UpdateNotificationSettingCommand(false, 3, true, java.util.Set.of(), java.util.Set.of());

            NotificationSettingResult result = notificationSettingService.updateNotificationSetting(1L, command);

            assertThat(result.emailEnabled()).isFalse();
            assertThat(result.daysBeforeDeadline()).isEqualTo(3);
            assertThat(result.recommendationEnabled()).isTrue();
        }

        @Test
        @DisplayName("기존 설정이 없으면 새로 생성한 뒤 값을 변경하고 반환한다")
        void noSetting_createsAndUpdates() {
            NotificationSetting newSetting = new NotificationSetting(1L);
            given(notificationSettingRepository.findByUserId(1L))
                    .willReturn(Optional.empty());
            given(notificationSettingRepository.save(any()))
                    .willReturn(newSetting);
            UpdateNotificationSettingCommand command = new UpdateNotificationSettingCommand(false, 5, true, java.util.Set.of(), java.util.Set.of());

            NotificationSettingResult result = notificationSettingService.updateNotificationSetting(1L, command);

            assertThat(result.emailEnabled()).isFalse();
            assertThat(result.daysBeforeDeadline()).isEqualTo(5);
            assertThat(result.recommendationEnabled()).isTrue();
        }

        @Test
        @DisplayName("recommendationEnabled만 단독으로 변경할 수 있다")
        void update_recommendationOnly() {
            NotificationSetting setting = new NotificationSetting(1L);
            given(notificationSettingRepository.findByUserId(1L))
                    .willReturn(Optional.of(setting));
            UpdateNotificationSettingCommand command = new UpdateNotificationSettingCommand(true, 7, true, java.util.Set.of(), java.util.Set.of());

            NotificationSettingResult result = notificationSettingService.updateNotificationSetting(1L, command);

            assertThat(result.emailEnabled()).isTrue();
            assertThat(result.daysBeforeDeadline()).isEqualTo(7);
            assertThat(result.recommendationEnabled()).isTrue();
        }
    }
}
