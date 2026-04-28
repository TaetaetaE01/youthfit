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
    void 이미_만원_표기가_같은_bullet에_있으면_그_bullet은_전체_skip한다() {
        GuideContent content = contentWithCriteriaItem(
                "중위소득 60% 이하 (정책 본문 기준 월 138만원, 230만원)인 자");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 (정책 본문 기준 월 138만원, 230만원)인 자");
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
