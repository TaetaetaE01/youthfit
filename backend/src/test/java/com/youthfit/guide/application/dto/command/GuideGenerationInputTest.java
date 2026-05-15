package com.youthfit.guide.application.dto.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationInputTest {

    @Test
    void givenBodyAndAttachmentChunks_whenCombinedSourceText_thenLabelsHaveMeta() {
        GuideGenerationInput input = new GuideGenerationInput(
                7L, "제목", 2026,
                "summary", "body", "supportTarget", "selectionCriteria", "supportContent",
                "contact", "organization",
                List.of(
                        new ChunkInput("본문 텍스트", null, null, null, "BODY"),
                        new ChunkInput("첨부 1페이지~3페이지 텍스트", 12L, 1, 3, "ATTACHMENT"),
                        new ChunkInput("HWP 텍스트", 13L, null, null, "ATTACHMENT")
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
                List.of(new ChunkInput("X", 12L, 35, 35, "ATTACHMENT")),
                null);

        String txt = input.combinedSourceText();
        assertThat(txt).contains("[chunk-0 source=ATTACHMENT attachment-id=12 pages=35-35]");
    }

    @Test
    void combinedSourceText_는_BODY_청크에_source_BODY_라벨을_붙인다() {
        GuideGenerationInput input = inputWithChunks(List.of(
                new ChunkInput("본문 청크 텍스트", null, null, null, "BODY")
        ));

        String text = input.combinedSourceText();

        assertThat(text).contains("[chunk-0 source=BODY]");
    }

    @Test
    void combinedSourceText_는_ATTACHMENT_청크에_attachment_id_와_pages_를_노출한다() {
        GuideGenerationInput input = inputWithChunks(List.of(
                new ChunkInput("첨부 청크", 42L, 3, 5, "ATTACHMENT")
        ));

        String text = input.combinedSourceText();

        assertThat(text).contains("[chunk-0 source=ATTACHMENT attachment-id=42 pages=3-5]");
    }

    @Test
    void combinedSourceText_는_ENRICHMENT_BODY_청크에_별도_source_라벨을_붙인다() {
        GuideGenerationInput input = inputWithChunks(List.of(
                new ChunkInput("외부 페이지 발췌", null, null, null, "ENRICHMENT_BODY")
        ));

        String text = input.combinedSourceText();

        assertThat(text).contains("[chunk-0 source=ENRICHMENT_BODY]");
    }

    private GuideGenerationInput inputWithChunks(List<ChunkInput> chunks) {
        return new GuideGenerationInput(
                1L, "정책 제목", 2026, "summary", "body",
                null, null, null, null, null,
                chunks, null);
    }
}
