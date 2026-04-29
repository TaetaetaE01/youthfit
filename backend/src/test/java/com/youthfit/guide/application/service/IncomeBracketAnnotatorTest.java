package com.youthfit.guide.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeBracketAnnotatorTest {

    private final IncomeBracketAnnotator annotator = new IncomeBracketAnnotator();

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setupLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(IncomeBracketAnnotator.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void teardownLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(IncomeBracketAnnotator.class);
        logger.detachAppender(appender);
    }

    private IncomeBracketReference reference2026() {
        return new IncomeBracketReference(
                2026,
                1,
                Map.of(
                        HouseholdSize.ONE, Map.of(60, 1538543L),
                        HouseholdSize.TWO, Map.of(60, 2519575L)),
                Map.of(HouseholdSize.ONE, 1282119L, HouseholdSize.TWO, 2099646L)
        );
    }

    private GuideContent contentWithCriteriaItem(String item) {
        return new GuideContent(
                "한 줄 요약",
                List.of(),
                null,
                new GuidePairedSection(List.of(new GuideGroup(null, List.of(item)))),
                null,
                List.of()
        );
    }

    @Test
    void 중위소득_60퍼_패턴이_있으면_1인2인_환산값을_괄호로_삽입한다() {
        GuideContent content = contentWithCriteriaItem("중위소득 60% 이하인 자");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원)인 자");
    }

    @Test
    void 차상위계층_패턴이_있으면_1인_가구만_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem("차상위계층 이하의 청년");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("차상위계층 이하 (2026년 기준 1인 가구 월 약 128만원 이하)의 청년");
    }

    @Test
    void 차상위_단독_표기도_매칭한다() {
        GuideContent content = contentWithCriteriaItem("차상위 청년");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("차상위 (2026년 기준 1인 가구 월 약 128만원 이하) 청년");
    }

    @Test
    void 이미_연도기준_환산값이_명시되어_있으면_재환산하지_않는다() {
        GuideContent content = contentWithCriteriaItem(
                "중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원)인 자");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원)인 자");
    }

    @Test
    void 무관한_지원금_만원_표기가_있어도_환산값을_삽입한다() {
        // EXISTING_AMOUNT_PATTERN 가 "월 약 X만원" / "YYYY년 기준 ... 만원" 만 가드하도록 좁혀져,
        // "월 30만원 매칭" 같은 무관한 만원에 끌려 스킵되지 않아야 한다.
        GuideContent content = contentWithCriteriaItem(
                "중위소득 60% 이하 가구에 월 30만원 정액 매칭");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원) 가구에 월 30만원 정액 매칭");
    }

    @Test
    void yaml_미등록_비율은_텍스트_보존하고_WARN_로그를_남긴다() {
        GuideContent content = contentWithCriteriaItem("중위소득 75% 이하 청년");
        GuideContent result = annotator.annotate(content, reference2026(), 7L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 75% 이하 청년");
        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("percent=75")
                        && e.getFormattedMessage().contains("policyId=7"));
    }

    @Test
    void 같은_텍스트_안에_동일_비율_반복시_첫_등장에만_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem(
                "중위소득 60% 이하인 자로서 중위소득 60% 이하 가구");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원)인 자로서 중위소득 60% 이하 가구");
    }

    @Test
    void 같은_텍스트_안에_차상위_반복시_첫_등장에만_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem(
                "차상위계층 이하 청년 또는 차상위 가구");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("차상위계층 이하 (2026년 기준 1인 가구 월 약 128만원 이하) 청년 또는 차상위 가구");
    }

    @Test
    void 다른_비율_여러개는_각각_한번씩_삽입한다() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2026, 1,
                Map.of(
                        HouseholdSize.ONE, Map.of(50, 1282119L, 60, 1538543L),
                        HouseholdSize.TWO, Map.of(50, 2099646L, 60, 2519575L)),
                Map.of(HouseholdSize.ONE, 1282119L)
        );
        GuideContent content = contentWithCriteriaItem("중위소득 50% 또는 중위소득 60% 이하");
        GuideContent result = annotator.annotate(content, ref, 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("중위소득 50% (2026년 기준 1인 가구 월 약 128만원, 2인 가구 월 약 210만원)")
                .contains("중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원)");
    }

    @Test
    void 빈_reference이면_모든_패턴_skip하고_INFO_로그_1회_남긴다() {
        IncomeBracketReference empty = new IncomeBracketReference(2026, 1, Map.of(), Map.of());
        GuideContent content = contentWithCriteriaItem("중위소득 60% 이하 청년");
        GuideContent result = annotator.annotate(content, empty, 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 청년");
        long infoCount = appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO
                        && e.getFormattedMessage().contains("empty income bracket reference"))
                .count();
        assertThat(infoCount).isEqualTo(1);
    }

    private IncomeBracketReference referenceWithUrbanWorker() {
        return new IncomeBracketReference(
                2026, 2,
                Map.of(),
                Map.of(),
                Map.of(HouseholdSize.THREE, 8_168_429L, HouseholdSize.FOUR, 8_802_202L));
    }

    private IncomeBracketReference referenceWithFullUrbanWorker() {
        return new IncomeBracketReference(
                2026, 2,
                Map.of(),
                Map.of(),
                Map.of(
                        HouseholdSize.ONE, 3_813_363L,
                        HouseholdSize.TWO, 5_866_270L,
                        HouseholdSize.THREE, 8_168_429L,
                        HouseholdSize.FOUR, 8_802_202L));
    }

    @Test
    void 도시근로자_신혼부부_키워드면_2인_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem(
                "신혼부부 특별공급은 도시 근로자 가구 평균소득 130% 이하");
        GuideContent result = annotator.annotate(content, referenceWithFullUrbanWorker(), 30L);
        // 2인 130% = 5,866,270 × 1.3 ≈ 7,626,151 → 약 763만원
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준 2인 가구 월 약 763만원)")
                .doesNotContain("3인 가구")
                .doesNotContain("4인 가구");
    }

    @Test
    void 도시근로자_다자녀_노부모_키워드면_3인4인_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem(
                "노부모 부양 및 다자녀 특별공급은 도시 근로자가구 평균소득 120% 이하");
        GuideContent result = annotator.annotate(content, referenceWithFullUrbanWorker(), 30L);
        // 3인 120% = 9,802,115 ≈ 980만, 4인 120% = 10,562,642 ≈ 1056만
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준 3인 가구 월 약 980만원, 4인 가구 월 약 1056만원)");
    }

    @Test
    void 도시근로자_키워드_없으면_기본_1인2인_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem(
                "전용면적 60㎡ 이하 공공분양은 도시 근로자 가구 평균소득 100% 이하");
        GuideContent result = annotator.annotate(content, referenceWithFullUrbanWorker(), 30L);
        // 1인 100% = 381만원, 2인 100% = 587만원
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준 1인 가구 월 약 381만원, 2인 가구 월 약 587만원)");
    }

    @Test
    void 도시근로자_가구당_조사_표기도_매칭한다() {
        // 청약 표준 표현 "도시근로자 가구당 월평균소득" 케이스
        GuideContent content = contentWithCriteriaItem(
                "전년도 도시근로자 가구당 월평균소득 100% 이하일 것");
        GuideContent result = annotator.annotate(content, referenceWithFullUrbanWorker(), 30L);
        // 키워드 없음 → 기본 1·2인
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준 1인 가구 월 약 381만원, 2인 가구 월 약 587만원)");
    }

    @Test
    void 도시근로자_청년_키워드면_1인_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem(
                "청년 단독 도시 근로자 가구 평균소득 130% 이하");
        GuideContent result = annotator.annotate(content, referenceWithFullUrbanWorker(), 30L);
        // 1인 130% = 3,813,363 × 1.3 ≈ 4,957,372 → 약 496만원
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준 1인 가구 월 약 496만원)")
                .doesNotContain("2인 가구");
    }

    @Test
    void 전년도_도시근로자_가구_평균소득_표기도_매칭한다() {
        GuideContent content = contentWithCriteriaItem(
                "전년도 도시근로자 가구 월평균소득 100% 이하일 것");
        GuideContent result = annotator.annotate(content, referenceWithFullUrbanWorker(), 30L);
        // 키워드 없음 → 기본 1·2인
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준 1인 가구 월 약 381만원, 2인 가구 월 약 587만원)");
    }

    @Test
    void 작년_도시_직장인_가구_평균소득_표기도_매칭한다() {
        GuideContent content = contentWithCriteriaItem(
                "작년 도시 직장인 가구의 한 달 평균 소득 120% 이하");
        GuideContent result = annotator.annotate(content, referenceWithFullUrbanWorker(), 30L);
        // 키워드 없음 → 기본 1·2인
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준")
                .contains("1인 가구")
                .contains("2인 가구");
    }

    @Test
    void 도시근로자_데이터_없으면_텍스트_보존하고_WARN_로그를_남긴다() {
        IncomeBracketReference noUrban = new IncomeBracketReference(
                2026, 2,
                Map.of(HouseholdSize.ONE, Map.of(60, 1538543L)),
                Map.of(HouseholdSize.ONE, 1282119L));
        GuideContent content = contentWithCriteriaItem("도시 근로자 가구 평균소득 130% 이하");
        GuideContent result = annotator.annotate(content, noUrban, 30L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("도시 근로자 가구 평균소득 130% 이하");
        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("unmapped urban-worker percent")
                        && e.getFormattedMessage().contains("percent=130"));
    }

    @Test
    void 중위소득과_도시근로자가_같은_텍스트에_있으면_같은_가구원수로_환산값_삽입() {
        IncomeBracketReference both = new IncomeBracketReference(
                2026, 2,
                Map.of(
                        HouseholdSize.ONE, Map.of(50, 1282119L),
                        HouseholdSize.TWO, Map.of(50, 2099646L)),
                Map.of(),
                Map.of(
                        HouseholdSize.ONE, 3_813_363L,
                        HouseholdSize.TWO, 5_866_270L,
                        HouseholdSize.THREE, 8_168_429L,
                        HouseholdSize.FOUR, 8_802_202L));
        GuideContent content = contentWithCriteriaItem(
                "중위소득 50% 이하이거나 도시 근로자 가구 평균소득 130% 이하");
        GuideContent result = annotator.annotate(content, both, 30L);
        // 키워드 없음 → 둘 다 1·2인 적용
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("중위소득 50% 이하 (2026년 기준 1인 가구 월 약 128만원, 2인 가구 월 약 210만원)")
                .contains("도시 근로자 가구 평균소득 130% 이하 (2026년 기준 1인 가구 월 약 496만원, 2인 가구 월 약 763만원)");
    }

    // resolveSizesFromContext 직접 테스트 — 키워드 매칭 규칙 검증
    @Test
    void resolveSizesFromContext_키워드_없으면_1인2인_기본() {
        assertThat(annotator.resolveSizesFromContext("도시 근로자 가구 평균소득 100%"))
                .containsExactly(HouseholdSize.ONE, HouseholdSize.TWO);
    }

    @Test
    void resolveSizesFromContext_청년키워드면_1인만() {
        assertThat(annotator.resolveSizesFromContext("청년 대상 정책"))
                .containsExactly(HouseholdSize.ONE);
    }

    @Test
    void resolveSizesFromContext_신혼부부키워드면_2인만() {
        assertThat(annotator.resolveSizesFromContext("신혼부부 특별공급"))
                .containsExactly(HouseholdSize.TWO);
    }

    @Test
    void resolveSizesFromContext_다자녀_노부모면_3인4인() {
        assertThat(annotator.resolveSizesFromContext("다자녀 가구 우선"))
                .containsExactly(HouseholdSize.THREE, HouseholdSize.FOUR);
        assertThat(annotator.resolveSizesFromContext("노부모 부양 가구"))
                .containsExactly(HouseholdSize.THREE, HouseholdSize.FOUR);
    }

    @Test
    void resolveSizesFromContext_여러_키워드면_합집합_정렬() {
        assertThat(annotator.resolveSizesFromContext("청년 신혼부부 다자녀 우선"))
                .containsExactly(HouseholdSize.ONE, HouseholdSize.TWO,
                        HouseholdSize.THREE, HouseholdSize.FOUR);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "기준중위소득 60%",
            "중위소득의 60%",
            "중위소득 60% 이내",
            "중위소득 60% 까지",
            "중위소득 60%"
    })
    void 중위소득_표현_변형도_모두_매칭한다(String variation) {
        // 가구원수 키워드 없는 일반 텍스트 → 기본 1·2인
        GuideContent content = contentWithCriteriaItem(variation + " 가구");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원)");
    }

    @Test
    void oneLineSummary_highlights_target_content_pitfalls_모두_적용된다() {
        GuideContent content = new GuideContent(
                "중위소득 60% 이하 청년에게 월세 지원",
                List.of(
                        new com.youthfit.guide.domain.model.GuideHighlight(
                                "중위소득 60% 이하 우선 공급",
                                com.youthfit.guide.domain.model.GuideSourceField.SUPPORT_TARGET)),
                new GuidePairedSection(List.of(new GuideGroup(null, List.of("중위소득 60% 이하 무주택자")))),
                new GuidePairedSection(List.of(new GuideGroup(null, List.of("선정기준 무관")))),
                new GuidePairedSection(List.of(new GuideGroup(null, List.of("중위소득 60% 이하만 지급")))),
                List.of(
                        new com.youthfit.guide.domain.model.GuidePitfall(
                                "중위소득 60% 초과 시 환수",
                                com.youthfit.guide.domain.model.GuideSourceField.SUPPORT_CONTENT))
        );
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.oneLineSummary()).contains("(2026년 기준 1인 가구 월 약 154만원");
        assertThat(result.highlights().get(0).text()).contains("(2026년 기준");
        assertThat(result.target().groups().get(0).items().get(0)).contains("(2026년 기준");
        assertThat(result.content().groups().get(0).items().get(0)).contains("(2026년 기준");
        assertThat(result.pitfalls().get(0).text()).contains("(2026년 기준");
    }
}
