package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideValidatorTest {

    GuideValidator validator = new GuideValidator();

    @Test
    void 원문에_금액이_풀이에_보존되면_통과() {
        String originalText = "월세 60만원 이하 주택 거주 청년";
        GuideContent content = new GuideContent(
                "월세 지원",
                new GuidePairedSection(List.of("월세 60만원 이하 주택 거주자")),
                null, null, List.of());

        assertThat(validator.findMissingNumericTokens(originalText, content)).isEmpty();
    }

    @Test
    void 원문에_있던_금액이_풀이에_없으면_누락_검출() {
        String originalText = "월세 60만원 이하 주택, 만 19세 이상";
        GuideContent content = new GuideContent(
                "월세 지원",
                new GuidePairedSection(List.of("청년 월세 지원")),
                null, null, List.of());

        List<String> missing = validator.findMissingNumericTokens(originalText, content);
        assertThat(missing).contains("60만원", "19세");
    }

    @Test
    void 친근체_검출() {
        GuideContent content = new GuideContent(
                "이 정책은 청년에게 도움이 돼요.",
                new GuidePairedSection(List.of("받을 수 있어요")),
                null, null, List.of());

        assertThat(validator.containsFriendlyTone(content)).isTrue();
    }

    @Test
    void 명사형_단정형은_친근체_아님() {
        GuideContent content = new GuideContent(
                "만 19~34세 청년 월세 지원",
                new GuidePairedSection(List.of("본인 명의 계약자")),
                null, null, List.of());

        assertThat(validator.containsFriendlyTone(content)).isFalse();
    }
}
