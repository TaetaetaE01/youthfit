package com.youthfit.user.application.service;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.email.EmailDispatcher;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.RegionSidoCode;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.BookmarkRepository;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.domain.repository.UserRepository;
import com.youthfit.user.domain.service.PolicyRecommender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("RecommendationOneDispatcher")
@ExtendWith(MockitoExtension.class)
class RecommendationOneDispatcherTest {

    @InjectMocks
    private RecommendationOneDispatcher dispatcher;

    @Mock private UserRepository userRepository;
    @Mock private EligibilityProfileRepository profileRepository;
    @Mock private PolicyRepository policyRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private NotificationHistoryRepository historyRepository;
    @Mock private EligibilityService eligibilityService;
    @Mock private NotificationDispatchService dispatchService;
    @Mock private EmailDispatcher emailDispatcher;
    @Spy  private PolicyRecommender recommender = new PolicyRecommender();

    private NotificationSetting enabledSetting;
    private EligibilityProfile profileFilled;

    @BeforeEach
    void setUp() {
        enabledSetting = new NotificationSetting(1L);
        enabledSetting.updateSetting(true, 7, true);
        enabledSetting.replaceInterestCategories(Set.of(Category.JOBS));
        enabledSetting.replaceInterestRegions(Set.of(RegionSidoCode.SEOUL));

        profileFilled = EligibilityProfile.empty(1L);
        profileFilled.changeLegalDongCode("1100000000");
        profileFilled.changeAge(28);
    }

    @Test
    @DisplayName("이메일 미등록 사용자는 발송하지 않는다")
    void noEmail_skips() {
        User user = User.builder().nickname("x").authProvider(AuthProvider.KAKAO).providerId("k_1").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        dispatcher.dispatchOne(enabledSetting);

        then(emailDispatcher).should(never()).dispatchRecommendation(any(), any(), any());
    }

    @Test
    @DisplayName("토글 OFF 사용자는 발송하지 않는다")
    void toggleOff_skips() {
        NotificationSetting offSetting = new NotificationSetting(1L);
        offSetting.updateSetting(true, 7, false);
        offSetting.replaceInterestCategories(Set.of(Category.JOBS));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));

        dispatcher.dispatchOne(offSetting);

        then(emailDispatcher).should(never()).dispatchRecommendation(any(), any(), any());
    }

    @Test
    @DisplayName("적합도 프로필 미입력자는 발송하지 않는다")
    void noProfile_skips() {
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.empty());

        dispatcher.dispatchOne(enabledSetting);

        then(emailDispatcher).should(never()).dispatchRecommendation(any(), any(), any());
    }

    @Test
    @DisplayName("후보 정책이 없으면 발송하지 않는다")
    void noCandidates_skips() {
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of());

        dispatcher.dispatchOne(enabledSetting);

        then(emailDispatcher).should(never()).dispatchRecommendation(any(), any(), any());
    }

    @Test
    @DisplayName("LIKELY_ELIGIBLE 정책만 통과하고 발송 후 markSent 호출")
    void onlyLikelyEligible_passed_andMarkSent() {
        Policy eligible = createPolicy(10L, Category.JOBS, "11", LocalDate.now().plusDays(5));
        Policy uncertain = createPolicy(11L, Category.JOBS, "11", LocalDate.now().plusDays(6));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of(eligible, uncertain));
        given(bookmarkRepository.existsByUserIdAndPolicyId(eq(1L), anyLong())).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION))).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willAnswer(inv -> {
                    JudgeEligibilityCommand cmd = inv.getArgument(1);
                    String r = cmd.policyId() == 10L
                            ? EligibilityResult.LIKELY_ELIGIBLE.name()
                            : EligibilityResult.UNCERTAIN.name();
                    return new EligibilityJudgmentResult(cmd.policyId(), "t", r, null, null, "");
                });
        givenReservePendingReturnsHistory();

        dispatcher.dispatchOne(enabledSetting);

        // 1개 eligible 정책 → dispatcher 1번 호출, remaining 없으므로 dispatchService.markSent 미호출
        then(emailDispatcher).should(times(1)).dispatchRecommendation(any(), eq(user), any());
        then(dispatchService).should(never()).markSent(anyLong());
    }

    @Test
    @DisplayName("EmailSendException 시 reserved 모든 행을 markFailed")
    void emailSendException_marksAllFailed() {
        Policy eligible = createPolicy(10L, Category.JOBS, "11", LocalDate.now().plusDays(5));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of(eligible));
        given(bookmarkRepository.existsByUserIdAndPolicyId(eq(1L), anyLong())).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION))).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willReturn(new EligibilityJudgmentResult(
                        10L, "t", EligibilityResult.LIKELY_ELIGIBLE.name(), null, null, ""));
        givenReservePendingReturnsHistory();
        willThrow(new EmailSendException("SES 실패", new RuntimeException()))
                .given(emailDispatcher).dispatchRecommendation(any(), any(), any());

        dispatcher.dispatchOne(enabledSetting);

        // dispatcher 내부에서 representative history markFailed 처리, remaining 없으므로 dispatchService.markFailed 미호출
        then(emailDispatcher).should(times(1)).dispatchRecommendation(any(), any(), any());
        then(dispatchService).should(never()).markFailed(anyLong(), any());
        then(dispatchService).should(never()).markSent(any());
    }

    @Test
    @DisplayName("추천은 5건으로 절단된다 (markSent 5회 호출)")
    void picksLimitedToFive() {
        List<Policy> openPolicies = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            openPolicies.add(createPolicy((long) (10 + i), Category.JOBS, "11", LocalDate.now().plusDays(2 + i)));
        }
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(openPolicies);
        given(bookmarkRepository.existsByUserIdAndPolicyId(eq(1L), anyLong())).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION))).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willAnswer(inv -> {
                    JudgeEligibilityCommand cmd = inv.getArgument(1);
                    return new EligibilityJudgmentResult(
                            cmd.policyId(), "t", EligibilityResult.LIKELY_ELIGIBLE.name(), null, null, "");
                });
        givenReservePendingReturnsHistory();

        dispatcher.dispatchOne(enabledSetting);

        // 5 picks → dispatcher 1번 (representative), 나머지 4개는 dispatchService.markSent 직접 호출
        then(emailDispatcher).should(times(1)).dispatchRecommendation(any(), eq(user), any());
        then(dispatchService).should(times(4)).markSent(anyLong());
    }

    // ── 헬퍼 ──

    /** reservePending 호출 시 unique id 의 새 PENDING history 를 반환하도록 stub */
    private void givenReservePendingReturnsHistory() {
        AtomicLong idSeq = new AtomicLong(1000L);
        given(dispatchService.reservePending(eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION)))
                .willAnswer(inv -> {
                    Long policyId = inv.getArgument(1);
                    NotificationHistory h = NotificationHistory.pending(1L, policyId, NotificationType.RECOMMENDATION);
                    ReflectionTestUtils.setField(h, "id", idSeq.incrementAndGet());
                    return h;
                });
    }

    private User createUser(Long id) {
        User user = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_" + id)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Policy createPolicy(Long id, Category category, String regionCode, LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title("정책-" + id)
                .category(category)
                .regionCode(regionCode)
                .applyStart(LocalDate.now().minusDays(30))
                .applyEnd(applyEnd)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        ReflectionTestUtils.setField(policy, "createdAt", LocalDateTime.now());
        return policy;
    }
}
