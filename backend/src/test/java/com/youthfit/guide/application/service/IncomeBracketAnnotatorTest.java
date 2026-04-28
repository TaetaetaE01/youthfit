package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeBracketAnnotatorTest {

    private final IncomeBracketAnnotator annotator = new IncomeBracketAnnotator();

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
}
