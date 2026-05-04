package com.youthfit.user.application.service;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("RecommendationOneDispatcher")
@ExtendWith(MockitoExtension.class)
class RecommendationOneDispatcherTest {

    @InjectMocks
    private RecommendationOneDispatcher dispatcher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EligibilityProfileRepository profileRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private NotificationHistoryRepository historyRepository;

    @Mock
    private EligibilityService eligibilityService;

    @Mock
    private EmailSender emailSender;

    @Spy
    private PolicyRecommender recommender = new PolicyRecommender();

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
        // given
        User user = User.builder()
                .nickname("테스터")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_1")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        dispatcher.dispatchOne(enabledSetting);

        // then
        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
        then(profileRepository).should(never()).findByUserId(anyLong());
    }

    @Test
    @DisplayName("토글 OFF 사용자는 발송하지 않는다 (canDispatchRecommendation=false)")
    void toggleOff_skips() {
        // given
        NotificationSetting offSetting = new NotificationSetting(1L);
        offSetting.updateSetting(true, 7, false);
        offSetting.replaceInterestCategories(Set.of(Category.JOBS));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));

        // when
        dispatcher.dispatchOne(offSetting);

        // then
        then(policyRepository).should(never()).findAllByStatus(any());
        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
    }

    @Test
    @DisplayName("적합도 프로필 미입력자는 발송하지 않는다")
    void noProfile_skips() {
        // given
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.empty());

        // when
        dispatcher.dispatchOne(enabledSetting);

        // then
        then(policyRepository).should(never()).findAllByStatus(any());
        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
    }

    @Test
    @DisplayName("후보 정책이 없으면 발송하지 않는다")
    void noCandidates_skips() {
        // given
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of());

        // when
        dispatcher.dispatchOne(enabledSetting);

        // then
        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
        then(historyRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("북마크된 정책은 추천 후보에서 제외한다")
    void bookmarkedPolicy_excluded() {
        // given
        Policy bookmarked = createPolicy(10L, Category.JOBS, "11", LocalDate.now().plusDays(5));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of(bookmarked));
        given(bookmarkRepository.existsByUserIdAndPolicyId(1L, 10L)).willReturn(true);

        // when
        dispatcher.dispatchOne(enabledSetting);

        // then
        then(eligibilityService).should(never()).judgeEligibility(anyLong(), any());
        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
    }

    @Test
    @DisplayName("이미 추천 이력이 있는 정책은 후보에서 제외한다")
    void alreadyRecommended_excluded() {
        // given
        Policy already = createPolicy(10L, Category.JOBS, "11", LocalDate.now().plusDays(5));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of(already));
        given(bookmarkRepository.existsByUserIdAndPolicyId(1L, 10L)).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                1L, 10L, NotificationType.RECOMMENDATION)).willReturn(true);

        // when
        dispatcher.dispatchOne(enabledSetting);

        // then
        then(eligibilityService).should(never()).judgeEligibility(anyLong(), any());
        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
    }

    @Test
    @DisplayName("LIKELY_ELIGIBLE 정책만 통과한다")
    void onlyLikelyEligible_passed() {
        // given
        Policy eligible1 = createPolicy(10L, Category.JOBS, "11", LocalDate.now().plusDays(5));
        Policy uncertain = createPolicy(11L, Category.JOBS, "11", LocalDate.now().plusDays(6));
        Policy ineligible = createPolicy(12L, Category.JOBS, "11", LocalDate.now().plusDays(7));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN))
                .willReturn(List.of(eligible1, uncertain, ineligible));
        given(bookmarkRepository.existsByUserIdAndPolicyId(eq(1L), anyLong())).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION))).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willAnswer(invocation -> {
                    JudgeEligibilityCommand cmd = invocation.getArgument(1);
                    String overall = switch (cmd.policyId().intValue()) {
                        case 10 -> EligibilityResult.LIKELY_ELIGIBLE.name();
                        case 11 -> EligibilityResult.UNCERTAIN.name();
                        default -> EligibilityResult.LIKELY_INELIGIBLE.name();
                    };
                    return new EligibilityJudgmentResult(cmd.policyId(), "title", overall, null, null, "");
                });

        // when
        dispatcher.dispatchOne(enabledSetting);

        // then
        then(emailSender).should().sendRecommendationNotification(eq("test@example.com"), any());
        then(historyRepository).should(times(1)).save(any(NotificationHistory.class));
    }

    @Test
    @DisplayName("추천은 5건으로 절단된다")
    void picksLimitedToFive() {
        // given: 6 eligible policies
        List<Policy> openPolicies = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            openPolicies.add(createPolicy((long) (10 + i), Category.JOBS, "11",
                    LocalDate.now().plusDays(2 + i)));
        }
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(openPolicies);
        given(bookmarkRepository.existsByUserIdAndPolicyId(eq(1L), anyLong())).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION))).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willAnswer(invocation -> {
                    JudgeEligibilityCommand cmd = invocation.getArgument(1);
                    return new EligibilityJudgmentResult(
                            cmd.policyId(), "title",
                            EligibilityResult.LIKELY_ELIGIBLE.name(), null, null, "");
                });

        // when
        dispatcher.dispatchOne(enabledSetting);

        // then: 5 history rows saved (one per pick)
        then(historyRepository).should(times(5)).save(any(NotificationHistory.class));
        then(emailSender).should().sendRecommendationNotification(eq("test@example.com"), any());
    }

    @Test
    @DisplayName("발송 시 NotificationHistory에 RECOMMENDATION 이력이 저장된다")
    void onSend_savesHistory() {
        // given
        Policy eligible = createPolicy(10L, Category.JOBS, "11", LocalDate.now().plusDays(5));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of(eligible));
        given(bookmarkRepository.existsByUserIdAndPolicyId(1L, 10L)).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                1L, 10L, NotificationType.RECOMMENDATION)).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willReturn(new EligibilityJudgmentResult(
                        10L, "title", EligibilityResult.LIKELY_ELIGIBLE.name(), null, null, ""));

        // when
        dispatcher.dispatchOne(enabledSetting);

        // then
        then(historyRepository).should().save(any(NotificationHistory.class));
    }

    // ── 헬퍼 메서드 ──

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
