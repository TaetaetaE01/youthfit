package com.youthfit.user.application.service;

import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@DisplayName("RecommendationDispatchService")
@ExtendWith(MockitoExtension.class)
class RecommendationDispatchServiceTest {

    @InjectMocks
    private RecommendationDispatchService dispatchService;

    @Mock
    private NotificationSettingRepository settingRepository;

    @Mock
    private RecommendationOneDispatcher oneDispatcher;

    @Test
    @DisplayName("recommendationEnabled=true 인 모든 설정에 대해 dispatchOne을 호출한다")
    void dispatchesForAllEnabledSettings() {
        // given
        NotificationSetting s1 = new NotificationSetting(1L);
        NotificationSetting s2 = new NotificationSetting(2L);
        given(settingRepository.findAllByRecommendationEnabled(true)).willReturn(List.of(s1, s2));

        // when
        dispatchService.dispatchWeekly();

        // then
        then(oneDispatcher).should().dispatchOne(s1);
        then(oneDispatcher).should().dispatchOne(s2);
    }

    @Test
    @DisplayName("한 사용자 dispatchOne 실패해도 다른 사용자 처리는 계속된다")
    void singleFailure_doesNotInterruptOthers() {
        // given
        NotificationSetting s1 = new NotificationSetting(1L);
        NotificationSetting s2 = new NotificationSetting(2L);
        NotificationSetting s3 = new NotificationSetting(3L);
        given(settingRepository.findAllByRecommendationEnabled(true)).willReturn(List.of(s1, s2, s3));
        willThrow(new RuntimeException("boom")).given(oneDispatcher).dispatchOne(s2);

        // when
        dispatchService.dispatchWeekly();

        // then
        then(oneDispatcher).should(times(3)).dispatchOne(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("활성 설정이 없으면 dispatchOne을 호출하지 않는다")
    void noSettings_doesNothing() {
        // given
        given(settingRepository.findAllByRecommendationEnabled(true)).willReturn(List.of());

        // when
        dispatchService.dispatchWeekly();

        // then
        then(oneDispatcher).shouldHaveNoInteractions();
    }
}
