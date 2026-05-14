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
                List.of(),
                null, null, null,
                null, null, null, null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pitfalls가_null이면_빈리스트로_저장() {
        GuideContent content = new GuideContent(
                "한 줄 요약", List.of(), null, null, null,
                null, null, null, null, null);
        assertThat(content.pitfalls()).isEmpty();
    }

    @Test
    void 모든_paired가_null이고_pitfalls도_비어있으면_허용() {
        GuideContent content = new GuideContent(
                "한 줄 요약", List.of(), null, null, null,
                null, null, null, null, List.of());
        assertThat(content.oneLineSummary()).isEqualTo("한 줄 요약");
        assertThat(content.target()).isNull();
        assertThat(content.criteria()).isNull();
        assertThat(content.content()).isNull();
        assertThat(content.pitfalls()).isEmpty();
    }

    @Test
    void 정상_생성() {
        GuideGroup group = new GuideGroup(null, List.of("만 19~34세"));
        GuidePairedSection target = new GuidePairedSection(List.of(group));
        GuidePitfall pitfall = new GuidePitfall("월세 60만원 초과 제외", GuideSourceField.SUPPORT_TARGET);

        GuideContent content = new GuideContent(
                "만 19~34세 청년 월세 지원",
                List.of(),
                target, null, null,
                null, null, null, null,
                List.of(pitfall));

        assertThat(content.target().groups().get(0).items()).containsExactly("만 19~34세");
        assertThat(content.pitfalls()).hasSize(1);
    }

    @Test
    void highlights_null이면_빈_리스트() {
        GuideContent c = new GuideContent("요약", null, null, null, null,
                null, null, null, null, List.of());
        assertThat(c.highlights()).isEmpty();
    }

    @Test
    void highlights_정상_수용() {
        GuideHighlight h = new GuideHighlight("월 20만원 지원", GuideSourceField.SUPPORT_CONTENT);
        GuideContent c = new GuideContent("요약", List.of(h), null, null, null,
                null, null, null, null, List.of());
        assertThat(c.highlights()).hasSize(1);
        assertThat(c.highlights().get(0).text()).isEqualTo("월 20만원 지원");
    }

    @Test
    void 신규_4개_섹션_모두_null로_생성_가능() {
        GuideContent c = new GuideContent(
                "한 줄 요약",
                List.of(new GuideHighlight("h1", GuideSourceField.BODY, null)),
                new GuidePairedSection(List.of(new GuideGroup("g1", List.of("i1")))),
                new GuidePairedSection(List.of(new GuideGroup("g2", List.of("i2")))),
                new GuidePairedSection(List.of(new GuideGroup("g3", List.of("i3")))),
                null,
                null,
                null,
                null,
                List.of(new GuidePitfall("p1", GuideSourceField.BODY, null))
        );
        assertThat(c.applyMethod()).isNull();
        assertThat(c.deadlineNote()).isNull();
        assertThat(c.requiredDocuments()).isNull();
        assertThat(c.contact()).isNull();
    }

    @Test
    void 신규_4개_섹션_모두_채워서_생성() {
        GuideListSection apply = new GuideListSection(List.of("회원가입", "신청서 작성"), GuideSourceField.ENRICHMENT);
        GuideListSection deadline = new GuideListSection(List.of("2026-03-01 ~ 2026-05-31"), GuideSourceField.ENRICHMENT);
        GuideListSection docs = new GuideListSection(List.of("등본"), GuideSourceField.ENRICHMENT);
        GuideListSection contact = new GuideListSection(List.of("02-2133-6586"), GuideSourceField.SUPPORT_CONTENT);

        GuideContent c = new GuideContent(
                "한 줄 요약",
                List.of(new GuideHighlight("h1", GuideSourceField.BODY, null)),
                new GuidePairedSection(List.of(new GuideGroup("g1", List.of("i1")))),
                new GuidePairedSection(List.of(new GuideGroup("g2", List.of("i2")))),
                new GuidePairedSection(List.of(new GuideGroup("g3", List.of("i3")))),
                apply, deadline, docs, contact,
                List.of(new GuidePitfall("p1", GuideSourceField.BODY, null))
        );
        assertThat(c.applyMethod().items()).containsExactly("회원가입", "신청서 작성");
        assertThat(c.deadlineNote().items()).containsExactly("2026-03-01 ~ 2026-05-31");
        assertThat(c.requiredDocuments().items()).containsExactly("등본");
        assertThat(c.contact().sourceField()).isEqualTo(GuideSourceField.SUPPORT_CONTENT);
    }
}
