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
                List.of(new ChunkInput("X", 12L, 35, 35)),
                null);

        String txt = input.combinedSourceText();
        assertThat(txt).contains("[chunk-0 source=ATTACHMENT attachment-id=12 pages=35-35]");
    }
}
