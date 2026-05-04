package com.youthfit.user.application.service;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.BookmarkRepository;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.domain.repository.UserRepository;
import com.youthfit.user.domain.service.PolicyRecommender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationOneDispatcher {

    private final UserRepository userRepository;
    private final EligibilityProfileRepository profileRepository;
    private final PolicyRepository policyRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NotificationHistoryRepository historyRepository;
    private final EligibilityService eligibilityService;
    private final EmailSender emailSender;
    private final PolicyRecommender recommender;

    // v0: 사용자당 후보 N건 × judgeEligibility/exists 호출 (N+1 패턴).
    //     룰 평가는 LLM 호출 없이 인메모리 매칭이라 v0 규모에서 안전.
    //     스케일 시 batch judge 또는 추천 사전 인덱스로 개선.
    @Transactional
    public void dispatchOne(NotificationSetting setting) {
        Long userId = setting.getUserId();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        EligibilityProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (!setting.canDispatchRecommendation(profile)) {
            return;
        }

        List<Policy> openPolicies = policyRepository.findAllByStatus(PolicyStatus.OPEN);
        List<Policy> matched = recommender.filterByInterest(setting, openPolicies);

        List<Policy> notSeen = matched.stream()
                .filter(p -> !bookmarkRepository.existsByUserIdAndPolicyId(userId, p.getId()))
                .filter(p -> !historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                        userId, p.getId(), NotificationType.RECOMMENDATION))
                .toList();

        List<Policy> eligible = notSeen.stream()
                .filter(p -> {
                    EligibilityJudgmentResult result = eligibilityService.judgeEligibility(
                            userId, new JudgeEligibilityCommand(p.getId()));
                    return EligibilityResult.LIKELY_ELIGIBLE.name().equals(result.overallResult());
                })
                .toList();

        List<Policy> picks = recommender.sortAndLimit(eligible);
        if (picks.isEmpty()) {
            return;
        }

        emailSender.sendRecommendationNotification(user.getEmail(), picks);

        for (Policy p : picks) {
            historyRepository.save(new NotificationHistory(userId, p.getId(), NotificationType.RECOMMENDATION));
        }
    }
}
