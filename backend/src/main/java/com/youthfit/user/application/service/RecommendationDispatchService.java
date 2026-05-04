package com.youthfit.user.application.service;

import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationDispatchService {

    private final NotificationSettingRepository settingRepository;
    private final RecommendationOneDispatcher oneDispatcher;

    public void dispatchWeekly() {
        List<NotificationSetting> settings = settingRepository.findAllByRecommendationEnabled(true);

        for (NotificationSetting setting : settings) {
            try {
                oneDispatcher.dispatchOne(setting);
            } catch (Exception e) {
                log.error("추천 발송 실패 userId={}", setting.getUserId(), e);
            }
        }
    }
}
