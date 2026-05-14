package com.youthfit.guide.application.dto.command;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationInputTest {

    @Test
    void givenBodyAndAttachmentChunks_whenCombinedSourceText_thenLabelsHaveMeta() {
        GuideGenerationInput input = new GuideGenerationInput(
                7L, "제목", 2026,
                "summary", "body", "supportTarget", "selectionCriteria", "supportContent",
                "contact", "organization",
                null,
                List.of(
                        new ChunkInput("본문 텍스트", null, null, null),
                        new ChunkInput("첨부 1페이지~3페이지 텍스트", 12L, 1, 3),
                        new ChunkInput("HWP 텍스트", 13L, null, null)
                ),
                null);

        String txt = input.combinedSourceText();

        assertThat(txt).contains("[chunk-0 source=BODY]");
        assertThat(txt).contains("본문 텍스트");
        assertThat(txt).contains("[chunk-1 source=ATTACHMENT attachment-id=12 pages=1-3]");
        assertThat(txt).contains("첨부 1페이지~3페이지 텍스트");
        assertThat(txt).contains("[chunk-2 source=ATTACHMENT attachment-id=13]");
        assertThat(txt).doesNotContain("[chunk-2 source=ATTACHMENT attachment-id=13 pages=");
    }

    @Test
    void givenSinglePageChunk_whenCombined_thenLabelHasIdenticalRange() {
        GuideGenerationInput input = new GuideGenerationInput(
                7L, "제목", null,
                null, null, null, null, null, null, null,
                null,
                List.of(new ChunkInput("X", 12L, 35, 35)),
                null);

        String txt = input.combinedSourceText();
        assertThat(txt).contains("[chunk-0 source=ATTACHMENT attachment-id=12 pages=35-35]");
    }

    @Test
    void enrichment이_null이면_combinedSourceText에_enrichment_절_미포함() {
        GuideGenerationInput input = new GuideGenerationInput(
                1L, "title", 2026, "summary", "body",
                "supportTarget", "criteria", "content", "contact", "org",
                null,                    // enrichment
                List.of(), null
        );
        String text = input.combinedSourceText();
        assertThat(text).doesNotContain("[enrichment");
    }

    @Test
    void enrichment이_있으면_9개_절_모두_직렬화() {
        GuideGenerationInput.EnrichmentInput enrichment = new GuideGenerationInput.EnrichmentInput(
                "AI 추출 지원대상",
                "AI 추출 지원내용",
                "1. 회원가입 2. 신청서 작성",
                "주민등록등본, 임대차계약서",
                "2026-03-01 ~ 2026-05-31",
                "정책 개요",
                "선정기준 풀이",
                "서울특별시 청년정책담당관",
                "02-2133-6586",
                Instant.parse("2026-05-12T04:12:33Z"),
                "https://www.youthcenter.go.kr/...",
                0.82
        );
        GuideGenerationInput input = new GuideGenerationInput(
                1L, "title", 2026, "summary", "body",
                null, null, null, null, null,
                enrichment,
                List.of(), null
        );
        String text = input.combinedSourceText();
        assertThat(text)
                .contains("[enrichment.meta]")
                .contains("[enrichment.policyOverview]")
                .contains("[enrichment.supportTarget]")
                .contains("[enrichment.eligibilityCriteria]")
                .contains("[enrichment.supportContent]")
                .contains("[enrichment.applyMethod]")
                .contains("[enrichment.requiredDocuments]")
                .contains("[enrichment.deadlineNote]")
                .contains("[enrichment.operatingOrganization]")
                .contains("[enrichment.contactPhone]")
                .contains("0.82")
                .contains("2026-05-12T04:12:33Z");
    }

    @Test
    void contact_및_organization이_있으면_combinedSourceText에_블록으로_직렬화() {
        GuideGenerationInput input = new GuideGenerationInput(
                1L, "title", 2026, "summary", "body",
                null, null, null,
                "02-2133-6586",
                "서울특별시 청년정책담당관",
                null,
                List.of(),
                null
        );
        String text = input.combinedSourceText();
        assertThat(text).contains("[contact]");
        assertThat(text).contains("02-2133-6586");
        assertThat(text).contains("[organization]");
        assertThat(text).contains("서울특별시 청년정책담당관");
    }

    @Test
    void enrichment_일부_sections이_null이면_해당_절_미포함() {
        GuideGenerationInput.EnrichmentInput enrichment = new GuideGenerationInput.EnrichmentInput(
                null,           // supportTarget null
                "지원내용",
                null,           // applyMethod null
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-05-12T04:12:33Z"),
                "https://...",
                0.7
        );
        GuideGenerationInput input = new GuideGenerationInput(
                1L, "t", null, null, null, null, null, null, null, null,
                enrichment, List.of(), null
        );
        String text = input.combinedSourceText();
        assertThat(text).contains("[enrichment.supportContent]");
        assertThat(text).doesNotContain("[enrichment.supportTarget]");
        assertThat(text).doesNotContain("[enrichment.applyMethod]");
    }
}
