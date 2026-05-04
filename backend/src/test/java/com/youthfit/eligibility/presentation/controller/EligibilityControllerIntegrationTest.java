package com.youthfit.eligibility.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.CriterionResult;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.dto.result.GroupedCriteria;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.SourceView;
import com.youthfit.eligibility.domain.model.view.SummaryView;
import com.youthfit.eligibility.domain.model.view.UserValueView;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EligibilityController 통합 테스트.
 *
 * <p>참고: 본 프로젝트는 통합 테스트용 임베디드 DB(H2/Testcontainers) 인프라를
 * 갖추고 있지 않으며(application.yml 은 PostgreSQL+pgvector 만 가정),
 * 모든 컨트롤러 테스트는 {@code @WebMvcTest} 슬라이스로 작성한다
 * (예: {@code BookmarkControllerTest}, {@code PolicyControllerTest}).
 *
 * <p>따라서 이 테스트 또한 동일한 슬라이스 패턴으로 작성하되,
 * 새로운 grouped criteria(eligible/uncertain/ineligible), summary, verdictText, source 필드가
 * 컨트롤러 → 응답 DTO → JSON 직렬화를 통과해 클라이언트에 노출되는지를 검증한다.
 */
@DisplayName("EligibilityController")
@WebMvcTest(controllers = EligibilityController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
class EligibilityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EligibilityService eligibilityService;

    @Test
    @DisplayName("POST /api/v1/eligibility/judge - 룰이 0개이면 LIKELY_ELIGIBLE 과 빈 criteria 를 반환한다")
    void zeroRules_returnsLikelyEligible() throws Exception {
        // given
        SummaryView summary = new SummaryView("모든 조건을 충족해요", 0, 0, 0);
        GroupedCriteria grouped = new GroupedCriteria(List.of(), List.of(), List.of());
        EligibilityJudgmentResult result = new EligibilityJudgmentResult(
                100L,
                "2026 청년월세지원",
                "LIKELY_ELIGIBLE",
                summary,
                grouped,
                EligibilityJudgmentResult.DISCLAIMER_TEXT
        );
        given(eligibilityService.judgeEligibility(eq(1L), eq(new JudgeEligibilityCommand(100L))))
                .willReturn(result);

        // when & then
        mockMvc.perform(post("/api/v1/eligibility/judge")
                        .with(authentication(createAuth(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyId": 100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyId").value(100))
                .andExpect(jsonPath("$.data.overallResult").value("LIKELY_ELIGIBLE"))
                .andExpect(jsonPath("$.data.criteria.eligible").isArray())
                .andExpect(jsonPath("$.data.criteria.eligible.length()").value(0))
                .andExpect(jsonPath("$.data.criteria.uncertain.length()").value(0))
                .andExpect(jsonPath("$.data.criteria.ineligible.length()").value(0))
                .andExpect(jsonPath("$.data.summary.headline").value("모든 조건을 충족해요"))
                .andExpect(jsonPath("$.data.disclaimer").exists());
    }

    @Test
    @DisplayName("POST /api/v1/eligibility/judge - HIGH + LOW 룰이 섞여 있으면 UNCERTAIN 과 그룹핑된 criteria 를 반환한다")
    void lowConfidenceRule_isGroupedAsUncertain() throws Exception {
        // given
        // - HIGH 신뢰도 age 룰: eligible 그룹
        // - LOW  신뢰도 employmentKind 룰: uncertain 그룹 + AMBIGUOUS_SOURCE
        CriterionResult ageEligible = new CriterionResult(
                "age", "연령", "LIKELY_ELIGIBLE", null,
                new RequirementView("BETWEEN", "만 19세 이상 34세 이하"),
                new UserValueView("29", "만 29세"),
                "연령 조건을 충족해요",
                new SourceView("자격 요건 > 연령")
        );
        CriterionResult employmentUncertain = new CriterionResult(
                "employmentKind", "고용 형태", "UNCERTAIN", UncertainReason.AMBIGUOUS_SOURCE,
                new RequirementView("EQ", "정규직"),
                new UserValueView(null, "정보 없음"),
                "정책 원문이 모호해 단정하기 어려워요",
                new SourceView("자격 요건 > 고용 형태")
        );
        SummaryView summary = new SummaryView("정책 원문이 모호한 조건이 1개 있어요", 1, 1, 0);
        GroupedCriteria grouped = new GroupedCriteria(List.of(), List.of(employmentUncertain), List.of(ageEligible));
        EligibilityJudgmentResult result = new EligibilityJudgmentResult(
                100L,
                "2026 청년월세지원",
                "UNCERTAIN",
                summary,
                grouped,
                EligibilityJudgmentResult.DISCLAIMER_TEXT
        );
        given(eligibilityService.judgeEligibility(eq(1L), eq(new JudgeEligibilityCommand(100L))))
                .willReturn(result);

        // when & then
        mockMvc.perform(post("/api/v1/eligibility/judge")
                        .with(authentication(createAuth(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyId": 100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overallResult").value("UNCERTAIN"))
                // eligible 그룹: age 조건 통과
                .andExpect(jsonPath("$.data.criteria.eligible.length()").value(1))
                .andExpect(jsonPath("$.data.criteria.eligible[0].field").value("age"))
                .andExpect(jsonPath("$.data.criteria.eligible[0].verdictText").value("연령 조건을 충족해요"))
                .andExpect(jsonPath("$.data.criteria.eligible[0].requirement.displayText").value("만 19세 이상 34세 이하"))
                .andExpect(jsonPath("$.data.criteria.eligible[0].userValue.displayText").value("만 29세"))
                // uncertain 그룹: LOW 신뢰도 룰 → AMBIGUOUS_SOURCE
                .andExpect(jsonPath("$.data.criteria.uncertain.length()").value(1))
                .andExpect(jsonPath("$.data.criteria.uncertain[0].field").value("employmentKind"))
                .andExpect(jsonPath("$.data.criteria.uncertain[0].uncertainReason").value("AMBIGUOUS_SOURCE"))
                .andExpect(jsonPath("$.data.criteria.uncertain[0].verdictText").value("정책 원문이 모호해 단정하기 어려워요"))
                // summary 검증
                .andExpect(jsonPath("$.data.summary.headline").value("정책 원문이 모호한 조건이 1개 있어요"))
                .andExpect(jsonPath("$.data.summary.eligibleCount").value(1))
                .andExpect(jsonPath("$.data.summary.uncertainCount").value(1));
    }

    // ── 헬퍼 메서드 ──

    private Authentication createAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
