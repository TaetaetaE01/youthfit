package com.youthfit.eligibility.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.CriterionResult;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
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
 * 룰 추출 결과로 만들어지는 {@code confidenceNote} 필드가 컨트롤러 → 응답
 * DTO → JSON 직렬화를 통과해 클라이언트에 노출되는지를 검증한다.
 *
 * <p>{@code EligibilityEvaluator} 자체의 LOW → UNCERTAIN 다운그레이드 동작은
 * {@code EligibilityEvaluatorTest} 에서 단위 테스트로 검증된다.
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
        EligibilityJudgmentResult result = new EligibilityJudgmentResult(
                100L,
                "2026 청년월세지원",
                EligibilityResult.LIKELY_ELIGIBLE,
                List.of(),
                List.of(),
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
                .andExpect(jsonPath("$.data.criteria").isArray())
                .andExpect(jsonPath("$.data.criteria.length()").value(0))
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.missingFields.length()").value(0))
                .andExpect(jsonPath("$.data.disclaimer").exists());
    }

    @Test
    @DisplayName("POST /api/v1/eligibility/judge - HIGH + LOW 룰이 섞여 있으면 UNCERTAIN 과 confidenceNote='근거가 모호함' 을 반환한다")
    void lowConfidenceRule_isDowngradedToUncertain() throws Exception {
        // given
        // - HIGH 신뢰도 age 룰: 19~34 BETWEEN, 프로필 age=29 매칭 → LIKELY_ELIGIBLE
        // - LOW  신뢰도 employmentKind 룰: EQ EMPLOYEE, 프로필 employmentKind=EMPLOYEE
        //   매칭이지만 RuleConfidence.LOW 로 인해 UNCERTAIN 다운그레이드 + confidenceNote
        CriterionResult ageHigh = new CriterionResult(
                "age",
                "연령",
                EligibilityResult.LIKELY_ELIGIBLE,
                "29 — 연령(19~34) 충족",
                "자격 요건 > 연령",
                null
        );
        CriterionResult employmentLow = new CriterionResult(
                "employmentKind",
                "고용 형태",
                EligibilityResult.UNCERTAIN,
                "원문 근거가 모호하여 판단 불가",
                "자격 요건 > 고용 형태",
                "근거가 모호함"
        );
        EligibilityJudgmentResult result = new EligibilityJudgmentResult(
                100L,
                "2026 청년월세지원",
                EligibilityResult.UNCERTAIN,
                List.of(ageHigh, employmentLow),
                List.of("employmentKind"),
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
                .andExpect(jsonPath("$.data.criteria.length()").value(2))
                // HIGH 룰: confidenceNote 가 null
                .andExpect(jsonPath("$.data.criteria[?(@.field=='age')].result")
                        .value("LIKELY_ELIGIBLE"))
                .andExpect(jsonPath("$.data.criteria[?(@.field=='age')].confidenceNote")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                // LOW 룰: UNCERTAIN 으로 다운그레이드되고 confidenceNote 가 채워짐
                .andExpect(jsonPath("$.data.criteria[?(@.field=='employmentKind')].result")
                        .value("UNCERTAIN"))
                .andExpect(jsonPath("$.data.criteria[?(@.field=='employmentKind')].confidenceNote")
                        .value(org.hamcrest.Matchers.hasItem("근거가 모호함")));
    }

    // ── 헬퍼 메서드 ──

    private Authentication createAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
