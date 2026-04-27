package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    private GuideContent sampleContent(String summary) {
        GuideGroup group = new GuideGroup(null, List.of("만 19~34세"));
        return new GuideContent(
                summary,
                new GuidePairedSection(List.of(group)),
                null, null, List.of());
    }

    @Test
    void 신규_가이드_생성() {
        GuideContent content = sampleContent("청년 월세 지원");
        Guide guide = Guide.builder()
                .policyId(1L)
                .content(content)
                .sourceHash("hash1")
                .build();

        assertThat(guide.getPolicyId()).isEqualTo(1L);
        assertThat(guide.getContent().oneLineSummary()).isEqualTo("청년 월세 지원");
        assertThat(guide.getSourceHash()).isEqualTo("hash1");
    }

    @Test
    void hasChanged는_해시가_다르면_true() {
        Guide guide = Guide.builder()
                .policyId(1L)
                .content(sampleContent("요약"))
                .sourceHash("hash1")
                .build();

        assertThat(guide.hasChanged("hash2")).isTrue();
        assertThat(guide.hasChanged("hash1")).isFalse();
    }

    @Test
    void regenerate는_콘텐츠와_해시를_갱신() {
        Guide guide = Guide.builder()
                .policyId(1L)
                .content(sampleContent("이전"))
                .sourceHash("oldHash")
                .build();

        GuideContent newContent = sampleContent("이후");
        guide.regenerate(newContent, "newHash");

        assertThat(guide.getContent().oneLineSummary()).isEqualTo("이후");
        assertThat(guide.getSourceHash()).isEqualTo("newHash");
    }
}
