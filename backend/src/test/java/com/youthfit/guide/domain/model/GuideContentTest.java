package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideContentTest {

    @Test
    void oneLineSummary가_빈문자열이면_예외() {
        assertThatThrownBy(() -> new GuideContent(
                "  ",
                null, null, null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pitfalls가_null이면_빈리스트로_저장() {
        GuideContent content = new GuideContent(
                "한 줄 요약", null, null, null, null);
        assertThat(content.pitfalls()).isEmpty();
    }

    @Test
    void 모든_paired가_null이고_pitfalls도_비어있으면_허용() {
        GuideContent content = new GuideContent(
                "한 줄 요약", null, null, null, List.of());
        assertThat(content.oneLineSummary()).isEqualTo("한 줄 요약");
        assertThat(content.target()).isNull();
        assertThat(content.criteria()).isNull();
        assertThat(content.content()).isNull();
        assertThat(content.pitfalls()).isEmpty();
    }

    @Test
    void 정상_생성() {
        GuidePairedSection target = new GuidePairedSection(List.of("만 19~34세"));
        GuidePitfall pitfall = new GuidePitfall("월세 60만원 초과 제외", GuideSourceField.SUPPORT_TARGET);

        GuideContent content = new GuideContent(
                "만 19~34세 청년 월세 지원",
                target, null, null,
                List.of(pitfall));

        assertThat(content.target().items()).containsExactly("만 19~34세");
        assertThat(content.pitfalls()).hasSize(1);
    }
}
