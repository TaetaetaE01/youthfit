package com.youthfit.user.application.service;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.exception.EmailSendException;
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

import java.util.ArrayList;
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
    private final NotificationDispatchService dispatchService;
    private final EmailSender emailSender;
    private final PolicyRecommender recommender;

    /**
     * v0: 사용자당 후보 N건 × judgeEligibility/exists 호출 (N+1 패턴).
     * 메서드 레벨 @Transactional 제거 — SES 외부 IO 동안 DB 커넥션 점유 방지.
     * 상태 전이는 NotificationDispatchService 의 REQUIRES_NEW 메서드들이 담당.
     */
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
        if (picks.isEmpty()) return;

        // 발송 전에 모든 pick 에 대해 PENDING 행 예약
        List<NotificationHistory> reserved = new ArrayList<>();
        List<Policy> toSend = new ArrayList<>();
        for (Policy p : picks) {
            NotificationHistory h = dispatchService.reservePending(userId, p.getId(), NotificationType.RECOMMENDATION);
            if (h != null) {
                reserved.add(h);
                toSend.add(p);
            }
        }
        if (toSend.isEmpty()) return;   // 모두 이미 처리됨

        try {
            emailSender.sendRecommendationNotification(user.getEmail(), toSend);
            for (NotificationHistory h : reserved) {
                dispatchService.markSent(h.getId());
            }
            log.info("추천 알림 발송 완료 userId={} count={}", userId, toSend.size());
        } catch (EmailSendException e) {
            for (NotificationHistory h : reserved) {
                dispatchService.markFailed(h.getId(), e.getMessage());
            }
            log.error("추천 알림 발송 실패 userId={} count={}", userId, toSend.size(), e);
        } catch (Exception e) {
            log.error("추천 알림 처리 중 예외 userId={}", userId, e);
        }
    }
}
