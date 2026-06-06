package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand.AttachmentItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("OpenAiAttachmentEmbeddingJudge buildUserMessage")
class OpenAiAttachmentEmbeddingJudgeTest {

    private OpenAiAttachmentEmbeddingJudge judge(int maxPreviewChars) {
        OpenAiAttachmentGateProperties props =
                new OpenAiAttachmentGateProperties("key", "gpt-4o-mini", 800, maxPreviewChars);
        return new OpenAiAttachmentEmbeddingJudge(
                props, new ObjectMapper(), mock(ApplicationEventPublisher.class),
                RestClient.builder());
    }

    @Test
    @DisplayName("프리뷰가 maxPreviewChars 로 잘린다")
    void truncatesPreview() {
        var cmd = new AttachmentEmbeddingJudgeCommand("제목", "요약",
                List.of(new AttachmentItem(10L, "a.hwp", "가".repeat(5000))));
        String msg = judge(100).buildUserMessage(cmd);
        // attachmentId 헤더 뒤의 본문 프리뷰가 100자 이내
        assertThat(msg).contains("attachmentId=10");
        assertThat(msg.length()).isLessThan(400); // 헤더 + 100자 수준
    }

    @Test
    @DisplayName("모든 첨부의 attachmentId 가 메시지에 포함된다")
    void includesAllIds() {
        var cmd = new AttachmentEmbeddingJudgeCommand("제목", null,
                List.of(new AttachmentItem(10L, "a.hwp", "x"),
                        new AttachmentItem(11L, "b.hwp", "y")));
        String msg = judge(1500).buildUserMessage(cmd);
        assertThat(msg).contains("attachmentId=10").contains("attachmentId=11");
    }
}
